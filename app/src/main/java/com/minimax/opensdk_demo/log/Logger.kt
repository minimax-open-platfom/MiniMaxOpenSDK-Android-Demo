package com.minimax.opensdk_demo.log

import android.util.Log
import com.google.android.exoplayer2.common.BuildConfig
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 *  Created by xijue on 2024/3/4
 *
 */
object Logger {

    fun d(tag: String, config: LogConfig = LogConfig(), msg: () -> String) {
        runInTest {
            val message =
                msg() + if (config.stackTraceEnable) "\nTrace:${Throwable().stackTraceToString()}" else ""
            Log.d(tag, message)
            appendLogItem(Log.DEBUG, tag, msg())
        }
    }

    fun i(tag: String, config: LogConfig = LogConfig(), msg: () -> String) {
        runInTest {
            val message =
                msg() + if (config.stackTraceEnable) "\nTrace:${Throwable().stackTraceToString()}" else ""
            Log.i(tag, message)
            appendLogItem(Log.INFO, tag, msg())
        }
    }


    fun w(tag: String, config: LogConfig = LogConfig(), msg: () -> String) {
        runInTest {
            val message =
                msg() + if (config.stackTraceEnable) "\nTrace:${Throwable().stackTraceToString()}" else ""
            Log.w(tag, message)
            appendLogItem(Log.WARN, tag, msg())
        }
    }


    fun e(tag: String, config: LogConfig = LogConfig(), msg: () -> String) {
        runInTest {
            val message =
                msg() + if (config.stackTraceEnable) "\nTrace:${Throwable().stackTraceToString()}" else ""
            Log.e(tag, message)
            appendLogItem(Log.ERROR, tag, msg())
        }
    }

    @Synchronized
    private fun appendLogItem(level: Int, tag: String, msg: String) {
    }

    private fun runInTest(block: () -> Unit) {
        if (BuildConfig.DEBUG) {
            block()
        }
    }

    val LINE_SEPARATOR = System.getProperty("line.separator")

    private fun printLine(tag: String, isTop: Boolean) {
        if (isTop) {
            d(tag) {
                "╔═══════════════════════════════════════════════════════════════════════════════════════"
            }
        } else {
            d(tag) {
                "╚═══════════════════════════════════════════════════════════════════════════════════════"
            }
        }
    }

    fun logJson(tag: String, headString: String, msgStr: () -> String) {
        var message: String
        val msg = msgStr.invoke()
        message = try {
            if (msg.startsWith("{")) {
                val jsonObject = JSONObject(msg)
                jsonObject.toString(4) //最重要的方法，就一行，返回格式化的json字符串，其中的数字4是缩进字符数
            } else if (msg.startsWith("[")) {
                val jsonArray = JSONArray(msg)
                jsonArray.toString(4)
            } else {
                msg
            }
        } catch (e: JSONException) {
            msg
        }
        printLine(tag, true)
        message = headString + LINE_SEPARATOR + message
        val lines = message.split(LINE_SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()
        for (line in lines) {
            d(tag) {
                "║ $line"
            }
        }
        printLine(tag, false)
    }
}