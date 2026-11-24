package at.zweng.xposed.lepro3infrared;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import at.zweng.xposed.lepro3infrared.quicksetservice.IControl;

import static at.zweng.xposed.lepro3infrared.XposedMain.TAG;
import static de.robv.android.xposed.XposedBridge.log;

/**
 * Perform the actual infrared magic in here...
 */
public class InfraredMagic {

    private IControl mControl;
    private Context mContext;
    private boolean mBound = false;

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

    public int transmitIrPattern(int carrierFrequency, int[] pattern) {
        if (mControl == null || !mBound) {
            Log.w(TAG, "QuickSet Service (for using IR Blaster) seems not to be bound. Trying to bind again and exit.");
            bindQuickSetService();
            return 999;
        }
        try {
            int[] pulses = new int[pattern.length];
            double factor = carrierFrequency / 1000000.0;
            for (int i = 0; i < pattern.length; i++) {
                pulses[i] = Math.max(1, (int) Math.round(pattern[i] * factor));
            }
            mControl.transmit(carrierFrequency, pulses);
            int resultCode = mControl.getLastResultcode();
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
