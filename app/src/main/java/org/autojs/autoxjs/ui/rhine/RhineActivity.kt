package org.autojs.autoxjs.ui.rhine

import DebugServer
import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
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
import org.autojs.autoxjs.autojs.AutoJs
import org.autojs.autoxjs.external.foreground.ForegroundService
import org.autojs.autoxjs.network.MessengerServiceConnection
import org.autojs.autoxjs.network.ozobi.KtorDocsService
import org.autojs.autoxjs.timing.TimedTaskScheduler
import org.autojs.autoxjs.ui.build.ProjectConfigActivity
import org.autojs.autoxjs.ui.build.ProjectConfigActivity_
import org.autojs.autoxjs.ui.common.ScriptOperations
import org.autojs.autoxjs.ui.compose.theme.AutoXJsTheme
import org.autojs.autoxjs.ui.compose.widget.MyIcon
import org.autojs.autoxjs.ui.compose.widget.SearchBox2
import org.autojs.autoxjs.ui.explorer.ExplorerViewKt
import org.autojs.autoxjs.ui.floating.FloatyWindowManger
import org.autojs.autoxjs.ui.main.components.DocumentPageMenuButton
import org.autojs.autoxjs.ui.main.components.LogButton
import org.autojs.autoxjs.ui.main.drawer.DrawerPage
import org.autojs.autoxjs.ui.main.drawer.isNightMode
import org.autojs.autoxjs.ui.main.scripts.ScriptListFragment
import org.autojs.autoxjs.ui.main.task.TaskManagerFragmentKt
import org.autojs.autoxjs.ui.main.web.EditorAppManager
import org.autojs.autoxjs.ui.widget.fillMaxSize


data class BottomNavigationItem(val icon: Int, val label: String)

class RhineActivity : FragmentActivity() {

    companion object {
        @JvmStatic
        fun getIntent(context: Context) = Intent(context, RhineActivity::class.java)
    }

    private val TAG = "Android Intelligence - RhineActivity"

    private val scriptListFragment by lazy { ScriptListFragment() }
    private val taskManagerFragment by lazy { TaskManagerFragmentKt() }
    private val webViewFragment by lazy { EditorAppManager() }
    private var lastBackPressedTime = 0L
    private var drawerState: DrawerState? = null
    private val viewPager: ViewPager2 by lazy { ViewPager2(this) }
    private var scope: CoroutineScope? = null
    private lateinit var serviceConnection:MessengerServiceConnection

    private var server: DebugServer? = null

    @OptIn(ExperimentalPermissionsApi::class)
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
                    MainPage(
                        activity = this
                    )
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
        if (drawerState?.isOpen == true) {
            scope?.launch { drawerState?.close() }
            return
        }
        if (viewPager.currentItem == 0 && scriptListFragment.onBackPressed()) {
            return
        }
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
fun MainPage(activity: FragmentActivity, ) {
    val context = LocalContext.current
    val scaffoldState = rememberScaffoldState()
    val scope = rememberCoroutineScope()

    var inputText by remember { mutableStateOf("") }

    SetSystemUI(scaffoldState)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        scaffoldState = scaffoldState,
        drawerGesturesEnabled = scaffoldState.drawerState.isOpen,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
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
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = MaterialTheme.colors.primary,
                    unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
                ),
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
fun rememberExternalStoragePermissionsState(context:Context, onPermissionsResult: (allAllow: Boolean) -> Unit) =
    rememberMultiplePermissionsState(
        permissions = getMediaPermissionList(context),
        onPermissionsResult = { map ->
            onPermissionsResult(map.all { it.value })
        })

@Composable
private fun SetSystemUI(scaffoldState: ScaffoldState) {
    val systemUiController = rememberSystemUiController()
    val useDarkIcons =
        if (MaterialTheme.colors.isLight) {
            scaffoldState.drawerState.isOpen || scaffoldState.drawerState.isAnimationRunning
        } else false

    val navigationUseDarkIcons = MaterialTheme.colors.isLight
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

private fun getBottomItems(context: Context) = mutableStateListOf(
    BottomNavigationItem(
        R.drawable.ic_home,
        context.getString(R.string.text_home)
    ),
    BottomNavigationItem(
        R.drawable.ic_manage,
        context.getString(R.string.text_management)
    ),
    BottomNavigationItem(
        R.drawable.ic_web,
        context.getString(R.string.text_document)
    )
)

@Composable
fun BottomBar(
    items: List<BottomNavigationItem>,
    currentSelected: Int,
    onSelectedChange: (Int) -> Unit
) {
    BottomNavigation(elevation = 0.dp, backgroundColor = MaterialTheme.colors.background) {
        items.forEachIndexed { index, item ->
            val selected = currentSelected == index
            val color = if (selected) MaterialTheme.colors.primary else Color.Gray
            BottomNavigationItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        onSelectedChange(index)
                    }
                },
                icon = {
                    Icon(
                        painter = painterResource(id = item.icon),
                        contentDescription = item.label,
                        tint = color
                    )
                },
                label = {
                    Text(text = item.label, color = color)
                }
            )
        }
    }
}

