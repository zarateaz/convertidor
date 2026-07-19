package com.example.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class MusicPlaybackService : Service() {

    private val CHANNEL_ID = "zarate_player_channel"
    private val NOTIFICATION_ID = 2099

    companion object {
        const val ACTION_PLAY = "PLAY"
        const val ACTION_PAUSE = "PAUSE"
        const val ACTION_TOGGLE = "TOGGLE"
        const val ACTION_NEXT = "NEXT"
        const val ACTION_PREV = "PREV"
        const val ACTION_STOP = "STOP"
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_TOGGLE -> {
                    if (MusicPlayerManager.isPlaying.value) {
                        MusicPlayerManager.pause(context)
                    } else {
                        MusicPlayerManager.resume(context)
                    }
                    updateNotification()
                }
                ACTION_NEXT -> {
                    MusicPlayerManager.next(context)
                    updateNotification()
                }
                ACTION_PREV -> {
                    MusicPlayerManager.previous(context)
                    updateNotification()
                }
                ACTION_STOP -> {
                    MusicPlayerManager.pause(context)
                    stopForeground(true)
                    stopSelf()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        val filter = IntentFilter().apply {
            addAction(ACTION_TOGGLE)
            addAction(ACTION_NEXT)
            addAction(ACTION_PREV)
            addAction(ACTION_STOP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action != null) {
            when (action) {
                ACTION_PLAY, ACTION_PAUSE -> {
                    updateNotification()
                }
                ACTION_TOGGLE -> {
                    if (MusicPlayerManager.isPlaying.value) {
                        MusicPlayerManager.pause(this)
                    } else {
                        MusicPlayerManager.resume(this)
                    }
                    updateNotification()
                }
                ACTION_NEXT -> {
                    MusicPlayerManager.next(this)
                    updateNotification()
                }
                ACTION_PREV -> {
                    MusicPlayerManager.previous(this)
                    updateNotification()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun updateNotification() {
        val song = MusicPlayerManager.currentSong.value ?: return
        val isPlaying = MusicPlayerManager.isPlaying.value

        // Open app intent
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE
        )

        // Action Intents
        val toggleIntent = Intent(ACTION_TOGGLE).apply { setPackage(packageName) }
        val togglePendingIntent = PendingIntent.getBroadcast(
            this, 1, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(ACTION_NEXT).apply { setPackage(packageName) }
        val nextPendingIntent = PendingIntent.getBroadcast(
            this, 2, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = Intent(ACTION_PREV).apply { setPackage(packageName) }
        val prevPendingIntent = PendingIntent.getBroadcast(
            this, 3, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(ACTION_STOP).apply { setPackage(packageName) }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this, 4, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val playPauseText = if (isPlaying) "Pausar" else "Reproducir"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(song.title)
            .setContentText("${song.artist} — ${song.album} [${song.format}]")
            .setSubText("Zarate Player Pro")
            .setContentIntent(openAppPendingIntent)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Vital for Lock Screen controls!
            .addAction(android.R.drawable.ic_media_previous, "Anterior", prevPendingIntent)
            .addAction(playPauseIcon, playPauseText, togglePendingIntent)
            .addAction(android.R.drawable.ic_media_next, "Siguiente", nextPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener", stopPendingIntent)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)
            )
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Zarate Player Playback Controls"
            val descriptionText = "Shows active playback notification with lock-screen controls"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {
            // silent catch
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
