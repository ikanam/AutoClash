package top.jarman.autoclash.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import top.jarman.autoclash.ui.screen.AboutScreen
import top.jarman.autoclash.ui.screen.AppSettingsScreen
import top.jarman.autoclash.ui.screen.ProxyGroupsScreen
import top.jarman.autoclash.ui.screen.RuleEditorScreen
import top.jarman.autoclash.ui.screen.SettingsScreen

object Routes {
    const val HOME_SETTINGS = "home_settings" // The API connection screen
    const val APP_SETTINGS = "app_settings"  // The new settings screen
    const val ABOUT = "about"
    const val PROXY_GROUPS = "proxy_groups"
    const val RULE_EDITOR = "rule_editor/{groupName}"

    fun ruleEditor(groupName: String) = "rule_editor/$groupName"
}

private const val ANIMATION_DURATION = 300

@Composable
fun AppNavigation(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME_SETTINGS,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(ANIMATION_DURATION)
            ) + fadeIn(animationSpec = tween(ANIMATION_DURATION))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -it / 3 },
                animationSpec = tween(ANIMATION_DURATION)
            ) + fadeOut(animationSpec = tween(ANIMATION_DURATION))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -it / 3 },
                animationSpec = tween(ANIMATION_DURATION)
            ) + fadeIn(animationSpec = tween(ANIMATION_DURATION))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(ANIMATION_DURATION)
            ) + fadeOut(animationSpec = tween(ANIMATION_DURATION))
        }
    ) {
        composable(Routes.HOME_SETTINGS) {
            SettingsScreen(
                onNavigateToGroups = {
                    navController.navigate(Routes.PROXY_GROUPS)
                },
                onNavigateToAppSettings = {
                    navController.navigate(Routes.APP_SETTINGS)
                }
            )
        }

        composable(Routes.APP_SETTINGS) {
            AppSettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToAbout = {
                    navController.navigate(Routes.ABOUT)
                }
            )
        }

        composable(Routes.ABOUT) {
            AboutScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.PROXY_GROUPS) {
            ProxyGroupsScreen(
                onNavigateToRuleEditor = { groupName ->
                    navController.navigate(Routes.ruleEditor(groupName))
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Routes.RULE_EDITOR,
            arguments = listOf(navArgument("groupName") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupName = backStackEntry.arguments?.getString("groupName") ?: ""
            RuleEditorScreen(
                groupName = groupName,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
