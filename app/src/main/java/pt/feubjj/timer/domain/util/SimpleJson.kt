package pt.feubjj.timer.domain.util

/**
 * Dependency-free RFC-8259 JSON parser and serializer so match history storage and the LAN
 * WebSocket referee server work identically on Android TV and pure JVM unit tests (without `org.json` stubs).
 */
object SimpleJson {

    fun quote(value: String): String {
        val sb = StringBuilder(value.length + 4)
        sb.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch.code < 0x20) {
                    sb.append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    sb.append(ch)
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }

    fun encodeObject(fields: Map<String, Any?>): String = buildString {
        append('{')
        var first = true
        for ((k, v) in fields) {
            if (!first) append(',')
            first = false
            append(quote(k)).append(':').append(encodeValue(v))
        }
        append('}')
    }

    fun encodeArray(items: List<Any?>): String = buildString {
        append('[')
        items.forEachIndexed { idx, v ->
            if (idx > 0) append(',')
            append(encodeValue(v))
        }
        append(']')
    }

    @Suppress("UNCHECKED_CAST")
    fun encodeValue(value: Any?): String = when (value) {
        null -> "null"
        is Boolean -> if (value) "true" else "false"
        is Number -> value.toString()
        is String -> quote(value)
        is Map<*, *> -> encodeObject(value as Map<String, Any?>)
        is List<*> -> encodeArray(value as List<Any?>)
        else -> quote(value.toString())
    }

    fun parse(json: String): Any? = Parser(json).parseRoot()

    @Suppress("UNCHECKED_CAST")
    fun parseObject(json: String): Map<String, Any?> =
        (parse(json) as? Map<String, Any?>) ?: emptyMap()

    @Suppress("UNCHECKED_CAST")
    fun parseArray(json: String): List<Any?> =
        (parse(json) as? List<Any?>) ?: emptyList()

    private class Parser(private val src: String) {
        private var pos = 0

        fun parseRoot(): Any? {
            skipWs()
            if (pos >= src.length) return null
            return readValue()
        }

        private fun readValue(): Any? {
            skipWs()
            if (pos >= src.length) return null
            return when (src[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> { pos += 4; true }
                'f' -> { pos += 5; false }
                'n' -> { pos += 4; null }
                else -> readNumber()
            }
        }

        private fun readObject(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            pos++ // '{'
            skipWs()
            if (pos < src.length && src[pos] == '}') {
                pos++
                return map
            }
            while (pos < src.length) {
                skipWs()
                val key = readString()
                skipWs()
                if (pos < src.length && src[pos] == ':') pos++
                val value = readValue()
                map[key] = value
                skipWs()
                if (pos < src.length && src[pos] == ',') {
                    pos++
                } else if (pos < src.length && src[pos] == '}') {
                    pos++
                    break
                } else {
                    break
                }
            }
            return map
        }

        private fun readArray(): List<Any?> {
            val list = ArrayList<Any?>()
            pos++ // '['
            skipWs()
            if (pos < src.length && src[pos] == ']') {
                pos++
                return list
            }
            while (pos < src.length) {
                list += readValue()
                skipWs()
                if (pos < src.length && src[pos] == ',') {
                    pos++
                } else if (pos < src.length && src[pos] == ']') {
                    pos++
                    break
                } else {
                    break
                }
            }
            return list
        }

        private fun readString(): String {
            if (pos >= src.length || src[pos] != '"') return ""
            pos++
            val sb = StringBuilder()
            while (pos < src.length) {
                val c = src[pos++]
                if (c == '"') break
                if (c == '\\' && pos < src.length) {
                    when (val esc = src[pos++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> if (pos + 4 <= src.length) {
                            val hex = src.substring(pos, pos + 4)
                            pos += 4
                            sb.append(hex.toIntOrNull(16)?.toChar() ?: '?')
                        }
                        else -> sb.append(esc)
                    }
                } else {
                    sb.append(c)
                }
            }
            return sb.toString()
        }

        private fun readNumber(): Number {
            val start = pos
            while (pos < src.length && src[pos] in "-+0123456789.eE") pos++
            val raw = src.substring(start, pos)
            return if ('.' in raw || 'e' in raw || 'E' in raw) {
                raw.toDoubleOrNull() ?: 0.0
            } else {
                raw.toLongOrNull() ?: 0L
            }
        }

        private fun skipWs() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }
    }
}
