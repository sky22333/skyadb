package com.sky22333.skyadb.ui.ai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sky22333.skyadb.R
import com.sky22333.skyadb.ai.McpServerConfig
import com.sky22333.skyadb.ai.AiConfig
import com.sky22333.skyadb.ai.AiViewModel
import com.sky22333.skyadb.ai.AiToolStatus
import com.sky22333.skyadb.ai.AiConversationSummary
import com.sky22333.skyadb.ai.AiDownload
import com.sky22333.skyadb.ai.AI_INPUT_LIMIT
import com.sky22333.skyadb.ai.inputLength
import com.sky22333.skyadb.ui.components.Button
import com.sky22333.skyadb.ui.components.FlatButton
import com.sky22333.skyadb.ui.components.AppIcons
import com.sky22333.skyadb.ui.components.TextField
import com.sky22333.skyadb.ui.components.PopupAnchor
import com.sky22333.skyadb.ui.components.SectionHeader
import com.sky22333.skyadb.ui.theme.AppDimens
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import com.swmansion.enriched.markdown.compose.EnrichedMarkdownText
import com.swmansion.enriched.markdown.compose.markdownStyle
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.ArrowPreference

@Composable
fun AiScreen(viewModel: AiViewModel = viewModel(), onSettingsVisibilityChange: (Boolean) -> Unit = {}, listState: LazyListState? = null, followState: MutableState<Boolean>? = null) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messagesListState = listState ?: key(state.historyLoading) {
        rememberLazyListState(initialFirstVisibleItemIndex = state.messages.lastIndex.coerceAtLeast(0))
    }
    var showConfig by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showDownloads by remember { mutableStateOf(false) }
    var downloadToDelete by remember { mutableStateOf<AiDownload?>(null) }
    var downloadToExport by remember { mutableStateOf<AiDownload?>(null) }
    var pendingDeletion by remember { mutableStateOf<AiConversationSummary?>(null) }
    var composerHeight by remember { mutableIntStateOf(0) }
    val composerPadding = with(LocalDensity.current) { composerHeight.toDp() }
    val input = state.draft
    val inputSize = remember(input) { inputLength(input) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    DisposableEffect(Unit) { onDispose { onSettingsVisibilityChange(false) } }
    fun closeConfig() { viewModel.clearModels(); viewModel.clearMcpTest(); showConfig = false; onSettingsVisibilityChange(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) viewModel.attach(uri) }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val download = downloadToExport
        downloadToExport = null
        if (uri != null && download != null) viewModel.exportDownload(download, uri)
    }
    val uriHandler = LocalUriHandler.current
    val color = MiuixTheme.colorScheme.onBackground
    val accent = MiuixTheme.colorScheme.primary
    val markdownStyle = remember(color, accent) { markdownStyle {
        paragraph { this.color = color }
        h1 { this.color = color }; h2 { this.color = color }; h3 { this.color = color }
        h4 { this.color = color }; h5 { this.color = color }; h6 { this.color = color }
        link { this.color = accent }
    } }
    val dragging by messagesListState.interactionSource.collectIsDraggedAsState()
    val localFollowState = remember { mutableStateOf(true) }
    var followsBottom by (followState ?: localFollowState)
    var wasDragging by remember(messagesListState) { mutableStateOf(false) }
    LaunchedEffect(dragging) {
        if (dragging) followsBottom = false
        else if (wasDragging && !messagesListState.canScrollForward) followsBottom = true
        wasDragging = dragging
    }
    val lastMessageId = state.messages.lastOrNull()?.id
    val lastIndex = state.messages.lastIndex
    LaunchedEffect(messagesListState, lastMessageId, followsBottom) {
        if (followsBottom && lastIndex >= 0) messagesListState.scrollToItem(lastIndex)
    }
    LaunchedEffect(messagesListState, lastMessageId, followsBottom) {
        if (followsBottom && lastIndex >= 0) {
            snapshotFlow {
                val layout = messagesListState.layoutInfo
                val last = layout.visibleItemsInfo.lastOrNull { it.index == lastIndex }
                last?.let { (it.offset + it.size + layout.afterContentPadding - layout.viewportEndOffset).coerceAtLeast(0) } ?: 0
            }.collect { overflow -> if (overflow > 0) messagesListState.scrollBy(overflow.toFloat()) }
        }
    }
    if (showConfig) {
        AiConfigPage(state.config, state.models, state.loadingModels, saving = state.savingConfig, onDismiss = ::closeConfig, onFetch = viewModel::loadModels, onClearModels = viewModel::clearModels, mcpTesting = state.mcpTesting, mcpNotice = state.mcpNotice, mcpNoticeId = state.mcpNoticeId, mcpToolNames = state.mcpToolNames, onMcpChanged = viewModel::clearMcpNotice, onCloseMcp = viewModel::clearMcpTest, onTestMcp = viewModel::testMcp, onSave = { viewModel.saveConfig(it, ::closeConfig) })
    } else Column(Modifier.fillMaxSize()) {
        SmallTopAppBar(title = stringResource(R.string.ai_title), color = MiuixTheme.colorScheme.background, actions = {
            IconButton(onClick = { focusManager.clearFocus(); keyboard?.hide(); viewModel.refreshDownloads(); showDownloads = true }, enabled = !state.busy && !state.managingDownload) { Icon(MiuixIcons.Download, stringResource(R.string.ai_downloads)) }
            IconButton(onClick = { focusManager.clearFocus(); keyboard?.hide(); showHistory = true }, enabled = !state.busy && !state.historyLoading) { Icon(MiuixIcons.ListView, stringResource(R.string.ai_history)) }
            IconButton(onClick = { followsBottom = true; viewModel.clear() }, enabled = !state.busy && !state.historyLoading) { Icon(MiuixIcons.Add, stringResource(R.string.ai_new)) }
            IconButton(onClick = { focusManager.clearFocus(); keyboard?.hide(); onSettingsVisibilityChange(true); showConfig = true }, enabled = !state.busy) { Icon(MiuixIcons.Settings, stringResource(R.string.ai_config)) }
        })
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.historyLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.messages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.ai_empty), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            } else {
                LazyColumn(state = messagesListState, modifier = Modifier.fillMaxSize().testTag("ai_messages"), contentPadding = PaddingValues(start = AppDimens.ScreenPadding, end = AppDimens.ScreenPadding, top = AppDimens.ScreenPadding, bottom = composerPadding + AppDimens.ScreenPadding), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(state.messages, key = { it.id }) { message ->
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = if (message.role == "user") Alignment.End else Alignment.Start) {
                            when (message.role) {
                                "assistant" -> EnrichedMarkdownText(markdown = message.text, style = markdownStyle, onLinkClick = { link -> runCatching { uriHandler.openUri(link) } })
                                "tool" -> {
                                    var expanded by remember(message.id) { mutableStateOf(false) }
                                    val toolName = message.text.substringBefore('\n')
                                    val toolLabel = when (toolName) {
                                        "download_file" -> stringResource(R.string.ai_tool_download)
                                        "pull_device_file" -> stringResource(R.string.ai_tool_pull)
                                        "export_device_apk" -> stringResource(R.string.ai_tool_export_apk)
                                        "list_authorized_files" -> stringResource(R.string.ai_tool_local_list)
                                        "read_authorized_file" -> stringResource(R.string.ai_tool_local_read)
                                        "write_authorized_file" -> stringResource(R.string.ai_tool_local_write)
                                        "open_local_app" -> stringResource(R.string.ai_tool_open_app)
                                        "agent_environments" -> stringResource(R.string.ai_agent_environments)
                                        "agent_discover" -> stringResource(R.string.ai_agent_discover)
                                        "agent_connect" -> stringResource(R.string.ai_agent_connect)
                                        "agent_disconnect" -> stringResource(R.string.ai_agent_disconnect)
                                        "agent_shell" -> stringResource(R.string.ai_agent_shell)
                                        "agent_screen" -> stringResource(R.string.ai_agent_screen)
                                        "agent_transfer" -> stringResource(R.string.ai_agent_transfer)
                                        "get_device_info" -> stringResource(R.string.ai_tool_device)
                                        "list_remote_files" -> stringResource(R.string.ai_tool_files)
                                        "run_device_shell" -> stringResource(R.string.ai_tool_shell)
                                        "upload_selected_file" -> stringResource(R.string.ai_tool_upload)
                                        "read_selected_file" -> stringResource(R.string.ai_tool_read)
                                        "list_selected_archive" -> stringResource(R.string.ai_tool_archive_list)
                                        "read_selected_archive_text" -> stringResource(R.string.ai_tool_archive_read)
                                        "list_local_files" -> stringResource(R.string.ai_tool_local_list)
                                        "read_local_file" -> stringResource(R.string.ai_tool_local_read)
                                        "upload_local_file" -> stringResource(R.string.ai_tool_local_upload)
                                        else -> toolName
                                    }
                                    Row(
                                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { expanded = !expanded }.padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(toolLabel, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                        message.toolStatus?.let { status ->
                                            Text(stringResource(when (status) {
                                                AiToolStatus.Running -> R.string.ai_running
                                                AiToolStatus.Finished -> R.string.ai_tool_finished
                                                AiToolStatus.Failed -> R.string.ai_tool_failed
                                                AiToolStatus.Interrupted -> R.string.ai_operation_interrupted
                                            }), style = MiuixTheme.textStyles.footnote1, color = if (status == AiToolStatus.Failed) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                        }
                                        Icon(MiuixIcons.ChevronBackward, stringResource(if (expanded) R.string.action_collapse else R.string.action_expand), modifier = Modifier.size(18.dp).rotate(if (expanded) 90f else 270f), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                    }
                                    if (expanded) Text(message.text.substringAfter('\n', ""), color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1)
                                }
                                "user" -> {
                                    if (message.attachmentName.isNotBlank()) Text(message.attachmentName, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1)
                                    if (message.text.isNotBlank()) Text(message.text, modifier = Modifier.widthIn(max = 320.dp))
                                }
                                else -> Text(message.text)
                            }
                        }
                    }
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).onSizeChanged { composerHeight = it.height }) {
                if (state.busy) Text(state.downloadProgress ?: stringResource(R.string.ai_running), modifier = Modifier.padding(horizontal = 20.dp), color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1)
                if (state.attachment != null) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    AttachmentThumbnail(state.attachment!!, state.attachmentName)
                    Text(state.attachmentName.ifBlank { stringResource(R.string.ai_attachment) }, modifier = Modifier.weight(1f), maxLines = 1, style = MiuixTheme.textStyles.footnote1)
                    IconButton(onClick = { viewModel.attach(null) }, enabled = !state.busy) { Icon(MiuixIcons.Close, stringResource(R.string.ai_remove_attachment)) }
                }
                if (inputSize >= AI_INPUT_LIMIT - 2000) Text(
                    text = stringResource(if (inputSize > AI_INPUT_LIMIT) R.string.ai_input_over_limit else R.string.ai_input_count, inputSize, AI_INPUT_LIMIT),
                    modifier = Modifier.align(Alignment.End).padding(horizontal = 20.dp),
                    style = MiuixTheme.textStyles.footnote1,
                    color = if (inputSize > AI_INPUT_LIMIT) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                TextField(
                    value = input, onValueChange = viewModel::updateDraft, enabled = !state.historyLoading,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
                        .shadow(2.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
                        .border(1.dp, MiuixTheme.colorScheme.onBackground.copy(alpha = 0.08f), RoundedCornerShape(24.dp)),
                    colors = TextFieldDefaults.textFieldColors(backgroundColor = MiuixTheme.colorScheme.background),
                    insideMargin = DpSize(16.dp, 8.dp),
                    label = stringResource(R.string.ai_prompt), useLabelAsPlaceholder = true, maxLines = 4,
                    leadingIcon = {
                        IconButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.size(48.dp), enabled = !state.busy) { Icon(MiuixIcons.Add, stringResource(R.string.ai_attach)) }
                    },
                    trailingIcon = {
                        IconButton(onClick = { if (state.busy) viewModel.stop() else if (viewModel.send(input)) { followsBottom = true } }, modifier = Modifier.size(48.dp), enabled = state.busy || ((input.isNotBlank() || state.attachment != null) && inputSize <= AI_INPUT_LIMIT && !state.historyLoading)) {
                            if (state.busy) {
                                val stopDescription = stringResource(R.string.ai_stop)
                                Box(
                                    modifier = Modifier.size(28.dp)
                                        .background(MiuixTheme.colorScheme.onBackground, CircleShape)
                                        .semantics { contentDescription = stopDescription },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(Modifier.size(9.dp).background(MiuixTheme.colorScheme.background))
                                }
                            } else {
                                Icon(AppIcons.ArrowUp, stringResource(R.string.ai_send), tint = if ((input.isNotBlank() || state.attachment != null) && inputSize <= AI_INPUT_LIMIT && !state.historyLoading) MiuixTheme.colorScheme.onBackground else MiuixTheme.colorScheme.onBackground.copy(alpha = 0.35f))
                            }
                        }
                    },
                )
            }
        }
    }
    if (showDownloads) WindowDialog(show = true, onDismissRequest = { showDownloads = false }, title = stringResource(R.string.ai_downloads)) {
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            state.downloadNotice?.let { Text(it, style = MiuixTheme.textStyles.footnote1) }
            if (state.downloads.isEmpty()) Text(stringResource(R.string.ai_downloads_empty))
            state.downloads.forEach { download ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(download.name, maxLines = 2)
                        Text(android.text.format.Formatter.formatFileSize(androidx.compose.ui.platform.LocalContext.current, download.size), style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                    FlatButton(onClick = { downloadToExport = download; exportPicker.launch(download.name) }, enabled = !state.managingDownload) { Text(stringResource(R.string.ai_download_export)) }
                    IconButton(onClick = { downloadToDelete = download; showDownloads = false }, enabled = !state.managingDownload) { Icon(MiuixIcons.Delete, stringResource(R.string.action_delete)) }
                }
            }
        }
    }
    downloadToDelete?.let { download ->
        fun dismiss() { downloadToDelete = null; showDownloads = true }
        WindowDialog(show = true, onDismissRequest = ::dismiss, title = stringResource(R.string.ai_download_delete), summary = download.name) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = ::dismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = { viewModel.deleteDownload(download); dismiss() }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_delete)) }
            }
        }
    }
    if (showHistory) WindowDialog(show = true, onDismissRequest = { showHistory = false }, title = stringResource(R.string.ai_history)) {
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            if (state.conversations.isEmpty()) Text(stringResource(R.string.ai_history_empty))
            state.conversations.forEach { conversation ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(conversation.title, modifier = Modifier.weight(1f).clickable { followsBottom = true; viewModel.selectConversation(conversation.id); showHistory = false }.padding(vertical = 12.dp), maxLines = 2)
                    IconButton(onClick = { showHistory = false; pendingDeletion = conversation }) { Icon(MiuixIcons.Delete, stringResource(R.string.action_delete)) }
                }
            }
        }
    }
    pendingDeletion?.let { conversation ->
        fun dismiss() { pendingDeletion = null; showHistory = true }
        WindowDialog(show = true, onDismissRequest = ::dismiss, title = stringResource(R.string.ai_history_delete), summary = conversation.title) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = ::dismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = { viewModel.deleteConversation(conversation.id); dismiss() }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_delete)) }
            }
        }
    }
    state.approval?.let { pending ->
        WindowDialog(show = true, onDismissRequest = { viewModel.respond(false) }, title = stringResource(R.string.ai_approval), summary = pending.title) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(pending.detail, modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.respond(false) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.ai_deny)) }
                    Button(onClick = { viewModel.respond(true) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.ai_allow)) }
                }
            }
        }
    }
    state.error?.let { error ->
        WindowDialog(show = true, onDismissRequest = viewModel::dismissError, title = stringResource(R.string.ai_error), summary = error) {
            Button(onClick = viewModel::dismissError, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_confirm)) }
        }
    }
}

