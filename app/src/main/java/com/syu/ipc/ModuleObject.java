package com.syu.ipc;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Return value of {@link IRemoteModule#get}. Wire layout must match the
 * platform's own class in {@code com.syu.ms}: the fields are marshalled in the
 * order ints, flts, strs (verified against the decompiled toolkit — see
 * docs/CAN-INTEGRATION.md). Do not reorder.
 */
public final class ModuleObject implements Parcelable {
    public int[] ints;
    public float[] flts;
    public String[] strs;

    public ModuleObject() {
    }

    protected ModuleObject(Parcel in) {
        ints = in.createIntArray();
        flts = in.createFloatArray();
        strs = in.createStringArray();
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeIntArray(ints);
        out.writeFloatArray(flts);
        out.writeStringArray(strs);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ModuleObject> CREATOR = new Creator<ModuleObject>() {
        @Override
        public ModuleObject createFromParcel(Parcel in) {
            return new ModuleObject(in);
        }

        @Override
        public ModuleObject[] newArray(int size) {
            return new ModuleObject[size];
        }
    };
}
