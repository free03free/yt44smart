package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.miniplayer;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.lang.ref.WeakReference;

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
 *     <li>Press the arrow towards the window's corner at the edge of the screen - the window gets the focus</li>
 *     <li>OK on the focused window - expand to fullscreen</li>
 *     <li>Arrow away from the corner (or Back) on the focused window - return to browsing</li>
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
    private static int sPosition = MiniPlayerData.POSITION_BOTTOM_RIGHT;
    private static WeakReference<View> sPrevFocus;
    private static WeakReference<View> sPendingFocus;
    private static ViewGroup sFocusRoot;
    private static boolean sFocusChanged;
    // The app moves the focus (and scrolls) with a delay. Wait before deciding that the edge has been reached.
    private static final long EDGE_CHECK_DELAY_MS = 350;
    private static final Runnable sJumpRunnable = MiniPlayerManager::jumpToWindowIfEdge;
    private static final ViewTreeObserver.OnGlobalFocusChangeListener sFocusListener =
            (oldFocus, newFocus) -> sFocusChanged = true;
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

        if (sContainer != null && sContainer.hasFocus() && handleFocusedKey(event)) {
            return true;
        }

        // The user is on the last/first row and presses the arrow towards the window's corner.
        // The window becomes the next item (before the search/account buttons of the top bar).
        if (sContainer != null && !sContainer.hasFocus() && event.getAction() == KeyEvent.ACTION_DOWN &&
                event.getRepeatCount() == 0 &&
                keyCode == (isBottom() ? KeyEvent.KEYCODE_DPAD_DOWN : KeyEvent.KEYCODE_DPAD_UP)) {
            View focus = activity.getCurrentFocus();

            if (focus != null && isContentEdge(focus, keyCode)) {
                Utils.removeCallbacks(sJumpRunnable);
                sPendingFocus = null;
                sPrevFocus = new WeakReference<>(focus);
                sContainer.requestFocus();
                return true;
            }
        }

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
     * Called after the activity has dispatched the key. When the user pressed the arrow towards the window's corner
     * and nothing else takes the focus (the edge of the content), the focus moves to the mini window.
     */
    public static void onKeyDispatched(Activity activity, KeyEvent event, View focusBefore) {
        if (sContainer == null || sHost != activity || activity instanceof PlaybackActivity ||
                event.getAction() != KeyEvent.ACTION_DOWN) {
            return;
        }

        // Any new key press cancels the pending check
        Utils.removeCallbacks(sJumpRunnable);
        sPendingFocus = null;

        int keyCode = event.getKeyCode();
        boolean isVertical = keyCode == (isBottom() ? KeyEvent.KEYCODE_DPAD_DOWN : KeyEvent.KEYCODE_DPAD_UP);
        boolean isSide = keyCode == (isRight() ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT);

        if ((!isVertical && !isSide) || event.getRepeatCount() != 0 || focusBefore == null || sContainer.hasFocus()) {
            return;
        }

        // There is more content in that direction. Not the edge
        if (canContentScroll(focusBefore, keyCode)) {
            return;
        }

        // Side arrow works only when the focused item is on the same band as the window
        if (isSide && !isSameBand(focusBefore, sContainer)) {
            return;
        }

        sFocusChanged = false;
        sPendingFocus = new WeakReference<>(focusBefore);
        Utils.postDelayed(sJumpRunnable, EDGE_CHECK_DELAY_MS);
    }

    private static void jumpToWindowIfEdge() {
        View focusBefore = sPendingFocus != null ? sPendingFocus.get() : null;
        sPendingFocus = null;

        if (focusBefore == null || sContainer == null || sHost == null || sContainer.hasFocus()) {
            return;
        }

        // The app has moved the focus meanwhile (maybe with a delay). Not the edge
        if (sFocusChanged || sHost.getCurrentFocus() != focusBefore) {
            return;
        }

        sPrevFocus = new WeakReference<>(focusBefore);
        sContainer.requestFocus();
    }

    /**
     * @return true when there are no more rows of the list in the key's direction (the system would
     * move the focus out of the list: to the top bar buttons, etc.)
     */
    private static boolean isContentEdge(View view, int keyCode) {
        RecyclerView outerList = null;
        ViewParent parent = view.getParent();

        while (parent instanceof View) {
            if (parent instanceof RecyclerView) {
                outerList = (RecyclerView) parent;
            }
            parent = parent.getParent();
        }

        if (outerList == null) {
            return false; // unknown layout. Use the delayed check instead
        }

        View next = view.focusSearch(keyCode == KeyEvent.KEYCODE_DPAD_UP ? View.FOCUS_UP : View.FOCUS_DOWN);

        if (next == null || next == sContainer) {
            return true;
        }

        // Next item is inside the same list: another row. Not the edge
        ViewParent nextParent = next.getParent();

        while (nextParent != null) {
            if (nextParent == outerList) {
                return false;
            }
            nextParent = nextParent.getParent();
        }

        return true;
    }

    private static boolean canContentScroll(View view, int keyCode) {
        boolean vertical = keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN;
        int direction = (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) ? -1 : 1;

        View current = view;

        while (current != null) {
            if (vertical ? current.canScrollVertically(direction) : current.canScrollHorizontally(direction)) {
                return true;
            }

            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }

        return false;
    }

    private static boolean handleFocusedKey(KeyEvent event) {
        int keyCode = event.getKeyCode();
        boolean isDown = event.getAction() == KeyEvent.ACTION_DOWN;

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    expand();
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (isDown) {
                    returnFocus();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                boolean towardsCorner = keyCode == (isBottom() ? KeyEvent.KEYCODE_DPAD_DOWN : KeyEvent.KEYCODE_DPAD_UP) ||
                        keyCode == (isRight() ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT);
                if (isDown && !towardsCorner) {
                    boolean isSideKey = keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;

                    if (!isSideKey || !moveFocusBySystem(keyCode)) {
                        returnFocus();
                    }
                }
                return true; // the window is in the corner: nowhere to go
            default:
                return false;
        }
    }

    /**
     * Moves the focus from the window to its neighbour on the same band (e.g. account/search buttons of the top bar).
     */
    private static boolean moveFocusBySystem(int keyCode) {
        if (sContainer == null) {
            return false;
        }

        View next = sContainer.focusSearch(keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? View.FOCUS_LEFT : View.FOCUS_RIGHT);

        return next != null && next != sContainer && next.requestFocus();
    }

    private static void returnFocus() {
        View prev = sPrevFocus != null ? sPrevFocus.get() : null;

        if (prev != null && prev.isAttachedToWindow() && prev.requestFocus()) {
            return;
        }

        // Previous focus is lost. Let the system find the closest item
        if (sContainer != null && sContainer.getParent() instanceof ViewGroup) {
            View next = sContainer.focusSearch(isBottom() ? View.FOCUS_UP : View.FOCUS_DOWN);
            if (next != null && next != sContainer) {
                next.requestFocus();
            }
        }
    }

    private static boolean isBottom() {
        return sPosition == MiniPlayerData.POSITION_BOTTOM_RIGHT || sPosition == MiniPlayerData.POSITION_BOTTOM_LEFT;
    }

    private static boolean isRight() {
        return sPosition == MiniPlayerData.POSITION_BOTTOM_RIGHT || sPosition == MiniPlayerData.POSITION_TOP_RIGHT;
    }

    private static boolean isSameBand(View a, View b) {
        Rect ra = new Rect();
        Rect rb = new Rect();

        if (!a.getGlobalVisibleRect(ra) || !b.getGlobalVisibleRect(rb)) {
            return false;
        }

        return ra.top < rb.bottom && ra.bottom > rb.top;
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
        container.setFocusable(true);
        container.setFocusableInTouchMode(false);
        container.setClickable(true);
        container.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        container.setOnClickListener(v -> expand());
        container.setOnFocusChangeListener((v, hasFocus) -> onWindowFocusChanged(v, hasFocus));

        EmbedPlayerView playerView = new EmbedPlayerView(activity);
        playerView.setQuality(EmbedPlayerView.QUALITY_NORMAL);
        playerView.setVideoFormat(getVideoFormat(data.getQuality()));
        playerView.setUseController(false);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setFocusable(false);
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
        sPosition = data.getPosition();
        sPrevFocus = null;
        sPendingFocus = null;
        sFocusRoot = root;
        root.getViewTreeObserver().addOnGlobalFocusChangeListener(sFocusListener);

        Video video = Video.from(sVideo);
        video.pendingPosMs = sPositionMs;

        try {
            playerView.openVideo(video);
        } catch (Exception e) {
            Log.e(TAG, "Mini player failed to start: %s", e.getMessage());
            dismiss();
        }
    }

    private static void onWindowFocusChanged(View view, boolean hasFocus) {
        if (hasFocus) {
            GradientDrawable border = new GradientDrawable();
            border.setColor(Color.TRANSPARENT);
            float density = view.getResources().getDisplayMetrics().density;
            border.setStroke((int) (density * 6), Color.WHITE);
            ((FrameLayout) view).setForeground(border);
            view.animate().scaleX(1.06f).scaleY(1.06f).setDuration(120).start();
        } else {
            ((FrameLayout) view).setForeground(null);
            view.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
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
        Utils.removeCallbacks(sJumpRunnable);
        sPendingFocus = null;

        if (sFocusRoot != null) {
            ViewTreeObserver observer = sFocusRoot.getViewTreeObserver();
            if (observer.isAlive()) {
                observer.removeOnGlobalFocusChangeListener(sFocusListener);
            }
            sFocusRoot = null;
        }

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
