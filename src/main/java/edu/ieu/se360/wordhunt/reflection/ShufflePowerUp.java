package edu.ieu.se360.wordhunt.reflection;

import edu.ieu.se360.wordhunt.model.PlayerScore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shuffle power-up: Rearranges the 12 board letters so the player can spot new words.
 */
@PowerUpInfo(
        code = "SHUFFLE",
        name = "Shuffle Letters",
        description = "Shuffles the 12 letters on the board to help spot new word combinations.",
        multiplier = 1
)
public class ShufflePowerUp implements PowerUp {

    public ShufflePowerUp() {
    }

    @Override
    public String activate(PlayerScore playerScore) {
        if (playerScore != null) {
            playerScore.setShuffleUsed(true);
        }
        return "SHUFFLE_ACTIVATED";
    }

    /**
     * Returns a new shuffled copy of the given board letters using Collections.shuffle().
     */
    public List<Character> shuffleLetters(List<Character> boardLetters) {
        if (boardLetters == null) {
            return Collections.emptyList();
        }
        List<Character> copy = new ArrayList<>(boardLetters);
        Collections.shuffle(copy);
        return copy;
    }
}
