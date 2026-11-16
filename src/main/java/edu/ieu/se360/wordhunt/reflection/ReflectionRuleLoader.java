package edu.ieu.se360.wordhunt.reflection;

import edu.ieu.se360.wordhunt.model.PlayerScore;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dynamically loads and executes {@link PowerUp} and {@link ScoringRule} classes at runtime
 * using the Java Reflection API ({@code Class.forName}, {@code isAnnotationPresent},
 * {@code getAnnotation}, {@code getDeclaredConstructor().newInstance()}, and {@code Method.invoke}).
 */
public class ReflectionRuleLoader {
    private static final String PACKAGE_PREFIX = "edu.ieu.se360.wordhunt.reflection.";

    private static final String[] DEFAULT_POWERUP_CLASS_NAMES = {
            PACKAGE_PREFIX + "HintPowerUp",
            PACKAGE_PREFIX + "DoubleScorePowerUp",
            PACKAGE_PREFIX + "ShufflePowerUp"
    };

    private static final String[] DEFAULT_SCORING_RULE_CLASS_NAMES = {
            PACKAGE_PREFIX + "LengthBonusRule"
    };

    private final Map<String, PowerUp> powerUpsByCode = new LinkedHashMap<>();
    private final Map<String, PowerUpInfo> powerUpMetadata = new LinkedHashMap<>();
    private final List<ScoringRule> scoringRules = new ArrayList<>();
    private final List<ScoringRuleInfo> scoringRuleMetadata = new ArrayList<>();

    public ReflectionRuleLoader() {
        loadDefaultClasses();
    }

    /**
     * Dynamically loads all default PowerUp and ScoringRule classes via Class.forName().
     */
    public final void loadDefaultClasses() {
        powerUpsByCode.clear();
        powerUpMetadata.clear();
        scoringRules.clear();
        scoringRuleMetadata.clear();

        for (String className : DEFAULT_POWERUP_CLASS_NAMES) {
            loadPowerUpClass(className);
        }
        for (String className : DEFAULT_SCORING_RULE_CLASS_NAMES) {
            loadScoringRuleClass(className);
        }
    }

