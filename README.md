# CloudX Android SDK

A powerful Android SDK for maximizing ad revenue through intelligent ad mediation across multiple ad networks. The CloudX SDK helps developers efficiently manage and optimize their ad inventory to ensure the highest possible returns.

## Features

- **Multiple Ad Formats**: Banner, Interstitial, and MREC ads
- **Intelligent Mediation**: Automatic optimization across multiple ad networks
- **Real-time Bidding**: Advanced bidding technology for maximum revenue
- **Comprehensive Analytics**: Detailed reporting and performance metrics
- **Easy Integration**: Simple API with comprehensive listener callbacks
- **Privacy Compliance**: Built-in GDPR, CCPA, and COPPA support
- **Revenue Transparency**: Publisher revenue reporting for optimization

## Requirements

- **Android**: API 21 (Android 5.0) or later
- **Target SDK**: API 35 recommended
- **Kotlin**: 1.9.0+ (built with 1.9.22)
- **Java**: Compatible with Java 8+ projects
- **Gradle**: 8.0+ with Android Gradle Plugin 8.0+

### Dependencies

The CloudX SDK uses the following key dependencies that may affect compatibility:

- **Ktor**: 2.3.8 (HTTP client library using Android engine)
- **Kotlinx Coroutines**: 1.7.3

