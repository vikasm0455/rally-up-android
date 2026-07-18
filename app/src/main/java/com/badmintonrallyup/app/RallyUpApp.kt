package com.badmintonrallyup.app

import android.app.Application

class RallyUpApp : Application() {
    companion object {
        lateinit var instance: RallyUpApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
