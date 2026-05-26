package com.im.client.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.im.client.ui.chat.ChatScreen
import com.im.client.ui.login.LoginScreen
import com.im.client.ui.register.RegisterScreen
import com.im.client.ui.sessions.SessionListScreen

@Composable
fun ImNavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = { navController.navigate(Routes.SESSIONS) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }},
                onNavigateToRegister = { navController.navigate(Routes.REGISTER) }
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                onRegisterSuccess = { navController.navigate(Routes.SESSIONS) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }},
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SESSIONS) {
            SessionListScreen(
                onSessionClick = { sessionId -> navController.navigate(Routes.chat(sessionId)) }
            )
        }
        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            ChatScreen(
                sessionId = sessionId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
