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

# Keep data models used by Gson / Supabase serialization
-keep class com.tanglycohort.greenflow.data.model.** { *; }

# Gson: preserve generic type information for TypeToken (fixes crash on release)
-keepattributes Signature
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Keep Supabase / Ktor
-keepattributes *Annotation*
-dontwarn io.ktor.**

# SLF4J: dependency brings API but no implementation on Android; suppress missing binder
-dontwarn org.slf4j.impl.StaticLoggerBinder

# Google Play Billing
-keep class com.android.vending.billing.**