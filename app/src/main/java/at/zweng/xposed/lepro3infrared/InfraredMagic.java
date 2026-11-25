package at.zweng.xposed.lepro3infrared;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import java.lang.reflect.Array;
import java.lang.reflect.Method;

import at.zweng.xposed.lepro3infrared.quicksetservice.IControl;
import dalvik.system.DexClassLoader;

import static at.zweng.xposed.lepro3infrared.XposedMain.TAG;
import static de.robv.android.xposed.XposedBridge.log;

public class InfraredMagic {

    private IControl mControl;
    private Context mContext;
    private boolean mBound = false;

    private boolean mIrrcInited = false;
    private Method mSetIrrcInfos;
    private Method mStartIrrcProcess;
    private Method mStopIrrcProcess;
    private Class<?> mInfosParamType;

    private final ServiceConnection mControlServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBound = true;
            mControl = new IControl(service);
            Log.i(TAG, "QuickSet SDK Service (for controlling IR Blaster) SUCCESSFULLY CONNECTED! Yeah! :-)");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
            mControl = null;
            Log.i(TAG, "QuickSet SDK Service (for controlling IR Blaster) DISCONNECTED!");
        }
    };

    private void ensureNativeIrrc() {
        if (mIrrcInited || mContext == null) {
            return;
        }
        try {
            String apkPath = mContext.getPackageManager()
                    .getApplicationInfo(IControl.QUICKSET_UEI_PACKAGE_NAME, 0).sourceDir;
            DexClassLoader loader = new DexClassLoader(
                    apkPath,
                    "/data/local/tmp",
                    "/system/lib",
                    ClassLoader.getSystemClassLoader()
            );
            Class<?> playerCls = loader.loadClass("com.lge.irrc.IRRCPlayer");
            for (Method m : playerCls.getDeclaredMethods()) {
                m.setAccessible(true);
                String name = m.getName();
                Class<?>[] params = m.getParameterTypes();
                if ("nativeInit".equals(name) && params.length == 0) {
                    m.invoke(null);
                } else if ("setIRRCInfos".equals(name) && params.length == 2) {
                    mSetIrrcInfos = m;
                    mInfosParamType = (Class<?>) Array.get(params, 1);
                } else if ("setStartIRRCProcess".equals(name)) {
                    mStartIrrcProcess = m;
                } else if ("setStopIRRCProcess".equals(name)) {
                    mStopIrrcProcess = m;
                }
            }
            mIrrcInited = true;
            Log.i(TAG, "Loaded native LG IRRCPlayer successfully! paramType=" + mInfosParamType);
        } catch (Throwable t) {
            Log.w(TAG, "Could not load native IRRCPlayer: " + t.getMessage());
        }
    }

    private void boostRepeatPulse(int carrierFrequency, int[] pattern) {
        ensureNativeIrrc();
        if (mSetIrrcInfos == null || mStartIrrcProcess == null || mStopIrrcProcess == null) {
            return;
        }
        try {
            int len = Math.min(Array.getLength(pattern), 128);
            Object dataArg;
            if (mInfosParamType == char[].class) {
                Object buf = Array.newInstance(char.class, len + 4);
                Array.setChar(buf, 0, (char) (carrierFrequency & 0xFFFF));
                Array.setChar(buf, 1, (char) ((carrierFrequency >> 16) & 0xFFFF));
                Array.setChar(buf, 2, (char) len);
                for (int i = 0; i < len; i++) {
                    int val = Array.getInt(pattern, i);
                    Array.setChar(buf, i + 3, (char) Math.min(65535, val));
                }
                dataArg = buf;
            } else {
                Object buf = Array.newInstance(byte.class, len * 2 + 4);
                Array.setByte(buf, 0, (byte) (carrierFrequency & 0xFF));
                Array.setByte(buf, 1, (byte) ((carrierFrequency >> 8) & 0xFF));
                Array.setByte(buf, 2, (byte) (len & 0xFF));
                for (int i = 0; i < len; i++) {
                    int val = Math.min(65535, Array.getInt(pattern, i));
                    Array.setByte(buf, 4 + i * 2, (byte) (val & 0xFF));
                    Array.setByte(buf, 4 + i * 2 + 1, (byte) ((val >> 8) & 0xFF));
                }
                dataArg = buf;
            }

            mSetIrrcInfos.invoke(null, 2, dataArg);
            if (mStartIrrcProcess.getParameterTypes().length == 1) {
                mStartIrrcProcess.invoke(null, 0);
            } else {
                mStartIrrcProcess.invoke(null);
            }
            Thread.sleep(320);
            if (mStopIrrcProcess.getParameterTypes().length == 1) {
                mStopIrrcProcess.invoke(null, 0);
            } else {
                mStopIrrcProcess.invoke(null);
            }
        } catch (Throwable t) {
            Log.w(TAG, "boostRepeatPulse error: " + t.getMessage());
        }
    }

    public int transmitIrPattern(int carrierFrequency, int[] pattern) {
        if (mControl == null || !mBound) {
            Log.w(TAG, "QuickSet Service (for using IR Blaster) seems not to be bound. Trying to bind again and exit.");
            bindQuickSetService();
            return 999;
        }
        try {
            mControl.transmit(carrierFrequency, pattern);
            int resultCode = mControl.getLastResultcode();
            boostRepeatPulse(carrierFrequency, pattern);
            if (resultCode != 0) {
                Log.w(TAG, "resultCode after calling transmit on QuickSet SDK was != 0. lastResultcode: " + resultCode);
            }
            return resultCode;
        } catch (Throwable t) {
            Log.e(TAG, "Exception while trying to send command to QuickSet Service. :-(", t);
            return 999;
        }
    }

    public void bindQuickSetService() {
        Log.d(TAG, "Trying to bind QuickSet service (for controlling IR Blaster): " + IControl.QUICKSET_UEI_PACKAGE_NAME + " - " + IControl.QUICKSET_UEI_SERVICE_CLASS);
        if (mContext == null) {
            Log.w(TAG, "Cannot bind QuickSet control service (now), as context is null. :-(");
            return;
        }
        try {
            Intent controlIntent = new Intent(IControl.ACTION);
            controlIntent.setClassName(IControl.QUICKSET_UEI_PACKAGE_NAME, IControl.QUICKSET_UEI_SERVICE_CLASS);
            boolean bindResult = mContext.bindService(controlIntent, mControlServiceConnection, Context.BIND_AUTO_CREATE);
            if (!bindResult) {
                Log.e(TAG, "bindResult == false. QuickSet SDK service seems NOT TO BE AVAILABLE ON THIS PHONE! IR Blaster will probably NOT WORK!");
                log(TAG + ": QuickSet SDK service seems NOT TO BE AVAILABLE ON THIS PHONE! IR Blaster will probably NOT WORK!");
            } else {
                Log.d(TAG, "bindService() result: true");
            }
        } catch (Throwable t) {
            Log.e(TAG, "Binding QuickSet Control service failed with exception :-(", t);
        }
    }

    public void setContext(Context ctx) {
        this.mContext = ctx;
    }
}
