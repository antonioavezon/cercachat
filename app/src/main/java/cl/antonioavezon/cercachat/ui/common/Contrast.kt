package cl.antonioavezon.cercachat.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max

object Contrast {
    fun ratio(a: Color, b: Color): Double {
        val l1 = a.luminance() + 0.05
        val l2 = b.luminance() + 0.05
        return max(l1, l2) / kotlin.math.min(l1, l2)
    }

    fun insufficient(text: Color, background: Color): Boolean = ratio(text, background) < 4.5
}