    /**
     * Dynamically loads a PowerUp class by its fully-qualified name using Reflection.
     */
    public PowerUp loadPowerUpClass(String fullyQualifiedClassName) {
        try {
            Class<?> clazz = Class.forName(fullyQualifiedClassName);
            if (clazz.isAnnotationPresent(PowerUpInfo.class) && PowerUp.class.isAssignableFrom(clazz)) {
                PowerUpInfo info = clazz.getAnnotation(PowerUpInfo.class);
                PowerUp instance = (PowerUp) clazz.getDeclaredConstructor().newInstance();
                String codeKey = info.code().toUpperCase(Locale.ENGLISH);
                powerUpsByCode.put(codeKey, instance);
                powerUpMetadata.put(codeKey, info);
                return instance;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load PowerUp class via Reflection: " + fullyQualifiedClassName, e);
        }
        return null;
    }

    /**
     * Dynamically loads a ScoringRule class by its fully-qualified name using Reflection.
     */
    public ScoringRule loadScoringRuleClass(String fullyQualifiedClassName) {
        try {
            Class<?> clazz = Class.forName(fullyQualifiedClassName);
            if (clazz.isAnnotationPresent(ScoringRuleInfo.class) && ScoringRule.class.isAssignableFrom(clazz)) {
                ScoringRuleInfo info = clazz.getAnnotation(ScoringRuleInfo.class);
                ScoringRule instance = (ScoringRule) clazz.getDeclaredConstructor().newInstance();
                scoringRules.add(instance);
                scoringRules.sort(Comparator.comparingInt(rule -> {
                    ScoringRuleInfo ruleInfo = rule.getClass().getAnnotation(ScoringRuleInfo.class);
                    return ruleInfo != null ? ruleInfo.priority() : Integer.MAX_VALUE;
                }));
                scoringRuleMetadata.clear();
                for (ScoringRule rule : scoringRules) {
                    scoringRuleMetadata.add(rule.getClass().getAnnotation(ScoringRuleInfo.class));
                }
                return instance;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load ScoringRule class via Reflection: " + fullyQualifiedClassName, e);
        }
        return null;
    }

    /**
     * Invokes {@code applyBonus(String, int)} on all loaded {@link ScoringRule} instances
     * using {@link Method#invoke(Object, Object...)}.
     *
     * @param word      the submitted word
     * @param baseScore the base letter score
     * @return the score after all scoring rules have been applied
     */
    public int applyAllScoringRules(String word, int baseScore) {
        int currentScore = baseScore;
        for (ScoringRule rule : scoringRules) {
            try {
                Method applyBonusMethod = rule.getClass().getMethod("applyBonus", String.class, int.class);
                Object result = applyBonusMethod.invoke(rule, word, currentScore);
                if (result instanceof Integer) {
                    currentScore = (Integer) result;
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to invoke applyBonus via Reflection on " + rule.getClass().getName(), e);
            }
        }
        return currentScore;
    }

    /**
     * Invokes {@code activate(PlayerScore)} on the specified PowerUp via Reflection {@link Method#invoke}.
     *
     * @param code        the power-up code (e.g., "HINT", "DOUBLE_SCORE", "SHUFFLE")
     * @param playerScore the player's score object
     * @return the activation result string, or null if power-up code is not found
     */
    public String activatePowerUp(String code, PlayerScore playerScore) {
        PowerUp powerUp = getPowerUp(code);
        if (powerUp == null) {
            return null;
        }
        try {
            Method activateMethod = powerUp.getClass().getMethod("activate", PlayerScore.class);
            Object result = activateMethod.invoke(powerUp, playerScore);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke activate via Reflection for power-up: " + code, e);
        }
    }

    /**
     * Applies the multiplier of a power-up (e.g., "DOUBLE_SCORE") to a score using Reflection.
     */
    public int applyPowerUpMultiplier(String code, int currentScore) {
        PowerUp powerUp = getPowerUp(code);
        if (powerUp == null) {
            return currentScore;
        }
        try {
            Method effectMethod = powerUp.getClass().getMethod("applyScoreEffect", int.class);
            Object result = effectMethod.invoke(powerUp, currentScore);
            if (result instanceof Integer) {
                return (Integer) result;
            }
        } catch (Exception e) {
            PowerUpInfo info = getPowerUpInfo(code);
            if (info != null && info.multiplier() > 1) {
                return currentScore * info.multiplier();
            }
        }
        return currentScore;
    }

    /**
     * Returns the multiplier defined on the {@link PowerUpInfo} annotation for the given power-up code.
     */
    public int getPowerUpMultiplier(String code) {
        PowerUpInfo info = getPowerUpInfo(code);
        return info != null ? info.multiplier() : 1;
    }

    public PowerUp getPowerUp(String code) {
        if (code == null) {
            return null;
        }
        return powerUpsByCode.get(code.trim().toUpperCase(Locale.ENGLISH));
    }

    public PowerUpInfo getPowerUpInfo(String code) {
        if (code == null) {
            return null;
        }
        return powerUpMetadata.get(code.trim().toUpperCase(Locale.ENGLISH));
    }

    public Map<String, PowerUp> getAllPowerUps() {
        return Collections.unmodifiableMap(powerUpsByCode);
    }

    public Map<String, PowerUpInfo> getAllPowerUpMetadata() {
        return Collections.unmodifiableMap(powerUpMetadata);
    }

    public List<ScoringRule> getScoringRules() {
        return Collections.unmodifiableList(scoringRules);
    }

    public List<ScoringRuleInfo> getScoringRuleMetadata() {
        return Collections.unmodifiableList(scoringRuleMetadata);
    }
}
