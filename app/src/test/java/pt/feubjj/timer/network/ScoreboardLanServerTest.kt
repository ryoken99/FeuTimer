package pt.feubjj.timer.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.feubjj.timer.audio.SoundCue
import pt.feubjj.timer.audio.SoundPack
import pt.feubjj.timer.audio.SoundPlayerPort
import pt.feubjj.timer.data.InMemoryTimerStorage
import pt.feubjj.timer.domain.util.SimpleJson
import pt.feubjj.timer.ui.viewmodel.FeuTimerSessionController
import pt.feubjj.timer.ui.viewmodel.TvScreen
import java.net.HttpURLConnection
import java.net.URI

class ScoreboardLanServerTest {

    private object NoopSoundPlayer : SoundPlayerPort {
        override fun play(cue: SoundCue, pack: SoundPack, voiceEnabled: Boolean) = Unit
        override fun stopAll() = Unit
        override fun release() = Unit
    }

    @Test
    fun `QR encoder produces valid square finder-anchored ISO matrix for LAN URLs`() {
        val qr = QrMatrixEncoder.encodeUrl("http://192.168.1.45:7777")
        assertEquals(25, qr.size)
        assertEquals(25, qr[0].size)
        // Top-left, top-right, and bottom-left 7x7 finder corners & centers must be dark
        assertTrue(qr[0][0] && qr[3][3] && qr[6][6])
        assertTrue(qr[0][24] && qr[3][21] && qr[6][18])
        assertTrue(qr[24][0] && qr[21][3] && qr[18][6])
    }

    @Test
    fun `embedded LAN server serves mobile HTML and executes live athlete, point, and result commands`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val storage = InMemoryTimerStorage()
        val controller = FeuTimerSessionController(storage, NoopSoundPlayer, scope, Dispatchers.Unconfined)
        val server = ScoreboardLanServer(
            controller = controller,
            refereeHtmlProvider = { "<!doctype html><title>Referee</title>".toByteArray() },
            scope = scope,
            preferredPorts = listOf(0)
        )

        try {
            server.start()
            assertTrue("Expected bound port > 0", server.boundPort > 0)
            val base = "http://127.0.0.1:${server.boundPort}"

            // 1. Serves mobile referee HTML page
            val html = URI("$base/").toURL().readText()
            assertTrue(html.contains("<title>Referee</title>"))

            // 2. Live athlete name + direct score + delta score + save result + delete result
            postCmd(base, """{"cmd":"set_name","side":"A","name":"Carlos Prates"}""")
            postCmd(base, """{"cmd":"set_name","side":"B","name":"João Silva"}""")
            postCmd(base, """{"cmd":"set_category","category":"Adulto Preta -82kg"}""")
            postCmd(base, """{"cmd":"score_delta","side":"A","field":"points","delta":4}""")
            postCmd(base, """{"cmd":"score_set","side":"B","field":"points","value":2}""")

            assertEquals(TvScreen.SCOREBOARD, controller.uiState.value.screen)
            assertEquals("Carlos Prates", controller.uiState.value.matchScore.fighterAName)
            assertEquals("João Silva", controller.uiState.value.matchScore.fighterBName)
            assertEquals(4, controller.uiState.value.matchScore.fighterAPoints)
            assertEquals(2, controller.uiState.value.matchScore.fighterBPoints)

            // Save match result
            val afterSave = SimpleJson.parseObject(
                postCmd(base, """{"cmd":"save_result","winner":"FIGHTER_A","method":"Finalização"}""")
            )
            val results = afterSave["results"] as List<*>
            assertEquals(1, results.size)
            assertEquals(1, storage.loadMatchResults().size)
            val savedId = storage.loadMatchResults().first().id

            // Delete single match result
            postCmd(base, """{"cmd":"delete_result","id":"$savedId"}""")
            assertEquals(0, storage.loadMatchResults().size)

            // Save two matches then clear all
            postCmd(base, """{"cmd":"save_result","winner":"FIGHTER_A","method":"Pontos"}""")
            postCmd(base, """{"cmd":"save_result","winner":"FIGHTER_B","method":"Vantagens"}""")
            assertEquals(2, storage.loadMatchResults().size)
            postCmd(base, """{"cmd":"clear_results"}""")
            assertEquals(0, storage.loadMatchResults().size)
        } finally {
            server.stop()
            scope.cancel()
        }
    }

    private fun postCmd(base: String, jsonBody: String): String {
        val conn = (URI("$base/api/cmd").toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
        return conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
    }
}
