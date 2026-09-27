package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.model.ArtSlot
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins `GET /api/search/cover` to the SearchCoverSchema shape in the RomM art-types branch
 * (`backend/endpoints/responses/search.py`): every item is {provider, art_type, name, resources}.
 */
class RomMCoverSearchTest {

    private val adapter: JsonAdapter<List<RomMCoverSearchResult>> = Moshi.Builder()
        .build()
        .adapter(Types.newParameterizedType(List::class.java, RomMCoverSearchResult::class.java))

    private fun parse(json: String): List<RomMCoverSearchResult> = adapter.fromJson(json)!!

    private fun item(artType: String?, vararg resources: String): String {
        val tag = artType?.let { "\"art_type\":\"$it\"," } ?: ""
        return """{"provider":"sgdb",$tag"name":"Chrono Trigger","resources":[${resources.joinToString(",")}]}"""
    }

    private fun resource(url: String?, thumb: String, type: String = "static"): String {
        val urlField = url?.let { "\"url\":\"$it\"," } ?: ""
        return """{${urlField}"thumb":"$thumb","type":"$type","width":1920,"height":620}"""
    }

    @Test
    fun `the art type tag is read from the wire`() {
        val parsed = parse("[${item("hero", resource("https://cdn/hero/a.png", "https://cdn/thumb/a.png"))}]")

        assertEquals("hero", parsed.single().artType)
    }

    @Test
    fun `slots map to the server art types`() {
        assertEquals(RomMCoverArtType.GRID, RomMCoverArtType.forSlot(ArtSlot.COVER))
        assertEquals(RomMCoverArtType.HERO, RomMCoverArtType.forSlot(ArtSlot.BACKGROUND))
        assertEquals(RomMCoverArtType.LOGO, RomMCoverArtType.forSlot(ArtSlot.LOGO))
        assertEquals(listOf("grid", "hero", "logo"), RomMCoverArtType.entries.map { it.wireName })
    }

    @Test
    fun `hero results keep only items tagged hero`() {
        val parsed = parse(
            "[" +
                item("hero", resource("https://cdn/hero/a.png", "https://cdn/thumb/a.png")) + "," +
                item("grid", resource("https://cdn/grid/b.png", "https://cdn/thumb/b.png")) +
                "]"
        )

        val urls = parsed.usableResources(RomMCoverArtType.HERO).map { it.url }

        assertEquals(listOf("https://cdn/hero/a.png"), urls)
    }

    @Test
    fun `logo results keep only items tagged logo`() {
        val parsed = parse(
            "[" +
                item("logo", resource("https://cdn/logo/a.png", "https://cdn/thumb/a.png")) + "," +
                item("hero", resource("https://cdn/hero/b.png", "https://cdn/thumb/b.png")) +
                "]"
        )

        val urls = parsed.usableResources(RomMCoverArtType.LOGO).map { it.url }

        assertEquals(listOf("https://cdn/logo/a.png"), urls)
    }

    @Test
    fun `an untagged response from an older server yields nothing for hero or logo`() {
        val parsed = parse("[${item(null, resource("https://cdn/grid/a.png", "https://cdn/thumb/a.png"))}]")

        assertTrue(parsed.usableResources(RomMCoverArtType.HERO).isEmpty())
        assertTrue(parsed.usableResources(RomMCoverArtType.LOGO).isEmpty())
    }

    @Test
    fun `grid results are taken whether tagged or not`() {
        val parsed = parse(
            "[" +
                item(null, resource("https://cdn/grid/a.png", "https://cdn/thumb/a.png")) + "," +
                item("grid", resource("https://cdn/grid/b.png", "https://cdn/thumb/b.png")) +
                "]"
        )

        val urls = parsed.usableResources(RomMCoverArtType.GRID).map { it.url }

        assertEquals(listOf("https://cdn/grid/a.png", "https://cdn/grid/b.png"), urls)
    }

    @Test
    fun `animated results are dropped by type or by webm file`() {
        val parsed = parse(
            "[" + item(
                "grid",
                resource("https://cdn/grid/still.png", "https://cdn/thumb/still.png"),
                resource("https://cdn/grid/typed.png", "https://cdn/thumb/typed.png", type = "animated"),
                resource("https://cdn/grid/moving.webm", "https://cdn/thumb/moving.png"),
                resource("https://cdn/grid/other.png", "https://cdn/thumb/other.WEBM")
            ) + "]"
        )

        val urls = parsed.usableResources(RomMCoverArtType.GRID).map { it.url }

        assertEquals(listOf("https://cdn/grid/still.png"), urls)
    }

    @Test
    fun `animated hero results are dropped too`() {
        val parsed = parse(
            "[" + item(
                "hero",
                resource("https://cdn/hero/still.png", "https://cdn/thumb/still.png"),
                resource("https://cdn/hero/moving.webm", "https://cdn/thumb/moving.webm", type = "animated")
            ) + "]"
        )

        val urls = parsed.usableResources(RomMCoverArtType.HERO).map { it.url }

        assertEquals(listOf("https://cdn/hero/still.png"), urls)
    }

    @Test
    fun `a grid without a url is rebuilt from its thumb`() {
        val parsed = parse("[${item("grid", resource(null, "https://cdn/thumb/a.png"))}]")

        val urls = parsed.usableResources(RomMCoverArtType.GRID).map { it.url }

        assertEquals(listOf("https://cdn/grid/a.png"), urls)
    }

    @Test
    fun `a hero or logo without a url is dropped rather than rebuilt from its thumb`() {
        val hero = parse("[${item("hero", resource(null, "https://cdn/thumb/a.png"))}]")
        val logo = parse("[${item("logo", resource(null, "https://cdn/thumb/b.png"))}]")

        assertTrue(hero.usableResources(RomMCoverArtType.HERO).isEmpty())
        assertTrue(logo.usableResources(RomMCoverArtType.LOGO).isEmpty())
    }
}
