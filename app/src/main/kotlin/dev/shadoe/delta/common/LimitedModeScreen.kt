package dev.shadoe.delta.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.shadoe.delta.R
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LimitedModeScreen(
  vm: LimitedModeViewModel,
  onSetupShizuku: () -> Unit,
) {
  val context = LocalContext.current
  val snapshot by vm.snapshot.collectAsState()
  var settingsFailed by remember { mutableStateOf(false) }
  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text(stringResource(R.string.app_name)) }
      )
    }
  ) { padding ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(padding)
          .verticalScroll(rememberScrollState())
          .padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
        stringResource(R.string.limited_title),
        style = MaterialTheme.typography.headlineSmall,
      )
      Text(stringResource(R.string.limited_description))
      Button(
        onClick = { settingsFailed = !openHotspotSystemSettings(context) },
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(stringResource(R.string.limited_open_settings))
      }
      if (settingsFailed)
        Text(
          stringResource(R.string.limited_settings_failed),
          color = MaterialTheme.colorScheme.error,
        )
      OutlinedButton(
        onClick = onSetupShizuku,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(stringResource(R.string.limited_setup_shizuku))
      }
      HorizontalDivider()
      Text(
        stringResource(R.string.limited_snapshot_title),
        style = MaterialTheme.typography.titleLarge,
      )
      Text(
        stringResource(R.string.limited_snapshot_warning),
        style = MaterialTheme.typography.bodyMedium,
      )
      val saved = snapshot
      if (saved == null) {
        Text(stringResource(R.string.limited_snapshot_empty))
      } else {
        Text(
          stringResource(
            R.string.limited_snapshot_time,
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
              .format(Date(saved.readAtMillis)),
          )
        )
        Text(
          stringResource(
            R.string.limited_ssid,
            saved.ssid ?: stringResource(R.string.limited_unknown),
          )
        )
        Text(
          stringResource(
            R.string.limited_hidden,
            stringResource(
              if (saved.hidden) R.string.limited_yes else R.string.limited_no
            ),
          )
        )
        Text(
          stringResource(
            R.string.limited_wifi6,
            stringResource(
              when (saved.wifi6) {
                true -> R.string.limited_yes
                false -> R.string.limited_no
                null -> R.string.limited_unknown
              }
            ),
          )
        )
        val security =
          when (saved.securityType) {
            0 -> R.string.security_proto_open
            1 -> R.string.security_proto_wpa2_psk
            2 -> R.string.security_proto_wpa3_sae_transition
            3 -> R.string.security_proto_wpa3_sae
            else -> R.string.limited_unknown
          }
        Text(
          stringResource(R.string.limited_security, stringResource(security))
        )
        val band =
          when (saved.frequencyBand) {
            1 -> R.string.freq_band_2_4_GHz
            2 -> R.string.freq_band_5_GHz
            4 -> R.string.freq_band_6_GHz
            8 -> R.string.freq_band_60_GHz
            else -> R.string.limited_unknown
          }
        Text(stringResource(R.string.limited_band, stringResource(band)))
      }
    }
  }
}
