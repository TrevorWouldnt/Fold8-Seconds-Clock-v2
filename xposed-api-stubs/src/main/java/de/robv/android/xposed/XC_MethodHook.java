package de.robv.android.xposed;
public abstract class XC_MethodHook {
    public class Unhook {
        public void unhook() { throw new UnsupportedOperationException(); }
    }
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}
    public static class MethodHookParam {
        public Object thisObject;
        public Object[] args;
        public void setResult(Object result) { throw new UnsupportedOperationException(); }
    }
}
