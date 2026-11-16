package edu.ieu.se360.wordhunt.reflection;

import edu.ieu.se360.wordhunt.model.PlayerScore;

/**
 * Interface representing a one-time power-up (Joker) in Word Hunt Arena.
 * Concrete implementations are annotated with {@link PowerUpInfo} and loaded via Reflection.
 */
public interface PowerUp {

    /**
     * Activates the power-up on the given player's score state and returns a status/effect message.
     *
     * @param playerScore the player's current match score state
     * @return a description of the power-up activation result
     */
    String activate(PlayerScore playerScore);

    /**
     * Applies this power-up's score multiplier to a calculated word score, if applicable.
     *
     * @param currentScore the score before power-up multiplier
     * @return the modified score
     */
    default int applyScoreEffect(int currentScore) {
        PowerUpInfo info = this.getClass().getAnnotation(PowerUpInfo.class);
        if (info != null && info.multiplier() > 1) {
            return currentScore * info.multiplier();
        }
        return currentScore;
    }

    default String getCode() {
        PowerUpInfo info = this.getClass().getAnnotation(PowerUpInfo.class);
        return info != null ? info.code() : this.getClass().getSimpleName();
    }

    default String getName() {
        PowerUpInfo info = this.getClass().getAnnotation(PowerUpInfo.class);
        return info != null ? info.name() : this.getClass().getSimpleName();
    }

    default String getDescription() {
        PowerUpInfo info = this.getClass().getAnnotation(PowerUpInfo.class);
        return info != null ? info.description() : "";
    }

    default int getMultiplier() {
        PowerUpInfo info = this.getClass().getAnnotation(PowerUpInfo.class);
        return info != null ? info.multiplier() : 1;
    }
}
