package com.im.client.ui.contacts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.im.client.App
import com.im.client.data.remote.ApiService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsTab(
    onNavigateToGroupCreate: () -> Unit = {}
) {
    val context = LocalContext.current
    val apiService = App.instance.apiService
    val authRepository = App.instance.authRepository
    val scope = rememberCoroutineScope()
    var friends by remember { mutableStateOf<List<ApiService.FriendListResponse>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ApiService.UserSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var addUserId by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val token = authRepository.getToken(context)
        if (token != null) {
            try { friends = apiService.getFriendList(token) } catch (_: Exception) { }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contacts") },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Friend")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { new ->
                    searchQuery = new
                    scope.launch {
                        if (new.isNotBlank()) {
                            isSearching = true
                            val token = authRepository.getToken(context)
                            if (token != null) {
                                searchResults = try { apiService.searchUsers(token, new) } catch (_: Exception) { emptyList() }
                            }
                            isSearching = false
                        } else {
                            searchResults = emptyList()
                        }
                    }
                },
                label = { Text("Search users") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true
            )

            if (isSearching) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            val displayList = if (searchQuery.isNotBlank()) {
                searchResults.map { Triple(it.userId, it.nickname.ifBlank { it.username }, it.username) }
            } else {
                friends.map { Triple(it.userId, it.nickname.ifBlank { it.username }, it.username) }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(displayList, key = { it.first }) { (id, name, username) ->
                    ListItem(
                        headlineContent = { Text(name) },
                        supportingContent = { Text("ID: $id · $username") },
                        leadingContent = {
                            Surface(modifier = Modifier.size(40.dp), shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.primaryContainer) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Friend") },
            text = {
                OutlinedTextField(value = addUserId, onValueChange = { addUserId = it },
                    label = { Text("User ID") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = addUserId.toLongOrNull()
                    if (id != null) {
                        scope.launch {
                            val token = authRepository.getToken(context)
                            if (token != null) {
                                try {
                                    apiService.applyFriend(token, id)
                                    friends = apiService.getFriendList(token)
                                } catch (_: Exception) { }
                            }
                        }
                    }
                    showAddDialog = false
                    addUserId = ""
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }
}
