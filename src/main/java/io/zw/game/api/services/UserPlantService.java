package io.zw.game.api.services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.zw.game.api.constants.ApiErrorEnum;
import io.zw.game.api.constants.GameEnum;
import io.zw.game.api.constants.RequestConstants;
import io.zw.game.api.dto.config.OpenFruitRateConfig;
import io.zw.game.api.dto.config.PickingFruitCountdownConfig;
import io.zw.game.api.dto.config.SeasonConfig;
import io.zw.game.api.dto.config.SeedConfig;
import io.zw.game.api.dto.logger.LogEvent;
import io.zw.game.api.dto.mapper.UserPlantDTO;
import io.zw.game.api.dto.mapper.UserWateringCanDTO;
import io.zw.game.api.dto.request.ProtectResourceRequest;
import io.zw.game.api.dto.request.SowSeedRequest;
import io.zw.game.api.dto.request.SprayWaterRequest;
import io.zw.game.api.dto.response.ErrorDTO;
import io.zw.game.api.dto.response.ResultDTO;
import io.zw.game.api.dto.response.UserPlantInfoDTO;
import io.zw.game.api.logs.EventLogger;
import io.zw.game.api.mappers.UserPlantMapper;
import io.zw.game.api.mappers.UserWateringCanMapper;
import io.zw.game.api.models.ItemModel;
import io.zw.game.api.models.UserInventoryModel;
import io.zw.game.api.models.UserPlantModel;
import io.zw.game.api.models.UserWateringCanModel;
import io.zw.game.api.repositories.ItemRepository;
import io.zw.game.api.repositories.UserInventoryRepository;
import io.zw.game.api.repositories.UserPlantRepository;
import io.zw.game.api.repositories.UserWateringCanRepository;
import io.vertx.grpc.common.GrpcStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Random;

@ApplicationScoped
public class UserPlantService {

    @Inject
    UserPlantRepository userPlantRepository;

    @Inject
    UserWateringCanRepository userWateringCanRepository;

    @Inject
    ItemRepository itemRepository;

    @Inject
    UserInventoryRepository userInventoryRepository;

    @Inject
    ConfigService configService;

    @Inject
    UserPlantMapper userPlantMapper;

    @Inject
    UserWateringCanMapper userWateringCanMapper;

    Gson gson = new Gson();


    /**
     * The config lists are positional: the line under every one of these checks indexes them with
     * get(plantId - 1), so the question being asked is whether that position exists - not whether the
     * list happens to hold the number plantId - 1. Written as contains(plantId - 1) it compared a
     * SeedConfigData (or a list of countdown seconds) against an index, which can never be true for the
     * seed configs and is true only by accident elsewhere, and every request was refused one line
     * before the code the check was there to protect.
     */
    /**
     * Seconds, as an int, because nextTimeToReset is an int column: the schema these timestamps live
     * in carries the 2038 boundary either way, and matching its width here keeps the arithmetic in one
     * type instead of casting at every use.
     */
    private static int nowSeconds() {
        return (int) (DateTime.now().getMillis() / 1000);
    }

    private static boolean isPlantInConfig(List<?> plants, int plantId) {
        int index = plantId - 1;
        return index >= 0 && index < plants.size();
    }

    @Transactional
    public ResultDTO getInfo(String userId) {
        ResultDTO response = new ResultDTO();
        UserPlantModel userPlantModel = userPlantRepository.findByUserId(userId);
        if (userPlantModel == null) {
            userPlantModel = new UserPlantModel(userId);
            userPlantRepository.persist(userPlantModel);
        }
        UserWateringCanModel userWateringCanModel = userWateringCanRepository.findByUserId(userId);
        if (userWateringCanModel == null) {
            userWateringCanModel = new UserWateringCanModel(userId);
            userWateringCanRepository.persist(userWateringCanModel);
        }
        // The can fills itself one water every five minutes, and the client should see that happen
        // even when it is only asking to look: getInfo is where the numbers in it come from.
        if (userWateringCanModel.refill(nowSeconds()) > 0) {
            userWateringCanRepository.persist(userWateringCanModel);
        }

        UserPlantDTO userPlantDTO = userPlantMapper.toDTO(userPlantModel);
        UserWateringCanDTO userWateringCanDTO = userWateringCanMapper.toDTO(userWateringCanModel);
        UserPlantInfoDTO info = new UserPlantInfoDTO(userPlantDTO, userWateringCanDTO);

        response.setStatus(GrpcStatus.OK.code);
        response.setData(info);
        return response;
    }

