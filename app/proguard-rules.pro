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
# Bouncy Castle: only the JCA provider packages are kept whole. The provider registers every
# algorithm as a class-name string (that is the lookup sshj relies on), which R8 cannot see.
# Everything else (engines, ASN.1, EC math) is referenced directly from those packages and kept
# only if reachable, which drops the post-quantum, EST, ITS and other unused code: about 40% of
# the app's classes under the old keep-everything rule. A by-name scan of bcprov/bcpkix/bcutil
# 1.75 found no reflective loads outside these packages that SSH needs, only the separate PQC
# provider, the EST client and composite-key class names. Re-run that scan after an upgrade, and
# test an SFTP backup from a release build, since a miss here only shows in minified builds.
-keep class org.bouncycastle.jcajce.provider.** { *; }
-keep class org.bouncycastle.jce.provider.** { *; }
-dontwarn net.schmizz.sshj.**
-dontwarn org.bouncycastle.**
-dontwarn org.slf4j.**
-dontwarn sun.security.x509.X509Key
