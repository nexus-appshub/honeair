# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep data models used by networking to prevent serialization issues
-keep class com.example.data.network.** { *; }
-keep class com.example.data.model.** { *; }
-keep class com.example.data.database.** { *; }
-keep class com.example.data.api.** { *; }
-keep class com.example.update.** { *; }
-keep class com.example.download.** { *; }
-keep class com.example.network.** { *; }
-keep class com.example.ui.viewmodel.UserProfile { *; }
-keep class com.example.ui.viewmodel.** { *; }

# Firebase & Firestore rules
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**
-keep class com.google.firestore.** { *; }
-dontwarn com.google.firestore.**
-keep class io.grpc.** { *; }
-dontwarn io.grpc.**
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
    @com.google.firebase.firestore.Exclude <fields>;
    @com.google.firebase.firestore.Exclude <methods>;
    @com.google.firebase.firestore.IgnoreExtraProperties <fields>;
    @com.google.firebase.firestore.IgnoreExtraProperties <methods>;
}

# Moshi rules
-keep class * extends com.squareup.moshi.JsonAdapter {
    public *;
}
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
}
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod

# Retrofit, OkHttp rules
-keep class retrofit2.** { *; }
-keepclassmembers class * {
    @retrofit2.http.* <methods>;
}

-keep class com.squareup.okhttp3.** { *; }
-dontwarn com.squareup.okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# Coroutines rules
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Coil rules
-keep class coil.** { *; }
-dontwarn coil.**

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# High-Security Code Obfuscation & Anti-Decompilation Rules
-repackageclasses 'a'
-allowaccessmodification
-renamesourcefileattribute SourceFile

# Protect Security Guard module from reflection breaking while obfuscating implementation
-keep class com.example.security.SecurityGuard {
    public static *** isDeviceRooted(...);
    public static *** isProxyOrVpnActive(...);
    public static *** isInsecureEnvironment(...);
    public static *** applyScreenProtection(...);
    public static *** decryptSecureUrl(...);
}

# Protect Security Violation Screen
-keep class com.example.ui.screens.SecurityViolationScreenKt { *; }
