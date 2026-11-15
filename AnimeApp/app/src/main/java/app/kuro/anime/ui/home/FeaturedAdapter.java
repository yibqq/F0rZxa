package app.kuro.anime.ui.home;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import app.kuro.anime.KuroApp;
import app.kuro.anime.R;
import app.kuro.anime.core.WindowSizeClass;
import app.kuro.anime.data.model.Anime;
import app.kuro.anime.databinding.ItemFeaturedPageBinding;
import app.kuro.anime.ui.common.Images;
import app.kuro.anime.ui.common.InsetsHelper;
import app.kuro.anime.ui.common.Labels;
import app.kuro.anime.ui.common.Motion;

/** Full-bleed hero pages. Compact windows use the portrait cover; wider windows use the banner art. */
final class FeaturedAdapter extends RecyclerView.Adapter<FeaturedAdapter.Holder> {

    interface Listener {
        void onOpen(Anime a);

        void onToggleList(Anime a, boolean inList);
    }

    private final List<Anime> items = new ArrayList<>();
    private final Listener listener;
    private Set<String> libraryKeys;

    FeaturedAdapter(Listener listener) { this.listener = listener; }

    void submit(List<Anime> list, Set<String> keys) {
        items.clear();
        items.addAll(list);
        libraryKeys = keys;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemFeaturedPageBinding b = ItemFeaturedPageBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        Motion.pressable(b.featuredDetails);
        Motion.pressable(b.featuredAdd);
        InsetsHelper.pad(b.featuredContent, InsetsHelper.START | InsetsHelper.END);
        return new Holder(b);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Anime a = items.get(position);
        Context c = h.itemView.getContext();
        boolean wide = WindowSizeClass.of(c).atLeast(WindowSizeClass.MEDIUM);
        String image = wide && a.bannerUrl != null ? a.bannerUrl : a.coverUrl;
        Images.backdrop(h.b.featuredImage, image, a.coverColor);
        h.b.featuredTitle.setText(a.displayTitle(KuroApp.get().settings().titleLanguage()));
        List<String> parts = new ArrayList<>();
        String meta = Labels.metaLine(c, a, true);
        if (!meta.isEmpty()) parts.add(meta);
        for (int i = 0; i < Math.min(3, a.genres.size()); i++) parts.add(Labels.genre(c, a.genres.get(i)));
        h.b.featuredMeta.setText(android.text.TextUtils.join(" · ", parts));
        boolean inList = libraryKeys != null && libraryKeys.contains(a.key());
        h.b.featuredAdd.setText(inList ? R.string.in_list : R.string.add_to_list);
        h.b.featuredAdd.setCompoundDrawablesRelativeWithIntrinsicBounds(
                inList ? R.drawable.ic_check : R.drawable.ic_plus, 0, 0, 0);
        h.b.featuredDetails.setOnClickListener(v -> listener.onOpen(a));
        h.b.featuredImage.setOnClickListener(v -> listener.onOpen(a));
        h.b.featuredAdd.setOnClickListener(v -> listener.onToggleList(a, inList));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemFeaturedPageBinding b;

        Holder(ItemFeaturedPageBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
