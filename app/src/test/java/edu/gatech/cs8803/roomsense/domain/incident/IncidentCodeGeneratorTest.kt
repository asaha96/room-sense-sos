package edu.gatech.cs8803.roomsense.domain.incident

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class IncidentCodeGeneratorTest {
    @Test
    fun alphabetDropsAmbiguousCharacters() {
        val alphabet = IncidentCodeGenerator.ALPHABET
        assertEquals(32, alphabet.length)
        assertTrue('0' !in alphabet)
        assertTrue('O' !in alphabet)
        assertTrue('1' !in alphabet)
        assertTrue('I' !in alphabet)
        assertTrue('L' !in alphabet)
    }

    @Test
    fun codesUseOnlyTheAlphabetAndDefaultToSixCharacters() {
        val random = Random(42)
        val pattern = Regex("^[${IncidentCodeGenerator.ALPHABET}]{6}$")
        repeat(200) {
            val code = IncidentCodeGenerator.generate(random = random)
            assertTrue(code, pattern.matches(code))
        }
    }

    @Test
    fun sameSeedIsDeterministic() {
        assertEquals(
            IncidentCodeGenerator.generate(6, Random(7)),
            IncidentCodeGenerator.generate(6, Random(7)),
        )
    }

    @Test
    fun honorsLength() {
        assertEquals(4, IncidentCodeGenerator.generate(4, Random(1)).length)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyLength() {
        IncidentCodeGenerator.generate(0, Random(1))
    }
}
