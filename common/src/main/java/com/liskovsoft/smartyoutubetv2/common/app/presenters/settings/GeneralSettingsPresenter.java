package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.okhttp.OkHttpManager;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.ContextMenuManager;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.ContextMenuProvider;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.LoadingSpinnerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MiniPlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PrefetchData;
import com.liskovsoft.smartyoutubetv2.common.prefs.NetworkData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.prefs.SearchData;
import com.liskovsoft.smartyoutubetv2.common.proxy.ProxyManager;
import com.liskovsoft.smartyoutubetv2.common.proxy.WebProxyDialog;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class GeneralSettingsPresenter extends BasePresenter<Void> {
    private final GeneralData mGeneralData;
    private final PlayerData mPlayerData;
    private final PlayerTweaksData mPlayerTweaksData;
    private final MainUIData mMainUIData;
    private final MediaServiceData mMediaServiceData;
    private final NetworkData mNetworkData;
    private final SidebarService mSidebarService;
    private boolean mRestartApp;
    private final Runnable mOnFinish = () -> {
        if (mRestartApp) {
            mRestartApp = false;
            MessageHelpers.showLongMessage(getContext(), R.string.msg_restart_app);
        }
    };

    private GeneralSettingsPresenter(Context context) {
        super(context);
        mGeneralData = GeneralData.instance(context);
        mPlayerData = PlayerData.instance(context);
        mPlayerTweaksData = PlayerTweaksData.instance(context);
        mMainUIData = MainUIData.instance(context);
        mMediaServiceData = MediaServiceData.instance();
        mNetworkData = NetworkData.instance(context);
        mSidebarService = SidebarService.instance(context);
    }

    public static GeneralSettingsPresenter instance(Context context) {
        return new GeneralSettingsPresenter(context);
    }

    public void show() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        appendBootToSection(settingsPresenter);
        appendEnabledSections(settingsPresenter);
        appendContextMenuItemsCategory(settingsPresenter);
        appendHideContent(settingsPresenter);
        appendAppExitCategory(settingsPresenter);
        appendMiniPlayerCategory(settingsPresenter);
        appendLoadingSpinnerCategory(settingsPresenter);
        appendBackgroundPlaybackCategory(settingsPresenter);
        appendScreenDimmingCategory(settingsPresenter);
        appendKeyRemappingCategory(settingsPresenter);
        appendInternetCensorship(settingsPresenter);
        appendHistoryCategory(settingsPresenter);
        appendMiscCategory(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.settings_general), mOnFinish);
    }

    private void appendEnabledSections(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        Map<Integer, Integer> sections = mSidebarService.getDefaultSections();

        for (Entry<Integer, Integer> section : sections.entrySet()) {
            int sectionResId = section.getKey();
            int sectionId = section.getValue();

            if (sectionId == MediaGroup.TYPE_SETTINGS) {
                continue;
            }

            options.add(UiOptionItem.from(getContext().getString(sectionResId), optionItem -> {
                BrowsePresenter.instance(getContext()).enableSection(sectionId, optionItem.isSelected());
            }, mSidebarService.isSectionPinned(sectionId)));
        }

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.side_panel_sections), options);
    }

    private void appendHideContent(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_shorts_everywhere),
                option -> {
                    mMediaServiceData.setContentHidden(MediaServiceData.CONTENT_SHORTS_ALL, option.isSelected());
                    BrowsePresenter.instance(getContext()).enableSection(MediaGroup.TYPE_SHORTS, !option.isSelected());
                },
                mMediaServiceData.isContentHidden(MediaServiceData.CONTENT_SHORTS_ALL)));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_mixes),
                option -> mMediaServiceData.setContentHidden(MediaServiceData.CONTENT_MIXES, option.isSelected()),
                mMediaServiceData.isContentHidden(MediaServiceData.CONTENT_MIXES)));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_watched_from_watch_later),
                option -> mMediaServiceData.setContentHidden(MediaServiceData.CONTENT_WATCHED_WATCH_LATER, option.isSelected()),
                mMediaServiceData.isContentHidden(MediaServiceData.CONTENT_WATCHED_WATCH_LATER)));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_watched_from_home),
                option -> mMediaServiceData.setContentHidden(MediaServiceData.CONTENT_WATCHED_HOME, option.isSelected()),
                mMediaServiceData.isContentHidden(MediaServiceData.CONTENT_WATCHED_HOME)));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_watched_from_subscriptions),
                option -> mMediaServiceData.setContentHidden(MediaServiceData.CONTENT_WATCHED_SUBSCRIPTIONS, option.isSelected()),
                mMediaServiceData.isContentHidden(MediaServiceData.CONTENT_WATCHED_SUBSCRIPTIONS)));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_watched_from_notifications),
                option -> mGeneralData.setHideWatchedFromNotificationsEnabled(option.isSelected()),
                mGeneralData.isHideWatchedFromNotificationsEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_shorts),
                option -> mMediaServiceData.setContentHidden(MediaServiceData.CONTENT_SHORTS_SUBSCRIPTIONS, option.isSelected()),
                mMediaServiceData.isContentHidden(MediaServiceData.CONTENT_SHORTS_SUBSCRIPTIONS)));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_shorts_from_search),
                option -> mMediaServiceData.setContentHidden(MediaServiceData.CONTENT_SHORTS_SEARCH, option.isSelected()),
                mMediaServiceData.isContentHidden(MediaServiceData.CONTENT_SHORTS_SEARCH)));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_shorts_from_home),
                option -> mMediaServiceData.setContentHidden(MediaServiceData.CONTENT_SHORTS_HOME, option.isSelected()),
                mMediaServiceData.isContentHidden(MediaServiceData.CONTENT_SHORTS_HOME)));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_shorts_channel),
                option -> mMediaServiceData.setContentHidden(MediaServiceData.CONTENT_SHORTS_CHANNEL, option.isSelected()),
                mMediaServiceData.isContentHidden(MediaServiceData.CONTENT_SHORTS_CHANNEL)));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_shorts_from_history),
                option -> mMediaServiceData.setContentHidden(MediaServiceData.CONTENT_SHORTS_HISTORY, option.isSelected()),
                mMediaServiceData.isContentHidden(MediaServiceData.CONTENT_SHORTS_HISTORY)));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_shorts_from_trending),
                option -> mMediaServiceData.setContentHidden(MediaServiceData.CONTENT_SHORTS_TRENDING, option.isSelected()),
                mMediaServiceData.isContentHidden(MediaServiceData.CONTENT_SHORTS_TRENDING)));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_streams),
                option -> mMediaServiceData.setContentHidden(MediaServiceData.CONTENT_STREAMS_SUBSCRIPTIONS, option.isSelected()),
                mMediaServiceData.isContentHidden(MediaServiceData.CONTENT_STREAMS_SUBSCRIPTIONS)));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_upcoming),
                option -> mMediaServiceData.setContentHidden(MediaServiceData.CONTENT_UPCOMING_SUBSCRIPTIONS, option.isSelected()),
                mMediaServiceData.isContentHidden(MediaServiceData.CONTENT_UPCOMING_SUBSCRIPTIONS)));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_upcoming_home),
                option -> mMediaServiceData.setContentHidden(MediaServiceData.CONTENT_UPCOMING_HOME, option.isSelected()),
                mMediaServiceData.isContentHidden(MediaServiceData.CONTENT_UPCOMING_HOME)));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_upcoming_channel),
                option -> mMediaServiceData.setContentHidden(MediaServiceData.CONTENT_UPCOMING_CHANNEL, option.isSelected()),
                mMediaServiceData.isContentHidden(MediaServiceData.CONTENT_UPCOMING_CHANNEL)));

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.hide_unwanted_content), options);
    }

    private void appendContextMenuItemsCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        Map<Long, Integer> menuNames = getMenuNames();

        for (Long menuItem : mMainUIData.getMenuItemsOrdered()) {
            Integer nameResId = menuNames.get(menuItem);

            if (nameResId == null) {
                continue;
            }

            options.add(UiOptionItem.from(getContext().getString(nameResId), optionItem -> {
                if (optionItem.isSelected()) {
                    mMainUIData.setMenuItemEnabled(menuItem);
                    showMenuItemOrderDialog(menuItem);
                } else {
                    mMainUIData.setMenuItemDisabled(menuItem);
                }
            }, mMainUIData.isMenuItemEnabled(menuItem)));
        }

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.context_menu), options);
    }

    private void appendContextMenuSortingCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        Map<Long, Integer> menuNames = getMenuNames();

        for (Long menuItem : mMainUIData.getMenuItemsOrdered()) {
            Integer nameResId = menuNames.get(menuItem);

            if (nameResId == null || !mMainUIData.isMenuItemEnabled(menuItem)) {
                continue;
            }

            options.add(UiOptionItem.from(getContext().getString(nameResId), optionItem ->
                    showMenuItemOrderDialog(menuItem), false));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.context_menu_sorting), options);
    }

    private void showMenuItemOrderDialog(Long menuItem) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(getContext());

        List<OptionItem> options = new ArrayList<>();

        Map<Long, Integer> menuNames = getMenuNames();

        Integer currentNameResId = menuNames.get(menuItem);

        if (currentNameResId == null) {
            return;
        }

        List<Long> menuItemsOrdered = mMainUIData.getMenuItemsOrdered();
        int size = menuItemsOrdered.size();
        int currentIndex = mMainUIData.getMenuItemIndex(menuItem);
        int counter = 0;

        for (int i = 0; i < size; i++) {
            Long item = menuItemsOrdered.get(i);
            Integer nameResId = menuNames.get(item);

            if (nameResId == null || !mMainUIData.isMenuItemEnabled(item)) {
                continue;
            }

            final int index = i;
            options.add(UiOptionItem.from((counter + 1) + " " + getContext().getString(nameResId), optionItem -> {
                if (optionItem.isSelected()) {
                    mMainUIData.setMenuItemIndex(index, menuItem);
                    dialog.goBack();
                }
            }, currentIndex == i));
            counter++;
        }

        String itemName = getContext().getString(currentNameResId);
        dialog.appendRadioCategory(getContext().getString(R.string.item_postion) + " " + itemName, options);

        dialog.showDialog();
    }

    private void appendBootToSection(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        Map<Integer, Integer> sections = mSidebarService.getDefaultSections();

        for (Entry<Integer, Integer> section : sections.entrySet()) {
            options.add(
                    UiOptionItem.from(
                            getContext().getString(section.getKey()),
                            optionItem -> mSidebarService.setBootSectionId(section.getValue()),
                            section.getValue().equals(mSidebarService.getBootSectionId())
                    )
            );
        }

        Collection<Video> pinnedItems = mSidebarService.getPinnedItems();

        for (Video item : pinnedItems) {
            if (item != null && item.getTitle() != null) {
                options.add(
                        UiOptionItem.from(
                                item.getTitle(),
                                optionItem -> mSidebarService.setBootSectionId(item.getId()),
                                item.hashCode() == mSidebarService.getBootSectionId()
                        )
                );
            }
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.boot_to_section), options);
    }

    private void appendAppExitCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.app_exit_none, GeneralData.EXIT_NONE},
                {R.string.app_double_back_exit, GeneralData.EXIT_DOUBLE_BACK},
                {R.string.app_single_back_exit, GeneralData.EXIT_SINGLE_BACK}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]),
                    optionItem -> mGeneralData.setAppExitShortcut(pair[1]),
                    mGeneralData.getAppExitShortcut() == pair[1]));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.app_exit_shortcut), options);
    }

    /**
     * In-app mini player: Back button inside the player shrinks the video into a small corner window
     * and returns to browsing. Position and size are configurable.
     */
    private void appendMiniPlayerCategory(AppDialogPresenter settingsPresenter) {
        MiniPlayerData data = MiniPlayerData.instance(getContext());

        settingsPresenter.appendSingleSwitch(UiOptionItem.from(
                getContext().getString(R.string.mini_player_title),
                option -> data.setEnabled(option.isSelected()),
                data.isEnabled()));

        settingsPresenter.appendSingleSwitch(UiOptionItem.from(
                getContext().getString(R.string.mini_player_play_in_mini),
                option -> data.setPlayInMiniEnabled(option.isSelected()),
                data.isPlayInMiniEnabled()));

        List<OptionItem> switchModes = new ArrayList<>();
        switchModes.add(UiOptionItem.from(getContext().getString(R.string.mini_player_switch_current),
                option -> data.setSeamlessEnabled(false), !data.isSeamlessEnabled()));
        switchModes.add(UiOptionItem.from(getContext().getString(R.string.mini_player_switch_full),
                option -> data.setSeamlessEnabled(true), data.isSeamlessEnabled()));
        settingsPresenter.appendRadioCategory(getContext().getString(R.string.mini_player_switch_mode), switchModes);

        List<OptionItem> positions = new ArrayList<>();
        int[] positionIds = {MiniPlayerData.POSITION_BOTTOM_RIGHT, MiniPlayerData.POSITION_BOTTOM_LEFT,
                MiniPlayerData.POSITION_TOP_RIGHT, MiniPlayerData.POSITION_TOP_LEFT};
        int[] positionTitles = {R.string.mini_player_pos_bottom_right, R.string.mini_player_pos_bottom_left,
                R.string.mini_player_pos_top_right, R.string.mini_player_pos_top_left};

        for (int i = 0; i < positionIds.length; i++) {
            int id = positionIds[i];
            positions.add(UiOptionItem.from(getContext().getString(positionTitles[i]),
                    option -> data.setPosition(id), data.getPosition() == id));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.mini_player_position), positions);

        List<OptionItem> sizes = new ArrayList<>();

        for (int percent = MiniPlayerData.MIN_SIZE_PERCENT; percent <= MiniPlayerData.MAX_SIZE_PERCENT;
             percent += MiniPlayerData.SIZE_STEP_PERCENT) {
            int value = percent;
            sizes.add(UiOptionItem.from(value + "%",
                    option -> data.setSizePercent(value), data.getSizePercent() == value));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.mini_player_size), sizes);

        PrefetchData prefetch = PrefetchData.instance(getContext());
        List<OptionItem> prefetchOptions = new ArrayList<>();
        int[] prefetchIds = {PrefetchData.MODE_OFF, PrefetchData.MODE_NORMAL, PrefetchData.MODE_MORE};
        int[] prefetchTitles = {R.string.prefetch_off, R.string.prefetch_normal, R.string.prefetch_more};

        for (int i = 0; i < prefetchIds.length; i++) {
            int id = prefetchIds[i];
            prefetchOptions.add(UiOptionItem.from(getContext().getString(prefetchTitles[i]),
                    option -> prefetch.setMode(id), prefetch.getMode() == id));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.prefetch_title), prefetchOptions);

        appendCacheMinutes(settingsPresenter, prefetch);
        appendCacheSections(settingsPresenter, prefetch);

        settingsPresenter.appendSingleSwitch(UiOptionItem.from(
                getContext().getString(R.string.refresh_on_enter_title),
                option -> prefetch.setRefreshOnEnterEnabled(option.isSelected()),
                prefetch.isRefreshOnEnterEnabled()));

        List<OptionItem> topBar = new ArrayList<>();
        int[] topBarIds = {MiniPlayerData.TOP_BAR_SHOW, MiniPlayerData.TOP_BAR_HIDE_WITH_MINI, MiniPlayerData.TOP_BAR_HIDE_ALWAYS};
        int[] topBarTitles = {R.string.mini_player_top_bar_show, R.string.mini_player_top_bar_hide_with_mini,
                R.string.mini_player_top_bar_hide_always};

        for (int i = 0; i < topBarIds.length; i++) {
            int id = topBarIds[i];
            topBar.add(UiOptionItem.from(getContext().getString(topBarTitles[i]),
                    option -> data.setTopBarMode(id), data.getTopBarMode() == id));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.mini_player_top_bar), topBar);

        List<OptionItem> qualities = new ArrayList<>();

        for (int height : MiniPlayerData.QUALITIES) {
            qualities.add(UiOptionItem.from(height + "p",
                    option -> data.setQuality(height), data.getQuality() == height));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.mini_player_quality), qualities);
    }

    /**
     * Style of the loading indicator (video loading, screens loading).
     */
    private String cacheMinutesTitle(int minutes) {
        if (minutes == 0) {
            return getContext().getString(R.string.section_cache_off);
        } else if (minutes < 0) {
            return getContext().getString(R.string.section_cache_forever);
        } else if (minutes >= 60 && minutes % 60 == 0) {
            return getContext().getString(R.string.section_cache_hours, minutes / 60);
        }

        return getContext().getString(R.string.section_cache_minutes, minutes);
    }

    private void appendCacheMinutes(AppDialogPresenter dialog, PrefetchData prefetch) {
        final int current = prefetch.getSectionCacheMinutes();
        boolean isPreset = false;
        List<OptionItem> options = new ArrayList<>();

        for (int minutes : PrefetchData.CACHE_MINUTES) {
            if (minutes == current) {
                isPreset = true;
            }

            options.add(UiOptionItem.from(cacheMinutesTitle(minutes),
                    option -> prefetch.setSectionCacheMinutes(minutes), minutes == current));
        }

        String customTitle = isPreset ? getContext().getString(R.string.library_custom)
                : getContext().getString(R.string.section_cache_custom_current, cacheMinutesTitle(current));

        options.add(UiOptionItem.from(customTitle, option -> {
            dialog.closeDialog();
            SimpleEditDialog.show(getContext(), getContext().getString(R.string.section_cache_title),
                    getContext().getString(R.string.section_cache_custom_minutes_prompt),
                    current > 0 ? String.valueOf(current) : "", value -> {
                        int number = parseInRange(value, PrefetchData.MAX_CACHE_MINUTES);

                        if (number <= 0) {
                            MessageHelpers.showMessage(getContext(), getContext().getString(R.string.section_cache_invalid_minutes));
                            return false;
                        }

                        prefetch.setSectionCacheMinutes(number);
                        show();
                        return true;
                    });
        }, !isPreset));

        dialog.appendRadioCategory(getContext().getString(R.string.section_cache_title), options);
    }

    private void appendCacheSections(AppDialogPresenter dialog, PrefetchData prefetch) {
        final int current = prefetch.getSectionCacheSections();
        boolean isPreset = false;
        List<OptionItem> options = new ArrayList<>();

        for (int count : PrefetchData.CACHE_SECTIONS) {
            if (count == current) {
                isPreset = true;
            }

            String title = count == 0 ? getContext().getString(R.string.section_cache_unlimited)
                    : getContext().getString(R.string.section_cache_count, count);
            options.add(UiOptionItem.from(title,
                    option -> prefetch.setSectionCacheSections(count), count == current));
        }

        String customTitle = isPreset ? getContext().getString(R.string.library_custom)
                : getContext().getString(R.string.section_cache_custom_current,
                        getContext().getString(R.string.section_cache_count, current));

        options.add(UiOptionItem.from(customTitle, option -> {
            dialog.closeDialog();
            SimpleEditDialog.show(getContext(), getContext().getString(R.string.section_cache_count_title),
                    getContext().getString(R.string.section_cache_custom_count_prompt),
                    current > 0 ? String.valueOf(current) : "", value -> {
                        int number = parseInRange(value, PrefetchData.MAX_CACHE_SECTIONS);

                        if (number <= 0) {
                            MessageHelpers.showMessage(getContext(), getContext().getString(R.string.section_cache_invalid_count));
                            return false;
                        }

                        prefetch.setSectionCacheSections(number);
                        show();
                        return true;
                    });
        }, !isPreset));

        dialog.appendRadioCategory(getContext().getString(R.string.section_cache_count_title), options);
    }

    /** @return the number or -1 when it is not a whole number in 1..max */
    private static int parseInRange(String value, int max) {
        if (value == null) {
            return -1;
        }

        try {
            long number = Long.parseLong(value.trim());
            return (number <= 0 || number > max) ? -1 : (int) number;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private void appendLoadingSpinnerCategory(AppDialogPresenter settingsPresenter) {
        LoadingSpinnerData data = LoadingSpinnerData.instance(getContext());

        int[] titles = {
                R.string.loading_spinner_style_0, R.string.loading_spinner_style_1, R.string.loading_spinner_style_2,
                R.string.loading_spinner_style_3, R.string.loading_spinner_style_4, R.string.loading_spinner_style_5,
                R.string.loading_spinner_style_6, R.string.loading_spinner_style_7, R.string.loading_spinner_style_8,
                R.string.loading_spinner_style_9, R.string.loading_spinner_style_10, R.string.loading_spinner_style_11
        };

        List<OptionItem> options = new ArrayList<>();

        for (int i = 0; i < titles.length; i++) {
            int style = i;
            options.add(UiOptionItem.from(getContext().getString(titles[i]),
                    option -> data.setStyle(style), data.getStyle() == style));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.loading_spinner_title), options);

        int[] colorTitles = {
                R.string.loading_spinner_color_0, R.string.loading_spinner_color_1, R.string.loading_spinner_color_2,
                R.string.loading_spinner_color_3, R.string.loading_spinner_color_4, R.string.loading_spinner_color_5,
                R.string.loading_spinner_color_6, R.string.loading_spinner_color_7, R.string.loading_spinner_color_8,
                R.string.loading_spinner_color_9
        };

        List<OptionItem> colors = new ArrayList<>();

        for (int i = 0; i < colorTitles.length; i++) {
            int index = i;
            colors.add(UiOptionItem.from(getContext().getString(colorTitles[i]),
                    option -> data.setColorIndex(index), data.getColorIndex() == index));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.loading_spinner_color), colors);

        List<OptionItem> sizes = new ArrayList<>();

        for (int dp = LoadingSpinnerData.MIN_SIZE_DP; dp <= LoadingSpinnerData.MAX_SIZE_DP; dp += LoadingSpinnerData.SIZE_STEP_DP) {
            int value = dp;
            sizes.add(UiOptionItem.from(value + " dp",
                    option -> data.setSizeDp(value), data.getSizeDp() == value));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.loading_spinner_size), sizes);
    }

    private void appendBackgroundPlaybackCategory(AppDialogPresenter settingsPresenter) {
        OptionCategory category = AppDialogUtil.createBackgroundPlaybackCategory(getContext(), mPlayerData, mGeneralData);
        settingsPresenter.appendRadioCategory(category.title, category.options);
    }

    private void appendKeyRemappingCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from("OK -> " + getContext().getString(R.string.player_toggle_speed),
                option -> mPlayerData.setOKButtonBehavior(option.isSelected() ? PlayerData.OK_TOGGLE_SPEED : PlayerData.OK_ONLY_UI),
                mPlayerData.getOKButtonBehavior() == PlayerData.OK_TOGGLE_SPEED));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_quick_shorts_skip_alt),
                option -> {
                    mPlayerTweaksData.setQuickSkipShortsAltEnabled(option.isSelected());
                    mGeneralData.setDpadUpDownAction(GeneralData.ACTION_UNDEFINED);
                },
                mPlayerTweaksData.isQuickSkipShortsAltEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_quick_shorts_skip),
                option -> {
                    mPlayerTweaksData.setQuickSkipShortsEnabled(option.isSelected());
                    mGeneralData.resetDpadLeftRightSettings();
                },
                mPlayerTweaksData.isQuickSkipShortsEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_quick_skip_videos_alt),
                option -> {
                    mPlayerTweaksData.setQuickSkipVideosAltEnabled(option.isSelected());
                    mGeneralData.setDpadUpDownAction(GeneralData.ACTION_UNDEFINED);
                },
                mPlayerTweaksData.isQuickSkipVideosAltEnabled()));
        
        options.add(UiOptionItem.from(getContext().getString(R.string.player_quick_skip_videos),
                option -> {
                    mPlayerTweaksData.setQuickSkipVideosEnabled(option.isSelected());
                    mGeneralData.resetDpadLeftRightSettings();
                },
                mPlayerTweaksData.isQuickSkipVideosEnabled()));

        options.add(UiOptionItem.from("Play/Pause -> OK",
                option -> mGeneralData.setRemapPlayToOKEnabled(option.isSelected()),
                mGeneralData.isRemapPlayToOKEnabled()));

        options.add(UiOptionItem.from("DPAD RIGHT/LEFT -> Volume Up/Down",
                option -> {
                    mGeneralData.setRemapDpadLeftToVolumeEnabled(option.isSelected());
                    mPlayerTweaksData.resetDpadLeftRightSettings();
                },
                mGeneralData.isRemapDpadLeftToVolumeEnabled()));

        options.add(UiOptionItem.from("DPAD UP/DOWN -> Volume Up/Down",
                option -> {
                    mGeneralData.setDpadUpDownAction(option.isSelected() ? GeneralData.ACTION_VOLUME_UP_DOWN : GeneralData.ACTION_UNDEFINED);
                    mPlayerTweaksData.resetDpadUpDownSettings();
                },
                mGeneralData.getDpadUpDownAction() == GeneralData.ACTION_VOLUME_UP_DOWN));

        options.add(UiOptionItem.from("DPAD UP/DOWN -> Speed Up/Down",
                option -> {
                    mGeneralData.setDpadUpDownAction(option.isSelected() ? GeneralData.ACTION_SPEED_UP_DOWN : GeneralData.ACTION_UNDEFINED);
                    mPlayerTweaksData.resetDpadUpDownSettings();
                },
                mGeneralData.getDpadUpDownAction() == GeneralData.ACTION_SPEED_UP_DOWN));

        options.add(UiOptionItem.from("Numbers 3/1 -> Speed Up/Down",
                option -> mGeneralData.setRemapNumbersToSpeedEnabled(option.isSelected()),
                mGeneralData.isRemapNumbersToSpeedEnabled()));

        options.add(UiOptionItem.from("Next/Previous -> Fast Forward/Rewind",
                option -> mGeneralData.setNextPreviousAction(option.isSelected() ? GeneralData.ACTION_FAST_FORWARD_REWIND : GeneralData.ACTION_UNDEFINED),
                mGeneralData.getNextPreviousAction() == GeneralData.ACTION_FAST_FORWARD_REWIND));

        options.add(UiOptionItem.from("Next/Previous -> Speed Up/Down",
                option -> mGeneralData.setNextPreviousAction(option.isSelected() ? GeneralData.ACTION_SPEED_UP_DOWN : GeneralData.ACTION_UNDEFINED),
                mGeneralData.getNextPreviousAction() == GeneralData.ACTION_SPEED_UP_DOWN));

        options.add(UiOptionItem.from("Fast Forward/Rewind -> Next/Previous",
                option -> mGeneralData.setFastForwardRewindAction(option.isSelected() ? GeneralData.ACTION_NEXT_PREVIOUS : GeneralData.ACTION_UNDEFINED),
                mGeneralData.getFastForwardRewindAction() == GeneralData.ACTION_NEXT_PREVIOUS));

        options.add(UiOptionItem.from("Fast Forward/Rewind -> Speed Up/Down",
                option -> mGeneralData.setFastForwardRewindAction(option.isSelected() ? GeneralData.ACTION_SPEED_UP_DOWN : GeneralData.ACTION_UNDEFINED),
                mGeneralData.getFastForwardRewindAction() == GeneralData.ACTION_SPEED_UP_DOWN));

        options.add(UiOptionItem.from("Fast Forward/Rewind -> Speed Toggle",
                option -> mGeneralData.setFastForwardRewindAction(option.isSelected() ? GeneralData.ACTION_SPEED_TOGGLE : GeneralData.ACTION_UNDEFINED),
                mGeneralData.getFastForwardRewindAction() == GeneralData.ACTION_SPEED_TOGGLE));

        options.add(UiOptionItem.from("S -> Speed Toggle",
                option -> mGeneralData.setRemapSToSpeedToggleEnabled(option.isSelected()),
                mGeneralData.isRemapSToSpeedToggleEnabled()));

        options.add(UiOptionItem.from("Page Up/Down -> Next/Previous",
                option -> mGeneralData.setPageUpDownAction(option.isSelected() ? GeneralData.ACTION_NEXT_PREVIOUS : GeneralData.ACTION_UNDEFINED),
                mGeneralData.getPageUpDownAction() == GeneralData.ACTION_NEXT_PREVIOUS));

        options.add(UiOptionItem.from("Page Up/Down -> Like/Dislike",
                option -> mGeneralData.setPageUpDownAction(option.isSelected() ? GeneralData.ACTION_LIKE_DISLIKE : GeneralData.ACTION_UNDEFINED),
                mGeneralData.getPageUpDownAction() == GeneralData.ACTION_LIKE_DISLIKE));

        options.add(UiOptionItem.from("Page Up/Down -> Speed Up/Down",
                option -> mGeneralData.setPageUpDownAction(option.isSelected() ? GeneralData.ACTION_SPEED_UP_DOWN : GeneralData.ACTION_UNDEFINED),
                mGeneralData.getPageUpDownAction() == GeneralData.ACTION_SPEED_UP_DOWN));
        
        options.add(UiOptionItem.from("Page Up/Down -> Speed Down/Up",
                option -> mGeneralData.setPageUpDownAction(option.isSelected() ? GeneralData.ACTION_SPEED_DOWN_UP : GeneralData.ACTION_UNDEFINED),
                mGeneralData.getPageUpDownAction() == GeneralData.ACTION_SPEED_DOWN_UP));

        options.add(UiOptionItem.from("Channel Up/Down -> Volume Up/Down",
                option -> mGeneralData.setChannelUpDownAction(option.isSelected() ? GeneralData.ACTION_VOLUME_UP_DOWN : GeneralData.ACTION_UNDEFINED),
                mGeneralData.getChannelUpDownAction() == GeneralData.ACTION_VOLUME_UP_DOWN));

        options.add(UiOptionItem.from("Channel Up/Down -> Next/Previous",
                option -> mGeneralData.setChannelUpDownAction(option.isSelected() ? GeneralData.ACTION_NEXT_PREVIOUS : GeneralData.ACTION_UNDEFINED),
                mGeneralData.getChannelUpDownAction() == GeneralData.ACTION_NEXT_PREVIOUS));

        options.add(UiOptionItem.from("Channel Up/Down -> Like/Dislike",
                option -> mGeneralData.setChannelUpDownAction(option.isSelected() ? GeneralData.ACTION_LIKE_DISLIKE : GeneralData.ACTION_UNDEFINED),
                mGeneralData.getChannelUpDownAction() == GeneralData.ACTION_LIKE_DISLIKE));

        options.add(UiOptionItem.from("Channel Up/Down -> Speed Up/Down",
                option -> mGeneralData.setChannelUpDownAction(option.isSelected() ? GeneralData.ACTION_SPEED_UP_DOWN : GeneralData.ACTION_UNDEFINED),
                mGeneralData.getChannelUpDownAction() == GeneralData.ACTION_SPEED_UP_DOWN));

        options.add(UiOptionItem.from("Channel Up/Down -> Speed Toggle",
                option -> mGeneralData.setChannelUpDownAction(option.isSelected() ? GeneralData.ACTION_SPEED_TOGGLE : GeneralData.ACTION_UNDEFINED),
                mGeneralData.getChannelUpDownAction() == GeneralData.ACTION_SPEED_TOGGLE));

        options.add(UiOptionItem.from("Channel Up/Down -> Search",
                option -> mGeneralData.setChannelUpDownAction(option.isSelected() ? GeneralData.ACTION_SEARCH : GeneralData.ACTION_UNDEFINED),
                mGeneralData.getChannelUpDownAction() == GeneralData.ACTION_SEARCH));

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.key_remapping), options);
    }

    private void appendScreenDimmingCategory(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleButton(UiOptionItem.from(getContext().getString(R.string.screen_dimming), optionItem -> {
            AppDialogPresenter presenter = AppDialogPresenter.instance(getContext());
            appendScreenDimmingAmountCategory(presenter);
            appendScreenDimmingTimeoutCategory(presenter);
            presenter.showDialog(getContext().getString(R.string.screen_dimming));
        }));
    }

    private void appendScreenDimmingAmountCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        int activeMode = mGeneralData.getScreensaverDimmingPercents();

        for (int dimPercents : Helpers.range(10, 80, 10)) {
            options.add(UiOptionItem.from(
                    dimPercents + "%",
                    option -> mGeneralData.setScreensaverDimmingPercents(dimPercents),
                    activeMode == dimPercents));
        }

        for (int dimPercents : Helpers.range(85, 100, 5)) {
            options.add(UiOptionItem.from(
                    dimPercents + "%",
                    option -> mGeneralData.setScreensaverDimmingPercents(dimPercents),
                    activeMode == dimPercents));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.screen_dimming_amount), options);
    }

    @SuppressLint("StringFormatMatches")
    private void appendScreenDimmingTimeoutCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        int screensaverTimeoutMs = mGeneralData.getScreensaverTimeoutMs();

        options.add(UiOptionItem.from(
                getContext().getString(R.string.option_never),
                option -> mGeneralData.setScreensaverTimeoutMs(GeneralData.SCREENSAVER_TIMEOUT_NEVER),
                screensaverTimeoutMs == GeneralData.SCREENSAVER_TIMEOUT_NEVER));

        for (int timeoutSec : new int[] {5, 15, 30}) {
            int timeoutMs = timeoutSec * 1_000;
            options.add(UiOptionItem.from(
                    getContext().getString(R.string.ui_hide_timeout_sec, timeoutSec),
                    option -> mGeneralData.setScreensaverTimeoutMs(timeoutMs),
                    screensaverTimeoutMs == timeoutMs));
        }

        for (int i = 1; i <= 15; i++) {
            int timeoutMs = i * 60 * 1_000;
            options.add(UiOptionItem.from(
                    getContext().getString(R.string.screen_dimming_timeout_min, i),
                    option -> mGeneralData.setScreensaverTimeoutMs(timeoutMs),
                    screensaverTimeoutMs == timeoutMs));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.screen_dimming_timeout), options);
    }

    //private void appendTimeFormatCategory(AppDialogPresenter settingsPresenter) {
    //    List<OptionItem> options = new ArrayList<>();
    //
    //    options.add(UiOptionItem.from(
    //            getContext().getString(R.string.time_format_24),
    //            option -> {
    //                mGeneralData.setTimeFormat(GeneralData.TIME_FORMAT_24);
    //                mRestartApp = true;
    //            },
    //            mGeneralData.getTimeFormat() == GeneralData.TIME_FORMAT_24));
    //
    //    options.add(UiOptionItem.from(
    //            getContext().getString(R.string.time_format_12),
    //            option -> {
    //                mGeneralData.setTimeFormat(GeneralData.TIME_FORMAT_12);
    //                mRestartApp = true;
    //            },
    //            mGeneralData.getTimeFormat() == GeneralData.TIME_FORMAT_12));
    //
    //    settingsPresenter.appendRadioCategory(getContext().getString(R.string.time_format), options);
    //}

    private void appendHistoryCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.auto_history, GeneralData.HISTORY_AUTO},
                {R.string.enable_history, GeneralData.HISTORY_ENABLED},
                {R.string.disable_history, GeneralData.HISTORY_DISABLED}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]), optionItem -> {
                mGeneralData.setHistoryState(pair[1]);
                MediaServiceManager.instance().enableHistory(pair[1] == GeneralData.HISTORY_AUTO || pair[1] == GeneralData.HISTORY_ENABLED);
            }, mGeneralData.getHistoryState() == pair[1]));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.header_history), options);
    }

    private void appendMiscCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.disable_screensaver),
                option -> mGeneralData.setScreensaverDisabled(option.isSelected()),
                mGeneralData.isScreensaverDisabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.multi_profiles),
                option -> {
                    AppPrefs.instance(getContext()).enableMultiProfiles(option.isSelected());
                    BrowsePresenter.instance(getContext()).updateSections();
                },
                AppPrefs.instance(getContext()).isMultiProfilesEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.protect_settings_with_password),
                option -> {
                    if (option.isSelected()) {
                        showPasswordDialog(settingsPresenter, null);
                    } else {
                        mGeneralData.setSettingsPassword(null);
                    }
                },
                mGeneralData.getSettingsPassword() != null));

        options.add(UiOptionItem.from(getContext().getString(R.string.enable_master_password),
                option -> {
                    if (option.isSelected()) {
                        showMasterPasswordDialog(settingsPresenter, null);
                    } else {
                        mGeneralData.setMasterPassword(null);
                    }
                },
                mGeneralData.getMasterPassword() != null));

        options.add(UiOptionItem.from(getContext().getString(R.string.child_mode),
                getContext().getString(R.string.child_mode_desc),
                option -> {
                    if (option.isSelected()) {
                        AppDialogUtil.showConfirmationDialog(getContext(), getContext().getString(R.string.lost_setting_warning),
                                () -> showPasswordDialog(settingsPresenter, () -> enableChildMode(option.isSelected())),
                                settingsPresenter::closeDialog);
                    } else {
                        mGeneralData.setSettingsPassword(null);
                        enableChildMode(option.isSelected());
                        settingsPresenter.closeDialog();
                    }
                },
                mGeneralData.isChildModeEnabled()));

        // Disable long press on buggy controllers.
        options.add(UiOptionItem.from(getContext().getString(R.string.disable_ok_long_press),
                getContext().getString(R.string.disable_ok_long_press_desc),
                option -> mGeneralData.setOkButtonLongPressDisabled(option.isSelected()),
                mGeneralData.isOkButtonLongPressDisabled()));

        options.add(UiOptionItem.from( getContext().getString(R.string.player_exit_shortcut) + ": " + getContext().getString(R.string.app_double_back_exit),
                option -> mGeneralData.setPlayerExitShortcut(option.isSelected() ? GeneralData.EXIT_DOUBLE_BACK : GeneralData.EXIT_SINGLE_BACK),
                mGeneralData.getPlayerExitShortcut() == GeneralData.EXIT_DOUBLE_BACK));

        options.add(UiOptionItem.from(getContext().getString(R.string.search_exit_shortcut) + ": " + getContext().getString(R.string.app_double_back_exit),
                option -> mGeneralData.setSearchExitShortcut(option.isSelected() ? GeneralData.EXIT_DOUBLE_BACK : GeneralData.EXIT_SINGLE_BACK),
                mGeneralData.getSearchExitShortcut() == GeneralData.EXIT_DOUBLE_BACK));

        options.add(UiOptionItem.from(getContext().getString(R.string.return_to_launcher),
                option -> mGeneralData.setReturnToLauncherEnabled(option.isSelected()),
                mGeneralData.isReturnToLauncherEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.remember_position_subscriptions),
                option -> mGeneralData.setRememberSubscriptionsPositionEnabled(option.isSelected()),
                mGeneralData.isRememberSubscriptionsPositionEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.remember_position_pinned),
                option -> mGeneralData.setRememberPinnedPositionEnabled(option.isSelected()),
                mGeneralData.isRememberPinnedPositionEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.select_channel_section),
                option -> mGeneralData.setSelectChannelSectionEnabled(option.isSelected()),
                mGeneralData.isSelectChannelSectionEnabled()));

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.player_other), options);
    }

    private void appendInternetCensorship(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        appendProxyManager(settingsPresenter, options);

        appendConscrypt(settingsPresenter, options);

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.network_settings), options);
    }

    private void appendProxyManager(AppDialogPresenter settingsPresenter, List<OptionItem> options) {
        ProxyManager proxyManager = new ProxyManager(getContext());

        if (proxyManager.isProxySupported()) {
            options.add(UiOptionItem.from(getContext().getString(R.string.enable_web_proxy),
                    option -> {
                        // Proxy with authentication supported only by OkHttp
                        mPlayerTweaksData.setPlayerDataSource(
                                option.isSelected() ? PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP : PlayerTweaksData.PLAYER_DATA_SOURCE_CRONET);
                        mGeneralData.setProxyEnabled(option.isSelected());
                        new WebProxyDialog(getContext()).enable(option.isSelected());
                        if (option.isSelected()) {
                            settingsPresenter.closeDialog();
                        }

                        OkHttpManager.unhold();
                    },
                    mGeneralData.isProxyEnabled()));
        }
    }

    private void appendConscrypt(AppDialogPresenter settingsPresenter, List<OptionItem> options) {
        options.add(UiOptionItem.from(getContext().getString(R.string.enable_conscrypt),
                getContext().getString(R.string.enable_conscrypt_desc),
                option -> {
                    mNetworkData.setConscryptEnabled(option.isSelected());
                    mRestartApp = true;
                },
                mNetworkData.isConscryptEnabled()));
    }

    private void enableChildMode(boolean enable) {
        mGeneralData.setChildModeEnabled(enable);

        int topButtons = MainUIData.TOP_BUTTON_BROWSE_ACCOUNTS;
        int playerButtons = PlayerTweaksData.PLAYER_BUTTON_PLAY_PAUSE | PlayerTweaksData.PLAYER_BUTTON_NEXT | PlayerTweaksData.PLAYER_BUTTON_PREVIOUS |
                    PlayerTweaksData.PLAYER_BUTTON_DISLIKE | PlayerTweaksData.PLAYER_BUTTON_LIKE | PlayerTweaksData.PLAYER_BUTTON_SCREEN_DIMMING |
                    PlayerTweaksData.PLAYER_BUTTON_SEEK_INTERVAL | PlayerTweaksData.PLAYER_BUTTON_PLAYBACK_QUEUE | PlayerTweaksData.PLAYER_BUTTON_OPEN_CHANNEL |
                    PlayerTweaksData.PLAYER_BUTTON_PIP | PlayerTweaksData.PLAYER_BUTTON_VIDEO_SPEED | PlayerTweaksData.PLAYER_BUTTON_SUBTITLES |
                    PlayerTweaksData.PLAYER_BUTTON_VIDEO_ZOOM | PlayerTweaksData.PLAYER_BUTTON_ADD_TO_PLAYLIST;
        long menuItems = MainUIData.MENU_ITEM_SHOW_QUEUE | MainUIData.MENU_ITEM_ADD_TO_QUEUE | MainUIData.MENU_ITEM_PLAY_NEXT |
                    MainUIData.MENU_ITEM_SELECT_ACCOUNT | MainUIData.MENU_ITEM_STREAM_REMINDER | MainUIData.MENU_ITEM_SAVE_REMOVE_PLAYLIST;

        PlayerTweaksData tweaksData = PlayerTweaksData.instance(getContext());
        SearchData searchData = SearchData.instance(getContext());

        // Remove all
        mMainUIData.setTopButtonDisabled(Integer.MAX_VALUE);
        tweaksData.setPlayerButtonDisabled(Integer.MAX_VALUE);
        mMainUIData.setMenuItemDisabled(Integer.MAX_VALUE);
        BrowsePresenter.instance(getContext()).enableAllSections(false);
        searchData.setPopularSearchesDisabled(true);

        if (enable) {
            // apply child tweaks
            mMainUIData.setTopButtonEnabled(topButtons);
            tweaksData.setPlayerButtonEnabled(playerButtons);
            mMainUIData.setMenuItemEnabled(menuItems);
            mPlayerData.setPlaybackMode(PlayerConstants.PLAYBACK_MODE_LIST);
            BrowsePresenter.instance(getContext()).enableSection(MediaGroup.TYPE_HISTORY, true);
            BrowsePresenter.instance(getContext()).enableSection(MediaGroup.TYPE_USER_PLAYLISTS, true);
            BrowsePresenter.instance(getContext()).enableSection(MediaGroup.TYPE_SUBSCRIPTIONS, true);
            BrowsePresenter.instance(getContext()).enableSection(MediaGroup.TYPE_CHANNEL_UPLOADS, true);
        } else {
            // apply default tweaks
            mMainUIData.setTopButtonEnabled(MainUIData.TOP_BUTTON_DEFAULT);
            tweaksData.setPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_DEFAULT);
            mMainUIData.setMenuItemEnabled(MainUIData.MENU_ITEM_DEFAULT);
            BrowsePresenter.instance(getContext()).enableAllSections(true);
            tweaksData.setSuggestionsDisabled(false);
            mPlayerData.setPlaybackMode(PlayerConstants.PLAYBACK_MODE_ALL);
            searchData.setPopularSearchesDisabled(false);
        }
    }

    private void showPasswordDialog(AppDialogPresenter settingsPresenter, Runnable onSuccess) {
        if (mGeneralData.getSettingsPassword() != null) {
            if (onSuccess != null) {
                onSuccess.run();
            }
            return;
        }

        settingsPresenter.closeDialog();
        SimpleEditDialog.showPassword(
                getContext(),
                getContext().getString(R.string.protect_settings_with_password),
                null,
                newValue -> {
                    mGeneralData.setSettingsPassword(newValue);
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                    return true;
                });
    }

    private void showMasterPasswordDialog(AppDialogPresenter settingsPresenter, Runnable onSuccess) {
        if (mGeneralData.getMasterPassword() != null) {
            if (onSuccess != null) {
                onSuccess.run();
            }
            return;
        }

        settingsPresenter.closeDialog();
        SimpleEditDialog.showPassword(
                getContext(),
                getContext().getString(R.string.enable_master_password),
                null,
                newValue -> {
                    mGeneralData.setMasterPassword(newValue);
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                    return true;
                });
    }

    private Map<Long, Integer> getMenuNames() {
        Map<Long, Integer> menuNames = new HashMap<>();
        menuNames.put(MainUIData.MENU_ITEM_EXIT_FROM_PIP, R.string.return_to_background_video);
        menuNames.put(MainUIData.MENU_ITEM_EXCLUDE_FROM_CONTENT_BLOCK, R.string.content_block_exclude_channel);
        menuNames.put(MainUIData.MENU_ITEM_MARK_AS_WATCHED, R.string.mark_as_watched);
        menuNames.put(MainUIData.MENU_ITEM_OPEN_CHANNEL, R.string.open_channel);
        menuNames.put(MainUIData.MENU_ITEM_UPDATE_CHECK, R.string.check_for_updates);
        menuNames.put(MainUIData.MENU_ITEM_CLEAR_HISTORY, R.string.clear_history);
        menuNames.put(MainUIData.MENU_ITEM_TOGGLE_HISTORY, R.string.pause_history);
        menuNames.put(MainUIData.MENU_ITEM_PLAYLIST_ORDER, R.string.playlist_order);
        menuNames.put(MainUIData.MENU_ITEM_PLAY_NEXT, R.string.play_next);
        menuNames.put(MainUIData.MENU_ITEM_ADD_TO_QUEUE, R.string.add_remove_from_playback_queue);
        menuNames.put(MainUIData.MENU_ITEM_SHOW_QUEUE, R.string.action_playback_queue);
        menuNames.put(MainUIData.MENU_ITEM_STREAM_REMINDER, R.string.set_stream_reminder);
        menuNames.put(MainUIData.MENU_ITEM_SUBSCRIBE, R.string.subscribe_unsubscribe_from_channel);
        menuNames.put(MainUIData.MENU_ITEM_SAVE_REMOVE_PLAYLIST, R.string.save_remove_playlist);
        menuNames.put(MainUIData.MENU_ITEM_CREATE_PLAYLIST, R.string.create_playlist);
        menuNames.put(MainUIData.MENU_ITEM_RENAME_PLAYLIST, R.string.rename_playlist);
        menuNames.put(MainUIData.MENU_ITEM_ADD_TO_WATCH_LATER, R.string.add_video_to_watch_later);
        menuNames.put(MainUIData.MENU_ITEM_ADD_TO_NEW_PLAYLIST, R.string.add_video_to_new_playlist);
        menuNames.put(MainUIData.MENU_ITEM_ADD_TO_PLAYLIST, R.string.dialog_add_to_playlist);
        menuNames.put(MainUIData.MENU_ITEM_RECENT_PLAYLIST, R.string.add_remove_from_recent_playlist);
        menuNames.put(MainUIData.MENU_ITEM_PLAY_VIDEO, R.string.play_video);
        menuNames.put(MainUIData.MENU_ITEM_PLAY_VIDEO_INCOGNITO, R.string.play_video_incognito);
        menuNames.put(MainUIData.MENU_ITEM_PLAY_FROM_START, R.string.play_from_start);
        menuNames.put(MainUIData.MENU_ITEM_NOT_INTERESTED, R.string.not_interested);
        menuNames.put(MainUIData.MENU_ITEM_NOT_RECOMMEND_CHANNEL, R.string.not_recommend_channel);
        menuNames.put(MainUIData.MENU_ITEM_REMOVE_FROM_HISTORY, R.string.remove_from_history);
        menuNames.put(MainUIData.MENU_ITEM_REMOVE_FROM_SUBSCRIPTIONS, R.string.remove_from_subscriptions);
        menuNames.put(MainUIData.MENU_ITEM_PIN_TO_SIDEBAR, R.string.pin_unpin_from_sidebar);
        menuNames.put(MainUIData.MENU_ITEM_SHARE_LINK, R.string.share_link);
        menuNames.put(MainUIData.MENU_ITEM_SHARE_EMBED_LINK, R.string.share_embed_link);
        menuNames.put(MainUIData.MENU_ITEM_SHARE_QR_LINK, R.string.share_qr_link);
        menuNames.put(MainUIData.MENU_ITEM_SELECT_ACCOUNT, R.string.dialog_account_list);
        menuNames.put(MainUIData.MENU_ITEM_MOVE_SECTION_UP, R.string.move_section_up);
        menuNames.put(MainUIData.MENU_ITEM_MOVE_SECTION_DOWN, R.string.move_section_down);
        menuNames.put(MainUIData.MENU_ITEM_RENAME_SECTION, R.string.rename_section);
        menuNames.put(MainUIData.MENU_ITEM_OPEN_DESCRIPTION, R.string.action_video_info);
        menuNames.put(MainUIData.MENU_ITEM_OPEN_COMMENTS, R.string.open_comments);
        menuNames.put(MainUIData.MENU_ITEM_OPEN_PLAYLIST, R.string.open_playlist);
        menuNames.put(MainUIData.MENU_ITEM_BLOCK_CHANNEL, R.string.dialog_block_channel);

        for (ContextMenuProvider provider : new ContextMenuManager(getContext()).getProviders()) {
            menuNames.put(provider.getId(), provider.getTitleResId());
        }

        return menuNames;
    }
}
