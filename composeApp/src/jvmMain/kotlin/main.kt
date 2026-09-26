import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.example.App
import java.io.File

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
            Window(
                onCloseRequest = ::exitApplication,
                title = "Fusion Cut",
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
