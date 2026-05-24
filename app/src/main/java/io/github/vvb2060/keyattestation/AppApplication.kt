package io.github.vvb2060.keyattestation

import android.app.Application
import android.content.Context
import me.bmax.apatch.apApp

/**
 * Bridge class to adapt KeyAttestation code to FolkPatch's Application
 */
object AppApplication {
    const val TAG = "KeyAttestation"

    @JvmStatic
    fun getApp(): Application {
        return apApp as Application
    }

    @JvmStatic
    fun getContext(): Context {
        return apApp
    }
}
