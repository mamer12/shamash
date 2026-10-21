# kotlinx.serialization ships its own rules; keep the generated serializers of our @Serializable classes.
-keepclassmembers @kotlinx.serialization.Serializable class io.shamash.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
# Services, receivers and the dream are referenced from the manifest (kept by AAPT); nothing else is reflective.
