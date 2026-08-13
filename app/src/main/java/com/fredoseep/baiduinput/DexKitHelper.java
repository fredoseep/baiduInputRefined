package com.fredoseep.baiduinput;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindField;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.FieldMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.FieldData;
import org.luckypray.dexkit.result.MethodData;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class DexKitHelper {

    public String onGettingNewClipboardContentMethodName = "t";
    public String setClipboardMethodName = "Lk";

    public String uploadPrivacyRecordMethodName = "n2";

    public String adFingerprintCollectionMethodName = "a";





    public final boolean IS_TESTING = false;

    /**
     * 解析并加载混淆变量。如果缓存有效则直接加载，否则启动 DexKit 扫描。
     */
    public void resolve(XC_LoadPackage.LoadPackageParam lpparam) {
        File apkFile = new File(lpparam.appInfo.sourceDir);
        long currentApkTime = apkFile.lastModified();
        File cacheFile = new File(lpparam.appInfo.dataDir, "cache/dexkit_hook_cache.properties");
        Properties cacheProps = new Properties();
        boolean needScan = true;

        if (cacheFile.exists()) {
            try (FileInputStream fis = new FileInputStream(cacheFile)) {
                cacheProps.load(fis);
                String cachedTimeStr = cacheProps.getProperty("apk_last_modified");

                if (cachedTimeStr != null && cachedTimeStr.equals(String.valueOf(currentApkTime))) {
                    MainHook.log("命中 DexKit 缓存，APK 未更新，跳过扫描");
                    onGettingNewClipboardContentMethodName = cacheProps.getProperty("onGettingNewClipboardContentMethodName", onGettingNewClipboardContentMethodName);
                    setClipboardMethodName = cacheProps.getProperty("setClipboardMethodName", setClipboardMethodName);
                    uploadPrivacyRecordMethodName = cacheProps.getProperty("uploadPrivacyRecordMethodName", uploadPrivacyRecordMethodName);
                    adFingerprintCollectionMethodName = cacheProps.getProperty("adFingerprintCollectionMethodName", adFingerprintCollectionMethodName);
                    needScan = false;
                }
            } catch (Exception e) {
                MainHook.log("读取缓存失败，将重新扫描: " + e.getMessage());
            }
        }

        if (needScan || IS_TESTING) {
            MainHook.log(" baiduinput版本更新或首次运行，启动 DexKit 深度扫描...");
            try (DexKitBridge bridge = DexKitBridge.create(lpparam.appInfo.sourceDir)) {
                if (bridge == null) {
                    MainHook.log("❌ DexKit 初始化失败！");
                    return;
                }

                List<ClassData> BDClipboardManagerClassDataList = bridge.findClass(
                        FindClass.create().matcher(ClassMatcher.create().className("com.baidu.input.clipboard.manager.BDClipboardManager"))
                );

                List<MethodData> onGettingNewClipboardContentMethodDataList = bridge.findMethod(
                        FindMethod.create().searchInClass(BDClipboardManagerClassDataList).matcher(
                                MethodMatcher.create()
                                        .paramCount(1)
                                        .paramTypes("java.lang.CharSequence")
                        )
                );
                onGettingNewClipboardContentMethodName = onGettingNewClipboardContentMethodDataList.get(0).getMethodName();

                List<ClassData> ClipboardDataManagerImplClassDataList = bridge.findClass(
                        FindClass.create().matcher(ClassMatcher.create().className("com.baidu.input.clipboard.datamanager.clipboard.ClipboardDataManagerImpl"))
                );

                List<MethodData> setClipboardMethodDataList = bridge.findMethod(
                        FindMethod.create().searchInClass(ClipboardDataManagerImplClassDataList).matcher(
                                MethodMatcher.create()
                                        .paramCount(2)
                                        .paramTypes("java.lang.String","boolean")
                                        .returnType("com.baidu.input.clipboard.datamanager.clipboard.dto.ClipboardItemDTO")
                        )
                );
                setClipboardMethodName = setClipboardMethodDataList.get(0).getMethodName();

                List<ClassData> PrivacyImplClassDataList = bridge.findClass(
                        FindClass.create().matcher(ClassMatcher.create().className("com.baidu.input.privacy.impl.PrivacyImpl"))
                );

                List<MethodData> uploadPrivacyRecordMethodDataList = bridge.findMethod(
                        FindMethod.create().searchInClass(PrivacyImplClassDataList).matcher(
                                MethodMatcher.create()
                                        .paramCount(1)
                                        .usingStrings("pref_key_privacy_location_list")
                        )
                );
                uploadPrivacyRecordMethodName = uploadPrivacyRecordMethodDataList.get(0).getMethodName();

                List<ClassData> QuickAccessAdDependencyClassDataList = bridge.findClass(
                        FindClass.create().matcher(ClassMatcher.create().className("com.baidu.input.di.feed.QuickAccessAdDependency"))
                );

                List<MethodData> adFingerprintCollectionMethodDataList = bridge.findMethod(
                        FindMethod.create().searchInClass(QuickAccessAdDependencyClassDataList).matcher(
                                MethodMatcher.create()
                                        .usingStrings("getQuickAccessAdClientInfo: ")
                        )
                );
                adFingerprintCollectionMethodName = adFingerprintCollectionMethodDataList.get(0).getMethodName();



                cacheProps.setProperty("apk_last_modified", String.valueOf(currentApkTime));
                cacheProps.setProperty("onGettingNewClipboardContentMethodName", onGettingNewClipboardContentMethodName);
                cacheProps.setProperty("setClipboardMethodName", setClipboardMethodName);
                cacheProps.setProperty("uploadPrivacyRecordMethodName", uploadPrivacyRecordMethodName);
                cacheProps.setProperty("adFingerprintCollectionMethodName", adFingerprintCollectionMethodName);





                cacheFile.getParentFile().mkdirs();
                try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                    cacheProps.store(fos, "DexKit Obfuscation Cache for BiliBili");
                    MainHook.log(" DexKit 扫描完成，结果已持久化至缓存");
                }
            } catch (Exception e) {
                MainHook.log("❌ DexKit 扫描过程发生异常: " + e.getMessage());
            }
        }
    }

    public ClassData getOuterClass(DexKitBridge bridge, ClassData innerClassData) {
        String innerClassName = innerClassData.getName();
        int dollarIndex = innerClassName.indexOf('$');
        if (dollarIndex == -1) {
            return null;
        }
        String outerClassName = innerClassName.substring(0, dollarIndex);
        return bridge.getClassData(outerClassName);
    }

    private static void listDataPrint(List<?> dataList) {
        if (dataList.isEmpty()) {
            MainHook.log("error: data list is empty");
            return;
        }
        MainHook.log("found " + dataList.size() + " class");
        for (Object data : dataList) {
            if (data instanceof ClassData) {
                MainHook.log("found class name: " + ((ClassData) data).getName() + " ");
            } else if (data instanceof MethodData) {
                MainHook.log("found method name: " + ((MethodData) data).getName() + " ");
            } else if (data instanceof FieldData) {
                MainHook.log("found field name: " + ((FieldData) data).getName() + " ");
            }
        }
    }
}