# ProGuard rules for Omni Browser

# 1. GeckoView (Firefox engine)
-keep class org.mozilla.geckoview.** { *; }
-keep class org.mozilla.gecko.SysInfo { *; }
-keep class org.mozilla.gecko.mozglue.JNIObject { *; }
-keep class * extends org.mozilla.gecko.mozglue.JNIObject { *; }
-keep @interface org.mozilla.gecko.annotation.JNITarget
-keep @org.mozilla.gecko.annotation.JNITarget class *
-keepclassmembers @org.mozilla.gecko.annotation.JNITarget class * { *; }
-keepclassmembers class * { @org.mozilla.gecko.annotation.JNITarget *; }
-dontwarn org.mozilla.**
-dontnote  org.mozilla.**

# 2. SQLCipher + Room
-keep class net.zetetic.database.sqlcipher.** { *; }
-keep class net.sqlcipher.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn net.zetetic.**
-dontwarn net.sqlcipher.**

# 3. WireGuard VPN
-keep class com.wireguard.android.backend.Tunnel { *; }
-keep class com.wireguard.android.backend.Tunnel$* { *; }
-dontwarn com.wireguard.**

# 4. ML Kit warnings suppression
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.internal.mlkit_**

# 5. Coil
-dontwarn coil.**

# 6. Strip android.util.Log calls in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

# 7. Kotlin null-check optimization
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullParameter(java.lang.Object, java.lang.String);
    static void checkExpressionValueIsNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullExpressionValue(java.lang.Object, java.lang.String);
    static void checkFieldIsNotNull(java.lang.Object, java.lang.String, java.lang.String);
    static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.Object, java.lang.String);
}
-dontwarn kotlin.coroutines.jvm.internal.SpillingKt

# 8. R8 optimization passes
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''

# 9. Resource shrinking rules
-keepclassmembers class **.R$* {
    public static <fields>;
}

# 10. Third party warnings suppression
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.codehaus.mojo.animalsniffer.IgnoreJRERequirement
