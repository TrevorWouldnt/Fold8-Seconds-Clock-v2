package com.fold8.secondsclock;

import android.os.Handler;
import android.os.Looper;
import android.view.View;

/** Vector can load module classes before Android prepares its main Looper. */
final class MainThread {
    // Class loading must not create a Handler or query Android's main Looper.
    private static volatile Handler handler;

    private MainThread() { }

    static Handler handler() {
        Handler result = handler;
        if (result != null) return result;
        Looper looper = Looper.getMainLooper();
        if (looper == null) return null;
        synchronized (MainThread.class) {
            result = handler;
            if (result == null) handler = result = Handler.createAsync(looper);
        }
        return result;
    }

    /** For operations reached only after a clock has entered Android's UI lifecycle. */
    static Handler requireHandler() {
        Handler result = handler();
        if (result == null) throw new IllegalStateException("Android main Looper is not ready");
        return result;
    }

    /** An unattached View queues work until attachment if no main Looper exists yet. */
    static boolean post(View anchor, Runnable task) {
        Handler result = handler();
        if (result != null) return result.post(task);
        return anchor != null && anchor.post(task);
    }

    static boolean isMainThread() {
        Looper looper = Looper.getMainLooper();
        return looper != null && Looper.myLooper() == looper;
    }
}
