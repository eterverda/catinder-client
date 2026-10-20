package com.catinder.logging

import android.util.Log

object MiniLogger {
    private const val TAG = "Catinder"

    @JvmStatic
    fun d(message: String) {
        Log.d(TAG, message)
    }

    @JvmStatic
    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
    }
}
