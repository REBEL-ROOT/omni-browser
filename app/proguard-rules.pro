# -------------------------------------------------------------
# Omni Browser Proguard/R8 Size & Optimization Rules
# -------------------------------------------------------------

# 1. GeckoView Engine JNI Keep Rules
-keep class org.mozilla.geckoview.** { *; }
-keep class org.mozilla.gecko.SysInfo { *; }
-keep class org.mozilla.gecko.mozglue.JNIObject { *; }
-keep class * extends org.mozilla.gecko.mozglue.JNIObject { *; }
-keep @interface org.mozilla.gecko.annotation.JNITarget
-keep @org.mozilla.gecko.annotation.JNITarget class *
-keepclassmembers @org.mozilla.gecko.annotation.JNITarget class * { *; }
-keepclassmembers class * { @org.mozilla.gecko.annotation.JNITarget *; }
-dontwarn org.mozilla.geckoview.**
-dontwarn mozilla.components.**
-dontnote org.mozilla.**

# 2. SQLCipher & Room Database
-keep class net.zetetic.database.sqlcipher.** { *; }
-keep class net.sqlcipher.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn net.zetetic.database.sqlcipher.**
-dontwarn net.sqlcipher.**

# 3. WireGuard VPN SDK
-keep class com.wireguard.android.backend.Tunnel { *; }
-keep class com.wireguard.android.backend.Tunnel$* { *; }
-dontwarn com.wireguard.android.**

# 4. ML Kit Document Scanner & Translation SDK
# Relying on consumer ProGuard rules packaged inside ML Kit AARs
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.internal.mlkit_**

# 5. Coil Image Loader
# Coil is pure Kotlin and uses consumer rules for reflection points
-dontwarn coil.**

# 6. Aggressive Optimization: Strip ALL Logging Statements
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

# 7. Strip Kotlin metadata/debug info in release
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullParameter(java.lang.Object, java.lang.String);
    static void checkExpressionValueIsNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullExpressionValue(java.lang.Object, java.lang.String);
    static void checkFieldIsNotNull(java.lang.Object, java.lang.String);
    static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.Object, java.lang.String);
}

# 8. General Android Shrinking Settings
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animalsniffer.IgnoreJRERequirement
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# 9. Remove unused R class fields (resource shrinking helper)
-keepclassmembers class **.R$* {
    public static <fields>;
}

# 10. Optimize with R8 full mode (enabled via gradle.properties)
# These rules help R8 be more aggressive:
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
