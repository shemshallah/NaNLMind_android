package org.nan0s.vm

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** PC-speaker emulation: a square wave at the frequency the guest programmed into PIT channel 2 (0 = silent). */
class Beeper(private val hz: () -> Int) : Thread("nan-beep") {
    @Volatile var running = true
    @Volatile var muted = false
    private val rate = 22050

    fun shutdown() { running = false }

    override fun run() {
        val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return
        val at = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(min * 2).setTransferMode(AudioTrack.MODE_STREAM).build()
        val buf = ShortArray(512)
        var phase = 0.0
        at.play()
        while (running) {
            val f = if (muted) 0 else hz()
            if (f <= 0 || f > rate / 2) { Thread.sleep(20); phase = 0.0; continue }
            val step = f.toDouble() / rate
            for (i in buf.indices) { buf[i] = if (phase < 0.5) 6000 else -6000; phase += step; if (phase >= 1.0) phase -= 1.0 }
            at.write(buf, 0, buf.size)
        }
        at.stop(); at.release()
    }
}