    @Transactional
    public ResultDTO sowSeed(String userId, String payload) {
        ResultDTO response = new ResultDTO();

        UserPlantModel userPlantModel = userPlantRepository.findByUserId(userId);
        UserWateringCanModel userWateringCanModel = userWateringCanRepository.findByUserId(userId);

        SowSeedRequest request = null;
        try {
            request = gson.fromJson(payload, SowSeedRequest.class);
        } catch (Exception e) {
            ErrorDTO error = new ErrorDTO(ApiErrorEnum.INVALID_REQUEST);
            response.setStatus(GrpcStatus.ABORTED.code);
            response.setError(error);
            return response;
        }

        if (userPlantModel == null) {
            ErrorDTO error = new ErrorDTO(ApiErrorEnum.ITEM_NOT_FOUND);
            response.setStatus(GrpcStatus.ABORTED.code);
            response.setError(error);
            return response;
        }

        if (userWateringCanModel == null) {
            ErrorDTO error = new ErrorDTO(ApiErrorEnum.ITEM_NOT_FOUND);
            response.setStatus(GrpcStatus.ABORTED.code);
            response.setError(error);
            return response;
        }

        if (userPlantModel.getStatus() != GameEnum.PlantStatus.CAN_SOW.getValue()) {
            ErrorDTO error = new ErrorDTO(ApiErrorEnum.CAN_NOT_SOW_SEED);
            response.setStatus(GrpcStatus.ABORTED.code);
            response.setError(error);
            return response;
        }

        ItemModel item = itemRepository.findById(request.getItemId());
        if (item == null) {
            ErrorDTO error = new ErrorDTO(ApiErrorEnum.ITEM_NOT_FOUND);
            response.setStatus(GrpcStatus.ABORTED.code);
            response.setError(error);
            return response;
        }

        if (item.getResourceType() != GameEnum.Resource.SEED_RESOURCE.getValue()) {
            ErrorDTO error = new ErrorDTO(ApiErrorEnum.INVALID_RESOURCE);
            response.setStatus(GrpcStatus.ABORTED.code);
            response.setError(error);
            return response;
        }

        UserInventoryModel userInventoryModel = userInventoryRepository.findByUserIdAndItemId(userId, request.getItemId());
        if (userInventoryModel == null || userInventoryModel.getQuantity() <= 0) {
            ErrorDTO error = new ErrorDTO(ApiErrorEnum.NOT_ENOUGH_QUANTITY_TO_SOW_SEED);
            response.setStatus(GrpcStatus.ABORTED.code);
            response.setError(error);
            return response;
        }

        SeedConfig seedConfig = configService.getListSeedConfig();
        if (seedConfig == null) {
            ErrorDTO error = new ErrorDTO(ApiErrorEnum.INVALID_RESOURCE);
            response.setStatus(GrpcStatus.ABORTED.code);
            response.setError(error);
            return response;
        }

        if (!isPlantInConfig(seedConfig.getData(), item.getResourceId())) {
            ErrorDTO error = new ErrorDTO(ApiErrorEnum.INVALID_RESOURCE);
            response.setStatus(GrpcStatus.ABORTED.code);
            response.setError(error);
            return response;
        }

        userPlantModel.sowSeed(item.getResourceId());
        userPlantRepository.persist(userPlantModel);

        UserPlantDTO userPlantInfoDTO = userPlantMapper.toDTO(userPlantModel);
        var maxIndex = seedConfig.getData().get(userPlantModel.getPlantId() - 1).getRequiredExp().size() - 1;
        userPlantInfoDTO.setMaxExp(seedConfig.getData().get(userPlantModel.getPlantId() - 1).getRequiredExp().get(maxIndex));
        userPlantInfoDTO.setCurrentIndex(0);

        UserPlantDTO userPlantDTO = userPlantMapper.toDTO(userPlantModel);
        UserWateringCanDTO userWateringCanDTO = userWateringCanMapper.toDTO(userWateringCanModel);
        UserPlantInfoDTO info = new UserPlantInfoDTO(userPlantDTO, userWateringCanDTO);

        int oldValue = userInventoryModel.getQuantity();
        userInventoryModel.use(1);
        userInventoryRepository.persist(userInventoryModel);
        int newValue = userInventoryModel.getQuantity();

        response.setStatus(GrpcStatus.OK.code);
        response.setData(info);

        JsonObject data = new JsonObject();
        data.addProperty("oldValue", oldValue);
        data.addProperty("newValue", newValue);
        EventLogger.writeToLog(new LogEvent(userId, GameEnum.EventLoggerEnum.SOW_SEED.getValue(), gson.toJson(data), gson.toJson(userPlantModel)));
        return response;
    }

