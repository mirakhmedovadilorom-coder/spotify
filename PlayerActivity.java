package dev.mirahmad.volna;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/** Полноэкранный плеер. */
public class PlayerActivity extends AppCompatActivity implements Player.Listener {
    private Player player;
    private ImageView cover;
    private TextView title, artist, tvPos, tvDur;
    private ImageButton btnPlay, btnShuffle, btnRepeat, like;
    private SeekBar seek;
    private boolean dragging;
    private String shownId;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!dragging) updateProgress();
            handler.postDelayed(this, 400);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_player);
        player = Player.get(this);
        cover = findViewById(R.id.cover);
        title = findViewById(R.id.title);
        artist = findViewById(R.id.artist);
        tvPos = findViewById(R.id.tvPos);
        tvDur = findViewById(R.id.tvDur);
        btnPlay = findViewById(R.id.btnPlay);
        btnShuffle = findViewById(R.id.btnShuffle);
        btnRepeat = findViewById(R.id.btnRepeat);
        like = findViewById(R.id.like);
        seek = findViewById(R.id.seek);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnPlay.setOnClickListener(v -> player.toggle());
        findViewById(R.id.btnPrev).setOnClickListener(v -> player.prev());
        findViewById(R.id.btnNext).setOnClickListener(v -> player.next());
        btnShuffle.setOnClickListener(v -> player.toggleShuffle());
        btnRepeat.setOnClickListener(v -> player.cycleRepeat());
        like.setOnClickListener(v -> {
            Track t = player.current();
            if (t != null) {
                Library.toggleLike(t.id);
                onChanged();
            }
        });
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) tvPos.setText(TrackAdapter.fmt((long) player.duration() * p / 1000));
            }
            @Override public void onStartTrackingTouch(SeekBar s) { dragging = true; }
            @Override public void onStopTrackingTouch(SeekBar s) {
                dragging = false;
                player.seekTo((int) ((long) player.duration() * s.getProgress() / 1000));
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        player.addListener(this);
        handler.post(ticker);
        onChanged();
    }

    @Override
    protected void onStop() {
        super.onStop();
        player.removeListener(this);
        handler.removeCallbacks(ticker);
    }

    @Override
    public void onChanged() {
        Track t = player.current();
        if (t == null) {
            finish();
            return;
        }
        if (!t.id.equals(shownId)) {
            shownId = t.id;
            cover.setImageDrawable(new CoverDrawable(t.title, 0.06f));
            title.setText(t.title);
            artist.setText(t.artist);
        }
        btnPlay.setImageResource(player.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
        int accent = getColor(R.color.accent), mute = getColor(R.color.mute);
        btnShuffle.setColorFilter(player.isShuffle() ? accent : mute);
        btnRepeat.setImageResource(player.getRepeat() == Player.REPEAT_ONE ? R.drawable.ic_repeat_one : R.drawable.ic_repeat);
        btnRepeat.setColorFilter(player.getRepeat() == Player.REPEAT_OFF ? mute : accent);
        boolean liked = Library.isLiked(t.id);
        like.setImageResource(liked ? R.drawable.ic_heart_filled : R.drawable.ic_heart);
        like.setColorFilter(liked ? accent : mute);
        updateProgress();
    }

    private void updateProgress() {
        int d = player.duration(), p = player.position();
        seek.setProgress(d > 0 ? (int) (1000L * p / d) : 0);
        tvPos.setText(TrackAdapter.fmt(p));
        tvDur.setText(TrackAdapter.fmt(d));
    }
}
