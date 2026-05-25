-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE
-dontwarn java.beans.Introspector
-dontwarn java.beans.VetoableChangeListener
-dontwarn java.beans.VetoableChangeSupport
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.PropertyDescriptor

# Keep ini4j Service Provider Interface
-keep,allowobfuscation,allowoptimization class org.ini4j.spi.** { *; }

# Keep native methods and JNI classes
-keep class me.yuki.foly.Natives {
    *;
}

-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class me.yuki.foly.Natives$Profile { *; }
-keep class me.yuki.foly.Natives$KPMCtlRes { *; }

# Keep RootServices
-keep class me.yuki.foly.services.RootServices { *; }

# Keep AIDL interfaces
-keep class me.yuki.foly.IAPRootService { *; }
-keep class me.yuki.foly.IAPRootService$Stub { *; }
-keep class rikka.parcelablelist.ParcelableListSlice { *; }
# Keep ScriptInfo for Gson serialization in release
-keep class me.yuki.foly.data.ScriptInfo { *; }
-keepclassmembers class me.yuki.foly.data.ScriptInfo { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Kotlin
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void check*(...);
    public static void throw*(...);
}

# Keep Umount configuration classes
-keep class me.yuki.foly.ui.component.UmountConfig { *; }
-keep class me.yuki.foly.ui.component.UmountConfigManager { *; }
-keep class me.yuki.foly.ui.screen.UmountConfigScreen { *; }

-keepclassmembers class me.yuki.foly.ui.component.UmountConfigManager {
    public static *;
}

-keepclassmembers class me.yuki.foly.ui.component.UmountConfig {
    public <init>(boolean, java.lang.String);
}

# Keep Umount destination
-keep class me.yuki.foly.ui.screen.destinations.UmountConfigScreenDestination { *; }

-repackageclasses
-allowaccessmodification
-overloadaggressively
-renamesourcefileattribute SourceFile
