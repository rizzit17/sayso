package com.samsung.prism.teachable

import android.app.Application
import android.content.Context

class PrismApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: PrismApplication
            private set

        fun getAppContext(): Context = instance.applicationContext
    }
}
