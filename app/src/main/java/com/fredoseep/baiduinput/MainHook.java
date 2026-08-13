package com.fredoseep.baiduinput;

import android.content.Context;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    private static final boolean isDebug = false;
    private static final String TARGET_KEY = "EN_FIND";
    private static String currentFullText = null;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.baidu.input_oppo") && !lpparam.packageName.equals("com.baidu.input")) {
            return;
        }
        log("module successfully implemented into baidu IME");
        try {
            System.loadLibrary("dexkit");
        } catch (Throwable t) {
            log("加载 DexKit 动态库失败: " + t.getMessage());
        }

        DexKitHelper helper = new DexKitHelper();
        helper.resolve(lpparam);

        XposedHelpers.findAndHookMethod(
                "android.app.Application",
                lpparam.classLoader,
                "onCreate",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // 2. 当 onCreate 真正被系统调用时，这里的代码才会执行
                        // onCreate 没有参数，必须用 thisObject 获取实例
                        Context context = (Context) param.thisObject;
                        ClassLoader realClassLoader = context.getClassLoader();

                        if (realClassLoader == null) {
                            log("error: realClassLoader is null during onCreate");
                            return;
                        }

                        log("successfully initialized the realClassLoader");

                        doAllHooks(realClassLoader,helper);
                    }
                }
        );
    }

    private void doAllHooks(ClassLoader realClassLoader, DexKitHelper helper) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.baidu.input.clipboard.manager.BDClipboardManager",
                    realClassLoader,
                    helper.onGettingNewClipboardContentMethodName,
                    CharSequence.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            CharSequence text = (CharSequence) param.args[0];
                            if (text != null && text.length() > 7000) {
                                currentFullText = text.toString();
                                log("【源头监控】截获到超长文本，长度: " + text.length());
                            } else {
                                currentFullText = null; // 短文本不干扰
                            }
                        }
                    }
            );

            XposedHelpers.findAndHookMethod(
                    "com.baidu.input.clipboard.datamanager.clipboard.ClipboardDataManagerImpl",
                    realClassLoader,
                    helper.setClipboardMethodName,
                    String.class,
                    boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String str = (String) param.args[0];

                            if (str != null && str.length() == 7000 && currentFullText != null) {
                                if (currentFullText.startsWith(str)) {
                                    param.args[0] = currentFullText;
                                    log("【入库替换】已在 Lk 方法拦截！成功将文本复原为长度: " + currentFullText.length());
                                    currentFullText = null;
                                }
                            }
                        }
                    }
            );
        } catch (Exception e) {
            log("Hook 入库流程异常: " + e.toString());
        }


//inlined method
        try {
            XposedHelpers.findAndHookMethod(
                    "com.baidu.input.sync.clipboard.ClipboardSyncHelper",
                    realClassLoader,
                    "b",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            param.setResult(false);
                            log("【逻辑欺骗】已强制禁用同步检查，完美绕过 7000 字截断！");
                        }
                    }
            );
        } catch (Exception e) {
            log("Hook ClipboardSyncHelper.b 异常: " + e.toString());
        }

        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.SharedPreferencesImpl$EditorImpl",
                    realClassLoader,
                    "putBoolean",
                    String.class,
                    boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String key = (String) param.args[0];
                            boolean value = (Boolean) param.args[1];
                            if (TARGET_KEY.equals(key)) {
                                if (!value) {
                                    param.args[1] = true;
                                }
                            }
                        }
                    }
            );
        } catch (Throwable t) {
            log("ERROR: " + t.toString());
            t.printStackTrace();
        }

        try {
            XposedHelpers.findAndHookMethod(
                    "com.baidu.input.privacy.impl.PrivacyImpl",
                    realClassLoader,
                    helper.uploadPrivacyRecordMethodName,
                    "com.baidu.input.privacy.api.data.PrivacyRecord",
                    XC_MethodReplacement.returnConstant(null)
            );
            log("original internet call set null successfully");
        } catch (Throwable t) {
            log(t.toString());
        }

        try {
            XposedHelpers.findAndHookMethod(
                    "com.baidu.input.ime.event.GetLocationHandler$1",
                    realClassLoader,
                    "accept",
                    Object.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Object locationObj = param.args[0];
                            if (locationObj != null && locationObj.getClass().getName().equals("android.location.Location")) {
                                XposedHelpers.callMethod(locationObj, "setLongitude", 0.0);
                                XposedHelpers.callMethod(locationObj, "setLatitude", 0.0);
                            }
                        }
                    }
            );
            log("second internet call set null successfully");
        } catch (Throwable t) {
            log(t.toString());
        }


        try {
            Class<?> completableEmptyClass = XposedHelpers.findClass("io.reactivex.internal.operators.completable.CompletableEmpty", realClassLoader);
            final Object emptyCompletable = XposedHelpers.getStaticObjectField(completableEmptyClass, completableEmptyClass.getFields()[0].getName());

            XC_MethodReplacement lambdaReplacer = new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    log("【隐私保护】已在底层拦截云同步发包任务，替换为系统原生空载荷！");
                    // 直接返回百度原生的空任务，完美融入它的 RxJava 链条
                    return emptyCompletable;
                }
            };

            XposedHelpers.findAndHookMethod(
                    "com.baidu.input.sync.clipboard.ClipboardSyncHelper$startAutoSync$1",
                    realClassLoader,
                    "invoke",
                    lambdaReplacer
            );

            XposedHelpers.findAndHookMethod(
                    "com.baidu.input.sync.clipboard.ClipboardSyncHelper$startSyncWithSuccessPref$1",
                    realClassLoader,
                    "invoke",
                    lambdaReplacer
            );

            XposedHelpers.findAndHookMethod(
                    "com.baidu.input.sync.clipboard.ClipboardSyncHelper$startUploadOnlyAutoSync$1",
                    realClassLoader,
                    "invoke",
                    lambdaReplacer
            );

        } catch (Exception ex) {
            log("Hook 深入云同步底层异常: " + ex.toString());
        }

        try {
            XposedHelpers.findAndHookMethod(
                        "com.baidu.input.di.feed.QuickAccessAdDependency",
                    realClassLoader,
                    helper.adFingerprintCollectionMethodName,
                    XC_MethodReplacement.returnConstant("")
            );
            log("Ad fingerprint collection blocked successfully");
        } catch (Throwable t) {
            log("Ad fingerprint Hook Error: " + t.toString());
        }
    }

    public static void log(String text) {
        if (isDebug) XposedBridge.log(text);
    }
}