**Note on Version Compatibility:** If your app uses different versions of Ktor or Kotlinx Coroutines, Gradle will 
typically resolve to the higher version. While we've tested basic compatibility, if you encounter version conflicts 
or runtime issues, please [report them](https://github.com/cloudx-io/cloudx-android/issues).

### Required Permissions

Add these permissions to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.READ_BASIC_PHONE_STATE" />
<uses-permission android:name="com.google.android.gms.permission.AD_ID" />
```

## Installation

### Gradle (Recommended)

1. Add Maven Central to your `settings.gradle` or `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
```

2. Add the CloudX SDK to your app's `build.gradle` or `build.gradle.kts`:

```kotlin
dependencies {
    // CloudX Core SDK
    implementation("io.cloudx:sdk:0.0.1.41")

    // Optional: CloudX Adapters (add as needed)
    implementation("io.cloudx:adapter-cloudx:0.0.1.41")
    implementation("io.cloudx:adapter-meta:0.0.1.41")
}
```

3. Sync your project in Android Studio.

## Quick Start

### 1. Initialize the SDK

**Kotlin:**
```kotlin
// Initialize with app key and optional hashed user ID
CloudX.initialize(
    initParams = CloudXInitializationParams(
        appKey = "your-app-key-here",
        initServer = CloudXInitializationServer.Production,
        hashedUserId = "hashed-user-id-optional"
    ),
    listener = object : CloudXInitializationListener {
        override fun onInitialized() {
            Log.d("CloudX", "✅ CloudX SDK initialized successfully")
        }

        override fun onInitializationFailed(cloudXError: CloudXError) {
            Log.e("CloudX", "❌ Failed to initialize CloudX SDK: ${cloudXError.effectiveMessage}")
        }
    }
)
```

**Java:**
```java
// Initialize with app key and optional hashed user ID
CloudX.initialize(
    new CloudXInitializationParams(
        "your-app-key-here",
        CloudXInitializationServer.production(),
        "hashed-user-id-optional"
    ),
    new CloudXInitializationListener() {
        @Override
        public void onInitialized() {
            Log.d("CloudX", "✅ CloudX SDK initialized successfully");
        }

        @Override
        public void onInitializationFailed(CloudXError cloudXError) {
            Log.e("CloudX", "❌ Failed to initialize CloudX SDK: " + cloudXError.getEffectiveMessage());
        }
    }
);
```

### 2. Enable Debug Logging (Optional)

**Kotlin:**
```kotlin
// Enable debug logging for troubleshooting
CloudX.setLoggingEnabled(true)
CloudX.setMinLogLevel(CloudXLogLevel.DEBUG)
```

**Java:**
```java
// Enable debug logging for troubleshooting
CloudX.setLoggingEnabled(true);
CloudX.setMinLogLevel(CloudXLogLevel.DEBUG);
```

## Ad Integration

### Banner Ads

Banner ads are rectangular ads that appear at the top or bottom of the screen.

**Kotlin:**
```kotlin
class MainActivity : AppCompatActivity(), CloudXAdViewListener {
    private lateinit var bannerAd: CloudXAdView

    private fun createBannerAd() {
        // Create banner ad
        bannerAd = CloudX.createBanner("your-banner-placement-name")

        // Set listener
        bannerAd.listener = this

        // Add to view hierarchy
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.gravity = Gravity.CENTER_HORIZONTAL

        // Add to your container view
        findViewById<LinearLayout>(R.id.banner_container).addView(bannerAd, layoutParams)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        bannerAd.destroy()
    }
    
    // CloudXAdViewListener callbacks
    override fun onAdLoaded(cloudXAd: CloudXAd) {
        Log.d("CloudX", "✅ Banner ad loaded successfully from ${cloudXAd.bidderName}")
    }
    
    override fun onAdLoadFailed(cloudXError: CloudXError) {
        Log.e("CloudX", "❌ Banner ad failed to load: ${cloudXError.effectiveMessage}")
    }
    
    override fun onAdDisplayed(cloudXAd: CloudXAd) {
        Log.d("CloudX", "👀 Banner ad shown from ${cloudXAd.bidderName}")
    }

    override fun onAdClicked(cloudXAd: CloudXAd) {
        Log.d("CloudX", "👆 Banner ad clicked from ${cloudXAd.bidderName}")
    }

    override fun onAdHidden(cloudXAd: CloudXAd) {
        Log.d("CloudX", "🔚 Banner ad hidden from ${cloudXAd.bidderName}")
    }
    
    override fun onAdDisplayFailed(cloudXError: CloudXError) {
        Log.e("CloudX", "❌ Banner ad failed to display: ${cloudXError.effectiveMessage}")
    }
    
    override fun onAdExpanded(cloudXAd: CloudXAd) {
        Log.d("CloudX", "📈 Banner ad expanded from ${cloudXAd.bidderName}")
    }

    override fun onAdCollapsed(cloudXAd: CloudXAd) {
        Log.d("CloudX", "📉 Banner ad collapsed from ${cloudXAd.bidderName}")
    }
}
```

**Java:**
```java
public class MainActivity extends AppCompatActivity implements CloudXAdViewListener {
    private CloudXAdView bannerAd;

    private void createBannerAd() {
        // Create banner ad
        bannerAd = CloudX.createBanner("your-banner-placement-name");

        // Set listener
        bannerAd.setListener(this);

        // Add to view hierarchy
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        layoutParams.gravity = Gravity.CENTER_HORIZONTAL;

        // Add to your container view
        LinearLayout container = findViewById(R.id.banner_container);
        container.addView(bannerAd, layoutParams);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bannerAd != null) {
            bannerAd.destroy();
        }
    }
    
    // CloudXAdViewListener callbacks
    @Override
    public void onAdLoaded(CloudXAd cloudXAd) {
        Log.d("CloudX", "✅ Banner ad loaded successfully from " + cloudXAd.getBidderName());
    }
    
    @Override
    public void onAdLoadFailed(CloudXError cloudXError) {
        Log.e("CloudX", "❌ Banner ad failed to load: " + cloudXError.getEffectiveMessage());
    }
    
    @Override
    public void onAdDisplayed(CloudXAd cloudXAd) {
        Log.d("CloudX", "👀 Banner ad shown from " + cloudXAd.getBidderName());
    }

    @Override
    public void onAdClicked(CloudXAd cloudXAd) {
        Log.d("CloudX", "👆 Banner ad clicked from " + cloudXAd.getBidderName());
    }

    @Override
    public void onAdHidden(CloudXAd cloudXAd) {
        Log.d("CloudX", "🔚 Banner ad hidden from " + cloudXAd.getBidderName());
    }
    
    @Override
    public void onAdDisplayFailed(CloudXError cloudXError) {
        Log.e("CloudX", "❌ Banner ad failed to display: " + cloudXError.getEffectiveMessage());
    }
    
    @Override
    public void onAdExpanded(CloudXAd cloudXAd) {
        Log.d("CloudX", "📈 Banner ad expanded from " + cloudXAd.getBidderName());
    }

    @Override
    public void onAdCollapsed(CloudXAd cloudXAd) {
        Log.d("CloudX", "📉 Banner ad collapsed from " + cloudXAd.getBidderName());
    }
}
```

### Interstitial Ads

Interstitial ads are full-screen ads that appear between app content.

**Kotlin:**
```kotlin
class MainActivity : AppCompatActivity(), CloudXInterstitialListener {
    private lateinit var interstitialAd: CloudXInterstitialAd

