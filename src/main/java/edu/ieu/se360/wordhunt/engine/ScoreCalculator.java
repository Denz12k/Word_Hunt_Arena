package edu.ieu.se360.wordhunt.engine;

import edu.ieu.se360.wordhunt.reflection.ReflectionRuleLoader;

import java.util.Locale;

/**
 * Calculates word scores by summing individual letter difficulty points from {@link DictionaryManager},
 * applying length bonuses via {@link ReflectionRuleLoader}, and doubling the total score if the
 * 2x Score power-up is active.
 */
public class ScoreCalculator {
    private final DictionaryManager dictionaryManager;
    private final ReflectionRuleLoader ruleLoader;

    public ScoreCalculator() {
        this(DictionaryManager.getInstance(), new ReflectionRuleLoader());
    }

    public ScoreCalculator(DictionaryManager dictionaryManager, ReflectionRuleLoader ruleLoader) {
        this.dictionaryManager = dictionaryManager != null ? dictionaryManager : DictionaryManager.getInstance();
        this.ruleLoader = ruleLoader != null ? ruleLoader : new ReflectionRuleLoader();
    }

    /**
     * Computes only the sum of individual letter scores for a word (without length bonus or 2x multiplier).
     */
    public int calculateBaseLetterScore(String word) {
        if (word == null || word.isBlank()) {
            return 0;
        }
        String normalized = word.trim().toUpperCase(Locale.ENGLISH);
        int baseScore = 0;
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (ch >= 'A' && ch <= 'Z') {
                baseScore += dictionaryManager.getLetterScore(ch);
            }
        }
        return baseScore;
    }

    /**
     * Computes the word score including letter difficulty points and reflection-loaded bonus rules
     * (e.g., {@code LengthBonusRule}), without the 2x power-up multiplier.
     */
    public int calculateWordScore(String word) {
        return calculateWordScore(word, false);
    }

    /**
     * Computes the total score for a word:
     * 1. Sums letter difficulty points from the HashMap in DictionaryManager.
     * 2. Applies all @ScoringRuleInfo bonus rules (e.g., +5 for 5 letters, +10 for 6+ letters) via ReflectionRuleLoader.
     * 3. If doubleScoreActive is true, applies the 2x multiplier from @PowerUpInfo (DoubleScorePowerUp).
     *
     * @param word              the submitted word
     * @param doubleScoreActive whether the player has an active 2x score power-up
     * @return the final calculated score
     */
    public int calculateWordScore(String word, boolean doubleScoreActive) {
        if (word == null || word.isBlank()) {
            return 0;
        }
        String normalized = word.trim().toUpperCase(Locale.ENGLISH);
        int baseScore = calculateBaseLetterScore(normalized);
        int scoreWithBonuses = ruleLoader.applyAllScoringRules(normalized, baseScore);

        if (doubleScoreActive) {
            return ruleLoader.applyPowerUpMultiplier("DOUBLE_SCORE", scoreWithBonuses);
        }
        return scoreWithBonuses;
    }

    public DictionaryManager getDictionaryManager() {
        return dictionaryManager;
    }

    public ReflectionRuleLoader getRuleLoader() {
        return ruleLoader;
    }
}
