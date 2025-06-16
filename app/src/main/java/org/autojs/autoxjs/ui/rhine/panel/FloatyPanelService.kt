package org.autojs.autoxjs.ui.rhine.panel

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.Lifecycle

class FloatyPanelService: Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        createFloatingWindow()
    }

    private fun createFloatingWindow() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 200

        val composeView = ComposeView(this).apply {
            setContent {
                FloatingWindowContent(
                    onActionClick = {
                    },
                    onCloseClick = {
                        stopSelf()
                    },
                    onDrag = { deltaX, deltaY ->
                        params.x += deltaX.toInt()
                        params.y += deltaY.toInt()
                        windowManager.updateViewLayout(floatingView, params)
                    }
                )
            }
        }

        floatingView = composeView

        windowManager.addView(floatingView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingView?.let {
            windowManager.removeView(it)
        }
    }
}

@Composable
fun FloatingWindowContent(
    onActionClick: () -> Unit,
    onCloseClick: () -> Unit,
    onDrag: (deltaX: Float, deltaY: Float) -> Unit,
) {
    Box(
        modifier = Modifier
            .wrapContentSize()
            .width(336.dp)
            .padding(20.dp)
            .background(Color.White)
    ) {
    }
}