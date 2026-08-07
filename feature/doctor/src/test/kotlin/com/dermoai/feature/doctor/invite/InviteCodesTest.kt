package com.dermoai.feature.doctor.invite

import com.dermoai.core.domain.model.DoctorInvite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The invite code is the credential that grants a clinician sight of someone's
 * medical photos, and it is transferred by being read aloud in a room.
 *
 * The failures worth catching are therefore of two kinds. Generation: a code
 * containing a glyph the alphabet excludes (I/L/O/0/1) is a code the patient
 * mistypes and the doctor cannot debug, and a short code is one the lookup
 * never matches. Normalisation: a field that rejects the lowercase, spaced or
 * hyphenated form of a correct code sends the patient back to a doctor who will
 * read out exactly the same characters again.
 */
class InviteCodesTest {

    /** Every glyph the alphabet deliberately omits, per DoctorInvite's KDoc. */
    private val ambiguous = listOf('I', 'L', 'O', '0', '1')

    // ── generation ───────────────────────────────────────────────────────────

    @Test
    fun `a generated code is exactly the declared length`() {
        // The UI sizes its field from CODE_LENGTH; a generator that disagreed
        // would produce codes the patient's field silently truncates.
        repeat(200) { seed ->
            assertEquals(
                DoctorInvite.CODE_LENGTH,
                InviteCodes.generate(Random(seed)).length,
            )
        }
    }

    @Test
    fun `a generated code only uses characters from the declared alphabet`() {
        // Anything outside the alphabet is dropped by normalise(), so such a
        // code could never be redeemed — it would fail as "not found" with no
        // way for either party to work out why.
        repeat(500) { seed ->
            InviteCodes.generate(Random(seed)).forEach { char ->
                assertTrue(
                    "Generated '$char', which is outside CODE_ALPHABET",
                    char in DoctorInvite.CODE_ALPHABET,
                )
            }
        }
    }

    @Test
    fun `a generated code never contains an ambiguous glyph`() {
        // Stated separately from the alphabet check because this is the
        // property that matters at the bedside: I/1 and O/0 are the pairs
        // people mishear and mistype.
        repeat(500) { seed ->
            val code = InviteCodes.generate(Random(seed))
            ambiguous.forEach { glyph ->
                assertFalse("Generated code '$code' contains '$glyph'", glyph in code)
            }
        }
    }

    @Test
    fun `generation from the same seed is reproducible`() {
        // Guards the injected-Random contract: if generate() reached for a
        // global source, this feature's only handle on the generator would be
        // gone and the security property untestable.
        assertEquals(InviteCodes.generate(Random(42)), InviteCodes.generate(Random(42)))
    }

    @Test
    fun `generation draws on more than one character of the alphabet`() {
        // A degenerate generator (always the same character) would satisfy every
        // check above while producing a trivially guessable code.
        val distinct = (0 until 50)
            .flatMap { seed -> InviteCodes.generate(Random(seed)).toList() }
            .distinct()
        assertTrue(
            "Only ${distinct.size} distinct characters across 50 codes",
            distinct.size > DoctorInvite.CODE_ALPHABET.length / 2,
        )
    }

    // ── normalisation ────────────────────────────────────────────────────────

    @Test
    fun `normalise uppercases what the patient typed`() {
        // Phone keyboards default to lowercase; codes are stored uppercase.
        assertEquals("ABCD2345", InviteCodes.normalise("abcd2345"))
    }

    @Test
    fun `normalise accepts spaces and hyphens`() {
        // Both appear when the code is printed on a card or read out in groups.
        assertEquals("ABCD2345", InviteCodes.normalise("ABCD-2345"))
        assertEquals("ABCD2345", InviteCodes.normalise("AB CD 23 45"))
        assertEquals("ABCD2345", InviteCodes.normalise(" abcd - 2345 "))
    }

    @Test
    fun `normalise drops characters the alphabet excludes`() {
        // A typed I or O cannot be part of a real code, so it is discarded
        // rather than allowed to produce a lookup that can only ever miss.
        assertEquals("ABCD", InviteCodes.normalise("AIBOCD01"))
    }

    @Test
    fun `normalise truncates an over-long input to the code length`() {
        // Covers a pasted deep link and a double paste: without the cap, the
        // field would hold a string no row can match.
        val pasted = InviteCodes.deepLink("ABCD2345")
        val normalised = InviteCodes.normalise(pasted)
        assertEquals(DoctorInvite.CODE_LENGTH, normalised.length)
        assertEquals(DoctorInvite.CODE_LENGTH, InviteCodes.normalise("ABCD2345EFGH").length)
    }

    @Test
    fun `a generated code survives a round trip through normalise`() {
        // The two halves of the flow have to agree: whatever the doctor's screen
        // produced must be exactly what the patient's field yields.
        repeat(200) { seed ->
            val code = InviteCodes.generate(Random(seed))
            assertEquals(code, InviteCodes.normalise(code.lowercase()))
        }
    }

    @Test
    fun `isComplete is true only at the full code length`() {
        assertFalse(InviteCodes.isComplete(""))
        assertFalse(InviteCodes.isComplete("ABCD234"))
        assertTrue(InviteCodes.isComplete("ABCD2345"))
    }

    // ── display and deep link ────────────────────────────────────────────────

    @Test
    fun `the deep link keeps the code verbatim`() {
        // The QR and the typed path must resolve to the same credential.
        assertEquals("dermoai://invite/ABCD2345", InviteCodes.deepLink("ABCD2345"))
    }

    @Test
    fun `formatForDisplay hyphenates only a complete code`() {
        // Hyphenating a partial code mid-typing would show the patient a shape
        // that does not match the card in the doctor's hand.
        assertEquals("ABCD-2345", InviteCodes.formatForDisplay("ABCD2345"))
        assertEquals("ABC", InviteCodes.formatForDisplay("ABC"))
    }
}
