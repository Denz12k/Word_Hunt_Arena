package edu.ieu.se360.wordhunt.reflection;

import edu.ieu.se360.wordhunt.model.PlayerScore;

/**
 * Double Score power-up: Doubles the points of the next valid word found by the player.
 */
@PowerUpInfo(
        code = "DOUBLE_SCORE",
        name = "2x Score Multiplier",
        description = "Doubles the total points earned for the next valid word found.",
        multiplier = 2
)
public class DoubleScorePowerUp implements PowerUp {

    public DoubleScorePowerUp() {
    }

    @Override
    public String activate(PlayerScore playerScore) {
        if (playerScore != null) {
            playerScore.setDoubleScoreActive(true);
            playerScore.setDoubleScoreUsed(true);
        }
        return "DOUBLE_SCORE_ACTIVATED";
    }

    @Override
    public int applyScoreEffect(int currentScore) {
        PowerUpInfo info = this.getClass().getAnnotation(PowerUpInfo.class);
        int mult = (info != null) ? info.multiplier() : 2;
        return currentScore * mult;
    }
}
