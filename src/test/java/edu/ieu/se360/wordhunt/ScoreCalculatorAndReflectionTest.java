package edu.ieu.se360.wordhunt;

import edu.ieu.se360.wordhunt.engine.DictionaryManager;
import edu.ieu.se360.wordhunt.engine.ScoreCalculator;
import edu.ieu.se360.wordhunt.model.PlayerScore;
import edu.ieu.se360.wordhunt.reflection.HintPowerUp;
import edu.ieu.se360.wordhunt.reflection.LengthBonusRule;
import edu.ieu.se360.wordhunt.reflection.PowerUpInfo;
import edu.ieu.se360.wordhunt.reflection.ReflectionRuleLoader;
import edu.ieu.se360.wordhunt.reflection.ScoringRuleInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 Unit Tests for {@link ScoreCalculator}, {@link LengthBonusRule},
 * and runtime annotation/reflection loading via {@link ReflectionRuleLoader}.
 */
@DisplayName("ScoreCalculator & Reflection API Unit Tests")
class ScoreCalculatorAndReflectionTest {

    private DictionaryManager dictionaryManager;
    private ReflectionRuleLoader ruleLoader;
    private ScoreCalculator scoreCalculator;

    @BeforeEach
    void setUp() {
        dictionaryManager = DictionaryManager.getInstance();
        ruleLoader = new ReflectionRuleLoader();
        scoreCalculator = new ScoreCalculator(dictionaryManager, ruleLoader);
    }

    @Test
    @DisplayName("Letter difficulty scores and LengthBonusRule (5 letters +5p, 6+ letters +10p) are calculated accurately")
    void testLetterScoresAndLengthBonusRule() {
        // Verify individual letter difficulty points
        assertEquals(1, dictionaryManager.getLetterScore('A'));
        assertEquals(2, dictionaryManager.getLetterScore('D'));
        assertEquals(3, dictionaryManager.getLetterScore('C'));
        assertEquals(4, dictionaryManager.getLetterScore('F'));
        assertEquals(5, dictionaryManager.getLetterScore('K'));
        assertEquals(8, dictionaryManager.getLetterScore('J'));
        assertEquals(10, dictionaryManager.getLetterScore('Q'));
        assertEquals(10, dictionaryManager.getLetterScore('Z'));

        // 1. 3-letter word: "CAT" -> C(3) + A(1) + T(1) = 5 pts (no length bonus)
        assertEquals(5, scoreCalculator.calculateBaseLetterScore("CAT"));
        assertEquals(5, scoreCalculator.calculateWordScore("CAT", false));

        // 2. 4-letter word: "CARD" -> C(3) + A(1) + R(1) + D(2) = 7 pts (no length bonus)
        assertEquals(7, scoreCalculator.calculateBaseLetterScore("CARD"));
        assertEquals(7, scoreCalculator.calculateWordScore("CARD", false));

        // 3. 5-letter word: "STONE" -> S(1)+T(1)+O(1)+N(1)+E(1) = 5 base + 5 length bonus = 10 pts
        assertEquals(5, scoreCalculator.calculateBaseLetterScore("STONE"));
        assertEquals(10, scoreCalculator.calculateWordScore("STONE", false));

        // 4. 6-letter word: "STREAM" -> S(1)+T(1)+R(1)+E(1)+A(1)+M(3) = 8 base + 10 length bonus = 18 pts
        assertEquals(8, scoreCalculator.calculateBaseLetterScore("STREAM"));
        assertEquals(18, scoreCalculator.calculateWordScore("STREAM", false));
    }

    @Test
    @DisplayName("ReflectionRuleLoader dynamically reads @PowerUpInfo and @ScoringRuleInfo annotations at runtime")
    void testReflectionRuleLoaderReadsAnnotationsAtRuntime() {
        Map<String, PowerUpInfo> powerUpMeta = ruleLoader.getAllPowerUpMetadata();
        assertEquals(3, powerUpMeta.size(), "All 3 default PowerUp classes should be loaded via Reflection");

        assertTrue(powerUpMeta.containsKey("HINT"));
        assertTrue(powerUpMeta.containsKey("DOUBLE_SCORE"));
        assertTrue(powerUpMeta.containsKey("SHUFFLE"));

        PowerUpInfo doubleScoreInfo = ruleLoader.getPowerUpInfo("DOUBLE_SCORE");
        assertNotNull(doubleScoreInfo);
        assertEquals("DOUBLE_SCORE", doubleScoreInfo.code());
        assertEquals("2x Score Multiplier", doubleScoreInfo.name());
        assertEquals(2, doubleScoreInfo.multiplier());

        List<ScoringRuleInfo> scoringRuleMeta = ruleLoader.getScoringRuleMetadata();
        assertFalse(scoringRuleMeta.isEmpty(), "ScoringRule classes annotated with @ScoringRuleInfo must be loaded");
        assertEquals("Word Length Bonus Rule", scoringRuleMeta.get(0).name());
        assertEquals(1, scoringRuleMeta.get(0).priority());
    }

    @Test
    @DisplayName("2x Score PowerUp doubles the total score (base + length bonus) via Reflection Method.invoke")
    void testDoubleScorePowerUpDoublesTotalScore() {
        // "STREAM" without 2x = 8 base + 10 bonus = 18 pts
        // "STREAM" with 2x = 18 * 2 = 36 pts
        int normalScore = scoreCalculator.calculateWordScore("STREAM", false);
        int doubledScore = scoreCalculator.calculateWordScore("STREAM", true);

        assertEquals(18, normalScore);
        assertEquals(36, doubledScore, "2x multiplier must double the total score including length bonus");

        // Test activating power-ups on PlayerScore via Reflection
        PlayerScore player = new PlayerScore("Deniz");
        assertFalse(player.isDoubleScoreActive());
        String result = ruleLoader.activatePowerUp("DOUBLE_SCORE", player);
        assertEquals("DOUBLE_SCORE_ACTIVATED", result);
        assertTrue(player.isDoubleScoreActive());
        assertTrue(player.isDoubleScoreUsed());

        // Test Hint formatting
        assertEquals("S _ _ _ _ M", HintPowerUp.formatHint("STREAM"));
    }
}
