package edu.ieu.se360.wordhunt.reflection;

import edu.ieu.se360.wordhunt.model.PlayerScore;

/**
 * Hint power-up: Reveals the first and last letters of an unclaimed valid word (e.g., S _ _ _ _ A).
 */
@PowerUpInfo(
        code = "HINT",
        name = "Hint Power-Up",
        description = "Reveals the first and last letter of an unclaimed valid word on the board.",
        multiplier = 1
)
public class HintPowerUp implements PowerUp {

    public HintPowerUp() {
    }

    @Override
    public String activate(PlayerScore playerScore) {
        if (playerScore != null) {
            playerScore.setHintUsed(true);
        }
        return "HINT_ACTIVATED";
    }

    /**
     * Formats a target word into a masked hint showing only the first and last character,
     * e.g., "SINEMA" -> "S _ _ _ _ A".
     */
    public static String formatHint(String word) {
        if (word == null || word.isBlank()) {
            return "";
        }
        String trimmed = word.trim().toUpperCase(java.util.Locale.ENGLISH);
        if (trimmed.length() <= 2) {
            return trimmed;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(trimmed.charAt(0));
        for (int i = 1; i < trimmed.length() - 1; i++) {
            sb.append(" _");
        }
        sb.append(" ").append(trimmed.charAt(trimmed.length() - 1));
        return sb.toString();
    }
}
