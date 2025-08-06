package com.alexprestes.dashcamviewer.ui.volume

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alexprestes.dashcamviewer.domain.model.VolumeInfo

@Composable
fun VolumeScreen(
    volumes: List<VolumeInfo>,
    onVolumeSelected: (VolumeInfo) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Text("Selecione uma unidade de armazenamento:", style = MaterialTheme.typography.titleLarge)

        Spacer(modifier = Modifier.height(16.dp))

        if (volumes.isEmpty()) {
            Text("Nenhuma unidade encontrada ou sem vídeos.", style = MaterialTheme.typography.bodyMedium)
        } else {
            volumes.forEach { volume ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onVolumeSelected(volume) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = volume.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${volume.videoCount} vídeos encontrados",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
