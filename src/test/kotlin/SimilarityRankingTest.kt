package io.github.siddhardh7.iconlens

import org.junit.Assert.assertEquals
import org.junit.Test

class SimilarityRankingTest {

    private val engine = DHashSimilarityEngine()

    private fun descriptor(hash: Long = 0L) = IconDescriptor(listOf(hash) + List(7) { 0L })
    private fun allBitsSetDescriptor() = IconDescriptor(List(8) { -1L })

    @Test
    fun `ranks candidates by descending score and preserves input order for ties`() {
        val query = descriptor()
        val candidates = listOf(
            "A" to descriptor(),
            "B" to descriptor(0b1111L),
            "C" to allBitsSetDescriptor(),
            "D" to descriptor(0b1111L),
        )

        val ranked = rankBySimilarity(engine, query, candidates)

        assertEquals(listOf("A", "B", "D", "C"), ranked.map { it.candidate })
        assertEquals(1.0, ranked[0].score, 0.0001)
        assertEquals(1.0 - 4.0 / 512.0, ranked[1].score, 0.0001)
        assertEquals(1.0 - 4.0 / 512.0, ranked[2].score, 0.0001)
        assertEquals(0.0, ranked[3].score, 0.0001)
    }

    @Test
    fun `empty candidate list produces an empty result`() {
        val ranked = rankBySimilarity(engine, descriptor(), emptyList<Pair<String, IconDescriptor>>())

        assertEquals(emptyList<ScoredMatch<String>>(), ranked)
    }
}
