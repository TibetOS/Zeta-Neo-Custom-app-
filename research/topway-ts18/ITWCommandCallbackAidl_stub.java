package p011c.p015b.p016a.p017a.p018a;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;

/* compiled from: ITWCommandCallbackAidl.java */
/* renamed from: c.b.a.a.a.e */
/* loaded from: classes4.dex */
public interface InterfaceC0517e extends IInterface {

    /* compiled from: ITWCommandCallbackAidl.java */
    /* renamed from: c.b.a.a.a.e$a */
    public static abstract class a extends Binder implements InterfaceC0517e {
        public a() {
            attachInterface(this, "com.tw.service.xt.aidl.ITWCommandCallbackAidl");
        }

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return this;
        }

        @Override // android.os.Binder
        public boolean onTransact(int i, Parcel parcel, Parcel parcel2, int i2) {
            if (i == 1598968902) {
                parcel2.writeString("com.tw.service.xt.aidl.ITWCommandCallbackAidl");
                return true;
            }
            switch (i) {
                case 1:
                    parcel.enforceInterface("com.tw.service.xt.aidl.ITWCommandCallbackAidl");
                    mo156R(parcel.readInt());
                    parcel2.writeNoException();
                    return true;
                case 2:
                    parcel.enforceInterface("com.tw.service.xt.aidl.ITWCommandCallbackAidl");
                    mo157V(parcel.readInt());
                    parcel2.writeNoException();
                    return true;
                case 3:
                    parcel.enforceInterface("com.tw.service.xt.aidl.ITWCommandCallbackAidl");
                    mo155O(parcel.readInt());
                    parcel2.writeNoException();
                    return true;
                case 4:
                    parcel.enforceInterface("com.tw.service.xt.aidl.ITWCommandCallbackAidl");
                    mo161c(parcel.readInt(), parcel.readString(), parcel.readString());
                    parcel2.writeNoException();
                    return true;
                case 5:
                    parcel.enforceInterface("com.tw.service.xt.aidl.ITWCommandCallbackAidl");
                    mo158X(parcel.readInt());
                    parcel2.writeNoException();
                    return true;
                case 6:
                    parcel.enforceInterface("com.tw.service.xt.aidl.ITWCommandCallbackAidl");
                    mo159Z(parcel.readInt());
                    parcel2.writeNoException();
                    return true;
                case 7:
                    parcel.enforceInterface("com.tw.service.xt.aidl.ITWCommandCallbackAidl");
                    mo162k(parcel.readInt());
                    parcel2.writeNoException();
                    return true;
                case 8:
                    parcel.enforceInterface("com.tw.service.xt.aidl.ITWCommandCallbackAidl");
                    mo160b(parcel.readInt() != 0 ? (Bundle) Bundle.CREATOR.createFromParcel(parcel) : null);
                    parcel2.writeNoException();
                    return true;
                default:
                    return super.onTransact(i, parcel, parcel2, i2);
            }
        }
    }

    /* renamed from: O */
    void mo155O(int i);

    /* renamed from: R */
    void mo156R(int i);

    /* renamed from: V */
    void mo157V(int i);

    /* renamed from: X */
    void mo158X(int i);

    /* renamed from: Z */
    void mo159Z(int i);

    /* renamed from: b */
    void mo160b(Bundle bundle);

    /* renamed from: c */
    void mo161c(int i, String str, String str2);

    /* renamed from: k */
    void mo162k(int i);
}
