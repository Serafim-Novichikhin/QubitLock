package com.qubitlock.core.utils

import java.security.MessageDigest

// Надо будет доработать
class MerkleTree {
    fun calculateRoot(data: ByteArray): String {
        return buildMerkleTree(splitIntoChunks(data).map { sha256(it) })
    }


    private fun splitIntoChunks(data: ByteArray, chunkSize: Int = 1024): List<ByteArray> {
        val chunks = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < data.size) {
            val end = minOf(offset + chunkSize, data.size)
            chunks.add(data.copyOfRange(offset, end))
            offset += chunkSize
        }

        return chunks
    }

    private fun sha256(data: ByteArray): String {
        return ((MessageDigest.getInstance("SHA-256").digest(data)).joinToString("") { "%02x".format(it) })
    }

    private fun buildMerkleTree(hashes: List<String>): String {
        if (hashes.isEmpty()) return ""
        if (hashes.size == 1) return hashes[0]
        val nextLevel = mutableListOf<String>()
        for (i in hashes.indices step 2) {
            val left = hashes[i]
            val right = if (i + 1 < hashes.size) hashes[i + 1] else left
            val combined = sha256("$left$right".toByteArray())
            nextLevel.add(combined)
        }
        return buildMerkleTree(nextLevel)
    }
}

