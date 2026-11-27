package com.djape1.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.C
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.util.concurrent.Executors

/**
 * Παίζει το live stream στο παρασκήνιο (foreground service) και δείχνει στην ειδοποίηση
 * και στην οθόνη κλειδώματος τίτλο, καλλιτέχνη, άλμπουμ και εξώφυλλο.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class PlayerService : Service() {

    companion object {
        const val ACTION_STOP = "com.djape1.player.STOP"
        private const val CHANNEL = "playback"
        private const val NOTIF_ID = 1
        private const val MAX_RETRIES = 6
    }

    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSessionCompat
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var invite: Invite? = null
    private var running = false
    private var retries = 0
    private var now: NowInfo? = null
    private var art: Bitmap? = null
    private var artId: String? = null
    private var logo: Bitmap? = null

    private val pollTask = object : Runnable {
        override fun run() {
            val inv = invite ?: return
            io.execute {
                try {
                    val info = Api.fetchNow(inv)
                    main.post { applyNow(info) }
                } catch (e: ApiException) {
                    if (e.code == 403 || e.code == 404) main.post { stopPlayback("The invitation is not valid", true) }
                } catch (e: Exception) {
                    // προσωρινό πρόβλημα δικτύου: το stream έχει δική του επανασύνδεση
                }
            }
            if (running) main.postDelayed(this, 4000)
        }
    }

    private val retryTask = Runnable { if (running) startPlayback() }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("DjApe1-Android/1.0")
            .setConnectTimeoutMs(10000)
            .setReadTimeoutMs(15000)
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(http))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (!running) return
                when (state) {
                    Player.STATE_BUFFERING -> Hub.update(true, "Connecting…")
                    Player.STATE_READY -> { retries = 0; Hub.update(true, "Connected – Stable"); updateState(true) }
                    Player.STATE_ENDED -> retry()
                    else -> {}
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val cause = error.cause
                if (cause is HttpDataSource.InvalidResponseCodeException &&
                    (cause.responseCode == 403 || cause.responseCode == 404)
                ) {
                    stopPlayback("The invitation is invalid or has expired", true)
                } else if (cause is HttpDataSource.InvalidResponseCodeException && cause.responseCode == 503) {
                    stopPlayback("The station is full. Please try again shortly.", true)
                } else if (cause is HttpDataSource.InvalidResponseCodeException && cause.responseCode == 429) {
                    stopPlayback("This invitation is already in use on the maximum number of devices.", true)
                } else {
                    retry()
                }
            }
        })

        session = MediaSessionCompat(this, "DjApe1").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { if (!running) startPlayback() }
                override fun onPause() { stopPlayback("Ready", false) }
                override fun onStop() { stopPlayback("Ready", false) }
            })
        }
        logo = BitmapFactory.decodeResource(resources, R.drawable.logo)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopPlayback("Ready", false)
            return START_NOT_STICKY
        }
        invite = Prefs.invite(this)
        if (invite == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Η startForeground πρέπει να κληθεί αμέσως μετά το startForegroundService
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            ServiceCompat.startForeground(this, NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, n)
        }
        running = true
        retries = 0
        startPlayback()
        main.removeCallbacks(pollTask)
        main.post(pollTask)
        return START_NOT_STICKY
    }

    private fun startPlayback() {
        val inv = invite ?: return
        main.removeCallbacks(retryTask)
        // Η παράμετρος t εμποδίζει την επαναχρησιμοποίηση παλιάς σύνδεσης
        player.setMediaItem(MediaItem.fromUri(streamUrl(inv)))
        player.prepare()
        player.playWhenReady = true
        session.isActive = true
        updateState(true)
        Hub.update(true, "Connecting…")
    }

    /** Το URL του stream με την ποιότητα που διάλεξε ο χρήστης. Η παράμετρος t εμποδίζει την επαναχρησιμοποίηση παλιάς σύνδεσης. */
    private fun streamUrl(inv: Invite): String {
        val base = inv.stream(Prefs.quality(this))
        val sep = if (base.contains("?")) "&" else "?"
        return base + sep + "t=" + System.currentTimeMillis()
    }

    private fun retry() {
        if (!running) return
        if (retries >= MAX_RETRIES) {
            stopPlayback("Connection lost. Tap the button to try again.", true)
            return
        }
        retries++
        Hub.update(true, "Connection interrupted. Reconnecting…", true)
        main.removeCallbacks(retryTask)
        main.postDelayed(retryTask, 1500L * retries)
    }

    private fun stopPlayback(msg: String, bad: Boolean) {
        running = false
        main.removeCallbacksAndMessages(null)
        try { player.stop() } catch (e: Exception) { /* ήδη σταματημένο */ }
        session.isActive = false
        Hub.update(false, msg, bad)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun applyNow(info: NowInfo) {
        if (!running) return
        val changed = now?.id != info.id || now?.title != info.title
        now = info
        History.record(this, info)
        if (info.hasCover && info.onAir) {
            if (artId != info.id) {
                artId = info.id
                art = null
                val inv = invite
                if (inv != null) io.execute {
                    val b = try { Api.fetchCover(inv, info.id) } catch (e: Exception) { null }
                    main.post { if (artId == info.id) { art = b; refresh() } }
                }
            }
        } else if (art != null || artId != null) {
            art = null; artId = null
        }
        if (changed) refresh()
    }

    private fun refresh() {
        val n = now
        val md = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, n?.title ?: n?.station ?: "Dj Ape1")
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, n?.artist ?: n?.station ?: "Dj Ape1")
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, n?.album ?: n?.station ?: "Dj Ape1")
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art ?: logo)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, art ?: logo)
            .build()
        session.setMetadata(md)
        NotificationManagerCompat.from(this).apply {
            try { notify(NOTIF_ID, buildNotification()) } catch (e: SecurityException) { /* χωρίς άδεια ειδοποιήσεων */ }
        }
    }

    private fun updateState(playing: Boolean) {
        val st = PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_STOP)
            .setState(
                if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_STOPPED,
                PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f
            ).build()
        session.setPlaybackState(st)
    }

    private fun buildNotification(): Notification {
        val n = now
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, PlayerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(n?.title ?: n?.station ?: getString(R.string.app_name))
            .setContentText(n?.artist ?: "Live broadcast")
            .setLargeIcon(art ?: logo)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_stop, "Stop", stop)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0)
            )
        if (!n?.album.isNullOrBlank()) b.setSubText(n?.album)
        return b.build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Playback", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        main.removeCallbacksAndMessages(null)
        player.release()
        session.release()
        io.shutdownNow()
        if (Hub.playing) Hub.update(false, "Ready")
        super.onDestroy()
    }
}
