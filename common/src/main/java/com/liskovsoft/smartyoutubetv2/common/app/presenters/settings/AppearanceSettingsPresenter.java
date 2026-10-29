package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.AppearancePrefs;
import com.liskovsoft.smartyoutubetv2.common.misc.AppearanceStyle;
import com.liskovsoft.smartyoutubetv2.common.misc.FontScaler;
import com.liskovsoft.smartyoutubetv2.common.misc.FontScaler.Category;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;

import java.util.ArrayList;
import java.util.List;

/**
 * Advanced appearance: per-element font size, text color and bold, background color and ready-made styles.
 */
public class AppearanceSettingsPresenter extends BasePresenter<Void> {
    private static final int[] COLOR_NAMES = {R.string.ap_color_default, R.string.ap_color_white, R.string.ap_color_grey, R.string.ap_color_yellow, R.string.ap_color_amber, R.string.ap_color_orange, R.string.ap_color_red, R.string.ap_color_pink, R.string.ap_color_purple, R.string.ap_color_blue, R.string.ap_color_cyan, R.string.ap_color_green, R.string.ap_color_black};
    private static final int[] COLOR_VALUES = {0, 0xFFFFFFFF, 0xFFE0E0E0, 0xFFFFEB3B, 0xFFFFC107, 0xFFFF9800, 0xFFFF5252, 0xFFFF80AB, 0xFFCE93D8, 0xFF64B5F6, 0xFF4DD0E1, 0xFF81C784, 0xFF000000};
    private static final int[] BG_NAMES = {R.string.ap_bg_default, R.string.ap_bg_black, R.string.ap_bg_dgrey, R.string.ap_bg_charcoal, R.string.ap_bg_navy, R.string.ap_bg_forest, R.string.ap_bg_plum, R.string.ap_bg_brown, R.string.ap_bg_wine};
    private static final int[] BG_VALUES = {0, 0xFF000000, 0xFF121212, 0xFF1E1E1E, 0xFF0D1B2A, 0xFF0F2417, 0xFF1A1025, 0xFF1B1410, 0xFF2A0D0D};
    private static final int[] PRESET_NAMES = {R.string.ap_preset_default, R.string.ap_preset_tv_large, R.string.ap_preset_compact, R.string.ap_preset_contrast, R.string.ap_preset_warm};
    private static final float[] FONT_STEPS = {0.6f, 0.7f, 0.8f, 0.9f, 1.0f, 1.1f, 1.2f, 1.3f, 1.4f, 1.5f, 1.7f, 2.0f, 2.5f};

    private final MainUIData mMainUIData;
    private boolean mRestartApp;
    private final Runnable mOnFinish = () -> {
        if (mRestartApp) {
            mRestartApp = false;
            MessageHelpers.showLongMessage(getContext(), R.string.ap_note_restart);
        }
    };

    private AppearanceSettingsPresenter(Context context) {
        super(context);
        mMainUIData = MainUIData.instance(context);
    }

    public static AppearanceSettingsPresenter instance(Context context) {
        return new AppearanceSettingsPresenter(context);
    }

