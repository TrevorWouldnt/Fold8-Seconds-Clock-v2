package de.robv.android.xposed;
public class XposedBridge {
    // Used by JVM tests only. Vector supplies these methods on the phone.
    public static void log(String message) { System.err.println(message); }
    public static void log(Throwable t) { t.printStackTrace(); }
}
