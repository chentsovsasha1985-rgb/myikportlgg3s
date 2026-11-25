package at.zweng.xposed.lepro3infrared;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Arrays;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
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
        if ("com.uei.lg.quicksetsdk.lite".equals(lpparam.packageName)) {
            inspectAndHookIrrc(lpparam.classLoader);
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

    private void inspectAndHookIrrc(ClassLoader classLoader) {
        try {
            Class<?> irrcPlayer = findClass("com.lge.irrc.IRRCPlayer", classLoader);
            for (Method m : irrcPlayer.getDeclaredMethods()) {
                Log.i(TAG, "IRRCPlayer method: " + m.getName() + " args=" + Arrays.toString(m.getParameterTypes()));
                if ("setIRRCInfos".equals(m.getName())) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            StringBuilder sb = new StringBuilder("setIRRCInfos called with args: ");
                            if (param.args != null) {
                                int count = Array.getLength(param.args);
                                for (int i = 0; i < count; i++) {
                                    Object a = Array.get(param.args, i);
                                    if (a != null && a.getClass().isArray()) {
                                        sb.append("arr(len=").append(Array.getLength(a)).append(") ");
                                    } else {
                                        sb.append(a).append(" ");
                                    }
                                }
                            }
                            Log.i(TAG, sb.toString());
                        }
                    });
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "inspectAndHookIrrc error: " + t.getMessage());
        }
    }
}
