package com.example.ui.movies

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.MobileMoneyProvider
import com.example.data.model.MovieTier
import com.example.data.model.PaymentRequest
import com.example.ui.i18n.UiStrings
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileMoneyUpgradeSheet(
    initialTier: MovieTier,
    strings: UiStrings,
    existingRequests: List<PaymentRequest>,
    onSubmitPayment: (MobileMoneyProvider, String, MovieTier) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTier by remember(initialTier) {
        mutableStateOf(if (initialTier == MovieTier.PREMIUM) MovieTier.PREMIUM else MovieTier.VIP)
    }
    var selectedProvider by remember { mutableStateOf(MobileMoneyProvider.MPESA) }
    var phoneNumber by remember { mutableStateOf("") }
    var phoneError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = OnyxSurface,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = TanzanianGold)
                Text(
                    text = strings.mobileMoneyCheckout,
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            // Tier selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TierChoiceCard(
                    title = "VIP",
                    priceLabel = "10,000 TZS",
                    selected = selectedTier == MovieTier.VIP,
                    accent = VipSapphire,
                    onClick = { selectedTier = MovieTier.VIP },
                    modifier = Modifier.weight(1f)
                )
                TierChoiceCard(
                    title = "PREMIUM",
                    priceLabel = "25,000 TZS",
                    selected = selectedTier == MovieTier.PREMIUM,
                    accent = TanzanianGold,
                    onClick = { selectedTier = MovieTier.PREMIUM },
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = strings.selectProvider,
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MobileMoneyProvider.entries.forEach { provider ->
                    val isSelected = provider == selectedProvider
                    Surface(
                        onClick = { selectedProvider = provider },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) TanzaniteTealContainer else SlateCard,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) TanzaniteTeal else DividerSubtle
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("provider_${provider.name}")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(provider.displayName, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Text("USSD: ${provider.ussdCode}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                            if (isSelected) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = TanzaniteTeal)
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = phoneNumber,
                onValueChange = {
                    phoneNumber = it
                    phoneError = null
                },
                label = { Text(strings.phoneHint) },
                singleLine = true,
                isError = phoneError != null,
                supportingText = phoneError?.let { { Text(it, color = ErrorCoral) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mobile_money_phone_input")
            )

            Button(
                onClick = {
                    val digits = phoneNumber.filter { it.isDigit() }
                    if (digits.length < 9) {
                        phoneError = strings.phoneHint
                    } else {
                        onSubmitPayment(selectedProvider, phoneNumber.trim(), selectedTier)
                        onDismiss()
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TanzanianGold,
                    contentColor = ObsidianBg
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("submit_mobile_money_button")
            ) {
                Icon(Icons.Filled.Star, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(strings.payAndSubmit, fontWeight = FontWeight.Bold)
            }

            if (existingRequests.isNotEmpty()) {
                HorizontalDivider(color = DividerSubtle)
                Text(
                    text = strings.pendingApprovals,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
                existingRequests.take(3).forEach { req ->
                    Surface(
                        color = SlateCard,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("${req.targetTier} • ${req.provider}", fontWeight = FontWeight.SemiBold)
                                Text("${req.referenceCode} • ${req.amountTzs} TZS", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                            Badge(
                                containerColor = when (req.status) {
                                    "APPROVED" -> TanzaniteTealContainer
                                    "REJECTED" -> PremiumCrimsonContainer
                                    else -> TanzanianGoldDark
                                }
                            ) {
                                Text(
                                    text = req.status,
                                    color = when (req.status) {
                                        "APPROVED" -> TanzaniteTeal
                                        "REJECTED" -> ErrorCoral
                                        else -> TanzanianGold
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun TierChoiceCard(
    title: String,
    priceLabel: String,
    selected: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) accent.copy(alpha = 0.16f) else SlateCard,
        border = BorderStroke(1.5.dp, if (selected) accent else DividerSubtle),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = accent, fontWeight = FontWeight.Bold)
            Text(priceLabel, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
    }
}
