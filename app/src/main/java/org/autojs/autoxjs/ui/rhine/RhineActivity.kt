package org.autojs.autoxjs.ui.rhine

import DebugServer
import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.stardust.app.permission.DrawOverlaysPermission
import com.stardust.autojs.core.permission.StoragePermissionUtils
import com.stardust.autojs.core.permission.StoragePermissionUtils.getMediaPermissionList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.autojs.autoxjs.Pref
import org.autojs.autoxjs.R
import org.autojs.autoxjs.external.foreground.ForegroundService
import org.autojs.autoxjs.network.MessengerServiceConnection
import org.autojs.autoxjs.timing.TimedTaskScheduler
import org.autojs.autoxjs.ui.compose.theme.AutoXJsTheme
import org.autojs.autoxjs.ui.floating.FloatyWindowManger
import org.autojs.autoxjs.ui.main.scripts.ScriptListFragment
import org.autojs.autoxjs.ui.main.task.TaskManagerFragmentKt
import org.autojs.autoxjs.ui.main.web.EditorAppManager


class RhineActivity : FragmentActivity() {

    companion object {
        @JvmStatic
        fun getIntent(context: Context) = Intent(context, RhineActivity::class.java)
    }

    private val TAG = "Android Intelligence - RhineActivity"

    private var lastBackPressedTime = 0L
    private var drawerState: DrawerState? = null
    private val viewPager: ViewPager2 by lazy { ViewPager2(this) }
    private var scope: CoroutineScope? = null
    private lateinit var serviceConnection:MessengerServiceConnection

    private var server: DebugServer? = null

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (!StoragePermissionUtils.hasManageAllFilesPermission()) {
            StoragePermissionUtils.requestManageAllFilesPermission(this)
        }

        if (Pref.isForegroundServiceEnabled()) ForegroundService.start(this)
        else ForegroundService.stop(this)

        if (Pref.isFloatingMenuShown() && !FloatyWindowManger.isCircularMenuShowing()) {
            if (DrawOverlaysPermission.isCanDrawOverlays(this)) FloatyWindowManger.showCircularMenu()
            else Pref.setFloatingMenuShown(false)
        }
        
        serviceConnection = MessengerServiceConnection(Looper.getMainLooper())
        val intent = Intent("com.stardust.autojs.messengerAction")
        intent.setPackage(this.packageName)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)

        setContent {
            scope = rememberCoroutineScope()
            AutoXJsTheme {
                Surface(color = MaterialTheme.colors.background) {
                    MainPage(activity = this)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(serviceConnection)
    }

    override fun onResume() {
        super.onResume()
        TimedTaskScheduler.ensureCheckTaskWorks(application)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        back()
    }

    private fun back() {
        val currentTime = System.currentTimeMillis()
        val interval = currentTime - lastBackPressedTime
        if (interval > 2000) {
            lastBackPressedTime = currentTime
            Toast.makeText(
                this,
                getString(R.string.text_press_again_to_exit),
                Toast.LENGTH_SHORT
            ).show()
        } else super.onBackPressed()
    }
}

@Composable
fun MainPage(activity: FragmentActivity) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var inputText by remember { mutableStateOf("") }

    SetSystemUI(drawerState)

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 标题
            Text(
                text = "Android Intelligence",
                style = MaterialTheme.typography.h2,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 32.dp)
            )
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                // 大输入框
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    placeholder = {
                        Text("请输入内容...")
                    },
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 5,
                    textStyle = TextStyle(fontSize = 16.sp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 按钮
                Button(
                    onClick = {
                        // 按钮点击事件处理
                        if (inputText.isNotBlank()) {
                            println("Input text: $inputText") // 输出日志
                            // 在这里添加你的业务逻辑
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = inputText.isNotBlank()
                ) {
                    Text(
                        text = "确认",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 底部区域
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Button(
                        onClick = { /* Home 按钮点击事件 */ },
                    ) {
                        Text("Home")
                    }

                    Button(
                        onClick = { /* MCP 按钮点击事件 */ },
                    ) {
                        Text("MCP")
                    }

                    Button(
                        onClick = { /* Setting 按钮点击事件 */ },
                    ) {
                        Text("Setting")
                    }
                }

                Text(
                    text = "RHINE.AI",
                )
            }
        }
    }
}

fun showExternalStoragePermissionToast(context: Context) {
    Toast.makeText(
        context,
        context.getString(R.string.text_please_enable_external_storage),
        Toast.LENGTH_SHORT
    ).show()
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberExternalStoragePermissionsState(
    context:Context,
    onPermissionsResult: (allAllow: Boolean) -> Unit
) = rememberMultiplePermissionsState(
    permissions = getMediaPermissionList(context),
    onPermissionsResult = { map ->
        onPermissionsResult(map.all { it.value })
    }
)

@Composable
private fun SetSystemUI(drawerState: DrawerState) {
    val systemUiController = rememberSystemUiController()
    val useDarkIcons =
        if (!isSystemInDarkTheme()) {
            drawerState.isOpen || drawerState.isAnimationRunning
        } else false

    val navigationUseDarkIcons = !isSystemInDarkTheme()
    SideEffect {
        systemUiController.setStatusBarColor(
            color = Color.Transparent,
            darkIcons = useDarkIcons
        )
        systemUiController.setNavigationBarColor(
            Color.Transparent,
            darkIcons = navigationUseDarkIcons
        )
    }
}
