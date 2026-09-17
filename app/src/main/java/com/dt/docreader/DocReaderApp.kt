package com.dt.docreader

import android.app.Application
import com.dt.docreader.infra.SandboxManager

class DocReaderApp : Application() {
    lateinit var sandbox: SandboxManager
        private set

    override fun onCreate() {
        super.onCreate()
        sandbox = SandboxManager(this)
    }
}
