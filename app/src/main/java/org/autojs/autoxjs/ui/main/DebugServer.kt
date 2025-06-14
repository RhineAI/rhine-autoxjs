import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.autojs.autoxjs.model.script.ScriptFile
import org.autojs.autoxjs.model.script.Scripts
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

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

        Log.i(TAG, "Running script...")

        Scripts.run(ScriptFile(filePath))
    } catch (e: Exception) {
        Log.i(TAG, "Error saving file: ${e.message}")
    }
}

