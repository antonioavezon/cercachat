package cl.antonioavezon.cercachat

import android.app.Application
import cl.antonioavezon.cercachat.di.AppContainer

class CercaChatApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
