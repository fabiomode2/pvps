# JSch
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**
-keep class org.bouncycastle.crypto.** { *; }
-keep class org.bouncycastle.jce.provider.** { *; }
-dontwarn com.sun.jna.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.slf4j.**
-dontwarn org.ietf.jgss.**
-dontwarn org.newsclub.net.unix.**
-dontwarn javax.naming.**

# Termux terminal emulator (GPLv3, personal use)
-keep class com.termux.terminal.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.controlcenter.**$$serializer { *; }
-keepclassmembers class dev.controlcenter.** {
    *** Companion;
}
-keepclasseswithmembers class dev.controlcenter.** {
    kotlinx.serialization.KSerializer serializer(...);
}
