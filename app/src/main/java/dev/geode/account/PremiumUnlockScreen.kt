package dev.geode.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.geode.R

/**
 * Unwired presentation component: callbacks must resolve current BillingClient
 * offers and perform verification. Displayed prices/ownership are not authority.
 */
@Composable
fun PremiumUnlockScreen(
    state: PremiumUnlockUiState,
    onSelectOffer: (String) -> Unit,
    onPurchase: (PremiumProductUiModel) -> Unit,
    onRestore: () -> Unit,
    onRetry: () -> Unit,
    onPrivacy: () -> Unit,
    onTerms: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onManage: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.account_premium_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
            )
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.account_premium_close)) }
        }
        Text(stringResource(R.string.account_premium_intro), style = MaterialTheme.typography.bodyLarge)

        val ownership = state.ownership
        if (ownership is PremiumOwnershipUiState.Owned) {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.account_premium_owned), style = MaterialTheme.typography.titleMedium)
                    if (ownership.description.isNotBlank()) Text(ownership.description)
                    onManage?.let { manage ->
                        TextButton(onClick = manage, enabled = !state.isBusy) {
                            Text(stringResource(R.string.account_premium_manage))
                        }
                    }
                }
            }
        } else {
            PremiumCatalog(
                state = state,
                onSelectOffer = onSelectOffer,
                onRetry = onRetry,
            )
        }

        PremiumOperationStatus(state.operation)

        if (ownership !is PremiumOwnershipUiState.Owned) {
            Button(
                onClick = { state.selectedProduct()?.let(onPurchase) },
                enabled = state.selectedProduct() != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.account_premium_continue))
            }
        }
        OutlinedButton(
            onClick = { if (state.canRestore) onRestore() },
            enabled = state.canRestore,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.account_premium_restore))
        }
        if (!state.restoreAvailable && state.catalog != PremiumCatalogState.Unconfigured) {
            Text(
                stringResource(R.string.account_premium_restore_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(R.string.account_premium_local_access),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onPrivacy) { Text(stringResource(R.string.account_premium_privacy)) }
            TextButton(onClick = onTerms) { Text(stringResource(R.string.account_premium_terms)) }
        }
    }
}

@Composable
private fun PremiumCatalog(
    state: PremiumUnlockUiState,
    onSelectOffer: (String) -> Unit,
    onRetry: () -> Unit,
) {
    when (val catalog = state.catalog) {
        PremiumCatalogState.Unconfigured -> Text(stringResource(R.string.account_premium_unconfigured))
        PremiumCatalogState.Loading -> PremiumProgress(stringResource(R.string.account_premium_loading))
        PremiumCatalogState.Empty -> Text(stringResource(R.string.account_premium_no_offers))
        is PremiumCatalogState.Error -> {
            Text(
                catalog.message.ifBlank { stringResource(R.string.account_premium_catalog_error) },
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            TextButton(onClick = onRetry, enabled = !state.isBusy) {
                Text(stringResource(R.string.account_premium_retry))
            }
        }
        is PremiumCatalogState.Available -> {
            val products = catalog.products
            if (products.isEmpty()) Text(stringResource(R.string.account_premium_no_offers))
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                products.forEach { product ->
                    PremiumOfferCard(
                        product = product,
                        selected = product.offerKey == state.selectedOfferKey,
                        enabled =
                            !state.isBusy && product.canPurchase &&
                                products.count { it.offerKey == product.offerKey } == 1,
                        onSelect = { onSelectOffer(product.offerKey) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumOfferCard(
    product: PremiumProductUiModel,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    OutlinedCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
        border =
            BorderStroke(
                if (selected) 2.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            ),
    ) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RadioButton(selected = selected, onClick = null, enabled = enabled)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(product.title, style = MaterialTheme.typography.titleMedium)
                Text(product.formattedPrice, style = MaterialTheme.typography.headlineSmall)
                Text(product.recurrence, style = MaterialTheme.typography.bodyMedium)
                Text(product.disclosure, style = MaterialTheme.typography.bodySmall)
                if (!product.canPurchase) {
                    Text(
                        product.unavailableReason?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.account_premium_offer_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumOperationStatus(operation: PremiumOperation) {
    when (operation) {
        PremiumOperation.Idle -> Unit
        PremiumOperation.Purchasing -> PremiumProgress(stringResource(R.string.account_premium_purchasing))
        PremiumOperation.Verifying -> PremiumProgress(stringResource(R.string.account_premium_verifying))
        PremiumOperation.Restoring -> PremiumProgress(stringResource(R.string.account_premium_restoring))
        PremiumOperation.Pending -> {
            Text(
                stringResource(R.string.account_premium_pending),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        PremiumOperation.RestoreFinished -> {
            Text(
                stringResource(R.string.account_premium_restore_finished),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        is PremiumOperation.Failed -> {
            Text(
                operation.message.ifBlank { stringResource(R.string.account_premium_operation_error) },
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun PremiumProgress(message: String) {
    Row(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
        Text(message)
    }
}
