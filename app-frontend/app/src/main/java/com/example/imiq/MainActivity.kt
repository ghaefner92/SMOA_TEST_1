package com.example.imiq

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.imiq.ui.theme.IMIQTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        TokenManager.init(applicationContext)
        PassportStore.init(applicationContext)
        LanguageState.init()
        setContent {
            val base = if (LanguageState.current == AppLanguage.DE) DeStrings else EnStrings
            IMIQTheme {
                CompositionLocalProvider(LocalStrings provides base) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        var showSplash by remember { mutableStateOf(true) }
                        if (showSplash) SplashScreen(onDone = { showSplash = false })
                        else ImiqApp()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ImiqApp() {
    val startScreen = remember {
        when {
            PassportStore.hasPassport() -> "home"
            BuildConfig.LOCAL_CORE_MODE -> "profile"
            TokenManager.isLoggedIn() -> "profile"
            else -> "signup"
        }
    }

    var currentScreen by remember { mutableStateOf(startScreen) }
    var previousScreen by remember { mutableStateOf("") }
    var tripDestination by remember { mutableStateOf(MobilityReferenceData.destination) }
    var userName by remember { mutableStateOf(TokenManager.getUserName() ?: "") }

    LaunchedEffect(currentScreen) {
        if (currentScreen == "home" && !PassportStore.hasPassport()) {
            previousScreen = "home"
            currentScreen = "profile"
        }
    }

    BackHandler(enabled = currentScreen != "signup" && currentScreen != "home") {
        val target = when (currentScreen) {
            "results", "settings", "passport_summary", "passport_detail", "refine_profile" -> "home"
            "profile" -> when {
                TokenManager.isProfileCompleted() -> "home"
                BuildConfig.LOCAL_CORE_MODE -> "profile"
                else -> "signup"
            }
            else -> currentScreen
        }
        previousScreen = currentScreen
        currentScreen = target
    }

    fun navigateTo(screen: String) {
        previousScreen = currentScreen
        currentScreen = screen
    }

    fun navigateBack() {
        val target = when (currentScreen) {
            "results", "settings", "passport_summary", "passport_detail", "refine_profile" -> "home"
            "profile" -> when {
                TokenManager.isProfileCompleted() -> "home"
                BuildConfig.LOCAL_CORE_MODE -> "profile"
                else -> "signup"
            }
            else -> currentScreen
        }
        previousScreen = currentScreen
        currentScreen = target
    }

    val screenOrder = listOf(
        "signup", "profile", "home", "results", "settings", "refine_profile", "passport_summary", "passport_detail"
    )
    val isForward = screenOrder.indexOf(currentScreen) >= screenOrder.indexOf(previousScreen)

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            if (isForward) {
                (slideInHorizontally(tween(350)) { it / 3 } + fadeIn(tween(350)))
                    .togetherWith(slideOutHorizontally(tween(350)) { -it / 3 } + fadeOut(tween(250)))
            } else {
                (slideInHorizontally(tween(350)) { -it / 3 } + fadeIn(tween(350)))
                    .togetherWith(slideOutHorizontally(tween(350)) { it / 3 } + fadeOut(tween(250)))
            }
        },
        label = "screen_transition",
    ) { screen ->
        when (screen) {
            "signup" -> SignUpScreen(onSignUpComplete = { navigateTo("profile") })

            "profile" -> AdaptiveProfileSetupScreen(
                onProfileComplete = {
                    userName = TokenManager.getUserName() ?: ""
                    navigateTo("home")
                },
                onBackClick = { navigateBack() },
            )

            "home" -> MobilityHomeScreen(
                userName = userName,
                onPlanTrip = { dest ->
                    tripDestination = dest
                    navigateTo("results")
                },
                onOpenProfile = {
                    val current = PassportStore.load()
                    if (current != null && AdaptiveProfileService.candidate(current) != null) {
                        navigateTo("refine_profile")
                    } else {
                        navigateTo("passport_summary")
                    }
                },
                onOpenSettings = { navigateTo("settings") },
            )

            "refine_profile" -> AdaptiveProfileScreen(
                onBack = { navigateBack() },
                onViewPassport = { navigateTo("passport_summary") },
            )

            "passport_summary" -> PassportSummaryScreen(
                onBack = { navigateBack() },
                onAdvanced = { navigateTo("passport_detail") },
            )

            "passport_detail" -> PassportDetailScreen(onBack = { navigateTo("passport_summary") })

            "results" -> RouteResultsScreen(
                destination = tripDestination,
                onBackClick = { navigateBack() },
            )

            "settings" -> SettingsScreen(
                onRecreateProfile = {
                    TokenManager.clearProfile()
                    PassportStore.clearCurrentPreservingHistory()
                    userName = ""
                    navigateTo("profile")
                },
                onBackClick = { navigateBack() },
            )

            else -> if (BuildConfig.LOCAL_CORE_MODE) {
                AdaptiveProfileSetupScreen(
                    onProfileComplete = {
                        userName = TokenManager.getUserName() ?: ""
                        navigateTo("home")
                    },
                    onBackClick = { navigateBack() },
                )
            } else {
                SignUpScreen(onSignUpComplete = { navigateTo("profile") })
            }
        }
    }
}
