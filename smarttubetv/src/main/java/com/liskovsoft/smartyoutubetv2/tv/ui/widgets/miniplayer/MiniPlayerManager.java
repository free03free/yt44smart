package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.miniplayer;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.graphics.Outline;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewOutlineProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.BrowseFragment;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import androidx.core.content.ContextCompat;

import androidx.recyclerview.widget.RecyclerView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.ExoFormatItem;
import com.liskovsoft.smartyoutubetv2.common.misc.FormatInfoPrefetcher;
import com.liskovsoft.smartyoutubetv2.common.misc.PlayerHandoff;
import com.liskovsoft.smartyoutubetv2.common.prefs.MiniPlayerData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.embedplayer.EmbedPlayerView;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.loading.LoadingSpinnerDrawable;

/**
 * In-app mini player: a small floating video window placed in a corner of the browse screens.
 * <br>
 * The fullscreen player hands over the current video (and position) when the user presses Back.
 * The window is recreated on every browse activity so it keeps playing while the user navigates.
 * It is closed for good when the user leaves the app (Home, exit, screen off).
 * <br>
 * Remote control while the window is visible:
 * <ul>
 *     <li>Press the arrow towards the window's corner at the edge of the screen - the window gets the focus</li>
 *     <li>OK on the focused window - expand to fullscreen</li>
 *     <li>Long press OK on the focused window - close the window without playing the video</li>
 *     <li>Arrow away from the corner (or Back) on the focused window - return to browsing</li>
 *     <li>Play/Pause - pause or resume</li>
 *     <li>Long press Play/Pause - expand to fullscreen</li>
 *     <li>Stop - close the window</li>
 *     <li>Optional: a video chosen in the app while the window is visible plays in the window (not fullscreen)</li>
 * </ul>
 */
public final class MiniPlayerManager {
    private static final String TAG = MiniPlayerManager.class.getSimpleName();
    // Start as soon as the fullscreen player has released its engine (otherwise both fight over the presenter)
    private static final long START_FIRST_CHECK_MS = 250;
    private static final long START_RETRY_MS = 100;
    private static final long START_MAX_WAIT_MS = 2500;
    private static long sStartDeadline;
    private static final long SPINNER_MAX_MS = 30_000;

    private static Video sVideo;
    private static long sPositionMs;
    private static Activity sHost;
    private static FrameLayout sContainer;
    private static EmbedPlayerView sPlayerView;
    private static boolean sLongPressHandled;
    // After a long press on OK the window is gone. Swallow the rest of that key press so it doesn't click anything below.
    private static boolean sConsumeEnterUntilUp;
    // Browse content that has been moved away from the window (to restore its margins later)
    private static final List<ShiftedView> sShiftedViews = new ArrayList<>();

    private static final class ShiftedView {
        final WeakReference<View> view;
        final int topMargin;
        final int bottomMargin;

        ShiftedView(View view, int topMargin, int bottomMargin) {
            this.view = new WeakReference<>(view);
            this.topMargin = topMargin;
            this.bottomMargin = bottomMargin;
        }
    }
    // Window is being recreated: don't flash the top bar back
    private static boolean sRecreating;
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

    static {
        PlaybackPresenter.setVideoInterceptor(MiniPlayerManager::onOpenVideo);
    }

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

