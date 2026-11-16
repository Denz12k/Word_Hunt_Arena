package edu.ieu.se360.wordhunt.engine;

import edu.ieu.se360.wordhunt.config.ConfigManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Generates a balanced 12-letter pool containing at least 4 vowels (A, E, I, O, U)
 * and 8 high-utility consonants, shuffled randomly via {@link Collections#shuffle(List)}.
 */
public class BoardGenerator {
    public static final int DEFAULT_BOARD_SIZE = 12;
    public static final int MIN_VOWELS = 4;

    private static final char[] VOWELS = {'A', 'E', 'I', 'O', 'U'};
    // Weighted vowel pool favoring common English vowels A, E, I, O
    private static final char[] WEIGHTED_VOWELS = {
            'A', 'A', 'A', 'E', 'E', 'E', 'E', 'I', 'I', 'I', 'O', 'O', 'O', 'U', 'U'
    };

    // Quality consonants weighted by utility in forming 3-8 letter words, plus occasional high-value letters
    private static final char[] WEIGHTED_CONSONANTS = {
            'R', 'R', 'R', 'S', 'S', 'S', 'T', 'T', 'T', 'L', 'L', 'L', 'N', 'N', 'N',
            'C', 'C', 'D', 'D', 'M', 'M', 'P', 'P', 'B', 'B', 'G', 'G', 'H', 'H',
            'F', 'W', 'Y', 'K', 'V', 'J', 'X', 'Q', 'Z'
    };

    private final Random random;

    public BoardGenerator() {
        this.random = new Random();
    }

    public BoardGenerator(long seed) {
        this.random = new Random(seed);
    }

    /**
     * Generates a balanced 12-letter board with at least 4 vowels (A, E, I, O, U)
     * and 8 quality consonants, shuffled with {@link Collections#shuffle(List, Random)}.
     */
    public List<Character> generateBoard() {
        int boardSize = ConfigManager.getInstance().getBoardSize();
        if (boardSize <= 0) {
            boardSize = DEFAULT_BOARD_SIZE;
        }
        return generateBoard(boardSize);
    }

    /**
     * Generates a balanced board of the requested size (default 12) with at least 4 vowels
     * (or proportional if boardSize < 4) and quality consonants.
     */
    public List<Character> generateBoard(int boardSize) {
        int targetSize = boardSize > 0 ? boardSize : DEFAULT_BOARD_SIZE;
        int vowelCount = Math.min(targetSize, Math.max(MIN_VOWELS, targetSize / 3));
        // Occasionally allow 5 vowels on a 12-letter board for richer word variety while keeping at least 4 vowels
        if (targetSize >= DEFAULT_BOARD_SIZE && random.nextBoolean()) {
            vowelCount = MIN_VOWELS; // Strictly 4 vowels + 8 consonants as specified
        } else if (targetSize == DEFAULT_BOARD_SIZE) {
            vowelCount = MIN_VOWELS;
        }

        List<Character> board = new ArrayList<>(targetSize);
        Map<Character, Integer> counts = new HashMap<>();

        // Pick 4 vowels (ensuring good diversity)
        for (int i = 0; i < vowelCount; i++) {
            char vowel = pickWithMaxLimit(WEIGHTED_VOWELS, counts, 2);
            board.add(vowel);
        }

        // Pick remaining (8) quality consonants
        int consonantCount = targetSize - vowelCount;
        for (int i = 0; i < consonantCount; i++) {
            char consonant = pickWithMaxLimit(WEIGHTED_CONSONANTS, counts, 2);
            board.add(consonant);
        }

        Collections.shuffle(board, random);
        return board;
    }

    /**
     * Shuffles an existing board of letters and returns a new shuffled list.
     */
    public List<Character> shuffleBoard(List<Character> currentBoard) {
        if (currentBoard == null) {
            return Collections.emptyList();
        }
        List<Character> shuffled = new ArrayList<>(currentBoard);
        Collections.shuffle(shuffled, random);
        return shuffled;
    }

    /**
     * Checks whether a given character is one of the 5 English vowels (A, E, I, O, U).
     */
    public static boolean isVowel(char ch) {
        char upper = Character.toUpperCase(ch);
        for (char v : VOWELS) {
            if (v == upper) {
                return true;
            }
        }
        return false;
    }

    private char pickWithMaxLimit(char[] pool, Map<Character, Integer> counts, int maxOccurrences) {
        for (int attempt = 0; attempt < 25; attempt++) {
            char candidate = pool[random.nextInt(pool.length)];
            int current = counts.getOrDefault(candidate, 0);
            // Rare consonants (J, Q, X, Z, K, V) should appear at most once per board
            int limit = isRareConsonant(candidate) ? 1 : maxOccurrences;
            if (current < limit) {
                counts.put(candidate, current + 1);
                return candidate;
            }
        }
        char fallback = pool[random.nextInt(pool.length)];
        counts.put(fallback, counts.getOrDefault(fallback, 0) + 1);
        return fallback;
    }

    private boolean isRareConsonant(char c) {
        return c == 'J' || c == 'Q' || c == 'X' || c == 'Z' || c == 'K' || c == 'V';
    }
}
