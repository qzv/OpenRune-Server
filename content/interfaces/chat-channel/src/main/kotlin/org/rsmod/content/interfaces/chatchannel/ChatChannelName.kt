package org.rsmod.content.interfaces.chatchannel

internal object ChatChannelName {
    private const val MAX_LENGTH = 12
    private const val SEPARATOR = ' '

    fun format(input: String): String? {
        val out = StringBuilder()
        var capitalise = true
        for (char in input) {
            if (!char.isLetterOrDigit()) {
                if (out.isNotEmpty()) {
                    out.append(SEPARATOR)
                }
                capitalise = true
                continue
            }
            out.append(if (capitalise) char.uppercaseChar() else char)
            capitalise = false
        }
        return out.toString().take(MAX_LENGTH).trimEnd(SEPARATOR).ifEmpty { null }
    }
}
