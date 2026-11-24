package at.zweng.xposed.lepro3infrared.quicksetservice;

import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

/**
 * Client wrapper for LG QuickSet SDK IControl binder interface (v0.1.75)
 */
public class IControl {

    public static final String QUICKSET_UEI_PACKAGE_NAME = "com.uei.lg.quicksetsdk.lite";
    public static final String QUICKSET_UEI_SERVICE_CLASS = "com.uei.control.Service";
    public static final String ACTION = "com.uei.control.IControl";

    private static final String DESCRIPTOR = "com.uei.control.IControl";
    private static final int TRANSACTION_getLastResultcode = 13;
    private static final int TRANSACTION_transmit = 16;

    private final IBinder mRemote;

    public IControl(IBinder remote) {
        this.mRemote = remote;
    }

    public int transmit(int carrierFrequency, int[] pattern) throws RemoteException {
        Parcel _data = Parcel.obtain();
        Parcel _reply = Parcel.obtain();
        int _result;
        try {
            _data.writeInterfaceToken(DESCRIPTOR);
            _data.writeInt(carrierFrequency);
            _data.writeIntArray(pattern);
            mRemote.transact(TRANSACTION_transmit, _data, _reply, 0);
            _reply.readException();
            _result = _reply.readInt();
        } finally {
            _reply.recycle();
            _data.recycle();
        }
        return _result;
    }

    public int getLastResultcode() throws RemoteException {
        Parcel _data = Parcel.obtain();
        Parcel _reply = Parcel.obtain();
        int _result;
        try {
            _data.writeInterfaceToken(DESCRIPTOR);
            mRemote.transact(TRANSACTION_getLastResultcode, _data, _reply, 0);
            _reply.readException();
            _result = _reply.readInt();
        } finally {
            _reply.recycle();
            _data.recycle();
        }
        return _result;
    }
}
