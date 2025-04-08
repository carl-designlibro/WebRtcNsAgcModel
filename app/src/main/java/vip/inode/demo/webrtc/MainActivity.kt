package vip.inode.demo.webrtc

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private val tag = "MainActivity"
    var isStop = false

    private lateinit var enable_ns_agc_switch: android.widget.Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        enable_ns_agc_switch = findViewById(R.id.enable_ns_agc_switch)
    }

    private val audioRes by lazy {
        val id = if (sampleRateInHz == 16000) {
            R.raw.recorder
        } else {
            R.raw.recorder_44d1k
        }
        resources.openRawResource(id)
    }

    val sampleRateInHz = 16000
//    错误场景测试
//val sampleRateInHz = 44100


    fun onClick(view: View) {
        when (view.id) {
            R.id.start_btn -> {
                isStop = false
                thread {
                    val enabledNsAgc = enable_ns_agc_switch.isChecked
                    val audioManager: AudioManager =
                        getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val bufferSize: Int =
                        AudioTrack.getMinBufferSize(
                            sampleRateInHz,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT
                        )
                    val audioAttributes: AudioAttributes = AudioAttributes.Builder()
                        .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                        .build()
                    val audioFormat: AudioFormat = AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRateInHz)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                    val sessionId = audioManager.generateAudioSessionId()
                    val audioTrack =
                        AudioTrack(
                            audioAttributes, audioFormat, bufferSize, AudioTrack.MODE_STREAM,
                            sessionId
                        )

                    var helper: Audio3AHelper? = null
                    if (enabledNsAgc) {
                        helper = Audio3AHelper()
                        helper.setSampleRate(sampleRateInHz)
                        helper.config()
                    }
                    kotlin.run {
                        audioTrack.play()
                        audioRes.reset()
                        val audioData = audioRes.readBytes()
                        if (isStop) {
                            return@run
                        }

                        var useNsAgc = enabledNsAgc
                        var chunkSize = 0
                        if (useNsAgc) {
                            chunkSize = helper!!.processSampleCount() * 2
                            if (chunkSize == 0) {
                                useNsAgc = false
                            }
                        }
                        if (useNsAgc) {
                            audioData.asSequence().chunked(chunkSize).filter { it.size == chunkSize }.forEach {
                                val byteArray = it.toByteArray()

                                val outData = ByteArray(chunkSize)

                                val succeed = helper!!.process(byteArray, outData)
                                if (succeed == true) {
                                    helper.testPlay(this, outData, 0, chunkSize)
                                }
                                else {
                                    helper.testPlay(this, byteArray, 0, chunkSize)
                                }
                            }
                        }
                        else {
                            audioTrack.write(audioData, 0, audioData.size)
                        }
                        if (isStop) {
                            return@run
                        }






                    }
//                    nsUtils?.nsxFree(nsxId)
//                    agcUtils?.agcFree(agcId)
                    helper?.free()
                    audioTrack.stop()
                    audioTrack.release()
                }
            }
            R.id.stop_btn -> {
                isStop = true
            }
            else -> {
            }
        }
    }

    override fun onStop() {
        isStop = true
        super.onStop()
    }

}
