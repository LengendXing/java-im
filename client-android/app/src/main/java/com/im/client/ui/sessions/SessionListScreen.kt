package com.im.client.ui.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.im.client.data.model.Session
import com.im.client.ui.contacts.FriendRequestsScreen
import com.im.client.ui.contacts.FriendRequestsViewModel
import com.im.client.ui.contacts.ContactsTab
import com.im.client.ui.settings.SettingsScreen
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    onSessionClick: (String) -> Unit,
    onNavigateToGroupCreate: () -> Unit = {},
    viewModel: SessionListViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val sessions by viewModel.sessions.collectAsState()
    val selectedTab by viewModel.selectedTab

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Messages") },
                actions = {
                    if (selectedTab == 1) {
                        IconButton(onClick = onNavigateToGroupCreate) {
                            Icon(Icons.Default.Add, contentDescription = "Create Group")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Tab row: Sessions | Requests | Contacts | Me
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { viewModel.selectTab(0) }, text = { Text("Sessions") })
                Tab(selected = selectedTab == 1, onClick = { viewModel.selectTab(1) }, text = { Text("Requests") })
                Tab(selected = selectedTab == 2, onClick = { viewModel.selectTab(2) }, text = { Text("Contacts") })
                Tab(selected = selectedTab == 3, onClick = { viewModel.selectTab(3) }, text = { Text("Me") })
            }

            when (selectedTab) {
                0 -> SessionListContent(
                    sessions = sessions,
                    isRefreshing = viewModel.isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    onSessionClick = onSessionClick
                )
                1 -> FriendRequestsScreen(
                    viewModel = androidx.lifecycle.viewmodel.compose.viewModel<FriendRequestsViewModel>()
                )
                2 -> ContactsTab(onNavigateToGroupCreate = onNavigateToGroupCreate)
                3 -> SettingsScreen(
                    onNavigateBack = { viewModel.selectTab(0) },
                    onLogout = { viewModel.selectTab(0) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListContent(
    sessions: List<Session>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onSessionClick: (String) -> Unit
) {
    if (sessions.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No conversations yet", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Pull to refresh", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(sessions, key = { it.sessionId }) { session ->
                    SessionItem(session = session, onClick = { onSessionClick(session.sessionId) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
fun SessionItem(session: Session, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = session.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = session.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatTime(session.lastMsgTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = session.lastMsg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (session.unreadCount > 0) {
                    BadgedBox(badge = {
                        Badge {
                            Text(
                                text = if (session.unreadCount > 99) "99+" else session.unreadCount.toString(),
                                fontSize = 10.sp
                            )
                        }
                    }) {}
                }
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
