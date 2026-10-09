package edu.gatech.cs8803.roomsense.domain.incident

import kotlin.random.Random

/**
 * Short codes a caller can read to 911 without mixing up glyphs.
 * The alphabet drops 0/O and 1/I/L.
 */
object IncidentCodeGenerator {
    const val ALPHABET: String = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    const val DEFAULT_LENGTH: Int = 6

    fun generate(length: Int = DEFAULT_LENGTH, random: Random = Random.Default): String {
        require(length > 0) { "Incident codes need at least one character" }
        return buildString(length) {
            repeat(length) {
                append(ALPHABET[random.nextInt(ALPHABET.length)])
            }
        }
    }
}
