package at.zweng.xposed.lepro3infrared;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Array;
import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.findAndHookConstructor;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;

public class XposedMain implements IXposedHookLoadPackage {

    public static final String TAG = "LeEco_Infrared_Fix";
    public static final int[] CONSUMERIR_CARRIER_FREQUENCIES = {30000, 60000};
    private InfraredMagic mInfraredMagic;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if ("com.lge.qremote".equals(lpparam.packageName)) {
            spoofLgG3Model();
            unlockQRemoteCategories(lpparam.classLoader);
            return;
        }

        if ("com.uei.lg.quicksetsdk.lite".equals(lpparam.packageName)) {
            spoofLgG3Model();
            log(TAG + ": Spoofed LG-D855 for QuickSetSDK!");
            return;
        }

        if ("com.tiqiaa.remote".equals(lpparam.packageName)) {
            spoofLgG3Model();
            log(TAG + ": Spoofed LG-D855 for ZazaRemote!");
            return;
        }

        if (!"android".equals(lpparam.packageName)) {
            return;
        }

        Log.d(TAG, "We are in 'android' package. Will try to hook ConsumerIrService...");
        mInfraredMagic = new InfraredMagic();

        Class<?> consumerIrService = findClass("com.android.server.ConsumerIrService", lpparam.classLoader);

        findAndHookConstructor(consumerIrService, Context.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Log.d(TAG, "ConsumerIrService constructor was called.");
                Object arg0 = Array.get(param.args, 0);
                if (arg0 != null && arg0 instanceof Context) {
                    mInfraredMagic.setContext((Context) arg0);
                    mInfraredMagic.bindQuickSetService();
                }
            }
        });

        findAndHookMethod(consumerIrService, "throwIfNoIrEmitter", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(null);
            }
        });

        findAndHookMethod(consumerIrService, "hasIrEmitter", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Log.v(TAG, "hasIrEmitter was called, returning true");
                param.setResult(true);
            }
        });

        if (Build.VERSION.SDK_INT >= 21) {
            Class<?> intArrayClass = Class.forName("[I");
            findAndHookMethod(consumerIrService, "halTransmit", int.class, intArrayClass, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    int carrierFrequency = (Integer) Array.get(param.args, 0);
                    int[] pattern = (int[]) Array.get(param.args, 1);
                    int result = mInfraredMagic.transmitIrPattern(carrierFrequency, pattern);
                    Log.v(TAG, "halTransmit was called. return value: " + result);
                    param.setResult(result);
                }
            });
        }

        findAndHookMethod(consumerIrService, "getCarrierFrequencies", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(CONSUMERIR_CARRIER_FREQUENCIES);
            }
        });

        Log.i(TAG, "Hooked ConsumerIrService successfully!");
    }

    private void spoofLgG3Model() {
        try {
            XposedHelpers.setStaticObjectField(Build.class, "MODEL", "LG-D855");
            XposedHelpers.setStaticObjectField(Build.class, "DEVICE", "g3");
            XposedHelpers.setStaticObjectField(Build.class, "PRODUCT", "g3_global_com");
        } catch (Throwable ignored) {
        }
    }

    private void unlockQRemoteCategories(ClassLoader classLoader) {
        String[] targetClasses = {
                "com.lge.qremote.settings.utils.QRemoteUtils",
                "com.lge.qremote.settings.AppState",
                "com.lge.qremote.settings.SetupServiceManager",
                "com.lge.qremote.main.AddDeviceActivity"
        };

        for (String clsName : targetClasses) {
            try {
                Class<?> cls = findClass(clsName, classLoader);
                for (Method m : cls.getDeclaredMethods()) {
                    String name = m.getName();
                    if ("isSWIrRC".equals(name)) {
                        XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(false));
                    } else if ("isSettopSupported".equals(name)
                            || "isAirconRemoteOn".equals(name)
                            || "isAudioRemoteOn".equals(name)
                            || "isIptvRemoteOn".equals(name)
                            || "isLearningSupported".equals(name)
                            || "isHomeControlSupported".equals(name)) {
                        XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(true));
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        log(TAG + ": Spoofed LG-D855 & unlocked all QRemote categories!");
    }
}
