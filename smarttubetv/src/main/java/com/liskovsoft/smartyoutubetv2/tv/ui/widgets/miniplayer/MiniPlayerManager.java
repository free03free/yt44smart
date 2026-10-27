package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.miniplayer;

import android.app.Activity;
import android.graphics.Color;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.embedplayer.EmbedPlayerView;

/**
 * In-app mini player: the video keeps playing in a small bar at the top of every browse screen
 * while the rest of the screen is used to browse other videos.
 * <p>
 * Built on top of {@link EmbedPlayerView} (the same engine the card previews use).
 */
public final class MiniPlayerManager {
    private static MiniPlayerManager sInstance;

    private EmbedPlayerView mPlayer;
    private FrameLayout mBar;
    private Video mPendingVideo;
    private Activity mActivity;      // the activity that is currently resumed (and may host the bar)
    private View mShiftedChild;
    private int mOrigTopMargin;
    private int mRetries;
    private final Runnable mTryStart = this::tryStart;

    private MiniPlayerManager() {
    }

    public static MiniPlayerManager instance() {
        if (sInstance == null) {
            sInstance = new MiniPlayerManager();
        }
        return sInstance;
    }

    public boolean isActive() {
        return mPlayer != null || mPendingVideo != null;
    }

    /**
     * Called when the user leaves the fullscreen player with Back: playback continues in the mini bar.
     */
    public void enterFrom(Video video, long positionMs) {
        if (video == null || video.videoId == null) {
            return;
        }

        stop(); // only one mini player at a time
        video.pendingPosMs = positionMs;
        mPendingVideo = video;
        mRetries = 0;
        // The fullscreen engine is released only after the browse activity is resumed: wait for it
        Utils.postDelayed(mTryStart, 600);
    }

    public void onActivityResumed(Activity activity) {
        mActivity = activity;

        if (activity instanceof PlaybackActivity) {
            stop(); // fullscreen playback takes over
            return;
        }

        if (mPlayer != null) {
            attach(activity);
        }
    }

    public void onActivityPaused(Activity activity) {
        if (mActivity == activity) {
            detach();
            mActivity = null;
        }
    }

    public void stop() {
        Utils.removeCallbacks(mTryStart);
        mPendingVideo = null;
        detach();
        if (mPlayer != null) {
            try {
                mPlayer.finish();
            } catch (Exception e) {
                // ignore: the engine may be already released
            }
            mPlayer = null;
        }
        mBar = null;
    }

    private void tryStart() {
        if (mPendingVideo == null) {
            return;
        }

        Activity activity = mActivity;
        PlaybackPresenter presenter = activity != null ? PlaybackPresenter.instance(activity) : null;
        PlaybackView view = presenter != null ? presenter.getView() : null;
        boolean fullscreenStillAlive = view != null && !(view instanceof EmbedPlayerView) && presenter.isEngineInitialized();

        if (activity == null || activity instanceof PlaybackActivity || fullscreenStillAlive) {
            if (++mRetries <= 10) {
                Utils.postDelayed(mTryStart, 500);
            } else {
                mPendingVideo = null; // give up
            }
            return;
        }

        Video video = mPendingVideo;
        mPendingVideo = null;

        mPlayer = new EmbedPlayerView(activity);
        mPlayer.setQuality(EmbedPlayerView.QUALITY_NORMAL);
        mPlayer.setUseController(false);
        mPlayer.setBackgroundColor(Color.BLACK);
        mBar = buildBar(activity);
        attach(activity);
        mPlayer.openVideo(video);
    }

    private FrameLayout buildBar(Activity activity) {
        FrameLayout bar = new FrameLayout(activity);
        bar.setBackgroundColor(Color.BLACK);
        bar.setClickable(true);

        bar.addView(mPlayer, new FrameLayout.LayoutParams(0, 0)); // real size is set in attach()

        View tap = new View(activity);
        tap.setOnClickListener(v -> togglePlay());
        bar.addView(tap, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Right to left: close, expand, play/pause
        addButton(bar, activity, "\u2715", 0, v -> stop());
        addButton(bar, activity, "\u26F6", 1, v -> expand());
        addButton(bar, activity, "\u23EF", 2, v -> togglePlay());

        return bar;
    }

    private void addButton(FrameLayout bar, Activity activity, String text, int index, View.OnClickListener listener) {
        TextView button = new TextView(activity);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundColor(0x99000000);
        button.setFocusable(true);
        button.setOnClickListener(listener);

        int size = dp(activity, 44);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.END);
        lp.setMarginEnd(index * (size + dp(activity, 4)) + dp(activity, 4));
        lp.topMargin = dp(activity, 4);
        bar.addView(button, lp);
    }

    private void togglePlay() {
        if (mPlayer != null) {
            mPlayer.setPlayWhenReady(!mPlayer.isPlaying());
        }
    }

    private void expand() {
        if (mPlayer == null || mActivity == null) {
            return;
        }

        Activity activity = mActivity;
        Video video = mPlayer.getVideo();
        long positionMs = mPlayer.getPositionMs();
        stop();

        if (video != null) {
            video.pendingPosMs = positionMs;
            PlaybackPresenter.instance(activity).openVideo(video);
        }
    }

    private void attach(Activity activity) {
        detach();

        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null || mBar == null || content.getChildCount() == 0) {
            return;
        }

        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        int width = content.getWidth() > 0 ? content.getWidth() : dm.widthPixels;
        int height = Math.min(width * 9 / 16, dm.heightPixels * 35 / 100);
        int playerWidth = Math.min(width, height * 16 / 9);

        FrameLayout.LayoutParams playerLp = new FrameLayout.LayoutParams(playerWidth, height, Gravity.CENTER_HORIZONTAL);
        mPlayer.setLayoutParams(playerLp);

        // Push the screen content down so the bar doesn't cover it
        View child = content.getChildAt(0);
        ViewGroup.LayoutParams lp = child.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
            mShiftedChild = child;
            mOrigTopMargin = mlp.topMargin;
            mlp.topMargin = height;
            child.setLayoutParams(mlp);
        }

        content.addView(mBar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height, Gravity.TOP));
        mBar.bringToFront();
    }

    private void detach() {
        if (mBar != null && mBar.getParent() instanceof ViewGroup) {
            ((ViewGroup) mBar.getParent()).removeView(mBar);
        }

        if (mShiftedChild != null) {
            ViewGroup.LayoutParams lp = mShiftedChild.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ((ViewGroup.MarginLayoutParams) lp).topMargin = mOrigTopMargin;
                mShiftedChild.setLayoutParams(lp);
            }
            mShiftedChild = null;
        }
    }

    private static int dp(Activity activity, int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, activity.getResources().getDisplayMetrics());
    }
}
