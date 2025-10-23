# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CloudX Android SDK is an ad mediation SDK that maximizes ad revenue through intelligent bidding across multiple ad networks. The SDK supports Banner, MREC, Interstitial, and Rewarded Interstitial ads with real-time bidding capabilities.

**Main Branch:** `main`
**Active Development Branch:** `develop`

## Build Commands

### Basic Build & Test
```bash
# Build all modules
./gradlew build

# Build specific module
./gradlew :sdk:build
./gradlew :adapter-cloudx:build
./gradlew :adapter-meta:build
./gradlew :app:build

# Run unit tests
./gradlew test
./gradlew :sdk:test

# Clean build
./gradlew clean
```

### Running the Demo App
```bash
# Install debug build to connected device
./gradlew :app:installDebug

# Build release APK (for direct installation)
./gradlew :app:assembleRelease

# Build release AAB (for Google Play Store)
./gradlew :app:bundleRelease
```

### Publishing
```bash
# Publish to Maven Local (for local testing)
./gradlew publishToMavenLocal

# Publish to Maven Central
./gradlew publishToMavenCentral

# Publish and release to Maven Central
./gradlew publishAndReleaseToMavenCentral
```

## Project Structure

The repository is organized into several modules:

### `:sdk` - Core SDK Module
- **Location:** `sdk/src/main/java/io/cloudx/sdk/`
- **Purpose:** Core SDK functionality, public API
- **Key Public API Classes:**
  - `CloudX.kt` - Main entry point for SDK initialization and ad creation
  - `CloudXAdView.kt` - Banner/MREC ad view component
  - `CloudXInterstitialAd.kt` - Interstitial ad interface
  - `CloudXRewardedInterstitialAd.kt` - Rewarded interstitial ad interface
  - `CloudXError.kt`, `CloudXErrorCode.kt` - Error handling
  - `CloudXPrivacy.kt` - Privacy settings (GDPR, CCPA, COPPA)

### `:sdk/internal` - Internal SDK Implementation
- **Location:** `sdk/src/main/java/io/cloudx/sdk/internal/`
- **Purpose:** Internal implementation details (not part of public API)
- **Key Subsystems:**
  - `adapter/` - Adapter interface definitions for ad networks
  - `ads/` - Ad loading, rendering, and lifecycle management
  - `bid/` - Bid request/response handling and auction logic
  - `config/` - Configuration API and management
  - `initialization/` - SDK initialization service
  - `privacy/` - Privacy compliance (GDPR, CCPA, GPP)
  - `tracker/` - Analytics, win/loss tracking, error reporting
  - `httpclient/` - HTTP client for network requests
  - `db/` - Room database for persistent storage
  - `CXSdk.kt` - Internal SDK singleton managing initialization state
  - `CXLogger.kt` - Logging infrastructure

### `:adapter-cloudx` - CloudX Network Adapter
- **Location:** `adapter-cloudx/src/main/java/io/cloudx/adapter/cloudx/`
- **Purpose:** First-party CloudX ad network integration
- **Key Files:**
  - `BannerFactory.kt`, `InterstitialFactory.kt`, `RewardedInterstitialFactory.kt` - Ad factories
  - `StaticBidBanner.kt`, `StaticBidInterstitial.kt` - Static bid implementations
  - `NativeAd.kt`, `NativeAdFactory.kt` - Native ad support
  - `Initializer.kt` - Adapter initialization

### `:adapter-meta` - Meta Audience Network Adapter
- **Location:** `adapter-meta/src/main/java/io/cloudx/adapter/meta/`
- **Purpose:** Integration with Meta Audience Network
- **Note:** Similar structure to adapter-cloudx

### `:app` - Demo Application
- **Location:** `app/src/main/`
- **Purpose:** Demo app showcasing SDK integration
- **Note:** Uses local dependencies (`:sdk`, `:adapter-cloudx`, `:adapter-meta`) for development

### `:build-logic` - Build Configuration
- **Location:** `build-logic/src/main/kotlin/`
- **Purpose:** Shared Gradle build configuration and conventions
- **Key Files:**
  - `app-conventions.gradle.kts` - Conventions for app modules

## Architecture

### Initialization Flow
1. Publisher calls `CloudX.initialize(params, listener)` → `CloudX.kt`
2. `CloudX` delegates to `CXSdk.initialize()` → `sdk/internal/CXSdk.kt`
3. `CXSdk` creates `InitializationService` and fetches configuration via `ConfigApi`
4. Once initialized, state transitions to `InitializationState.Initialized` with `InitializationService`
5. Success/failure callbacks are invoked on the main thread

### Ad Loading Flow
1. Publisher creates ad via `CloudX.createBanner()` / `CloudX.createInterstitial()` → `CloudX.kt`
2. Ad instance is created (e.g., `CloudXAdView`, `CXInterstitialAd`)
3. When load is triggered (automatically for banners, explicitly for interstitials):
   - `AdLoader` is created with `BidAdSource` → `sdk/internal/ads/AdLoader.kt`
   - `BidAdSource` requests bid from server via `BidApi` → `sdk/internal/bid/`
   - Server returns ranked list of ad network bids (auction winners)
   - `AdLoader` iterates through bids by rank and attempts to load from each adapter
   - First successful ad load wins, others are notified of loss via `WinLossTracker`
4. Loaded ad is rendered in the view or shown as fullscreen
5. Ad lifecycle events (displayed, clicked, hidden, etc.) are tracked via `tracker/`

### Adapter System
- Each ad network has an adapter module (e.g., `:adapter-cloudx`, `:adapter-meta`)
- Adapters implement SDK-defined interfaces from `sdk/internal/adapter/`:
  - `CloudXAdViewAdapter` - For banner/MREC ads
  - `CloudXInterstitialAdapter` - For interstitial ads
  - `CloudXRewardedInterstitialAdapter` - For rewarded ads
  - `CloudXAdapterInitializer` - For adapter initialization
- Adapters are instantiated dynamically via factories in response to bid responses
- Each adapter wraps the native ad SDK and translates events to CloudX callbacks

### Privacy & Compliance
- SDK supports GDPR, CCPA, COPPA via `CloudX.setPrivacy()` → `CloudXPrivacy.kt`
- IAB TCF strings are read from `SharedPreferences` (IABTCF_TCString, IABUSPrivacy_String, IABGPP_HDR_GppString)
- Privacy signals are included in bid requests → `sdk/internal/bid/BidRequestProviderImpl.kt`
- Privacy service manages consent state → `sdk/internal/privacy/PrivacyService.kt`
- **SharedPreferences for IAB Compliance:**
  - Privacy providers (`TCFProviderImpl`, `USPrivacyProviderImpl`, `GPPProviderImpl`) use dependency injection
  - Factory methods use `Context.createIabSharedPreferences()` from `sdk/internal/util/Utils.kt`
  - Creates SharedPreferences with name: `"${packageName}_preferences"` (IAB standard location)
  - This avoids namespace collisions with host app and follows IAB CMP guidelines

### Tracking & Analytics
- **Win/Loss Tracking:** `WinLossTracker` sends win/loss events for bid lifecycle
- **Error Reporting:** `ErrorReportingService` reports SDK errors to backend
- **Metrics:** `MetricsTracker` tracks method calls and usage patterns
- All tracking services are in `sdk/internal/tracker/`

### Database
- Room database is used for persistent storage → `sdk/internal/db/`
- Stores configuration, caching data, and other persistent state

## Key Configuration Files

- **`gradle.properties`** - Project-wide Gradle settings, configuration cache enabled
- **`settings.gradle.kts`** - Module includes, repository configuration, composite build for build-logic
- **`sdk/gradle.properties`** - SDK-specific properties (if exists)
- **`keystore.properties`** - Keystore configuration for signing (gitignored, used by `:app`)
- **`local.properties`** - Local SDK paths (gitignored)

## Dependencies

- **Kotlin:** 1.9.22 with Kotlin JVM target 1.8
- **Kotlinx Coroutines:** 1.7.3 (for async operations throughout SDK)
- **Ktor:** 2.3.8 (HTTP client using Android engine) → `sdk/internal/httpclient/`
- **Room:** Database ORM → `sdk/internal/db/`
- **Google Advertising ID:** For GAID access → `sdk/internal/gaid/`
- **Lifecycle Process:** For app lifecycle tracking → `sdk/internal/startup/`

