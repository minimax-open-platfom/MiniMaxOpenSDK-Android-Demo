package com.minimax.opensdk_demo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MutableLiveData
import com.minimax.opensdk_android.voice_call.MMVoiceCallManager
import com.minimax.opensdk_android.voice_call.bean.AssistantParams
import com.minimax.opensdk_android.voice_call.bean.Message
import com.minimax.opensdk_android.voice_call.bean.MessageType
import com.minimax.opensdk_android.voice_call.bean.RoleType
import com.minimax.opensdk_android.voice_call.bean.StreamParams
import com.minimax.opensdk_android.voice_call.bean.StreamType
import com.minimax.opensdk_android.voice_call.bean.T2aOption
import com.minimax.opensdk_android.voice_call.bean.ThreadParams
import com.minimax.opensdk_android.voice_call.bean.Tool
import com.minimax.opensdk_android.voice_call.config.RecordSampleRate
import com.minimax.opensdk_android.voice_call.listener.AudioEndReason
import com.minimax.opensdk_android.voice_call.listener.RecordError
import com.minimax.opensdk_android.voice_call.listener.RecordListener
import com.minimax.opensdk_android.voice_call.listener.VoiceCallMsgListener
import com.minimax.opensdk_android.voice_call.util.AudioUtil
import com.minimax.opensdk_demo.contract.AudioSelect
import com.minimax.opensdk_demo.contract.IAudioSelect
import com.minimax.opensdk_demo.log.Logger
import com.minimax.opensdk_demo.open.databinding.MainActivityBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 *  Created by xijue on 2024/3/4
 *
 */
