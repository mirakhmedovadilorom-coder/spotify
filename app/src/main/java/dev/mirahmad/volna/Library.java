package dev.mirahmad.volna;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Каталог треков (assets/tracks.json + добавленные пользователем) и «любимые». */
public final class Library {
    private static final List<Track> all = new ArrayList<>();
    private static final Set<String> likes = new HashSet<>();
    private static SharedPreferences sp;
    private static boolean inited;

    private Library() {}

    public static synchronized void init(Context c) {
        if (inited) return;
        inited = true;
        Context app = c.getApplicationContext();
        sp = app.getSharedPreferences("volna", Context.MODE_PRIVATE);
        try {
            JSONArray a = new JSONArray(readAsset(app, "tracks.json"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                all.add(new Track(o.getString("id"), o.getString("title"), o.getString("artist"),
                        o.getString("src"), null, (long) (o.optDouble("duration", 0) * 1000)));
            }
        } catch (Exception ignored) {
        }
        try {
            JSONArray a = new JSONArray(sp.getString("user_tracks", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                all.add(new Track(o.getString("id"), o.getString("title"), o.getString("artist"),
                        null, o.getString("uri"), o.optLong("dur", 0)));
            }
        } catch (Exception ignored) {
        }
        likes.addAll(sp.getStringSet("likes", new HashSet<String>()));
    }

    public static synchronized List<Track> all() {
        return new ArrayList<>(all);
    }

    public static synchronized List<Track> liked() {
        List<Track> r = new ArrayList<>();
        for (Track t : all) if (likes.contains(t.id)) r.add(t);
        return r;
    }

    public static synchronized boolean isLiked(String id) {
        return likes.contains(id);
    }

    public static synchronized void toggleLike(String id) {
        if (!likes.remove(id)) likes.add(id);
        sp.edit().putStringSet("likes", new HashSet<>(likes)).apply();
    }

    /** Добавляет выбранные пользователем аудиофайлы. Возвращает, сколько добавлено. */
    public static synchronized int addUserTracks(Context c, List<Uri> uris) {
        int added = 0;
        for (Uri u : uris) {
            boolean dup = false;
            for (Track t : all) if (u.toString().equals(t.uri)) dup = true;
            if (dup) continue;
            String name = displayName(c.getContentResolver(), u);
            String title = name, artist = "Мои треки";
            long dur = 0;
            MediaMetadataRetriever mr = new MediaMetadataRetriever();
            try {
                mr.setDataSource(c, u);
                String t = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                String a = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                String d = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (t != null && !t.trim().isEmpty()) title = t.trim();
                if (a != null && !a.trim().isEmpty()) artist = a.trim();
                if (d != null) dur = Long.parseLong(d);
            } catch (Exception ignored) {
            } finally {
                try { mr.release(); } catch (Exception ignored) {}
            }
            // «Исполнитель - Название» из имени файла, если в тегах пусто
            if (artist.equals("Мои треки") && title.contains(" - ")) {
                int k = title.indexOf(" - ");
                artist = title.substring(0, k).trim();
                title = title.substring(k + 3).trim();
            }
            all.add(new Track("u" + System.nanoTime(), title, artist, null, u.toString(), dur));
            added++;
        }
        saveUser();
        return added;
    }

    public static synchronized void removeUserTrack(Track t) {
        if (!t.isUser()) return;
        all.remove(t);
        likes.remove(t.id);
        sp.edit().putStringSet("likes", new HashSet<>(likes)).apply();
        saveUser();
    }

    private static void saveUser() {
        JSONArray a = new JSONArray();
        try {
            for (Track t : all) {
                if (!t.isUser()) continue;
                JSONObject o = new JSONObject();
                o.put("id", t.id);
                o.put("title", t.title);
                o.put("artist", t.artist);
                o.put("uri", t.uri);
                o.put("dur", t.durationMs);
                a.put(o);
            }
        } catch (Exception ignored) {
        }
        sp.edit().putString("user_tracks", a.toString()).apply();
    }

    private static String displayName(ContentResolver cr, Uri u) {
        String name = null;
        try (Cursor cur = cr.query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) name = cur.getString(0);
        } catch (Exception ignored) {
        }
        if (name == null) name = "Трек";
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return name.replace('_', ' ').trim();
    }

    private static String readAsset(Context c, String path) throws Exception {
        try (InputStream in = c.getAssets().open(path)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }
}
