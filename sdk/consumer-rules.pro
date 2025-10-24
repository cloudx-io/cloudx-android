# CloudX SDK ProGuard Rules
# These rules are automatically applied to consuming applications

# === Room Database ===
# Keep Room entities, DAOs, and database classes
-keep class io.cloudx.sdk.internal.db.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Database abstract class * { *; }

# === Dynamic Adapter Loading ===
# Keep adapter classes that are loaded dynamically via Class.forName
-keep class * implements io.cloudx.sdk.internal.adapter.CloudXAdapterInitializer { *; }
-keep class * implements io.cloudx.sdk.internal.adapter.CloudXAdapterBidRequestExtrasProvider { *; }
-keep class * implements io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapterFactory { *; }
-keep class * implements io.cloudx.sdk.internal.adapter.CloudXRewardedInterstitialAdapterFactory { *; }
-keep class * implements io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterFactory { *; }

# Keep adapter factory object instances (Kotlin objects)
-keep class **Initializer { *; }
-keep class **BidRequestExtrasProvider { *; }
-keep class **InterstitialFactory { *; }
-keep class **RewardedInterstitialFactory { *; }
-keep class **BannerFactory { *; }
-keep class **NativeAdFactory { *; }

# === JSON Serialization ===
# Keep classes used for JSON parsing with org.json
-keep class io.cloudx.sdk.internal.bid.BidResponse { *; }
-keep class io.cloudx.sdk.internal.config.Config { *; }
-keep class io.cloudx.sdk.internal.tracker.** { *; }

# === Third-party Dependencies ===
# Ktor client
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }

# Room
-dontwarn androidx.room.**
-keep class androidx.room.** { *; }

# Google Play Services
-dontwarn com.google.android.gms.**
-keep class com.google.android.gms.** { *; }

# === Warnings Suppression ===
-dontwarn org.slf4j.impl.StaticLoggerBinder
-keep class kotlin.Metadata

# === Additional Safety Rules ===
# Keep all Kotlin object instances
-keep class * extends kotlin.jvm.internal.DefaultConstructorMarker
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}