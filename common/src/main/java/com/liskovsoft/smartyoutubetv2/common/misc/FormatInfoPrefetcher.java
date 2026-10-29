package com.liskovsoft.smartyoutubetv2.common.misc;

import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.LinkedHashMap;
import java.util.Map;

import io.reactivex.disposables.Disposable;

/**
 * Fetches video format info (stream urls) in advance, when the user focuses a card.
 * When the video is opened, the data is already there and playback starts almost instantly.
 */
public class FormatInfoPrefetcher {
    private static final long FOCUS_DELAY_MS = 400;          // don't fetch while the user is scrolling fast
    private static final long MAX_AGE_MS = 20 * 60 * 1_000;  // stream urls live for hours; stay on the safe side
    private static final int MAX_ENTRIES = 6;

    private static final Map<String, Entry> sCache = new LinkedHashMap<>();
    private static Disposable sAction;
    private static String sPendingId;
    private static final Runnable sStart = FormatInfoPrefetcher::startFetch;

    private static class Entry {
        final MediaItemFormatInfo info;
        final long time = System.currentTimeMillis();

        Entry(MediaItemFormatInfo info) {
            this.info = info;
        }
    }

    /** Call when a card gets focus. */
    public static void prefetch(Video item) {
        if (item == null || item.videoId == null || !item.hasVideo() || item.isLive || item.isUpcoming) {
            return;
        }

        synchronized (FormatInfoPrefetcher.class) {
            Entry entry = sCache.get(item.videoId);
            if (entry != null && System.currentTimeMillis() - entry.time < MAX_AGE_MS) {
                return; // already have it
            }
            sPendingId = item.videoId;
        }

        Utils.removeCallbacks(sStart);
        Utils.postDelayed(sStart, FOCUS_DELAY_MS);
    }

    // Last format info that a player has actually loaded (used for the mini player <-> fullscreen handoff)
    private static String sLastId;
    private static MediaItemFormatInfo sLastInfo;
    private static long sLastTime;

    /** Called by the player each time it has loaded the format info of a video. Doesn't affect normal playback. */
    public static void noteLoaded(String videoId, MediaItemFormatInfo info) {
        if (videoId == null || info == null) {
            return;
        }

        synchronized (FormatInfoPrefetcher.class) {
            sLastId = videoId;
            sLastInfo = info;
            sLastTime = System.currentTimeMillis();
        }
    }

    /**
     * Call right before the video moves between the fullscreen player and the mini window.
     * The next open of the same video takes the stream urls from memory: no new network request, almost instant start.
     * The data is used only once (see {@link #take}), so a failed playback never reuses it.
     */
    public static void keepForHandoff(String videoId) {
        MediaItemFormatInfo info = null;

        synchronized (FormatInfoPrefetcher.class) {
            if (videoId != null && videoId.equals(sLastId) && System.currentTimeMillis() - sLastTime < MAX_AGE_MS) {
                info = sLastInfo;
            }
        }

        if (info != null) {
            put(videoId, info);
        }
    }

    /** Returns prefetched data (and removes it from the cache) or null. */
    public static MediaItemFormatInfo take(String videoId) {
        if (videoId == null) {
            return null;
        }

        synchronized (FormatInfoPrefetcher.class) {
            Entry entry = sCache.remove(videoId);
            if (entry != null && System.currentTimeMillis() - entry.time < MAX_AGE_MS) {
                return entry.info;
            }
        }

        return null;
    }

    private static void startFetch() {
        final String videoId;
        synchronized (FormatInfoPrefetcher.class) {
            videoId = sPendingId;
        }

        if (videoId == null) {
            return;
        }

        if (sAction != null && !sAction.isDisposed()) {
            sAction.dispose(); // only the last focused card matters
        }

        try {
            sAction = YouTubeServiceManager.instance().getMediaItemService().getFormatInfoObserve(videoId)
                    .subscribe(info -> put(videoId, info), error -> { /* NOP: the real request will retry */ });
        } catch (Exception e) {
            // NOP
        }
    }

    private static void put(String videoId, MediaItemFormatInfo info) {
        if (info == null || info.isUnplayable() || info.isLive()) {
            return;
        }

        synchronized (FormatInfoPrefetcher.class) {
            sCache.put(videoId, new Entry(info));
            while (sCache.size() > MAX_ENTRIES) {
                sCache.remove(sCache.keySet().iterator().next());
            }
        }
    }
}
