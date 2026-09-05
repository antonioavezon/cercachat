package cl.antonioavezon.cercachat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import cl.antonioavezon.cercachat.ui.CercaChatRoot
import cl.antonioavezon.cercachat.ui.theme.CercaChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CercaChatApp).container
        setContent {
            CercaChatTheme {
                Surface(Modifier.fillMaxSize()) {
                    val snapshot by container.session.state.collectAsState()
                    CercaChatRoot(
                        session = container.session,
                        settingsRepository = container.settings,
                        savedFiles = container.saved,
                        fileStore = container.files,
                        snapshot = snapshot,
                    )
                }
            }
        }
    }
}
