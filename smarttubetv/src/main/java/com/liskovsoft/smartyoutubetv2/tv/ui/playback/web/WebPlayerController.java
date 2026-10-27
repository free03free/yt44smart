package com.liskovsoft.smartyoutubetv2.tv.ui.playback.web;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;

import java.util.regex.Pattern;

/**
 * Plays the video with the YouTube web player (IFrame Player API inside a WebView).
 * Exposes the same kind of state the ExoPlayer controller does, so the existing player UI keeps working.
 */
public class WebPlayerController {
    private static final String TAG = WebPlayerController.class.getSimpleName();
    private static final String BASE_URL = "https://www.youtube.com";
    private static final Pattern VIDEO_ID = Pattern.compile("^[A-Za-z0-9_-]{6,20}$");

    public interface Host {
        Video getVideo();
        /** The web player can't play this video (e.g. embedding is disabled). */
        void onWebError(String videoId, int code);
    }

    public interface StateListener {
        void onStateChanged();
        void onDurationChanged();
    }

    private final PlayerEventListener mListener;
    private final Host mHost;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final FrameLayout mContainer;
    private WebView mWebView;
    private StateListener mStateListener;
    private boolean mReleased;

    private String mVideoId;
    private boolean mLoadedNotified;
    private boolean mEnded;
    private boolean mPlayWhenReady = true;
    private boolean mPlaying;
    private boolean mBuffering;
    private boolean mMediaLoaded;
    private long mPositionMs = -1;
    private long mDurationMs = -1;
    private long mBufferedMs;
    private float mSpeed = 1f;
    private float mVolume = 1f;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    public WebPlayerController(Context context, PlayerEventListener listener, Host host) {
        mListener = listener;
        mHost = host;

        // The video is controlled only by the player UI: don't let the web page take touches or focus
        mContainer = new FrameLayout(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                return true;
            }

            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                return false;
            }
        };
        mContainer.setBackgroundColor(Color.BLACK);
        mContainer.setFocusable(false);

        mWebView = new WebView(context);
        mWebView.setBackgroundColor(Color.BLACK);
        mWebView.setFocusable(false);
        mWebView.setFocusableInTouchMode(false);
        mWebView.setWebChromeClient(new WebChromeClient());

        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        mWebView.addJavascriptInterface(new Bridge(), "Native");
        mContainer.addView(mWebView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public android.view.View getView() {
        return mContainer;
    }

    public void setStateListener(StateListener listener) {
        mStateListener = listener;
    }

    public void open(Video video, String videoId) {
        if (mReleased || mWebView == null) {
            return;
        }

        if (videoId == null || !VIDEO_ID.matcher(videoId).matches()) {
            mHost.onWebError(videoId, -1);
            return;
        }

        mVideoId = videoId;
        mLoadedNotified = false;
        mEnded = false;
        mPlaying = false;
        mBuffering = true;
        mMediaLoaded = false;
        mPositionMs = -1;
        mDurationMs = -1;
        mBufferedMs = 0;
        mPlayWhenReady = true;

        mListener.onSourceChanged(video);
        mWebView.loadDataWithBaseURL(BASE_URL, buildHtml(videoId), "text/html", "utf-8", null);
        notifyState();
    }

    // Engine-like API (mirrors what ExoPlayerController provides to the fragment)

    public boolean hasVideo() {
        return mVideoId != null;
    }

    public long getPositionMs() {
        return Math.max(mPositionMs, 0);
    }

    public void setPositionMs(long positionMs) {
        if (positionMs < 0) {
            return;
        }

        mPositionMs = positionMs;
        mEnded = false;
        js("ytSeek(" + (positionMs / 1000.0) + ")");
        mHandler.post(() -> {
            if (!mReleased) {
                mListener.onSeekEnd();
            }
        });
    }

    public long getDurationMs() {
        return mDurationMs;
    }

    public long getBufferedMs() {
        return mBufferedMs;
    }

    public void setPlayWhenReady(boolean play) {
        mPlayWhenReady = play;
        js(play ? "ytPlay()" : "ytPause()");
    }

    public boolean getPlayWhenReady() {
        return mPlayWhenReady;
    }

    public boolean isPlaying() {
        return mPlaying;
    }

    public boolean isBuffering() {
        return mBuffering;
    }

    public boolean isLoading() {
        return mVideoId != null && (!mMediaLoaded || mBuffering);
    }

    public boolean isEnded() {
        return mEnded;
    }

    public boolean containsMedia() {
        return mVideoId != null;
    }

    public float getSpeed() {
        return mSpeed;
    }

    public void setSpeed(float speed) {
        if (speed <= 0) {
            return;
        }

        mSpeed = speed;
        js("ytRate(" + speed + ")");
    }

    public float getVolume() {
        return mVolume;
    }

    public void setVolume(float volume) {
        mVolume = volume;
        // The web player can't boost the volume above 100%
        js("ytVol(" + Math.round(Math.max(0f, Math.min(1f, volume)) * 100) + ")");
    }

    public void release() {
        mReleased = true;
        mStateListener = null;
        mHandler.removeCallbacksAndMessages(null);

        ViewParent parent = mContainer.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(mContainer);
        }

        if (mWebView != null) {
            mContainer.removeView(mWebView);
            mWebView.removeJavascriptInterface("Native");
            mWebView.loadUrl("about:blank");
            mWebView.destroy();
            mWebView = null;
        }
    }

    private void js(String script) {
        if (mWebView != null && !mReleased) {
            if (Build.VERSION.SDK_INT >= 19) {
                mWebView.evaluateJavascript(script, null);
            } else {
                mWebView.loadUrl("javascript:" + script);
            }
        }
    }

    private void notifyState() {
        if (mStateListener != null) {
            mStateListener.onStateChanged();
        }
    }

    // States of the IFrame Player API: -1 unstarted, 0 ended, 1 playing, 2 paused, 3 buffering, 5 cued
    private void handleState(int state) {
        if (mReleased) {
            return;
        }

        mBuffering = state == 3 || state == -1;

        switch (state) {
            case 1:
                mPlaying = true;
                mMediaLoaded = true;
                mEnded = false;
                if (!mPlayWhenReady) { // the user paused before the player started
                    js("ytPause()");
                }
                notifyVideoLoaded();
                mListener.onPlay();
                break;
            case 2:
                mPlaying = false;
                mMediaLoaded = true;
                mListener.onPause();
                break;
            case 3:
                mPlaying = false;
                mListener.onBuffering();
                break;
            case 0:
                mPlaying = false;
                if (!mEnded) {
                    mEnded = true;
                    mListener.onPlayEnd();
                }
                break;
            default:
                break;
        }

        notifyState();
    }

    private void notifyVideoLoaded() {
        if (!mLoadedNotified) {
            mLoadedNotified = true;
            mListener.onVideoLoaded(mHost.getVideo());
        }
    }

    private void handleError(int code) {
        if (mReleased) {
            return;
        }

        Log.e(TAG, "Web player error: " + code + ", video: " + mVideoId);
        mHost.onWebError(mVideoId, code);
    }

    private void handleTime(double posMs, double durMs, double bufferedFraction) {
        if (mReleased || Double.isNaN(posMs) || Double.isNaN(durMs)) {
            return;
        }

        mPositionMs = (long) posMs;
        mBufferedMs = (long) (durMs * Math.max(0, Math.min(1, bufferedFraction)));

        if (durMs > 0 && (long) durMs != mDurationMs) {
            mDurationMs = (long) durMs;
            if (mStateListener != null) {
                mStateListener.onDurationChanged();
            }
        }
    }

    // Called from the JS thread
    private class Bridge {
        @JavascriptInterface
        public void onState(int state) {
            mHandler.post(() -> handleState(state));
        }

        @JavascriptInterface
        public void onError(int code) {
            mHandler.post(() -> handleError(code));
        }

        @JavascriptInterface
        public void onTime(double posMs, double durMs, double bufferedFraction) {
            mHandler.post(() -> handleTime(posMs, durMs, bufferedFraction));
        }
    }

    private static String buildHtml(String videoId) {
        return "<!DOCTYPE html><html><head>"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}"
                + "#p{position:absolute;top:0;left:0;width:100%;height:100%}</style></head>"
                + "<body><div id=\"p\"></div>"
                + "<script src=\"https://www.youtube.com/iframe_api\"></script>"
                + "<script>"
                + "var player;"
                + "function onYouTubeIframeAPIReady(){"
                + "player=new YT.Player('p',{width:'100%',height:'100%',videoId:'" + videoId + "',"
                + "playerVars:{autoplay:1,controls:0,disablekb:1,fs:0,rel:0,playsinline:1,iv_load_policy:3,"
                + "cc_load_policy:0,modestbranding:1,enablejsapi:1,origin:'" + BASE_URL + "'},"
                + "events:{onReady:function(e){e.target.playVideo();},"
                + "onStateChange:function(e){Native.onState(e.data);},"
                + "onError:function(e){Native.onError(e.data);}}});}"
                + "setInterval(function(){try{if(player&&player.getDuration&&player.getDuration()>0){"
                + "Native.onTime(player.getCurrentTime()*1000,player.getDuration()*1000,player.getVideoLoadedFraction());}}catch(e){}},500);"
                + "function ytPlay(){try{player.playVideo();}catch(e){}}"
                + "function ytPause(){try{player.pauseVideo();}catch(e){}}"
                + "function ytSeek(s){try{player.seekTo(s,true);}catch(e){}}"
                + "function ytRate(r){try{player.setPlaybackRate(r);}catch(e){}}"
                + "function ytVol(v){try{player.setVolume(v);}catch(e){}}"
                + "</script></body></html>";
    }
}