        // The mini window starts from the stream urls already loaded by the fullscreen player
        FormatInfoPrefetcher.keepForHandoff(sVideo.videoId);
    }

    public static boolean isActive() {
        return sVideo != null;
    }

    /**
     * @return true when the window is shown at the top of the screen (the rows are laid out below it)
     */
    public static boolean isTopWindowVisible() {
        return sContainer != null && sPlayerView != null && !isBottomPosition(sPosition);
    }

    public static void onActivityResumed(Activity activity) {
        if (activity instanceof PlaybackActivity) {
            // Fullscreen player takes over (a waiting engine is taken by the fullscreen player itself)
            dismiss(true);
            return;
        }

        if (sVideo == null || !MiniPlayerData.instance(activity).isEnabled()) {
            return;
        }

        if (sPlayerView != null && sHost == activity) {
            return;
        }

        // Moving to another browse screen: remember position and recreate the window there
        savePositionAndDestroyView(false);

        sPendingHost = activity;
        sStartDeadline = SystemClock.uptimeMillis() + START_MAX_WAIT_MS;
        Utils.removeCallbacks(sStartRunnable);
        Utils.postDelayed(sStartRunnable, START_FIRST_CHECK_MS);
    }

    public static void onActivityStopped(Activity activity) {
        if (sPendingHost == activity) {
            sPendingHost = null;
            Utils.removeCallbacks(sStartRunnable);
        }

        if (sHost == activity) {
            if (activity.isChangingConfigurations() || AppDialogPresenter.instance(activity).isDialogShown()) {
                // Temporary stop (rotation, opaque dialog on top): the window comes back on resume
                savePositionAndDestroyView(true);
            } else {
                // The user has left the app (Home, screen off, etc.): close the window for good
                dismiss();
            }
        }
    }

    /**
     * @return true when the event has been consumed
     */
    public static boolean handleKeyEvent(Activity activity, KeyEvent event) {
        if (sConsumeEnterUntilUp && isEnterKey(event.getKeyCode())) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                sConsumeEnterUntilUp = false;
            }
            return true;
        }

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
                if (isDown) {
                    if (event.getRepeatCount() > 0 && !sLongPressHandled) {
                        // Long press OK: close the window without starting the fullscreen playback
                        returnFocus();
                        dismiss();
                        sConsumeEnterUntilUp = true;
                    }
                } else if (event.getAction() == KeyEvent.ACTION_UP) {
                    if (!sLongPressHandled) {
                        expand();
                    }
                    sLongPressHandled = false;
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

    private static boolean isEnterKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    /**
     * Called before the fullscreen player starts. While the mini window is visible and the option is on,
     * the chosen video replaces the one in the window instead of opening fullscreen.
     *
     * @return true when the video has been taken over by the mini window
     */
    private static boolean onOpenVideo(Video video) {
        Activity host = sHost;

        if (video == null || host == null || sPlayerView == null || sContainer == null || host instanceof PlaybackActivity ||
                host.isFinishing() || host.isDestroyed()) {
            return false;
        }

        MiniPlayerData data = MiniPlayerData.instance(host);

        if (!data.isEnabled() || !data.isPlayInMiniEnabled()) {
            return false;
        }

        sVideo = Video.from(video);
        sPositionMs = Math.max(video.pendingPosMs, 0);
        sLongPressHandled = false;

        // Recreate the window with the new video. It keeps the focus state of the browse screen untouched.
        boolean hadFocus = sContainer.hasFocus();
        createView(host);

        if (hadFocus && sContainer != null) {
            sContainer.requestFocus();
        }

        return true;
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
        dismiss(false);
    }

    /**
     * @param keepEngine don't release the engine that waits for the next window (fullscreen player takes it)
     */
    private static void dismiss(boolean keepEngine) {
        Utils.removeCallbacks(sStartRunnable);
        sPendingHost = null;
        destroyView(false, false);
        if (!keepEngine) {
            PlayerHandoff.discard();
        }
        sVideo = null;
        sPositionMs = 0;
        sLongPressHandled = false;
    }

    private static void startPending() {
        Activity activity = sPendingHost;

        if (activity == null || sVideo == null || activity.isFinishing() || activity.isDestroyed()) {
            sPendingHost = null;
            return;
        }

        // Fullscreen playback is still releasing its engine. Check again shortly
        PlaybackPresenter presenter = PlaybackPresenter.instance(activity);
        if (presenter.getView() != null && !presenter.getView().isEmbed() && presenter.isEngineInitialized()) {
            if (SystemClock.uptimeMillis() < sStartDeadline) {
                Utils.postDelayed(sStartRunnable, START_RETRY_MS);
            } else {
                sPendingHost = null; // fullscreen playback is really running. Don't interfere
            }
            return;
        }

        sPendingHost = null;
        createView(activity);
    }

    private static void createView(Activity activity) {
        ViewGroup root = activity.findViewById(android.R.id.content);

        if (root == null) {
            return;
        }

        boolean seamless = MiniPlayerData.instance(activity).isSeamlessEnabled();

        sRecreating = true;
        try {
            destroyView(seamless, false); // full mode: the running engine goes to the new window
        } finally {
            sRecreating = false;
        }

        MiniPlayerData data = MiniPlayerData.instance(activity);

        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int width = (int) (screenWidth * data.getSizePercent() / 100f);
        int height = width * 9 / 16;
        int margin = (int) (activity.getResources().getDisplayMetrics().density * 24);

        float density = activity.getResources().getDisplayMetrics().density;
        final float radius = density * 14;
        final int frame = Math.round(density * 3);

        FrameLayout container = new FrameLayout(activity);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFF121418);
        background.setCornerRadius(radius);
        background.setStroke(Math.max(1, Math.round(density * 1.5f)), 0x40FFFFFF);
        container.setBackground(background);
        container.setPadding(frame, frame, frame, frame);
        container.setElevation(density * 12);
        container.setClipToOutline(true);
        container.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        container.setFocusable(true);
        container.setFocusableInTouchMode(false);
        container.setClickable(true);
        container.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        container.setOnClickListener(v -> expand());
        container.setOnFocusChangeListener((v, hasFocus) -> onWindowFocusChanged(v, hasFocus));

        EmbedPlayerView playerView = inflatePlayerView(activity);
        playerView.setQuality(EmbedPlayerView.QUALITY_NORMAL);
        playerView.setVideoFormat(getVideoFormat(data.getQuality()));
        playerView.setUseController(false);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setFocusable(false);
        container.addView(playerView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Loading indicator until the first frames of the new video are shown
        ProgressBar spinner = createSpinner(activity, height);
        container.addView(spinner);
        playerView.setOnShownListener(() -> spinner.setVisibility(View.GONE));
        // Don't spin forever when the video can't be loaded
        spinner.postDelayed(() -> spinner.setVisibility(View.GONE), SPINNER_MAX_MS);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height, getGravity(data.getPosition()));
        lp.setMargins(margin, margin, margin, margin);
        root.addView(container, lp);

        // Keep the rows out of the window: the rows end above the window (or start below it). The side menu keeps its full height.
        int gap = (int) (activity.getResources().getDisplayMetrics().density * 12);
        shiftContent(root, container, height + margin + gap, isBottomPosition(data.getPosition()));

        sHost = activity;
        sContainer = container;
        sPlayerView = playerView;
        sPosition = data.getPosition();
        sPrevFocus = null;
        sPendingFocus = null;
        sFocusRoot = root;
        root.getViewTreeObserver().addOnGlobalFocusChangeListener(sFocusListener);
        applyTopBar(activity, true);

        if (!isBottomPosition(data.getPosition())) {
            final int reserved = height + margin + gap;
            final ViewGroup rootFinal = root;
            final View windowFinal = container;
            Runnable shiftTitle = () -> {
                if (sContainer == windowFinal) {
                    restoreTitleParts();
                    shiftTitleParts(rootFinal, windowFinal, reserved);
                }
            };
            root.post(shiftTitle);
            root.postDelayed(shiftTitle, 400); // the top bar may be laid out later
        }

        Video video = Video.from(sVideo);
        video.pendingPosMs = sPositionMs;

        try {
            boolean adopted = false;

            if (seamless) {
                // Full mode: show the engine that is already playing this video (no reload)
                PlayerHandoff.Engine engine = PlayerHandoff.take(sVideo.videoId);
                if (engine != null) {
                    adopted = playerView.adoptEngine(engine, engine.video != null ? engine.video : video);
                    if (!adopted) {
                        PlayerHandoff.release(engine);
                    }
                }
            }

            if (!adopted) {
                playerView.openVideo(video);
            }
        } catch (Exception e) {
            Log.e(TAG, "Mini player failed to start: %s", e.getMessage());
            dismiss();
        }
    }

    private static EmbedPlayerView inflatePlayerView(Activity activity) {
        try {
            View view = LayoutInflater.from(activity).inflate(com.liskovsoft.smartyoutubetv2.tv.R.layout.mini_player_video, null);

            if (view instanceof EmbedPlayerView) {
                return (EmbedPlayerView) view;
            }
        } catch (Exception e) {
            Log.e(TAG, "Can't inflate the mini player view: %s", e.getMessage());
        }

        return new EmbedPlayerView(activity);
    }

    private static ProgressBar createSpinner(Activity activity, int windowHeight) {
        ProgressBar spinner = new ProgressBar(activity, null, android.R.attr.progressBarStyleLarge);
        spinner.setIndeterminate(true);
        spinner.setFocusable(false);

        Drawable custom = LoadingSpinnerDrawable.createFromPrefs(activity);
        spinner.setIndeterminateDrawable(custom != null ? custom :
                ContextCompat.getDrawable(activity, com.liskovsoft.smartyoutubetv2.tv.R.drawable.progress_large_holo));

        // Fit the indicator to the small window (the app-wide spinner size may be too big for it)
        int size = Math.max(windowHeight / 3, (int) (activity.getResources().getDisplayMetrics().density * 24));
        spinner.setLayoutParams(new FrameLayout.LayoutParams(size, size, Gravity.CENTER));

        return spinner;
    }

    private static boolean isBottomPosition(int position) {
        return position == MiniPlayerData.POSITION_BOTTOM_RIGHT || position == MiniPlayerData.POSITION_BOTTOM_LEFT;
    }

    /**
     * Reserves the strip of the screen occupied by the window so the rows are laid out next to it, not behind it.
     */
    private static void shiftContent(ViewGroup root, View window, int reservedPx, boolean atBottom) {
        restoreContent();

        List<View> targets = new ArrayList<>();
        View rows = root.findViewById(androidx.leanback.R.id.browse_container_dock);

        if (rows != null) {
            // Browse screen: move only the rows (and the top bar when the window is on top). The side menu is not squeezed.
            targets.add(rows);
        } else {
            // Other screens: move everything
            for (int i = 0; i < root.getChildCount(); i++) {
                View child = root.getChildAt(i);
                if (child != window) {
                    targets.add(child);
                }
            }
        }

        for (View child : targets) {
            if (!(child.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) {
                continue;
            }

            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
            sShiftedViews.add(new ShiftedView(child, params.topMargin, params.bottomMargin));

            if (atBottom) {
                params.bottomMargin += reservedPx;
            } else {
                params.topMargin += reservedPx;
            }

            child.setLayoutParams(params);
        }
    }

    // Parts of the top bar (logo+clock / buttons) that were moved down to stay clear of the window
    private static final List<WeakReference<View>> sTitleShifted = new ArrayList<>();

    /**
     * Moves down only the top bar parts that are on the same side as the window (usually the logo and clock).
     * The search/account buttons on the other side stay at the top, so they never cover the side menu.
     */
    private static void shiftTitleParts(ViewGroup root, View window, int reservedPx) {
        View title = root.findViewById(com.liskovsoft.smartyoutubetv2.tv.R.id.browse_title_group);

        if (!(title instanceof ViewGroup) || title.getVisibility() == View.GONE) {
            return;
        }

        ViewGroup titleGroup = (ViewGroup) title;
        int rootWidth = root.getWidth() > 0 ? root.getWidth() : root.getResources().getDisplayMetrics().widthPixels;
        boolean windowOnRight = isRight();

        for (int i = 0; i < titleGroup.getChildCount(); i++) {
            View part = titleGroup.getChildAt(i);

            if (part.getVisibility() == View.GONE || part.getWidth() <= 0) {
                continue;
            }

            int[] loc = new int[2];
            part.getLocationOnScreen(loc);
            float centerX = loc[0] + part.getWidth() / 2f;
            boolean partOnRight = centerX > rootWidth / 2f;

            if (partOnRight == windowOnRight) {
                part.setTranslationY(reservedPx);
                sTitleShifted.add(new WeakReference<>(part));
            }
        }
    }

    private static void restoreTitleParts() {
        for (WeakReference<View> ref : sTitleShifted) {
            View part = ref.get();
            if (part != null) {
                part.setTranslationY(0);
            }
        }

        sTitleShifted.clear();
    }

    /**
     * Tells the browse screen that the mini window appeared/disappeared (the top bar may need to hide/show).
     */
    private static void applyTopBar(Activity activity, boolean miniVisible) {
        if (!(activity instanceof FragmentActivity)) {
            return;
        }

        try {
            Fragment fragment = ((FragmentActivity) activity).getSupportFragmentManager()
                    .findFragmentById(com.liskovsoft.smartyoutubetv2.tv.R.id.main_frame);

            if (fragment instanceof BrowseFragment) {
                ((BrowseFragment) fragment).applyTopBarMode(miniVisible);
            }
        } catch (Exception e) {
            Log.e(TAG, "Can't update the top bar: %s", e.getMessage());
        }
    }

    private static void restoreContent() {
        restoreTitleParts();

        for (ShiftedView shifted : sShiftedViews) {
            View view = shifted.view.get();

            if (view != null && view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
                params.topMargin = shifted.topMargin;
                params.bottomMargin = shifted.bottomMargin;
                view.setLayoutParams(params);
            }
        }

        sShiftedViews.clear();
    }

    private static void onWindowFocusChanged(View view, boolean hasFocus) {
        if (hasFocus) {
            GradientDrawable border = new GradientDrawable();
            border.setColor(Color.TRANSPARENT);
            float density = view.getResources().getDisplayMetrics().density;
            border.setCornerRadius(density * 14);
            border.setStroke((int) (density * 4), Color.WHITE);
            ((FrameLayout) view).setForeground(border);
            view.animate().scaleX(1.06f).scaleY(1.06f).setDuration(120).start();
        } else {
            ((FrameLayout) view).setForeground(null);
            view.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
        }
    }

    /**
     * @param pause full mode: pause the engine while the window is away
     */
    private static void savePositionAndDestroyView(boolean pause) {
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

        boolean seamless = sHost != null && MiniPlayerData.instance(sHost).isSeamlessEnabled();
        destroyView(seamless, pause);
    }

    /**
     * @param keepEngine full mode: don't release the running engine, keep it for the next window
     * @param pause pause the kept engine while it has no window
     */
    private static void destroyView(boolean keepEngine, boolean pause) {
        Utils.removeCallbacks(sJumpRunnable);
        sPendingFocus = null;

        if (sFocusRoot != null) {
            ViewTreeObserver observer = sFocusRoot.getViewTreeObserver();
            if (observer.isAlive()) {
                observer.removeOnGlobalFocusChangeListener(sFocusListener);
            }
            sFocusRoot = null;
        }

        restoreContent();

        if (sHost != null && !sRecreating) {
            applyTopBar(sHost, false);
        }

        if (sContainer != null) {
            ViewGroup parent = (ViewGroup) sContainer.getParent();
            if (parent != null) {
                parent.removeView(sContainer);
            }
        }

        if (sPlayerView != null) {
            if (keepEngine) {
                try {
                    PlayerHandoff.Engine kept = sPlayerView.detachEngine(pause);
                    if (kept != null) {
                        PlayerHandoff.put(kept);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Can't keep the mini player engine: %s", e.getMessage());
                }
            }
            sPlayerView.finish(); // releases the engine unless it has been taken out
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

        // Fullscreen starts from the stream urls already loaded by the mini window
        FormatInfoPrefetcher.keepForHandoff(video.videoId);

        // Full mode: the running engine moves to the fullscreen player (no reload)
        if (host != null && MiniPlayerData.instance(host).isSeamlessEnabled()) {
            try {
                PlayerHandoff.Engine engine = sPlayerView.detachEngine(false);
                if (engine != null) {
                    if (engine.video != null) {
                        PlayerHandoff.put(engine);
                        video = engine.video;
                        video.pendingPosMs = 0; // the engine is already at the right position
                    } else {
                        PlayerHandoff.release(engine);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Can't hand the engine to the fullscreen player: %s", e.getMessage());
            }
        }

        dismiss(true);

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
