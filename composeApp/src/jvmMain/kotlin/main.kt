import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.example.App
import java.io.File
import javax.imageio.ImageIO

fun main() {
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        println("CRASH IN THREAD ${thread.name}:")
        throwable.printStackTrace()
        try {
            File("crash_stacktrace.txt").writeText("CRASH IN THREAD ${thread.name}:\n" + throwable.stackTraceToString())
        } catch (e: Exception) {
            // Ignore
        }
    }

    try {
        application {
            val module = com.example.di.createModule(Unit)
            val appIcon = remember {
                try {
                    val stream = ::main.javaClass.getResourceAsStream("/icon.png")
                    if (stream != null) {
                        val img = ImageIO.read(stream)
                        BitmapPainter(img.toComposeImageBitmap())
                    } else null
                } catch (e: Exception) {
                    null
                }
            }

            Window(
                onCloseRequest = ::exitApplication,
                title = "Fusion Cut",
                icon = appIcon
            ) {
                App(module)
            }
        }
    } catch (e: Throwable) {
        e.printStackTrace()
        try {
            File("crash_stacktrace.txt").writeText("APP LAUNCH CRASH:\n" + e.stackTraceToString())
        } catch (ex: Exception) {
            // Ignore
        }
    }
}
