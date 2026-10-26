package dev.andikune.robotasisten.io

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.andikune.robotasisten.io.util.CrashHandler

@HiltAndroidApp
class RAiOApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.init(this)
    }
}
