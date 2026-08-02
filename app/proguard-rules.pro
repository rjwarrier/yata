# Add project specific ProGuard rules here.
# Room, Hilt, Compose, and DataStore ship their own consumer-rules.pro,
# which R8 merges automatically — no manual keep rules needed for them.

# Keep attributes/annotations for kotlin metadata and standard stack traces


# Kotlin metadata (keeps reflection-based stack traces readable / data class equals-hashCode intact)
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keep class kotlin.Metadata { *; }

# PdfBox-Android's JPXFilter references the Gemalto JPEG-2000 codec, which is an optional
# dependency we don't ship. R8 treats the missing class as an error and fails the release build
# outright, so the reference has to be suppressed explicitly.
#
# Safe to suppress rather than add the codec: JPXFilter only decodes/encodes JPEG-2000 streams
# *inside* a PDF, and the PDF export path here rasterises Compose output to PNG. Nothing in the
# app can reach this code — if it ever could, the failure would be a clear NoClassDefFoundError
# on an unsupported image format, not silent corruption.
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.gemalto.jp2.JP2Encoder

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
