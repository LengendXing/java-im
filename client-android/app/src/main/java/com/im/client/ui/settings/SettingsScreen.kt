package com.im.client.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.im.client.App
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val authRepository = App.instance.authRepository
    val scope = rememberCoroutineScope()
    var isDarkMode by remember { mutableStateOf(true) }
    var nickname by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val user = authRepository.getSavedUser(context)
        if (user != null) {
            nickname = user.nickname
            username = user.username
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Me") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(modifier = Modifier.size(56.dp), shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(nickname.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(nickname, style = MaterialTheme.typography.titleLarge)
                        Text("@$username", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            ListItem(
                headlineContent = { Text("Dark Mode") },
                trailingContent = { Switch(checked = isDarkMode, onCheckedChange = { isDarkMode = it }) }
            )
            HorizontalDivider()

            ListItem(
                headlineContent = { Text("About") },
                supportingContent = { Text("java-im v0.5.0") }
            )
            HorizontalDivider()

            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    scope.launch {
                        authRepository.logout(context)
                        onLogout()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) { Text("Log Out") }
        }
    }
}
