package org.nontrivialpursuit.appelflap

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import kotlin.concurrent.thread

class Maestrosity(val context: Context, val wait: Long, val loop: Boolean = false) {
    var player: MediaPlayer? = null
    var playthread: Thread? = null


    fun start() {
        if (Build.VERSION.SDK_INT >= 24) {
            playthread = thread {
                Thread.sleep(wait)
                runCatching {
                    context.assets.openFd("media/push_the_bundle_in.opus").use {
                        player = MediaPlayer().apply {
                            setAudioAttributes(
                                AudioAttributes.Builder().setContentType((AudioAttributes.CONTENT_TYPE_SONIFICATION))
                                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build()
                            )
                            isLooping = loop
                            setDataSource(it)
                            prepare()
                            start()
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        playthread?.join()
        player?.apply {
            runCatching { stop() }
            runCatching { release() }
        }
    }
}
