package dev.mirahmad.volna;

/** Один трек: либо из assets (assetPath), либо добавленный пользователем (uri). */
public class Track {
    public final String id;
    public final String title;
    public final String artist;
    public final String assetPath; // например "audio/01.mp3", либо null
    public final String uri;       // content:// uri пользовательского файла, либо null
    public final long durationMs;

    public Track(String id, String title, String artist, String assetPath, String uri, long durationMs) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.assetPath = assetPath;
        this.uri = uri;
        this.durationMs = durationMs;
    }

    public boolean isUser() {
        return uri != null;
    }
}
