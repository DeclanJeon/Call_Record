# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Prevent code obfuscation to keep the original class, method, and field names.
-dontobfuscate

# Keep the original class and method names for debugging on release builds.
-keepattributes LineNumberTable
-keepattributes SourceFile
-keepattributes Signature, InnerClasses, EnclosingMethod

# Strip all Log.v calls
-assumenosideeffects class android.util.Log {
  v(...);
}

# Also strip your our custom AppLogger verbose calls
-assumenosideeffects class com.declan.callrecord.utils.AppLogger {
  v(...);
  java.lang.String getCallerTag(); # Needed because this is used as a parameter the v() method above
}
# --- Call_Record: on-device speech recognition (Vosk / JNA) ---
# The Vosk bindings and JNA are reached through reflection and JNI, so they must be kept.
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { *; }
-dontwarn com.sun.jna.**
-dontwarn org.vosk.**
