package io.github.vvb2060.keyattestation;

import android.app.Application;
import android.content.Context;

public class AppApplication {

    public static final String TAG = "KeyAttestation";

    public static Application app;

    public static void init(Application application) {
        app = application;
    }
}