## Testing

### Unit Tests
- Located in `sdk/src/test/java/io/cloudx/sdk/`
- Test framework: JUnit with MockK (pure unit tests, no Robolectric)
- **All tests must extend `CXTest`** - Base class that:
  - Disables logging (`CXLogger.isEnabled = false`) to prevent unmocked `android.util.Log` calls
  - Applies MockKRule for proper MockK setup
  - Provides consistent test setup across the codebase
- Key test files:
  - `CXTest.kt` - Base test class (all tests must extend this)
  - `internal/ads/AdLoaderTest.kt` - Ad loading logic tests
  - `internal/privacy/TCFProviderImplTest.kt` - IAB TCF privacy tests
  - `internal/privacy/USPrivacyProviderImplTest.kt` - CCPA privacy tests

### Running Tests
```bash
# All tests
./gradlew test

# SDK tests only
./gradlew :sdk:test

# Specific test class (note: no --tests flag with gradle wrapper)
./gradlew :sdk:testDebugUnitTest

# With debug output
./gradlew :sdk:test --info
```

### Test Dependencies
- **junit** - JUnit 4 test framework
- **mockk** - Mocking library for Kotlin
- **truth** - Assertion library
- **kotlinx-coroutines-test** - Testing utilities for coroutines
- **ktor-client-mock** - Mock HTTP client for testing
- **slf4j-nop** - No-op SLF4J implementation (silences Ktor logging warnings)

## Code Patterns & Conventions

### Logging
- Use `CXLogger.forComponent("ComponentName")` or `CXLogger.forPlacement("Component", placementName)`
- Log levels: `d()` debug, `i()` info, `w()` warning, `e()` error
- Logging is controlled via `CloudX.setLoggingEnabled()` and `CloudX.setMinLogLevel()`

### Error Handling
- Use `Result<T, CloudXError>` for operations that can fail
- Convert exceptions to `CloudXError` via `Exception.toCloudXError()`
- Use `CloudXErrorCode` enum for standardized error codes
- Report errors via `ErrorReportingService` for non-user-facing failures

### Coroutines
- SDK uses coroutines extensively for async operations
- `MainScope()` for UI-related operations
- `withTimeout()` for ad loading with timeouts
- Always handle `CancellationException` properly (don't catch and swallow)
- Use `ensureActive()` in loops to respect cancellation

### Thread Safety
- Ad creation and initialization callbacks run on main thread (enforced via `ThreadUtils.GlobalMainScope`)
- Internal operations use coroutines with proper dispatchers
- State management uses `StateFlow` for reactive state

### Dependency Injection
- Use constructor injection for testability (especially for Android framework dependencies)
- Provide companion object factory methods for production use
- Example pattern (from privacy providers):
  ```kotlin
  internal class TCFProviderImpl(
      private val sharedPrefs: SharedPreferences  // Injected for testing
  ) : TCFProvider {
      companion object {
          fun create(context: Context): TCFProviderImpl {
              return TCFProviderImpl(context.createIabSharedPreferences())
          }
      }
  }
  ```
- This allows easy mocking in tests while maintaining clean production code

### Naming Conventions
- Public API: `CloudX` prefix (e.g., `CloudXAdView`, `CloudXError`)
- Internal classes: `CX` prefix (e.g., `CXSdk`, `CXLogger`, `CXInterstitialAd`)
- Adapter interfaces: `CloudXAdapter` suffix (e.g., `CloudXAdViewAdapter`)

## Important Notes

- **Module Dependencies:** The `:app` demo module uses local project dependencies (`:sdk`, `:adapter-cloudx`, `:adapter-meta`) during development. For Maven Central releases, these would be remote dependencies.
- **Build Config:** SDK generates BuildConfig with `SDK_VERSION_NAME` via `sdk/build.gradle.kts`
- **ProGuard:** Consumer ProGuard rules are defined in `consumer-rules.pro` for each module
- **Java Compatibility:** SDK is Kotlin-first but provides Java-friendly APIs using `@JvmStatic` and `@JvmOverloads`
- **Gradle Configuration Cache:** Enabled in `gradle.properties` for faster builds
- **Signed Releases:** App module can be signed with keystore defined in `keystore.properties` (gitignored)
