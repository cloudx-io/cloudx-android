# CloudX Android SDK

A powerful Android SDK for maximizing ad revenue through intelligent ad mediation across multiple ad networks. The CloudX SDK helps developers efficiently manage and optimize their ad inventory to ensure the highest possible returns.

## Features

- **Multiple Ad Formats**: Banner, Interstitial, Rewarded, Native, and MREC ads
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

### Required Permissions

Add these permissions to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.READ_BASIC_PHONE_STATE" />
<uses-permission android:name="com.google.android.gms.permission.AD_ID" />

<!-- Optional: For better targeting -->
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
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
    implementation("io.cloudx:sdk:<latest-sdk-version>")
    
    // Optional: CloudX Adapters (add as needed)
    // implementation("io.cloudx:adapter-meta:<latest-adapter-version>")
}
```

3. Sync your project in Android Studio.

## Quick Start

### 1. Initialize the SDK

**Kotlin:**
```kotlin
// Initialize with app key and optional hashed user ID
CloudX.initialize(
    context = this,
    initializationParams = CloudX.InitializationParams(
        appKey = "your-app-key-here",
        initEndpointUrl = "https://pro.cloudx.io/sdk",
        hashedUserId = "hashed-user-id-optional"
    )
) { status ->
    if (status.initialized) {
        Log.d("CloudX", "✅ CloudX SDK initialized successfully")
    } else {
        Log.e("CloudX", "❌ Failed to initialize CloudX SDK: ${status.description}")
    }
}
```

**Java:**
```java
// Initialize with app key and optional hashed user ID
CloudX.initialize(
    this,
    new CloudX.InitializationParams(
        "your-app-key-here",
        "https://pro.cloudx.io/sdk",
        "hashed-user-id-optional"
    ),
    status -> {
        if (status.getInitialized()) {
            Log.d("CloudX", "✅ CloudX SDK initialized successfully");
        } else {
            Log.e("CloudX", "❌ Failed to initialize CloudX SDK: " + status.getDescription());
        }
    }
);
```

### 2. Check SDK Status

**Kotlin:**
```kotlin
val isInitialized = CloudX.isInitialized
```

**Java:**
```java
boolean isInitialized = CloudX.isInitialized();
```

## Ad Integration

### Banner Ads

Banner ads are rectangular ads that appear at the top or bottom of the screen.

**Kotlin:**
```kotlin
class MainActivity : AppCompatActivity(), CloudXAdViewListener {
    private var bannerAd: CloudXAdView? = null
    
    private fun createBannerAd() {
        // Create banner ad
        bannerAd = CloudX.createBanner(
            activity = this,
            placementName = "your-banner-placement",
            listener = this
        )
        
        bannerAd?.let { banner ->
            // Add to view hierarchy
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.gravity = Gravity.CENTER_HORIZONTAL
            
            // Add to your container view
            findViewById<LinearLayout>(R.id.banner_container).addView(banner, layoutParams)
            
            // Banner starts loading automatically when added to view hierarchy
        } ?: run {
            Log.e("CloudX", "Failed to create banner ad - check placement name and SDK initialization")
        }
    }
    
    override fun onResume() {
        super.onResume()
        bannerAd?.show()
    }
    
    override fun onPause() {
        super.onPause()
        bannerAd?.hide()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        bannerAd?.destroy()
    }
    
    // CloudXAdViewListener callbacks
    override fun onAdLoaded(cloudXAd: CloudXAd) {
        Log.d("CloudX", "✅ Banner ad loaded successfully from ${cloudXAd.networkName}")
    }
    
    override fun onAdLoadFailed(cloudXAdError: CloudXAdError) {
        Log.e("CloudX", "❌ Banner ad failed to load: ${cloudXAdError.description}")
    }
    
    override fun onAdDisplayed(cloudXAd: CloudXAd) {
        Log.d("CloudX", "👀 Banner ad shown from ${cloudXAd.networkName}")
    }
    
    override fun onAdClicked(cloudXAd: CloudXAd) {
        Log.d("CloudX", "👆 Banner ad clicked from ${cloudXAd.networkName}")
    }
    
    override fun onAdHidden(cloudXAd: CloudXAd) {
        Log.d("CloudX", "🔚 Banner ad hidden from ${cloudXAd.networkName}")
    }
    
    override fun onAdDisplayFailed(cloudXAdError: CloudXAdError) {
        Log.e("CloudX", "❌ Banner ad failed to display: ${cloudXAdError.description}")
    }
    
    override fun onAdExpanded(placementName: String) {
        Log.d("CloudX", "📈 Banner ad expanded for placement: $placementName")
    }
    
    override fun onAdCollapsed(placementName: String) {
        Log.d("CloudX", "📉 Banner ad collapsed for placement: $placementName")
    }
}
```

**Java:**
```java
public class MainActivity extends AppCompatActivity implements CloudXAdViewListener {
    private CloudXAdView bannerAd;
    
