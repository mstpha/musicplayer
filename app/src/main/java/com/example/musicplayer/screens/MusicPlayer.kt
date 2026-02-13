package com.example.musicplayer

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

data class MusicFile(
    val name: String,
    val path: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerApp() {
    val context = LocalContext.current
    val musicFiles = remember { getMusicFiles(context) }
    var currentPlayingIndex by remember { mutableStateOf<Int?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Music Player") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(musicFiles.size) { index ->
                MusicItem(
                    musicFile = musicFiles[index],
                    isPlaying = currentPlayingIndex == index,
                    onPlayStop = {
                        if (currentPlayingIndex == index) {
                            // Stop current track
                            mediaPlayer?.stop()
                            mediaPlayer?.release()
                            mediaPlayer = null
                            currentPlayingIndex = null
                        } else {
                            // Stop previous track if any
                            mediaPlayer?.stop()
                            mediaPlayer?.release()

                            // Play new track
                            try {
                                val afd = context.assets.openFd(musicFiles[index].path)
                                mediaPlayer = MediaPlayer().apply {
                                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                                    afd.close()
                                    prepare()
                                    start()
                                    setOnCompletionListener {
                                        currentPlayingIndex = null
                                    }
                                }
                                currentPlayingIndex = index
                            } catch (e: Exception) {
                                e.printStackTrace()
                                mediaPlayer = null
                                currentPlayingIndex = null
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun MusicItem(
    musicFile: MusicFile,
    isPlaying: Boolean,
    onPlayStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = musicFile.name,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            IconButton(
                onClick = onPlayStop,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isPlaying)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Stop" else "Play",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

// Function to get music files from assets/music folder
fun getMusicFiles(context: Context): List<MusicFile> {
    return try {
        val assetManager = context.assets
        val musicFiles = assetManager.list("music") ?: emptyArray()

        musicFiles
            .filter { it.endsWith(".mp3") || it.endsWith(".wav") || it.endsWith(".m4a") }
            .map { fileName ->
                MusicFile(
                    name = fileName.substringBeforeLast("."),
                    path = "music/$fileName"
                )
            }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}


