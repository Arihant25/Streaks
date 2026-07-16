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

# Keep line numbers so Play Console crash traces retrace to real lines
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Gson deserializes streak data reflectively; renaming or stripping these
# classes would silently null out saved streak fields on load
-keep class com.arihant.streaks.data.** { *; }

# Gson resolves List<StreakExportDto> etc. through generic signatures and
# TypeToken subclasses; R8 full mode strips both without these
-keepattributes Signature
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken