Wake word assets required at runtime (not committed - per-account licensed files)
================================================================================

WakeWordDetector (see app/src/main/java/com/osai/voiceassistant/voice/WakeWordDetector.kt)
looks for two files in this directory at runtime. Without them, the app still
builds and runs; wake word detection simply reports an error via
WakeWordDetector.WakeWordListener.onError() and Riding Mode continues to work
for the "Test microphone" / on-demand paths.

1. access_key.txt
   - Your personal Picovoice AccessKey from https://console.picovoice.ai
   - Plain text file containing only the key string.

2. hey_assistant.ppn
   - A custom wake word model for the phrase "Hey Assistant", trained via the
     Picovoice Console's wake word training flow for the Android platform.
   - Picovoice's free tier does not ship "Hey Assistant" as a built-in
     keyword, so a custom model is required for this exact phrase.

Both files are gitignored (see .gitignore) so real credentials are never
committed. CI/release builds should inject them via a secure secrets step
before packaging the APK.
