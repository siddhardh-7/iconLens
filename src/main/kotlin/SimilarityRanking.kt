package io.github.siddhardh7.iconlens

data class ScoredMatch<T>(val candidate: T, val score: Double)

fun <T> rankBySimilarity(
    engine: SimilarityEngine,
    query: IconDescriptor,
    candidates: List<Pair<T, IconDescriptor>>,
): List<ScoredMatch<T>> =
    candidates.map { (item, descriptor) -> ScoredMatch(item, engine.score(query, descriptor)) }
        .sortedByDescending { it.score }
