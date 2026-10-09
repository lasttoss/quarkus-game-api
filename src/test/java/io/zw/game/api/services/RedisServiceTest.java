package io.zw.game.api.services;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cache under the config service, against a real Redis rather than a mocked client.
 *
 * A mock would say that this class calls getBucket and set, which is what its four methods are - the
 * interesting part is what Redis does with what it is handed, and that only exists in a server. So this
 * test starts nothing itself, but it needs one: REDIS_TEST_URL, defaulting to the address the repository's
 * own compose file publishes redis on, and it skips with a visible reason when there is no server there.
 * The database is 9 so that a stray key from a run cannot be read by an application pointed at the
 * default, and the keys are prefixed and removed after each test.
 */
class RedisServiceTest {

    private static final String TEST_DATABASE = "9";
    private static final String KEY_PREFIX = "test:redis-service:";

    static RedissonClient client;
    static RedisService redisService;

    @BeforeAll
    static void connect() {
        String address = System.getenv().getOrDefault("REDIS_TEST_URL", "redis://localhost:56379");
        Config config = new Config();
        config.useSingleServer().setAddress(address).setDatabase(Integer.parseInt(TEST_DATABASE))
                .setConnectionMinimumIdleSize(1).setConnectionPoolSize(4);
        try {
            client = Redisson.create(config);
            client.getKeys().count();
        } catch (RuntimeException e) {
            client = null;
        }
        if (client != null) {
            redisService = new RedisService();
            redisService.redissonClient = client;
        }
    }

    @AfterAll
    static void disconnect() {
        if (client != null) client.shutdown();
    }

    /**
     * The assumption lives here rather than in the connection, so that a run with no server reports five
     * skipped tests rather than a class that ran nothing: a suite that quietly does not run is a suite
     * nobody notices is not running.
     */
    @BeforeEach
    void requireRedisAndClear() {
        Assumptions.assumeTrue(client != null,
                "no redis at " + System.getenv().getOrDefault("REDIS_TEST_URL", "redis://localhost:56379")
                        + " - start one with: docker run -d --rm -p 56379:6379 redis:7-alpine");
        client.getKeys().deleteByPattern(KEY_PREFIX + "*");
    }

    String key(String name) {
        return KEY_PREFIX + name;
    }

    @Test
    void whatIsSavedIsThereAndTheKeySaysSo() {
        redisService.saveWithoutExpiredTime(key("plain"), "a value");

        assertEquals("a value", redisService.get(key("plain")));
        assertTrue(redisService.checkIfKeyExists(key("plain")));
    }

    @Test
    void savingTheSameKeyAgainReplacesIt() {
        redisService.saveWithoutExpiredTime(key("twice"), 1);
        redisService.saveWithoutExpiredTime(key("twice"), 2);

        assertEquals(2, redisService.get(key("twice")));
    }

    @Test
    void aKeyThatWasNeverSavedIsNullAndSaysSo() {
        assertNull(redisService.get(key("missing")));
        assertFalse(redisService.checkIfKeyExists(key("missing")));
    }

    /** The half a mocked client cannot answer: whether the duration reaches Redis and is honoured. */
    @Test
    void aValueSavedWithATimeLimitIsGoneAfterIt() throws InterruptedException {
        redisService.saveWithExpiredTime(key("expiring"), "gone soon", Duration.ofSeconds(1));

        assertEquals("gone soon", redisService.get(key("expiring")), "it should be there before the time is up");

        Thread.sleep(1_300);

        assertNull(redisService.get(key("expiring")), "and gone after it");
        assertFalse(redisService.checkIfKeyExists(key("expiring")));
    }

    /**
     * The shape the config service actually uses: a whole config object stored under one key and read
     * back later. A map stands in for the config, because a value whose codec cannot round-trip would
     * fail here for a reason that has nothing to do with this class.
     */
    @Test
    void aWholeConfigRoundTripsThroughTheCache() {
        Map<String, Object> config = Map.of("currentSeason", 7, "previousSeason", 6);

        redisService.saveWithoutExpiredTime(key("config"), config);

        assertEquals(config, redisService.get(key("config")));
    }
}
