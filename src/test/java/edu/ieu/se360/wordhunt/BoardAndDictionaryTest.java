package edu.ieu.se360.wordhunt;

import edu.ieu.se360.wordhunt.engine.BoardGenerator;
import edu.ieu.se360.wordhunt.engine.DictionaryManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 Unit Tests for {@link BoardGenerator} and {@link DictionaryManager}.
 */
@DisplayName("BoardGenerator & DictionaryManager Unit Tests")
class BoardAndDictionaryTest {

    private BoardGenerator boardGenerator;
    private DictionaryManager dictionaryManager;

    @BeforeEach
    void setUp() {
        boardGenerator = new BoardGenerator(42L);
        dictionaryManager = DictionaryManager.getInstance();
    }

    @Test
    @DisplayName("BoardGenerator generates exactly 12 letters and contains at least 4 vowels")
    void testBoardGeneratorProducesTwelveLettersWithAtLeastFourVowels() {
        for (int trial = 0; trial < 25; trial++) {
            List<Character> board = boardGenerator.generateBoard();
            assertNotNull(board, "Generated board should not be null");
            assertEquals(12, board.size(), "Board must contain exactly 12 letters");

            long vowelCount = board.stream()
                    .filter(BoardGenerator::isVowel)
                    .count();

            assertTrue(vowelCount >= 4,
                    "Board must contain at least 4 vowels (A, E, I, O, U), but found " + vowelCount + " in " + board);
        }
    }

    @Test
    @DisplayName("BoardGenerator shuffleBoard preserves the exact same 12 letters")
    void testShuffleBoardPreservesCharacters() {
        List<Character> original = boardGenerator.generateBoard();
        List<Character> shuffled = boardGenerator.shuffleBoard(original);

        assertEquals(original.size(), shuffled.size(), "Shuffled board must have the same size");
        assertTrue(shuffled.containsAll(original) && original.containsAll(shuffled),
                "Shuffled board must contain the exact same characters");
    }

    @Test
    @DisplayName("DictionaryManager validates legitimate English words in O(1) via HashSet and rejects nonsense words")
    void testDictionaryValidatesEnglishWordsAndRejectsNonsense() {
        assertTrue(dictionaryManager.getDictionarySize() > 500, "Dictionary should be populated from dictionary_en.json");

        // Valid words (case-insensitive)
        assertTrue(dictionaryManager.isValidWord("CAT"), "'CAT' should be a valid word");
        assertTrue(dictionaryManager.isValidWord("stone"), "'stone' (lowercase) should be valid");
        assertTrue(dictionaryManager.isValidWord("  PLANET  "), "'PLANET' with whitespace should be valid");
        assertTrue(dictionaryManager.isValidWord("STREAM"), "'STREAM' should be a valid word");
        assertTrue(dictionaryManager.isValidWord("CARPET"), "'CARPET' should be a valid word");
        assertTrue(dictionaryManager.isValidWord("MARKET"), "'MARKET' should be a valid word");
        assertTrue(dictionaryManager.isValidWord("GARDEN"), "'GARDEN' should be a valid word");
        assertTrue(dictionaryManager.isValidWord("CASTLE"), "'CASTLE' should be a valid word");
        assertTrue(dictionaryManager.getDictionarySize() >= 15000, "Dictionary should contain at least 15,000 words");

        // Nonsense / invalid words
        assertFalse(dictionaryManager.isValidWord("XYZQWK"), "Nonsense string 'XYZQWK' must be rejected");
        assertFalse(dictionaryManager.isValidWord("ASDFGHJKL"), "Nonsense string 'ASDFGHJKL' must be rejected");
        assertFalse(dictionaryManager.isValidWord(""), "Empty string must be rejected");
        assertFalse(dictionaryManager.isValidWord("   "), "Blank string must be rejected");
        assertFalse(dictionaryManager.isValidWord(null), "Null input must be rejected");

        // Verify O(1) lookup performance over 10,000 queries (< 50 ms)
        long startNano = System.nanoTime();
        for (int i = 0; i < 10_000; i++) {
            dictionaryManager.isValidWord("STONE");
            dictionaryManager.isValidWord("NONEXISTENTWORD");
        }
        long elapsedMs = (System.nanoTime() - startNano) / 1_000_000;
        assertTrue(elapsedMs < 200, "10,000 HashSet lookups should complete in O(1) time (<200ms), took: " + elapsedMs + "ms");
    }

    @Test
    @DisplayName("DictionaryManager canFormWord enforces board letter frequencies accurately")
    void testCanFormWordRespectsLetterFrequencies() {
        List<Character> board = Arrays.asList('S', 'T', 'O', 'N', 'E', 'A', 'R', 'M', 'I', 'L', 'C', 'P');

        assertTrue(dictionaryManager.canFormWord("STONE", board), "'STONE' can be formed from board");
        assertTrue(dictionaryManager.canFormWord("STAR", board), "'STAR' can be formed from board");
        assertFalse(dictionaryManager.canFormWord("STONES", board), "'STONES' requires two 'S' tiles, but board has only one 'S'");
        assertFalse(dictionaryManager.canFormWord("ZEBRA", board), "'ZEBRA' contains 'Z' and 'B' which are not on the board");
    }
}
