## OpenSDK-Demo

com.minimax.opensdk_android:voice 依赖

targetSdkVersion 32
minSdkVersion 20
coroutine 1.6.4
androidX
okhttp 4.11.0
android-vad:webrtc

## 能力支持

1. 创建 Assistant & Thread
2. 开始录音 & 开始说话后 vad 检测静音自动停止
3. 手动停止录音
4. 发送语音消息请求并播放返回音频
5. 手动停止语音消息请求

## 使用流程

![使用流程图](https://raw.githubusercontent.com/minimax-open-platfom/MiniMaxOpenSDK-Android-Demo/master/art/usage_flow.jpeg)

## SDK 接入

1. 添加 maven 仓库

```
repositories {
    maven {
        url 'https://raw.githubusercontent.com/minimax-open-platfom/MiniMaxOpenSDK-Android-Repo/master'
    }
    maven { url 'https://jitpack.io' }
}
```

2. 添加依赖

```
dependencies {
    implementation "com.minimax.opensdk_android:voice_call:1.0.0"
}
```

3. 在代码中引用 MMVoiceCallManager

```
class MainActivity : Activity {

    fun onCreate() {
        // ...
        MMVoiceCallManager.init(applicationContext)

        // 开始使用
    }

    fun onDestory() {
        super.onDestory()
        MMVoiceCallManager.release()
    }
}
```

Demo 截图

<img src="https://raw.githubusercontent.com/minimax-open-platfom/MiniMaxOpenSDK-Android-Demo/master/art/screenshot.jpeg" alt="截图" style="zoom:30%;" />

## MMVoiceCallManager 使用

### init

MMVoiceCallManager 初始化，需要在使用 MMVoiceCallManager 使用前调用。

```
/**
  @param context applicationContext 必须传
  @param recordSampleRate 采样率 8000/16000/32000/48000
  @param vadVolume vad 收音音量 30-100 ,默认 55
  @param vadTimeout vad 检测不到人声后 x ms 后停止录音，默认 1500 ms
  @param vadMaxTime vad 可录制的最长人声，单位 ms 默认 60_000 ms
  @return Boolean 是否初始化成功
*/
fun init(context: Context, recordSampleRate: Int, vadVolume: Int, vadTimeout: Long, vadMaxTime: Long): Boolean
```

### createAssistant

创建一个 assistant，参数对齐开平官网参数。在子线程执行，返回可空，或抛出异常

```
/**
  @params params AssistantParams 数据结构对齐请求的参数 https://api.minimax.chat/document/guides/Assistants/document/assistant?id=6586b86b4da4834fd75906f6
  @return AssistantBean？ 可空，可能抛出异常
*/
fun createAssistant(params: AssistantParams): AssistantBean?

class AssistantBean()
```

### createThread

创建一个 thread ，参数对齐开平官网参数。子线程执行，返回可空，或抛出异常

```
/**
  @param params 创建 thread 的参数
  @return ThreadBean？ 可空
*/
fun createThread(params: ThreadParams): ThreadBean?

class ThreadBean()
```

### startListen

开始监听，需要接入方提前申请录音权限，调用方法后，会打开录音机进行收音，并开启 vad 自动检测，检测到人声并且声音分贝高于 vadVolume 后开始录音，在收不到人声或者人声低于 vadVolume 持续 vadTimeout 毫秒后，会自动接入，并通过 RecordListener.onRecordEnd 返回音频文件。如果一直在讲话，那么最多可录制 vadMaxTime 毫秒的音频文件。

```
/**
  @param listener 录音状态监听器
*/
fun startListen(listener: RecordListener)

interface RecordListener {

  // 开始监听讲话，端上可做一些状态变化
  fun onListenStart()

  // 人声检测检测到了人声，并开始进行文件写入
  fun onRecordStart()

  /**
    停止录制并返回录音结果
    @param success 是否录制成功
    @param error 如果失败的话，失败的原因
    @param audioFile 录音文件的地址 wav
  */
  fun onRecordEnd(success: Boolean, error: RecordError?, audioFile: File?)
}

enum class RecordError {
    START_RECORDER_ERROR,  // 启动录音机失败，可能是没权限，或者录音机被占用
    TOO_SHORT,  // 录音太短，可能是 vad 自动结束，但是录音时间 < 100ms 或者手动结束，录音时间不足 1s
    UNKNOWN  // 默认错误
}
```

### stopListen

手动停止录音，当环境嘈杂，或者有其他的人声混入时，可以调用这个方法结束录音。

```
/**
  停止录音，用户可以通过 vadTimeout 来自动停止录音，也可以手动调用，调用 stopRecord 来停止，停止后，会在 onRecordEnd 里获得音频文件
*/
fun stopListen(): Boolean
```

### isListening

录音设备是否开启

```
/**
  @return 录音机是否开启
*/
fun isListening(): Boolean
```

### sendVoiceCallMsgAndPlayAudio

发送语音并流式播放返回的语音消息。最关键的是需要在 streamParams 里手动传一个音频文件转 hex 后的字符串。输入的文件类型目前支持 wav/mp3 。
可以通过 streamParams.t2aOption.format 字段设置输出的音频格式，目前支持 mp3/flac 。

```
/**
  @param streamParams 语音通话请求参数
  @param listener 语音消息的状态监听
*/
fun sendVoiceCallMsgAndPlayAudio(streamParams: StreamParams, listener: VoiceCallMsgListener)

interface VoiceCallMsgListener {

  // sse 请求开始
  fun onRequestStart()

  /**
    @param errorCode 错误码，-1 表示服务端没有返回错误码，使用客户端兜底
    @param errorMSg 错误信息，可能包含一些敏感词，没有音频返回，以及网络请求的错误
  */
  fun onRequestError(errorCode: Int, errorMsg: String)

  // 收到了音频返回
  fun onAudioMsgReceived()

  // 语音消息开始播放
  fun onAudioMsgPlayStart()

  /**
    @param success 是否是正常结束
    @param reason 错误
  */
  fun onAudioMsgPlayEnd(success: Boolean, reason: StreamAudioPlayer.Reason?)

  // 流程结束，无论是请求失败还是播放完成，均会走到 onComplete 接口
  fun onComplete()
}

enum class Reason(
    var detail: PlaybackException? = null
) {
    BY_INTERRUPTED,         // 被打断
    BUFFER_TIMEOUT,         // 缓冲时间过长，一般是 30s，也就是收到音频数据后，30s 内不播放的话会停止
    PLAYBACK_EXCEPTION,     // 其他的播放异常
}

class StreamParams {}
```

在发送前会 check 下 hex 字符串的长度，确保不会超过最大长度。由于 hex 是由文件转换而成的。hex.length = file.size \* 2 。而 file 的最大 size 限制可以调用 AudioUtil.getMaxFileSize() 获得。

```
/**
  * @param sampleRate 初始化时输入的采样率
  * @param audioFormat sdk 录音时使用 ENCODDING_PCM_16BIT，这里穿同样的值
  * @param channelCount sdk 录音时使用的单声道，这里传 1
  * @param duration 录音写入的最大时长，init 时输入的最大时长，默认 60000 ms
  * @return 单位 kb
*/
fun getMaxFileSize(
    sampleRate: Int,
    audioFormat: Int,
    channelCount: Int,
    duration: Long,
): Long {
    return if (audioFormat == ENCODING_PCM_16BIT) {
        sampleRate * 2 * channelCount * (duration / 1000) / 1024
    } else {
        sampleRate * channelCount * (duration / 1000) / 1024
    }
}
```

### cancelVoiceCallMsgAndStopPlay

取消语音通话的请求，如果正在播放回复的话，也会停止回复

```
/**
  * @param CancelStreamParams 停止流式请求，可空，如果有传入的话，就停止传入的 runId 所在的请求，不传的话停止当前正在进行的流式请求
*/
fun cancelVoiceCallMsgAndStopPlay(params: CancelStreamParams?)
```

### release

通常在页面退出（Activity$OnDestory）等时机使用，用于清除一些录音过程中的缓存文件。如果在录音过程中退出的话，需要先调用 stopListen。同样在网络请求中退出的话，需要先调用 cancelVoiceCallMsgAndStopPlay

```
/**
  释放一些资源，通常在页面销毁的时候调用
*/
fun release()
```
