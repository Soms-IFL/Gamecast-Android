# Shrink unused code only; keep names and behaviour as written.
-dontobfuscate
-dontoptimize
-keep class app.cash.quickjs.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
-keep class com.iflgamecast.app.** { *; }
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
