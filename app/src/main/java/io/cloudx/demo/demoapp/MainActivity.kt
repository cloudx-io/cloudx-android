package io.cloudx.demo.demoapp

import android.os.Bundle
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
import io.cloudx.sdk.CloudX
import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.CloudXInitializationListener
import io.cloudx.sdk.CloudXPrivacy
import io.cloudx.sdk.internal.CXLogger
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(R.layout.activity_main) {

    private val initState by CloudXInitializer::initState

    private lateinit var toolbar: Toolbar
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var bottomNavBar: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        toolbar = findViewById(R.id.toolbar)

        setSupportActionBar(toolbar)

        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "Unknown"
        }
        supportActionBar?.subtitle = "Demo App v$versionName"

        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            view.setPadding(
                view.paddingLeft,
                insets.getInsets(WindowInsetsCompat.Type.systemBars()).top,
                view.paddingRight,
                view.paddingBottom
            )
            insets
        }

        progressBar = findViewById(R.id.progress)

        bottomNavBar = findViewById(R.id.bottom_nav)
        bottomNavBar.setup()

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

        // Logging and Meta test mode are now handled in DemoApplication
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

    private fun initializeCloudX() {
        lifecycleScope.launch {
            val settings = settings()

            CXLogger.i(
                TAG,
                "🚀 Starting SDK init with appKey: ${settings.appKey}, endpoint: ${settings.initUrl}"
            )

            val postInit = { msg: String ->
                CXLogger.i(TAG, msg)
                shortSnackbar(bottomNavBar, msg)
            }
            CloudXInitializer.initializeCloudX(
                context = this@MainActivity,
                settings = settings,
                logTag = TAG,
                listener = object : CloudXInitializationListener {
                    override fun onInitialized() {
                        postInit(INIT_SUCCESS)
                    }

                    override fun onInitializationFailed(cloudXError: CloudXError) {
                        postInit("$INIT_FAILURE ${cloudXError.effectiveMessage}")
                    }
                })
        }
    }

    private fun stopCloudX() {
        CloudX.deinitialize()
        CloudX.setHashedUserId("")
        CloudX.setPrivacy(CloudXPrivacy())

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