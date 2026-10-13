package com.krishna.kalam

import android.app.Application
import com.krishna.kalam.core.Logger

/**
 * Logger.init used to run only in MainActivity, so alarm receivers, the sound service and the
 * next-day re-arm — which usually start in a cold process — never wrote to Error Logs.
 */
class KalamApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Logger.init(this)
    }
}
