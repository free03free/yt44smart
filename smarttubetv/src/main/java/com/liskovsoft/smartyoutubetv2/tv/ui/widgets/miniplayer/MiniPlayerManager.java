package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.miniplayer;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.ExoFormatItem;
import com.liskovsoft.smartyoutubetv2.common.prefs.MiniPlayerData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.embedplayer.EmbedPlayerView;

/**
 * In-app mini player: a small floating video window placed in a corner of the browse screens.
 * <br>
 * The fullscreen player hands over the current video (and position) when the user presses Back.
 * The window is recreated on every browse activity so it keeps playing while the user navigates.
 * <br>
 * Remote control while the window is visible:
 * <ul>
 *     <li>Play/Pause - pause or resume</li>
 *     <li>Long press Play/Pause - expand to fullscreen</li>
 *     <li>Stop - close the window</li>
 * </ul>
 */
public final class MiniPlayerManager {
    private static final String TAG = MiniPlayerManager.class.getSimpleName();
    // Wait until the fullscreen activity releases its engine, otherwise both fight over the presenter
    private static final long START_DELAY_MS = 1200;
    private static final long HINT_HIDE_DELAY_MS = 7_000;

    private static Video sVideo;
    private static long sPositionMs;
    private static Activity sHost;
    private static FrameLayout sContainer;
    private static EmbedPlayerView sPlayerView;
    private static boolean sLongPressHandled;
    private static Activity sPendingHost;
    private static final Runnable sStartRunnable = MiniPlayerManager::startPending;

    private MiniPlayerManager() {
    }

    /**
     * Called by the fullscreen player when the user leaves it with the Back button.
     */
    public static void capture(Video video, long positionMs) {
        if (video == null) {
            return;
        }

        sVideo = Video.from(video);
        sPositionMs = Math.max(positionMs, 0);
    }

    public static boolean isActive() {
        return sVideo != null;
    }

    public static void onActivityResumed(Activity activity) {
        if (activity instanceof PlaybackActivity) {
            // Fullscreen player takes over
            dismiss();
            return;
        }

        if (sVideo == null || !MiniPlayerData.instance(activity).isEnabled()) {
            return;
        }

        if (sPlayerView != null && sHost == activity) {
            return;
        }

        // Moving to another browse screen: remember position and recreate the window there
        savePositionAndDestroyView();

        sPendingHost = activity;
        Utils.removeCallbacks(sStartRunnable);
        Utils.postDelayed(sStartRunnable, START_DELAY_MS);
    }

    public static void onActivityStopped(Activity activity) {
        if (sPendingHost == activity) {
            sPendingHost = null;
            Utils.removeCallbacks(sStartRunnable);
        }

        if (sHost == activity) {
            savePositionAndDestroyView();
        }
    }

