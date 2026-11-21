package pt.feubjj.timer.network

/**
 * Pure, zero-dependency ISO/IEC 18004 Byte-mode QR Code matrix generator (Versions 1..4, ECC Level L).
 * Encodes local LAN URLs (such as `http://192.168.1.45:7777`) into a crisp boolean module grid
 * rendered directly on Android TV Compose `Canvas` without external barcode library bloat.
 */
object QrMatrixEncoder {

    private data class VersionSpec(
        val version: Int,
        val size: Int,
        val dataCodewords: Int,
        val ecCodewords: Int,
        val alignmentCenters: IntArray
    )

    private val SPECS = listOf(
        VersionSpec(version = 1, size = 21, dataCodewords = 19, ecCodewords = 7, alignmentCenters = intArrayOf()),
        VersionSpec(version = 2, size = 25, dataCodewords = 34, ecCodewords = 10, alignmentCenters = intArrayOf(6, 18)),
        VersionSpec(version = 3, size = 29, dataCodewords = 55, ecCodewords = 15, alignmentCenters = intArrayOf(6, 22)),
        VersionSpec(version = 4, size = 33, dataCodewords = 80, ecCodewords = 20, alignmentCenters = intArrayOf(6, 26))
    )

    fun encodeUrl(text: String): Array<BooleanArray> {
        val payload = text.toByteArray(Charsets.ISO_8859_1)
        val spec = SPECS.firstOrNull { payload.size + 2 <= it.dataCodewords } ?: SPECS.last()
        val trimmed = if (payload.size + 2 > spec.dataCodewords) {
            payload.copyOf(spec.dataCodewords - 2)
        } else {
            payload
        }

        val dataCw = buildDataCodewords(trimmed, spec.dataCodewords)
        val ecCw = computeReedSolomonEc(dataCw, spec.ecCodewords)
        val allCodewords = IntArray(dataCw.size + ecCw.size)
        System.arraycopy(dataCw, 0, allCodewords, 0, dataCw.size)
        System.arraycopy(ecCw, 0, allCodewords, dataCw.size, ecCw.size)

        val n = spec.size
        val modules = Array(n) { BooleanArray(n) }
        val isFunction = Array(n) { BooleanArray(n) }

        placeFinderPattern(modules, isFunction, 0, 0)
        placeFinderPattern(modules, isFunction, 0, n - 7)
        placeFinderPattern(modules, isFunction, n - 7, 0)
        placeTimingPatterns(modules, isFunction, n)
        placeAlignmentPatterns(modules, isFunction, spec.alignmentCenters, n)
        reserveFormatAreas(modules, isFunction, n)

        // Dark module
        modules[4 * spec.version + 9][8] = true
        isFunction[4 * spec.version + 9][8] = true

        placeDataBits(modules, isFunction, allCodewords, n)

        // Choose lowest-penalty mask among all 8 ISO masks
        var bestMask = 0
        var bestPenalty = Int.MAX_VALUE
        var bestGrid = modules
        for (mask in 0..7) {
            val candidate = Array(n) { r -> modules[r].clone() }
            applyMask(candidate, isFunction, mask, n)
            writeFormatBits(candidate, mask, n)
            val pen = evaluatePenalty(candidate, n)
            if (pen < bestPenalty) {
                bestPenalty = pen
                bestMask = mask
                bestGrid = candidate
            }
        }
        if (bestMask >= 0) return bestGrid
        return modules
    }

    private fun buildDataCodewords(bytes: ByteArray, capacity: Int): IntArray {
        val bits = ArrayList<Int>(capacity * 8)
        fun pushBits(value: Int, count: Int) {
            for (i in count - 1 downTo 0) {
                bits.add((value ushr i) and 1)
            }
        }
        // Byte mode indicator (0100) + 8-bit character count (Versions 1..9)
        pushBits(0b0100, 4)
        pushBits(bytes.size, 8)
        for (b in bytes) {
            pushBits(b.toInt() and 0xFF, 8)
        }
        // Terminator up to 4 zero bits
        val maxBits = capacity * 8
        repeat(minOf(4, maxBits - bits.size)) { bits.add(0) }
        while (bits.size % 8 != 0) bits.add(0)

        val padBytes = intArrayOf(0xEC, 0x11)
        var padIdx = 0
        while (bits.size < maxBits) {
            pushBits(padBytes[padIdx % 2], 8)
            padIdx++
        }

        return IntArray(capacity) { idx ->
            var v = 0
            for (b in 0 until 8) {
                v = (v shl 1) or bits[idx * 8 + b]
            }
            v
        }
    }

    private fun computeReedSolomonEc(data: IntArray, ecLen: Int): IntArray {
        // Generator polynomial in GF(256) with primitive 0x11D
        var gen = intArrayOf(1)
        var root = 1
        repeat(ecLen) {
            val next = IntArray(gen.size + 1)
            for (i in gen.indices) {
                next[i] = next[i] xor gen[i]
                next[i + 1] = next[i + 1] xor gfMul(gen[i], root)
            }
            gen = next
            root = gfMul(root, 0x02)
        }

        val rem = IntArray(ecLen)
        for (b in data) {
            val factor = b xor rem[0]
            System.arraycopy(rem, 1, rem, 0, ecLen - 1)
            rem[ecLen - 1] = 0
            for (i in 0 until ecLen) {
                rem[i] = rem[i] xor gfMul(gen[i + 1], factor)
            }
        }
        return rem
    }

    private fun gfMul(x: Int, y: Int): Int {
        var a = x
        var b = y
        var res = 0
        repeat(8) {
            if ((b and 1) != 0) res = res xor a
            val hi = (a and 0x80) != 0
            a = (a shl 1) and 0xFF
            if (hi) a = a xor 0x1D
            b = b ushr 1
        }
        return res
    }

