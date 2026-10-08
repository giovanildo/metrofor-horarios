package io.github.giova.metrofortaleza.trip

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Fala os avisos da viagem com a voz do sistema (o "Conversão de texto em voz"
 * do Android), em português do Brasil. Não precisa de internet quando a voz já
 * está baixada no aparelho; sem voz disponível, simplesmente não fala.
 */
internal class TripVoice(context: Context) : TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private val pending = mutableListOf<String>()
    private var nextId = 0

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val result = tts.setLanguage(Locale.forLanguageTag("pt-BR"))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) return
        // Mesmo canal de áudio de GPS de carro: sai no fone e abaixa a música.
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        ready = true
        pending.forEach(::speak)
        pending.clear()
    }

    /** Fala [text] depois de uma pausa curta, para não encavalar com o som da notificação. */
    fun speak(text: String) {
        if (!ready) {
            pending += text
            return
        }
        tts.playSilentUtterance(PAUSE_MILLIS, TextToSpeech.QUEUE_ADD, "pausa-${nextId++}")
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "aviso-${nextId++}")
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    private companion object {
        const val PAUSE_MILLIS = 1_200L
    }
}
