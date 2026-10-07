package dev.geode.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumUnlockUiStateTest {
    private val offer =
        PremiumProductUiModel(
            offerKey = "monthly/base-plan",
            productId = PremiumProduct.MONTHLY.productId,
            kind = PremiumProductKind.SUBSCRIPTION,
            title = "Monthly fixture",
            formattedPrice = "Localized price fixture",
            recurrence = "Billing period fixture",
            disclosure = "Complete renewal disclosure fixture",
            available = true,
        )
    private val ready =
        PremiumUnlockUiState(
            catalog = PremiumCatalogState.Available(listOf(offer)),
            selectedOfferKey = offer.offerKey,
            restoreAvailable = true,
        )

    @Test
    fun `no implicit selection or fabricated price can enable purchase`() {
        assertNull(PremiumUnlockUiState().selectedProduct())
        assertEquals(PremiumCatalogState.Unconfigured, PremiumUnlockUiState().catalog)
        assertFalse(PremiumUnlockUiState().canRestore)
        assertFalse(PremiumUnlockUiState(restoreAvailable = true).canRestore)
        assertNull(ready.copy(selectedOfferKey = null).selectedProduct())
        listOf(
            offer.copy(formattedPrice = ""),
            offer.copy(recurrence = ""),
            offer.copy(disclosure = ""),
            offer.copy(available = false),
            offer.copy(productId = "unknown_product"),
            offer.copy(kind = PremiumProductKind.LIFETIME),
        ).forEach { invalid ->
            assertNull(ready.copy(catalog = PremiumCatalogState.Available(listOf(invalid))).selectedProduct())
        }
        assertEquals(offer, ready.selectedProduct())
    }

    @Test
    fun `catalog refresh removes stale selections and duplicate keys fail closed`() {
        assertNull(ready.copy(catalog = PremiumCatalogState.Loading).selectedProduct())
        assertNull(ready.copy(catalog = PremiumCatalogState.Empty).selectedProduct())
        assertNull(ready.copy(catalog = PremiumCatalogState.Error("Unavailable fixture")).selectedProduct())
        assertNull(ready.copy(selectedOfferKey = "old-offer").selectedProduct())
        assertNull(ready.copy(catalog = PremiumCatalogState.Available(listOf(offer, offer))).selectedProduct())
    }

    @Test
    fun `payment verification pending and restore prevent overlapping purchase actions`() {
        listOf(
            PremiumOperation.Purchasing,
            PremiumOperation.Pending,
            PremiumOperation.Verifying,
            PremiumOperation.Restoring,
        ).forEach { operation ->
            val busy = ready.copy(operation = operation)
            assertNull(busy.selectedProduct())
            assertFalse(busy.canRestore)
        }
        assertTrue(ready.canRestore)
        assertFalse(ready.copy(restoreAvailable = false).canRestore)
    }

    @Test
    fun `owned display prevents duplicate purchase while restore remains available`() {
        val owned = ready.copy(ownership = PremiumOwnershipUiState.Owned("Verified access fixture"))
        assertNull(owned.selectedProduct())
        assertTrue(owned.canRestore)
    }

    @Test
    fun `restore completion is an operation result and never marks ownership`() {
        val restored = ready.copy(operation = PremiumOperation.RestoreFinished)
        assertEquals(PremiumOwnershipUiState.NotOwned, restored.ownership)
        assertEquals(offer, restored.selectedProduct())
    }

    @Test
    fun `lifetime selection keeps the supplied one-time price and terms`() {
        val lifetime =
            offer.copy(
                offerKey = "lifetime",
                productId = PremiumProduct.LIFETIME.productId,
                kind = PremiumProductKind.LIFETIME,
                recurrence = "One-time payment fixture",
            )
        val selected =
            ready.copy(
                catalog = PremiumCatalogState.Available(listOf(offer, lifetime)),
                selectedOfferKey = lifetime.offerKey,
            )
        assertEquals(lifetime, selected.selectedProduct())
    }

    @Test
    fun `published catalog is not changed by mutation of its source list`() {
        val source = mutableListOf(offer)
        val catalog = PremiumCatalogState.Available(source)
        source.clear()
        assertEquals(listOf(offer), catalog.products)
    }
}
