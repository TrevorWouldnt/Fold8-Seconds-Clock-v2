package de.robv.android.xposed;
public class XposedHelpers {
    public static Class<?> findClass(String name, ClassLoader loader) { throw new UnsupportedOperationException(); }
    public static Object getObjectField(Object obj, String fieldName) {throw new UnsupportedOperationException();}
    // Correct binary return type, verified against the Xposed API and the v6 fix.
    public static XC_MethodHook.Unhook findAndHookMethod(Class<?> clazz, String name, Object... params) {
        throw new UnsupportedOperationException();
    }
}
