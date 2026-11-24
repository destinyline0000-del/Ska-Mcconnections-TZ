package com.example.ui.movies

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.InstallBannerSettings
import com.example.data.model.*
import com.example.ui.auth.CategoryBoxTile
import com.example.ui.i18n.UiStrings
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun MoviesScreen(
    moviesState: UiState<List<Movie>>,
    userProfile: UserProfile?,
    offlineMovies: List<OfflineMovieItem>,
    downloadsProgress: Map<String, DownloadProgressState>,
    installSettings: InstallBannerSettings,
    strings: UiStrings,
    onPlayMovie: (Movie, String?) -> Unit,
    onDownloadMovie: (Movie) -> Unit,
    onOpenUpgradeSheet: (MovieTier) -> Unit,
    onOpenInstallPortal: () -> Unit,
    onDismissInstallBanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<MovieTier?>(null) }
    var selectedCategoryKey by remember { mutableStateOf<String?>(null) }
    var showAllExpanded by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val offlineById = remember(offlineMovies) {
        offlineMovies.associateBy { it.movieId }
    }

    val bundledShowcase = remember(strings) {
        Movie(
            id = "bundled_ska_showcase",
            title = strings.featuredShowcaseTitle,
            description = strings.featuredShowcaseDesc,
            tier = "FREE",
            videoUrl = "bundled://sample_showcase",
            resolvedStreamUrl = "bundled://sample_showcase",
            linkType = "BUNDLED_CLIP",
            genre = "Showcase",
            durationMinutes = 1L,
            driveAccountSlot = 0L,
            published = true
        )
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero Cover Banner + Floating Install Ad + Category Icon Tiles Strip
        item(key = "hero_and_categories") {
            Column {
                HeroCinemaBanner(
                    showcaseMovie = bundledShowcase,
                    userProfile = userProfile,
                    showFloatingInstallAd = installSettings.bannerEnabled && !installSettings.bannerDismissed,
                    strings = strings,
                    onStartWatching = {
                        onPlayMovie(bundledShowcase, offlineById[bundledShowcase.id]?.localFilePath)
                    },
                    onBrowseClick = {
                        scope.launch { listState.animateScrollToItem(2) }
                    },
                    onUpgradeVip = { onOpenUpgradeSheet(MovieTier.VIP) },
                    onOpenInstallPortal = onOpenInstallPortal,
                    onDismissInstallBanner = onDismissInstallBanner
                )

                CategoryTilesStrip(
                    strings = strings,
                    selectedCategoryKey = selectedCategoryKey,
                    selectedTier = selectedFilter,
                    onSelectAll = {
                        selectedCategoryKey = null
                        selectedFilter = null
                    },
                    onSelectPremium = {
                        selectedCategoryKey = null
                        selectedFilter = MovieTier.PREMIUM
                    },
                    onSelectGenre = { genreKey ->
                        selectedFilter = null
                        selectedCategoryKey = if (selectedCategoryKey == genreKey) null else genreKey
                    }
                )
            }
        }

        // 2. "Featured Movies" Poster Carousel with Watch & Download buttons directly on each card
        if (moviesState is UiState.Success) {
            val featuredList = (moviesState.data.ifEmpty { listOf(bundledShowcase) })
            item(key = "featured_carousel") {
                FeaturedMoviesCarousel(
                    movies = featuredList,
                    userProfile = userProfile,
                    offlineById = offlineById,
                    strings = strings,
                    showAllExpanded = showAllExpanded,
                    onToggleViewAll = {
                        showAllExpanded = !showAllExpanded
                        selectedFilter = null
                        selectedCategoryKey = null
                        scope.launch { listState.animateScrollToItem(2) }
                    },
                    onWatchClick = { movie ->
                        val tier = movie.resolvedTier()
                        val unlocked = userProfile?.canWatch(tier) ?: (tier == MovieTier.FREE)
                        if (unlocked) {
                            onPlayMovie(movie, offlineById[movie.id]?.localFilePath)
                        } else {
                            onOpenUpgradeSheet(tier)
                        }
                    },
                    onDownloadClick = { movie ->
                        val tier = movie.resolvedTier()
                        val unlocked = userProfile?.canWatch(tier) ?: (tier == MovieTier.FREE)
                        if (unlocked) {
                            onDownloadMovie(movie)
                        } else {
                            onOpenUpgradeSheet(tier)
                        }
                    }
                )
            }
        }

        // 3. Search + Tier Filter Chips
        item(key = "search_and_filters") {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(strings.searchMoviesHint) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TanzanianGold) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TanzanianGold,
                        unfocusedBorderColor = DividerSubtle,
                        focusedContainerColor = OnyxSurface,
                        unfocusedContainerColor = OnyxSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("movies_search_input")
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedFilter == null && selectedCategoryKey == null,
                            onClick = {
                                selectedFilter = null
                                selectedCategoryKey = null
                            },
                            label = { Text(strings.allMovies) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TanzanianGold,
                                selectedLabelColor = ObsidianBg
                            )
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedFilter == MovieTier.FREE,
                            onClick = { selectedFilter = MovieTier.FREE },
                            label = { Text(strings.freeMovies) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TanzaniteTeal,
                                selectedLabelColor = ObsidianBg
                            )
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedFilter == MovieTier.VIP,
                            onClick = { selectedFilter = MovieTier.VIP },
                            label = { Text(strings.vipMovies) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VipSapphire,
                                selectedLabelColor = TextPrimary
                            )
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedFilter == MovieTier.PREMIUM,
                            onClick = { selectedFilter = MovieTier.PREMIUM },
                            label = { Text(strings.premiumMovies) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TanzanianGold,
                                selectedLabelColor = ObsidianBg
                            )
                        )
                    }
                }
            }
        }

        // 4. Free / VIP / Premium Movie Sections
        when (moviesState) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = TanzanianGold)
                    }
                }
            }
            is UiState.Error -> {
                item {
                    Surface(
                        color = PremiumCrimsonContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = moviesState.message,
                            color = TextPrimary,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            is UiState.Success -> {
                val allMovies = moviesState.data.ifEmpty { listOf(bundledShowcase) }
                val filtered = allMovies.filter { movie ->
                    val matchesTier = selectedFilter == null || movie.resolvedTier() == selectedFilter
                    val cat = selectedCategoryKey?.lowercase()
                    val matchesCategory = cat == null ||
                        movie.genre.lowercase().contains(cat) ||
                        movie.title.lowercase().contains(cat) ||
                        movie.description.lowercase().contains(cat)
                    val q = searchQuery.trim().lowercase()
                    val matchesSearch = q.isBlank() ||
                        movie.title.lowercase().contains(q) ||
                        movie.genre.lowercase().contains(q) ||
                        movie.description.lowercase().contains(q)
                    matchesTier && matchesCategory && matchesSearch
                }

                val tiersToShow = if (selectedFilter != null) listOf(selectedFilter!!) else listOf(
                    MovieTier.FREE,
                    MovieTier.VIP,
                    MovieTier.PREMIUM
                )

                tiersToShow.forEach { tier ->
                    val sectionMovies = filtered.filter { it.resolvedTier() == tier }
                    item(key = "header_${tier.name}") {
                        TierSectionHeader(
                            tier = tier,
                            count = sectionMovies.size,
                            strings = strings,
                            userProfile = userProfile,
                            onUpgradeClick = { onOpenUpgradeSheet(tier) },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    if (sectionMovies.isEmpty()) {
                        item(key = "empty_${tier.name}") {
                            Surface(
                                color = OnyxSurface,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, DividerSubtle),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                Text(
                                    text = strings.noMoviesYet,
                                    color = TextMuted,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        itemsIndexed(sectionMovies, key = { _, movie -> movie.id }) { idx, movie ->
                            val canWatch = userProfile?.canWatch(tier) ?: (tier == MovieTier.FREE)
                            MovieCard(
                                movie = movie,
                                fallbackDrawable = fallbackPosterFor(movie, idx),
                                canWatch = canWatch,
                                offlinePath = offlineById[movie.id]?.localFilePath,
                                downloadProgress = downloadsProgress[movie.id],
                                strings = strings,
                                onPlay = { onPlayMovie(movie, offlineById[movie.id]?.localFilePath) },
                                onDownload = { onDownloadMovie(movie) },
                                onUpgrade = { onOpenUpgradeSheet(tier) },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }
        }

        // 5. Secure & Trusted + Bottom Support Banner matching reference footer
        item(key = "trust_footer") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = GoldStripSurface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, GoldBorderGlow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VerifiedUser,
                            contentDescription = strings.secureTrustedTitle,
                            tint = TanzanianGold,
                            modifier = Modifier.size(30.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.secureTrustedTitle.replace("\n", " "),
                                color = TanzanianGold,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "${strings.secureTrustedSubtitle.replace("\n", " — ")} • ${strings.needHelpContact}",
                                color = TextSecondary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
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
    }
}

private fun fallbackPosterFor(movie: Movie, index: Int): Int {
    if (movie.id == "bundled_ska_showcase") return R.drawable.img_poster_showcase
    return when (movie.resolvedTier()) {
        MovieTier.FREE -> if (index % 2 == 0) R.drawable.img_poster_showcase else R.drawable.img_poster_free
        MovieTier.VIP -> R.drawable.img_poster_vip
        MovieTier.PREMIUM -> R.drawable.img_poster_premium
    }
}

@Composable
private fun HeroCinemaBanner(
    showcaseMovie: Movie,
    userProfile: UserProfile?,
    showFloatingInstallAd: Boolean,
    strings: UiStrings,
    onStartWatching: () -> Unit,
    onBrowseClick: () -> Unit,
    onUpgradeVip: () -> Unit,
    onOpenInstallPortal: () -> Unit,
    onDismissInstallBanner: () -> Unit
) {
    val tier = userProfile?.resolvedTier() ?: MovieTier.FREE
    val role = userProfile?.resolvedRole() ?: UserRole.FREE

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 256.dp)
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
                        0.0f to ObsidianBg.copy(alpha = 0.55f),
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
                        0.76f to Color.Transparent,
                        1.0f to ObsidianBg.copy(alpha = 0.96f)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = ObsidianBg.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, GoldBorderGlow)
                ) {
                    Text(
                        text = if (role == UserRole.ADMIN) "ADMIN • PREMIUM" else "TIER: ${tier.name}",
                        color = TanzanianGoldLight,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                if (tier == MovieTier.FREE && role != UserRole.ADMIN) {
                    Surface(
                        color = TanzanianGold.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, TanzanianGold.copy(alpha = 0.55f)),
                        modifier = Modifier.clickable(onClick = onUpgradeVip)
                    ) {
                        Text(
                            text = strings.upgradeToVip,
                            color = TanzanianGoldLight,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            if (showFloatingInstallAd && isWide) {
                FloatingInstallAppAdCard(
                    strings = strings,
                    onInstallClick = onOpenInstallPortal,
                    onDismiss = onDismissInstallBanner,
                    modifier = Modifier.align(Alignment.End)
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
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
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontStyle = FontStyle.Italic,
                    letterSpacing = 1.sp
                )
                Text(
                    text = strings.watchDownloadEnjoy,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(if (isWide) Alignment.End else Alignment.Start)
                    .horizontalScroll(rememberScrollState())
            ) {
                Button(
                    onClick = onStartWatching,
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TanzanianGold,
                        contentColor = ObsidianBg
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("hero_play_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = strings.startWatching,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                OutlinedButton(
                    onClick = onBrowseClick,
                    shape = RoundedCornerShape(26.dp),
                    border = BorderStroke(1.dp, GoldBorderGlow),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = ObsidianBg.copy(alpha = 0.7f),
                        contentColor = TextPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("hero_browse_button")
                ) {
                    Text(
                        text = strings.browseMovies,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = onOpenInstallPortal,
                    shape = RoundedCornerShape(26.dp),
                    border = BorderStroke(1.dp, GoldBorderGlow),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = ObsidianBg.copy(alpha = 0.7f),
                        contentColor = TanzanianGoldLight
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("hero_get_app_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.InstallMobile,
                        contentDescription = null,
                        tint = TanzanianGold,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = strings.getPhoneApp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            if (showFloatingInstallAd && !isWide) {
                Spacer(modifier = Modifier.height(6.dp))
                FloatingInstallAppAdCard(
                    strings = strings,
                    onInstallClick = onOpenInstallPortal,
                    onDismiss = onDismissInstallBanner,
                    modifier = Modifier.align(Alignment.End)
                )
            }

            // Carousel pagination dots matching reference
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
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
private fun CategoryTilesStrip(
    strings: UiStrings,
    selectedCategoryKey: String?,
    selectedTier: MovieTier?,
    onSelectAll: () -> Unit,
    onSelectPremium: () -> Unit,
    onSelectGenre: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianBg)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, TanzanianGold, TanzanianGoldLight, TanzanianGold, Color.Transparent)
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CategoryBoxTile(
                icon = Icons.Filled.LocalMovies,
                label = strings.catAction,
                selected = selectedCategoryKey == "action",
                onClick = { onSelectGenre("action") }
            )
            CategoryBoxTile(
                icon = Icons.Filled.TheaterComedy,
                label = strings.catComedy,
                selected = selectedCategoryKey == "comedy",
                onClick = { onSelectGenre("comedy") }
            )
            CategoryBoxTile(
                icon = Icons.Filled.Favorite,
                label = strings.catRomance,
                selected = selectedCategoryKey == "romance",
                onClick = { onSelectGenre("romance") }
            )
            CategoryBoxTile(
                icon = Icons.Filled.Visibility,
                label = strings.catHorror,
                selected = selectedCategoryKey == "thriller",
                onClick = { onSelectGenre("thriller") }
            )
            CategoryBoxTile(
                icon = Icons.Filled.RocketLaunch,
                label = strings.catSciFi,
                selected = selectedCategoryKey == "sci",
                onClick = { onSelectGenre("sci") }
            )
            CategoryBoxTile(
                icon = Icons.Filled.LiveTv,
                label = strings.catSeries,
                selected = selectedCategoryKey == "drama",
                onClick = { onSelectGenre("drama") }
            )
            CategoryBoxTile(
                icon = Icons.Filled.WorkspacePremium,
                label = strings.catPremium,
                selected = selectedTier == MovieTier.PREMIUM,
                onClick = onSelectPremium
            )
            CategoryBoxTile(
                icon = Icons.Filled.GridView,
                label = strings.catAll,
                selected = selectedCategoryKey == null && selectedTier == null,
                onClick = onSelectAll
            )
        }
    }
}

@Composable
private fun FeaturedMoviesCarousel(
    movies: List<Movie>,
    userProfile: UserProfile?,
    offlineById: Map<String, OfflineMovieItem>,
    strings: UiStrings,
    showAllExpanded: Boolean,
    onToggleViewAll: () -> Unit,
    onWatchClick: (Movie) -> Unit,
    onDownloadClick: (Movie) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = TanzanianGold,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = strings.featuredMovies,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
            }

            OutlinedButton(
                onClick = onToggleViewAll,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, GoldBorderGlow),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier
                    .defaultMinSize(minHeight = 36.dp)
                    .testTag("trending_view_all_button")
            ) {
                Text(
                    text = if (showAllExpanded) strings.showAllMovies else strings.viewAll,
                    color = TextPrimary,
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = TanzanianGold,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(movies, key = { _, m -> "trending_${m.id}" }) { index, movie ->
                val tier = movie.resolvedTier()
                val canWatch = userProfile?.canWatch(tier) ?: (tier == MovieTier.FREE)
                FeaturedPosterCard(
                    movie = movie,
                    fallbackDrawable = fallbackPosterFor(movie, index),
                    canWatch = canWatch,
                    isSavedOffline = offlineById.containsKey(movie.id),
                    strings = strings,
                    onWatch = { onWatchClick(movie) },
                    onDownload = { onDownloadClick(movie) }
                )
            }
        }
    }
}

@Composable
private fun FeaturedPosterCard(
    movie: Movie,
    fallbackDrawable: Int,
    canWatch: Boolean,
    isSavedOffline: Boolean,
    strings: UiStrings,
    onWatch: () -> Unit,
    onDownload: () -> Unit
) {
    val tier = movie.resolvedTier()
    val tierColor = when (tier) {
        MovieTier.FREE -> TanzaniteTeal
        MovieTier.VIP -> VipSapphire
        MovieTier.PREMIUM -> TanzanianGold
    }

    Column(
        modifier = Modifier
            .width(164.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SlateCard)
            .border(1.dp, DividerSubtle, RoundedCornerShape(14.dp))
            .testTag("trending_poster_${movie.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clickable(onClick = onWatch)
        ) {
            if (!movie.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = movie.posterUrl,
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    painter = painterResource(id = fallbackDrawable),
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, ObsidianBg.copy(alpha = 0.92f))
                        )
                    )
            )

            Surface(
                color = ObsidianBg.copy(alpha = 0.85f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, tierColor.copy(alpha = 0.8f)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Text(
                    text = tier.name,
                    color = tierColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }

            if (!canWatch) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(ObsidianBg.copy(alpha = 0.84f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Locked",
                        tint = tierColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Surface(
                color = TanzanianGold,
                shape = RoundedCornerShape(5.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                Text(
                    text = "HD",
                    color = ObsidianBg,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Text(
                text = if (movie.driveAccountSlot > 0) "D#${movie.driveAccountSlot}" else "${movie.durationMinutes}m",
                color = TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            )
        }

        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = movie.title,
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    onClick = onWatch,
                    color = if (canWatch) TanzanianGold else tierColor,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (canWatch) Icons.Filled.PlayArrow else Icons.Filled.LockOpen,
                            contentDescription = null,
                            tint = ObsidianBg,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = if (canWatch) strings.watchButton else tier.name,
                            color = ObsidianBg,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }
                }

                Surface(
                    onClick = if (isSavedOffline) onWatch else onDownload,
                    color = ObsidianBg,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (isSavedOffline) TanzaniteTeal else GoldBorderGlow),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSavedOffline) Icons.Filled.CloudDone else Icons.Filled.Download,
                            contentDescription = null,
                            tint = if (isSavedOffline) TanzaniteTeal else TanzanianGold,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = if (isSavedOffline) "Saved" else strings.downloadOffline,
                            color = if (isSavedOffline) TanzaniteTeal else TextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TierSectionHeader(
    tier: MovieTier,
    count: Int,
    strings: UiStrings,
    userProfile: UserProfile?,
    onUpgradeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (title, accent) = when (tier) {
        MovieTier.FREE -> strings.freeMovies to TanzaniteTeal
        MovieTier.VIP -> strings.vipMovies to VipSapphire
        MovieTier.PREMIUM -> strings.premiumMovies to TanzanianGold
    }
    val unlocked = userProfile?.canWatch(tier) ?: (tier == MovieTier.FREE)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Text(
                text = "$title ($count)",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
        }
        if (!unlocked) {
            TextButton(onClick = onUpgradeClick) {
                Icon(Icons.Filled.LockOpen, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (tier == MovieTier.VIP) strings.upgradeToVip else strings.upgradeToPremium,
                    color = accent,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun MovieCard(
    movie: Movie,
    fallbackDrawable: Int,
    canWatch: Boolean,
    offlinePath: String?,
    downloadProgress: DownloadProgressState?,
    strings: UiStrings,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tier = movie.resolvedTier()
    val tierColor = when (tier) {
        MovieTier.FREE -> TanzaniteTeal
        MovieTier.VIP -> VipSapphire
        MovieTier.PREMIUM -> TanzanianGold
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        border = BorderStroke(1.dp, if (tier == MovieTier.PREMIUM) GoldBorderGlow else DividerSubtle),
        modifier = modifier
            .fillMaxWidth()
            .testTag("movie_card_${movie.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(164.dp)
                    .background(SlateCardElevated)
            ) {
                if (!movie.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = movie.posterUrl,
                        contentDescription = movie.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Image(
                        painter = painterResource(id = fallbackDrawable),
                        contentDescription = movie.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(ObsidianBg.copy(alpha = 0.25f), ObsidianBg.copy(alpha = 0.72f))
                            )
                        )
                )

                Surface(
                    color = ObsidianBg.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, tierColor),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    Text(
                        text = tier.name,
                        color = tierColor,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Surface(
                    color = ObsidianBg.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    val sourceText = if (movie.driveAccountSlot > 0) {
                        "Drive #${movie.driveAccountSlot}"
                    } else {
                        movie.resolvedLinkType().name.replace('_', ' ')
                    }
                    Text(
                        text = sourceText,
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Surface(
                    color = TanzanianGold,
                    shape = RoundedCornerShape(5.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "HD",
                        color = ObsidianBg,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }

                if (!canWatch) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(ObsidianBg.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Locked",
                            tint = tierColor,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = movie.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${movie.durationMinutes}m • ${movie.genre}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                Text(
                    text = if (canWatch) movie.description else {
                        if (tier == MovieTier.VIP) strings.lockedVipDesc else strings.lockedPremiumDesc
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (downloadProgress != null && !downloadProgress.isCompleted && downloadProgress.errorMessage == null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { downloadProgress.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = TanzanianGold,
                            trackColor = DividerSubtle
                        )
                        Text(
                            text = "${strings.downloading} ${downloadProgress.progressPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = TanzanianGold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (canWatch) {
                        Button(
                            onClick = onPlay,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TanzanianGold,
                                contentColor = ObsidianBg
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.playNow, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = if (offlinePath != null) onPlay else onDownload,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (offlinePath != null) TanzaniteTeal else GoldBorderGlow),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (offlinePath != null) Icons.Filled.CloudDone else Icons.Filled.Download,
                                contentDescription = null,
                                tint = if (offlinePath != null) TanzaniteTeal else TanzanianGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (offlinePath != null) strings.downloaded else strings.downloadOffline,
                                color = if (offlinePath != null) TanzaniteTeal else TextPrimary,
                                maxLines = 1
                            )
                        }
                    } else {
                        Button(
                            onClick = onUpgrade,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = tierColor,
                                contentColor = ObsidianBg
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("upgrade_prompt_${movie.id}")
                        ) {
                            Icon(Icons.Filled.WorkspacePremium, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (tier == MovieTier.VIP) strings.upgradeToVip else strings.upgradeToPremium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
