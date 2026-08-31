package com.example.vplayer

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.input.pointer.pointerInput


@Composable
fun VideoPlayerScreen(videoId: Long, videoUri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val dataStore = remember { PlaybackDataStore(context) }
    val scope = rememberCoroutineScope()

    var showSpeedMenu by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1f) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(1L) }
    var volume by remember { mutableStateOf(1f) }

    // Controls visibility state
    var controlsVisible by remember { mutableStateOf(true) }

    val exoPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = true
        }
    }

    // Resume fromm last saved position
    LaunchedEffect(videoId) {
        val savedPos = dataStore.getPosition(videoId)
        if (savedPos > 0) exoPlayer.seekTo(savedPos)
        dataStore.addToHistory(videoId)
    }

    // Poll position for seek bar and save progress
    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(1L)
            dataStore.savePosition(videoId, currentPosition)
            delay(1000)
        }
    }

    // Auto-hide controls after 3 seconds while playing
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3000)
            controlsVisible = false
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            scope.launch { dataStore.savePosition(videoId, exoPlayer.currentPosition) }
            exoPlayer.release()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> exoPlayer.playWhenReady = isPlaying
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                controlsVisible = !controlsVisible
            }
    ) {
        AndroidView(
            factory = {
                PlayerView(context).apply {
                    player = exoPlayer
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Controls overlay
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(12.dp)
            ) {
                // NEW
                VlcStyleSeekBar(
                    progress = (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
                    onSeek = { fraction ->
                        exoPlayer.seekTo((fraction * duration).toLong())
                        controlsVisible = true
                    },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatDuration(currentPosition), color = Color.White, fontSize = 12.sp)
                    Text(formatDuration(duration), color = Color.White, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    // Centered group: back10 - play/pause - forward10
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { exoPlayer.seekTo((currentPosition - 10000).coerceAtLeast(0)) }) {
                            Icon(Icons.Filled.Replay10, contentDescription = "Back 10s", tint = Color.White)
                        }
                        IconButton(onClick = {
                            isPlaying = !isPlaying
                            exoPlayer.playWhenReady = isPlaying
                        }) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        IconButton(onClick = { exoPlayer.seekTo((currentPosition + 10000).coerceAtMost(duration)) }) {
                            Icon(Icons.Filled.Forward10, contentDescription = "Forward 10s", tint = Color.White)
                        }
                    }

                    // Speed button pinned to the end, doesn't affect center group's position
                    Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                        IconButton(onClick = { showSpeedMenu = true }) {
                            Icon(Icons.Filled.Speed, contentDescription = "Speed", tint = Color.White)
                        }
                        DropdownMenu(expanded = showSpeedMenu, onDismissRequest = { showSpeedMenu = false }) {
                            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                                DropdownMenuItem(
                                    text = { Text("${speed}x") },
                                    onClick = {
                                        playbackSpeed = speed
                                        exoPlayer.playbackParameters = PlaybackParameters(speed)
                                        showSpeedMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.VolumeUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Slider(
                        value = volume,
                        onValueChange = {
                            volume = it
                            exoPlayer.volume = it
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = MaterialTheme.colorScheme.secondary
                        )
                    )
                }
            }
        }
    }
}
@Composable
fun VlcStyleSeekBar(
    progress: Float,          // 0f..1f
    onSeek: (Float) -> Unit,  // 0f..1f
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableStateOf(progress) }
    val displayProgress = if (isDragging) dragProgress else progress

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp) // generous touch target, visuals stay thin
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = {
                        isDragging = false
                        onSeek(dragProgress)
                    },
                    onHorizontalDrag = { change, _ ->
                        val newProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                        dragProgress = newProgress
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidth = maxWidth

        // Background track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .align(Alignment.Center)
                .background(Color.White.copy(alpha = 0.3f))
        )
        // Played portion
        Box(
            modifier = Modifier
                .width(trackWidth * displayProgress)
                .height(3.dp)
                .align(Alignment.CenterStart)
                .background(MaterialTheme.colorScheme.primary)
        )
        // Thumb
        Box(
            modifier = Modifier
                .offset(x = (trackWidth * displayProgress) - 6.dp)
                .size(12.dp)
                .align(Alignment.CenterStart)
                .background(MaterialTheme.colorScheme.primary, shape = androidx.compose.foundation.shape.CircleShape)
        )
    }
}