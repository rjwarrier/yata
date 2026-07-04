# Add project specific ProGuard rules here.
# Room, Hilt, Compose, and DataStore ship their own consumer-rules.pro,
# which R8 merges automatically — no manual keep rules needed for them.

# Keep domain models: not reflected on today, but cheap insurance since
# JsonExporter/Room map onto them by field.
-keep class com.mj.yata.domain.model.** { *; }
-keep class com.mj.yata.data.local.db.entity.** { *; }

# Kotlin metadata (keeps reflection-based stack traces readable / data class equals-hashCode intact)
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keep class kotlin.Metadata { *; }
