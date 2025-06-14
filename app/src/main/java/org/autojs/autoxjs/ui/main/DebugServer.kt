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
                Log.i(TAG, "Executing JavaScript code")

                // 使用协程等待执行完成并获取结果
                val result = runBlocking {
                    runCodeString(code)
                }

                // 构建响应内容
                val responseText = buildString {
                    result.second.forEach { log ->
                        appendLine(log)
                    }
                }

                newFixedLengthResponse(Response.Status.OK, "text/plain", responseText)
            } else {
                Log.w(TAG, "Missing 'code' parameter in request")
                newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing 'code' parameter")
            }
        }

        Log.w(TAG, "Endpoint not found: $uri")
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
    }
}

@SuppressLint("SdCardPath")
suspend fun runCodeString(code: String): Pair<Any?, List<String>> {
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
            val created = dir.mkdirs()
            Log.d(TAG, "Created debug directory: $dirPath, success: $created")
        }

        // 写入文件
        val file = File(filePath)
        file.writeText(code)

        Log.i(TAG, "Script saved successfully to: $filePath")
        Log.i(TAG, "Starting script execution...")

        // 执行脚本并等待结果
        val scriptSource = ScriptFile(filePath).toSource()
        if (scriptSource == null) {
            Log.e(TAG, "Failed to create script source from file: $filePath")
            return Pair("Error: Script source not found", listOf("ERROR: Failed to create script source from file: $filePath"))
        }
        
        return try {
            val (result, logs) = executeScriptAndWait(scriptSource)
            
            Log.i(TAG, "Script execution completed successfully")
            
            Pair(result, logs)
        } catch (e: Exception) {
            Log.e(TAG, "Script execution failed: ${e.message}", e)
            Pair("Error: ${e.message}", listOf("ERROR: Script execution failed: ${e.message}"))
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to save script file: ${e.message}", e)
        return Pair("Error: ${e.message}", listOf("ERROR: Failed to save script file: ${e.message}"))
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
                CoroutineScope(Dispatchers.IO).launch {
                    delay(34)

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
                CoroutineScope(Dispatchers.IO).launch {
                    delay(34)

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

