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
    public static final int[] CONSUMERIR_CARRIER_FREQUENCIES = {30000, 60000};
    private InfraredMagic mInfraredMagic;

    private static char[] sLastMakeData = null;
    private static int sLastMakeLen = 0;

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
                param.setResult(CONSUMERIR_CARRIER_FREQUENCIES);
            }
        });

        Log.i(TAG, "Hooked ConsumerIrService successfully!");
    }

    private void hookQuickSetIrrc(ClassLoader classLoader) {
        try {
            Class<?> irrcPlayer = findClass("com.lge.irrc.IRRCPlayer", classLoader);
            Class<?> charArrayClass = Class.forName("[C");

            findAndHookMethod(irrcPlayer, "setIRRCInfos", int.class, charArrayClass, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    int dataType = (Integer) Array.get(param.args, 0);
                    char[] buf = (char[]) Array.get(param.args, 1);
                    int len = (Integer) Array.get(param.args, 2);

                    if (dataType == 1 && buf != null && len > 0) {
                        sLastMakeData = buf.clone();
                        sLastMakeLen = len;
                    } else if (dataType == 2 && len == 0 && sLastMakeData != null && sLastMakeLen > 0) {
                        Array.set(param.args, 1, sLastMakeData.clone());
                        Array.set(param.args, 2, sLastMakeLen);
                        Log.i(TAG, "Injected REPEAT_DATA (len=" + sLastMakeLen + ") cleanly!");
                    }
                }
            });
            log(TAG + ": Hooked IRRCPlayer.setIRRCInfos(int, char[], int) cleanly!");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook setIRRCInfos: " + t.getMessage());
        }
    }
}
