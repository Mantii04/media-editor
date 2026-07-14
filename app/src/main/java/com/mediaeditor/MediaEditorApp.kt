package com.mediaeditor

import android.app.Application

class MediaEditorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: MediaEditorApp
            private set
    }
}
