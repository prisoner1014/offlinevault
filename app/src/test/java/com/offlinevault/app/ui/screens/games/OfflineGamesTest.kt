package com.offlinevault.app.ui.screens.games

import org.junit.Assert.*
import org.junit.Test

class OfflineGamesTest {

    @Test
    fun testCatalogHasExactlyTenGames() {
        assertEquals("There should be exactly 10 offline games", 10, OfflineGameCatalog.games.size)
    }

    @Test
    fun testAllGamesHaveUniqueIdsAndValidMetadata() {
        val ids = mutableSetOf<String>()
        OfflineGameCatalog.games.forEach { game ->
            assertTrue("Game ID must not be blank", game.id.isNotBlank())
            assertTrue("Game ID '${game.id}' must be unique", ids.add(game.id))
            assertTrue("Game title must not be blank", game.title.isNotBlank())
            assertTrue("Game description must not be blank", game.description.isNotBlank())
            assertTrue("Game instructions must not be blank", game.instructions.isNotBlank())
            assertTrue("Game badge must not be blank", game.badge.isNotBlank())
        }
    }

    @Test
    fun testAllGamesGenerateCompleteSelfContainedHtml() {
        OfflineGameCatalog.games.forEach { game ->
            val html = GameHtmlProvider.getHtmlForGame(game.id)
            assertNotNull("HTML for '${game.id}' must not be null", html)
            assertTrue("HTML for '${game.id}' must not be blank", html.isNotBlank())
            assertTrue("HTML for '${game.id}' must contain <!DOCTYPE html>", html.contains("<!DOCTYPE html>"))
            assertTrue("HTML for '${game.id}' must contain viewport meta tag", html.contains("viewport"))
            assertTrue("HTML for '${game.id}' must contain AudioFX synthesizer", html.contains("AudioFX"))
            assertTrue("HTML for '${game.id}' must contain background styling", html.contains("#090D16") || html.contains("#0D131F"))
        }
    }

    @Test
    fun testFindGameById() {
        val game2048 = OfflineGameCatalog.getGameById("2048")
        assertNotNull(game2048)
        assertEquals("2048 Cyber", game2048?.title)

        val snake = OfflineGameCatalog.getGameById("snake")
        assertNotNull(snake)
        assertEquals("Retro Snake", snake?.title)

        val tetris = OfflineGameCatalog.getGameById("tetris")
        assertNotNull(tetris)
        assertEquals("Block Drop", tetris?.title)

        val nonExistent = OfflineGameCatalog.getGameById("non_existent_game")
        assertNull(nonExistent)
    }
}
