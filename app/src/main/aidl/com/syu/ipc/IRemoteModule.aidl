package com.syu.ipc;

import com.syu.ipc.IModuleCallback;
import com.syu.ipc.ModuleObject;

/**
 * One functional module (canbus, main MCU, sound, ...) of the FYT toolkit
 * service. Method order defines binder transaction codes — do not reorder.
 *
 * NOTE: this is the minimal community-reproduced subset of the proprietary
 * interface. If calls misbehave on a specific firmware, pull /system app
 * com.syu.ms from the unit and verify the transaction order against its
 * decompiled Stub (see docs/CAN-INTEGRATION.md).
 */
interface IRemoteModule {
    void cmd(int cmdCode, in int[] ints, in float[] flts, in String[] strs);
    ModuleObject get(int getCode, in int[] ints, in float[] flts, in String[] strs);
    void register(IModuleCallback callback, int updateCode, int flag);
    void unregister(IModuleCallback callback, int updateCode);
}
