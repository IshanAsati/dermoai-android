package com.dermoai.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.dermoai.feature.analytics.AnalyticsPlaceholderScreen
import com.dermoai.feature.auth.OnboardingScreen
import com.dermoai.feature.auth.SessionViewModel
import com.dermoai.feature.auth.SignInScreen
import com.dermoai.feature.auth.SignUpScreen
import com.dermoai.feature.auth.SplashScreen
import com.dermoai.feature.home.HomeDashboardScreen
import com.dermoai.feature.reports.ReportsPlaceholderScreen
import com.dermoai.feature.scan.ScanCaptureScreen
import com.dermoai.feature.scan.ScanEntryScreen
import com.dermoai.feature.scan.ScanResultsScreen
import com.dermoai.feature.scan.ScanReviewScreen
import com.dermoai.feature.settings.SettingsPlaceholderScreen
import com.dermoai.feature.skinnmind.SkinMindScreen
import com.dermoai.feature.timeline.TimelineScreen
import com.dermoai.feature.timeline.TimelineDetailScreen
import com.dermoai.feature.treatment.TreatmentScreen
import com.dermoai.feature.wellness.BreathingScreen
import com.dermoai.feature.wellness.JournalScreen
import com.dermoai.feature.wellness.WellnessHubScreen
import com.dermoai.feature.analytics.AnalyticsScreen
import com.dermoai.feature.faq.FaqScreen
import com.dermoai.feature.finder.FinderScreen
import com.dermoai.feature.reports.ReportScreen
import com.dermoai.feature.settings.SettingsScreen
import com.dermoai.feature.wellness.WellnessPlaceholderScreen

/**
 * Root navigation host with auth guard, typed routes, and 5-tab bottom navigation.
 *
 * Auth routes (Splash → Onboarding → Sign-in/up) have no bottom bar.
 * Tab routes (Home · Timeline · Scan · SkinMind · More) show the bottom bar.
 * Scan capture and More hub sub-routes hide the bottom bar.
 */
