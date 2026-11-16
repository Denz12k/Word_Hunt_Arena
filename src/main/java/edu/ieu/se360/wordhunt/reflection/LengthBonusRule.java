package edu.ieu.se360.wordhunt.reflection;

/**
 * Awards extra points based on word length:
 * - 5-letter words receive +5 bonus points
 * - 6+ letter words receive +10 bonus points
 */
@ScoringRuleInfo(
        name = "Word Length Bonus Rule",
        priority = 1
)
public class LengthBonusRule implements ScoringRule {

    public static final int FIVE_LETTER_BONUS = 5;
    public static final int SIX_OR_MORE_LETTER_BONUS = 10;

    public LengthBonusRule() {
    }

    @Override
    public int applyBonus(String word, int baseScore) {
        if (word == null) {
            return baseScore;
        }
        int len = word.trim().length();
        if (len >= 6) {
            return baseScore + SIX_OR_MORE_LETTER_BONUS;
        } else if (len == 5) {
            return baseScore + FIVE_LETTER_BONUS;
        }
        return baseScore;
    }
}
