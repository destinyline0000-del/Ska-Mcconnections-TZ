package com.example.ui.social

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.*
import com.example.ui.i18n.UiStrings
import com.example.ui.theme.*
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ConnectionScreen(
    currentUserId: String,
    groupsState: UiState<List<ChatGroup>>,
    selectedGroup: ChatGroup?,
    groupMessagesState: UiState<List<GroupMessage>>,
    privateChatsState: UiState<List<PrivateChat>>,
    selectedPrivateChat: PrivateChat?,
    privateMessagesState: UiState<List<PrivateMessage>>,
    directoryState: UiState<List<UserDirectoryEntry>>,
    strings: UiStrings,
    onSelectGroup: (ChatGroup?) -> Unit,
    onCreateGroup: (String, String, String) -> Unit,
    onSendGroupMessage: (groupId: String, text: String, pickedUri: Uri?, isVideo: Boolean, manualUrl: String?) -> Unit,
    onSelectPrivateChat: (PrivateChat?) -> Unit,
    onStartPrivateChatWithMember: (UserDirectoryEntry) -> Unit,
    onSendPrivateMessage: (PrivateChat, String, String?) -> Unit,
    onPlayMediaUrl: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (selectedGroup != null) {
        GroupChatRoom(
            group = selectedGroup,
            currentUserId = currentUserId,
            messagesState = groupMessagesState,
            strings = strings,
            onBack = { onSelectGroup(null) },
            onSend = { text, uri, isVideo, url ->
                onSendGroupMessage(selectedGroup.id, text, uri, isVideo, url)
            },
            onPlayMediaUrl = onPlayMediaUrl,
            modifier = modifier
        )
        return
    }

    if (selectedPrivateChat != null) {
        PrivateChatRoom(
            chat = selectedPrivateChat,
            currentUserId = currentUserId,
            messagesState = privateMessagesState,
            strings = strings,
            readOnlyOversight = currentUserId !in selectedPrivateChat.participantIds,
            onBack = { onSelectPrivateChat(null) },
            onSend = { text, url -> onSendPrivateMessage(selectedPrivateChat, text, url) },
            onPlayMediaUrl = onPlayMediaUrl,
            modifier = modifier
        )
        return
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = OnyxSurface,
            contentColor = TanzanianGold
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(strings.groupChats) },
                icon = { Icon(Icons.Filled.Groups, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(strings.privateChats) },
                icon = { Icon(Icons.Filled.ChatBubble, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text(strings.membersDirectory) },
                icon = { Icon(Icons.Filled.People, contentDescription = null) }
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                0 -> GroupsTab(
                    groupsState = groupsState,
                    strings = strings,
                    onOpenGroup = onSelectGroup,
                    onCreateGroupClick = { showCreateGroupDialog = true }
                )
                1 -> PrivateChatsTab(
                    currentUserId = currentUserId,
                    privateChatsState = privateChatsState,
                    strings = strings,
                    onOpenChat = onSelectPrivateChat,
                    onExploreMembers = { selectedTab = 2 }
                )
                else -> MembersDirectoryTab(
                    currentUserId = currentUserId,
                    directoryState = directoryState,
                    strings = strings,
                    onMessageUser = onStartPrivateChatWithMember
                )
            }
        }
    }

    if (showCreateGroupDialog) {
        CreateGroupDialog(
            strings = strings,
            onDismiss = { showCreateGroupDialog = false },
            onConfirm = { name, desc, tag ->
                onCreateGroup(name, desc, tag)
                showCreateGroupDialog = false
            }
        )
    }
}

