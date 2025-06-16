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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
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

data class ChatMessage(
    val id: Int,
    val username: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

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
                        onClose = {
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
    onClose: () -> Unit,
    onDrag: (deltaX: Float, deltaY: Float) -> Unit,
) {
    var messages by remember {
        mutableStateOf(listOf(
            ChatMessage(1, "RHINE AI", "你好！我是 RHINE.AI 公司研发的 Android Intelligence。我可以帮你完成安卓系统上的操作！"),
        ))
    }
    var inputText by remember { mutableStateOf("") }
    var messageIdCounter by remember { mutableStateOf(2) }

    Box(
        modifier = Modifier
            .wrapContentSize()
            .padding(18.dp)
    ) {
        Box(
            modifier = Modifier
                .width(320.dp)
                .height(500.dp)
                .shadow(
                    elevation = 9.dp,
                    shape = RoundedCornerShape(32.dp)
                )
                .background(
                    color = Color.White,
                    shape = RoundedCornerShape(32.dp)
                )
                .pointerInput(Unit) {
                    detectDragGestures { _, dragAmount ->
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                }
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 聊天标题栏
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Color.White,
                            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                        )
                        .padding(top = 16.dp, bottom = 4.dp)
                ) {
                    Text(
                        text = "RHINE AI",
                        color = Color.Black,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // 消息列表容器
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    // 消息列表
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 20.dp, bottom = 20.dp) // 为上下渐变遮罩留出空间
                    ) {
                        items(messages) { message ->
                            ChatMessageItem(message = message)
                        }
                    }

                    // 顶部白色渐变遮罩层
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White,
                                        Color.White.copy(alpha = 0.8f),
                                        Color.White.copy(alpha = 0f)
                                    )
                                )
                            )
                    )

                    // 底部白色渐变遮罩层
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0f),
                                        Color.White.copy(alpha = 0.8f),
                                        Color.White
                                    )
                                )
                            )
                    )
                }

                // 输入框和发送按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(Color.White)
                        .padding(start = 8.dp, bottom = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .background(
                                color = Color(0xFFF2F2F2),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .padding(16.dp, 12.dp),
                    ) {
                        if (inputText.isEmpty()) {
                            Text(
                                text = "Input Something...",
                                color = Color.Gray,
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 1.dp)
                                    .wrapContentHeight(Alignment.CenterVertically)
                            )
                        }

                        BasicTextField(
                            modifier = Modifier
                                .fillMaxSize()
                                .wrapContentHeight(Alignment.CenterVertically),
                            textStyle = TextStyle(fontSize = 14.sp),
                            value = inputText,
                            onValueChange = { inputText = it },
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))
                    
                    IconButton(
                        modifier = Modifier.size(48.dp),
                        onClick = {
                            if (inputText.isNotBlank()) {
                                messages = messages + ChatMessage(
                                    id = messageIdCounter++,
                                    username = "用户", 
                                    content = inputText
                                )
                                inputText = ""
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "发送",
                            tint = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(message: ChatMessage) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    ) {
        // 用户名
        Text(
            text = message.username,
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // 消息气泡 - 所有消息都靠左
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = message.content,
                    fontSize = 14.sp,
                    color = Color.Black,
                    lineHeight = 24.sp
                )
            }
        }
    }
}

@Preview
@Composable
fun FloatingWindowContentPreview() {
    Box(
        modifier = Modifier
            .background(Color.White)
            .padding(30.dp, 50.dp)
    ) {
        FloatingWindowContent(
            onClose = { },
            onDrag = { _, _ -> }
        )
    }
}
