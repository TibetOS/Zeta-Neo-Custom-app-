package com.syu.ipc;

/**
 * Callback interface of the FYT (com.syu.ms) toolkit service.
 * Signatures match the platform's own AIDL (method order defines binder
 * transaction codes and must not be changed).
 */
interface IModuleCallback {
    void update(int updateCode, in int[] ints, in float[] flts, in String[] strs);
}
