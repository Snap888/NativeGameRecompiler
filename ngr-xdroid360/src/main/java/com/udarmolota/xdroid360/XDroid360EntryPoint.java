package com.udarmolota.xdroid360;

import android.content.Context;
import android.content.Intent;

/** Public, narrow entry point for the XDroid360 runtime embedded by NGR. */
public final class XDroid360EntryPoint {
    private XDroid360EntryPoint() {}

    /** True only when the pinned on-device compiler kit and at least one game profile are installed. */
    public static boolean isBuildEnvironmentReady(Context context) {
        return Toolchain.present(context) && !Toolchain.profiles(context).isEmpty();
    }

    /** Opens the donor's complete Xbox 360 library/build UI inside the NGR application. */
    public static void launch(Context context) {
        Intent intent = new Intent(context, LauncherActivity.class);
        if (!(context instanceof android.app.Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
