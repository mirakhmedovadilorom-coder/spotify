package dev.mirahmad.volna;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;

/** Держит процесс живым в фоне, показывает уведомление с кнопками и обновляет MediaSession. */
public class PlayerService extends Service implements Player.Listener {
    private static final String CHANNEL = "playback";
    private static final int NOTIF_ID = 1;
    private static final String A_PREV = "dev.mirahmad.volna.PREV";
    private static final String A_TOGGLE = "dev.mirahmad.volna.TOGGLE";
    private static final String A_NEXT = "dev.mirahmad.volna.NEXT";
    private static final String A_STOP = "dev.mirahmad.volna.STOP";

    private Player player;
    private MediaSession session;
    private NotificationManager nm;
    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { player.pause(); } // выдернули наушники
    };

    @Override
    public void onCreate() {
        super.onCreate();
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Воспроизведение", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        player = Player.get(this);
        session = new MediaSession(this, "Volna");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { if (!player.isPlaying()) player.toggle(); }
            @Override public void onPause() { player.pause(); }
            @Override public void onSkipToNext() { player.next(); }
            @Override public void onSkipToPrevious() { player.prev(); }
            @Override public void onSeekTo(long pos) { player.seekTo((int) pos); }
        });
        session.setActive(true);
        player.addListener(this);
        registerReceiver(noisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent != null ? intent.getAction() : null;
        if (A_PREV.equals(a)) player.prev();
        else if (A_TOGGLE.equals(a)) player.toggle();
        else if (A_NEXT.equals(a)) player.next();
        else if (A_STOP.equals(a)) {
            player.stopAll();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        Notification n = build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }
        updateSession();
        return START_NOT_STICKY;
    }

    @Override
    public void onChanged() {
        if (player.current() == null) return;
        updateSession();
        nm.notify(NOTIF_ID, build());
    }

    private void updateSession() {
        Track t = player.current();
        if (t == null) return;
        session.setMetadata(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, t.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, t.artist)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, player.duration())
                .build());
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO;
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(actions)
                .setState(player.isPlaying() ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                        player.position(), 1f)
                .build());
    }

    private PendingIntent action(String a, int code) {
        Intent i = new Intent(this, PlayerService.class).setAction(a);
        return PendingIntent.getService(this, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Notification.Action act(int icon, String title, String a, int code) {
        return new Notification.Action.Builder(Icon.createWithResource(this, icon), title, action(a, code)).build();
    }

    private Notification build() {
        Track t = player.current();
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_note)
                .setContentTitle(t != null ? t.title : "Волна")
                .setContentText(t != null ? t.artist : "")
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(player.isPlaying())
                .setOnlyAlertOnce(true)
                .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
                .addAction(act(R.drawable.ic_prev, "Назад", A_PREV, 1))
                .addAction(act(player.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play,
                        player.isPlaying() ? "Пауза" : "Играть", A_TOGGLE, 2))
                .addAction(act(R.drawable.ic_next, "Вперёд", A_NEXT, 3))
                .addAction(act(R.drawable.ic_close, "Закрыть", A_STOP, 4))
                .setStyle(new Notification.MediaStyle()
                        .setMediaSession(session.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2));
        if (t != null) b.setLargeIcon(new CoverDrawable(t.title, 0.12f).toBitmap(256));
        return b.build();
    }

    @Override
    public void onDestroy() {
        player.removeListener(this);
        try { unregisterReceiver(noisy); } catch (Exception ignored) {}
        session.release();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
