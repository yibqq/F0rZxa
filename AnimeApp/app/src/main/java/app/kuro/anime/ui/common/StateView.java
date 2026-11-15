package app.kuro.anime.ui.common;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import app.kuro.anime.R;
import app.kuro.anime.core.AppError;
import app.kuro.anime.databinding.ViewStateBinding;

/** Shared empty / error / offline presentation with an optional action. */
public class StateView extends FrameLayout {
    private final ViewStateBinding b;

    public StateView(Context context) { this(context, null); }

    public StateView(Context context, AttributeSet attrs) {
        super(context, attrs);
        b = ViewStateBinding.inflate(LayoutInflater.from(context), this, true);
        setVisibility(GONE);
    }

    public void showError(@Nullable AppError error, @Nullable Runnable retry) {
        boolean offline = error != null && error.kind == AppError.Kind.OFFLINE;
        show(offline ? R.drawable.ic_offline : R.drawable.ic_info,
                getContext().getString(offline ? R.string.state_offline_title : R.string.state_error_title),
                Labels.error(getContext(), error),
                retry == null ? null : getContext().getString(R.string.retry), retry);
    }

    public void show(@DrawableRes int icon, CharSequence title, @Nullable CharSequence body,
                     @Nullable CharSequence action, @Nullable Runnable onAction) {
        b.stateIcon.setImageResource(icon);
        b.stateTitle.setText(title);
        b.stateBody.setText(body);
        b.stateBody.setVisibility(body == null ? GONE : VISIBLE);
        if (action != null && onAction != null) {
            b.stateAction.setText(action);
            b.stateAction.setVisibility(VISIBLE);
            b.stateAction.setOnClickListener(v -> onAction.run());
        } else {
            b.stateAction.setVisibility(GONE);
        }
        Motion.fadeIn(this);
    }

    public void hide() { setVisibility(GONE); }

    /** Gives D-pad users a focus target when the screen has nothing else. */
    public View actionButton() { return b.stateAction; }
}
