package p011c.p015b.p016a.p017a.p018a;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;

/* compiled from: ITWCommandAidl.java */
/* renamed from: c.b.a.a.a.d */
/* loaded from: classes4.dex */
public interface InterfaceC0516d extends IInterface {
    /* renamed from: W */
    void mo141W(int i);

    /* renamed from: a */
    void mo142a(Bundle bundle);

    /* renamed from: a */
    void mo143a(InterfaceC0513a interfaceC0513a);

    /* renamed from: a */
    void mo144a(InterfaceC0514b interfaceC0514b);

    /* renamed from: a */
    void mo145a(InterfaceC0515c interfaceC0515c);

    /* renamed from: a */
    void mo146a(InterfaceC0517e interfaceC0517e);

    /* renamed from: a */
    void mo147a(InterfaceC0518f interfaceC0518f);

    /* renamed from: b */
    void mo148b(InterfaceC0513a interfaceC0513a);

    /* renamed from: b */
    void mo149b(InterfaceC0514b interfaceC0514b);

    /* renamed from: b */
    void mo150b(InterfaceC0515c interfaceC0515c);

    /* renamed from: b */
    void mo151b(InterfaceC0517e interfaceC0517e);

    /* renamed from: b */
    void mo152b(InterfaceC0518f interfaceC0518f);

    /* renamed from: d */
    void mo153d(int i, String str, String str2);

    /* renamed from: m */
    void mo154m(int i);

    /* compiled from: ITWCommandAidl.java */
    /* renamed from: c.b.a.a.a.d$a */
    public static abstract class a extends Binder implements InterfaceC0516d {
        public static InterfaceC0516d asInterface(IBinder iBinder) {
            if (iBinder == null) {
                return null;
            }
            IInterface queryLocalInterface = iBinder.queryLocalInterface("com.tw.service.xt.aidl.ITWCommandAidl");
            return (queryLocalInterface == null || !(queryLocalInterface instanceof InterfaceC0516d)) ? new C0864a(iBinder) : (InterfaceC0516d) queryLocalInterface;
        }

        public static InterfaceC0516d getDefaultImpl() {
            return C0864a.sDefaultImpl;
        }

        /* compiled from: ITWCommandAidl.java */
        /* renamed from: c.b.a.a.a.d$a$a, reason: collision with other inner class name */
        private static class C0864a implements InterfaceC0516d {
            public static InterfaceC0516d sDefaultImpl;
            private IBinder mRemote;

            C0864a(IBinder iBinder) {
                this.mRemote = iBinder;
            }