    public void show() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        appendPreview(settingsPresenter);
        appendPresets(settingsPresenter);
        appendSlots(settingsPresenter);
        appendTransfer(settingsPresenter);
        appendGlobalFontScale(settingsPresenter);
        appendFontSizes(settingsPresenter);
        appendTextColors(settingsPresenter);
        appendBold(settingsPresenter);
        appendAccent(settingsPresenter);
        appendBackground(settingsPresenter);
        appendReset(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.ap_dialog), mOnFinish);
    }

    private void appendPresets(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int i = 0; i < PRESET_NAMES.length; i++) {
            final int preset = i;
            options.add(UiOptionItem.from(getContext().getString(PRESET_NAMES[i]),
                    optionItem -> {
                        applyPreset(preset);
                        MessageHelpers.showMessage(getContext(), R.string.ap_preset_applied);
                    }, false));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.ap_presets), options);
    }

    private void appendGlobalFontScale(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (float scale : new float[] {0.7f, 0.8f, 0.9f, 1.0f, 1.1f, 1.2f, 1.3f, 1.4f, 1.5f, 1.7f, 2.0f}) {
            options.add(UiOptionItem.from(String.format("%sx", scale),
                    optionItem -> {
                        mMainUIData.setFontScale(scale);
                        mRestartApp = true;
                    },
                    Helpers.floatEquals(scale, mMainUIData.getFontScale())));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.font_scale), options);
    }

    private void appendFontSizes(AppDialogPresenter settingsPresenter) {
        for (Category category : Category.values()) {
            List<OptionItem> options = new ArrayList<>();

            for (float scale : FONT_STEPS) {
                options.add(UiOptionItem.from(String.format("%sx", scale),
                        optionItem -> mMainUIData.setFontScale(category, scale),
                        Helpers.floatEquals(scale, mMainUIData.getFontScale(category))));
            }

            settingsPresenter.appendRadioCategory(
                    getContext().getString(R.string.ap_size_of, getContext().getString(category.titleResId)), options);
        }
    }

    private void appendTextColors(AppDialogPresenter settingsPresenter) {
        for (Category category : Category.values()) {
            List<OptionItem> options = new ArrayList<>();

            for (int i = 0; i < COLOR_VALUES.length; i++) {
                final int color = COLOR_VALUES[i];
                options.add(UiOptionItem.from(swatch(getContext().getString(COLOR_NAMES[i]), color),
                        optionItem -> AppearancePrefs.setTextColor(getContext(), category, color),
                        AppearancePrefs.getTextColor(getContext(), category) == color));
            }

            settingsPresenter.appendRadioCategory(
                    getContext().getString(R.string.ap_color_of, getContext().getString(category.titleResId)), options);
        }
    }

    private void appendBold(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (Category category : Category.values()) {
            options.add(UiOptionItem.from(getContext().getString(category.titleResId),
                    optionItem -> AppearancePrefs.setBold(getContext(), category, optionItem.isSelected()),
                    AppearancePrefs.isBold(getContext(), category)));
        }

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.ap_bold), options);
    }

    private void appendBackground(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int i = 0; i < BG_VALUES.length; i++) {
            final int color = BG_VALUES[i];
            options.add(UiOptionItem.from(swatch(getContext().getString(BG_NAMES[i]), color),
                    optionItem -> AppearancePrefs.setBackground(getContext(), color),
                    AppearancePrefs.getBackground(getContext()) == color));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.ap_background), options);
    }

    private void appendAccent(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int i = 0; i < COLOR_VALUES.length; i++) {
            final int color = COLOR_VALUES[i];
            options.add(UiOptionItem.from(swatch(getContext().getString(COLOR_NAMES[i]), color),
                    optionItem -> AppearancePrefs.setAccent(getContext(), color),
                    AppearancePrefs.getAccent(getContext()) == color));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.ap_accent), options);
    }

    /** A colored dot before the name, so the color can be recognized at a glance */
    private static CharSequence swatch(String name, int color) {
        if (color == 0) {
            return name;
        }

        SpannableString text = new SpannableString("\u25CF " + name);
        // Black would be invisible on dark dialogs: show it with a light outline-like grey dot
        int dot = (color & 0xFFFFFF) == 0 ? 0xFF616161 : color;
        text.setSpan(new ForegroundColorSpan(dot), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    private void appendSlots(AppDialogPresenter settingsPresenter) {
        List<OptionItem> save = new ArrayList<>();
        List<OptionItem> load = new ArrayList<>();

        for (int i = 1; i <= AppearanceStyle.SLOTS; i++) {
            final int slot = i;
            String saved = AppearanceStyle.getSlot(getContext(), slot);
            String title = saved == null ?
                    getContext().getString(R.string.ap_slot_empty, slot) :
                    getContext().getString(R.string.ap_slot_filled, slot,
                            android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", AppearanceStyle.getSlotTime(getContext(), slot)));

            save.add(UiOptionItem.from(title, optionItem -> {
                AppearanceStyle.saveSlot(getContext(), slot);
                MessageHelpers.showMessage(getContext(), R.string.ap_slot_saved);
            }, false));

            if (saved != null) {
                load.add(UiOptionItem.from(title, optionItem -> {
                    if (AppearanceStyle.apply(getContext(), AppearanceStyle.getSlot(getContext(), slot))) {
                        MessageHelpers.showMessage(getContext(), R.string.ap_slot_loaded);
                    }
                }, false));
            }
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.ap_save_slot), save);
        if (!load.isEmpty()) {
            settingsPresenter.appendRadioCategory(getContext().getString(R.string.ap_load_slot), load);
        }
    }

    private void appendTransfer(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleButton(UiOptionItem.from(getContext().getString(R.string.ap_export), optionItem -> {
            ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("style", AppearanceStyle.serialize(getContext())));
                MessageHelpers.showLongMessage(getContext(), R.string.ap_export_done);
            }
        }));

        settingsPresenter.appendSingleButton(UiOptionItem.from(getContext().getString(R.string.ap_import), optionItem -> {
            ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = cm != null ? cm.getPrimaryClip() : null;
            CharSequence text = clip != null && clip.getItemCount() > 0 ? clip.getItemAt(0).coerceToText(getContext()) : null;

            if (text != null && AppearanceStyle.apply(getContext(), text.toString())) {
                MessageHelpers.showMessage(getContext(), R.string.ap_import_done);
            } else {
                MessageHelpers.showMessage(getContext(), R.string.ap_import_bad);
            }
        }));
    }

    private void appendPreview(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleButton(UiOptionItem.from(getContext().getString(R.string.ap_preview), optionItem -> showPreview()));
    }

    /** A small window with sample texts that use the same view ids as the real screens, so every option applies to them */
    private void showPreview() {
        if (!(getContext() instanceof Activity)) {
            return;
        }

        Activity activity = (Activity) getContext();
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, activity.getResources().getDisplayMetrics());
        root.setPadding(pad, pad, pad, pad);
        int bg = AppearancePrefs.getBackground(activity);
        root.setBackgroundColor(bg != 0 ? bg : 0xFF1E1E1E);

        addSample(root, "row_header", R.string.ap_prev_row, 20);
        addSample(root, "title_text", R.string.ap_prev_title, 16);
        addSample(root, "content_text", R.string.ap_prev_info, 13);
        addSample(root, "lb_action_button", R.string.ap_prev_button, 15);
        addSample(root, "current_time", R.string.ap_prev_time, 14);
        addSample(root, "quality_info", R.string.ap_prev_quality, 12);

        FontScaler.applyToTree(activity, root);

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(root);
        dialog.show();
    }

    private void addSample(LinearLayout root, String idName, int textRes, int sp) {
        TextView tv = new TextView(root.getContext());
        tv.setId(root.getResources().getIdentifier(idName, "id", root.getContext().getPackageName()));
        tv.setText(textRes);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setGravity(Gravity.START);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 12;
        root.addView(tv, lp);
    }

    private void appendReset(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleButton(UiOptionItem.from(getContext().getString(R.string.ap_reset), optionItem -> {
            resetAll();
            MessageHelpers.showMessage(getContext(), R.string.ap_reset_done);
        }));
    }

    private void resetAll() {
        AppearancePrefs.resetAll(getContext());
        for (Category category : Category.values()) {
            mMainUIData.setFontScale(category, 1.0f);
        }
    }

    private void setAllSizes(float scale) {
        for (Category category : Category.values()) {
            mMainUIData.setFontScale(category, scale);
        }
    }

    private void setAllColors(int color) {
        for (Category category : Category.values()) {
            AppearancePrefs.setTextColor(getContext(), category, color);
        }
    }

    private void applyPreset(int preset) {
        resetAll();

        switch (preset) {
            case 1: // large text
                setAllSizes(1.3f);
                break;
            case 2: // compact
                setAllSizes(0.85f);
                break;
            case 3: // high contrast
                setAllSizes(1.1f);
                setAllColors(0xFFFFFFFF);
                AppearancePrefs.setBackground(getContext(), 0xFF000000);
                for (Category category : new Category[] {Category.CARD_TITLE, Category.ROW_HEADER, Category.MENU_ITEM, Category.SETTINGS_ITEM}) {
                    AppearancePrefs.setBold(getContext(), category, true);
                }
                break;
            case 4: // warm night
                setAllColors(0xFFFFE0B2);
                AppearancePrefs.setBackground(getContext(), 0xFF1B1410);
                break;
            default: // 0: already reset
                break;
        }
    }
}
