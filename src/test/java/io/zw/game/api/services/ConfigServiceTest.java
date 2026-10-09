package io.zw.game.api.services;

import io.zw.game.api.constants.GameEnum;
import io.zw.game.api.dto.config.OpenFruitRateConfig;
import io.zw.game.api.dto.config.PickingFruitCountdownConfig;
import io.zw.game.api.dto.config.SeasonConfig;
import io.zw.game.api.dto.config.SeedConfig;
import io.zw.game.api.models.ConfigModel;
import io.zw.game.api.repositories.ConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Config is read through Redis with the database behind it, and the four getters are the same shape
 * four times: answer from the cache, or load from the database and fill the cache, or answer null when
 * neither has anything. Each of those three outcomes gets a test per config, because a cache that is
 * never filled and a cache that is trusted blindly look identical from the outside until you check
 * which of the two was called.
 */
@ExtendWith(MockitoExtension.class)
class ConfigServiceTest {

    @Mock
    ConfigRepository configRepository;

    @Mock
    RedisService redisService;

    @InjectMocks
    ConfigService configService;

    static ConfigModel row(String key, String data) {
        ConfigModel model = new ConfigModel();
        model.setKey(key);
        model.setData(data);
        return model;
    }

    /** The cached answer is used as it is, and the database is left alone. */
    @Test
    void seedConfigComesFromTheCacheWhenItIsThere() {
        SeedConfig cached = new SeedConfig();
        when(redisService.checkIfKeyExists(GameEnum.RedisKey.CONFIG_SEED_CONFIG.getKey())).thenReturn(true);
        when(redisService.get(GameEnum.RedisKey.CONFIG_SEED_CONFIG.getKey())).thenReturn(cached);

        assertSame(cached, configService.getListSeedConfig());
        verify(configRepository, never()).findByKey(any());
    }

    /** On a miss the row is parsed and written back, so the next call is a hit. */
    @Test
    void seedConfigIsLoadedFromTheDatabaseAndCachedOnAMiss() {
        when(redisService.checkIfKeyExists(GameEnum.RedisKey.CONFIG_SEED_CONFIG.getKey())).thenReturn(false);
        when(configRepository.findByKey(GameEnum.ConfigKey.SEED_CONFIG.getKey()))
                .thenReturn(row("seed", "{\"data\":[{\"plantId\":1,\"requiredExp\":[10,20,30]}]}"));

        SeedConfig loaded = configService.getListSeedConfig();

        assertEquals(1, loaded.getData().size());
        assertEquals(1, loaded.getData().get(0).getPlantId());
        assertEquals(List.of(10, 20, 30), loaded.getData().get(0).getRequiredExp());
        verify(redisService).saveWithoutExpiredTime(GameEnum.RedisKey.CONFIG_SEED_CONFIG.getKey(), loaded);
    }

    /** Nothing anywhere is null, not an exception: the caller decides what a missing config means. */
    @Test
    void seedConfigIsNullWhenNeitherTheCacheNorTheDatabaseHasIt() {
        when(redisService.checkIfKeyExists(GameEnum.RedisKey.CONFIG_SEED_CONFIG.getKey())).thenReturn(false);
        when(configRepository.findByKey(GameEnum.ConfigKey.SEED_CONFIG.getKey())).thenReturn(null);

        assertNull(configService.getListSeedConfig());
        verify(redisService, never()).saveWithoutExpiredTime(any(), any());
    }

    @Test
    void pickingFruitCountdownConfigComesFromTheCacheWhenItIsThere() {
        PickingFruitCountdownConfig cached = new PickingFruitCountdownConfig();
        when(redisService.checkIfKeyExists(GameEnum.RedisKey.CONFIG_PICKING_FRUIT_COUNTDOWN_CONFIG.getKey())).thenReturn(true);
        when(redisService.get(GameEnum.RedisKey.CONFIG_PICKING_FRUIT_COUNTDOWN_CONFIG.getKey())).thenReturn(cached);

        assertSame(cached, configService.getPickingFruitCountdownConfig());
        verify(configRepository, never()).findByKey(any());
    }

    @Test
    void pickingFruitCountdownConfigIsLoadedFromTheDatabaseAndCachedOnAMiss() {
        when(redisService.checkIfKeyExists(GameEnum.RedisKey.CONFIG_PICKING_FRUIT_COUNTDOWN_CONFIG.getKey())).thenReturn(false);
        when(configRepository.findByKey(GameEnum.ConfigKey.PICKING_FRUIT_COUNTDOWN_CONFIG.getKey()))
                .thenReturn(row("countdown", "{\"data\":{\"fruitTimeCountdown\":[60,120]}}"));

        PickingFruitCountdownConfig loaded = configService.getPickingFruitCountdownConfig();

        assertEquals(List.of(60, 120), loaded.getData().getFruitTimeCountdown());
        verify(redisService).saveWithoutExpiredTime(GameEnum.RedisKey.CONFIG_PICKING_FRUIT_COUNTDOWN_CONFIG.getKey(), loaded);
    }

