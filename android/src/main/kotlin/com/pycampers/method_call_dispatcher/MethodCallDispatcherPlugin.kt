package com.pycampers.method_call_dispatcher

import android.os.AsyncTask
import android.util.Log
import io.flutter.plugin.common.EventChannel.EventSink
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import java.io.PrintWriter
import java.io.StringWriter

const val TAG = "MethodCallDispatcher"

typealias OnError = (errorCode: String, errorMessage: String?, errorDetails: Any?) -> Unit
typealias OnSuccess = (result: Any?) -> Unit
typealias AnyFunc = () -> Any?
typealias unitFunc = () -> Unit

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
fun ignoreIllegalState(fn: unitFunc) {
    try {
        fn()
    } catch (e: IllegalStateException) {
        Log.d(TAG, "ignoring exception: $e. See https://github.com/flutter/flutter/issues/29092 for details.")
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
fun trySendError(onError: OnError, name: String?, message: String?, stackTrace: String?) {
    ignoreIllegalState {
        Log.d(TAG, "piping exception to flutter ($name)")
        onError(name ?: "null", message, stackTrace)
    }
}

fun trySendError(result: Result, name: String?, message: String?, stackTrace: String?) {
    trySendError(result::error, name, message, stackTrace)
}

fun trySendError(events: EventSink, name: String?, message: String?, stackTrace: String?) {
    trySendError(events::error, name, message, stackTrace)
}

/**
 * Serialize the [throwable] and send it using [trySendError].
 */
fun trySendThrowable(onError: OnError, throwable: Throwable) {
    val e = throwable.cause ?: throwable
    trySendError(
        onError,
        e.javaClass.canonicalName,
        e.message,
        serializeStackTrace(e)
    )
}

fun trySendThrowable(result: Result, throwable: Throwable) = trySendThrowable(result::error, throwable)
fun trySendThrowable(events: EventSink, throwable: Throwable) = trySendThrowable(events::error, throwable)

/**
 * Try to send the value returned by [fn] using [result] ([Result.success]),
 * by encapsulating calls inside [ignoreIllegalState].
 *
 * It is advisable to wrap any native code inside [fn],
 * because this will automatically send exceptions using error using [trySendThrowable] if required.
 */
fun trySend(onSuccess: OnSuccess, onError: OnError, fn: AnyFunc? = null) {
    val value: Any?
    try {
        value = fn?.invoke()
    } catch (e: Throwable) {
        trySendThrowable(onError, e)
        return
    }

    ignoreIllegalState {
        onSuccess(if (value is Unit) null else value)
    }
}

fun trySend(result: Result, fn: AnyFunc? = null) = trySend(result::success, result::error, fn)
fun trySend(events: EventSink, fn: AnyFunc? = null) = trySend(events::success, events::error, fn)

/**
 * Run [fn].
 * Automatically send exceptions using error using [trySendThrowable] if required.
 *
 * This differs from [trySend],
 * in that it won't invoke [Result.success] using the return value of [fn].
 */
fun catchErrors(onError: OnError, fn: unitFunc) {
    try {
        fn.invoke()
    } catch (e: Throwable) {
        trySendThrowable(onError, e)
    }
}

fun catchErrors(result: Result, fn: unitFunc) = catchErrors(result::error, fn)
fun catchErrors(events: EventSink, fn: unitFunc) = catchErrors(events::error, fn)

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
            Log.d(TAG, "invoke { method: ${javaClass.simpleName}.$methodName(), args: ${call.arguments} }")
            try {
                ignoreIllegalState { method.invoke(this, call, result) }
            } catch (e: Throwable) {
                trySendThrowable(result, e)
            }
        }
    }
}