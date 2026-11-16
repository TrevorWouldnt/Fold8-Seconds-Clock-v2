package de.robv.android.xposed;
import java.lang.reflect.Member;
import java.util.Set;
public class XposedBridge {
    public static void log(String message) { System.err.println(message); }
    public static void log(Throwable error) { error.printStackTrace(); }
    public static XC_MethodHook.Unhook hookMethod(Member member, XC_MethodHook callback) { throw new UnsupportedOperationException(); }
    public static Set<XC_MethodHook.Unhook> hookAllConstructors(Class<?> type, XC_MethodHook callback) { throw new UnsupportedOperationException(); }
    public static Set<XC_MethodHook.Unhook> hookAllMethods(Class<?> type, String name, XC_MethodHook callback) { throw new UnsupportedOperationException(); }
}
