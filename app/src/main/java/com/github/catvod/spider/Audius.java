package com.github.catvod.spider;

import android.text.TextUtils;
import android.util.Log;

import com.github.catvod.crawler.Spider;
import com.github.tvbox.osc.server.ControlManager;
import com.lzy.okgo.OkGo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;

import okhttp3.Response;

/**
 * Read-only Audius music source for the Sharp Android 4.4 build.
 *
 * The adapter is compiled into the APK so legacy safe mode can remain enabled;
 * no configuration-supplied dex or JavaScript is executed.
 */
public class Audius extends Spider {
    private static final String TAG = "AudiusSource";
    private static final String API = "https://api.audius.co/v1";
    private static final String APP_NAME = "sharp_tv_music";
    private static final int PAGE_SIZE = 20;

    private static final String[][] CATEGORIES = new String[][]{
            {"all", "热门"},
            {"Pop", "流行"},
            {"Electronic", "电子"},
            {"Rock", "摇滚"},
            {"Hip-Hop/Rap", "嘻哈"},
            {"Ambient", "氛围"},
            {"Classical", "古典"},
            {"Jazz", "爵士"},
            {"Lo-Fi", "Lo-Fi"}
    };

    @Override
    public String homeContent(boolean filter) {
        JSONObject result = new JSONObject();
        try {
            JSONArray classes = new JSONArray();
            for (String[] category : CATEGORIES) {
                JSONObject item = new JSONObject();
                item.put("type_id", category[0]);
                item.put("type_name", category[1]);
                classes.put(item);
            }
            result.put("class", classes);
            result.put("list", fetchTracks("/tracks/trending", null, 0));
        } catch (Throwable error) {
            Log.e(TAG, "home failed", error);
        }
        return result.toString();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        int page = positiveInt(pg, 1);
        JSONObject result = pageResult(page);
        try {
            result.put("list", fetchTracks("/tracks/trending", "all".equals(tid) ? null : tid,
                    (page - 1) * PAGE_SIZE));
        } catch (Throwable error) {
            Log.e(TAG, "category failed", error);
        }
        return result.toString();
    }

    @Override
    public String searchContent(String key, boolean quick) {
        JSONObject result = pageResult(1);
        try {
            Response response = OkGo.<String>get(API + "/tracks/search")
                    .headers("User-Agent", "SharpTVMusic/1.0")
                    .params("query", key)
                    .params("limit", quick ? 10 : PAGE_SIZE)
                    .params("app_name", APP_NAME)
                    .execute();
            result.put("list", parseTrackList(response));
        } catch (Throwable error) {
            Log.e(TAG, "search failed", error);
        }
        return result.toString();
    }

    @Override
    public String detailContent(List<String> ids) {
        JSONObject result = pageResult(1);
        JSONArray list = new JSONArray();
        try {
            if (ids != null && !ids.isEmpty()) {
                Response response = OkGo.<String>get(API + "/tracks/" + ids.get(0))
                        .headers("User-Agent", "SharpTVMusic/1.0")
                        .params("app_name", APP_NAME)
                        .execute();
                JSONObject root = checkedJson(response);
                JSONObject track = root.optJSONObject("data");
                if (track != null) list.put(toVod(track, true));
            }
        } catch (Throwable error) {
            Log.e(TAG, "detail failed", error);
        }
        try {
            result.put("list", list);
        } catch (Throwable ignored) {
        }
        return result.toString();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        JSONObject result = new JSONObject();
        try {
            result.put("parse", 0);
            String localBase = ControlManager.get().getAddress(true);
            result.put("url", localBase + "audio/" + id);
        } catch (Throwable error) {
            Log.e(TAG, "player failed", error);
        }
        return result.toString();
    }

    private JSONArray fetchTracks(String path, String genre, int offset) throws Exception {
        com.lzy.okgo.request.GetRequest<String> request = OkGo.<String>get(API + path)
                .headers("User-Agent", "SharpTVMusic/1.0")
                .params("limit", PAGE_SIZE)
                .params("offset", offset)
                .params("app_name", APP_NAME);
        if (!TextUtils.isEmpty(genre)) request.params("genre", genre);
        return parseTrackList(request.execute());
    }

    private JSONArray parseTrackList(Response response) throws Exception {
        JSONObject root = checkedJson(response);
        JSONArray data = root.optJSONArray("data");
        JSONArray list = new JSONArray();
        if (data == null) return list;
        for (int i = 0; i < data.length(); i++) {
            JSONObject track = data.optJSONObject(i);
            if (track == null || !track.optBoolean("is_streamable", true)) continue;
            list.put(toVod(track, false));
        }
        return list;
    }

    private JSONObject checkedJson(Response response) throws Exception {
        if (response == null || !response.isSuccessful() || response.body() == null) {
            int code = response == null ? -1 : response.code();
            throw new IllegalStateException("Audius HTTP " + code);
        }
        String body = response.body().string();
        if (TextUtils.isEmpty(body)) throw new IllegalStateException("Audius returned an empty body");
        return new JSONObject(body);
    }

    private JSONObject toVod(JSONObject track, boolean detail) throws Exception {
        JSONObject vod = new JSONObject();
        JSONObject user = track.optJSONObject("user");
        JSONObject artwork = track.optJSONObject("artwork");
        String id = track.optString("id");
        String artist = user == null ? "未知音乐人" : user.optString("name", "未知音乐人");
        String title = track.optString("title", "未命名曲目");
        String duration = formatDuration(track.optInt("duration", 0));
        String genre = track.optString("genre", "音乐");
        String picture = "";
        if (artwork != null) {
            picture = artwork.optString("480x480", artwork.optString("150x150", ""));
        }

        vod.put("vod_id", id);
        vod.put("vod_name", title);
        vod.put("vod_pic", picture);
        vod.put("vod_remarks", artist + (duration.isEmpty() ? "" : " · " + duration));
        vod.put("vod_actor", artist);
        vod.put("vod_director", artist);
        vod.put("vod_class", genre);
        vod.put("type_name", genre);
        if (detail) {
            String description = track.optString("description", "");
            vod.put("vod_content", TextUtils.isEmpty(description)
                    ? "Audius 在线曲目 · " + artist
                    : description);
            vod.put("vod_play_from", "Audius");
            vod.put("vod_play_url", "播放$" + id);
        }
        return vod;
    }

    private JSONObject pageResult(int page) {
        JSONObject result = new JSONObject();
        try {
            result.put("code", 1);
            result.put("page", page);
            result.put("pagecount", 100);
            result.put("limit", String.valueOf(PAGE_SIZE));
            result.put("total", PAGE_SIZE * 100);
            result.put("list", new JSONArray());
        } catch (Throwable ignored) {
        }
        return result;
    }

    private int positiveInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private String formatDuration(int seconds) {
        if (seconds <= 0) return "";
        int minutes = seconds / 60;
        int rest = seconds % 60;
        return String.format(java.util.Locale.US, "%d:%02d", minutes, rest);
    }
}
