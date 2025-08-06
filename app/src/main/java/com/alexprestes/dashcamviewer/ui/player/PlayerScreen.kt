package com.alexprestes.dashcamviewer.ui.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ConcatenatingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.alexprestes.dashcamviewer.domain.model.CameraType
import com.alexprestes.dashcamviewer.domain.model.VideoFile
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(playerViewModel: PlayerViewModel = viewModel()) {
    val uiState by playerViewModel.uiState.collectAsState()

    Surface(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is PlayerUiState.Loading -> {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator()
                }
            }
            is PlayerUiState.Error -> {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(text = "Error: ${state.message}")
                }
            }
            is PlayerUiState.Success -> {
                val videos = remember(state.timeline) {
                    state.timeline.segments.flatMap { it.clips }.mapNotNull { it.frontVideo }
                }
                val insideVideos = remember(state.timeline) {
                    state.timeline.segments.flatMap { it.clips }.mapNotNull { it.rearVideo }
                }
                PlayerContent(videos, insideVideos)
            }
        }
    }
}

@Composable
private fun PlayerContent(frontVideos: List<VideoFile>, insideVideos: List<VideoFile>) {
    val context = LocalContext.current

    val frontPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            prepareWithVideos(frontVideos, context)
        }
    }

    val insidePlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            prepareWithVideos(insideVideos, context)
        }
    }
    
    val totalDuration = remember(frontVideos) {
        frontVideos.size * 60 * 1000L // 60 segundos por vídeo
    }

    var positionMs by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        frontPlayer.addListener(object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                insidePlayer.playWhenReady = isPlaying
            }

            override fun onEvents(player: androidx.media3.common.Player, events: androidx.media3.common.Player.Events) {
                if (events.contains(androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY)) {
                    insidePlayer.seekTo(player.currentPosition)
                }
            }
        })

        while (true) {
            positionMs = frontPlayer.currentPosition
            delay(500L)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            frontPlayer.release()
            insidePlayer.release()
        }
    }

    Column {
        Row(modifier = Modifier.weight(1f)) {
            PlayerViewComposable(frontPlayer, Modifier.weight(1f))
            PlayerViewComposable(insidePlayer, Modifier.weight(1f))
        }

        Slider(
            value = positionMs.toFloat(),
            onValueChange = { value ->
                frontPlayer.seekTo(value.toLong())
            },
            valueRange = 0f..totalDuration.toFloat(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )
    }
}


@Composable
private fun PlayerViewComposable(player: ExoPlayer, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = true
            }
        },
        update = { view ->
            view.player = player
        },
        modifier = modifier
    )
}

@OptIn(UnstableApi::class)
private fun ExoPlayer.prepareWithVideos(videos: List<VideoFile>, context: Context) {
    if (videos.isEmpty()) return
    
    val dataSourceFactory = DefaultDataSource.Factory(context)
    val mediaSources = videos.map {
        ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(it.uri))
    }
    val concatenated = ConcatenatingMediaSource(*mediaSources.toTypedArray())
    setMediaSource(concatenated)
    prepare()
}