    @Test
    void pickingFruitCountdownConfigIsNullWhenNeitherTheCacheNorTheDatabaseHasIt() {
        when(redisService.checkIfKeyExists(GameEnum.RedisKey.CONFIG_PICKING_FRUIT_COUNTDOWN_CONFIG.getKey())).thenReturn(false);
        when(configRepository.findByKey(GameEnum.ConfigKey.PICKING_FRUIT_COUNTDOWN_CONFIG.getKey())).thenReturn(null);

        assertNull(configService.getPickingFruitCountdownConfig());
        verify(redisService, never()).saveWithoutExpiredTime(any(), any());
    }

    @Test
    void openFruitRateConfigComesFromTheCacheWhenItIsThere() {
        OpenFruitRateConfig cached = new OpenFruitRateConfig();
        when(redisService.checkIfKeyExists(GameEnum.RedisKey.CONFIG_OPEN_FRUIT_RATE_CONFIG.getKey())).thenReturn(true);
        when(redisService.get(GameEnum.RedisKey.CONFIG_OPEN_FRUIT_RATE_CONFIG.getKey())).thenReturn(cached);

        assertSame(cached, configService.getOpenFruitRateConfig());
        verify(configRepository, never()).findByKey(any());
    }

    @Test
    void openFruitRateConfigIsLoadedFromTheDatabaseAndCachedOnAMiss() {
        when(redisService.checkIfKeyExists(GameEnum.RedisKey.CONFIG_OPEN_FRUIT_RATE_CONFIG.getKey())).thenReturn(false);
        when(configRepository.findByKey(GameEnum.ConfigKey.OPEN_FRUIT_RATE_CONFIG.getKey()))
                .thenReturn(row("rate", "{\"data\":{\"rates\":[[1,2],[3]]}}"));

        OpenFruitRateConfig loaded = configService.getOpenFruitRateConfig();

        assertEquals(2, loaded.getData().getRates().size());
        assertEquals(List.of(3), loaded.getData().getRates().get(1));
        verify(redisService).saveWithoutExpiredTime(GameEnum.RedisKey.CONFIG_OPEN_FRUIT_RATE_CONFIG.getKey(), loaded);
    }

    @Test
    void openFruitRateConfigIsNullWhenNeitherTheCacheNorTheDatabaseHasIt() {
        when(redisService.checkIfKeyExists(GameEnum.RedisKey.CONFIG_OPEN_FRUIT_RATE_CONFIG.getKey())).thenReturn(false);
        when(configRepository.findByKey(GameEnum.ConfigKey.OPEN_FRUIT_RATE_CONFIG.getKey())).thenReturn(null);

        assertNull(configService.getOpenFruitRateConfig());
        verify(redisService, never()).saveWithoutExpiredTime(any(), any());
    }

    @Test
    void seasonConfigComesFromTheCacheWhenItIsThere() {
        SeasonConfig cached = new SeasonConfig();
        when(redisService.checkIfKeyExists(GameEnum.RedisKey.CONFIG_SEASON_CONFIG.getKey())).thenReturn(true);
        when(redisService.get(GameEnum.RedisKey.CONFIG_SEASON_CONFIG.getKey())).thenReturn(cached);

        assertSame(cached, configService.getSeasonConfig());
        verify(configRepository, never()).findByKey(any());
    }

    @Test
    void seasonConfigIsLoadedFromTheDatabaseAndCachedOnAMiss() {
        when(redisService.checkIfKeyExists(GameEnum.RedisKey.CONFIG_SEASON_CONFIG.getKey())).thenReturn(false);
        when(configRepository.findByKey(GameEnum.ConfigKey.SEASON_CONFIG.getKey()))
                .thenReturn(row("season", "{\"data\":{\"currentSeason\":7,\"previousSeason\":6}}"));

        SeasonConfig loaded = configService.getSeasonConfig();

        assertEquals(7, loaded.getData().getCurrentSeason());
        assertEquals(6, loaded.getData().getPreviousSeason());
        verify(redisService).saveWithoutExpiredTime(GameEnum.RedisKey.CONFIG_SEASON_CONFIG.getKey(), loaded);
    }

    @Test
    void seasonConfigIsNullWhenNeitherTheCacheNorTheDatabaseHasIt() {
        when(redisService.checkIfKeyExists(GameEnum.RedisKey.CONFIG_SEASON_CONFIG.getKey())).thenReturn(false);
        when(configRepository.findByKey(GameEnum.ConfigKey.SEASON_CONFIG.getKey())).thenReturn(null);

        assertNull(configService.getSeasonConfig());
        verify(redisService, never()).saveWithoutExpiredTime(any(), any());
    }

    /**
     * The four getters read four different keys, and this is the test that says so: a copy-paste that
     * left one getter looking up the previous config's key would still pass every test above, because
     * each of those stubs its own key and never asks which one was used.
     */
    @Test
    void eachGetterReadsItsOwnKey() {
        when(redisService.checkIfKeyExists(any())).thenReturn(false);
        when(configRepository.findByKey(any())).thenReturn(null);

        configService.getListSeedConfig();
        configService.getPickingFruitCountdownConfig();
        configService.getOpenFruitRateConfig();
        configService.getSeasonConfig();

        for (GameEnum.ConfigKey key : GameEnum.ConfigKey.values()) {
            verify(configRepository).findByKey(key.getKey());
        }
        assertTrue(GameEnum.ConfigKey.values().length == 4);
    }
}