class MainActivity : FragmentActivity(),
    IPermissionContext by PermissionContext(),
    IRequestPermissionsContext,
    IAudioSelect by AudioSelect() {

    var assistantId = MutableLiveData(ASSISTANT_ID)

    var threadId = MutableLiveData(THREAD_ID)

    val recordStatus = MutableLiveData("等待录音")

    val audioFilePath = MutableLiveData<String?>()

    val sendFileStatus = MutableLiveData("等待发送")

    val firstAudioPlayCost = MutableLiveData<Long>(0)

    val audioDecodeCost = MutableLiveData<Long>(0)

    override fun context(): Context {
        return this
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onRequestPermissionsResultAsync(requestCode, permissions, grantResults, this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MainActivityBinding.inflate(layoutInflater).apply {
            setContentView(root)
            this.view = this@MainActivity
            lifecycleOwner = this@MainActivity
        }
        MMVoiceCallManager.init(
            applicationContext = applicationContext,
            recordSampleRate = RecordSampleRate.SAMPLE_RATE_32K,
            vadVolume = 40,
            vadTimeout = 1500,
            vadMaxTime = 60_000,
        )

        FastAudioPlayer.applicationContext = applicationContext
    }

    fun sendAudio() {
        if (assistantId.value.isNullOrEmpty()) {
            toast("assistantId is null")
            return
        }
        if (threadId.value.isNullOrEmpty()) {
            toast("threadId is null")
            return
        }
        if (audioFilePath.value == null) {
            toast("audioFile is null")
            return
        }
        CoroutineScope(Dispatchers.Main).launch {
            val path = audioFilePath.value ?: return@launch
            val hex = withContext(Dispatchers.IO) {
                if (path.contains("://")) {
                    AudioUtil.uriToHex(baseContext, path)
                } else {
                    AudioUtil.fileToHex(File(path).path)
                }
            }
            Logger.i(LOG_TAG) {
                "audio file hex.length: ${hex?.length}"
            }
            val requestStart = System.currentTimeMillis()
            var firstAudioReceived: Long = 0
            hex?.let { hex ->
                MMVoiceCallManager.sendVoiceCallMsgAndPlayAudio(
                    params = StreamParams(
                        apiKey = API_KEY,
                        groupId = GROUP_ID,
                        stream = StreamType.TEXT_AND_AUDIO_STREAM.value,
                        assistantId = assistantId.value!!,
                        threadId = threadId.value!!,
                        messages = listOf(
                            Message(
                                type = MessageType.AUDIO.value,
                                role = RoleType.USER.value,
                                content = hex
                            )
                        ),
                        model = "abab5.5-chat",
                        t2aOption = T2aOption(
                            model = "speech-01",
                            voiceId = "male-qn-qingse",
                        ),
                        tools = listOf(
                            Tool(type = "web_search")
                        )
                    ),
                    listener = object : VoiceCallMsgListener {
                        override fun onRequestStart() {
                            Logger.i(LOG_TAG) {
                                "onRequestStart"
                            }
                            sendFileStatus.postValue("onRequestStart")
                        }

                        override fun onRequestError(errorCode: Int, errorMsg: String) {
                            Logger.i(LOG_TAG) {
                                "onRequestError errorCode: $errorCode, errorMsg: $errorMsg"
                            }
                            sendFileStatus.postValue("onRequestError errorCode: $errorCode, errorMsg: $errorMsg")

                        }

                        override fun onAudioMsgReceived() {
                            Logger.i(LOG_TAG) {
                                "onVoiceCallMsgReceived"
                            }
                            firstAudioReceived = System.currentTimeMillis()
                            sendFileStatus.postValue("onVoiceCallMsgReceived")
                        }

                        override fun onAudioMsgPlayStart() {
                            Logger.i(LOG_TAG) {
                                "onVoiceCallMsgPlayStart"
                            }
                            val now = System.currentTimeMillis()
                            firstAudioPlayCost.postValue(now - requestStart)
                            audioDecodeCost.postValue(now - firstAudioReceived)
                            sendFileStatus.postValue("onVoiceCallMsgPlayStart")
                        }

                        override fun onAudioMsgPlayEnd(success: Boolean, reason: AudioEndReason?) {
                            Logger.i(LOG_TAG) {
                                "onVoiceCallMsgPlayEnd success: $success, error: $reason"
                            }
                            sendFileStatus.postValue("onVoiceCallMsgPlayEnd success: $success, error: $reason")
                        }

                        override fun onComplete() {
                            // 结束
                            Logger.i(LOG_TAG) {
                                "onComplete"
                            }
                        }
                    }
                )
            }
        }

    }

    fun stopReply() {
        MMVoiceCallManager.cancelVoiceCallMsgAndStopPlay()
    }

    fun startListen() {
        checkPermissionAsync(
            requestContext = this,
            permission = android.Manifest.permission.RECORD_AUDIO,
            needShowDescriptionDialog = false,
            rejectedRunnable = null,
        ) {
            // 拿到了权限
            MMVoiceCallManager.startListen(object : RecordListener {
                override fun onListenStart() {
                    Logger.i("opensdk_demo") {
                        "onListenStart"
                    }
                    recordStatus.postValue("开始监听讲话")
                }

                override fun onRecordStart() {
                    Logger.i("opensdk_demo") {
                        "onRecordStart"
                    }
                    recordStatus.postValue("检测到人声，开始写入")
                }

                override fun onRecordEnd(success: Boolean, error: RecordError?, audioFile: File?) {
                    Logger.i("opensdk_demo") {
                        "onRecordEnd success: $success, error: $error, tempFile: $audioFile"
                    }
                    runOnUiThread {
                        recordStatus.postValue("录音结束")
                        this@MainActivity.audioFilePath.value = audioFile?.path
                    }
                }
            })
        }
    }

    fun stopListen() {
        MMVoiceCallManager.stopListen()
    }

    // 播放录音
    fun playRecord() {
        if (MMVoiceCallManager.isListening()) {
            stopListen()
        }
        // 播放录音
        val filePath = audioFilePath.value ?: return
        FastAudioPlayer.playMedia(
            MediaData(System.currentTimeMillis().toString(), filePath),
        )
    }

    fun stopPlay() {
        FastAudioPlayer.stopPlayer()
    }

    // 选择音频
    fun selectAudioFile() {
        selectAudio()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        handleActivityResult(requestCode, resultCode, data)?.let { uri ->
            audioFilePath.value = uri
        }
    }

    fun createAssistant() {
        CoroutineScope(Dispatchers.IO).launch {
            val resp = MMVoiceCallManager.createAssistant(
                AssistantParams(
                    apiKey = API_KEY,
                    groupId = GROUP_ID,
                    model = "abab5.5-chat",
                    instructions = "是一个小说解读专家，读过万卷书，了解小说里的各种细致情节，另外也非常热心，会热情的帮读者解答小说里的问题",
                    name = "小说解读专家",
                    description = "小说解读专家，用在小说解读场景中回答用户问题",
                )
            )
            withContext(Dispatchers.Main) {
                assistantId.value = resp?.id
            }
            Logger.i(LOG_TAG) {
                "createAssistant resp: $resp"
            }
        }
    }

    fun createThread() {
        CoroutineScope(Dispatchers.IO).launch {
            val resp = MMVoiceCallManager.createThread(
                ThreadParams(
                    apiKey = API_KEY,
                    groupId = GROUP_ID,
                )
            )
            withContext(Dispatchers.Main) {
                threadId.value = resp?.id
            }
            Logger.i(LOG_TAG) {
                "createThread resp: $resp"
            }
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        MMVoiceCallManager.release()
    }

    companion object {
        const val ASSISTANT_ID = ""
        const val THREAD_ID = ""

        // todo 申请 apiKey
        const val API_KEY =
            ""

        // todo 申请 groupId
        const val GROUP_ID =
            ""

        const val LOG_TAG = "opensdk_demo"

        @JvmStatic
        fun String.last10Char(): String {
            return if (length > 10) {
                substring(length - 10, length)
            } else {
                this
            }
        }
    }
}