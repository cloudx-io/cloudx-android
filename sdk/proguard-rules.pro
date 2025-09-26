# CloudX SDK ProGuard Rules for Development Builds
# These rules are only applied when building the SDK itself

# === Debugging Support ===
# Keep line numbers and source file names for better crash reports
-keepattributes SourceFile,LineNumberTable

# Keep method parameter names for debugging
-keepattributes MethodParameters

# Keep annotations for debugging and reflection
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations,RuntimeInvisibleParameterAnnotations

# === Development Optimizations ===
# Allow more aggressive optimization for internal development
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# === WebView Support ===
# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# === Exception Handling ===
# Keep exception class names for better error reporting
-keep class * extends java.lang.Exception { *; }
-keep class * extends java.lang.Error { *; }

# === Testing Support ===
# Keep test-related classes when building debug variants
-keep class **.*Test { *; }
-keep class **.*Test$* { *; }