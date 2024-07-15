// OverlayView.kt
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.example.myapplication.R

class OverlayView(private val context: Context) {
    private var windowManager: WindowManager? = null
    private var view: View? = null
    private val handler = Handler(Looper.getMainLooper())

    fun show(message: String) {
        handler.post {
            if (view == null) {
                windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                view = inflater.inflate(R.layout.overlay_layout, null)

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                )
                params.gravity = Gravity.CENTER

                windowManager?.addView(view, params)
            }

            view?.findViewById<TextView>(R.id.overlayText)?.text = message
            view?.visibility = View.VISIBLE

            handler.postDelayed({
                hide()
            }, 3000) // Hide after 3 seconds
        }
    }

    private fun hide() {
        handler.post {
            view?.visibility = View.GONE
        }
    }
}