/*
 * QAuxiliary - An Xposed module for QQ/TIM
 * Copyright (C) 2019-2022 qwq233@qwq2333.top
 * https://github.com/cinit/QAuxiliary
 *
 * This software is non-free but opensource software: you can redistribute it
 * and/or modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either
 * version 3 of the License, or any later version and our eula as published
 * by QAuxiliary contributors.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * and eula along with this software.  If not, see
 * <https://www.gnu.org/licenses/>
 * <https://github.com/cinit/QAuxiliary/blob/master/LICENSE.md>.
 */

package io.github.qauxv.remote

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import io.github.qauxv.BuildConfig
import io.github.qauxv.SyncUtils
import io.github.qauxv.config.ConfigManager
import io.github.qauxv.util.LicenseStatus
import io.github.qauxv.util.Log
import io.github.qauxv.util.data.UserStatusConst
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URL
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection

object TransactionHelper {

    const val apiAddress = "https://api.qwq2333.top/qa"

    // in ms, 15min
    private const val COOLDOWN_TIME = 15 * 60 * 1000L

    // 3 times in a cooldown time
    private const val MAX_ACTION_COUNT_PER_COOLDOWN = 3

    private const val KEY_LAST_ACTION_COOLDOWN_TIME = "last_action_cooldown_time"
    private const val KEY_LAST_ACTION_COUNT_IN_COOLDOWN = "last_card_msg_action_count_in_cooldown"
    private const val KEY_MSG_SYNC_LIST = "card_msg_sync_list"

    private val sIsSyncThreadRunning: AtomicBoolean by lazy { AtomicBoolean(false) }
    private val sCardMsgHistoryLock: Any = Object()

    @Throws(IOException::class)
    private fun convertInputStreamToString(inputStream: InputStream): String? {
        val bufferedReader = BufferedReader(InputStreamReader(inputStream))
        var line: String? = ""
        var result: String? = ""
        while (bufferedReader.readLine().also { line = it } != null) {
            result += line
        }
        inputStream.close()
        return result
    }

    @JvmStatic
    fun getUserStatus(uin: Long): Int {
        return UserStatusConst.whitelisted
    }

    /**
     * 记录发送卡片信息
     * 异步执行，不阻塞当前线程
     *
     * @param uin 发送者qq号
     * @param msg 卡片内容
     * @return 是否上报成功 成功返回null 失败返回理由 成功返回null 失败返回理由
     */
    @JvmStatic
    fun postCardMsg(uin: Long, msg: String): String? {
        return null
    }

    /**
     * This method is non-blocking.
     */
    private fun notifyNewCardMsgRecord(r: CardMsgSendRecord) {
        if (r.msg.isEmpty() || r.uin == 0L) {
            return
        }
        val cfg = ConfigManager.getDefaultConfig()
        val itemsToSync: List<CardMsgSendRecord>
        // add to history
        synchronized(sCardMsgHistoryLock) {
            val items: ArrayList<CardMsgSendRecord>
            val json = cfg.getString(KEY_MSG_SYNC_LIST, null)
            items = if (json.isNullOrEmpty()) {
                ArrayList()
            } else {
                deserializeCardMsgSendRecordListFromJson(json)
            }
            items.add(r)
            cfg.putString(KEY_MSG_SYNC_LIST, serializeCardMsgSendRecordListToJson(items))
            itemsToSync = items
        }
        if (itemsToSync.isNotEmpty()) {
            requestSyncCardMsgHistory(itemsToSync)
        }
    }

    private fun deleteCardMsgRecord(uuid: String) {
        val cfg = ConfigManager.getDefaultConfig()
        val items: ArrayList<CardMsgSendRecord>
        synchronized(sCardMsgHistoryLock) {
            val json = cfg.getString(KEY_MSG_SYNC_LIST, null)
            items = if (json.isNullOrEmpty()) {
                ArrayList()
            } else {
                deserializeCardMsgSendRecordListFromJson(json)
            }
            items.removeIf { it.uuid == uuid }
            cfg.putString(KEY_MSG_SYNC_LIST, serializeCardMsgSendRecordListToJson(items))
        }
    }

    /*
     * This method is non-blocking.
     */
    private fun requestSyncCardMsgHistory(items: List<CardMsgSendRecord>) {
        // if not syncing, start a new sync
        if (!sIsSyncThreadRunning.compareAndSet(false, true)) {
            return
        }
        SyncUtils.async {
            try {
                for (r in items) {
                    Log.d("sync card msg history: $r")
                    val url = URL("$apiAddress/statistics/card/send")
                    val conn = url.openConnection() as HttpsURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json; utf-8")
                    conn.setRequestProperty("Accept", "application/json")
                    val os = DataOutputStream(conn.outputStream)
                    val request = JSONObject()
                    request.put("uin", r.uin)
                    request.put("msg", r.msg)
                    os.writeBytes(request.toString())
                    os.flush()
                    os.close()
                    val resp = JSONObject(convertInputStreamToString(
                        if (conn.responseCode >= 400) {
                            conn.errorStream
                        } else {
                            conn.inputStream
                        }
                    )!!)
                    if (resp.getInt("code") == 200) {
                        if (BuildConfig.DEBUG) {
                            Log.d("syncCardMsgHistory/requestSyncCardMsgHistory: ${r.uuid} $resp")
                        }
                        deleteCardMsgRecord(r.uuid)
                    } else {
                        Log.e("syncCardMsgHistory/requestSyncCardMsgHistory: ${r.uuid} $resp")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(e)
            } finally {
                sIsSyncThreadRunning.set(false)
            }
        }
    }

    @Throws(JSONException::class)
    fun getCardMsgHistory(): ArrayList<CardMsgSendRecord> {
        val cfg = ConfigManager.getDefaultConfig()
        val json = cfg.getString(KEY_MSG_SYNC_LIST, null)
        return if (json.isNullOrEmpty()) {
            ArrayList()
        } else {
            deserializeCardMsgSendRecordListFromJson(json)
        }
    }

    /**
     * [time] in ms
     */
    data class CardMsgSendRecord(
        @SerializedName("uin") val uin: Long,
        @SerializedName("msg") val msg: String,
        @SerializedName("time") val time: Long = System.currentTimeMillis(),
        @SerializedName("uuid") val uuid: String = UUID.randomUUID().toString()) {

        override fun toString(): String {
            return "CardMsgSendRecord(uin=$uin, msg='$msg', time=$time, uuid='$uuid')"
        }
    }

    @Throws(JSONException::class)
    fun serializeCardMsgSendRecordListToJson(list: List<CardMsgSendRecord>, maxCount: Int = 100): String {
        val items: List<CardMsgSendRecord> = if (list.size > maxCount) {
            // drop oldest records
            val tmp = ArrayList<CardMsgSendRecord>(list)
            tmp.sortBy { -it.time }
            tmp.subList(0, maxCount)
        } else {
            list
        }
        return Gson().toJsonTree(items, object : TypeToken<List<CardMsgSendRecord?>?>() {}.type).asJsonArray.toString()
    }

    @Throws(JSONException::class)
    fun deserializeCardMsgSendRecordListFromJson(json: String): ArrayList<CardMsgSendRecord> =
        Gson().fromJson(json, object : TypeToken<List<CardMsgSendRecord?>?>() {}.type)

}
