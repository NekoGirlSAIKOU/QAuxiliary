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
package com.hicore.dialog;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.RadioGroup.LayoutParams;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.view.ViewCompat;
import cc.ioctl.util.LayoutHelper;
import cc.ioctl.util.ui.FaultyDialog;
import cc.ioctl.util.ui.drawable.DebugDrawable;
import de.robv.android.xposed.XposedBridge;
import io.github.qauxv.R;
import io.github.qauxv.config.ConfigManager;
import io.github.qauxv.ui.CustomDialog;
import io.github.qauxv.ui.ResUtils;
import io.github.qauxv.util.Log;
import io.github.qauxv.util.SafUtils;
import io.github.qauxv.util.Toasts;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class RepeaterPlusIconSettingDialog implements View.OnClickListener,
        DialogInterface.OnClickListener, TextWatcher,
        CompoundButton.OnCheckedChangeListener {

    public static final String qn_repeat_icon_data = "qn_repeat_plus_icon_data";
    public static final String qn_repeat_icon_dpi = "qn_repeat_plus_icon_dpi";
    public static final String qn_repeat_last_file = "qn_repeat_plus_last_file";
    private static Bitmap sCachedRepeaterIcon;
    private final Context ctx;
    private final AlertDialog dialog;
    private final Button loadBtn;
    private final Button browseBtn;
    private final Button restoreDefBtn;
    private final EditText pathInput;
    private final ImageView prevImgView;
    private final CheckBox specDpi;
    private final LinearLayout linearLayoutDpi;
    private final TextView textViewWarning;
    private final EditText InputDPI;
    private byte[] targetIconData;
    @Nullable
    private String targetIconPathHint;
    private Button saveBtn;
    private Bitmap currentIcon;
    private BitmapDrawable currentIconDrawable;
    private boolean useDefault;

    public RepeaterPlusIconSettingDialog(Context context) {
        dialog = (AlertDialog) CustomDialog.createFailsafe(context).setTitle("自定义+1Plus图标")
                .setPositiveButton("保存", this)
                .setNegativeButton("取消", null).setCancelable(true).create();
        ctx = dialog.getContext();
        dialog.setCanceledOnTouchOutside(false);
        @SuppressLint("InflateParams") View v = LayoutInflater.from(ctx)
                .inflate(R.layout.select_repeater_icon_dialog_plus, null);
        loadBtn = v.findViewById(R.id.selectRepeaterIcon_buttonLoadFile);
        loadBtn.setOnClickListener(this);
        browseBtn = v.findViewById(R.id.selectRepeaterIcon_buttonBrowseImg);
        browseBtn.setOnClickListener(this);
        restoreDefBtn = v.findViewById(R.id.selectRepeaterIcon_buttonRestoreDefaultIcon);
        restoreDefBtn.setOnClickListener(this);
        prevImgView = v.findViewById(R.id.selectRepeaterIcon_imageViewPreview);
        prevImgView.setPadding(1, 1, 1, 1);
        ViewCompat.setBackground(prevImgView, new DebugDrawable(ctx));
        specDpi = v.findViewById(R.id.selectRepeaterIcon_checkBoxSpecifyDpi);
        specDpi.setOnCheckedChangeListener(this);

        pathInput = v.findViewById(R.id.selectRepeaterIcon_editTextIconLocation);
        linearLayoutDpi = v.findViewById(R.id.selectRepeaterIcon_linearLayoutDpi);
        textViewWarning = v.findViewById(R.id.selectRepeaterIcon_textViewWarnMsg);
        InputDPI = v.findViewById(R.id.selectRepeaterIcon_InputDpi);
        InputDPI.addTextChangedListener(this);
        InputDPI.setText(String.valueOf(getDpiSet()));
        dialog.setView(v);
    }

    public static Bitmap getRepeaterIcon() {
        if (sCachedRepeaterIcon != null) {
            return sCachedRepeaterIcon;
        }
        ConfigManager cfg = ConfigManager.getDefaultConfig();
        byte[] data = (byte[]) cfg.getBytes(qn_repeat_icon_data);
        int dpi = cfg.getIntOrDefault(qn_repeat_icon_dpi, 0);
        if (data != null) {
            Bitmap bm = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (bm != null) {
                if (dpi > 0) {
                    bm.setDensity(dpi);
                }
                sCachedRepeaterIcon = bm;
            }
        }
        if (sCachedRepeaterIcon == null) {
            InputStream in = ResUtils.openAsset("repeat.png");
            if (in != null) {
                sCachedRepeaterIcon = BitmapFactory.decodeStream(in);
                try {
                    in.close();
                } catch (IOException ignored) {
                }
                sCachedRepeaterIcon.setDensity(320);
            } else {
                Log.e("getRepeaterIcon/E ResUtils.openAsset(\"repeat.png\") == null");
            }
        }
        return sCachedRepeaterIcon;
    }

    public static int getDpiSet(){
        ConfigManager cfg = ConfigManager.getDefaultConfig();
        return cfg.getIntOrDefault(qn_repeat_icon_dpi, 50);
    }

    public static void createAndShowDialog(Context ctx) {
        new RepeaterPlusIconSettingDialog(ctx).show();
    }

    public AlertDialog show() {
        dialog.show();
        saveBtn = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        saveBtn.setOnClickListener(this);
        ConfigManager cfg = ConfigManager.getDefaultConfig();
        String lastPath = cfg.getString(qn_repeat_last_file);
        byte[] data = (byte[]) cfg.getBytes(qn_repeat_icon_data);
        int dpi = cfg.getIntOrDefault(qn_repeat_icon_dpi, 0);
        if (lastPath != null) {
            pathInput.setText(lastPath);
        }
        if (data != null) {
            currentIcon = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (currentIcon != null) {
                useDefault = false;
                if (dpi > 0) {
                    currentIcon.setDensity(dpi);
                }
            }
        }
        if (currentIcon == null) {
            currentIcon = BitmapFactory.decodeStream(ResUtils.openAsset("repeat.png"));
            currentIcon.setDensity(320);
            useDefault = true;
            linearLayoutDpi.setVisibility(View.GONE);
        } else {
            linearLayoutDpi.setVisibility(View.VISIBLE);
        }
        currentIconDrawable = new BitmapDrawable(ctx.getResources(), currentIcon);
        prevImgView.setImageDrawable(currentIconDrawable);

        ViewGroup.LayoutParams params = (ViewGroup.LayoutParams) prevImgView.getLayoutParams();
        params.width = LayoutHelper.dip2px(ctx,getCurrentDPI());
        params.height = LayoutHelper.dip2px(ctx,getCurrentDPI());
        prevImgView.setLayoutParams(params);

        prevImgView.setMaxHeight(LayoutHelper.dip2px(ctx,getCurrentDPI()));
        prevImgView.setMaxWidth(LayoutHelper.dip2px(ctx,getCurrentDPI()));
        return dialog;
    }
    private int getCurrentDPI(){
        try{
            return Integer.parseInt(InputDPI.getText().toString());
        }catch (Exception e){
            return 45;
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        dialog.dismiss();
    }

    @SuppressLint("DefaultLocale")
    @Override
    public void onClick(View v) {
        if (v == loadBtn) {
            String path = pathInput.getText().toString();
            if (path.length() == 0) {
                Toasts.error(ctx, "请输入图片路径");
                return;
            }
            File file = new File(path);
            if (!file.exists()) {
                Toasts.error(ctx, "找不到文件");
                return;
            }
            try {
                FileInputStream fin = new FileInputStream(file);
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                byte[] buf = new byte[2048];
                int len;
                while ((len = fin.read(buf)) != -1) {
                    bout.write(buf, 0, len);
                }
                fin.close();
                byte[] arr = bout.toByteArray();
                updateBitmapPreview(arr);
            } catch (IOException e) {
                FaultyDialog.show(ctx, "读取文件失败", e);
            }
        } else if (v == restoreDefBtn) {
            currentIcon = null;
            useDefault = true;
            targetIconData = null;
            linearLayoutDpi.setVisibility(View.GONE);
            textViewWarning.setVisibility(View.GONE);
            prevImgView.setImageDrawable(ResUtils.loadDrawableFromAsset("repeat.png", ctx));
        } else if (v == saveBtn) {
            if (targetIconData != null) {
                int dpi = getCurrentDPI();
                ConfigManager cfg = ConfigManager.getDefaultConfig();
                cfg.putBytes(qn_repeat_icon_data, targetIconData);
                cfg.putInt(qn_repeat_icon_dpi, dpi);
                cfg.putString(qn_repeat_last_file, targetIconPathHint);
                cfg.save();
                sCachedRepeaterIcon = currentIcon;
                dialog.dismiss();
            } else {
                if (useDefault) {
                    ConfigManager cfg = ConfigManager.getDefaultConfig();
                    cfg.remove(qn_repeat_icon_data);
                    cfg.remove(qn_repeat_icon_dpi);
                    cfg.remove(qn_repeat_last_file);
                    cfg.save();
                    dialog.dismiss();
                    sCachedRepeaterIcon = null;
                } else {
                    ConfigManager cfg = ConfigManager.getDefaultConfig();
                    cfg.putInt(qn_repeat_icon_dpi, getCurrentDPI());
                    cfg.save();
                    dialog.dismiss();
                    sCachedRepeaterIcon = null;
                }
            }
        } else if (v == browseBtn) {
            SafUtils.requestOpenFile(ctx).setMimeType("image/*").onResult(uri -> {
                        try (InputStream is = SafUtils.openInputStream(ctx, uri)) {
                            ByteArrayOutputStream bout = new ByteArrayOutputStream();
                            byte[] buf = new byte[2048];
                            int len;
                            while ((len = is.read(buf)) != -1) {
                                bout.write(buf, 0, len);
                            }
                            byte[] arr = bout.toByteArray();
                            targetIconPathHint = uri.toString();
                            pathInput.setText(targetIconPathHint);
                            updateBitmapPreview(arr);
                        } catch (IOException e) {
                            FaultyDialog.show(ctx, "打开文件失败", e);
                        }
                    }
            ).commit();
        }
    }

    @UiThread
    private void updateBitmapPreview(@NonNull byte[] data) {
        Bitmap bm = BitmapFactory.decodeByteArray(data, 0, data.length);
        if (bm == null) {
            Toasts.error(ctx, "不支持此文件(格式)");
            return;
        }
        long fileSize = data.length;
        if (fileSize > 16 * 1024) {
            textViewWarning.setText(String.format("该图片文件体积较大(%dbytes),可能导致卡顿", fileSize));
            textViewWarning.setVisibility(View.VISIBLE);
        } else {
            textViewWarning.setVisibility(View.GONE);
        }
        currentIcon = bm;
        targetIconData = data;
        currentIcon.setDensity(getCurrentDPI());
        currentIconDrawable = new BitmapDrawable(ctx.getResources(), currentIcon);
        prevImgView.setImageDrawable(currentIconDrawable);
        useDefault = false;
        linearLayoutDpi.setVisibility(View.VISIBLE);
    }
    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView == specDpi) {
            InputDPI.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            currentIcon.setDensity(getCurrentDPI());
            if (currentIconDrawable != null) {
                currentIconDrawable = new BitmapDrawable(ctx.getResources(), currentIcon);
                prevImgView.setImageDrawable(currentIconDrawable);
            }
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }

    @Override
    public void afterTextChanged(Editable s) {
        if (currentIcon != null) {
            if (currentIconDrawable != null) {
                currentIconDrawable = new BitmapDrawable(ctx.getResources(), currentIcon);
                prevImgView.setImageDrawable(currentIconDrawable);
                prevImgView.setMaxHeight(LayoutHelper.dip2px(ctx,getCurrentDPI()));
                prevImgView.setMaxWidth(LayoutHelper.dip2px(ctx,getCurrentDPI()));
                ViewGroup.LayoutParams params = (ViewGroup.LayoutParams) prevImgView.getLayoutParams();
                params.width = LayoutHelper.dip2px(ctx,getCurrentDPI());
                params.height = LayoutHelper.dip2px(ctx,getCurrentDPI());
                prevImgView.setLayoutParams(params);
            }
        }
    }
}
