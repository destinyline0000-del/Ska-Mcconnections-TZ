package com.example.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.local.InstallBannerSettings
import com.example.ui.components.SkaBrandHeaderTitle
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.UiStrings
import com.example.ui.i18n.stringsFor
import com.example.ui.movies.BrowserInstallPortalDialog
import com.example.ui.movies.FloatingInstallAppAdCard
import com.example.ui.theme.*

@Composable
fun AuthScreen(
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val strings = stringsFor(language)

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showInstallAd by remember { mutableStateOf(true) }
    var showInstallPortal by remember { mutableStateOf(false) }

    val triggerSignIn: () -> Unit = {
        if (!isLoading) {
            errorMessage = null
            isLoading = true
            onGoogleSignInClicked(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    isLoading = false
                    onAuthSuccess()
                },
                onAuthError = { msg ->
                    isLoading = false
                    errorMessage = msg
                },
                scope = scope,
                onAuthCancelled = {
                    isLoading = false
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = { },
            scope = scope
        )
    }

    Scaffold(
        containerColor = ObsidianBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // 1. Top Header Bar matching reference cover
            AuthCoverTopBar(
                language = language,
                strings = strings,
                isLoading = isLoading,
                onLanguageChange = onLanguageChange,
                onLoginClick = triggerSignIn
            )

            // Golden hairline divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, GoldBorderGlow, TanzanianGold, GoldBorderGlow, Color.Transparent)
                        )
                    )
            )

            // 2. Dramatic Hero Cover with "SKA MACCONNECTIONS TZ / Watch • Download • Enjoy / Start Watching" + Floating Install Ad
            AuthHeroCoverBanner(
                strings = strings,
                isLoading = isLoading,
                showInstallAd = showInstallAd,
                onStartWatching = triggerSignIn,
                onInstallClick = { showInstallPortal = true },
                onDismissAd = { showInstallAd = false }
            )

            // 3. Category Tiles Strip (Action, Comedy, Romance, Horror, Sci-Fi, Series, Premium, All Categories)
            AuthCategoryStrip(
                strings = strings,
                onCategoryClick = triggerSignIn
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Featured Movies Preview Row (Watch + Download buttons)
            AuthFeaturedPreviewSection(
                strings = strings,
                onPosterAction = triggerSignIn
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 5. Secure & Trusted Shield + Google Sign-In CTA + Support Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (errorMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ErrorCoral.copy(alpha = 0.16f)),
                        border = BorderStroke(1.dp, ErrorCoral.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage.orEmpty(),
                            color = ErrorCoral,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Secure & Trusted Golden Shield Badge
                Surface(
                    color = GoldStripSurface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, GoldBorderGlow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(TanzanianGold.copy(alpha = 0.14f))
                                .border(1.dp, GoldBorderGlow, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VerifiedUser,
                                contentDescription = strings.secureTrustedTitle,
                                tint = TanzanianGold,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.secureTrustedTitle.replace("\n", " "),
                                style = MaterialTheme.typography.titleMedium,
                                color = TanzanianGold,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = strings.secureTrustedSubtitle.replace("\n", " — "),
                                style = MaterialTheme.typography.bodySmall,
                                color = TanzanianGoldLight
                            )
                        }
                    }
                }

                Button(
                    onClick = triggerSignIn,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TanzanianGold,
                        contentColor = ObsidianBg
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("google_sign_in_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = ObsidianBg,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(strings.signingIn, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.signInWithGoogle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Bottom Support & Brand Pill Footer matching reference
                Surface(
                    color = OnyxSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, DividerSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SupportAgent,
                                contentDescription = null,
                                tint = TanzanianGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = strings.needHelpContact,
                                style = MaterialTheme.typography.labelMedium,
                                color = TanzanianGoldLight,
                                textAlign = TextAlign.Center
                            )
                        }
                        Surface(
                            color = TanzanianGold,
                            shape = RoundedCornerShape(22.dp)
                        ) {
                            Text(
                                text = strings.moviesAnytimePill,
                                color = ObsidianBg,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showInstallPortal) {
        BrowserInstallPortalDialog(
            settings = InstallBannerSettings(bannerEnabled = true),
            language = language,
            strings = strings,
            onDismiss = { showInstallPortal = false },
            onStatusMessage = { msg -> errorMessage = msg }
        )
    }
}

@Composable
private fun AuthCoverTopBar(
    language: AppLanguage,
    strings: UiStrings,
    isLoading: Boolean,
    onLanguageChange: (AppLanguage) -> Unit,
    onLoginClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(OnyxSurface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SkaBrandHeaderTitle(
                subtitle = strings.premiumBrandSubtitle,
                compact = true,
                modifier = Modifier.weight(1f)
            )

            OutlinedButton(
                onClick = onLoginClick,
                enabled = !isLoading,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.2.dp, TanzanianGold),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = ObsidianBg.copy(alpha = 0.7f),
                    contentColor = TanzanianGoldLight
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.defaultMinSize(minHeight = 38.dp)
            ) {
                Text(
                    text = strings.loginSignUp,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }

        // Language chips row (Swahili / English) matching top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppLanguage.entries.forEach { lang ->
                val selected = lang == language
                FilterChip(
                    selected = selected,
                    onClick = { onLanguageChange(lang) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Language,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text(lang.label, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TanzanianGold,
                        selectedLabelColor = ObsidianBg,
                        selectedLeadingIconColor = ObsidianBg,
                        containerColor = SlateCard,
                        labelColor = TextPrimary,
                        iconColor = TanzanianGold
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = GoldBorderGlow
                    ),
                    modifier = Modifier.testTag("auth_lang_${lang.code}")
                )
            }
        }
    }
}

@Composable
private fun AuthHeroCoverBanner(
    strings: UiStrings,
    isLoading: Boolean,
    showInstallAd: Boolean,
    onStartWatching: () -> Unit,
    onInstallClick: () -> Unit,
    onDismissAd: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 270.dp)
    ) {
        val isWide = maxWidth >= 560.dp

        Image(
            painter = painterResource(id = R.drawable.img_hero_banner),
            contentDescription = strings.appTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        0.0f to ObsidianBg.copy(alpha = 0.45f),
                        0.45f to ObsidianBg.copy(alpha = 0.78f),
                        1.0f to ObsidianBg.copy(alpha = 0.94f)
                    )
                )
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to ObsidianBg.copy(alpha = 0.25f),
                        0.75f to Color.Transparent,
                        1.0f to ObsidianBg.copy(alpha = 0.96f)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showInstallAd && isWide) {
                FloatingInstallAppAdCard(
                    strings = strings,
                    onInstallClick = onInstallClick,
                    onDismiss = onDismissAd,
                    modifier = Modifier.align(Alignment.End)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.End),
                horizontalAlignment = if (isWide) Alignment.End else Alignment.Start
            ) {
                Text(
                    text = "SKA",
                    color = TanzanianGold,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    letterSpacing = 2.sp,
                    lineHeight = 44.sp
                )
                Text(
                    text = "MACCONNECTIONS TZ",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontStyle = FontStyle.Italic,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = strings.watchDownloadEnjoy,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onStartWatching,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TanzanianGold,
                        contentColor = ObsidianBg
                    ),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = strings.startWatching,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }

            if (showInstallAd && !isWide) {
                Spacer(modifier = Modifier.height(6.dp))
                FloatingInstallAppAdCard(
                    strings = strings,
                    onInstallClick = onInstallClick,
                    onDismiss = onDismissAd,
                    modifier = Modifier.align(Alignment.End)
                )
            }

            // Carousel indicator dots matching screenshot
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) { idx ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (idx == 1) 9.dp else 7.dp)
                            .clip(CircleShape)
                            .background(if (idx == 1) TanzanianGold else TextSecondary.copy(alpha = 0.5f))
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthCategoryStrip(
    strings: UiStrings,
    onCategoryClick: () -> Unit
) {
    val categories = remember(strings) {
        listOf(
            Icons.Filled.LocalMovies to strings.catAction,
            Icons.Filled.TheaterComedy to strings.catComedy,
            Icons.Filled.Favorite to strings.catRomance,
            Icons.Filled.Visibility to strings.catHorror,
            Icons.Filled.RocketLaunch to strings.catSciFi,
            Icons.Filled.LiveTv to strings.catSeries,
            Icons.Filled.WorkspacePremium to strings.catPremium,
            Icons.Filled.GridView to strings.catAll
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianBg)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        categories.forEach { (icon, label) ->
            CategoryBoxTile(
                icon = icon,
                label = label,
                selected = false,
                onClick = onCategoryClick
            )
        }
    }
}

