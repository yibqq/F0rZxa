package app.kuro.anime.ui.details;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import app.kuro.anime.databinding.ItemPersonBinding;
import app.kuro.anime.ui.common.Images;

/** Characters (with role and Japanese voice actor) and staff. */
public final class PersonAdapter extends RecyclerView.Adapter<PersonAdapter.Holder> {

    public static final class Person {
        final String name, role, imageUrl;

        public Person(String name, String role, String imageUrl) {
            this.name = name;
            this.role = role;
            this.imageUrl = imageUrl;
        }
    }

    private final List<Person> items = new ArrayList<>();

    public PersonAdapter(List<Person> people) { items.addAll(people); }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemPersonBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Person p = items.get(position);
        h.b.personName.setText(p.name);
        h.b.personRole.setText(p.role);
        Images.avatar(h.b.personImage, p.imageUrl);
        h.itemView.setContentDescription(p.role == null ? p.name : p.name + ", " + p.role);
    }

    @Override public int getItemCount() { return items.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemPersonBinding b;

        Holder(ItemPersonBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
