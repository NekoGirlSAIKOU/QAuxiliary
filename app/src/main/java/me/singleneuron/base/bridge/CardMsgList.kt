/*
 * QNotified - An Xposed module for QQ/TIM
 * Copyright (C) 2019-2022 dmca@ioctl.cc
 * https://github.com/ferredoxin/QNotified
 *
 * This software is non-free but opensource software: you can redistribute it
 * and/or modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either
 * version 3 of the License, or any later version and our eula as published
 * by ferredoxin.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * and eula along with this software.  If not, see
 * <https://www.gnu.org/licenses/>
 * <https://github.com/ferredoxin/QNotified/blob/master/LICENSE.md>.
 */
package me.singleneuron.base.bridge

import androidx.annotation.NonNull
import com.google.gson.Gson
import io.github.qauxv.SyncUtils
import io.github.qauxv.config.ConfigManager
import io.github.qauxv.util.LicenseStatus
import java.net.URL

const val apiAddress = "https://2fa.qwq2333.top/card/BlackList"
const val cacheKey = "cardRuleCache"
const val lastUpdateTimeKey = "cardRuleCacheLastUpdateTime"

abstract class CardMsgList {

    companion object {

        @JvmStatic
        @NonNull
        fun getInstance(): () -> String {
            //Todo
            return ::getBlackList
        }

    }
}

fun getBuiltInRule(): String {
    return "{}"
}

fun getBlackList(): String {
    return "{}"
}
