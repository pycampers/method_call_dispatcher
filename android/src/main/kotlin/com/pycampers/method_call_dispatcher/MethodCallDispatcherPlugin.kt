package com.pycampers.method_call_dispatcher

import android.os.AsyncTask
import android.util.Log
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import java.io.PrintWriter
import java.io.StringWriter

const val TAG = "MethodCallDispatcher"

class MethodCallDispatcherPlugin {
    companion object {
        @JvmStatic
        fun registerWith(registrar: Registrar) = Unit
    }
}

class DoAsync(val fn: () -> Unit) : AsyncTask<Void, Void, Void>() {
    init {
        execute()
    }

    override fun doInBackground(vararg params: Void?): Void? {
        fn()
        return null
    }
}

/*
Run `fn`, ignoring `IllegalStateException`.
(workaround for https://github.com/flutter/flutter/issues/29092.)
*/
fun ignoreIllegalState(fn: () -> Unit) {
    try {
        fn()
    } catch (e: IllegalStateException) {
        Log.d(TAG, "ignoring exception: $e. See https://github.com/flutter/flutter/issues/29092 for details.")
    }
}

/*
Try to send the value returned by `fn()` using `result`.
Sends an error using `sendError` if required.
*/
fun <T> trySend(result: Result, fn: () -> T) {
    val value: T
    try {
        value = fn()
    } catch (e: Throwable) {
        sendError(e, result)
        return
    }

    ignoreIllegalState {
        if (value is Unit) {
            result.success(null)
        } else {
            result.success(value)
        }
    }
}

fun serializeStackTrace(e: Throwable): String {
    val sw = StringWriter()
    val pw = PrintWriter(sw)
    e.printStackTrace(pw)
    return sw.toString()
}

/* Pipe the exception `throwable` back to flutter using `result.error()`. */
fun sendError(throwable: Throwable, result: Result) {
    val e = throwable.cause ?: throwable
    ignoreIllegalState {
        Log.d(TAG, "piping exception to flutter: $e")
        result.error(
            e.javaClass.canonicalName,
            e.message,
            serializeStackTrace(e)
        )
    }
}

/*
Inherit this class to make any kotlin methods with the signature:-

    `methodName(io.flutter.plugin.common.MethodCall, io.flutter.plugin.common.MethodChannel.Result)`

be magically available to flutter platform channels, by the power of dynamic dispatch!
*/
open class MethodCallDispatcher : MethodCallHandler {
    override fun onMethodCall(call: MethodCall, result: Result) {
        val methodName = call.method

        val method = try {
            javaClass.getMethod(methodName, MethodCall::class.java, Result::class.java)
        } catch (e: java.lang.Exception) {
            when (e) {
                is NoSuchMethodException, is SecurityException -> null
                else -> throw e
            }
        }

        if (method == null) {
            result.notImplemented()
            return
        }

        DoAsync {
            Log.d(TAG, "invoking { ${javaClass.simpleName}.$methodName() }...")
            try {
                ignoreIllegalState { method.invoke(this, call, result) }
            } catch (e: Throwable) {
                sendError(e, result)
            }
        }
    }
}