@Composable
private fun AttachmentThumbnail(uri: android.net.Uri, name: String) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, uri, name) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                if (com.sky22333.skyadb.ai.imageMime(resolver.getType(uri), name)?.startsWith("image/") != true) return@runCatching null
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / sample > 96 || bounds.outHeight / sample > 96) sample *= 2
                resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }) }
            }.getOrNull()
        }
    }
    bitmap?.let {
        Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(40.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiConfigPage(initial: AiConfig, models: List<String>, loading: Boolean, saving: Boolean, onDismiss: () -> Unit, onFetch: (AiConfig) -> Unit, onClearModels: () -> Unit, mcpTesting: Boolean, mcpNotice: String?, mcpNoticeId: String?, mcpToolNames: List<String>, onMcpChanged: () -> Unit, onCloseMcp: () -> Unit, onTestMcp: (McpServerConfig) -> Unit, onSave: (AiConfig) -> Unit) {
    var config by remember(initial) { mutableStateOf(initial) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    var directoryError by remember { mutableStateOf(false) }
    var editingMcp by remember { mutableStateOf<McpServerConfig?>(null) }
    fun releaseTemporaryDirectory() {
        if (config.documentTree.isNotEmpty() && config.documentTree != initial.documentTree) runCatching {
            context.contentResolver.releasePersistableUriPermission(android.net.Uri.parse(config.documentTree), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }
    val directoryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val result = runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            directoryError = result.isFailure
            if (result.isSuccess) { if (uri.toString() != config.documentTree) releaseTemporaryDirectory(); config = config.copy(documentTree = uri.toString()) }
        }
    }
    val modelListLabel = stringResource(R.string.ai_model_list)
    val modelListWidth = with(LocalDensity.current) {
        rememberTextMeasurer().measure(AnnotatedString(modelListLabel), style = MiuixTheme.textStyles.main).size.width.toDp() + 40.dp
    }
    LaunchedEffect(models) { if (config.model.isBlank() && models.isNotEmpty()) config = config.copy(model = models.first()) }
    val onBack = { if (!saving) { releaseTemporaryDirectory(); onDismiss() } }
    BackHandler(enabled = !WindowInsets.isImeVisible, onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        SmallTopAppBar(title = stringResource(R.string.ai_config), navigationIcon = {
            IconButton(onClick = onBack, enabled = !saving) { Icon(MiuixIcons.Back, stringResource(R.string.action_back)) }
        })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = AppDimens.ScreenPadding, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(AppDimens.CompactGap)) {
            TextField(value = config.baseUrl, onValueChange = { config = config.copy(baseUrl = it); onClearModels() }, modifier = Modifier.fillMaxWidth(), label = stringResource(R.string.ai_url), singleLine = true, enabled = !saving)
            TextField(value = config.apiKey, onValueChange = { config = config.copy(apiKey = it); onClearModels() }, modifier = Modifier.fillMaxWidth(), label = stringResource(R.string.ai_key), singleLine = true, enabled = !saving, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = PasswordVisualTransformation())
            TextField(value = config.model, onValueChange = { config = config.copy(model = it) }, modifier = Modifier.fillMaxWidth(), label = stringResource(R.string.ai_model), singleLine = true, enabled = !saving)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FlatButton(onClick = { focusManager.clearFocus(); keyboard?.hide(); onFetch(config) }, enabled = !loading && !saving) { Text(stringResource(if (loading) R.string.ai_running else R.string.ai_models)) }
                Spacer(Modifier.weight(1f))
                PopupAnchor {
                    OverlayDropdownPreference(
                        title = modelListLabel,
                        items = models,
                        selectedIndex = models.indexOf(config.model),
                        onSelectedIndexChange = { config = config.copy(model = models[it]) },
                        modifier = Modifier.width(modelListWidth),
                        insideMargin = PaddingValues(start = 12.dp),
                        maxHeight = 320.dp,
                        enabled = !loading && !saving && models.isNotEmpty(),
                        showValue = false,
                        onExpandedChange = { if (it) { focusManager.clearFocus(); keyboard?.hide() } },
                    )
                }
            }
            if (models.isEmpty()) Text(stringResource(R.string.ai_empty_models), style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            PopupAnchor {
                OverlayDropdownPreference(
                    title = stringResource(R.string.ai_protocol),
                    items = listOf("Chat Completions", "Responses"),
                    selectedIndex = if (config.responses) 1 else 0,
                    onSelectedIndexChange = { config = config.copy(responses = it == 1) },
                    insideMargin = PaddingValues(0.dp),
                    enabled = !saving,
                    onExpandedChange = { if (it) { focusManager.clearFocus(); keyboard?.hide() } },
                )
            }
            PopupAnchor {
                OverlayDropdownPreference(
                    title = stringResource(R.string.ai_execution_mode),
                    items = listOf(stringResource(R.string.ai_execution_confirm), stringResource(R.string.ai_execution_auto)),
                    selectedIndex = if (config.autoExecute) 1 else 0,
                    onSelectedIndexChange = { config = config.copy(autoExecute = it == 1) },
                    insideMargin = PaddingValues(0.dp),
                    enabled = !saving,
                    onExpandedChange = { if (it) { focusManager.clearFocus(); keyboard?.hide() } },
                )
            }
            ArrowPreference(
                title = stringResource(if (config.documentTree.isEmpty()) R.string.ai_directory_choose else R.string.ai_directory_change),
                insideMargin = PaddingValues(0.dp),
                enabled = !saving,
                onClick = { focusManager.clearFocus(); keyboard?.hide(); directoryPicker.launch(null) },
            )
            if (config.documentTree.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(android.net.Uri.parse(config.documentTree).lastPathSegment.orEmpty(), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    IconButton(onClick = { releaseTemporaryDirectory(); config = config.copy(documentTree = "") }, enabled = !saving) { Icon(MiuixIcons.Delete, stringResource(R.string.ai_directory_remove)) }
                }
            }
            if (directoryError) Text(stringResource(R.string.ai_directory_failed), color = MiuixTheme.colorScheme.error, style = MiuixTheme.textStyles.footnote1)
            Spacer(Modifier.height(8.dp))
            SectionHeader(
                title = stringResource(R.string.ai_mcp_title),
                trailing = {
                    IconButton(onClick = { onMcpChanged(); focusManager.clearFocus(); keyboard?.hide(); editingMcp = McpServerConfig() }, enabled = !saving) {
                        Icon(MiuixIcons.Add, stringResource(R.string.ai_mcp_add))
                    }
                },
            )
            config.mcpServers.forEach { server ->
                ArrowPreference(
                    title = server.name,
                    insideMargin = PaddingValues(0.dp),
                    enabled = !saving,
                    onClick = { onMcpChanged(); focusManager.clearFocus(); keyboard?.hide(); editingMcp = server },
                    endActions = {
                        Switch(
                            checked = server.enabled,
                            onCheckedChange = { enabled ->
                                onMcpChanged()
                                config = config.copy(mcpServers = config.mcpServers.map { if (it.id == server.id) it.copy(enabled = enabled) else it })
                            },
                            modifier = Modifier.semantics { contentDescription = server.name },
                            enabled = !saving,
                        )
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onBack, enabled = !saving, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = { onSave(config) }, enabled = !saving, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColorsPrimary()) { Text(stringResource(R.string.ai_save)) }
            }
        }
    }
    editingMcp?.let { server ->
        val exists = config.mcpServers.any { it.id == server.id }
        McpServerEditor(
            initial = server,
            isNew = !exists,
            testing = mcpTesting,
            notice = mcpNotice.takeIf { mcpNoticeId == server.id },
            toolNames = if (mcpNoticeId == server.id) mcpToolNames else emptyList(),
            onChanged = onMcpChanged,
            onTest = onTestMcp,
            onDismiss = { onCloseMcp(); editingMcp = null },
            onSave = { updated ->
                config = config.copy(mcpServers = if (exists) config.mcpServers.map { if (it.id == updated.id) updated else it } else config.mcpServers + updated)
                onCloseMcp(); editingMcp = null
            },
            onDelete = { config = config.copy(mcpServers = config.mcpServers.filterNot { it.id == server.id }); onCloseMcp(); editingMcp = null },
        )
    }
}


@Composable
private fun McpServerEditor(
    initial: McpServerConfig,
    isNew: Boolean,
    testing: Boolean,
    notice: String?,
    toolNames: List<String>,
    onChanged: () -> Unit,
    onTest: (McpServerConfig) -> Unit,
    onDismiss: () -> Unit,
    onSave: (McpServerConfig) -> Unit,
    onDelete: () -> Unit,
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    var invalid by remember(initial.id) { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun update(value: McpServerConfig) { draft = value; invalid = false; onChanged() }
    WindowDialog(show = true, onDismissRequest = onDismiss, title = stringResource(if (isNew) R.string.ai_mcp_add else R.string.ai_mcp_server)) {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(value = draft.name, onValueChange = { update(draft.copy(name = it)) }, modifier = Modifier.fillMaxWidth(), label = stringResource(R.string.ai_mcp_name), singleLine = true, enabled = !testing)
            TextField(value = draft.url, onValueChange = { update(draft.copy(url = it)) }, modifier = Modifier.fillMaxWidth(), label = stringResource(R.string.ai_mcp_url), singleLine = true, enabled = !testing)
            TextField(value = draft.token, onValueChange = { update(draft.copy(token = it)) }, modifier = Modifier.fillMaxWidth(), label = stringResource(R.string.ai_mcp_token), singleLine = true, enabled = !testing, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = PasswordVisualTransformation())
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FlatButton(onClick = { focusManager.clearFocus(); keyboard?.hide(); onTest(draft.copy(url = draft.url.trim())) }, enabled = !testing) { Text(stringResource(if (testing) R.string.ai_running else R.string.ai_mcp_test)) }
                Spacer(Modifier.weight(1f))
                if (!isNew) FlatButton(onClick = onDelete, enabled = !testing) { Text(stringResource(R.string.action_delete)) }
            }
            if (invalid) Text(stringResource(R.string.ai_mcp_invalid), color = MiuixTheme.colorScheme.error, style = MiuixTheme.textStyles.footnote1)
            notice?.let { Text(it, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
            if (toolNames.isNotEmpty()) Text(toolNames.joinToString(" · "), style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = {
                    val updated = draft.copy(name = draft.name.trim(), url = draft.url.trim())
                    if (updated.name.isBlank() || updated.url.toHttpUrlOrNull() == null) invalid = true else onSave(updated)
                }, enabled = !testing, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColorsPrimary()) { Text(stringResource(R.string.action_confirm)) }
            }
        }
    }
}
