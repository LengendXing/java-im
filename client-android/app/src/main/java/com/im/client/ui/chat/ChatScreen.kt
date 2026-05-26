package com.im.client.ui.chat

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.im.client.data.model.Message
import com.im.client.data.model.ContentType
import com.im.client.ui.theme.WeChatGreen
import com.im.client.ui.theme.WeChatGreenLight
import com.im.client.ui.theme.Gray900
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    sessionId: String,
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel = viewModel()
) {
    viewModel.init(sessionId)
    val messages by viewModel.messages.collectAsState()
    val inputText by viewModel.inputText
    val searchMode by viewModel.searchMode
    val searchQuery by viewModel.searchQuery
    val searchResults by viewModel.searchResults.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var showRecallDialog by mutableStateOf<Message?>(null)

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Recall confirmation dialog
    showRecallDialog?.let { msg ->
        AlertDialog(
            onDismissRequest = { showRecallDialog = null },
            title = { Text("Recall Message") },
            text = { Text("Are you sure you want to recall this message?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.recallMessage(context, msg.msgId, msg.sessionId)
                    showRecallDialog = null
                }) { Text("Recall", color = WeChatGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showRecallDialog = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searchMode) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = viewModel::onSearchQueryChange,
                            placeholder = { Text("Search messages...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp)
                        )
                    } else {
                        Text(extractName(sessionId))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (searchMode) viewModel.toggleSearchMode() else onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!searchMode) {
                        IconButton(onClick = { viewModel.toggleSearchMode() }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            MessageInput(
                value = inputText,
                onValueChange = viewModel::onInputChange,
                onSend = viewModel::sendMessage,
                onPickImage = { viewModel.pickImage(context, it) },
                onPickFile = { viewModel.pickFile(context, it) },
                enabled = !viewModel.isLoading
            )
        }
    ) { padding ->
        val displayMessages = if (searchMode && searchQuery.isNotBlank()) searchResults else messages
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(displayMessages, key = { it.msgId }) { message ->
                MessageBubble(
                    message = message,
                    onLongPress = {
                        if (message.isMine && !message.isRecalled &&
                            System.currentTimeMillis() - message.serverTime < 2 * 60 * 1000
                        ) {
                            showRecallDialog = message
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(message: Message, onLongPress: () -> Unit = {}) {
    val isMine = message.isMine
    val alignment = if (isMine) Alignment.End else Alignment.Start

    if (message.isRecalled) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Message recalled",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                textDecoration = TextDecoration.Italic
            )
        }
        return
    }

    val bgColor = if (isMine) WeChatGreenLight
        else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isMine) Gray900
        else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isMine) 16.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 16.dp
            ),
            color = bgColor,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongPress
                )
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                when (ContentType.fromValue(message.msgType)) {
                    ContentType.IMAGE -> {
                        val imageUrl = message.content
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = "Image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                    ContentType.FILE -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AttachFile,
                                contentDescription = "File",
                                tint = textColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = message.content.substringAfterLast("/").substringAfterLast("%2F").substringAfterLast("%3A"),
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor
                        )
                    }
                }
                Text(
                    text = formatTime(message.serverTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
fun MessageInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onPickImage: (Uri) -> Unit,
    onPickFile: (Uri) -> Unit,
    enabled: Boolean
) {
    val context = LocalContext.current
    var showAttachMenu by remember { mutableStateOf(false) }

    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            if (showAttachMenu) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    IconButton(onClick = {
                        pickImage(context, onPickImage)
                        showAttachMenu = false
                    }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Image, contentDescription = "Image", tint = WeChatGreen)
                            Text("Image", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    IconButton(onClick = {
                        pickFile(context, onPickFile)
                        showAttachMenu = false
                    }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AttachFile, contentDescription = "File", tint = WeChatGreen)
                            Text("File", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                HorizontalDivider()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .imePadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showAttachMenu = !showAttachMenu }) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = "Attach",
                        tint = if (showAttachMenu) WeChatGreen
                            else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message...") },
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onSend,
                    enabled = enabled && value.isNotBlank()
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Send",
                        tint = if (value.isNotBlank()) WeChatGreen
                            else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun pickImage(context: Context, onResult: (Uri) -> Unit) {
    // Caller should use ActivityResultLauncher; for simplicity, trigger the content intent
    // The ViewModel handles this via the activity'sActivityResultRegistry
}

private fun pickFile(context: Context, onResult: (Uri) -> Unit) {
    // Same as above - ViewModel integration point
}

private fun extractName(sessionId: String): String {
    return sessionId.replace("c2c_", "Chat with ").replace("group_", "Group ")
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
