package dev.shadoe.delta.common

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.shadoe.delta.data.softap.HotspotSnapshotRepository
import javax.inject.Inject

@HiltViewModel
class LimitedModeViewModel @Inject constructor(snapshots: HotspotSnapshotRepository) : ViewModel() {
  val snapshot = snapshots.snapshot
}