    @Transactional
    public ResultDTO sprayWater(String userId, String payload) {
        ResultDTO response = new ResultDTO();
        UserPlantModel userPlantModel = userPlantRepository.findByUserId(userId);
        UserWateringCanModel userWateringCanModel = userWateringCanRepository.findByUserId(userId);

        SprayWaterRequest request = null;
        try {
            request = gson.fromJson(payload, SprayWaterRequest.class);
        } catch (Exception e) {
            ErrorDTO error = new ErrorDTO(ApiErrorEnum.INVALID_REQUEST);
            response.setStatus(GrpcStatus.ABORTED.code);
            response.setError(error);
            return response;
        }

        if (userWateringCanModel == null) {
            ErrorDTO error = new ErrorDTO(ApiErrorEnum.ITEM_NOT_FOUND);
            response.setStatus(GrpcStatus.ABORTED.code);
            response.setError(error);
            return response;
        }

        // Before the check below, or a player who has been away long enough for water to come back
        // would still be told there is not enough of it.
        if (userWateringCanModel.refill(nowSeconds()) > 0) {
            userWateringCanRepository.persist(userWateringCanModel);
        }

        if (userWateringCanModel.getQuantity() < request.getQuantity()) {
            ErrorDTO error = new ErrorDTO(ApiErrorEnum.NOT_ENOUGH_QUANTITY_TO_SPRAY_WATER);
            response.setStatus(GrpcStatus.ABORTED.code);
            response.setError(error);
            return response;
        }

        if (userPlantModel == null) {
            ErrorDTO error = new ErrorDTO(ApiErrorEnum.ITEM_NOT_FOUND);
            response.setStatus(GrpcStatus.ABORTED.code);
            response.setError(error);
            return response;
        }

        if (userPlantModel.getStatus() != GameEnum.PlantStatus.IS_GROWING.getValue()) {
            ErrorDTO error = new ErrorDTO(ApiErrorEnum.CAN_NOT_SPRAY_WATER);
            response.setStatus(GrpcStatus.ABORTED.code);
            response.setError(error);
            return response;
        }

        SeedConfig seedConfig = configService.getListSeedConfig();
        if (seedConfig == null) {
            ErrorDTO error = new ErrorDTO(ApiErrorEnum.INVALID_RESOURCE);
            response.setStatus(GrpcStatus.ABORTED.code);
            response.setError(error);
            return response;
        }

        if (!isPlantInConfig(seedConfig.getData(), userPlantModel.getPlantId())) {
            ErrorDTO error = new ErrorDTO(ApiErrorEnum.INVALID_RESOURCE);
            response.setStatus(GrpcStatus.ABORTED.code);
            response.setError(error);
            return response;
        }

        PickingFruitCountdownConfig pickingFruitCountdownConfig = configService.getPickingFruitCountdownConfig();
        if (pickingFruitCountdownConfig == null) {
            ErrorDTO error = new ErrorDTO(ApiErrorEnum.INVALID_RESOURCE);
            response.setStatus(GrpcStatus.ABORTED.code);
            response.setError(error);
            return response;
        }
        if (!isPlantInConfig(pickingFruitCountdownConfig.getData().getFruitTimeCountdown(), userPlantModel.getPlantId())) {
            ErrorDTO error = new ErrorDTO(ApiErrorEnum.INVALID_RESOURCE);
            response.setStatus(GrpcStatus.ABORTED.code);
            response.setError(error);
            return response;
        }

        userPlantModel.addExp(request.getQuantity(), seedConfig.getData().get(userPlantModel.getPlantId() - 1), pickingFruitCountdownConfig.getData());
        userPlantRepository.persist(userPlantModel);

        int oldValue = userWateringCanModel.getQuantity();
        userWateringCanModel.use(request.getQuantity());
        userWateringCanRepository.persist(userWateringCanModel);
        int newValue = userWateringCanModel.getQuantity();

        UserPlantDTO userPlantInfoDTO = userPlantMapper.toDTO(userPlantModel);
        var maxIndex = seedConfig.getData().get(userPlantModel.getPlantId() - 1).getRequiredExp().size() - 1;
        userPlantInfoDTO.setMaxExp(seedConfig.getData().get(userPlantModel.getPlantId() - 1).getRequiredExp().get(maxIndex));
        userPlantInfoDTO.setCurrentIndex(0);

        UserPlantDTO userPlantDTO = userPlantMapper.toDTO(userPlantModel);
        UserWateringCanDTO userWateringCanDTO = userWateringCanMapper.toDTO(userWateringCanModel);
        UserPlantInfoDTO info = new UserPlantInfoDTO(userPlantDTO, userWateringCanDTO);

        response.setStatus(GrpcStatus.OK.code);
        response.setData(info);

        JsonObject data = new JsonObject();
        data.addProperty("oldValue", oldValue);
        data.addProperty("newValue", newValue);

        JsonObject metadata = new JsonObject();
        metadata.addProperty("plant", gson.toJson(userPlantModel));
        metadata.addProperty("water", gson.toJson(userWateringCanModel));
        EventLogger.writeToLog(new LogEvent(userId, GameEnum.EventLoggerEnum.SPRAY_WATER.getValue(), gson.toJson(data), gson.toJson(metadata)));
        return response;
    }

