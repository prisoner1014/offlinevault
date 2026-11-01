package com.offlinevault.app.ui.screens.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.offlinevault.app.data.local.entity.ChatSessionEntity
import com.offlinevault.app.service.ai.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(
    modelManager: AiModelManager,
    aiEngine: OfflineAiEngine,
    prefilledPrompt: String? = null
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Chat, 1 = Local Models
    var showHistoryDialog by remember { mutableStateOf(false) }

    // Intercept hardware back button & system edge back gestures
    BackHandler(enabled = showHistoryDialog || selectedTab != 0) {
        if (showHistoryDialog) {
            showHistoryDialog = false
        } else {
            selectedTab = 0
        }
    }

    // Handle prefilled prompt
    LaunchedEffect(prefilledPrompt) {
        if (!prefilledPrompt.isNullOrBlank()) {
            aiEngine.sendMessage(prefilledPrompt)
        }
    }

    // Chat History Dialog
    if (showHistoryDialog) {
        ChatHistoryDialog(
            aiEngine = aiEngine,
            onDismiss = { showHistoryDialog = false },
            onSelectSession = { session ->
                aiEngine.loadSession(session)
                showHistoryDialog = false
            },
            onNewChat = {
                aiEngine.startNewChat()
                showHistoryDialog = false
                Toast.makeText(context, "New chat started", Toast.LENGTH_SHORT).show()
            }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isFoldUnfolded = maxWidth >= 600.dp

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Psychology,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Offline AI Hub", fontWeight = FontWeight.Bold)
                            }
                            val activeModel = modelManager.getActiveModelInfo()
                            val isReady = modelManager.isModelReady(activeModel?.id ?: "")
                            Text(
                                text = "${activeModel?.name ?: "SmolLM2 135M"} • ${if (isReady) "🟢 Ready" else "🟡 Not Downloaded"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    actions = {
                        // Start New Chat Button
                        IconButton(
                            onClick = {
                                aiEngine.startNewChat()
                                Toast.makeText(context, "New chat started", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.AddComment, contentDescription = "New Chat")
                        }

                        // Chat History Button
                        IconButton(onClick = { showHistoryDialog = true }) {
                            Icon(Icons.Default.History, contentDescription = "Chat History")
                        }

                        // Switch between Chat and Models tab
                        IconButton(onClick = { selectedTab = if (selectedTab == 0) 1 else 0 }) {
                            Icon(
                                if (selectedTab == 0) Icons.Default.CloudDownload else Icons.AutoMirrored.Filled.Chat,
                                contentDescription = if (selectedTab == 0) "Manage Models" else "Go to Chat"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Secondary Navigation Tabs (if not fold-expanded)
                if (!isFoldUnfolded) {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("AI Assistant") },
                            icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Local Models (${AiModelCatalog.curatedModels.size})") },
                            icon = { Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }

                if (isFoldUnfolded) {
                    // Galaxy Fold Unfolded Dual-Pane Layout
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left Pane: Models & Storage
                        Box(
                            modifier = Modifier
                                .weight(0.42f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                        ) {
                            LocalModelsList(modelManager = modelManager)
                        }

                        // Crease Divider
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Right Pane: Chat Interface
                        Box(
                            modifier = Modifier
                                .weight(0.58f)
                                .fillMaxHeight()
                        ) {
                            ChatPane(
                                aiEngine = aiEngine,
                                modelManager = modelManager,
                                onNavigateToModels = { selectedTab = 1 },
                                onOpenHistory = { showHistoryDialog = true }
                            )
                        }
                    }
                } else {
                    // Fold Cover / Phone Layout
                    if (selectedTab == 0) {
                        ChatPane(
                            aiEngine = aiEngine,
                            modelManager = modelManager,
                            onNavigateToModels = { selectedTab = 1 },
                            onOpenHistory = { showHistoryDialog = true }
                        )
                    } else {
                        LocalModelsList(modelManager = modelManager)
                    }
                }
            }
        }
    }
}

@Composable
fun ChatPane(
    aiEngine: OfflineAiEngine,
    modelManager: AiModelManager,
    onNavigateToModels: () -> Unit = {},
    onOpenHistory: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    val activeModel = modelManager.getActiveModelInfo()
    val isModelReady = modelManager.isModelReady(activeModel?.id ?: "")

    // Auto-scroll to bottom on new message
    LaunchedEffect(aiEngine.messages.size, aiEngine.currentStreamingText) {
        if (aiEngine.messages.isNotEmpty()) {
            listState.animateScrollToItem(aiEngine.messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Active Session Header Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Forum,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = aiEngine.currentSessionTitle,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onOpenHistory,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("History", fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    TextButton(
                        onClick = {
                            aiEngine.startNewChat()
                            Toast.makeText(context, "New chat started", Toast.LENGTH_SHORT).show()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Chat", fontSize = 11.sp)
                    }
                }
            }
        }

        // Model Download Alert Banner
        if (!isModelReady) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clickable { onNavigateToModels() },
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${activeModel?.name ?: "Model"} weights not downloaded",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Size: ~${activeModel?.fileSizeMb ?: 0} MB. Tap here to open Local Models & download.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // Quick Action Prompt Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SuggestionChip(
                onClick = { inputText = "Summarize the key points of: " },
                label = { Text("📖 Summarize", fontSize = 12.sp) }
            )
            SuggestionChip(
                onClick = { inputText = "Explain this concept in simple terms: " },
                label = { Text("🔍 Explain", fontSize = 12.sp) }
            )
            SuggestionChip(
                onClick = { inputText = "Draft structured study notes about: " },
                label = { Text("📝 Study Notes", fontSize = 12.sp) }
            )
        }

        // Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(aiEngine.messages, key = { it.id }) { msg ->
                val isUser = msg.role == MessageRole.USER
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                ) {
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        ),
                        color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = if (isUser) 2.dp else 1.dp,
                        modifier = Modifier.widthIn(max = 420.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            if (!isUser) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = msg.modelTag ?: "Offline AI",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("AI Message", msg.text))
                                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(15.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (!isUser && msg.stats != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = msg.stats,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Input Bar
        Surface(
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Ask anything offline…") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                FilledIconButton(
                    onClick = {
                        if (inputText.isNotBlank() && !aiEngine.isGenerating) {
                            val prompt = inputText.trim()
                            inputText = ""
                            aiEngine.sendMessage(prompt)
                        }
                    },
                    enabled = inputText.isNotBlank() && !aiEngine.isGenerating,
                    modifier = Modifier.size(48.dp)
                ) {
                    if (aiEngine.isGenerating) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}

@Composable
fun ChatHistoryDialog(
    aiEngine: OfflineAiEngine,
    onDismiss: () -> Unit,
    onSelectSession: (ChatSessionEntity) -> Unit,
    onNewChat: () -> Unit
) {
    val sessions by aiEngine.allSessions.collectAsState(initial = emptyList())
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Chat History",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Start New Chat Button
                Button(
                    onClick = onNewChat,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start New Chat", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (sessions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No saved chats yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Send a message to create your first conversation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sessions, key = { it.id }) { session ->
                            val isSelected = session.id == aiEngine.currentSessionId
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectSession(session) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = session.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = dateFormat.format(Date(session.updatedAt)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    IconButton(
                                        onClick = { aiEngine.deleteSession(session.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Delete Chat",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LocalModelsList(
    modelManager: AiModelManager
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableIntStateOf(0) } // 0 = All, 1 = New Releases, 2 = Under 500MB

    val filteredModels = remember(selectedFilter) {
        when (selectedFilter) {
            1 -> AiModelCatalog.curatedModels.filter { it.isNew }
            2 -> AiModelCatalog.curatedModels.filter { it.fileSizeMb < 500 }
            else -> AiModelCatalog.curatedModels
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val success = modelManager.importCustomModel(uri, "imported_custom.gguf")
            if (success) {
                Toast.makeText(context, "Custom model imported successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to import custom model", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("On-Device AI Engine", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Download small, quantized GGUF weights directly into your Offline Vault. Once downloaded, models operate 100% offline with zero cloud telemetry.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Curated Mobile Models (${AiModelCatalog.curatedModels.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(
                        onClick = { launcher.launch("*/*") },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import .GGUF", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Model Category Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == 0,
                        onClick = { selectedFilter = 0 },
                        label = { Text("All (${AiModelCatalog.curatedModels.size})", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = selectedFilter == 1,
                        onClick = { selectedFilter = 1 },
                        label = { Text("🔥 New Releases", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = selectedFilter == 2,
                        onClick = { selectedFilter = 2 },
                        label = { Text("⚡ Compact (<500MB)", fontSize = 12.sp) }
                    )
                }
            }
        }

        items(filteredModels, key = { it.id }) { model ->
            val state = modelManager.modelStates[model.id] ?: ModelDownloadState.Idle
            val isActive = modelManager.activeModelId == model.id

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
                ),
                border = if (isActive) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = model.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (model.isNew) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFE11D48)
                                    ) {
                                        Text(
                                            text = "NEW",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "${model.publisher} • ${model.parameterCount} • ${model.quantization}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (state is ModelDownloadState.Ready) {
                            FilterChip(
                                selected = isActive,
                                onClick = { modelManager.setActiveModel(model.id) },
                                label = { Text(if (isActive) "Active" else "Select") },
                                leadingIcon = {
                                    if (isActive) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = model.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Tags & Specs Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "${model.fileSizeMb} MB",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "~${model.ramRequiredMb} MB RAM",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        model.tags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action / Progress Row
                    when (state) {
                        is ModelDownloadState.Downloading -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${(state.progress * 100).toInt()}% • ${state.speedMbPerSec} MB/s",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${state.downloadedBytes / (1024 * 1024)} / ${state.totalBytes / (1024 * 1024)} MB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { modelManager.cancelDownload(model.id) },
                                    modifier = Modifier.align(Alignment.End),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Cancel", fontSize = 12.sp)
                                }
                            }
                        }
                        is ModelDownloadState.Ready -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "✓ Ready on Disk (${state.sizeMb} MB)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF22C55E),
                                    fontWeight = FontWeight.Bold
                                )

                                IconButton(
                                    onClick = { modelManager.deleteModel(model.id) }
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Delete Model",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        is ModelDownloadState.Error -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Button(
                                    onClick = { modelManager.startDownload(model) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Retry Download (${model.fileSizeMb} MB)")
                                }
                            }
                        }
                        is ModelDownloadState.Idle -> {
                            Button(
                                onClick = { modelManager.startDownload(model) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Download ${model.fileSizeMb} MB")
                            }
                        }
                    }
                }
            }
        }
    }
}
