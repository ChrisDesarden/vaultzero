# ProGuard rules for VaultZero
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keep class com.vaultzero.app.** { *; }
-keep class androidx.datastore.** { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel { <init>(...); }