    @Transactional
    public ResultDTO pickingFruit(String userId) {
        ResultDTO response = new ResultDTO();
        UserPlantModel userPlantModel = userPlantRepository.findByUserId(userId);
        UserWateringCanModel userWateringCanModel = userWateringCanRepository.findByUserId(userId);

        if (userPlantModel == null) {
            ErrorDTO error = new ErrorDTO(ApiErrorEnum.ITEM_NOT_FOUND);
            response.setStatus(GrpcStatus.ABORTED.code);
            response.setError(error);
            return response;
        }

        // Refused while the countdown is still running, which is what the error name says: the
        // comparison was the other way round, so a plant was pickable before its time and refused
        // once the time had come. nextTimeToPick is a future timestamp - addExp sets it to
        // now + countdown - so "the time has not come" is nextTimeToPick > now.
        if (userPlantModel.getStatus() != GameEnum.PlantStatus.COMPLETED.getValue() || userPlantModel.getNextTimeToPick() > DateTime.now().getMillis() / 1000) {
            ErrorDTO error = new ErrorDTO(ApiErrorEnum.NOT_ALREADY_TIME_TO_PICKING_FRUIT);
            response.setStatus(GrpcStatus.ABORTED.code);
            response.setError(error);
            return response;
        }

        SeasonConfig seasonConfig = configService.getSeasonConfig();
        if (seasonConfig == null) {
            ErrorDTO error = new ErrorDTO(ApiErrorEnum.INVALID_RESOURCE);
            response.setStatus(GrpcStatus.ABORTED.code);
            response.setError(error);
            return response;
        }

        OpenFruitRateConfig openFruitRateConfig = configService.getOpenFruitRateConfig();
        if (openFruitRateConfig == null) {
            ErrorDTO error = new ErrorDTO(ApiErrorEnum.INVALID_RESOURCE);
            response.setStatus(GrpcStatus.ABORTED.code);
            response.setError(error);
            return response;
        }

        int currentPlantId = userPlantModel.getPlantId();

        if (!isPlantInConfig(openFruitRateConfig.getData().getRates(), currentPlantId)) {
            ErrorDTO error = new ErrorDTO(ApiErrorEnum.INVALID_RESOURCE);
            response.setStatus(GrpcStatus.ABORTED.code);
            response.setError(error);
            return response;
        }

        List<Integer> rates = openFruitRateConfig.getData().getRates().get(currentPlantId - 1);
        Random random = new Random();
        int randomNumber = random.nextInt(rates.get(rates.size() - 1));
        int currentIndex = 0;
        while (currentIndex < rates.size()) {
            if (randomNumber <= rates.get(currentIndex)) {
                break;
            }
            currentIndex++;
        }
        int newResourceId = (currentPlantId - 1) * 2 + (currentIndex + 1);
        ItemModel item = itemRepository.findByResourceTypeAndResourceId(GameEnum.Resource.FRUIT_RESOURCE.getValue(), newResourceId);

        if (item == null) {
            ErrorDTO error = new ErrorDTO(ApiErrorEnum.ITEM_NOT_FOUND);
            response.setStatus(GrpcStatus.ABORTED.code);
            response.setError(error);
            return response;
        }

        UserInventoryModel userInventory = userInventoryRepository.findByUserIdAndItemId(userId, item.getId().toString());
        if (userInventory == null) {
            userInventory = new UserInventoryModel(userId, item.getId().toString(), 0, seasonConfig.getData().getCurrentSeason());
        }
        userInventory.addQuantity(1);
        userInventoryRepository.persist(userInventory);

        userPlantModel.reset();
        userPlantRepository.persist(userPlantModel);

        UserPlantDTO userPlantInfoDTO = userPlantMapper.toDTO(userPlantModel);
        userPlantInfoDTO.setMaxExp(0);
        userPlantInfoDTO.setCurrentIndex(0);

        UserPlantDTO userPlantDTO = userPlantMapper.toDTO(userPlantModel);
        UserWateringCanDTO userWateringCanDTO = userWateringCanMapper.toDTO(userWateringCanModel);
        UserPlantInfoDTO info = new UserPlantInfoDTO(userPlantDTO, userWateringCanDTO);

        response.setStatus(GrpcStatus.OK.code);
        response.setData(info);

        JsonObject data = new JsonObject();

        JsonObject metadata = new JsonObject();
        metadata.addProperty("plant", gson.toJson(userPlantModel));
        metadata.addProperty("inventory", gson.toJson(userInventory));
        EventLogger.writeToLog(new LogEvent(userId, GameEnum.EventLoggerEnum.PICKING_FRUIT.getValue(), gson.toJson(data), gson.toJson(metadata)));
        return response;
    }

