package edu.ieu.se360.wordhunt.engine;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Manages the English word dictionary and letter difficulty scores loaded from dictionary_en.json.
 * Uses {@link HashSet} for O(1) word lookup and {@link HashMap} for letter scores.
 */
public class DictionaryManager {
    private static final String DEFAULT_DICTIONARY_RESOURCE = "/dictionary_en.json";
    private static volatile DictionaryManager instance;

    private final HashSet<String> words = new HashSet<>();
    private final HashMap<Character, Integer> letterScores = new HashMap<>();

    public DictionaryManager() {
        this(DEFAULT_DICTIONARY_RESOURCE);
    }

    public DictionaryManager(String resourcePath) {
        initializeDefaultLetterScores();
        loadDictionaryFromResource(resourcePath);
    }

    public static DictionaryManager getInstance() {
        if (instance == null) {
            synchronized (DictionaryManager.class) {
                if (instance == null) {
                    instance = new DictionaryManager(DEFAULT_DICTIONARY_RESOURCE);
                }
            }
        }
        return instance;
    }

    private void initializeDefaultLetterScores() {
        // A=1, E=1, I=1, O=1, U=1, N=1, R=1, T=1, L=1, S=1
        char[] onePoint = {'A', 'E', 'I', 'O', 'U', 'N', 'R', 'T', 'L', 'S'};
        for (char c : onePoint) {
            letterScores.put(c, 1);
        }
        // D=2, G=2
        letterScores.put('D', 2);
        letterScores.put('G', 2);
        // B=3, C=3, M=3, P=3
        char[] threePoints = {'B', 'C', 'M', 'P'};
        for (char c : threePoints) {
            letterScores.put(c, 3);
        }
        // F=4, H=4, V=4, W=4, Y=4
        char[] fourPoints = {'F', 'H', 'V', 'W', 'Y'};
        for (char c : fourPoints) {
            letterScores.put(c, 4);
        }
        // K=5
        letterScores.put('K', 5);
        // J=8, X=8
        letterScores.put('J', 8);
        letterScores.put('X', 8);
        // Q=10, Z=10
        letterScores.put('Q', 10);
        letterScores.put('Z', 10);
    }

    /**
     * Loads letterScores and words from the specified JSON resource on the classpath.
     */
    public final synchronized void loadDictionaryFromResource(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return;
        }
        String normalizedPath = resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath;
        Gson gson = new Gson();
        try (InputStream is = DictionaryManager.class.getResourceAsStream(normalizedPath)) {
            if (is == null) {
                throw new IllegalStateException("Dictionary resource not found: " + normalizedPath);
            }
            try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                JsonObject root = gson.fromJson(reader, JsonObject.class);
                if (root == null) {
                    return;
                }

                if (root.has("letterScores") && root.get("letterScores").isJsonObject()) {
                    JsonObject scoresObj = root.getAsJsonObject("letterScores");
                    for (Map.Entry<String, JsonElement> entry : scoresObj.entrySet()) {
                        String key = entry.getKey().trim().toUpperCase(Locale.ENGLISH);
                        if (!key.isEmpty()) {
                            letterScores.put(key.charAt(0), entry.getValue().getAsInt());
                        }
                    }
                }

                if (root.has("words") && root.get("words").isJsonArray()) {
                    words.clear();
                    JsonArray wordsArray = root.getAsJsonArray("words");
                    for (JsonElement element : wordsArray) {
                        if (element != null && !element.isJsonNull()) {
                            String word = element.getAsString().trim().toUpperCase(Locale.ENGLISH);
                            if (word.length() >= 3) {
                                words.add(word);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load dictionary from " + normalizedPath + ": " + e.getMessage(), e);
        }
    }

    /**
     * Checks in O(1) time whether the given word exists in the dictionary HashSet.
     */
    public boolean isValidWord(String word) {
        if (word == null || word.isBlank()) {
            return false;
        }
        return words.contains(word.trim().toUpperCase(Locale.ENGLISH));
    }

    /**
     * Returns the base point value for a single character from the HashMap.
     */
    public int getLetterScore(char letter) {
        char upper = Character.toUpperCase(letter);
        return letterScores.getOrDefault(upper, 1);
    }

    /**
     * Checks whether the candidate word can be formed using the available letters and their frequencies
     * on the board (each board tile can be used at most once per word).
     *
     * @param word         the word to check
     * @param boardLetters the 12 letters on the board
     * @return true if the word can be formed from boardLetters
     */
    public boolean canFormWord(String word, List<Character> boardLetters) {
        if (word == null || boardLetters == null) {
            return false;
        }
        String normalized = word.trim().toUpperCase(Locale.ENGLISH);
        if (normalized.isEmpty() || normalized.length() > boardLetters.size()) {
            return false;
        }

        int[] availableCounts = buildFrequencyArray(boardLetters);
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (ch < 'A' || ch > 'Z') {
                return false;
            }
            int idx = ch - 'A';
            if (availableCounts[idx] <= 0) {
                return false;
            }
            availableCounts[idx]--;
        }
        return true;
    }

    /**
     * Finds all valid dictionary words that can be formed using the given board letters.
     *
     * @param boardLetters the list of characters on the board
     * @return list of all valid words that can be formed
     */
    public List<String> findAllPossibleWords(List<Character> boardLetters) {
        if (boardLetters == null || boardLetters.isEmpty()) {
            return Collections.emptyList();
        }
        int[] boardFreq = buildFrequencyArray(boardLetters);
        int maxPossibleLength = boardLetters.size();
        List<String> possibleWords = new ArrayList<>();

        for (String dictWord : words) {
            if (dictWord.length() <= maxPossibleLength && canFormWithFrequency(dictWord, boardFreq)) {
                possibleWords.add(dictWord);
            }
        }
        Collections.sort(possibleWords);
        return possibleWords;
    }

    private int[] buildFrequencyArray(List<Character> letters) {
        int[] freq = new int[26];
        for (Character ch : letters) {
            if (ch != null) {
                char upper = Character.toUpperCase(ch);
                if (upper >= 'A' && upper <= 'Z') {
                    freq[upper - 'A']++;
                }
            }
        }
        return freq;
    }

    private boolean canFormWithFrequency(String word, int[] boardFreq) {
        int[] used = new int[26];
        for (int i = 0; i < word.length(); i++) {
            char ch = word.charAt(i);
            if (ch < 'A' || ch > 'Z') {
                return false;
            }
            int idx = ch - 'A';
            used[idx]++;
            if (used[idx] > boardFreq[idx]) {
                return false;
            }
        }
        return true;
    }

    public Set<String> getWords() {
        return Collections.unmodifiableSet(words);
    }

    public HashMap<Character, Integer> getLetterScores() {
        return new HashMap<>(letterScores);
    }

    public int getDictionarySize() {
        return words.size();
    }

    public void addWord(String word) {
        if (word != null && !word.isBlank()) {
            words.add(word.trim().toUpperCase(Locale.ENGLISH));
        }
    }
}
