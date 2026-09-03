# Voice-Controlled Bluetooth Assistant - MVP 1 ProGuard rules

# Keep Porcupine wake-word engine (JNI bindings rely on class/method names)
-keep class ai.picovoice.porcupine.** { *; }
-dontwarn ai.picovoice.porcupine.**

# Keep enum values used by reflection-free command parsing (defensive, enums are safe by default)
-keepclassmembers enum com.osai.voiceassistant.command.CommandType { *; }

# Keep MediaSession callback classes
-keep class androidx.media.** { *; }

# Kotlin coroutines
-dontwarn kotlinx.coroutines.**