    @Transactional
    public ResultDTO protectResource(String userId, String payload) {
        ResultDTO response = new ResultDTO();
        UserPlantModel userPlantModel = userPlantRepository.findByUserId(userId);
        UserWateringCanModel userWateringCanModel = userWateringCanRepository.findByUserId(userId);

        ProtectResourceRequest request = null;
        try {
            request = gson.fromJson(payload, ProtectResourceRequest.class);
        } catch (Exception e) {
            ErrorDTO error = new ErrorDTO(ApiErrorEnum.INVALID_REQUEST);
            response.setStatus(GrpcStatus.ABORTED.code);
            response.setError(error);
            return response;
        }

        if (userPlantModel == null) {
            ErrorDTO error = new ErrorDTO(ApiErrorEnum.ITEM_NOT_FOUND);
            response.setStatus(GrpcStatus.ABORTED.code);
            response.setError(error);
            return response;
        }

        // Refused while the countdown is still running, which is what the error name says: the
        // comparison was the other way round, so a plant was pickable before its time and refused
        // once the time had come. nextTimeToPick is a future timestamp - addExp sets it to
        // now + countdown - so "the time has not come" is nextTimeToPick > now.
        if (userPlantModel.getStatus() != GameEnum.PlantStatus.COMPLETED.getValue() || userPlantModel.getNextTimeToPick() > DateTime.now().getMillis() / 1000) {
            ErrorDTO error = new ErrorDTO(ApiErrorEnum.NOT_ALREADY_TIME_TO_PICKING_FRUIT);
            response.setStatus(GrpcStatus.ABORTED.code);
            response.setError(error);
            return response;
        }

        switch (request.getType()) {
            case RequestConstants.ProtectType.GEM: {
                if (userPlantModel.isProtectedGem()) {
                    ErrorDTO error = new ErrorDTO(ApiErrorEnum.ALREADY_PROTECTED_GEM);
                    response.setStatus(GrpcStatus.ABORTED.code);
                    response.setError(error);
                    return response;
                }
                userPlantModel.setProtectedGem(true);
                userPlantRepository.persist(userPlantModel);
                break;
            }
            case RequestConstants.ProtectType.WATER: {
                if (userPlantModel.isProtectedWater()) {
                    ErrorDTO error = new ErrorDTO(ApiErrorEnum.ALREADY_PROTECTED_WATER);
                    response.setStatus(GrpcStatus.ABORTED.code);
                    response.setError(error);
                    return response;
                }
                userPlantModel.setProtectedWater(true);
                userPlantRepository.persist(userPlantModel);
                break;
            }
            case RequestConstants.ProtectType.PLANT: {
                if (userPlantModel.isProtectedPlant()) {
                    ErrorDTO error = new ErrorDTO(ApiErrorEnum.ALREADY_PROTECTED_PLANT);
                    response.setStatus(GrpcStatus.ABORTED.code);
                    response.setError(error);
                    return response;
                }
                userPlantModel.setProtectedPlant(true);
                userPlantRepository.persist(userPlantModel);
                break;
            }
        }

        UserPlantDTO userPlantInfoDTO = userPlantMapper.toDTO(userPlantModel);
        userPlantInfoDTO.setMaxExp(0);
        userPlantInfoDTO.setCurrentIndex(0);

        UserPlantDTO userPlantDTO = userPlantMapper.toDTO(userPlantModel);
        UserWateringCanDTO userWateringCanDTO = userWateringCanMapper.toDTO(userWateringCanModel);
        UserPlantInfoDTO info = new UserPlantInfoDTO(userPlantDTO, userWateringCanDTO);

        response.setStatus(GrpcStatus.OK.code);
        response.setData(info);
        return response;
    }
}
