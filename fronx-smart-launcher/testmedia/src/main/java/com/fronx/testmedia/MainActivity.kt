package com.fronx.testmedia

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.util.Log
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var session: MediaSession
    private var playing = true
    private var position = 30_000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Test Media Player - session active" })
        session = MediaSession(this, "FronxTestSession")
        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() { Log.i(TAG, "onPlay"); playing = true; publish() }
            override fun onPause() { Log.i(TAG, "onPause"); playing = false; publish() }
            override fun onSkipToNext() { Log.i(TAG, "onSkipToNext") }
            override fun onSkipToPrevious() { Log.i(TAG, "onSkipToPrevious") }
        })
        val art = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200, 80, 60)) }
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, "Test Track")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Fronx Test Artist")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, 200_000L)
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art)
                .build()
        )
        publish()
        session.isActive = true
        Log.i(TAG, "session active")
    }

    private fun publish() {
        val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        session.setPlaybackState(
            PlaybackState.Builder().setActions(actions)
                .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, position, 1f)
                .build()
        )
    }

    override fun onDestroy() { session.release(); super.onDestroy() }

    private companion object { const val TAG = "FronxTestMedia" }
}
