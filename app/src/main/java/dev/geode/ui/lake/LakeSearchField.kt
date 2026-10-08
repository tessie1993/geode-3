package dev.geode.ui.lake

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.geode.ui.theme.StoneIcon
import dev.geode.ui.theme.StoneIconArt

/** Native editing and accessibility over a local optical backing, without simulated scene blur. */
@Composable
fun LakeSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = { Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingIcon = { StoneIconArt(StoneIcon.SEARCH, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier =
            modifier
                .fillMaxWidth()
                .lakeFrostedPanel(corner = LakeMaterials.SearchCorner)
                .semantics { contentDescription = hint },
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
    )
}
