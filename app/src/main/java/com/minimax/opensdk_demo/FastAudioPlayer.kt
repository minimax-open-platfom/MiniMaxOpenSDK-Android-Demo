package com.minimax.opensdk_demo

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Player.Listener
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.minimax.opensdk_demo.log.Logger

/**
 * Simple的声音播放控制器
 */
object FastAudioPlayer {

    private const val TAG = "FastAudioPlayer"

    var applicationContext: Context? = null

    /**
     * PlayMedia() -> onLoading -> onStart -> onStop
     */
    interface PlayerStateListener {
        /** 开始加载 */
        fun onLoading(data: MediaData?) = Unit

        /** 开始播放 */
        fun onStart(data: MediaData?) = Unit

        /** 播放停止 */
        fun onStop(data: MediaData?, isError: Boolean) = Unit
    }

    private val nativePlayer by lazy {
        ExoPlayer.Builder(applicationContext!!).build().apply {
            addListener(object : Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {

                        Player.STATE_IDLE -> {
                            Logger.d(TAG) { "STATE_IDLE data = $currentMedia" }
                        }

                        Player.STATE_BUFFERING -> {
                            Logger.d(TAG) { "STATE_BUFFERING data = $currentMedia" }
                        }

                        Player.STATE_READY -> {
                            Logger.d(TAG) { "STATE_READY data = $currentMedia" }
                            onPlayerStart()
                        }

                        Player.STATE_ENDED -> {
                            Logger.d(TAG) { "STATE_ENDED data = $currentMedia" }
                            onPlayerEnd()
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Logger.d(TAG) { "onPlayerError, data = $currentMedia, err = $error" }
                    onPlayerEnd()
                }
            })
        }
    }

    private val mediaSourceFactory: ProgressiveMediaSource.Factory by lazy {
        ProgressiveMediaSource.Factory(dataSourceFactory)
    }

    private val dataSourceFactory: DataSource.Factory by lazy {
        DefaultDataSource.Factory(
            applicationContext!!
        )
    }

    /**
     * 当前播放的声音data
     * 需要判断当前播放的是否为当前 uri
     * */
    var currentMedia: MediaData? = null
        private set

    /** 声音状态变化 */
    private val playerStateListeners: MutableList<PlayerStateListener> = mutableListOf()

    fun getSessionId(): Int {
        return nativePlayer.audioSessionId
    }

    /**
     * 播放语音
     */
    @Synchronized
    fun playMedia(data: MediaData) {
        if (data.mediaUri.isEmpty()) {
            Logger.d(TAG) { "playMedia data invalid, data = $data" }
            return
        }

        if (nativePlayer.isPlaying || currentMedia != null) {
            nativePlayer.stop()
            onPlayerEnd()
        }

        safeCall {
            nativePlayer.apply {
                currentMedia = data
                notifyPlayerState(PlayerState.LOADING)
                val mediaSource =
                    mediaSourceFactory.createMediaSource(MediaItem.fromUri(data.mediaUri))

                nativePlayer.setMediaSource(mediaSource)
                nativePlayer.prepare()
                nativePlayer.playWhenReady = true
            }
        }
    }

    /**
     * 停止播放声音
     */
    @Synchronized
    fun stopPlayer() {
        safeCall {
            if (nativePlayer.playbackState != Player.STATE_IDLE) {
                nativePlayer.stop()
                onPlayerEnd()
            }
        }
    }

    /**
     * 声音结束播放
     */
    private fun onPlayerEnd() {
        notifyPlayerState(PlayerState.STOP)
        currentMedia = null
    }

    /**
     * 声音开始播放
     */
    private fun onPlayerStart() {
        notifyPlayerState(PlayerState.START)
    }

    fun safeCall(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 注册声音播放状态监听
     */
    fun addPlayerStateListener(listener: PlayerStateListener) {
        if (playerStateListeners.contains(listener)) return
        playerStateListeners.add(listener)
    }

    /**
     * 解除注册声音播放状态监听
     */
    fun removePlayerStateListener(listener: PlayerStateListener) {
        playerStateListeners.remove(listener)
    }

    /**
     * 注册带生命周期感知的状态监听
     */
    fun LifecycleOwner.addPlayerStateListenerLifecycleAware(listener: PlayerStateListener) {
        if (playerStateListeners.contains(listener)) return
        playerStateListeners.add(listener)
        lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_DESTROY) {
                    removePlayerStateListener(listener)
                    lifecycle.removeObserver(this)
                }
            }
        })
    }

    /**
     * 生命周期onPause时自动停止播放声音
     */
    fun LifecycleOwner.registerPlayerAutoPauseListenerLifecycleAware() {
        lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_PAUSE) {
                    stopPlayer()
                }
            }
        })
    }

    private fun notifyPlayerState(state: PlayerState, isError: Boolean = false) {
        playerStateListeners.forEach {
            if (state == PlayerState.START) it.onStart(currentMedia)
            if (state == PlayerState.STOP) it.onStop(currentMedia, isError)
            if (state == PlayerState.LOADING) it.onLoading(currentMedia)
        }
    }

}

