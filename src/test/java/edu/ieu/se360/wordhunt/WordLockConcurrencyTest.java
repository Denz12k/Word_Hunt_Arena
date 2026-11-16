package edu.ieu.se360.wordhunt;

import edu.ieu.se360.wordhunt.engine.DictionaryManager;
import edu.ieu.se360.wordhunt.engine.GameSession;
import edu.ieu.se360.wordhunt.engine.ScoreCalculator;
import edu.ieu.se360.wordhunt.engine.WordSubmissionResult;
import edu.ieu.se360.wordhunt.model.PlayerScore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 Unit & Multithreaded Concurrency Tests for {@link GameSession}.
 * Proves that synchronized word locking prevents race conditions when 10 threads
 * submit the exact same word at the exact same millisecond.
 */
@DisplayName("GameSession Word Locking & Concurrency Race Condition Tests")
class WordLockConcurrencyTest {

    private GameSession gameSession;
    private List<Character> fixedBoard;

    @BeforeEach
    void setUp() {
        // Fixed 12-letter board containing S, T, O, N, E, A, R, M, I, L, C, P
        fixedBoard = Arrays.asList('S', 'T', 'O', 'N', 'E', 'A', 'R', 'M', 'I', 'L', 'C', 'P');
        gameSession = new GameSession(fixedBoard, DictionaryManager.getInstance(), new ScoreCalculator());
    }

    @Test
    @DisplayName("submitWord rejects invalid letters, accepts first valid submission, and returns ALREADY_CLAIMED on second")
    void testSequentialWordSubmissionValidationAndLocking() {
        gameSession.addPlayer("Alice");
        gameSession.addPlayer("Bob");

        // 1. Too short (< 3 letters)
        WordSubmissionResult shortRes = gameSession.submitWord("Alice", "AT");
        assertEquals(WordSubmissionResult.Status.TOO_SHORT, shortRes.getStatus());
        assertEquals(0, shortRes.getPointsAwarded());

        // 2. Cannot be formed from the 12 board letters
        WordSubmissionResult invalidLettersRes = gameSession.submitWord("Alice", "JACKET");
        assertEquals(WordSubmissionResult.Status.INVALID_LETTERS, invalidLettersRes.getStatus());
        assertEquals(0, invalidLettersRes.getPointsAwarded());

        // 3. Formed from board letters, but not a valid English word in dictionary
        WordSubmissionResult notInDictRes = gameSession.submitWord("Alice", "STONP");
        assertEquals(WordSubmissionResult.Status.NOT_IN_DICTIONARY, notInDictRes.getStatus());
        assertEquals(0, notInDictRes.getPointsAwarded());

        // 4. First valid submission of "STONE" by Alice -> SUCCESS
        WordSubmissionResult firstClaim = gameSession.submitWord("Alice", "STONE");
        assertEquals(WordSubmissionResult.Status.SUCCESS, firstClaim.getStatus());
        assertTrue(firstClaim.getPointsAwarded() > 0);
        assertEquals("Alice", firstClaim.getClaimedBy());

        // 5. Second submission of "STONE" by Bob -> ALREADY_CLAIMED
        WordSubmissionResult secondClaim = gameSession.submitWord("Bob", "STONE");
        assertEquals(WordSubmissionResult.Status.ALREADY_CLAIMED, secondClaim.getStatus());
        assertEquals(0, secondClaim.getPointsAwarded());
        assertEquals("Alice", secondClaim.getClaimedBy());
    }

    @Test
    @DisplayName("Multithreaded Race Condition Test: 10 threads submitting 'STONE' at the exact same millisecond -> only 1 wins!")
    void testTenConcurrentThreadsSubmittingSameWordAtSameMillisecond() throws Exception {
        final int threadCount = 10;
        final String contestedWord = "STONE";

        for (int i = 1; i <= threadCount; i++) {
            gameSession.addPlayer("Player" + i);
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startGun = new CountDownLatch(1);

        List<Callable<WordSubmissionResult>> tasks = new ArrayList<>(threadCount);
        for (int i = 1; i <= threadCount; i++) {
            final String playerName = "Player" + i;
            tasks.add(() -> {
                readyLatch.countDown();
                // All 10 threads block here until the startGun latch is released simultaneously
                startGun.await();
                return gameSession.submitWord(playerName, contestedWord);
            });
        }

        List<Future<WordSubmissionResult>> futures = new ArrayList<>(threadCount);
        for (Callable<WordSubmissionResult> task : tasks) {
            futures.add(executor.submit(task));
        }

        // Wait until all 10 threads are ready at the barrier, then release them at the exact same millisecond
        assertTrue(readyLatch.await(5, TimeUnit.SECONDS), "All 10 threads should be ready at the latch");
        startGun.countDown();

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger alreadyClaimedCount = new AtomicInteger(0);
        String winningPlayer = null;

        for (Future<WordSubmissionResult> future : futures) {
            WordSubmissionResult result = future.get(5, TimeUnit.SECONDS);
            if (result.getStatus() == WordSubmissionResult.Status.SUCCESS) {
                successCount.incrementAndGet();
                winningPlayer = result.getUsername();
                assertTrue(result.getPointsAwarded() > 0, "Winner must receive positive points");
            } else if (result.getStatus() == WordSubmissionResult.Status.ALREADY_CLAIMED) {
                alreadyClaimedCount.incrementAndGet();
                assertEquals(0, result.getPointsAwarded(), "Rejected threads must receive 0 points");
            } else {
                fail("Unexpected status during race condition test: " + result.getStatus());
            }
        }

        executor.shutdownNow();

        // Prove that synchronized lock allowed ONLY 1 player to claim the word and rejected the other 9
        assertEquals(1, successCount.get(), "Exactly 1 thread must succeed in claiming the word");
        assertEquals(9, alreadyClaimedCount.get(), "Exactly 9 threads must receive ALREADY_CLAIMED");
        assertNotNull(winningPlayer, "A winning player must be recorded");

        // Verify leaderboard and player scores consistency
        List<PlayerScore> leaderboard = gameSession.getSortedLeaderboard();
        assertEquals(winningPlayer, leaderboard.get(0).getUsername(), "Top player on leaderboard must be the lock winner");
        assertTrue(leaderboard.get(0).getScore() > 0);

        for (int i = 1; i < leaderboard.size(); i++) {
            assertEquals(0, leaderboard.get(i).getScore(), "All other 9 players must have 0 score");
        }
    }
}
