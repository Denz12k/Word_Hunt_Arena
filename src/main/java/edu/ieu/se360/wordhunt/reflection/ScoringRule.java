package edu.ieu.se360.wordhunt.reflection;

/**
 * Interface for scoring bonus rules evaluated dynamically via Reflection.
 */
public interface ScoringRule {

    /**
     * Applies a bonus to the given base score based on the submitted word.
     *
     * @param word      the valid word submitted by a player
     * @param baseScore the current score before this rule is applied
     * @return the updated score including any bonus from this rule
     */
    int applyBonus(String word, int baseScore);
}