    private fun createInterstitialAd() {
        // Create interstitial ad
        interstitialAd = CloudX.createInterstitial("your-interstitial-placement-name")

        // Set listener
        interstitialAd.listener = this

        // Load the ad
        interstitialAd.load()
    }
    
    private fun showInterstitialAd() {
        if (interstitialAd.isAdReady) {
            interstitialAd.show()
        } else {
            Log.w("CloudX", "Interstitial ad not ready yet")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        interstitialAd.destroy()
    }
    
    // CloudXInterstitialListener callbacks
    override fun onAdLoaded(cloudXAd: CloudXAd) {
        Log.d("CloudX", "✅ Interstitial ad loaded successfully from ${cloudXAd.bidderName}")
    }
    
    override fun onAdLoadFailed(cloudXError: CloudXError) {
        Log.e("CloudX", "❌ Interstitial ad failed to load: ${cloudXError.effectiveMessage}")
    }
    
    override fun onAdDisplayed(cloudXAd: CloudXAd) {
        Log.d("CloudX", "👀 Interstitial ad shown from ${cloudXAd.bidderName}")
    }

    override fun onAdDisplayFailed(cloudXError: CloudXError) {
        Log.e("CloudX", "❌ Interstitial ad failed to show: ${cloudXError.effectiveMessage}")
    }

    override fun onAdHidden(cloudXAd: CloudXAd) {
        Log.d("CloudX", "🔚 Interstitial ad hidden from ${cloudXAd.bidderName}")
        // Reload for next use
        createInterstitialAd()
    }

    override fun onAdClicked(cloudXAd: CloudXAd) {
        Log.d("CloudX", "👆 Interstitial ad clicked from ${cloudXAd.bidderName}")
    }
}
```

**Java:**
```java
public class MainActivity extends AppCompatActivity implements CloudXInterstitialListener {
    private CloudXInterstitialAd interstitialAd;

    private void createInterstitialAd() {
        // Create interstitial ad
        interstitialAd = CloudX.createInterstitial("your-interstitial-placement-name");

        // Set listener
        interstitialAd.setListener(this);

        // Load the ad
        interstitialAd.load();
    }
    
