# Keep the FYT/CAN integration surface: the AIDL binder stubs must keep their
# exact shape for IPC with the proprietary com.syu.ms service.
-keep class com.traffko.outlanderhub.vehicle.** { *; }
-keep class com.syu.ipc.** { *; }
-keep interface com.syu.ipc.** { *; }
