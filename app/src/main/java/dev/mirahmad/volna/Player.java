package dev.mirahmad.volna;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.PowerManager;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Синглтон-плеер: очередь, shuffle, repeat, аудиофокус. Живёт, пока жив процесс. */
public final class Player implements AudioManager.OnAudioFocusChangeListener {
    public interface Listener {
        void onChanged();
    }

    public static final int REPEAT_OFF = 0, REPEAT_ALL = 1, REPEAT_ONE = 2;

    private static Player inst;

    public static synchronized Player get(Context c) {
        if (inst == null) inst = new Player(c.getApplicationContext());
        return inst;
    }

    private final Context app;
    private final AudioManager am;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private MediaPlayer mp;
    private boolean prepared;
    private boolean resumeOnFocus;
    private List<Track> ctx = new ArrayList<>();
    private final List<Integer> order = new ArrayList<>();
    private int pos = -1;
    private boolean shuffle;
    private int repeat = REPEAT_OFF;

    private Player(Context app) {
        this.app = app;
        this.am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
    }

    // ---------- слушатели ----------
    public void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }
    private void changed() { for (Listener l : listeners) l.onChanged(); }

    // ---------- состояние ----------
    public Track current() {
        return (pos >= 0 && pos < order.size()) ? ctx.get(order.get(pos)) : null;
    }
    public boolean isPlaying() { return mp != null && prepared && mp.isPlaying(); }
    public boolean isShuffle() { return shuffle; }
    public int getRepeat() { return repeat; }

    public int position() {
        try { return (mp != null && prepared) ? mp.getCurrentPosition() : 0; } catch (Exception e) { return 0; }
    }

    public int duration() {
        try {
            if (mp != null && prepared) return mp.getDuration();
        } catch (Exception ignored) {
        }
        Track t = current();
        return t != null ? (int) t.durationMs : 0;
    }

    // ---------- управление ----------
    public void playList(List<Track> list, int index) {
        if (list.isEmpty()) return;
        ctx = new ArrayList<>(list);
        buildOrder(Math.max(0, Math.min(index, ctx.size() - 1)));
        ensureService();
        load(true);
    }

    public void playShuffled(List<Track> list) {
        if (list.isEmpty()) return;
        shuffle = true;
        playList(list, (int) (Math.random() * list.size()));
    }

    public void toggle() {
        if (mp == null || !prepared) return;
        if (mp.isPlaying()) {
            mp.pause();
            changed();
        } else {
            if (requestFocus()) mp.start();
            changed();
        }
    }

    public void pause() {
        if (mp != null && prepared && mp.isPlaying()) {
            mp.pause();
            changed();
        }
    }

    public void next() {
        if (order.isEmpty()) return;
        pos = (pos + 1) % order.size();
        load(true);
    }

    public void prev() {
        if (order.isEmpty()) return;
        if (position() > 3000) {
            seekTo(0);
            return;
        }
        pos = pos > 0 ? pos - 1 : order.size() - 1;
        load(true);
    }

    public void seekTo(int ms) {
        if (mp != null && prepared) {
            mp.seekTo(ms);
            changed();
        }
    }

    public void toggleShuffle() {
        shuffle = !shuffle;
        Track cur = current();
        if (cur != null) buildOrder(ctx.indexOf(cur));
        changed();
    }

    public void cycleRepeat() {
        repeat = (repeat + 1) % 3;
        changed();
    }

    /** Полная остановка (кнопка «закрыть» в уведомлении). */
    public void stopAll() {
        if (mp != null) {
            try { mp.release(); } catch (Exception ignored) {}
            mp = null;
        }
        prepared = false;
        pos = -1;
        order.clear();
        ctx = new ArrayList<>();
        am.abandonAudioFocus(this);
        changed();
    }

    // ---------- внутреннее ----------
    private void buildOrder(int start) {
        order.clear();
        for (int i = 0; i < ctx.size(); i++) order.add(i);
        if (shuffle && !order.isEmpty()) {
            order.remove(Integer.valueOf(start));
            Collections.shuffle(order);
            order.add(0, start);
            pos = 0;
        } else {
            pos = start;
        }
    }

    private void ensureService() {
        ContextCompat.startForegroundService(app, new Intent(app, PlayerService.class));
    }

    private void load(boolean autoplay) {
        Track t = current();
        if (t == null) return;
        if (mp != null) {
            try { mp.release(); } catch (Exception ignored) {}
        }
        prepared = false;
        mp = new MediaPlayer();
        mp.setWakeMode(app, PowerManager.PARTIAL_WAKE_LOCK);
        mp.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
        mp.setOnPreparedListener(p -> {
            prepared = true;
            if (autoplay && requestFocus()) p.start();
            changed();
        });
        mp.setOnCompletionListener(p -> onCompleted());
        mp.setOnErrorListener((p, what, extra) -> {
            // битый файл — пропускаем
            if (order.size() > 1) next(); else changed();
            return true;
        });
        try {
            if (t.assetPath != null) {
                AssetFileDescriptor afd = app.getAssets().openFd(t.assetPath);
                mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
            } else {
                mp.setDataSource(app, Uri.parse(t.uri));
            }
            mp.prepareAsync();
        } catch (Exception e) {
            if (order.size() > 1) next();
        }
        changed(); // сразу показываем новый трек в UI
    }

    private void onCompleted() {
        if (repeat == REPEAT_ONE) {
            mp.seekTo(0);
            mp.start();
        } else if (pos + 1 < order.size()) {
            pos++;
            load(true);
        } else if (repeat == REPEAT_ALL) {
            pos = 0;
            load(true);
        } else {
            mp.seekTo(0); // конец очереди — стоим на нуле
            changed();
        }
    }

    @SuppressWarnings("deprecation")
    private boolean requestFocus() {
        return am.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    @Override
    public void onAudioFocusChange(int change) {
        if (mp == null || !prepared) return;
        switch (change) {
            case AudioManager.AUDIOFOCUS_LOSS:
                resumeOnFocus = false;
                pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                resumeOnFocus = mp.isPlaying();
                pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                mp.setVolume(0.25f, 0.25f);
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                mp.setVolume(1f, 1f);
                if (resumeOnFocus) {
                    resumeOnFocus = false;
                    mp.start();
                    changed();
                }
                break;
        }
    }
}
