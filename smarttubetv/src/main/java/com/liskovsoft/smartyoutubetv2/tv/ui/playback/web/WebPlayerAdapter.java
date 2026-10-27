package com.liskovsoft.smartyoutubetv2.tv.ui.playback.web;

import android.os.Handler;
import android.os.Looper;

import androidx.leanback.media.PlaybackGlueHost;
import androidx.leanback.media.PlayerAdapter;

/**
 * Leanback adapter that feeds the existing player UI (VideoPlayerGlue) from the web player.
 * Replaces LeanbackPlayerAdapter when the web player is enabled.
 */
public class WebPlayerAdapter extends PlayerAdapter implements Runnable, WebPlayerController.StateListener {
    private final WebPlayerController mController;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final int mUpdatePeriodMs;
    private boolean mLastPrepared;

    public WebPlayerAdapter(WebPlayerController controller, int updatePeriodMs) {
        mController = controller;
        mUpdatePeriodMs = updatePeriodMs;
    }

    @Override
    public void onAttachedToHost(PlaybackGlueHost host) {
        mController.setStateListener(this);
        onStateChanged();
    }

    @Override
    public void onDetachedFromHost() {
        mHandler.removeCallbacks(this);
        mController.setStateListener(null);

        Callback callback = getCallback();
        if (callback != null) {
            callback.onBufferingStateChanged(this, false);
            callback.onPlayStateChanged(this);
        }
    }

    @Override
    public void setProgressUpdatingEnabled(boolean enabled) {
        mHandler.removeCallbacks(this);
        if (enabled) {
            mHandler.post(this);
        }
    }

    @Override
    public boolean isPlaying() {
        return mController.isPlaying();
    }

    @Override
    public boolean isPrepared() {
        return mController.hasVideo();
    }

    @Override
    public long getDuration() {
        return mController.getDurationMs();
    }

    @Override
    public long getCurrentPosition() {
        return mController.hasVideo() ? mController.getPositionMs() : -1;
    }

    @Override
    public long getBufferedPosition() {
        return mController.getBufferedMs();
    }

    @Override
    public void play() {
        if (mController.isEnded()) {
            mController.setPositionMs(0);
        }

        mController.setPlayWhenReady(true);
        Callback callback = getCallback();
        if (callback != null) {
            callback.onPlayStateChanged(this);
        }
    }

    @Override
    public void pause() {
        mController.setPlayWhenReady(false);
        Callback callback = getCallback();
        if (callback != null) {
            callback.onPlayStateChanged(this);
        }
    }

    @Override
    public void seekTo(long positionMs) {
        mController.setPositionMs(positionMs);
        Callback callback = getCallback();
        if (callback != null) {
            callback.onCurrentPositionChanged(this);
        }
    }

    // Progress loop (same as in LeanbackPlayerAdapter)
    @Override
    public void run() {
        Callback callback = getCallback();
        if (callback != null) {
            callback.onCurrentPositionChanged(this);
            callback.onBufferedPositionChanged(this);
        }
        mHandler.postDelayed(this, mUpdatePeriodMs);
    }

    // WebPlayerController.StateListener

    @Override
    public void onStateChanged() {
        Callback callback = getCallback();
        if (callback == null) {
            return;
        }

        boolean prepared = isPrepared();
        if (prepared != mLastPrepared) {
            mLastPrepared = prepared;
            callback.onPreparedStateChanged(this);
        }

        callback.onPlayStateChanged(this);
        callback.onBufferingStateChanged(this, mController.isBuffering());
        callback.onDurationChanged(this);
        callback.onCurrentPositionChanged(this);

        if (mController.isEnded()) {
            callback.onPlayCompleted(this);
        }
    }

    @Override
    public void onDurationChanged() {
        Callback callback = getCallback();
        if (callback != null) {
            callback.onDurationChanged(this);
        }
    }
}