    /**
     * @return true when the event has been consumed
     */
    public static boolean handleKeyEvent(Activity activity, KeyEvent event) {
        if (sPlayerView == null || sHost != activity || activity instanceof PlaybackActivity) {
            return false;
        }

        int keyCode = event.getKeyCode();

        if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                dismiss();
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() > 0 && !sLongPressHandled) {
                    sLongPressHandled = true;
                    expand();
                }
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                if (!sLongPressHandled) {
                    togglePlayPause();
                }
                sLongPressHandled = false;
            }
            return true;
        }

        return false;
    }

    /**
     * Closes the window and forgets the video (e.g. new fullscreen playback or app exit).
     */
    public static void dismiss() {
        Utils.removeCallbacks(sStartRunnable);
        sPendingHost = null;
        destroyView();
        sVideo = null;
        sPositionMs = 0;
        sLongPressHandled = false;
    }

    private static void startPending() {
        Activity activity = sPendingHost;
        sPendingHost = null;

        if (activity == null || sVideo == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        // Fullscreen playback is still alive. Don't interfere
        PlaybackPresenter presenter = PlaybackPresenter.instance(activity);
        if (presenter.getView() != null && !presenter.getView().isEmbed() && presenter.isEngineInitialized()) {
            return;
        }

        createView(activity);
    }

    private static void createView(Activity activity) {
        ViewGroup root = activity.findViewById(android.R.id.content);

        if (root == null) {
            return;
        }

        destroyView();

        MiniPlayerData data = MiniPlayerData.instance(activity);

        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int width = (int) (screenWidth * data.getSizePercent() / 100f);
        int height = width * 9 / 16;
        int margin = (int) (activity.getResources().getDisplayMetrics().density * 24);

        FrameLayout container = new FrameLayout(activity);
        container.setBackgroundColor(Color.BLACK);
        container.setFocusable(false);
        container.setFocusableInTouchMode(false);
        container.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

        EmbedPlayerView playerView = new EmbedPlayerView(activity);
        playerView.setQuality(EmbedPlayerView.QUALITY_NORMAL);
        playerView.setVideoFormat(getVideoFormat(data.getQuality()));
        playerView.setUseController(false);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setFocusable(false);
        playerView.setOnClickListener(v -> expand()); // touch devices
        container.addView(playerView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView hint = new TextView(activity);
        hint.setText(R.string.mini_player_hint);
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(12);
        hint.setBackgroundColor(0xAA000000);
        hint.setPadding(margin / 2, margin / 4, margin / 2, margin / 4);
        hint.setGravity(Gravity.CENTER);
        container.addView(hint, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        Utils.postDelayed(() -> hint.setVisibility(View.GONE), HINT_HIDE_DELAY_MS);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height, getGravity(data.getPosition()));
        lp.setMargins(margin, margin, margin, margin);
        root.addView(container, lp);

        sHost = activity;
        sContainer = container;
        sPlayerView = playerView;

        Video video = Video.from(sVideo);
        video.pendingPosMs = sPositionMs;

        try {
            playerView.openVideo(video);
        } catch (Exception e) {
            Log.e(TAG, "Mini player failed to start: %s", e.getMessage());
            dismiss();
        }
    }

    private static void savePositionAndDestroyView() {
        if (sPlayerView != null) {
            try {
                if (sPlayerView.isEngineInitialized()) {
                    long position = sPlayerView.getPositionMs();
                    if (position > 0) {
                        sPositionMs = position;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Can't read mini player position: %s", e.getMessage());
            }
        }

        destroyView();
    }

    private static void destroyView() {
        if (sContainer != null) {
            ViewGroup parent = (ViewGroup) sContainer.getParent();
            if (parent != null) {
                parent.removeView(sContainer);
            }
        }

        if (sPlayerView != null) {
            sPlayerView.finish();
        }

        sContainer = null;
        sPlayerView = null;
        sHost = null;
    }

    private static void togglePlayPause() {
        if (sPlayerView != null && sPlayerView.isEngineInitialized()) {
            sPlayerView.setPlayWhenReady(!sPlayerView.getPlayWhenReady());
        }
    }

    private static void expand() {
        if (sPlayerView == null || sVideo == null) {
            return;
        }

        Activity host = sHost;
        long position = sPositionMs;

        try {
            if (sPlayerView.isEngineInitialized() && sPlayerView.getPositionMs() > 0) {
                position = sPlayerView.getPositionMs();
            }
        } catch (Exception e) {
            // use the saved one
        }

        Video video = Video.from(sVideo);
        video.pendingPosMs = position;

        dismiss();

        if (host != null) {
            PlaybackPresenter.instance(host).openVideo(video);
        }
    }

    private static ExoFormatItem getVideoFormat(int height) {
        int width;

        switch (height) {
            case 144:
                width = 256;
                break;
            case 240:
                width = 426;
                break;
            case 480:
                width = 854;
                break;
            case 720:
                width = 1280;
                break;
            case 1080:
                width = 1920;
                break;
            default:
                height = 360;
                width = 640;
        }

        return ExoFormatItem.fromVideoSpec(width + "," + height + ",30,avc", false);
    }

    private static int getGravity(int position) {
        switch (position) {
            case MiniPlayerData.POSITION_BOTTOM_LEFT:
                return Gravity.BOTTOM | Gravity.LEFT;
            case MiniPlayerData.POSITION_TOP_RIGHT:
                return Gravity.TOP | Gravity.RIGHT;
            case MiniPlayerData.POSITION_TOP_LEFT:
                return Gravity.TOP | Gravity.LEFT;
            default:
                return Gravity.BOTTOM | Gravity.RIGHT;
        }
    }
}
