package io.github.siddhardh7.iconlens

interface IconSource {
    suspend fun discover(): List<IconResource>
}