    private void createBannerAd() {
        // Create banner ad
        bannerAd = CloudX.createBanner(
            this,
            "your-banner-placement",
            this
        );
        
        if (bannerAd != null) {
            // Add to view hierarchy
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            layoutParams.gravity = Gravity.CENTER_HORIZONTAL;
            
            // Add to your container view
            LinearLayout container = findViewById(R.id.banner_container);
            container.addView(bannerAd, layoutParams);
            
            // Banner starts loading automatically when added to view hierarchy
        } else {
            Log.e("CloudX", "Failed to create banner ad - check placement name and SDK initialization");
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (bannerAd != null) {
            bannerAd.show();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (bannerAd != null) {
            bannerAd.hide();
        }
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
        Log.d("CloudX", "✅ Banner ad loaded successfully from " + cloudXAd.getNetworkName());
    }
    
    @Override
    public void onAdLoadFailed(CloudXAdError cloudXAdError) {
        Log.e("CloudX", "❌ Banner ad failed to load: " + cloudXAdError.getDescription());
    }
    
    @Override
    public void onAdDisplayed(CloudXAd cloudXAd) {
        Log.d("CloudX", "👀 Banner ad shown from " + cloudXAd.getNetworkName());
    }
    
    @Override
    public void onAdClicked(CloudXAd cloudXAd) {
        Log.d("CloudX", "👆 Banner ad clicked from " + cloudXAd.getNetworkName());
    }
    
    @Override
    public void onAdHidden(CloudXAd cloudXAd) {
        Log.d("CloudX", "🔚 Banner ad hidden from " + cloudXAd.getNetworkName());
    }
    
    @Override
    public void onAdDisplayFailed(CloudXAdError cloudXAdError) {
        Log.e("CloudX", "❌ Banner ad failed to display: " + cloudXAdError.getDescription());
    }
    
    @Override
    public void onAdExpanded(String placementName) {
        Log.d("CloudX", "📈 Banner ad expanded for placement: " + placementName);
    }
    
    @Override
    public void onAdCollapsed(String placementName) {
        Log.d("CloudX", "📉 Banner ad collapsed for placement: " + placementName);
    }
}
```

### Interstitial Ads

Interstitial ads are full-screen ads that appear between app content.

**Kotlin:**
```kotlin
class MainActivity : AppCompatActivity(), CloudXInterstitialListener {
    private var interstitialAd: CloudXInterstitialAd? = null
    
    private fun createInterstitialAd() {
        // Create interstitial ad
        interstitialAd = CloudX.createInterstitial(
            activity = this,
            placementName = "your-interstitial-placement",
            listener = this
        )
        
        interstitialAd?.let { ad ->
            // Load the ad
            ad.load()
            
            // Optional: Listen to load status changes
            ad.setIsAdLoadedListener { isLoaded ->
                Log.d("CloudX", "Interstitial ad load status changed: $isLoaded")
            }
        } ?: run {
            Log.e("CloudX", "Failed to create interstitial ad")
        }
    }
    
    private fun showInterstitialAd() {
        interstitialAd?.let { ad ->
            if (ad.isAdLoaded) {
                ad.show()
            } else {
                Log.w("CloudX", "Interstitial ad not ready yet")
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        interstitialAd?.destroy()
    }
    
    // CloudXInterstitialListener callbacks
    override fun onAdLoaded(cloudXAd: CloudXAd) {
        Log.d("CloudX", "✅ Interstitial ad loaded successfully from ${cloudXAd.networkName}")
    }
    
    override fun onAdLoadFailed(cloudXAdError: CloudXAdError) {
        Log.e("CloudX", "❌ Interstitial ad failed to load: ${cloudXAdError.description}")
    }
    
    override fun onAdDisplayed(cloudXAd: CloudXAd) {
        Log.d("CloudX", "👀 Interstitial ad shown from ${cloudXAd.networkName}")
    }
    
    override fun onAdDisplayFailed(cloudXAdError: CloudXAdError) {
        Log.e("CloudX", "❌ Interstitial ad failed to show: ${cloudXAdError.description}")
    }
    
    override fun onAdHidden(cloudXAd: CloudXAd) {
        Log.d("CloudX", "🔚 Interstitial ad hidden from ${cloudXAd.networkName}")
        // Reload for next use
        createInterstitialAd()
    }
    
    override fun onAdClicked(cloudXAd: CloudXAd) {
        Log.d("CloudX", "👆 Interstitial ad clicked from ${cloudXAd.networkName}")
    }
}
```

**Java:**
```java
public class MainActivity extends AppCompatActivity implements CloudXInterstitialListener {
    private CloudXInterstitialAd interstitialAd;
    
    private void createInterstitialAd() {
        // Create interstitial ad
        interstitialAd = CloudX.createInterstitial(
            this,
            "your-interstitial-placement",
            this
        );
        
        if (interstitialAd != null) {
            // Load the ad
            interstitialAd.load();
            
            // Optional: Listen to load status changes
            interstitialAd.setIsAdLoadedListener(isLoaded -> {
                Log.d("CloudX", "Interstitial ad load status changed: " + isLoaded);
            });
        } else {
            Log.e("CloudX", "Failed to create interstitial ad");
        }
    }
    
    private void showInterstitialAd() {
        if (interstitialAd != null) {
            if (interstitialAd.getIsAdLoaded()) {
                interstitialAd.show();
            } else {
                Log.w("CloudX", "Interstitial ad not ready yet");
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (interstitialAd != null) {
            interstitialAd.destroy();
        }
    }
    
    // CloudXInterstitialListener callbacks
    @Override
    public void onAdLoaded(CloudXAd cloudXAd) {
        Log.d("CloudX", "✅ Interstitial ad loaded successfully from " + cloudXAd.getNetworkName());
    }
    
    @Override
    public void onAdLoadFailed(CloudXAdError cloudXAdError) {
        Log.e("CloudX", "❌ Interstitial ad failed to load: " + cloudXAdError.getDescription());
    }
    
    @Override
    public void onAdDisplayed(CloudXAd cloudXAd) {
        Log.d("CloudX", "👀 Interstitial ad shown from " + cloudXAd.getNetworkName());
    }
    
    @Override
    public void onAdDisplayFailed(CloudXAdError cloudXAdError) {
        Log.e("CloudX", "❌ Interstitial ad failed to show: " + cloudXAdError.getDescription());
    }
    
    @Override
    public void onAdHidden(CloudXAd cloudXAd) {
        Log.d("CloudX", "🔚 Interstitial ad hidden from " + cloudXAd.getNetworkName());
        // Reload for next use
        createInterstitialAd();
    }
    
    @Override
    public void onAdClicked(CloudXAd cloudXAd) {
        Log.d("CloudX", "👆 Interstitial ad clicked from " + cloudXAd.getNetworkName());
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

### Revenue Information

Get publisher revenue information for loaded ads:

**Kotlin:**
```kotlin
// For fullscreen ads (Interstitial/Rewarded)
interstitialAd?.adToDisplayInfo?.let { info ->
    val revenue = info.publisherRevenue // Revenue in USD
    val network = info.networkName      // Ad network name
    Log.d("CloudX", "Expected revenue: $$revenue from $network")
}
```

**Java:**
```java
// For fullscreen ads (Interstitial/Rewarded)
CloudXAdToDisplayInfoApi.Info info = interstitialAd.getAdToDisplayInfo();
if (info != null) {
    Double revenue = info.getPublisherRevenue(); // Revenue in USD
    String network = info.getNetworkName();      // Ad network name
    Log.d("CloudX", "Expected revenue: $" + revenue + " from " + network);
}
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
| `CloudX.initialize(context, params, listener)` | Initialize SDK with context and parameters |
| `CloudX.isInitialized` | Check if SDK is initialized |
| `CloudX.setPrivacy(privacy)` | Set privacy preferences |

### Ad Creation Methods

| Method | Description |
|--------|-------------|
| `CloudX.createBanner(activity, placement, listener)` | Create banner ad (320x50) |
| `CloudX.createMREC(activity, placement, listener)` | Create MREC ad (300x250) |
| `CloudX.createNativeAdSmall(activity, placement, listener)` | Create small native ad |
| `CloudX.createNativeAdMedium(activity, placement, listener)` | Create medium native ad |
| `CloudX.createInterstitial(activity, placement, listener)` | Create interstitial ad |
| `CloudX.createRewardedInterstitial(activity, placement, listener)` | Create rewarded ad |

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
| `load()` | Load ad content (fullscreen ads) |
| `show()` | Show/resume ad display |
| `hide()` | Hide/pause ad display (banner ads) |
| `isAdLoaded` | Check if fullscreen ad is ready |
| `destroy()` | Destroy ad and release resources |
| `setIsAdLoadedListener(listener)` | Listen to load status changes |

### Listener Callbacks

All ad types support these common callbacks:
- `onAdLoaded(ad)` - Ad loaded successfully
- `onAdLoadFailed(error)` - Ad failed to load
- `onAdDisplayed(ad)` - Ad was shown
- `onAdDisplayFailed(error)` - Ad failed to show
- `onAdHidden(ad)` - Ad was hidden
- `onAdClicked(ad)` - Ad was clicked

**Banner/Native ads additionally support:**
- `onAdExpanded(placement)` - Ad was expanded
- `onAdCollapsed(placement)` - Ad was collapsed

**Rewarded ads additionally support:**
- `onUserRewarded(ad)` - User earned reward

## Troubleshooting

### Common Issues

1. **SDK not initialized**
   - Ensure you call `CloudX.initialize()` before creating ads
   - Check that the initialization callback is called with success

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
// Access internal logging (for debugging only)
import io.cloudx.sdk.internal.CloudXLogger
CloudXLogger.logEnabled = true
```

**Java:**
```java
// Access internal logging (for debugging only)
import io.cloudx.sdk.internal.CloudXLogger;
CloudXLogger.setLogEnabled(true);
```

## Support

- **Documentation**: [CloudX Android SDK Docs](https://github.com/cloudx-xenoss/cloudexchange.android.sdk)
- **Issues**: [GitHub Issues](https://github.com/cloudx-xenoss/cloudexchange.android.sdk/issues)
- **Email**: eng@cloudx.io

## License

This project is licensed under the Elastic License 2.0. See the [LICENSE](./LICENSE) file for details.
