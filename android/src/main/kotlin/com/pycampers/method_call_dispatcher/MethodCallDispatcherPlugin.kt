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

/**
 * Runs [fn], ignoring [IllegalStateException], if encountered.
 *
 * Workaround for https://github.com/flutter/flutter/issues/29092.
 */
fun ignoreIllegalState(fn: () -> Unit) {
    try {
        fn()
    } catch (e: IllegalStateException) {
        Log.d(TAG, "ignoring exception: $e. See https://github.com/flutter/flutter/issues/29092 for details.")
    }
}

/**
 * Try to send the value returned by [fn] using [result] ([Result.success]),
 * by encapsulating calls inside [ignoreIllegalState].
 *
 * It is advisable to wrap any native code inside [fn],
 * because this will automatically send exceptions using error using [trySendThrowable] if required.
 */
fun trySend(result: Result, fn: (() -> Any?)? = null) {
    val value: Any?
    try {
        value = fn?.invoke()
    } catch (e: Throwable) {
        trySendThrowable(result, e)
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

/**
 * Serialize the stacktrace contained in [throwable] to a [String].
 */
fun serializeStackTrace(throwable: Throwable): String {
    val sw = StringWriter()
    val pw = PrintWriter(sw)
    throwable.printStackTrace(pw)
    return sw.toString()
}

/**
 * Try to send an error using [Result.error],
 * by encapsulating calls inside [ignoreIllegalState].
 */
fun trySendError(result: Result, name: String?, message: String?, stackTrace: String?) {
    ignoreIllegalState {
        Log.d(TAG, "piping exception to flutter ($name)")
        result.error(name, message, stackTrace)
    }
}

/**
 * Serialize the [throwable] and send it using [trySendError].
 */
fun trySendThrowable(result: Result, throwable: Throwable) {
    val e = throwable.cause ?: throwable
    trySendError(
        result,
        e.javaClass.canonicalName,
        e.message,
        serializeStackTrace(e)
    )
}

/**
 * Inherit this class to make any kotlin methods with the signature:-
 *
 *  methodName([MethodCall], [Result])
 *
 * be magically available to Flutter's platform channels,
 * by the power of dynamic dispatch!
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
                trySendThrowable(result, e)
            }
        }
    }
}