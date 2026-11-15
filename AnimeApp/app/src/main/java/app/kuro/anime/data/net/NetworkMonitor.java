package app.kuro.anime.data.net;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/** Observes validated internet connectivity. */
public final class NetworkMonitor {
    private final ConnectivityManager cm;
    private final MutableLiveData<Boolean> online = new MutableLiveData<>();

    public NetworkMonitor(Context context) {
        cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        online.postValue(isOnline());
        try {
            cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(@NonNull Network network) { online.postValue(true); }
                @Override public void onLost(@NonNull Network network) { online.postValue(isOnline()); }
                @Override public void onCapabilitiesChanged(@NonNull Network n, @NonNull NetworkCapabilities c) {
                    online.postValue(c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET));
                }
            });
        } catch (RuntimeException ignored) {
            // Some OEM builds throw when too many callbacks are registered; polling still works.
        }
    }

    public boolean isOnline() {
        if (cm == null) return true;
        Network n = cm.getActiveNetwork();
        if (n == null) return false;
        NetworkCapabilities c = cm.getNetworkCapabilities(n);
        return c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    public LiveData<Boolean> online() { return online; }
}