    private fun placeFinderPattern(
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        top: Int,
        left: Int
    ) {
        val n = modules.size
        for (dr in -1..7) {
            for (dc in -1..7) {
                val r = top + dr
                val c = left + dc
                if (r !in 0 until n || c !in 0 until n) continue
                val inOuter = dr in 0..6 && dc in 0..6
                val isDark = inOuter && (dr == 0 || dr == 6 || dc == 0 || dc == 6 || (dr in 2..4 && dc in 2..4))
                modules[r][c] = isDark
                isFunction[r][c] = true
            }
        }
    }

    private fun placeTimingPatterns(
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        n: Int
    ) {
        for (i in 8 until n - 8) {
            val dark = i % 2 == 0
            if (!isFunction[6][i]) {
                modules[6][i] = dark
                isFunction[6][i] = true
            }
            if (!isFunction[i][6]) {
                modules[i][6] = dark
                isFunction[i][6] = true
            }
        }
    }

    private fun placeAlignmentPatterns(
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        centers: IntArray,
        n: Int
    ) {
        for (cy in centers) {
            for (cx in centers) {
                if ((cy == 6 && cx == 6) || (cy == 6 && cx == n - 7) || (cy == n - 7 && cx == 6)) continue
                for (dr in -2..2) {
                    for (dc in -2..2) {
                        val r = cy + dr
                        val c = cx + dc
                        val dark = kotlin.math.max(kotlin.math.abs(dr), kotlin.math.abs(dc)) != 1
                        modules[r][c] = dark
                        isFunction[r][c] = true
                    }
                }
            }
        }
    }

    private fun reserveFormatAreas(
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        n: Int
    ) {
        for (i in 0..8) {
            if (i != 6) {
                isFunction[8][i] = true
                isFunction[i][8] = true
                modules[8][i] = false
                modules[i][8] = false
            }
        }
        for (i in 0..7) {
            isFunction[8][n - 1 - i] = true
            modules[8][n - 1 - i] = false
        }
        for (i in 0..6) {
            isFunction[n - 1 - i][8] = true
            modules[n - 1 - i][8] = false
        }
    }

    private fun placeDataBits(
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        codewords: IntArray,
        n: Int
    ) {
        var bitIdx = 0
        val totalBits = codewords.size * 8
        var rightCol = n - 1
        var upward = true
        while (rightCol >= 1) {
            if (rightCol == 6) rightCol = 5
            val rows = if (upward) (n - 1 downTo 0) else (0 until n)
            for (r in rows) {
                for (offset in 0..1) {
                    val c = rightCol - offset
                    if (!isFunction[r][c]) {
                        val dark = if (bitIdx < totalBits) {
                            ((codewords[bitIdx / 8] ushr (7 - (bitIdx % 8))) and 1) != 0
                        } else {
                            false
                        }
                        modules[r][c] = dark
                        bitIdx++
                    }
                }
            }
            upward = !upward
            rightCol -= 2
        }
    }

    private fun applyMask(
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        mask: Int,
        n: Int
    ) {
        for (r in 0 until n) {
            for (c in 0 until n) {
                if (isFunction[r][c]) continue
                val invert = when (mask) {
                    0 -> (r + c) % 2 == 0
                    1 -> r % 2 == 0
                    2 -> c % 3 == 0
                    3 -> (r + c) % 3 == 0
                    4 -> (r / 2 + c / 3) % 2 == 0
                    5 -> (r * c) % 2 + (r * c) % 3 == 0
                    6 -> ((r * c) % 2 + (r * c) % 3) % 2 == 0
                    else -> ((r + c) % 2 + (r * c) % 3) % 2 == 0
                }
                if (invert) modules[r][c] = !modules[r][c]
            }
        }
    }

    private fun writeFormatBits(modules: Array<BooleanArray>, mask: Int, n: Int) {
        // ECC Level L = 0b01
        val data = (0b01 shl 3) or mask
        var rem = data shl 10
        for (i in 14 downTo 10) {
            if ((rem and (1 shl i)) != 0) {
                rem = rem xor (0x537 shl (i - 10))
            }
        }
        val bits = ((data shl 10) or rem) xor 0x5412
        fun bit(i: Int): Boolean = ((bits ushr i) and 1) != 0

        // Top-left copy
        for (i in 0..5) modules[8][i] = bit(14 - i)
        modules[8][7] = bit(8)
        modules[8][8] = bit(7)
        modules[7][8] = bit(6)
        for (i in 9..14) modules[14 - i][8] = bit(14 - i)

        // Split copy (bottom-left + top-right)
        for (i in 0..6) modules[n - 1 - i][8] = bit(14 - i)
        for (i in 7..14) modules[8][n - 15 + i] = bit(14 - i)
    }

    private fun evaluatePenalty(m: Array<BooleanArray>, n: Int): Int {
        var score = 0
        for (r in 0 until n) {
            var runLen = 1
            for (c in 1 until n) {
                if (m[r][c] == m[r][c - 1]) {
                    runLen++
                    if (runLen == 5) score += 3 else if (runLen > 5) score += 1
                } else {
                    runLen = 1
                }
            }
        }
        for (c in 0 until n) {
            var runLen = 1
            for (r in 1 until n) {
                if (m[r][c] == m[r - 1][c]) {
                    runLen++
                    if (runLen == 5) score += 3 else if (runLen > 5) score += 1
                } else {
                    runLen = 1
                }
            }
        }
        return score
    }
}
