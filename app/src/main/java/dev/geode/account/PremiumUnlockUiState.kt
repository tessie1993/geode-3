package dev.geode.account

/** Display-only projection of fresh Play product details; it never grants an entitlement. */
data class PremiumProductUiModel(
    val offerKey: String,
    val productId: String,
    val kind: PremiumProductKind,
    val title: String,
    val formattedPrice: String,
    val recurrence: String,
    val disclosure: String,
    val available: Boolean,
    val unavailableReason: String? = null,
) {
    val canPurchase: Boolean
        get() =
            available &&
                offerKey.isNotBlank() &&
                title.isNotBlank() &&
                formattedPrice.isNotBlank() &&
                recurrence.isNotBlank() &&
                disclosure.isNotBlank() &&
                PremiumProduct.fromId(productId)?.kind == kind
}

sealed interface PremiumCatalogState {
    data object Unconfigured : PremiumCatalogState

    data object Loading : PremiumCatalogState

    data object Empty : PremiumCatalogState

    data class Error(val message: String) : PremiumCatalogState

    /** Copy at both boundaries so a mutable source list cannot change a published state. */
    class Available(products: List<PremiumProductUiModel>) : PremiumCatalogState {
        private val snapshot = products.toList()
        val products: List<PremiumProductUiModel> get() = snapshot.toList()
    }
}

sealed interface PremiumOperation {
    data object Idle : PremiumOperation

    data object Purchasing : PremiumOperation

    data object Pending : PremiumOperation

    data object Verifying : PremiumOperation

    data object Restoring : PremiumOperation

    data object RestoreFinished : PremiumOperation

    data class Failed(val message: String) : PremiumOperation
}

sealed interface PremiumOwnershipUiState {
    data object NotOwned : PremiumOwnershipUiState

    /** Render only from a valid access decision; this display state is never proof of payment. */
    data class Owned(val description: String) : PremiumOwnershipUiState
}

data class PremiumUnlockUiState(
    val catalog: PremiumCatalogState = PremiumCatalogState.Unconfigured,
    val selectedOfferKey: String? = null,
    val operation: PremiumOperation = PremiumOperation.Idle,
    val ownership: PremiumOwnershipUiState = PremiumOwnershipUiState.NotOwned,
    val restoreAvailable: Boolean = false,
) {
    val isBusy: Boolean
        get() =
            operation == PremiumOperation.Purchasing ||
                operation == PremiumOperation.Pending ||
                operation == PremiumOperation.Verifying ||
                operation == PremiumOperation.Restoring

    val canRestore: Boolean
        get() = restoreAvailable && !isBusy && catalog != PremiumCatalogState.Unconfigured

    fun selectedProduct(): PremiumProductUiModel? {
        if (isBusy || ownership is PremiumOwnershipUiState.Owned) return null
        val products = (catalog as? PremiumCatalogState.Available)?.products ?: return null
        // Duplicate/stale offer keys cannot accidentally launch a different offer.
        return products.singleOrNull { it.offerKey == selectedOfferKey }?.takeIf { it.canPurchase }
    }
}