@Composable
fun CategoryBoxTile(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) TanzanianGoldDark else GoldStripSurface,
        border = BorderStroke(1.dp, if (selected) TanzanianGold else GoldBorderGlow.copy(alpha = 0.65f)),
        modifier = modifier
            .width(86.dp)
            .height(74.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = TanzanianGold,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = label,
                color = if (selected) TanzanianGoldLight else TextPrimary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class PreviewPosterSpec(
    val title: String,
    val tier: String,
    val drawableRes: Int
)

@Composable
private fun AuthFeaturedPreviewSection(
    strings: UiStrings,
    onPosterAction: () -> Unit
) {
    val posters = remember {
        listOf(
            PreviewPosterSpec("Ska TZ Showcase", "FREE", R.drawable.img_poster_showcase),
            PreviewPosterSpec("Dar es Salaam Nights", "FREE", R.drawable.img_poster_free),
            PreviewPosterSpec("Kilimanjaro Horizon", "VIP", R.drawable.img_poster_vip),
            PreviewPosterSpec("Zanzibar Crown", "PREMIUM", R.drawable.img_poster_premium)
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = TanzanianGold, modifier = Modifier.size(22.dp))
                Text(
                    text = strings.featuredMovies,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
            }
            TextButton(onClick = onPosterAction) {
                Text(strings.viewAll, color = TanzanianGoldLight, fontWeight = FontWeight.Bold)
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(posters) { item ->
                Column(
                    modifier = Modifier
                        .width(148.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SlateCard)
                        .border(1.dp, DividerSubtle, RoundedCornerShape(14.dp))
                        .clickable(onClick = onPosterAction)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                    ) {
                        Image(
                            painter = painterResource(id = item.drawableRes),
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            color = ObsidianBg.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, GoldBorderGlow),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = item.tier,
                                color = TanzanianGoldLight,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                color = TanzanianGold,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = ObsidianBg, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(2.dp))
                                    Text(strings.watchButton, color = ObsidianBg, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                            Surface(
                                color = ObsidianBg,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, GoldBorderGlow),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null, tint = TanzanianGold, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(2.dp))
                                    Text(strings.downloadOffline, color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
