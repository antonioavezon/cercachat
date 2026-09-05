package cl.antonioavezon.cercachat.protocol

/**
 * Codificador/decodificador mínimo para objetos JSON planos (string/número/bool).
 * Evita kotlinx.serialization y org.json para que las pruebas JVM no dependan de Android.
 */
object FlatJson {
    fun obj(vararg pairs: Pair<String, Any?>): String {
        return pairs.joinToString(prefix = "{", postfix = "}") { (k, v) ->
            "\"${escape(k)}\":${value(v)}"
        }
    }

    fun parse(raw: String): Map<String, String> {
        val text = raw.trim()
        require(text.startsWith("{") && text.endsWith("}")) { "JSON inválido" }
        val inner = text.substring(1, text.length - 1).trim()
        if (inner.isEmpty()) return emptyMap()
        val out = linkedMapOf<String, String>()
        var i = 0
        while (i < inner.length) {
            while (i < inner.length && inner[i].isWhitespace()) i++
            require(i < inner.length && inner[i] == '"') { "Clave inválida" }
            val (key, next) = readString(inner, i)
            i = next
            while (i < inner.length && inner[i].isWhitespace()) i++
            require(i < inner.length && inner[i] == ':') { "Se esperaba ':'" }
            i++
            while (i < inner.length && inner[i].isWhitespace()) i++
            val (value, after) = readValue(inner, i)
            out[key] = value
            i = after
            while (i < inner.length && inner[i].isWhitespace()) i++
            if (i < inner.length && inner[i] == ',') i++
        }
        return out
    }

    fun string(map: Map<String, String>, key: String): String =
        map[key] ?: throw IllegalArgumentException("Falta el campo $key")

    fun long(map: Map<String, String>, key: String): Long =
        string(map, key).toLong()

    fun int(map: Map<String, String>, key: String): Int =
        string(map, key).toInt()

    fun optString(map: Map<String, String>, key: String): String? = map[key]

    private fun value(v: Any?): String = when (v) {
        null -> "null"
        is Boolean -> v.toString()
        is Number -> v.toString()
        else -> "\"${escape(v.toString())}\""
    }

    private fun escape(s: String): String = buildString(s.length) {
        s.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
    }

    private fun readString(src: String, start: Int): Pair<String, Int> {
        require(src[start] == '"')
        val sb = StringBuilder()
        var i = start + 1
        while (i < src.length) {
            val c = src[i]
            if (c == '\\') {
                require(i + 1 < src.length)
                when (val n = src[i + 1]) {
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    '"', '\\' -> sb.append(n)
                    else -> sb.append(n)
                }
                i += 2
            } else if (c == '"') {
                return sb.toString() to (i + 1)
            } else {
                sb.append(c)
                i++
            }
        }
        throw IllegalArgumentException("Cadena sin cerrar")
    }

    private fun readValue(src: String, start: Int): Pair<String, Int> {
        if (src[start] == '"') {
            return readString(src, start)
        }
        var i = start
        while (i < src.length && src[i] != ',' && src[i] != '}') i++
        return src.substring(start, i).trim() to i
    }
}