@Composable
private fun GroupsTab(
    groupsState: UiState<List<ChatGroup>>,
    strings: UiStrings,
    onOpenGroup: (ChatGroup) -> Unit,
    onCreateGroupClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (groupsState) {
            is UiState.Loading -> CircularProgressIndicator(
                color = TanzanianGold,
                modifier = Modifier.align(Alignment.Center)
            )
            is UiState.Error -> Text(
                text = groupsState.message,
                color = ErrorCoral,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
            )
            is UiState.Success -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(groupsState.data, key = { it.id }) { group ->
                        Card(
                            onClick = { onOpenGroup(group) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = SlateCard),
                            border = BorderStroke(1.dp, DividerSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("group_card_${group.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(TanzanianGoldDark),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Groups, contentDescription = null, tint = TanzanianGold)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(group.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    Text(group.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 2)
                                }
                                FilledTonalButton(onClick = { onOpenGroup(group) }) {
                                    Text(strings.joinAndOpen)
                                }
                            }
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onCreateGroupClick,
            containerColor = TanzanianGold,
            contentColor = ObsidianBg,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("create_group_fab")
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(strings.createGroup, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PrivateChatsTab(
    currentUserId: String,
    privateChatsState: UiState<List<PrivateChat>>,
    strings: UiStrings,
    onOpenChat: (PrivateChat) -> Unit,
    onExploreMembers: () -> Unit
) {
    when (privateChatsState) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = TanzanianGold)
        }
        is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(privateChatsState.message, color = ErrorCoral, modifier = Modifier.padding(16.dp))
        }
        is UiState.Success -> {
            if (privateChatsState.data.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.ChatBubbleOutline, contentDescription = null, tint = TextMuted, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(strings.privateChats, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onExploreMembers,
                        colors = ButtonDefaults.buttonColors(containerColor = TanzanianGold, contentColor = ObsidianBg)
                    ) {
                        Text(strings.membersDirectory, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(privateChatsState.data, key = { it.id }) { chat ->
                        val otherId = chat.participantIds.firstOrNull { it != currentUserId } ?: ""
                        val otherName = chat.participantNames[otherId] ?: "Member"
                        Card(
                            onClick = { onOpenChat(chat) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = SlateCard),
                            border = BorderStroke(1.dp, DividerSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(TanzaniteTealContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = otherName.take(1).uppercase(),
                                        color = TanzaniteTeal,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(otherName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    Text(
                                        text = chat.lastMessage.ifBlank { "..." },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                        maxLines = 1
                                    )
                                }
                                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MembersDirectoryTab(
    currentUserId: String,
    directoryState: UiState<List<UserDirectoryEntry>>,
    strings: UiStrings,
    onMessageUser: (UserDirectoryEntry) -> Unit
) {
    when (directoryState) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = TanzanianGold)
        }
        is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(directoryState.message, color = ErrorCoral, modifier = Modifier.padding(16.dp))
        }
        is UiState.Success -> {
            val others = directoryState.data.filter { it.userId != currentUserId }
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(others, key = { it.userId }) { member ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SlateCard),
                        border = BorderStroke(1.dp, DividerSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(VipSapphireContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = member.displayName.take(1).uppercase(),
                                    color = VipSapphire,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(member.displayName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text("${member.role} • ${member.subscriptionTier}", style = MaterialTheme.typography.labelSmall, color = TanzanianGold)
                            }
                            Button(
                                onClick = { onMessageUser(member) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TanzanianGold,
                                    contentColor = ObsidianBg
                                )
                            ) {
                                Text(strings.startPrivateChat, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupChatRoom(
    group: ChatGroup,
    currentUserId: String,
    messagesState: UiState<List<GroupMessage>>,
    strings: UiStrings,
    onBack: () -> Unit,
    onSend: (String, Uri?, Boolean, String?) -> Unit,
    onPlayMediaUrl: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var draft by remember { mutableStateOf("") }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var pickedIsVideo by remember { mutableStateOf(false) }
    var mediaUrlInput by remember { mutableStateOf("") }
    var showMediaLinkBox by remember { mutableStateOf(false) }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pickedUri = uri
            val mime = context.contentResolver.getType(uri).orEmpty()
            pickedIsVideo = mime.startsWith("video/")
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = ObsidianBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = OnyxSurface),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                title = {
                    Column {
                        Text(group.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(group.description, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (messagesState) {
                    is UiState.Loading -> item {
                        CircularProgressIndicator(color = TanzanianGold)
                    }
                    is UiState.Error -> item {
                        Text(messagesState.message, color = ErrorCoral)
                    }
                    is UiState.Success -> {
                        items(messagesState.data, key = { it.id }) { msg ->
                            val isMine = msg.senderId == currentUserId
                            GroupMessageBubble(
                                msg = msg,
                                isMine = isMine,
                                onPlayVideo = { url -> onPlayMediaUrl(url, "${msg.senderName} Video") }
                            )
                        }
                    }
                }
            }

            if (pickedUri != null) {
                Surface(
                    color = TanzaniteTealContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (pickedIsVideo) "Video ready to send" else "Photo ready to send",
                            color = TanzaniteTeal,
                            style = MaterialTheme.typography.labelLarge
                        )
                        IconButton(onClick = { pickedUri = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear", tint = TanzaniteTeal)
                        }
                    }
                }
            }

            if (showMediaLinkBox) {
                OutlinedTextField(
                    value = mediaUrlInput,
                    onValueChange = { mediaUrlInput = it },
                    label = { Text("Optional Video/Photo URL (MP4, Drive, YouTube)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OnyxSurface)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = {
                        mediaPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    modifier = Modifier.testTag("group_attach_media_button")
                ) {
                    Icon(Icons.Filled.PermMedia, contentDescription = strings.attachPhotoOrVideo, tint = TanzanianGold)
                }
                IconButton(onClick = { showMediaLinkBox = !showMediaLinkBox }) {
                    Icon(Icons.Filled.Link, contentDescription = "Media Link", tint = TanzaniteTeal)
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text(strings.typeMessageHint) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("group_message_input")
                )
                IconButton(
                    onClick = {
                        if (draft.isNotBlank() || pickedUri != null || mediaUrlInput.isNotBlank()) {
                            onSend(draft, pickedUri, pickedIsVideo, mediaUrlInput)
                            draft = ""
                            pickedUri = null
                            mediaUrlInput = ""
                            showMediaLinkBox = false
                        }
                    },
                    modifier = Modifier.testTag("group_send_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = strings.send, tint = TanzanianGold)
                }
            }
        }
    }
}

@Composable
private fun GroupMessageBubble(
    msg: GroupMessage,
    isMine: Boolean,
    onPlayVideo: (String) -> Unit
) {
    val timeStr = remember(msg.createdAt) {
        val date = (msg.createdAt ?: Timestamp.now()).toDate()
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isMine) TanzanianGoldDark else SlateCard,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, if (isMine) TanzanianGold.copy(alpha = 0.4f) else DividerSubtle),
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = msg.senderName,
                        style = MaterialTheme.typography.labelLarge,
                        color = TanzanianGold
                    )
                    Text(
                        text = "• ${msg.senderTier} • $timeStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                if (!msg.thumbnailData.isNullOrBlank()) {
                    val bmp = remember(msg.thumbnailData) {
                        try {
                            val bytes = Base64.decode(msg.thumbnailData, Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        } catch (_: Exception) {
                            null
                        }
                    }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = "Shared photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(170.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                } else if (!msg.mediaUrl.isNullOrBlank() && msg.resolvedMediaType() == ChatMediaType.IMAGE) {
                    AsyncImage(
                        model = msg.mediaUrl,
                        contentDescription = "Shared image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else if (!msg.mediaUrl.isNullOrBlank() && msg.resolvedMediaType() == ChatMediaType.VIDEO) {
                    Surface(
                        color = ObsidianBg,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayVideo(msg.mediaUrl) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.PlayCircle, contentDescription = "Play Video", tint = TanzanianGold, modifier = Modifier.size(32.dp))
                            Column {
                                Text("Video Attachment", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text(msg.mediaUrl, color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }
                }

                if (msg.text.isNotBlank()) {
                    Text(
                        text = msg.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateChatRoom(
    chat: PrivateChat,
    currentUserId: String,
    messagesState: UiState<List<PrivateMessage>>,
    strings: UiStrings,
    readOnlyOversight: Boolean,
    onBack: () -> Unit,
    onSend: (String, String?) -> Unit,
    onPlayMediaUrl: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    var draft by remember { mutableStateOf("") }
    val titleNames = chat.participantNames.values.joinToString(" & ").ifBlank { "Private Chat" }

    Scaffold(
        modifier = modifier,
        containerColor = ObsidianBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = OnyxSurface),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                title = {
                    Column {
                        Text(titleNames, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        if (readOnlyOversight) {
                            Text("ADMIN OVERSIGHT VIEW", style = MaterialTheme.typography.labelSmall, color = PremiumCrimson)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (messagesState) {
                    is UiState.Loading -> item { CircularProgressIndicator(color = TanzanianGold) }
                    is UiState.Error -> item { Text(messagesState.message, color = ErrorCoral) }
                    is UiState.Success -> {
                        items(messagesState.data, key = { it.id }) { msg ->
                            val isMine = msg.senderId == currentUserId
                            val timeStr = remember(msg.createdAt) {
                                SimpleDateFormat("HH:mm", Locale.getDefault()).format((msg.createdAt ?: Timestamp.now()).toDate())
                            }
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
                            ) {
                                Surface(
                                    color = if (isMine) TanzaniteTealContainer else SlateCard,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.widthIn(max = 300.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "${msg.senderName} • $timeStr",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TanzanianGold
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(msg.text, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                        if (!msg.mediaUrl.isNullOrBlank()) {
                                            TextButton(onClick = { onPlayMediaUrl(msg.mediaUrl, "Private Video") }) {
                                                Text("Play Attachment")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!readOnlyOversight) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OnyxSurface)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text(strings.typeMessageHint) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        if (draft.isNotBlank()) {
                            onSend(draft, null)
                            draft = ""
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = strings.send, tint = TanzanianGold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateGroupDialog(
    strings: UiStrings,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("Cinema") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OnyxSurface,
        title = { Text(strings.createGroup) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.groupNameHint) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text(strings.groupDescHint) }
                )
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it },
                    label = { Text("Tag") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank() && desc.isNotBlank()) onConfirm(name, desc, tag) },
                colors = ButtonDefaults.buttonColors(containerColor = TanzanianGold, contentColor = ObsidianBg)
            ) {
                Text(strings.createGroup, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
