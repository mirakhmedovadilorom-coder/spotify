package dev.mirahmad.volna;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TrackAdapter extends RecyclerView.Adapter<TrackAdapter.VH> {
    public interface Callbacks {
        void onPlay(List<Track> list, int index);
        void onLike(Track t);
        void onLongPress(Track t);
    }

    private List<Track> data = new ArrayList<>();
    private String playingId;
    private final Callbacks cb;

    public TrackAdapter(Callbacks cb) { this.cb = cb; }

    public void setData(List<Track> d) { data = d; notifyDataSetChanged(); }

    public void setPlaying(String id) {
        if ((id == null && playingId == null) || (id != null && id.equals(playingId))) return;
        playingId = id;
        notifyDataSetChanged();
    }

    public static String fmt(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int viewType) {
        return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_track, p, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Track t = data.get(position);
        h.title.setText(t.title);
        h.artist.setText(t.artist);
        h.dur.setText(t.durationMs > 0 ? fmt(t.durationMs) : "");
        h.cover.setImageDrawable(new CoverDrawable(t.title, 0.14f));
        boolean playing = t.id.equals(playingId);
        h.title.setTextColor(playing ? h.itemView.getContext().getColor(R.color.accent) : Color.WHITE);
        boolean liked = Library.isLiked(t.id);
        h.like.setImageResource(liked ? R.drawable.ic_heart_filled : R.drawable.ic_heart);
        h.like.setColorFilter(liked ? h.itemView.getContext().getColor(R.color.accent)
                : h.itemView.getContext().getColor(R.color.mute));
        h.itemView.setOnClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p != RecyclerView.NO_POSITION) cb.onPlay(data, p);
        });
        h.itemView.setOnLongClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p != RecyclerView.NO_POSITION) cb.onLongPress(data.get(p));
            return true;
        });
        h.like.setOnClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p != RecyclerView.NO_POSITION) {
                cb.onLike(data.get(p));
                notifyItemChanged(p);
            }
        });
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView cover;
        final TextView title, artist, dur;
        final ImageButton like;

        VH(View v) {
            super(v);
            cover = v.findViewById(R.id.cover);
            title = v.findViewById(R.id.title);
            artist = v.findViewById(R.id.artist);
            dur = v.findViewById(R.id.dur);
            like = v.findViewById(R.id.like);
        }
    }
}
