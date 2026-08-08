# Nomi keeps its own model classes readable for kotlinx.serialization. The serialization,
# Room, Ktor, and Coil artifacts ship their own consumer rules, so only Nomi's own reflective
# surface is declared here.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses,Signature

# Generated serializers are looked up through the companion of each @Serializable class.
-keepclasseswithmembers class com.nomi.app.** {
    public static ** Companion;
}
-keepclassmembers class com.nomi.app.** {
    public static **$* *;
    *** Companion;
}
-keepclasseswithmembers class com.nomi.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Enum entries are resolved by name when preferences and backups are restored.
-keepclassmembers enum com.nomi.app.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
