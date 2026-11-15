package app.kuro.anime.core;

import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;

/** Detects the device category so the right experience (touch vs. 10-foot) is used. */
public final class DeviceProfile {

    private DeviceProfile() {}

    public static boolean isTelevision(Context context) {
        int type = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_TYPE_MASK;
        if (type == Configuration.UI_MODE_TYPE_TELEVISION) return true;
        UiModeManager ui = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        if (ui != null && ui.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) return true;
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    public static boolean isAutomotive(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }
}
