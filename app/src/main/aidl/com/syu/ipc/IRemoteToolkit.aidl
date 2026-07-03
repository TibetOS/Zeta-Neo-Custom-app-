package com.syu.ipc;

import com.syu.ipc.IRemoteModule;

/**
 * Root interface of the FYT (com.syu.ms) toolkit service.
 */
interface IRemoteToolkit {
    IRemoteModule getRemoteModule(int moduleCode);
}
