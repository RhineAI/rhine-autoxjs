package org.autojs.autoxjs.ui.rhine.panel

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.lifecycle.ViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.autojs.autoxjs.ui.compose.theme.AutoXJsTheme
import org.autojs.autoxjs.ui.floating.MyLifecycleOwner

class FloatingPanelService: Service(), ViewModelStoreOwner, SavedStateRegistryOwner, LifecycleOwner {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val viewModelStore = ViewModelStore()

    override fun getViewModelStore(): ViewModelStore {
        return viewModelStore
    }

    override fun getLifecycle(): Lifecycle {
        return lifecycleRegistry
    }

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        createFloatingWindow()

        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
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
                AutoXJsTheme {
                    FloatingWindowContent(
                        onActionClick = {
                            // 处理动作点击
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
        }

        // 参考 LayoutHierarchyFloatyWindow 的方法设置 ViewTree owners
        val viewModelStore = ViewModelStore()
        val lifecycleOwner = MyLifecycleOwner()

        // 初始化生命周期
        lifecycleOwner.performRestore(null)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        // 设置 ViewTree owners
        ViewTreeLifecycleOwner.set(composeView, lifecycleOwner)
        ViewTreeViewModelStoreOwner.set(composeView) { viewModelStore }
        composeView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)

        floatingView = composeView

        try {
            windowManager.addView(floatingView, params)
            Log.d("FloatingPanelService", "Successfully added floating view")
        } catch (e: Exception) {
            Log.e("FloatingPanelService", "Failed to add floating view: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
        floatingView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e("FloatingPanelService", "Failed to remove floating view: ${e.message}")
                e.printStackTrace()
            }
        }
        viewModelStore.clear()
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
            .background(
                color = Color(0xFF1E1E1E), // 深灰色背景
                shape = RoundedCornerShape(16.dp)
            )
            .padding(24.dp), // 内边距
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "RHINE AI",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}