            @Override // p011c.p015b.p016a.p017a.p018a.InterfaceC0516d
            /* renamed from: W */
            public void mo141W(int i) {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken("com.tw.service.xt.aidl.ITWCommandAidl");
                    obtain.writeInt(i);
                    if (this.mRemote.transact(27, obtain, obtain2, 0) || a.getDefaultImpl() == null) {
                        obtain2.readException();
                    } else {
                        a.getDefaultImpl().mo141W(i);
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // p011c.p015b.p016a.p017a.p018a.InterfaceC0516d
            /* renamed from: a */
            public void mo146a(InterfaceC0517e interfaceC0517e) {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken("com.tw.service.xt.aidl.ITWCommandAidl");
                    obtain.writeStrongBinder(interfaceC0517e != null ? interfaceC0517e.asBinder() : null);
                    if (this.mRemote.transact(2, obtain, obtain2, 0) || a.getDefaultImpl() == null) {
                        obtain2.readException();
                    } else {
                        a.getDefaultImpl().mo146a(interfaceC0517e);
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // android.os.IInterface
            public IBinder asBinder() {
                return this.mRemote;
            }

            @Override // p011c.p015b.p016a.p017a.p018a.InterfaceC0516d
            /* renamed from: b */
            public void mo151b(InterfaceC0517e interfaceC0517e) {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken("com.tw.service.xt.aidl.ITWCommandAidl");
                    obtain.writeStrongBinder(interfaceC0517e != null ? interfaceC0517e.asBinder() : null);
                    if (this.mRemote.transact(1, obtain, obtain2, 0) || a.getDefaultImpl() == null) {
                        obtain2.readException();
                    } else {
                        a.getDefaultImpl().mo151b(interfaceC0517e);
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // p011c.p015b.p016a.p017a.p018a.InterfaceC0516d
            /* renamed from: d */
            public void mo153d(int i, String str, String str2) {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken("com.tw.service.xt.aidl.ITWCommandAidl");
                    obtain.writeInt(i);
                    obtain.writeString(str);
                    obtain.writeString(str2);
                    if (this.mRemote.transact(30, obtain, obtain2, 0) || a.getDefaultImpl() == null) {
                        obtain2.readException();
                    } else {
                        a.getDefaultImpl().mo153d(i, str, str2);
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // p011c.p015b.p016a.p017a.p018a.InterfaceC0516d
            /* renamed from: m */
            public void mo154m(int i) {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken("com.tw.service.xt.aidl.ITWCommandAidl");
                    obtain.writeInt(i);
                    if (this.mRemote.transact(29, obtain, obtain2, 0) || a.getDefaultImpl() == null) {
                        obtain2.readException();
                    } else {
                        a.getDefaultImpl().mo154m(i);
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // p011c.p015b.p016a.p017a.p018a.InterfaceC0516d
            /* renamed from: a */
            public void mo145a(InterfaceC0515c interfaceC0515c) {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken("com.tw.service.xt.aidl.ITWCommandAidl");
                    obtain.writeStrongBinder(interfaceC0515c != null ? interfaceC0515c.asBinder() : null);
                    if (!this.mRemote.transact(4, obtain, obtain2, 0) && a.getDefaultImpl() != null) {
                        a.getDefaultImpl().mo145a(interfaceC0515c);
                    } else {
                        obtain2.readException();
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // p011c.p015b.p016a.p017a.p018a.InterfaceC0516d
            /* renamed from: b */
            public void mo150b(InterfaceC0515c interfaceC0515c) {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken("com.tw.service.xt.aidl.ITWCommandAidl");
                    obtain.writeStrongBinder(interfaceC0515c != null ? interfaceC0515c.asBinder() : null);
                    if (!this.mRemote.transact(3, obtain, obtain2, 0) && a.getDefaultImpl() != null) {
                        a.getDefaultImpl().mo150b(interfaceC0515c);
                    } else {
                        obtain2.readException();
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // p011c.p015b.p016a.p017a.p018a.InterfaceC0516d
            /* renamed from: a */
            public void mo144a(InterfaceC0514b interfaceC0514b) {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken("com.tw.service.xt.aidl.ITWCommandAidl");
                    obtain.writeStrongBinder(interfaceC0514b != null ? interfaceC0514b.asBinder() : null);
                    if (!this.mRemote.transact(5, obtain, obtain2, 0) && a.getDefaultImpl() != null) {
                        a.getDefaultImpl().mo144a(interfaceC0514b);
                    } else {
                        obtain2.readException();
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // p011c.p015b.p016a.p017a.p018a.InterfaceC0516d
            /* renamed from: b */
            public void mo149b(InterfaceC0514b interfaceC0514b) {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken("com.tw.service.xt.aidl.ITWCommandAidl");
                    obtain.writeStrongBinder(interfaceC0514b != null ? interfaceC0514b.asBinder() : null);
                    if (!this.mRemote.transact(6, obtain, obtain2, 0) && a.getDefaultImpl() != null) {
                        a.getDefaultImpl().mo149b(interfaceC0514b);
                    } else {
                        obtain2.readException();
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // p011c.p015b.p016a.p017a.p018a.InterfaceC0516d
            /* renamed from: a */
            public void mo147a(InterfaceC0518f interfaceC0518f) {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken("com.tw.service.xt.aidl.ITWCommandAidl");
                    obtain.writeStrongBinder(interfaceC0518f != null ? interfaceC0518f.asBinder() : null);
                    if (!this.mRemote.transact(8, obtain, obtain2, 0) && a.getDefaultImpl() != null) {
                        a.getDefaultImpl().mo147a(interfaceC0518f);
                    } else {
                        obtain2.readException();
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // p011c.p015b.p016a.p017a.p018a.InterfaceC0516d
            /* renamed from: b */
            public void mo152b(InterfaceC0518f interfaceC0518f) {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken("com.tw.service.xt.aidl.ITWCommandAidl");
                    obtain.writeStrongBinder(interfaceC0518f != null ? interfaceC0518f.asBinder() : null);
                    if (!this.mRemote.transact(7, obtain, obtain2, 0) && a.getDefaultImpl() != null) {
                        a.getDefaultImpl().mo152b(interfaceC0518f);
                    } else {
                        obtain2.readException();
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // p011c.p015b.p016a.p017a.p018a.InterfaceC0516d
            /* renamed from: a */
            public void mo143a(InterfaceC0513a interfaceC0513a) {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken("com.tw.service.xt.aidl.ITWCommandAidl");
                    obtain.writeStrongBinder(interfaceC0513a != null ? interfaceC0513a.asBinder() : null);
                    if (!this.mRemote.transact(10, obtain, obtain2, 0) && a.getDefaultImpl() != null) {
                        a.getDefaultImpl().mo143a(interfaceC0513a);
                    } else {
                        obtain2.readException();
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // p011c.p015b.p016a.p017a.p018a.InterfaceC0516d
            /* renamed from: b */
            public void mo148b(InterfaceC0513a interfaceC0513a) {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken("com.tw.service.xt.aidl.ITWCommandAidl");
                    obtain.writeStrongBinder(interfaceC0513a != null ? interfaceC0513a.asBinder() : null);
                    if (!this.mRemote.transact(9, obtain, obtain2, 0) && a.getDefaultImpl() != null) {
                        a.getDefaultImpl().mo148b(interfaceC0513a);
                    } else {
                        obtain2.readException();
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override // p011c.p015b.p016a.p017a.p018a.InterfaceC0516d
            /* renamed from: a */
            public void mo142a(Bundle bundle) {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken("com.tw.service.xt.aidl.ITWCommandAidl");
                    if (bundle != null) {
                        obtain.writeInt(1);
                        bundle.writeToParcel(obtain, 0);
                    } else {
                        obtain.writeInt(0);
                    }
                    if (!this.mRemote.transact(67, obtain, obtain2, 0) && a.getDefaultImpl() != null) {
                        a.getDefaultImpl().mo142a(bundle);
                    } else {
                        obtain2.readException();
                    }
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }
        }
    }
}
