package com.im.client.ui.group

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.im.client.data.remote.ApiService
import com.im.client.ui.theme.WeChatGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupCreateScreen(
    onNavigateBack: () -> Unit,
    viewModel: GroupCreateViewModel = viewModel()
) {
    val groupName by viewModel.groupName
    val friends by viewModel.friends.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val isCreating by viewModel.isCreating

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Group") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.createGroup {
                                onNavigateBack()
                            }
                        },
                        enabled = groupName.isNotBlank() && selectedIds.isNotEmpty() && !isCreating
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Create", tint = WeChatGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = groupName,
                onValueChange = viewModel::onGroupNameChange,
                label = { Text("Group Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Select Members",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (friends.isEmpty()) {
                Text("No friends available", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(friends, key = { it.userId }) { friend ->
                        val isSelected = selectedIds.contains(friend.userId)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleMember(friend.userId) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { viewModel.toggleMember(friend.userId) },
                                colors = CheckboxDefaults.colors(checkedColor = WeChatGreen)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        friend.nickname.take(1).uppercase(),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(friend.nickname, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}