    private void showInterstitialAd() {
        if (interstitialAd.getIsAdReady()) {
            interstitialAd.show();
        } else {
            Log.w("CloudX", "Interstitial ad not ready yet");
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        interstitialAd.destroy();
    }
    
    // CloudXInterstitialListener callbacks
    @Override
    public void onAdLoaded(CloudXAd cloudXAd) {
        Log.d("CloudX", "✅ Interstitial ad loaded successfully from " + cloudXAd.getBidderName());
    }
    
    @Override
    public void onAdLoadFailed(CloudXError cloudXError) {
        Log.e("CloudX", "❌ Interstitial ad failed to load: " + cloudXError.getEffectiveMessage());
    }
    
    @Override
    public void onAdDisplayed(CloudXAd cloudXAd) {
        Log.d("CloudX", "👀 Interstitial ad shown from " + cloudXAd.getBidderName());
    }

    @Override
    public void onAdDisplayFailed(CloudXError cloudXError) {
        Log.e("CloudX", "❌ Interstitial ad failed to show: " + cloudXError.getEffectiveMessage());
    }

    @Override
    public void onAdHidden(CloudXAd cloudXAd) {
        Log.d("CloudX", "🔚 Interstitial ad hidden from " + cloudXAd.getBidderName());
        // Reload for next use
        createInterstitialAd();
    }

    @Override
    public void onAdClicked(CloudXAd cloudXAd) {
        Log.d("CloudX", "👆 Interstitial ad clicked from " + cloudXAd.getBidderName());
    }
}
```

## Advanced Features

### Privacy Compliance & GDPR Integration

The CloudX SDK supports privacy compliance for GDPR, CCPA, and COPPA regulations. Publishers are responsible for obtaining consent through their Consent Management Platform (CMP) and providing the privacy signals to our SDK.

**Kotlin:**
```kotlin
// Set privacy preferences
CloudX.setPrivacy(
    CloudXPrivacy(
        isUserConsent = true,        // GDPR consent
        isAgeRestrictedUser = false, // COPPA compliance
    )
)

// For IAB TCF compliance, set values in SharedPreferences
val prefs = PreferenceManager.getDefaultSharedPreferences(this)
prefs.edit().apply {
    // GDPR TCF String
    putString("IABTCF_TCString", "CPcABcABcABcA...")
    putInt("IABTCF_gdprApplies", 1) // 1 = applies, 0 = doesn't apply
    
    // CCPA Privacy String
    putString("IABUSPrivacy_String", "1YNN")
    
    // GPP String (Global Privacy Platform)
    putString("IABGPP_HDR_GppString", "DBACNYA~CPXxRfAPXxRfAAfKABENB...")
    putString("IABGPP_GppSID", "7_8")
    
    apply()
}
```

**Java:**
```java
// Set privacy preferences
CloudX.setPrivacy(new CloudXPrivacy(
    true,  // isUserConsent (GDPR)
    false // isAgeRestrictedUser (COPPA)
));

// For IAB TCF compliance, set values in SharedPreferences
SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
SharedPreferences.Editor editor = prefs.edit();

// GDPR TCF String
editor.putString("IABTCF_TCString", "CPcABcABcABcA...");
editor.putInt("IABTCF_gdprApplies", 1); // 1 = applies, 0 = doesn't apply

// CCPA Privacy String
editor.putString("IABUSPrivacy_String", "1YNN");

// GPP String (Global Privacy Platform)
editor.putString("IABGPP_HDR_GppString", "DBACNYA~CPXxRfAPXxRfAAfKABENB...");
editor.putString("IABGPP_GppSID", "7_8");

editor.apply();
```

#### Privacy Keys Reference

| Key | Type | Description |
|-----|------|-------------|
| `IABTCF_TCString` | String | GDPR TC String from your CMP |
| `IABTCF_gdprApplies` | Integer | Whether GDPR applies (1 = yes, 0 = no) |
| `IABUSPrivacy_String` | String | CCPA privacy string (e.g., "1YNN") |
| `IABGPP_HDR_GppString` | String | Global Privacy Platform string |
| `IABGPP_GppSID` | String | GPP Section IDs |

### User Targeting

**Kotlin:**
```kotlin
// Set hashed user ID for targeting
CloudX.setHashedUserId("hashed-user-id")

// Set custom user key-value pairs
CloudX.setUserKeyValue("age", "25")
CloudX.setUserKeyValue("gender", "male")
CloudX.setUserKeyValue("location", "US")

// Set custom app key-value pairs
CloudX.setAppKeyValue("app_version", "1.0.0")
CloudX.setAppKeyValue("user_level", "premium")

// Clear all custom key-values
CloudX.clearAllKeyValues()
```

**Java:**
```java
// Set hashed user ID for targeting
CloudX.setHashedUserId("hashed-user-id");

// Set custom user key-value pairs
CloudX.setUserKeyValue("age", "25");
CloudX.setUserKeyValue("gender", "male");
CloudX.setUserKeyValue("location", "US");

// Set custom app key-value pairs
CloudX.setAppKeyValue("app_version", "1.0.0");
CloudX.setAppKeyValue("user_level", "premium");

// Clear all custom key-values
CloudX.clearAllKeyValues();
```


### Test Mode Configuration

Enable test mode for supported ad networks:

**Kotlin:**
```kotlin
// Enable Meta Audience Network test mode
import io.cloudx.adapter.meta.enableMetaAudienceNetworkTestMode
enableMetaAudienceNetworkTestMode(true)
```

**Java:**
```java
// Enable Meta Audience Network test mode
import static io.cloudx.adapter.meta.MetaEnableTestModeKt.enableMetaAudienceNetworkTestMode;
enableMetaAudienceNetworkTestMode(true);
```

## API Reference

### Core Methods

| Method | Description |
|--------|-------------|
| `CloudX.initialize(params, listener)` | Initialize SDK with parameters and listener |
| `CloudX.setPrivacy(privacy)` | Set privacy preferences |
| `CloudX.setLoggingEnabled(enabled)` | Enable/disable SDK logging |
| `CloudX.setMinLogLevel(level)` | Set minimum log level |

### Ad Creation Methods

| Method | Description |
|--------|-------------|
| `CloudX.createBanner(placement)` | Create banner ad (320x50) |
| `CloudX.createMREC(placement)` | Create MREC ad (300x250) |
| `CloudX.createInterstitial(placement)` | Create interstitial ad |

### User Targeting Methods

| Method | Description |
|--------|-------------|
| `CloudX.setHashedUserId(hashedId)` | Set hashed user ID |
| `CloudX.setUserKeyValue(key, value)` | Set user key-value pair |
| `CloudX.setAppKeyValue(key, value)` | Set app key-value pair |
| `CloudX.clearAllKeyValues()` | Clear all custom key-values |

### Ad Control Methods

| Method | Description |
|--------|-------------|
| `load()` | Load ad content (interstitial only) |
| `show()` | Show fullscreen ad (interstitial only) |
| `isAdReady` | Check if fullscreen ad is ready |
| `destroy()` | Destroy ad and release resources |
| `listener` | Property to set ad event listener |

### Listener Callbacks

All ad types support these common callbacks:
- `onAdLoaded(ad)` - Ad loaded successfully
- `onAdLoadFailed(error)` - Ad failed to load
- `onAdDisplayed(ad)` - Ad was shown
- `onAdDisplayFailed(error)` - Ad failed to show
- `onAdHidden(ad)` - Ad was hidden
- `onAdClicked(ad)` - Ad was clicked

**Banner ads additionally support:**
- `onAdExpanded(ad)` - Ad was expanded
- `onAdCollapsed(ad)` - Ad was collapsed

## Troubleshooting

### Common Issues

1. **SDK not initialized**
   - Ensure you call `CloudX.initialize()` before creating ads
   - Check that the `onInitialized()` callback is called successfully

2. **Ads not loading**
   - Verify your placement names are correct
   - Check network connectivity
   - Ensure you're testing on a real device (not emulator for some networks)

3. **Listener methods not called**
   - Verify your activity/fragment implements the correct listener interface
   - Ensure the listener is set when creating ads

4. **Build errors**
   - Make sure you're using Android API 21 or later
   - Verify all required dependencies are included
   - Check that you're using compatible Kotlin/Gradle versions

### Debug Logging

Enable debug logging to troubleshoot issues:

**Kotlin:**
```kotlin
// Enable debug logging for troubleshooting
CloudX.setLoggingEnabled(true)
CloudX.setMinLogLevel(CloudXLogLevel.DEBUG)
```

**Java:**
```java
// Enable debug logging for troubleshooting
CloudX.setLoggingEnabled(true);
CloudX.setMinLogLevel(CloudXLogLevel.DEBUG);
```

## Support

- **Documentation**: [CloudX Android SDK Docs](https://github.com/cloudx-io/cloudx-android)
- **Issues**: [GitHub Issues](https://github.com/cloudx-io/cloudx-android/issues)
- **Email**: mobile@cloudx.io

## License

This project is licensed under the Elastic License 2.0. See the [LICENSE](./LICENSE) file for details.
