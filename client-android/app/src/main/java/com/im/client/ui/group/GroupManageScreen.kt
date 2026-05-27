package com.im.client.ui.group

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.im.client.App
import com.im.client.data.remote.ApiService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupManageScreen(
    groupId: Long,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val apiService = App.instance.apiService
    val authRepository = App.instance.authRepository
    val scope = rememberCoroutineScope()
    var members by remember { mutableStateOf<List<ApiService.GroupMemberResponse>>(emptyList()) }
    var isOwner by remember { mutableStateOf(false) }

    LaunchedEffect(groupId) {
        val token = authRepository.getToken(context)
        val userId = authRepository.getUserId(context)
        if (token != null) {
            try {
                members = apiService.getGroupMembers(token, groupId)
                isOwner = members.any { it.userId == userId && it.role == 1 }
            } catch (_: Exception) { }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Group Manage") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Members (${members.size})", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(members, key = { it.userId }) { member ->
                    ListItem(
                        headlineContent = { Text(member.nickname.ifBlank { member.username }) },
                        supportingContent = { Text(if (member.role == 1) "Owner" else "Member") },
                        trailingContent = {
                            if (isOwner && member.role != 1) {
                                TextButton(onClick = {
                                    scope.launch {
                                        val token = authRepository.getToken(context)
                                        if (token != null) {
                                            try {
                                                apiService.kickFromGroup(token, groupId, member.userId)
                                                members = apiService.getGroupMembers(token, groupId)
                                            } catch (_: Exception) { }
                                        }
                                    }
                                }) { Text("Kick", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    )
                }
            }

            if (isOwner) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { /* invite */ }, modifier = Modifier.weight(1f)) { Text("Invite") }
                    Button(
                        onClick = {
                            scope.launch {
                                val token = authRepository.getToken(context)
                                if (token != null) {
                                    try { apiService.dissolveGroup(token, groupId); onNavigateBack() }
                                    catch (_: Exception) { }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) { Text("Dissolve") }
                }
            }
        }
    }
}
