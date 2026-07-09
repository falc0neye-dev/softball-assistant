# kotlinx.serialization — keep serializers for backup models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class com.keithfalcon.softball.** {
    kotlinx.serialization.KSerializer serializer(...);
}
