package com.rick.bluetoothmusicplayer.audio

import android.content.Context
import android.media.MediaPlayer
import com.rick.bluetoothmusicplayer.R

class TrackPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null

    val isPlaying: Boolean
        get() = mediaPlayer?.isPlaying == true

    fun play() {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(context, R.raw.studymusic).apply {
                setOnCompletionListener {
                    // Keep the player ready for another tap
                }
            }
        }
        mediaPlayer?.start()
    }

    fun pause() {
        mediaPlayer?.takeIf { it.isPlaying }?.pause()
    }

    fun stop() {
        mediaPlayer?.apply {
            stop()
            release()
        }
        mediaPlayer = null
    }

    fun release() {
        stop()
    }
}
