package hu.bbara.viewideas.util

import android.util.Log

inline fun <T> logDuration(tag: String, blockName: String, block: () -> T): T {
    val start = System.currentTimeMillis()
    return try {
        block()
    } finally {
        val duration = System.currentTimeMillis() - start
        Log.d(tag, "$blockName took ${duration}ms")
    }
}
