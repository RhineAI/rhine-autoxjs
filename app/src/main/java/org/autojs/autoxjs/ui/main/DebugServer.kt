import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.*
import org.autojs.autoxjs.autojs.AutoJs
import org.autojs.autoxjs.model.script.ScriptFile
import org.autojs.autoxjs.model.script.Scripts
import com.stardust.autojs.core.console.ConsoleImpl
import com.stardust.autojs.execution.SimpleScriptExecutionListener
import com.stardust.autojs.execution.ScriptExecution
import com.stardust.autojs.execution.ExecutionConfig
import com.stardust.autojs.script.JavaScriptSource
import com.stardust.autojs.script.ScriptSource
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class DebugServer(port: Int, val context: Context? = null): NanoHTTPD("0.0.0.0", port) {

    private val TAG = "Android Intelligence - DebugServer"

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        if (uri.startsWith("/debug/execute") && method == Method.GET) {
            val params = session.parms
            val code = params["code"]

            return if (code != null) {
                Log.i(TAG, "Executing code: $code")

                runCodeString(code)

                newFixedLengthResponse(Response.Status.OK, "text/plain", "Success")
            } else {
                newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing 'code' parameter")
            }
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
    }
}

@SuppressLint("SdCardPath")
fun runCodeString(code: String) {
    val TAG = "Android Intelligence - run"

    // 获取当前时间戳，精确到秒
    val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    val timestamp = dateFormat.format(Date())

    // 构建文件路径
    val dirPath = "/sdcard/脚本/debug"
    val fileName = "$timestamp.js"
    val filePath = "$dirPath/$fileName"

    try {
        // 创建目录（如果不存在）
        val dir = File(dirPath)
        if (!dir.exists()) {
            dir.mkdirs()
        }

        // 写入文件
        val file = File(filePath)
        file.writeText(code)

        Log.i(TAG, "Code saved successfully to: $filePath")
        Log.i(TAG, "Running script... B")

        // 使用协程执行脚本并等待结果
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scriptSource = ScriptFile(filePath).toSource()
                if (scriptSource == null) {
                    Log.e(TAG, "未找到可执行脚本: ${filePath}")
                    return@launch
                }
                val (result, logs) = executeScriptAndWait(scriptSource)
                
                Log.i(TAG, "=== 脚本执行完成 ===")
                Log.i(TAG, "执行结果: $result")
                Log.i(TAG, "=== 执行日志 ===")
                logs.forEach { log ->
                    Log.i(TAG, log)
                }
            } catch (e: Exception) {
                Log.e(TAG, "脚本执行失败: ${e.message}", e)
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error saving file: ${e.message}", e)
    }
}

// 协程等待执行完成的方法
suspend fun executeScriptAndWait(scriptSource: ScriptSource): Pair<Any?, List<String>> {
    return suspendCancellableCoroutine { continuation ->
        val logs = mutableListOf<String>()
        val scriptEngineService = AutoJs.getInstance().scriptEngineService
        val globalConsole = scriptEngineService.globalConsole as ConsoleImpl
        val initialLogCount = globalConsole.allLogs.size
        
        val listener = object : SimpleScriptExecutionListener() {
            override fun onSuccess(execution: ScriptExecution, result: Any?) {
                // 等待100ms再收集日志，确保所有延迟日志都被收集
                CoroutineScope(Dispatchers.IO).launch {
                    delay(100)
                    
                    // 收集日志
                    val allLogs = globalConsole.allLogs
                    if (allLogs.size > initialLogCount) {
                        val newLogs = allLogs.subList(initialLogCount, allLogs.size)
                        newLogs.forEach { logs.add("${getLevelName(it.level)}: ${it.content}") }
                    }
                    
                    continuation.resume(Pair(result, logs))
                }
            }
            
            override fun onException(execution: ScriptExecution, e: Throwable) {
                // 等待100ms再收集日志，确保所有延迟日志都被收集
                CoroutineScope(Dispatchers.IO).launch {
                    delay(100)
                    
                    // 收集日志
                    val allLogs = globalConsole.allLogs
                    if (allLogs.size > initialLogCount) {
                        val newLogs = allLogs.subList(initialLogCount, allLogs.size)
                        newLogs.forEach { logs.add("${getLevelName(it.level)}: ${it.content}") }
                    }
                    
                    continuation.resumeWithException(e)
                }
            }
        }
        
        val execution = scriptEngineService.execute(scriptSource, listener, ExecutionConfig())
        
        // 设置取消处理
        continuation.invokeOnCancellation {
            execution.engine?.forceStop()
        }
    }
}

// 获取日志级别名称
private fun getLevelName(level: Int): String {
    return when (level) {
        Log.VERBOSE -> "VERBOSE"
        Log.DEBUG -> "DEBUG"  
        Log.INFO -> "INFO"
        Log.WARN -> "WARN"
        Log.ERROR -> "ERROR"
        else -> "UNKNOWN"
    }
}

