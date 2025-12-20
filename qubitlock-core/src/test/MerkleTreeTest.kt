package com.qubitlock.core.utils

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MerkleTreeTest {

    private val merkleTree = MerkleTree()

    @Test
    fun `calculateRoot should return empty string for empty data`() {
        val result = merkleTree.calculateRoot(ByteArray(0))
        assertEquals("", result)
    }

    @Test
    fun `calculateRoot should produce same hash for same data`() {
        val data = "Test data for Merkle tree".toByteArray()
        val hash1 = merkleTree.calculateRoot(data)
        val hash2 = merkleTree.calculateRoot(data)

        assertEquals(hash1, hash2)
        assertNotNull(hash1)
        assertEquals(64, hash1.length)
    }

    @Test
    fun `calculateRoot should produce different hashes for different data`() {
        val data1 = "First test data".toByteArray()
        val data2 = "Second test data".toByteArray()

        val hash1 = merkleTree.calculateRoot(data1)
        val hash2 = merkleTree.calculateRoot(data2)

        assertTrue(hash1 != hash2)
    }

    @Test
    fun `calculateRoot should handle large data`() {
        val largeData = ByteArray(1024 * 1024) { it.toByte() } // 1MB of data
        val hash = merkleTree.calculateRoot(largeData)

        assertNotNull(hash)
        assertEquals(64, hash.length)
    }

    @Test
    fun `calculateRoot should produce consistent hashes for chunked data`() {
        val data = ByteArray(5000) { (it % 256).toByte() }

        val directHash = merkleTree.calculateRoot(data)

        val chunks = data.toList().chunked(1024).map { it.toByteArray() }
        val chunkedData = chunks.flatMap { it.asList() }.toByteArray()
        val chunkedHash = merkleTree.calculateRoot(chunkedData)

        assertEquals(directHash, chunkedHash)
    }

    @Test
    fun `hash should be deterministic`() {
        val testCases = listOf(
            "Small data".toByteArray(),
            "Medium size data that needs to be split".toByteArray(),
            "A".repeat(1024).toByteArray(),
            "B".repeat(2048).toByteArray(),
            "C".repeat(3000).toByteArray(),
        )

        testCases.forEach { data ->
            val hash1 = merkleTree.calculateRoot(data)
            val hash2 = merkleTree.calculateRoot(data)
            assertEquals(hash1, hash2, "Hash should be deterministic for data size ${data.size}")
        }
    }

    @Test
    fun `should handle single byte data`() {
        val singleByte = byteArrayOf(42)
        val hash = merkleTree.calculateRoot(singleByte)

        assertNotNull(hash)
        assertEquals(64, hash.length)
    }

    @Test
    fun `sha256 function should produce valid hex string`() {
        val data = "Test".toByteArray()
        val method = MerkleTree::class.java.getDeclaredMethod("sha256", ByteArray::class.java)
        method.isAccessible = true

        val result = method.invoke(merkleTree, data) as String

        assertEquals(64, result.length)
        assertTrue(result.matches(Regex("[0-9a-f]{64}")))
    }
}