# Orbit Files – R8 rules
# Keep crash stack traces readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
# Biometric uses reflection-free APIs; Compose, Coil 3 and Media3 ship consumer rules.
-dontwarn org.slf4j.**
