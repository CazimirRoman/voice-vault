# whisper-android AAR JNI surface. libwhisper.so resolves these methods by exact name and
# signature at runtime, and constructs some result classes directly from native code. R8
# sees none of those references, so it would strip or rename the classes as "unused". The
# package is tiny, so keep all of it and every native method intact.
-keep class dev.ffmpegkit.whisper.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# The source is public on GitHub, so obfuscation hides nothing and only turns crash stack
# traces into unreadable a/b/c names. Keep the code shrinking and optimization; skip renaming.
-dontobfuscate
