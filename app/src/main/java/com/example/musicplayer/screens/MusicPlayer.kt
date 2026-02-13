package com.example.musicplayer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

data class MusicFile(
    val name: String,
    val path: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerApp() {
    val context = LocalContext.current
    val musicFiles = remember { getMusicFiles(context) }

    var musicService by remember { mutableStateOf<MusicService?>(null) }
    var currentPlayingIndex by remember { mutableStateOf<Int?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as MusicService.MusicBinder
                musicService = binder.getService()

                // Sync initial state from service
                currentPlayingIndex = musicService?.getCurrentIndex()
                isPlaying = musicService?.isCurrentlyPlaying() ?: false

                // Register listener for state changes
                musicService?.registerStateListener { index, playing ->
                    currentPlayingIndex = index
                    isPlaying = playing
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                musicService = null
            }
        }
    }

    DisposableEffect(Unit) {
        val intent = Intent(context, MusicService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        onDispose {
            musicService?.unregisterStateListener { _, _ -> }
            context.unbindService(serviceConnection)
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
                    isPlaying = currentPlayingIndex == index && isPlaying,
                    onPlayStop = {
                        if (currentPlayingIndex == index) {
                            // Stop current track
                            musicService?.stopPlayback()
                        } else {
                            // Play new track - pass full playlist
                            musicService?.playMusic(musicFiles[index], index, musicFiles)
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