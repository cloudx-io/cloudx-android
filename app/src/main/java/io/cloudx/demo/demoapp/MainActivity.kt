package io.cloudx.demo.demoapp

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.iterator
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.progressindicator.LinearProgressIndicator
import io.cloudx.adapter.meta.enableMetaAudienceNetworkTestMode
import io.cloudx.demo.demoapp.dynamic.ConfigurationManager
import io.cloudx.demo.demoapp.dynamic.normalizeAndHash
import io.cloudx.sdk.CloudX
import io.cloudx.sdk.CloudXPrivacy
import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.testing.SdkEnvironment
import io.cloudx.sdk.testing.SdkOverridesProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(R.layout.activity_main),
    SettingsFragment.ConfigUpdateCallback {

    private val initState by CloudXInitializer::initState

    private lateinit var configurationManager: ConfigurationManager

    private lateinit var toolbar: Toolbar
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var bottomNavBar: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        toolbar = findViewById(R.id.toolbar)

        setSupportActionBar(toolbar)

        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            view.setPadding(
                view.paddingLeft,
                insets.getInsets(WindowInsetsCompat.Type.systemBars()).top,
                view.paddingRight,
                view.paddingBottom
            )
            insets
        }

        configurationManager = ConfigurationManager(this)
        lifecycleScope.launch(Dispatchers.IO) {
        }

        progressBar = findViewById(R.id.progress)

        bottomNavBar = findViewById(R.id.bottom_nav)
        bottomNavBar.setup()

        SdkEnvironment.overridesProvider = object : SdkOverridesProvider {
            override fun getBundleOverride(): String? {
                return configurationManager.getCurrentConfig()?.SDKConfiguration?.bundle
            }

            override fun getIFAOverride(): String? {
                return configurationManager.getCurrentConfig()?.SDKConfiguration?.ifa
            }
        }

        // UI update for SDK init state.
        repeatOnStart {
            initState.collectLatest {
                supportInvalidateOptionsMenu()

                if (it == InitializationState.InProgress) {
                    progressBar.show()
                } else {
                    progressBar.hide()
                }
            }
        }

        // Enforce logging for the demo app regardless of build variant.
        CloudXLogger.logEnabled = true

        // TODO. Move to settings screen?
        enableMetaAudienceNetworkTestMode(true)
    }

    private fun BottomNavigationView.setup() {
        with(this) {
            setOnItemSelectedListener {

                val settings = settings()

                val tag = it.itemId.toString()

                when (it.itemId) {
                    R.id.menu_banner -> {

                        val banners = settings.bannerPlacementNames
                        val mrecs = settings.mrecPlacementNames

                        PlacementTypeSelectorFragment::class to PlacementTypeSelectorFragment.bundleFrom(
                            listOf(
                                PlacementTypeSelectorFragment.Companion.PlacementItem(
                                    getString(R.string.banner_standard),
                                    StandardBannerProgrammaticFragment::class.java,
                                    BannerProgrammaticFragment.createArgs(
                                        banners,
                                        logTag = "StandardBannerFragment",
                                    )
                                ),
                                PlacementTypeSelectorFragment.Companion.PlacementItem(
                                    getString(R.string.mrec),
                                    MRECProgrammaticFragment::class.java,
                                    BannerProgrammaticFragment.createArgs(
                                        mrecs,
                                        logTag = "MRECFragment",
                                    )
                                )
                            )
                        )
                    }

                    R.id.menu_native -> {
                        val nativeSmall = settings.nativeSmallPlacementNames
                        val nativeMedium = settings.nativeMediumPlacementNames

                        PlacementTypeSelectorFragment::class to PlacementTypeSelectorFragment.bundleFrom(
                            listOf(
                                PlacementTypeSelectorFragment.Companion.PlacementItem(
                                    getString(R.string.small),
                                    NativeAdSmallProgrammaticFragment::class.java,
                                    BannerProgrammaticFragment.createArgs(
                                        placements = nativeSmall,
                                        logTag = "NativeAdSmallFragment"
                                    )
                                ),
                                PlacementTypeSelectorFragment.Companion.PlacementItem(
                                    getString(R.string.medium),
                                    NativeAdMediumProgrammaticFragment::class.java,
                                    BannerProgrammaticFragment.createArgs(
                                        placements = nativeMedium,
                                        logTag = "NativeAdMediumFragment"
                                    )
                                )
                            )
                        )
                    }

                    R.id.menu_interstitial -> {

                        val interstitial = settings.interstitialPlacementNames

                        InterstitialFragment::class to FullPageAdFragment.createArgs(
                            interstitial
                        )
                    }

                    R.id.menu_rewarded -> {
                        val rewarded = settings.rewardedPlacementNames

                        RewardedFragment::class to FullPageAdFragment.createArgs(
                            rewarded
                        )
                    }

                    R.id.menu_settings -> SettingsFragment::class to Bundle()
                    else -> null
                }?.let { (fragmentClass, bundle) ->
                    val isFragmentAlreadyOnScreen =
                        supportFragmentManager.findFragmentByTag(tag) != null
                    if (isFragmentAlreadyOnScreen) {
                        return@let
                    }

                    supportFragmentManager.commit {
                        setCustomAnimations(
                            com.google.android.material.R.anim.abc_fade_in,
                            com.google.android.material.R.anim.abc_fade_out
                        )
                        replace(R.id.fragment_container, fragmentClass.java, bundle, tag)
                    }
                }
                // Respond to all navigation item clicks
                true
            }

            selectedItemId = R.id.menu_settings
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {

        menuInflater.inflate(R.menu.menu, menu)

        menu?.let {
            for (menuItem in it) {
                menuItem.isVisible = when (initState.value) {
                    InitializationState.NotInitialized ->
                        menuItem.itemId == R.id.menu_init

                    InitializationState.InProgress ->
                        menuItem.itemId == R.id.menu_init_in_progress

                    InitializationState.FailedToInitialize ->
                        menuItem.itemId == R.id.menu_init_retry

                    InitializationState.Initialized ->
                        menuItem.itemId == R.id.menu_init_success || menuItem.itemId == R.id.menu_stop
                }
            }
        }

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.menu_init, R.id.menu_init_retry -> {
            initializeCloudX()
            true
        }

        R.id.menu_stop -> {
            stopCloudX()
            true
        }

        else -> {
            // The user's action isn't recognized.
            // Invoke the superclass to handle it.
            super.onOptionsItemSelected(item)
        }
    }

    override fun onAppKeyChanged(newAppKey: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val success = configurationManager.fetchAndApplyRemoteConfig(appKey = newAppKey)
            if (success) {
                runOnUiThread {
                    shortSnackbar(bottomNavBar, "✅ Config loaded for $newAppKey")

                    val currentFrag =
                        supportFragmentManager.findFragmentById(R.id.fragment_container)
                    if (currentFrag is SettingsFragment) {
                        currentFrag.updateInitUrl()
                        currentFrag.updateUserEmail()
                        currentFrag.updatePlacementsFromPreferences()
                    }
                }
            } else {
                runOnUiThread {
                    shortSnackbar(
                        bottomNavBar,
                        "⚠️ No config found for $newAppKey, using existing settings"
                    )
                }
            }
        }
    }

    private fun initializeCloudX() {
        lifecycleScope.launch {
            val activity = this@MainActivity
            val settings = settings()

            CloudXLogger.info(
                TAG,
                "🚀 Starting SDK init with appKey: ${settings.appKey}, endpoint: ${settings.initUrl}"
            )

            val userInfoConfig = configurationManager.getCurrentConfig()?.SDKConfiguration?.userInfo
            val finalHashedEmail = userInfoConfig?.let { info ->
                when {
                    !info.userEmailHashed.isNullOrBlank() -> {
                        CloudXLogger.info(TAG, "📧 Using pre-hashed email from config")
                        info.userEmailHashed
                    }

                    !info.userEmail.isNullOrBlank() -> {
                        val algo = info.hashAlgo?.lowercase() ?: "sha256"
                        val hashed = normalizeAndHash(info.userEmail, algo)
                        CloudXLogger.info(TAG, "📧 Normalized and hashed email using $algo")
                        hashed
                    }

                    else -> null
                }
            }

            val emailInjectionDelayMS = userInfoConfig?.userIdRegisteredAtMS ?: 0L
            val hashedEmailForInit = if (emailInjectionDelayMS == 0L) finalHashedEmail else null

            // Log email registration strategy
            when {
                finalHashedEmail == null -> CloudXLogger.info(
                    TAG,
                    "📧 Hashed Email → No email to register"
                )

                emailInjectionDelayMS == 0L -> CloudXLogger.info(
                    TAG,
                    "📧 Hashed Email → Init-time registration"
                )

                else -> CloudXLogger.info(
                    TAG,
                    "📧 Hashed Email → Delayed registration in ${emailInjectionDelayMS / 1000}s"
                )
            }

            CloudXInitializer.initializeCloudX(
                activity,
                settings,
                hashedEmailForInit,
                TAG
            ) { result ->
                val resultMsg =
                    if (result.initialized) INIT_SUCCESS else "$INIT_FAILURE ${result.description}"

                if (result.initialized) {
                    CloudXLogger.info(TAG, resultMsg)

                    if (!finalHashedEmail.isNullOrBlank() && emailInjectionDelayMS > 0L) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            CloudX.setHashedUserId(finalHashedEmail)
                            CloudXLogger.info(TAG, "📧 Hashed Email → Registered after delay")
                            println("hop: 📧 hashed user ID injected after delay: $finalHashedEmail")
                        }, emailInjectionDelayMS)
                    }

                } else {
                    CloudXLogger.error(TAG, resultMsg)
                }

                shortSnackbar(bottomNavBar, resultMsg)
            }
        }
    }

    private fun stopCloudX() {
        CloudX.deinitialize()
        CloudX.setHashedUserId("")
        CloudX.setPrivacy(CloudXPrivacy())
        CloudX.setTargeting(null)

        CloudXInitializer.reset()

        bottomNavBar.selectedItemId = R.id.menu_settings
        shortSnackbar(bottomNavBar, "CloudX SDK stopped!")
    }

    companion object {

        val TAG = "MainActivity"
    }
}

const val INIT_SUCCESS = "Init success!"
const val INIT_FAILURE = "Init failure:"