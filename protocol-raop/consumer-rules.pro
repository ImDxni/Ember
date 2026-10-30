# Natives are bound by name with RegisterNatives in JNI_OnLoad.
-keep class **.protocol.raop.NativeBridge { native <methods>; }
