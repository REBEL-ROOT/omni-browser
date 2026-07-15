# ProGuard rules for Omni Browser

# 0. CRITICAL: Preserve GeckoRuntime initialization.
# R8 strips getGeckoRuntime() calls as "dead code" because the return value is
# often discarded and the geckoRuntime field write is not observable from the
# call site. This left GeckoView uninitialized and caused the black-screen bug
# in signed release builds (worked fine on emulator/debug where R8 is off).
# @Keep is on the method; these rules are a belt-and-suspenders safeguard.
-keepclassmembers class com.rebelroot.omni.browser.BrowserViewModel {
    @androidx.annotation.Keep public *** getGeckoRuntime(...);
    private volatile *** geckoRuntime;
}

# 1. GeckoView (Firefox engine) — KEEP ALL; heavy reflection + JNI usage
-keep class org.mozilla.geckoview.** { *; }
-keep class org.mozilla.gecko.** { *; }
-keep class org.mozilla.gecko.SysInfo { *; }
-keep class org.mozilla.gecko.mozglue.JNIObject { *; }
-keep class * extends org.mozilla.gecko.mozglue.JNIObject { *; }
-keep @interface org.mozilla.gecko.annotation.JNITarget
-keep @org.mozilla.gecko.annotation.JNITarget class *
-keepclassmembers @org.mozilla.gecko.annotation.JNITarget class * { *; }
-keepclassmembers class * { @org.mozilla.gecko.annotation.JNITarget *; }
-keep class org.mozilla.geckoview.GeckoResult { *; }
-keep class org.mozilla.geckoview.GeckoResult$* { *; }
-keep class org.mozilla.geckoview.WebExtension$MessageDelegate { *; }
-keep class org.mozilla.geckoview.WebExtension$MessageSender { *; }
-keep class org.mozilla.geckoview.WebExtension$ActionDelegate { *; }
-keep class org.mozilla.geckoview.WebExtension$PortDelegate { *; }
-keep class org.mozilla.geckoview.WebExtension$SessionController { *; }
-keep class org.mozilla.geckoview.WebExtensionController$PromptDelegate { *; }
-keep class org.mozilla.geckoview.ContentBlocking$Settings { *; }
-keep class org.mozilla.geckoview.GeckoRuntimeSettings { *; }
-keep class org.mozilla.geckoview.GeckoRuntimeSettings$Builder { *; }
-keep class org.mozilla.geckoview.GeckoSession$Settings { *; }
-keep class org.mozilla.geckoview.GeckoSession$ProgressDelegate { *; }
-keep class org.mozilla.geckoview.GeckoSession$NavigationDelegate { *; }
-keep class org.mozilla.geckoview.GeckoSession$ContentDelegate { *; }
-keep class org.mozilla.geckoview.GeckoSession$PermissionDelegate { *; }
-keep class org.mozilla.geckoview.GeckoSession$PromptDelegate { *; }
-keep class org.mozilla.geckoview.GeckoSession$MediaDelegate { *; }
-keep class org.mozilla.geckoview.GeckoSession$SelectionActionDelegate { *; }
-keep class org.mozilla.geckoview.GeckoSession$HistoryDelegate { *; }
-keep class org.mozilla.geckoview.Autocomplete$StorageDelegate { *; }
-keep class org.mozilla.geckoview.Autocomplete$LoginEntry { *; }
-keep class org.mozilla.geckoview.WebExtension$PermissionPromptResponse { *; }
# Keep all native method bindings (JNI) — R8 strips these by default, breaking GeckoView
-keepclasseswithmembernames class * { native <methods>; }
# Keep any subclasses of GeckoView (e.g. anonymous classes created in Compose AndroidView)
-keep class * extends org.mozilla.geckoview.GeckoView { *; }
-keepclassmembers class org.mozilla.geckoview.GeckoView { *; }
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

# 7. Kotlin null-check optimization — KEEP CRITICAL checks
# Only strip the non-crashing checks; keep parameter and field null guards
# to prevent NPE crashes when GeckoView returns null unexpectedly
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkExpressionValueIsNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullExpressionValue(java.lang.Object, java.lang.String);
}
# These MUST be kept to prevent NPE crashes in release builds
-keepclassmembers class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullParameter(java.lang.Object, java.lang.String);
    static void checkFieldIsNotNull(java.lang.Object, java.lang.String, java.lang.String);
    static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.Object, java.lang.String);
}
-dontwarn kotlin.coroutines.jvm.internal.SpillingKt

# 8. R8 optimization passes — toned down for stability
# Removed -allowaccessmodification (breaks GeckoView reflection internals)
# Removed -repackageclasses '' (breaks GeckoView classpath assumptions)
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 3

# 9. Compose — keep internal classes loaded via reflection
-keep class androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }
-keepclassmembers class androidx.compose.ui.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.animation.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.material.** { *; }
-dontwarn androidx.compose.**

# 9b. AndroidX Activity Compose — used by CompositionLocalProvider in MainActivity
-keep class androidx.activity.compose.** { *; }
-keepclassmembers class androidx.activity.compose.** { *; }
-dontwarn androidx.activity.compose.**

# 9c. AndroidX Core / Lifecycle / Fragment — used by FragmentActivity, ViewModel, etc.
-keep class androidx.core.** { *; }
-keepclassmembers class androidx.core.** { *; }
-keep class androidx.lifecycle.** { *; }
-keepclassmembers class androidx.lifecycle.** { *; }
-keep class androidx.fragment.** { *; }
-keepclassmembers class androidx.fragment.** { *; }
-dontwarn androidx.core.**
-dontwarn androidx.lifecycle.**
-dontwarn androidx.fragment.**

# 10. Keep all Compose compiler-generated classes (remember, LaunchedEffect, etc.)
-keepclassmembers class * {
    androidx.compose.runtime.Composer *;
    androidx.compose.runtime.CompositionLocal *;
}

# 11. Resource shrinking rules
-keepclassmembers class **.R$* {
    public static <fields>;
}

# 12. org.json — used for WebExtension message passing
-keep class org.json.** { *; }

# 13. Third party warnings suppression
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.codehaus.mojo.animalsniffer.IgnoreJRERequirement

# 14. Kotlin coroutines — GeckoView async APIs depend on these
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
