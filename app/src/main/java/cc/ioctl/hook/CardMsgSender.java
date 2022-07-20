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
package cc.ioctl.hook;

import android.content.Context;
import android.os.Parcelable;
import android.view.View;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.qauxv.SyncUtils;
import io.github.qauxv.base.IDynamicHook;
import io.github.qauxv.base.annotation.FunctionHookEntry;
import io.github.qauxv.base.annotation.UiItemAgentEntry;
import io.github.qauxv.bridge.AppRuntimeHelper;
import io.github.qauxv.dsl.FunctionEntryRouter.Locations.Auxiliary;
import io.github.qauxv.remote.TransactionHelper;
import io.github.qauxv.router.decorator.BaseSwitchFunctionDecorator;
import io.github.qauxv.router.decorator.IInputButtonDecorator;
import io.github.qauxv.router.dispacher.InputButtonHookDispatcher;
import io.github.qauxv.step.Step;
import io.github.qauxv.util.DexKit;
import io.github.qauxv.util.Toasts;
import java.lang.reflect.InvocationTargetException;
import mqq.app.AppRuntime;

@UiItemAgentEntry
@FunctionHookEntry
public class CardMsgSender extends BaseSwitchFunctionDecorator implements IInputButtonDecorator {

    public static final CardMsgSender INSTANCE = new CardMsgSender();

    private CardMsgSender() {
        super("qn_send_card_msg", false, new int[]{
                DexKit.C_ARK_APP_ITEM_BUBBLE_BUILDER,
                DexKit.C_FACADE,
                DexKit.C_TEST_STRUCT_MSG,
                DexKit.N_BASE_CHAT_PIE__INIT
        });
    }

    @NonNull
    @Override
    protected IDynamicHook getDispatcher() {
        return InputButtonHookDispatcher.INSTANCE;
    }

    @NonNull
    @Override
    public String getName() {
        return "发送卡片消息";
    }

    @Nullable
    @Override
    public String getDescription() {
        return "小心使用，可能会导致自己被封号。"
                + "注意 ‘发送卡片消息’ 和 ‘复制卡片消息’ 是两个不同的功能，两者无关。";
    }

    @NonNull
    @Override
    public String[] getUiItemLocation() {
        return Auxiliary.EXPERIMENTAL_CATEGORY;
    }

    @Override
    public boolean onFunBtnLongClick(@NonNull String text,
                                     @NonNull Parcelable session,
                                     @NonNull EditText input,
                                     @NonNull View sendBtn,
                                     @NonNull Context ctx1,
                                     @NonNull AppRuntime qqApp) throws Exception {
        if (!isEnabled()) {
            return false;
        }
        if ((text.contains("<?xml") || text.contains("{\""))) {
            long uin = AppRuntimeHelper.getLongAccountUin();
            if (uin < 10000) {
                Toasts.error(ctx1, "Invalid account uin");
                return false;
            }
            SyncUtils.async(() -> {
                if (text.contains("<?xml")) {
                    try {
                        String errorMsg = TransactionHelper.postCardMsg(uin, text);
                        if (errorMsg != null) {
                            Toasts.error(ctx1, errorMsg);
                            return;
                        }
                        if (CardMsgSender.ntSendCardMsg(qqApp, session, text)) {
                            SyncUtils.runOnUiThread(() -> input.setText(""));
                        } else {
                            Toasts.error(ctx1, "XML语法错误(代码有误)");
                        }
                    } catch (Throwable e) {
                        if (e instanceof InvocationTargetException) {
                            e = e.getCause();
                        }
                        traceError(e);
                        Toasts.error(ctx1, e.toString().replace("java.lang.", ""));
                    }
                } else if (text.contains("{\"")) {
                    try {
                        String errorMsg = TransactionHelper.postCardMsg(uin, text);
                        if (errorMsg != null) {
                            Toasts.error(ctx1, errorMsg);
                            return;
                        }
                        // Object arkMsg = load("com.tencent.mobileqq.data.ArkAppMessage").newInstance();
                        if (CardMsgSender.ntSendCardMsg(qqApp, session, text)) {
                            SyncUtils.runOnUiThread(() -> input.setText(""));
                        } else {
                            Toasts.error(ctx1, "JSON语法错误(代码有误)");
                        }
                    } catch (Throwable e) {
                        if (e instanceof InvocationTargetException) {
                            e = e.getCause();
                        }
                        traceError(e);
                        Toasts.error(ctx1, e.toString().replace("java.lang.", ""));
                    }
                }
            });
            return true;
        }
        return false;
    }

    @SuppressWarnings("JavaJniMissingFunction")
    static native boolean ntSendCardMsg(AppRuntime rt, Parcelable session, String msg) throws Exception;

    @Override
    public boolean initOnce() {
        return true;
    }

    @Nullable
    @Override
    public Step[] makePreparationSteps() {
        return new Step[]{};
    }
}
