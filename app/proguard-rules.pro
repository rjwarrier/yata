# Add project specific ProGuard rules here.
# Room, Hilt, Compose, and DataStore ship their own consumer-rules.pro,
# which R8 merges automatically — no manual keep rules needed for them.

# Keep attributes/annotations for kotlin metadata and standard stack traces


# Kotlin metadata (keeps reflection-based stack traces readable / data class equals-hashCode intact)
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keep class kotlin.Metadata { *; }

# sshj (SFTP self-hosted backup) and Bouncy Castle look up cipher/KEX/signature algorithm
# implementations by class name at runtime (a ServiceLoader-style pattern), not through direct
# references R8 can see. Without these keep rules, an algorithm class that's never referenced
# directly gets stripped as "unused" and the SFTP connection fails at runtime in a release build
# only -- the debug build (no minification) would show nothing wrong. Both are plain JARs, not
# AARs, so unlike Room/Hilt/Compose above they don't ship their own consumer-rules.pro.
-keep class net.schmizz.sshj.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn net.schmizz.sshj.**
-dontwarn org.bouncycastle.**
-dontwarn org.slf4j.**
-dontwarn sun.security.x509.X509Key
