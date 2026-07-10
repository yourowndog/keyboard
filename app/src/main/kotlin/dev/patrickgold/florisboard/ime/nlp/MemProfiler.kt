/*
 * MemProfiler - Heap usage checkpoints for NLP init stages.
 *
 * Logs Java heap (used/max) and native heap at named stages so we can see
 * exactly which load step (SymSpell index, n-gram engine, bigram table,
 * ONNX session) is responsible for memory growth on-device.
 *
 * Read with: adb logcat -s MemProfiler   (or logcat viewer in Termux)
 */
package dev.patrickgold.florisboard.ime.nlp

import android.os.Debug
import android.util.Log
import java.util.Locale

object MemProfiler {
    private const val TAG = "MemProfiler"

    fun log(stage: String) {
        try {
            val rt = Runtime.getRuntime()
            val usedMb = (rt.totalMemory() - rt.freeMemory()) / 1048576.0
            val maxMb = rt.maxMemory() / 1048576.0
            val nativeMb = Debug.getNativeHeapAllocatedSize() / 1048576.0
            Log.i(
                TAG,
                String.format(
                    Locale.US,
                    "%s: javaHeap=%.1f/%.1fMB nativeHeap=%.1fMB",
                    stage, usedMb, maxMb, nativeMb,
                )
            )
        } catch (_: Exception) {
            // Never let instrumentation break init
        }
    }
}
