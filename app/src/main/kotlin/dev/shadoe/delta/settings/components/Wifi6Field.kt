package dev.shadoe.delta.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.shadoe.delta.R

@Composable
internal fun Wifi6Field(
  enabled: Boolean?,
  supported: Boolean?,
  onChange: (Boolean) -> Unit,
) {
  val editable = enabled != null && supported == true
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      Modifier.fillMaxWidth()
        .toggleable(
          value = enabled == true,
          enabled = editable,
          role = Role.Switch,
          onValueChange = onChange,
        )
        .padding(vertical = 16.dp),
  ) {
    Icon(imageVector = Icons.Rounded.Wifi, contentDescription = null)
    Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
      Text(
        stringResource(R.string.wifi6_title),
        style = MaterialTheme.typography.titleMedium,
      )
      Text(
        stringResource(
          when (enabled) {
            true -> R.string.wifi6_on
            false -> R.string.wifi6_off
            null -> R.string.wifi6_unknown
          }
        ),
        style = MaterialTheme.typography.bodyMedium,
      )
      Text(
        stringResource(
          when (supported) {
            null -> R.string.wifi6_loading
            false -> R.string.wifi6_unavailable
            true -> R.string.wifi6_description
          }
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(
      checked = enabled == true,
      onCheckedChange = null,
      enabled = editable,
    )
  }
}
