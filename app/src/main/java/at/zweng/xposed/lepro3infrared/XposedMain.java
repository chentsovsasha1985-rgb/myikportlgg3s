package at.zweng.xposed.lepro3infrared;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Array;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.findAndHookConstructor;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;

public class XposedMain implements IXposedHookLoadPackage {

    public static final String TAG = "LeEco_Infrared_Fix";
    private InfraredMagic mInfraredMagic;
    private static Object sLastMakeDataBuffer = null;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if ("com.uei.lg.quicksetsdk.lite".equals(lpparam.packageName)) {
            hookQuickSetIrrc(lpparam.classLoader);
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
                Object freqs = Array.newInstance(int.class, 2);
                Array.setInt(freqs, 0, 30000);
                Array.setInt(freqs, 1, 60000);
                param.setResult(freqs);
            }
        });

        Log.i(TAG, "Hooked ConsumerIrService successfully!");
    }

    private void hookQuickSetIrrc(ClassLoader classLoader) {
        Class<?> irrcPlayer = findClass("com.lge.irrc.IRRCPlayer", classLoader);
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args == null || Array.getLength(param.args) < 2) {
                    return;
                }
                Object arg0 = Array.get(param.args, 0);
                if (!(arg0 instanceof Integer)) {
                    return;
                }
                int dataType = (Integer) arg0;
                Object buf = Array.get(param.args, 1);
                int bufLen = (buf != null && buf.getClass().isArray()) ? Array.getLength(buf) : 0;

                if (dataType == 1 && bufLen > 0) {
                    sLastMakeDataBuffer = buf;
                } else if (dataType == 2 && bufLen == 0 && sLastMakeDataBuffer != null) {
                    Array.set(param.args, 1, sLastMakeDataBuffer);
                    Log.i(TAG, "Injected MAKE_DATA into empty REPEAT_DATA buffer! len=" + Array.getLength(sLastMakeDataBuffer));
                }
            }
        };

        try {
            findAndHookMethod(irrcPlayer, "setIRRCInfos", int.class, Class.forName("[C"), hook);
            log(TAG + ": Hooked IRRCPlayer.setIRRCInfos(int, char[])!");
        } catch (Throwable ignored) {
        }

        try {
            findAndHookMethod(irrcPlayer, "setIRRCInfos", int.class, Class.forName("[B"), hook);
            log(TAG + ": Hooked IRRCPlayer.setIRRCInfos(int, byte[])!");
        } catch (Throwable ignored) {
        }
    }
}
