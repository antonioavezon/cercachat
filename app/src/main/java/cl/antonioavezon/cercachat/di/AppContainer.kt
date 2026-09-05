package cl.antonioavezon.cercachat.di

import android.app.Application
import cl.antonioavezon.cercachat.notify.AlertPlayer
import cl.antonioavezon.cercachat.session.SessionController
import cl.antonioavezon.cercachat.storage.IncomingFileStore
import cl.antonioavezon.cercachat.storage.SavedFilesRepository
import cl.antonioavezon.cercachat.storage.SettingsRepository

class AppContainer(app: Application) {
    val settings = SettingsRepository(app)
    val files = IncomingFileStore(app)
    val saved = SavedFilesRepository(app)
    val alerts = AlertPlayer(app, settings)
    val session = SessionController(app, settings, files, saved, alerts)
}
