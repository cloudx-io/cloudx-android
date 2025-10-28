# CloudX Android SDK

一个强大的 Android SDK，通过在多个广告网络之间进行智能广告中介来最大化广告收入。CloudX SDK 帮助开发者高效管理和优化他们的广告库存，确保获得最高的收益回报。

[![Maven Central](https://img.shields.io/maven-central/v/io.cloudx/sdk)](https://central.sonatype.com/artifact/io.cloudx/sdk)

## 功能特性

- **多种广告格式**：Banner（横幅）、Interstitial（插页）和 MREC 广告
- **智能中介**：跨多个广告网络的自动优化
- **实时竞价**：先进的竞价技术实现最大化收入
- **全面的分析功能**：详细的报告和性能指标
- **易于集成**：简单的 API 和全面的监听器回调
- **隐私合规**：内置 GDPR、CCPA 和 COPPA 支持
- **收入透明度**：发布商收入报告用于优化

## 系统要求

- **Android**：API 21 (Android 5.0) 或更高版本
- **Target SDK**：建议使用 API 35
- **Kotlin**：1.9.0+（使用 1.9.22 构建）
- **Java**：兼容 Java 8+ 项目
- **Gradle**：8.0+ 配合 Android Gradle Plugin 8.0+

### 依赖项

CloudX SDK 使用以下可能影响兼容性的关键依赖项：

- **Ktor**：2.3.8（使用 Android 引擎的 HTTP 客户端库）
- **Kotlinx Coroutines**：1.7.3

**版本兼容性说明：** 如果您的应用使用不同版本的 Ktor 或 Kotlinx Coroutines，Gradle 通常会解析为更高的版本。虽然我们已经测试了基本兼容性，但如果您遇到版本冲突或运行时问题，请[报告问题](https://github.com/cloudx-io/cloudx-android/issues)。

### 所需权限

在您的 `AndroidManifest.xml` 中添加这些权限：

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.READ_BASIC_PHONE_STATE" />
<uses-permission android:name="com.google.android.gms.permission.AD_ID" />
```

## 安装

### Gradle（推荐）

1. 在您的 `settings.gradle` 或 `settings.gradle.kts` 中添加 Maven Central：

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
```

2. 在您应用的 `build.gradle` 或 `build.gradle.kts` 中添加 CloudX SDK：

```kotlin
dependencies {
    // CloudX Core SDK
    implementation("io.cloudx:sdk:0.5.0")

    // 可选：CloudX Adapters（按需添加）
    implementation("io.cloudx:adapter-cloudx:0.5.0")
    implementation("io.cloudx:adapter-meta:0.5.0")
}
```

3. 在 Android Studio 中同步您的项目。

## 快速开始

### 1. 初始化 SDK

**Kotlin：**
```kotlin
// 使用 app key 初始化
CloudX.initialize(
    initParams = CloudXInitializationParams(
        appKey = "your-app-key-here"
    ),
    listener = object : CloudXInitializationListener {
        override fun onInitialized() {
            Log.d("CloudX", "✅ CloudX SDK 初始化成功")
        }

        override fun onInitializationFailed(cloudXError: CloudXError) {
            Log.e("CloudX", "❌ CloudX SDK 初始化失败: ${cloudXError.effectiveMessage}")
        }
    }
)
```

**Java：**
```java
// 使用 app key 初始化
CloudX.initialize(
    new CloudXInitializationParams("your-app-key-here"),
    new CloudXInitializationListener() {
        @Override
        public void onInitialized() {
            Log.d("CloudX", "✅ CloudX SDK 初始化成功");
        }

        @Override
        public void onInitializationFailed(CloudXError cloudXError) {
            Log.e("CloudX", "❌ CloudX SDK 初始化失败: " + cloudXError.getEffectiveMessage());
        }
    }
);
```

### 2. 启用调试日志（可选）

**Kotlin：**
```kotlin
// 启用调试日志用于故障排查
CloudX.setLoggingEnabled(true)
CloudX.setMinLogLevel(CloudXLogLevel.DEBUG)
```

**Java：**
```java
// 启用调试日志用于故障排查
CloudX.setLoggingEnabled(true);
CloudX.setMinLogLevel(CloudXLogLevel.DEBUG);
```

## 广告集成

### Banner 广告

Banner 广告是出现在屏幕顶部或底部的矩形广告。

**Kotlin：**
```kotlin
class MainActivity : AppCompatActivity(), CloudXAdViewListener {
    private lateinit var bannerAd: CloudXAdView

    private fun createBannerAd() {
        // 创建 banner 广告
        bannerAd = CloudX.createBanner("your-banner-placement-name")

        // 设置监听器
        bannerAd.listener = this

        // 添加到视图层级
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.gravity = Gravity.CENTER_HORIZONTAL

        // 添加到容器视图
        findViewById<LinearLayout>(R.id.banner_container).addView(bannerAd, layoutParams)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        bannerAd.destroy()
    }
    
    // CloudXAdViewListener 回调
    override fun onAdLoaded(cloudXAd: CloudXAd) {
        Log.d("CloudX", "✅ Banner 广告加载成功，来自 ${cloudXAd.bidderName}")
    }
    
    override fun onAdLoadFailed(cloudXError: CloudXError) {
        Log.e("CloudX", "❌ Banner 广告加载失败: ${cloudXError.effectiveMessage}")
    }
    
    override fun onAdDisplayed(cloudXAd: CloudXAd) {
        Log.d("CloudX", "👀 Banner 广告展示，来自 ${cloudXAd.bidderName}")
    }

    override fun onAdClicked(cloudXAd: CloudXAd) {
        Log.d("CloudX", "👆 Banner 广告点击，来自 ${cloudXAd.bidderName}")
    }

    override fun onAdHidden(cloudXAd: CloudXAd) {
        Log.d("CloudX", "🔚 Banner 广告隐藏，来自 ${cloudXAd.bidderName}")
    }
    
    override fun onAdDisplayFailed(cloudXError: CloudXError) {
        Log.e("CloudX", "❌ Banner 广告展示失败: ${cloudXError.effectiveMessage}")
    }
    
    override fun onAdExpanded(cloudXAd: CloudXAd) {
        Log.d("CloudX", "📈 Banner 广告展开，来自 ${cloudXAd.bidderName}")
    }

    override fun onAdCollapsed(cloudXAd: CloudXAd) {
        Log.d("CloudX", "📉 Banner 广告折叠，来自 ${cloudXAd.bidderName}")
    }
}
```

**Java：**
```java
public class MainActivity extends AppCompatActivity implements CloudXAdViewListener {
    private CloudXAdView bannerAd;

    private void createBannerAd() {
        // 创建 banner 广告
        bannerAd = CloudX.createBanner("your-banner-placement-name");

        // 设置监听器
        bannerAd.setListener(this);

        // 添加到视图层级
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        layoutParams.gravity = Gravity.CENTER_HORIZONTAL;

        // 添加到容器视图
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
    
    // CloudXAdViewListener 回调
    @Override
    public void onAdLoaded(CloudXAd cloudXAd) {
        Log.d("CloudX", "✅ Banner 广告加载成功，来自 " + cloudXAd.getBidderName());
    }
    
    @Override
    public void onAdLoadFailed(CloudXError cloudXError) {
        Log.e("CloudX", "❌ Banner 广告加载失败: " + cloudXError.getEffectiveMessage());
    }
    
    @Override
    public void onAdDisplayed(CloudXAd cloudXAd) {
        Log.d("CloudX", "👀 Banner 广告展示，来自 " + cloudXAd.getBidderName());
    }

    @Override
    public void onAdClicked(CloudXAd cloudXAd) {
        Log.d("CloudX", "👆 Banner 广告点击，来自 " + cloudXAd.getBidderName());
    }

    @Override
    public void onAdHidden(CloudXAd cloudXAd) {
        Log.d("CloudX", "🔚 Banner 广告隐藏，来自 " + cloudXAd.getBidderName());
    }
    
    @Override
    public void onAdDisplayFailed(CloudXError cloudXError) {
        Log.e("CloudX", "❌ Banner 广告展示失败: " + cloudXError.getEffectiveMessage());
    }
    
    @Override
    public void onAdExpanded(CloudXAd cloudXAd) {
        Log.d("CloudX", "📈 Banner 广告展开，来自 " + cloudXAd.getBidderName());
    }

    @Override
    public void onAdCollapsed(CloudXAd cloudXAd) {
        Log.d("CloudX", "📉 Banner 广告折叠，来自 " + cloudXAd.getBidderName());
    }
}
```

### Interstitial 广告

Interstitial 广告是在应用内容之间出现的全屏广告。

**Kotlin：**
```kotlin
class MainActivity : AppCompatActivity(), CloudXInterstitialListener {
    private lateinit var interstitialAd: CloudXInterstitialAd

    private fun createInterstitialAd() {
        // 创建插页广告
        interstitialAd = CloudX.createInterstitial("your-interstitial-placement-name")

        // 设置监听器
        interstitialAd.listener = this

        // 加载广告
        interstitialAd.load()
    }
    
    private fun showInterstitialAd() {
        if (interstitialAd.isAdReady) {
            interstitialAd.show()
        } else {
            Log.w("CloudX", "插页广告尚未准备就绪")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        interstitialAd.destroy()
    }
    
    // CloudXInterstitialListener 回调
    override fun onAdLoaded(cloudXAd: CloudXAd) {
        Log.d("CloudX", "✅ 插页广告加载成功，来自 ${cloudXAd.bidderName}")
    }
    
    override fun onAdLoadFailed(cloudXError: CloudXError) {
        Log.e("CloudX", "❌ 插页广告加载失败: ${cloudXError.effectiveMessage}")
    }
    
    override fun onAdDisplayed(cloudXAd: CloudXAd) {
        Log.d("CloudX", "👀 插页广告展示，来自 ${cloudXAd.bidderName}")
    }

    override fun onAdDisplayFailed(cloudXError: CloudXError) {
        Log.e("CloudX", "❌ 插页广告展示失败: ${cloudXError.effectiveMessage}")
    }

    override fun onAdHidden(cloudXAd: CloudXAd) {
        Log.d("CloudX", "🔚 插页广告隐藏，来自 ${cloudXAd.bidderName}")
        // 为下次使用重新加载
        createInterstitialAd()
    }

    override fun onAdClicked(cloudXAd: CloudXAd) {
        Log.d("CloudX", "👆 插页广告点击，来自 ${cloudXAd.bidderName}")
    }
}
```

**Java：**
```java
public class MainActivity extends AppCompatActivity implements CloudXInterstitialListener {
    private CloudXInterstitialAd interstitialAd;

    private void createInterstitialAd() {
        // 创建插页广告
        interstitialAd = CloudX.createInterstitial("your-interstitial-placement-name");

        // 设置监听器
        interstitialAd.setListener(this);

        // 加载广告
        interstitialAd.load();
    }
    
    private void showInterstitialAd() {
        if (interstitialAd.getIsAdReady()) {
            interstitialAd.show();
        } else {
            Log.w("CloudX", "插页广告尚未准备就绪");
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        interstitialAd.destroy();
    }
    
    // CloudXInterstitialListener 回调
    @Override
    public void onAdLoaded(CloudXAd cloudXAd) {
        Log.d("CloudX", "✅ 插页广告加载成功，来自 " + cloudXAd.getBidderName());
    }
    
    @Override
    public void onAdLoadFailed(CloudXError cloudXError) {
        Log.e("CloudX", "❌ 插页广告加载失败: " + cloudXError.getEffectiveMessage());
    }
    
    @Override
    public void onAdDisplayed(CloudXAd cloudXAd) {
        Log.d("CloudX", "👀 插页广告展示，来自 " + cloudXAd.getBidderName());
    }

    @Override
    public void onAdDisplayFailed(CloudXError cloudXError) {
        Log.e("CloudX", "❌ 插页广告展示失败: " + cloudXError.getEffectiveMessage());
    }

    @Override
    public void onAdHidden(CloudXAd cloudXAd) {
        Log.d("CloudX", "🔚 插页广告隐藏，来自 " + cloudXAd.getBidderName());
        // 为下次使用重新加载
        createInterstitialAd();
    }

    @Override
    public void onAdClicked(CloudXAd cloudXAd) {
        Log.d("CloudX", "👆 插页广告点击，来自 " + cloudXAd.getBidderName());
    }
}
```

## 高级功能

### 隐私合规与 GDPR 集成

CloudX SDK 支持 GDPR、CCPA 和 COPPA 法规的隐私合规。发布商负责通过其同意管理平台（CMP）获取同意，并向我们的 SDK 提供隐私信号。

**Kotlin：**
```kotlin
// 设置隐私偏好
CloudX.setPrivacy(
    CloudXPrivacy(
        isUserConsent = true,        // GDPR 同意
        isAgeRestrictedUser = false, // COPPA 合规
    )
)

// 对于 IAB TCF 合规，在 SharedPreferences 中设置值
val prefs = PreferenceManager.getDefaultSharedPreferences(this)
prefs.edit().apply {
    // GDPR TCF String
    putString("IABTCF_TCString", "CPcABcABcABcA...")
    putInt("IABTCF_gdprApplies", 1) // 1 = 适用, 0 = 不适用
    
    // CCPA Privacy String
    putString("IABUSPrivacy_String", "1YNN")
    
    // GPP String (Global Privacy Platform)
    putString("IABGPP_HDR_GppString", "DBACNYA~CPXxRfAPXxRfAAfKABENB...")
    putString("IABGPP_GppSID", "7_8")
    
    apply()
}
```

**Java：**
```java
// 设置隐私偏好
CloudX.setPrivacy(new CloudXPrivacy(
    true,  // isUserConsent (GDPR)
    false // isAgeRestrictedUser (COPPA)
));

// 对于 IAB TCF 合规，在 SharedPreferences 中设置值
SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
SharedPreferences.Editor editor = prefs.edit();

// GDPR TCF String
editor.putString("IABTCF_TCString", "CPcABcABcABcA...");
editor.putInt("IABTCF_gdprApplies", 1); // 1 = 适用, 0 = 不适用

// CCPA Privacy String
editor.putString("IABUSPrivacy_String", "1YNN");

// GPP String (Global Privacy Platform)
editor.putString("IABGPP_HDR_GppString", "DBACNYA~CPXxRfAPXxRfAAfKABENB...");
editor.putString("IABGPP_GppSID", "7_8");

editor.apply();
```

#### 隐私密钥参考

| 密钥 | 类型 | 描述 |
|-----|------|-------------|
| `IABTCF_TCString` | String | 来自您的 CMP 的 GDPR TC String |
| `IABTCF_gdprApplies` | Integer | GDPR 是否适用（1 = 是，0 = 否） |
| `IABUSPrivacy_String` | String | CCPA 隐私字符串（例如 "1YNN"） |
| `IABGPP_HDR_GppString` | String | Global Privacy Platform 字符串 |
| `IABGPP_GppSID` | String | GPP Section ID |

### 用户定向

**Kotlin：**
```kotlin
// 设置用于定向的哈希用户 ID
CloudX.setHashedUserId("hashed-user-id")

// 设置自定义用户键值对
CloudX.setUserKeyValue("age", "25")
CloudX.setUserKeyValue("gender", "male")
CloudX.setUserKeyValue("location", "US")

// 设置自定义应用键值对
CloudX.setAppKeyValue("app_version", "1.0.0")
CloudX.setAppKeyValue("user_level", "premium")

// 清除所有自定义键值
CloudX.clearAllKeyValues()
```

**Java：**
```java
// 设置用于定向的哈希用户 ID
CloudX.setHashedUserId("hashed-user-id");

// 设置自定义用户键值对
CloudX.setUserKeyValue("age", "25");
CloudX.setUserKeyValue("gender", "male");
CloudX.setUserKeyValue("location", "US");

// 设置自定义应用键值对
CloudX.setAppKeyValue("app_version", "1.0.0");
CloudX.setAppKeyValue("user_level", "premium");

// 清除所有自定义键值
CloudX.clearAllKeyValues();
```

### 测试模式配置

为支持的广告网络启用测试模式：

**Kotlin：**
```kotlin
// 启用 Meta Audience Network 测试模式
import io.cloudx.adapter.meta.enableMetaAudienceNetworkTestMode
enableMetaAudienceNetworkTestMode(true)
```

**Java：**
```java
// 启用 Meta Audience Network 测试模式
import static io.cloudx.adapter.meta.MetaEnableTestModeKt.enableMetaAudienceNetworkTestMode;
enableMetaAudienceNetworkTestMode(true);
```

## API 参考

### 核心方法

| 方法 | 描述 |
|--------|-------------|
| `CloudX.initialize(params, listener)` | 使用参数和监听器初始化 SDK |
| `CloudX.setPrivacy(privacy)` | 设置隐私偏好 |
| `CloudX.setLoggingEnabled(enabled)` | 启用/禁用 SDK 日志 |
| `CloudX.setMinLogLevel(level)` | 设置最小日志级别 |

### 广告创建方法

| 方法 | 描述 |
|--------|-------------|
| `CloudX.createBanner(placement)` | 创建 banner 广告 (320x50) |
| `CloudX.createMREC(placement)` | 创建 MREC 广告 (300x250) |
| `CloudX.createInterstitial(placement)` | 创建插页广告 |

### 用户定向方法

| 方法 | 描述 |
|--------|-------------|
| `CloudX.setHashedUserId(hashedId)` | 设置哈希用户 ID |
| `CloudX.setUserKeyValue(key, value)` | 设置用户键值对 |
| `CloudX.setAppKeyValue(key, value)` | 设置应用键值对 |
| `CloudX.clearAllKeyValues()` | 清除所有自定义键值 |

### 广告控制方法

| 方法 | 描述 |
|--------|-------------|
| `load()` | 加载广告内容（仅限插页广告） |
| `show()` | 展示全屏广告（仅限插页广告） |
| `isAdReady` | 检查全屏广告是否准备就绪 |
| `destroy()` | 销毁广告并释放资源 |
| `listener` | 设置广告事件监听器的属性 |

### 监听器回调

所有广告类型都支持这些通用回调：
- `onAdLoaded(ad)` - 广告加载成功
- `onAdLoadFailed(error)` - 广告加载失败
- `onAdDisplayed(ad)` - 广告已展示
- `onAdDisplayFailed(error)` - 广告展示失败
- `onAdHidden(ad)` - 广告已隐藏
- `onAdClicked(ad)` - 广告被点击

**Banner 广告额外支持：**
- `onAdExpanded(ad)` - 广告已展开
- `onAdCollapsed(ad)` - 广告已折叠

## 故障排查

### 常见问题

1. **SDK 未初始化**
   - 确保在创建广告之前调用 `CloudX.initialize()`
   - 检查 `onInitialized()` 回调是否成功调用

2. **广告未加载**
   - 验证您的 placement 名称是否正确
   - 检查网络连接
   - 确保在真实设备上测试（某些网络不支持模拟器）

3. **监听器方法未调用**
   - 验证您的 activity/fragment 实现了正确的监听器接口
   - 确保在创建广告时设置了监听器

4. **构建错误**
   - 确保使用 Android API 21 或更高版本
   - 验证所有必需的依赖项已包含
   - 检查您使用的 Kotlin/Gradle 版本是否兼容

### 调试日志

启用调试日志以排查问题：

**Kotlin：**
```kotlin
// 启用调试日志用于故障排查
CloudX.setLoggingEnabled(true)
CloudX.setMinLogLevel(CloudXLogLevel.DEBUG)
```

**Java：**
```java
// 启用调试日志用于故障排查
CloudX.setLoggingEnabled(true);
CloudX.setMinLogLevel(CloudXLogLevel.DEBUG);
```

## 支持

- **文档**：[CloudX Android SDK 文档](https://github.com/cloudx-io/cloudx-android)
- **更新日志**：[CHANGELOG.md](./CHANGELOG.md)
- **问题**：[GitHub Issues](https://github.com/cloudx-io/cloudx-android/issues)
- **邮箱**：mobile@cloudx.io

## 许可证

本项目采用 Elastic License 2.0 许可。详情请参阅 [LICENSE](./LICENSE) 文件。