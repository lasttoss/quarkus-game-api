package io.zw.game.api.services;

import com.google.gson.Gson;
import io.zw.game.api.constants.GameEnum;
import io.zw.game.api.dto.config.OpenFruitRateConfig;
import io.zw.game.api.dto.config.PickingFruitCountdownConfig;
import io.zw.game.api.dto.config.SeasonConfig;
import io.zw.game.api.dto.config.SeedConfig;
import io.zw.game.api.models.ConfigModel;
import io.zw.game.api.repositories.ConfigRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ConfigService {

    @Inject
    ConfigRepository configRepository;

    @Inject
    RedisService redisService;

    Gson gson = new Gson();

    public SeedConfig getListSeedConfig() {
        if (!redisService.checkIfKeyExists(GameEnum.RedisKey.CONFIG_SEED_CONFIG.getKey())) {
            ConfigModel configModel = configRepository.findByKey(GameEnum.ConfigKey.SEED_CONFIG.getKey());
            if (configModel == null) {
                return null;
            }
            SeedConfig cfg = gson.fromJson(configModel.getData(), SeedConfig.class);
            redisService.saveWithoutExpiredTime(GameEnum.RedisKey.CONFIG_SEED_CONFIG.getKey(), cfg);
            return cfg;
        } else {
            SeedConfig cfg = (SeedConfig) redisService.get(GameEnum.RedisKey.CONFIG_SEED_CONFIG.getKey());
            return cfg;
        }
    }

    public PickingFruitCountdownConfig getPickingFruitCountdownConfig() {
        if (!redisService.checkIfKeyExists(GameEnum.RedisKey.CONFIG_PICKING_FRUIT_COUNTDOWN_CONFIG.getKey())) {
            ConfigModel configModel = configRepository.findByKey(GameEnum.ConfigKey.PICKING_FRUIT_COUNTDOWN_CONFIG.getKey());
            if (configModel == null) {
                return null;
            }
            PickingFruitCountdownConfig cfg = gson.fromJson(configModel.getData(), PickingFruitCountdownConfig.class);
            redisService.saveWithoutExpiredTime(GameEnum.RedisKey.CONFIG_PICKING_FRUIT_COUNTDOWN_CONFIG.getKey(), cfg);
            return cfg;
        } else {
            PickingFruitCountdownConfig cfg = (PickingFruitCountdownConfig) redisService.get(GameEnum.RedisKey.CONFIG_PICKING_FRUIT_COUNTDOWN_CONFIG.getKey());
            return cfg;
        }
    }

    public OpenFruitRateConfig getOpenFruitRateConfig() {
        if (!redisService.checkIfKeyExists(GameEnum.RedisKey.CONFIG_OPEN_FRUIT_RATE_CONFIG.getKey())) {
            ConfigModel configModel = configRepository.findByKey(GameEnum.ConfigKey.OPEN_FRUIT_RATE_CONFIG.getKey());
            if (configModel == null) {
                return null;
            }
            OpenFruitRateConfig cfg = gson.fromJson(configModel.getData(), OpenFruitRateConfig.class);
            redisService.saveWithoutExpiredTime(GameEnum.RedisKey.CONFIG_OPEN_FRUIT_RATE_CONFIG.getKey(), cfg);
            return cfg;
        } else {
            OpenFruitRateConfig cfg = (OpenFruitRateConfig) redisService.get(GameEnum.RedisKey.CONFIG_OPEN_FRUIT_RATE_CONFIG.getKey());
            return cfg;
        }
    }

    public SeasonConfig getSeasonConfig() {
        if (!redisService.checkIfKeyExists(GameEnum.RedisKey.CONFIG_SEASON_CONFIG.getKey())) {
            ConfigModel configModel = configRepository.findByKey(GameEnum.ConfigKey.SEASON_CONFIG.getKey());
            if (configModel == null) {
                return null;
            }
            SeasonConfig cfg = gson.fromJson(configModel.getData(), SeasonConfig.class);
            redisService.saveWithoutExpiredTime(GameEnum.RedisKey.CONFIG_SEASON_CONFIG.getKey(), cfg);
            return cfg;
        } else {
            SeasonConfig cfg = (SeasonConfig) redisService.get(GameEnum.RedisKey.CONFIG_SEASON_CONFIG.getKey());
            return cfg;
        }
    }
}
