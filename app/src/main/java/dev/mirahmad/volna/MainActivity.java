package dev.mirahmad.volna;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements Player.Listener, TrackAdapter.Callbacks {
    private Player player;
    private TrackAdapter adapter;
    private EditText etSearch;
    private TextView tvAll, tvLiked, tvEmpty;
    private View mini;
    private ImageView miniCover;
    private TextView miniTitle, miniArtist;
    private ImageButton miniPlay;
    private ProgressBar miniProgress;
    private boolean likedTab;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateProgress();
            handler.postDelayed(this, 500);
        }
    };

    private final ActivityResultLauncher<String[]> picker =
            registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
                if (uris == null || uris.isEmpty()) return;
                List<Uri> list = new ArrayList<>();
                for (Uri u : uris) {
                    try {
                        getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignored) {
                    }
                    list.add(u);
                }
                int n = Library.addUserTracks(this, list);
                Toast.makeText(this, n > 0 ? "Добавлено треков: " + n : "Эти треки уже есть", Toast.LENGTH_SHORT).show();
                refresh();
            });

    private final ActivityResultLauncher<String> notifPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), ok -> { });

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        Library.init(this);
        player = Player.get(this);

        etSearch = findViewById(R.id.etSearch);
        tvAll = findViewById(R.id.tvAll);
        tvLiked = findViewById(R.id.tvLiked);
        tvEmpty = findViewById(R.id.tvEmpty);
        mini = findViewById(R.id.mini);
        miniCover = findViewById(R.id.miniCover);
        miniTitle = findViewById(R.id.miniTitle);
        miniArtist = findViewById(R.id.miniArtist);
        miniPlay = findViewById(R.id.miniPlay);
        miniProgress = findViewById(R.id.miniProgress);

        RecyclerView rv = findViewById(R.id.rv);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TrackAdapter(this);
        rv.setAdapter(adapter);

        tvAll.setOnClickListener(v -> { likedTab = false; refresh(); });
        tvLiked.setOnClickListener(v -> { likedTab = true; refresh(); });
        findViewById(R.id.btnAdd).setOnClickListener(v -> picker.launch(new String[]{"audio/*"}));
        findViewById(R.id.btnShuffleAll).setOnClickListener(v -> {
            List<Track> l = visible();
            if (l.isEmpty()) return;
            player.playShuffled(l);
        });
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { refresh(); }
        });

        miniPlay.setOnClickListener(v -> player.toggle());
        findViewById(R.id.miniPrev).setOnClickListener(v -> player.prev());
        findViewById(R.id.miniNext).setOnClickListener(v -> player.next());
        mini.setOnClickListener(v -> startActivity(new Intent(this, PlayerActivity.class)));

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
        refresh();
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

    private List<Track> visible() {
        List<Track> base = likedTab ? Library.liked() : Library.all();
        String q = etSearch.getText().toString().trim().toLowerCase(Locale.getDefault());
        if (q.isEmpty()) return base;
        List<Track> r = new ArrayList<>();
        for (Track t : base) {
            if (t.title.toLowerCase(Locale.getDefault()).contains(q)
                    || t.artist.toLowerCase(Locale.getDefault()).contains(q)) r.add(t);
        }
        return r;
    }

    private void refresh() {
        tvAll.setSelected(!likedTab);
        tvLiked.setSelected(likedTab);
        List<Track> l = visible();
        adapter.setData(l);
        if (l.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText(likedTab ? "Пока пусто — нажми на сердечко у трека" : "Ничего не найдено");
        } else {
            tvEmpty.setVisibility(View.GONE);
        }
    }

    // ---- Player.Listener ----
    @Override
    public void onChanged() {
        Track t = player.current();
        adapter.setPlaying(t != null ? t.id : null);
        if (t == null) {
            mini.setVisibility(View.GONE);
            return;
        }
        mini.setVisibility(View.VISIBLE);
        miniTitle.setText(t.title);
        miniArtist.setText(t.artist);
        miniCover.setImageDrawable(new CoverDrawable(t.title, 0.14f));
        miniPlay.setImageResource(player.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
        updateProgress();
    }

    private void updateProgress() {
        int d = player.duration();
        miniProgress.setProgress(d > 0 ? (int) (1000L * player.position() / d) : 0);
    }

    // ---- TrackAdapter.Callbacks ----
    @Override public void onPlay(List<Track> list, int index) { player.playList(list, index); }

    @Override
    public void onLike(Track t) {
        Library.toggleLike(t.id);
        if (likedTab) refresh();
    }

    @Override
    public void onLongPress(final Track t) {
        if (!t.isUser()) {
            Toast.makeText(this, "Встроенные треки удалить нельзя", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(t.title)
                .setMessage("Убрать трек из приложения? Сам файл на телефоне останется.")
                .setPositiveButton("Убрать", (d, w) -> {
                    Library.removeUserTrack(t);
                    refresh();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }
}
