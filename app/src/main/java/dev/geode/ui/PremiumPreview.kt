package dev.geode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.geode.BuildConfig
import dev.geode.R
import dev.geode.account.PremiumUnlockScreen
import dev.geode.account.PremiumUnlockUiState

/** Review surface only; release navigation waits for real billing and legal configuration. */
@Composable
internal fun PremiumPreviewEntry() {
    if (!BuildConfig.DEBUG) return
    var showing by rememberSaveable { mutableStateOf(false) }
    var legalNotice by rememberSaveable { mutableStateOf(false) }
    val state = remember { PremiumUnlockUiState() }
    TextButton(onClick = { showing = true }) {
        Text(stringResource(R.string.premium_preview_action))
    }
    if (showing) {
        Dialog(
            onDismissRequest = { showing = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            PremiumUnlockScreen(
                state = state,
                onSelectOffer = {},
                onPurchase = {},
                onRestore = {},
                onRetry = {},
                onPrivacy = { legalNotice = true },
                onTerms = { legalNotice = true },
                onDismiss = { showing = false },
                modifier =
                    Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .systemBarsPadding(),
            )
        }
    }
    if (legalNotice) {
        AlertDialog(
            onDismissRequest = { legalNotice = false },
            title = { Text(stringResource(R.string.premium_preview_notice_title)) },
            text = { Text(stringResource(R.string.premium_preview_notice_body)) },
            confirmButton = {
                TextButton(onClick = { legalNotice = false }) {
                    Text(stringResource(R.string.account_premium_close))
                }
            },
        )
    }
}