@Composable
fun DermoAppRoot(
    sessionViewModel: SessionViewModel = hiltViewModel(),
) {
    val session by sessionViewModel.sessionState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination

    // Determine which tab is active (null = not on a tab → no bottom bar)
    val currentTab: TabRoute? = currentDest?.resolveTabRoute()

    // Auth + onboarding guard
    LaunchedEffect(session.isLoading, session.isOnboarded, session.isAuthenticated) {
        if (session.isLoading) return@LaunchedEffect

        val target: Any = when {
            !session.isOnboarded -> OnboardingRoute
            !session.isAuthenticated -> SignInRoute
            else -> HomeTab
        }

        val dest = navController.currentDestination ?: return@LaunchedEffect
        val destRoute = dest.route
        val targetRoute = target.routeName()

        val isOnTarget = destRoute == targetRoute
        val isOnSignUp = destRoute == SignUpRoute.routeName()

        if (!isOnTarget && !isOnSignUp) {
            navController.navigate(target) {
                popUpTo(navController.graph.findStartDestination().id) {
                    inclusive = true
                }
                launchSingleTop = true
            }
        }

        // After successful auth while on SignIn/SignUp, route home
        if (session.isAuthenticated &&
            (destRoute == SignInRoute.routeName() || destRoute == SignUpRoute.routeName())
        ) {
            navController.navigate(HomeTab) {
                popUpTo(navController.graph.findStartDestination().id) {
                    inclusive = true
                }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (currentTab != null) {
                DermoBottomBar(
                    currentRoute = currentTab,
                    onTabSelected = { tab ->
                        navController.navigate(tab) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { scaffoldPadding ->
        NavHost(
            navController = navController,
            startDestination = SplashRoute,
            modifier = Modifier.fillMaxSize(),
        ) {
            // ── Auth routes (no bottom bar) ──────────────────────────

            composable<SplashRoute> {
                SplashScreen()
            }
            composable<OnboardingRoute> {
                OnboardingScreen(
                    onFinished = {
                        // SessionViewModel observes isOnboarded; guard navigates to SignIn
                    },
                )
            }
            composable<SignInRoute> {
                SignInScreen(
                    onSignedIn = {
                        navController.navigate(HomeTab) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    },
                    onNavigateToSignUp = {
                        navController.navigate(SignUpRoute)
                    },
                )
            }
            composable<SignUpRoute> {
                SignUpScreen(
                    onSignedUp = {
                        navController.navigate(HomeTab) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    },
                    onNavigateToSignIn = {
                        navController.popBackStack()
                    },
                )
            }

            // ── Tab destinations (bottom bar visible) ────────────────

            composable<HomeTab> {
                HomeDashboardScreen(
                    displayName = session.user?.displayName.orEmpty(),
                    userId = session.user?.id.orEmpty(),
                    onNavigateToScan = {
                        navController.navigate(ScanTab) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToSkinMind = {
                        navController.navigate(SkinMindTab) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onScanClick = { scanId ->
                        navController.navigate(TimelineDetailRoute(scanId))
                    },
                    modifier = Modifier.padding(scaffoldPadding),
                )
            }
            composable<TimelineTab> {
                TimelineScreen(
                    userId = session.user?.id.orEmpty(),
                    onScanClick = { scanId ->
                        navController.navigate(TimelineDetailRoute(scanId))
                    },
                    modifier = Modifier.padding(scaffoldPadding),
                )
            }
            composable<ScanTab> {
                ScanEntryScreen(
                    onNavigateToCapture = {
                        navController.navigate(ScanCaptureRoute)
                    },
                    onPhotoPicked = { photoPath ->
                        navController.navigate(ScanReviewRoute(photoPath))
                    },
                    onBack = {
                        navController.navigate(HomeTab) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    modifier = Modifier.padding(scaffoldPadding),
                )
            }
            composable<SkinMindTab> {
                SkinMindScreen(
                    userId = session.user?.id.orEmpty(),
                    modifier = Modifier.padding(scaffoldPadding),
                )
            }
            composable<MoreTab> {
                MoreHubScreen(
                    onNavigate = { route -> navController.navigate(route) },
                    onSignOut = {
                        sessionViewModel.signOut()
                        navController.navigate(SignInRoute) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    },
                    modifier = Modifier.padding(scaffoldPadding),
                )
            }

            // ── Scan sub-routes (bottom bar hidden) ──────────────────

            composable<ScanCaptureRoute> {
                ScanCaptureScreen(
                    onPhotoCaptured = { photoPath ->
                        navController.navigate(ScanReviewRoute(photoPath))
                    },
                    onBack = {
                        navController.popBackStack()
                    },
                )
            }
            composable<ScanReviewRoute> { backStackEntry ->
                val route: ScanReviewRoute = backStackEntry.toRoute()
                ScanReviewScreen(
                    photoPath = route.photoPath,
                    onUsePhoto = { path ->
                        navController.navigate(ScanResultsRoute(path)) {
                            popUpTo<ScanCaptureRoute> { inclusive = true }
                        }
                    },
                    onRetake = {
                        navController.popBackStack()
                    },
                )
            }
            composable<ScanResultsRoute> { backStackEntry ->
                val route: ScanResultsRoute = backStackEntry.toRoute()
                ScanResultsScreen(
                    photoPath = route.photoPath,
                    userId = session.user?.id.orEmpty(),
                    onBack = {
                        navController.navigate(ScanTab) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    },
                    onSavedToTimeline = {
                        navController.navigate(HomeTab) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    },
                    onFindDermatologist = {
                        navController.navigate(FindDermatologistRoute)
                    },
                )
            }

            // ── More hub sub-routes (bottom bar hidden) ──────────────

            composable<TreatmentRoute> {
                TreatmentScreen(userId = session.user?.id.orEmpty())
            }
            composable<WellnessRoute> {
                WellnessHubScreen(
                    userId = session.user?.id.orEmpty(),
                    onBreathing = { navController.navigate(BreathingRoute) },
                    onJournal = { navController.navigate(JournalRoute) },
                    onStreaks = { navController.navigate(SkinMindTab) },
                )
            }
            composable<BreathingRoute> {
                BreathingScreen(onBack = { navController.popBackStack() })
            }
            composable<JournalRoute> {
                JournalScreen(
                    userId = session.user?.id.orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable<AnalyticsRoute> {
                AnalyticsScreen(userId = session.user?.id.orEmpty())
            }
            composable<ReportsRoute> {
                ReportScreen(
                    userId = session.user?.id.orEmpty(),
                    displayName = session.user?.displayName.orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onSignOut = { sessionViewModel.signOut() },
                )
            }
            composable<FaqRoute> {
                FaqScreen(onOpenSettings = { navController.navigate(SettingsRoute) })
            }
            composable<FindDermatologistRoute> {
                FinderScreen()
            }

            // ── Timeline detail ──────────────────────────────────────

            composable<TimelineDetailRoute> { backStackEntry ->
                val route: TimelineDetailRoute = backStackEntry.toRoute()
                TimelineDetailScreen(
                    scanId = route.scanId,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────

/** Resolve the active [TabRoute] from the current [NavDestination]. */
private fun NavDestination.resolveTabRoute(): TabRoute? {
    val route = this.route ?: return null
    return when (route) {
        HomeTab.routeName() -> HomeTab
        TimelineTab.routeName() -> TimelineTab
        ScanTab.routeName() -> ScanTab
        SkinMindTab.routeName() -> SkinMindTab
        MoreTab.routeName() -> MoreTab
        else -> null
    }
}

/** Serial name used as the navigation route string for a [DermoRoutes] object. */
private fun Any.routeName(): String = this::class.qualifiedName ?: ""
