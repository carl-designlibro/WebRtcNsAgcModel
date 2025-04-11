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

                    val helper = Audio3AHelper()
                    helper.setSampleRate(sampleRateInHz)
                    helper.config()

                    kotlin.run {
                        audioRes.reset()
                        val audioData = audioRes.readBytes()
                        if (isStop) {
                            return@run
                        }

                        var useNsAgc = enabledNsAgc
                        if (useNsAgc) {
                            if (helper.processSampleCount() == 0) {
                                useNsAgc = false
                            }
                        }
                        if (useNsAgc) {
                            val outData = ByteArray(audioData.size)
                            val succeed = helper.process(audioData, 0, outData, 0, audioData.size)
                            if (succeed) {
                                helper.testPlay(this, outData, 0, outData.size)
                            }
                            else {
                                helper.testPlay(this, audioData, 0, audioData.size)
                            }
                        }
                        else {
                            helper.testPlay(this, audioData, 0, audioData.size)
                        }
                        if (isStop) {
                            return@run
                        }

                    }
                    helper.free()
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
