package com.kinghy2302.emg.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kinghy2302.emg.EmgUiState
import com.kinghy2302.emg.demo.DemoSignal
import com.kinghy2302.emg.dsp.EmgDsp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmgApp(
    state: EmgUiState,
    onRefresh: () -> Unit,
    onSelectDevice: (Int) -> Unit,
    onDemoChanged: (Boolean) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val analysis = state.analysis
    val timeRange = analysis?.let { EmgDsp.paddedTimeRange(it.timeYUv) } ?: (-1.0 to 1.0)
    val specMax = analysis?.let { EmgDsp.spectrumYMax(it.freqYUv) } ?: 1.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("肌电信号采集与频谱分析") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DevicePicker(
                    state = state,
                    onSelectDevice = onSelectDevice,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onRefresh, enabled = !state.running) {
                    Text("刷新设备")
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("演示模式", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = state.demoMode,
                        onCheckedChange = onDemoChanged,
                        enabled = !state.running,
                    )
                }
                Row {
                    Button(onClick = onStart, enabled = !state.running) {
                        Text("开始")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = onStop, enabled = state.running) {
                        Text("停止")
                    }
                }
            }

            Text(
                text = state.status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            state.checksumWarning?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(8.dp))
            MetricsRow(state)
            Spacer(Modifier.height(8.dp))

            SignalPlot(
                title = "时域信号",
                x = analysis?.timeX ?: DoubleArray(0),
                y = analysis?.timeYUv ?: DoubleArray(0),
                xMin = 0.0,
                xMax = 0.1,
                yMin = timeRange.first,
                yMax = timeRange.second,
                xLabel = "s",
                yLabel = "µV",
                lineColor = Color(0xFF1976D2),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            Spacer(Modifier.height(8.dp))
            SignalPlot(
                title = "频谱",
                x = analysis?.freqX ?: DoubleArray(0),
                y = analysis?.freqYUv ?: DoubleArray(0),
                xMin = 0.0,
                xMax = 600.0,
                yMin = 0.0,
                yMax = specMax,
                xLabel = "Hz",
                yLabel = "µV",
                lineColor = Color(0xFFE65100),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            Text(
                "工程采集与可视化工具，非医疗器械 / 非诊断用途",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevicePicker(
    state: EmgUiState,
    onSelectDevice: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = state.devices.firstOrNull { it.deviceId == state.selectedDeviceId }
    val label = when {
        state.demoMode -> DemoSignal.LABEL
        selected != null -> selected.displayName
        else -> "未发现 USB 串口设备"
    }
    ExposedDropdownMenuBox(
        expanded = expanded && !state.demoMode && !state.running,
        onExpandedChange = {
            if (!state.demoMode && !state.running) expanded = !expanded
        },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("设备") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            enabled = !state.demoMode && !state.running,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.devices.forEach { device ->
                DropdownMenuItem(
                    text = { Text(device.displayName) },
                    onClick = {
                        onSelectDevice(device.deviceId)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun MetricsRow(state: EmgUiState) {
    val a = state.analysis
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricChip("主频", a?.let { "%.2f Hz".format(it.peakFreqHz) } ?: "--", Modifier.weight(1f))
        MetricChip("峰值", a?.let { "%.2f µV".format(it.peakUv) } ?: "--", Modifier.weight(1f))
        MetricChip("峰峰值", a?.let { "%.2f µV".format(it.peakToPeakUv) } ?: "--", Modifier.weight(1f))
        MetricChip("RMS值", a?.let { "%.2f µV".format(it.rmsUv) } ?: "--", Modifier.weight(1f))
    }
}

@Composable
private fun MetricChip(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
    }
}
