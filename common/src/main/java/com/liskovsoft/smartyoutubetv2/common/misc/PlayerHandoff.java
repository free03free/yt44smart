package com.liskovsoft.smartyoutubetv2.common.misc;

import android.os.SystemClock;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.ExoPlayerController;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.ExoPlayerInitializer;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

/**
 * "Full mode" of the mini player: the same running ExoPlayer moves between the fullscreen player and the mini window.
 * <br>
 * The window that gives up the engine detaches it (the engine keeps its media, buffer and position) and puts it here.
 * The window that opens next takes it and shows it: no new player, no new network request, no reload.
 * <br>
 * Safety: an engine that nobody takes within {@link #EXPIRE_MS} is released, so a live player never leaks.
 */
public final class PlayerHandoff {
    private static final String TAG = PlayerHandoff.class.getSimpleName();
    private static final long KEEP_REQUEST_MS = 5_000;
    private static final long EXPIRE_MS = 8_000;
    private static final long ADOPTED_MS = 3_000;

    /**
     * Everything that belongs to the running engine.
     */
    public static final class Engine {
        public final SimpleExoPlayer player;
        public final ExoPlayerController controller;
        public final ExoPlayerInitializer initializer;
        public final Video video;
        // The engine was paused on detach: resume it when somebody takes it
        final boolean resumeOnTake;

        Engine(SimpleExoPlayer player, ExoPlayerController controller, ExoPlayerInitializer initializer, Video video,
               boolean resumeOnTake) {
            this.player = player;
            this.controller = controller;
            this.initializer = initializer;
            this.video = video;
            this.resumeOnTake = resumeOnTake;
        }
    }

    private static Engine sEngine;
    private static long sKeepUntil;
    private static long sAdoptedUntil;
    private static final Runnable sExpire = PlayerHandoff::discard;

    private PlayerHandoff() {
    }

    // The fullscreen player is being closed with Back: don't release its engine, hand it over

    public static void requestKeep() {
        sKeepUntil = SystemClock.uptimeMillis() + KEEP_REQUEST_MS;
    }

    public static void cancelKeepRequest() {
        sKeepUntil = 0;
    }

    /**
     * @return true once after {@link #requestKeep()}
     */
    public static boolean consumeKeepRequest() {
        boolean requested = sKeepUntil != 0 && SystemClock.uptimeMillis() < sKeepUntil;
        sKeepUntil = 0;
        return requested;
    }

    /**
     * Detaches the engine from its window. The engine is not released and not stopped.
     * It stops reporting events to the presenter until somebody takes it.
     *
     * @param pause pause the engine while it has no window (it is resumed when taken)
     */
    public static Engine detach(SimpleExoPlayer player, ExoPlayerController controller, ExoPlayerInitializer initializer,
                                Video video, boolean pause) {
        if (player == null || controller == null || initializer == null) {
            return null;
        }

        boolean resume = false;

        controller.detachForHandoff();

        try {
            if (pause && player.getPlayWhenReady()) {
                player.setPlayWhenReady(false);
                resume = true;
            }
            player.clearVideoSurface();
        } catch (Exception e) {
            Log.e(TAG, "Can't detach the engine cleanly: %s", e.getMessage());
        }

        return new Engine(player, controller, initializer, video, resume);
    }

    /**
     * Keeps the detached engine until somebody takes it.
     */
    public static void put(Engine engine) {
        discard();

        if (engine == null) {
            return;
        }

        sEngine = engine;
        Utils.removeCallbacks(sExpire);
        Utils.postDelayed(sExpire, EXPIRE_MS);
    }

    /**
     * @return the engine when it plays the same video, otherwise null (a different engine is released)
     */
    public static Engine take(String videoId) {
        Engine engine = sEngine;

        if (engine == null) {
            return null;
        }

        sEngine = null;
        Utils.removeCallbacks(sExpire);

        if (videoId != null && engine.video != null && videoId.equals(engine.video.videoId)) {
            if (engine.resumeOnTake) {
                try {
                    engine.player.setPlayWhenReady(true);
                } catch (Exception e) {
                    Log.e(TAG, "Can't resume the engine: %s", e.getMessage());
                }
            }

            return engine;
        }

        release(engine);

        return null;
    }

    public static boolean hasEngine() {
        return sEngine != null;
    }

    /**
     * Releases the engine that nobody has taken.
     */
    public static void discard() {
        Engine engine = sEngine;
        sEngine = null;
        Utils.removeCallbacks(sExpire);

        if (engine != null) {
            release(engine);
        }
    }

    public static void release(Engine engine) {
        if (engine == null) {
            return;
        }

        try {
            engine.controller.release(); // releases the player too
        } catch (Exception e) {
            Log.e(TAG, "Can't release the controller: %s", e.getMessage());
        }

        try {
            engine.initializer.release();
        } catch (Exception e) {
            Log.e(TAG, "Can't release the initializer: %s", e.getMessage());
        }
    }

    // The window that took the engine: tells the controllers not to load the video again

    public static void markAdopted() {
        sAdoptedUntil = SystemClock.uptimeMillis() + ADOPTED_MS;
    }

    /**
     * @return true once after {@link #markAdopted()}
     */
    public static boolean consumeAdopted() {
        boolean adopted = sAdoptedUntil != 0 && SystemClock.uptimeMillis() < sAdoptedUntil;
        sAdoptedUntil = 0;
        return adopted;
    }
}
