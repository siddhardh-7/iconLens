package io.github.siddhardh7.iconlens

interface SimilarityEngine {
    fun describe(icon: NormalizedIcon): IconDescriptor
    fun score(a: IconDescriptor, b: IconDescriptor): Double
}
