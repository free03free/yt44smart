package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;

/**
 * Per-element font size. Every text on the screen is matched (by its view id) to a category,
 * and each category has its own multiplier (see MainUIData.getFontScale(Category)).
 * Works on top of the global font scale and applies immediately (no restart).
 */
public final class FontScaler {
    public enum Category {
    CARD_TITLE(R.string.font_cat_card_title),
    CARD_INFO(R.string.font_cat_card_info),
    ROW_HEADER(R.string.font_cat_row_header),
    MENU_ITEM(R.string.font_cat_menu_item),
    MENU_DESC(R.string.font_cat_menu_desc),
    DIALOG_TITLE(R.string.font_cat_dialog_title),
    BROWSE_TITLE(R.string.font_cat_browse_title),
    DETAILS(R.string.font_cat_details),
    PLAYER_TIME(R.string.font_cat_player_time),
    PLAYER_INFO(R.string.font_cat_player_info),
    BUTTONS(R.string.font_cat_buttons),
    SETTINGS_ITEM(R.string.font_cat_settings_item),
    SETTINGS_DESC(R.string.font_cat_settings_desc);

        public final int titleResId;

        Category(int titleResId) {
            this.titleResId = titleResId;
        }
    }

    private static final SparseArray<Object> sIdCache = new SparseArray<>();
    private static final Object NONE = new Object();

    private FontScaler() {
    }

    public static void apply(Context context, View root) {
        float[] scales = MainUIData.instance(context).getFontScales();
        walk(root, scales, false);
    }

    private static void walk(View v, float[] scales, boolean inTitleView) {
        if (v == null || v.getVisibility() == View.GONE && !(v instanceof ViewGroup)) {
            return;
        }

        if (v instanceof TextView) {
            applyToText((TextView) v, scales, inTitleView);
        }

        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            boolean title = inTitleView || "TitleView".equals(g.getClass().getSimpleName());
            for (int i = 0; i < g.getChildCount(); i++) {
                walk(g.getChildAt(i), scales, title);
            }
        }
    }

    private static void applyToText(TextView tv, float[] scales, boolean inTitleView) {
        // ResizingTextView resets its own size on every layout: touching it would loop
        if ("ResizingTextView".equals(tv.getClass().getSimpleName())) {
            return;
        }

        Category c = categoryOf(tv, inTitleView);
        if (c == null) {
            return;
        }

        float scale = scales[c.ordinal()];
        float cur = tv.getTextSize();
        float[] st = (float[]) tv.getTag(R.id.font_scaler_state);
        // If somebody else changed the size meanwhile, take it as the new original
        float orig = st != null && Math.abs(cur - st[1]) < 0.5f ? st[0] : cur;
        float target = orig * scale;

        if (st == null || st[0] != orig || st[1] != target) {
            tv.setTag(R.id.font_scaler_state, new float[] {orig, target});
        }

        if (Math.abs(cur - target) > 0.5f) {
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, target);
        }
    }

    private static Category categoryOf(TextView tv, boolean inTitleView) {
        int id = tv.getId();
        if (id == View.NO_ID) {
            return null;
        }

        String name = nameOf(tv, id);
        if (name == null) {
            return null;
        }

        switch (name) {
            case "title_text":
                return inTitleView ? Category.BROWSE_TITLE : Category.CARD_TITLE;
            case "channel_title":
                return Category.CARD_TITLE;
            case "content_text": case "extra_text_badge":
                return Category.CARD_INFO;
            case "row_header": case "row_header_description": case "header_label":
                return Category.ROW_HEADER;
            case "guidedactions_item_title": case "text_option_title":
                return Category.MENU_ITEM;
            case "settings_title": case "decor_title":
            case "title": // android.R.id.title: settings (preference) items and list-dialog items
                return Category.SETTINGS_ITEM;
            case "guidedactions_item_description": case "text_option_value": case "guidance_description":
                return Category.MENU_DESC;
            case "summary": case "seekbar_value": // android.R.id.summary: settings (preference) item descriptions
                return Category.SETTINGS_DESC;
            case "guidance_title": case "guidance_breadcrumb":
                return Category.DIALOG_TITLE;
            case "pip_title":
                return Category.BROWSE_TITLE;
            case "lb_details_description_title": case "lb_details_description_subtitle": case "lb_details_description_body":
                return Category.DETAILS;
            case "current_time": case "total_time": case "ending_time": case "separate_time": case "exo_position": case "exo_duration":
                return Category.PLAYER_TIME;
            case "quality_info": case "thumbs_row_title":
                return Category.PLAYER_INFO;
            case "lb_action_button": case "label": case "button": case "select_tracks_button":
                return Category.BUTTONS;
            default:
                return null;
        }
    }

    private static String nameOf(View v, int id) {
        Object cached = sIdCache.get(id);
        if (cached != null) {
            return cached == NONE ? null : (String) cached;
        }

        String name = null;
        try {
            name = v.getResources().getResourceEntryName(id);
        } catch (Exception e) {
            // not a resource id
        }
        sIdCache.put(id, name == null ? NONE : name);
        return name;
    }
}