@Composable
private fun TopBar(
    currentPage: Int,
    requestOpenDrawer: () -> Unit,
    onSearch: (String) -> Unit,
    scriptListFragment: ScriptListFragment,
    webViewFragment: EditorAppManager,
) {
    var isSearch by remember {
        mutableStateOf(false)
    }
    val context = LocalContext.current
    TopAppBar(elevation = 0.dp) {
        CompositionLocalProvider(
            LocalContentAlpha provides ContentAlpha.high,
        ) {
            if (!isSearch) {
                IconButton(onClick = requestOpenDrawer) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = stringResource(id = R.string.text_menu),
                    )
                }

                ProvideTextStyle(value = MaterialTheme.typography.h6) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = stringResource(id = R.string.app_name)
                    )
                }
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                    IconButton(onClick = {
//                        context.startActivity(Intent(context, EditActivity::class.java))
//                    }) {
//                        Icon(
//                            imageVector = Icons.Default.Edit,
//                            contentDescription = "editor"
//                        )
//                    }
//                }
                if (currentPage == 0) {
                    IconButton(onClick = { isSearch = true }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(id = R.string.text_search)
                        )
                    }
                }
            } else {
                IconButton(onClick = {
                    isSearch = false
                    onSearch("")
                }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = stringResource(id = R.string.text_exit_search)
                    )
                }

                var keyword by remember {
                    mutableStateOf("")
                }
                SearchBox2(
                    value = keyword,
                    onValueChange = { keyword = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(text = stringResource(id = R.string.text_search)) },
                    keyboardActions = KeyboardActions(onSearch = {
                        onSearch(keyword)
                    })
                )
                if (keyword.isNotEmpty()) {
                    IconButton(onClick = { keyword = "" }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = null
                        )
                    }
                }
            }
            LogButton()
            when (currentPage) {
                0 -> {
                    var expanded by remember {
                        mutableStateOf(false)
                    }
                    Box() {
                        IconButton(onClick = { expanded = true }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(id = R.string.desc_more)
                            )
                        }
                        TopAppBarMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            scriptListFragment = scriptListFragment
                        )
                    }
                }

                1 -> {
                    IconButton(onClick = { AutoJs.getInstance().scriptEngineService.stopAll() }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(id = R.string.desc_more)
                        )
                    }
                }

                2 -> {
                    DocumentPageMenuButton { webViewFragment.swipeRefreshWebView.webView }
                }
            }

        }
    }
}

@Composable
fun TopAppBarMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    offset: DpOffset = DpOffset.Zero,
    scriptListFragment: ScriptListFragment
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest, offset = offset) {
        val context = LocalContext.current
        NewDirectory(context, scriptListFragment, onDismissRequest)
        NewFile(context, scriptListFragment, onDismissRequest)
        ImportFile(context, scriptListFragment, onDismissRequest)
        NewProject(context, scriptListFragment, onDismissRequest)
//        DropdownMenuItem(onClick = { /*TODO*/ }) {
//            MyIcon(
//                painter = painterResource(id = R.drawable.ic_timed_task),
//                contentDescription = stringResource(id = R.string.text_switch_timed_task_scheduler)
//            )
//            Spacer(modifier = Modifier.width(8.dp))
//            Text(text = stringResource(id = R.string.text_switch_timed_task_scheduler))
//        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun NewDirectory(
    context: Context,
    scriptListFragment: ScriptListFragment,
    onDismissRequest: () -> Unit
) {
    val permission = rememberExternalStoragePermissionsState(LocalContext.current) {
        if (it) getScriptOperations(
            context,
            scriptListFragment.explorerView
        ).newDirectory()
        else showExternalStoragePermissionToast(context)
    }
    DropdownMenuItem(onClick = {
        onDismissRequest()
        permission.launchMultiplePermissionRequest()
    }) {
        MyIcon(
            painter = painterResource(id = R.drawable.ic_floating_action_menu_dir),
            contentDescription = null, nightMode = isNightMode()
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(id = R.string.text_directory))
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun NewFile(
    context: Context,
    scriptListFragment: ScriptListFragment,
    onDismissRequest: () -> Unit
) {
    val permission = rememberExternalStoragePermissionsState(LocalContext.current) {
        if (it) getScriptOperations(
            context,
            scriptListFragment.explorerView
        ).newFile()
        else showExternalStoragePermissionToast(context)
    }
    DropdownMenuItem(onClick = {
        onDismissRequest()
        permission.launchMultiplePermissionRequest()
    }) {
        MyIcon(
            painter = painterResource(id = R.drawable.ic_floating_action_menu_file),
            contentDescription = null, nightMode = isNightMode()
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(id = R.string.text_file))
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun ImportFile(
    context: Context,
    scriptListFragment: ScriptListFragment,
    onDismissRequest: () -> Unit
) {
    val permission = rememberExternalStoragePermissionsState(LocalContext.current) {
        if (it) getScriptOperations(
            context,
            scriptListFragment.explorerView
        ).importFile()
        else showExternalStoragePermissionToast(context)
    }
    DropdownMenuItem(onClick = {
        onDismissRequest()
        permission.launchMultiplePermissionRequest()
    }) {
        MyIcon(
            painter = painterResource(id = R.drawable.ic_floating_action_menu_open),
            contentDescription = null, nightMode = isNightMode()
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(id = R.string.text_import))
    }
}

@Composable
private fun NewProject(
    context: Context,
    scriptListFragment: ScriptListFragment,
    onDismissRequest: () -> Unit
) {
    DropdownMenuItem(onClick = {
        onDismissRequest()
        ProjectConfigActivity_.intent(context)
            .extra(
                ProjectConfigActivity.EXTRA_PARENT_DIRECTORY,
                scriptListFragment.explorerView.currentPage?.path
            )
            .extra(ProjectConfigActivity.EXTRA_NEW_PROJECT, true)
            .start()
    }) {
        MyIcon(
            painter = painterResource(id = R.drawable.ic_project2),
            contentDescription = null, nightMode = isNightMode()
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(id = R.string.text_project))
    }
}

private fun getScriptOperations(
    context: Context,
    explorerView: ExplorerViewKt
): ScriptOperations {
    return ScriptOperations(
        context,
        explorerView,
        explorerView.currentPage
    )
}