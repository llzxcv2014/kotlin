/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.library.impl

import org.jetbrains.kotlin.konan.file.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

abstract class IrArrayReader(private val buffer: ByteBuffer) {
    private val indexToOffset: IntArray

    fun entryCount() = indexToOffset.size - 1

    init {
        val count = buffer.int
        indexToOffset = IntArray(count + 1)
        indexToOffset[0] = 4 * (count + 1)
        for (i in 0 until count) {
            val size = buffer.int
            indexToOffset[i + 1] = indexToOffset[i] + size
        }
    }

    fun tableItemBytes(id: Int): ByteArray {
        val offset = indexToOffset[id]
        val size = indexToOffset[id + 1] - offset
        val result = ByteArray(size)
        buffer.position(offset)
        buffer.get(result, 0, size)
        return result
    }
}

class IrArrayFileReader(file: File) : IrArrayReader(file.readBuffer)
class IrArrayMemoryReader(bytes: ByteArray) : IrArrayReader(bytes.buffer)

abstract class IrMultiArrayReader(private val buffer: ByteBuffer) {
    private val indexToOffset: IntArray
    private val indexIndexToOffset = mutableMapOf<Int, IntArray>()

    private fun readOffsets(position: Int): IntArray {
        buffer.position(position)
        val count = buffer.int
        val result = IntArray(count + 1)
        result[0] = 4 * (count + 1)
        for (i in 0 until count) {
            val size = buffer.int
            result[i + 1] = result[i] + size
        }

        return result
    }

    init {
        indexToOffset = readOffsets(0)
    }

    fun tableItemBytes(id: Int): ByteArray {
        val offset = indexToOffset[id]
        val size = indexToOffset[id + 1] - offset
        val result = ByteArray(size)
        buffer.position(offset)
        buffer.get(result, 0, size)
        return result
    }

    fun tableItemBytes(row: Int, column: Int): ByteArray {
        val rowOffset = indexToOffset[row]

        val collumnOffsets = indexIndexToOffset.getOrPut(row) {
            readOffsets(rowOffset)
        }

        val dataOffset = collumnOffsets[column]
        val dataSize = collumnOffsets[column + 1] - dataOffset
        val result = ByteArray(dataSize)

        buffer.position(rowOffset + dataOffset)
        buffer.get(result, 0, dataSize)

        return result
    }
}

class IrMultiArrayFileReader(file: File) : IrMultiArrayReader(file.readBuffer)
class IrMultiArrayMemoryReader(bytes: ByteArray) : IrMultiArrayReader(bytes.buffer)

abstract class IrMultiTableReader<K>(private val buffer: ByteBuffer, private val keyReader: ByteBuffer.() -> K) {
    private val indexToOffset: IntArray
    private val indexToIndexMap = mutableMapOf<Int, Map<K, Pair<Int, Int>>>()

    private fun readOffsets(position: Int): IntArray {
        buffer.position(position)
        val count = buffer.int
        val result = IntArray(count + 1)
        result[0] = 4 * (count + 1)
        for (i in 0 until count) {
            val size = buffer.int
            result[i + 1] = result[i] + size
        }

        return result
    }

    init {
        indexToOffset = readOffsets(0)
    }

    private fun readIndexMap(position: Int): Map<K, Pair<Int, Int>> {
        buffer.position(position)
        val result = mutableMapOf<K, Pair<Int, Int>>()

        val count = buffer.int

        for (i in 0 until count) {
            val key = keyReader(buffer)
            val offset = buffer.int
            val size = buffer.int

            result[key] = offset to size
        }

        return result
    }

    fun tableItemBytes(row: Int, id: K): ByteArray {

        val rowOffset = indexToOffset[row]

        val indexToMap = indexToIndexMap.getOrPut(row) {
            readIndexMap(rowOffset)
        }

        val coordinates = indexToMap[id] ?: error("No coordinates found for $id")
        val offset = coordinates.first
        val size = coordinates.second
        val result = ByteArray(size)
        buffer.position(rowOffset + offset)
        buffer.get(result, 0, size)
        return result
    }
}

abstract class IrTableReader<K>(private val buffer: ByteBuffer, keyReader: ByteBuffer.() -> K) {
    private val indexToOffset = mutableMapOf<K, Pair<Int, Int>>()

    init {
        val count = buffer.int
        for (i in 0 until count) {
            val key = keyReader(buffer)
            val offset = buffer.int
            val size = buffer.int

            indexToOffset[key] = offset to size
        }
    }

    fun tableItemBytes(id: K): ByteArray {
        val coordinates = indexToOffset[id] ?: error("No coordinates found for $id")
        val offset = coordinates.first
        val size = coordinates.second
        val result = ByteArray(size)
        buffer.position(offset)
        buffer.get(result, 0, size)
        return result
    }
}

val File.readBuffer: ByteBuffer get() = this.readBytes().buffer
val ByteArray.buffer: ByteBuffer get() = ByteBuffer.wrap(this)

class IndexIrTableFileReader(file: File) : IrTableReader<Long>(file.readBuffer, { long })
class IndexIrTableMemoryReader(bytes: ByteArray) : IrTableReader<Long>(bytes.buffer, { long })

data class DeclarationId(val id: Int)

class DeclarationIrTableFileReader(file: File) : IrTableReader<DeclarationId>(file.readBuffer, { DeclarationId(int) })
class DeclarationIrTableMemoryReader(bytes: ByteArray) : IrTableReader<DeclarationId>(bytes.buffer, { DeclarationId(int) })

class DeclarationIrMultiTableFileReader(file: File) : IrMultiTableReader<DeclarationId>(file.readBuffer, { DeclarationId(int) })
class DeclarationIrMultiTableMemoryReader(bytes: ByteArray) : IrMultiTableReader<DeclarationId>(bytes.buffer, { DeclarationId(int) })