package io.zw.game.api.services;

import com.google.gson.JsonObject;
import io.vertx.grpc.common.GrpcStatus;
import io.zw.game.api.constants.ApiErrorEnum;
import io.zw.game.api.constants.GameEnum;
import io.zw.game.api.constants.RequestConstants;
import io.zw.game.api.dto.config.OpenFruitRateConfig;
import io.zw.game.api.dto.config.OpenFruitRateConfigData;
import io.zw.game.api.dto.config.PickingFruitCountdownConfig;
import io.zw.game.api.dto.config.PickingFruitCountdownConfigData;
import io.zw.game.api.dto.config.SeasonConfig;
import io.zw.game.api.dto.config.SeasonConfigData;
import io.zw.game.api.dto.config.SeedConfig;
import io.zw.game.api.dto.config.SeedConfigData;
import io.zw.game.api.dto.mapper.UserPlantDTO;
import io.zw.game.api.dto.mapper.UserWateringCanDTO;
import io.zw.game.api.dto.response.ResultDTO;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The plant service is where the rules are enforced against a database, so these tests are about who
 * gets to do what and when: when a fruit may be picked, when a resource may be protected, and what a
 * client is told when the answer is no.
 *
 * Everything is mocked - four repositories, the config service and the two mappers - which is what
 * makes the timing rules testable at all: the countdown a completed plant waits for is a timestamp,
 * and a test can place it on either side of now.
 */
@ExtendWith(MockitoExtension.class)
class UserPlantServiceTest {

    private static final String USER_ID = "user-1";

    @Mock
    UserPlantRepository userPlantRepository;

    @Mock
    UserWateringCanRepository userWateringCanRepository;

    @Mock
    ItemRepository itemRepository;

    @Mock
    UserInventoryRepository userInventoryRepository;

    @Mock
    ConfigService configService;

    @Mock
    UserPlantMapper userPlantMapper;

    @Mock
    UserWateringCanMapper userWateringCanMapper;

    @InjectMocks
    UserPlantService service;

    static UserPlantModel completedPlant(long nextTimeToPick) {
        UserPlantModel plant = new UserPlantModel(USER_ID);
        plant.sowSeed(1);
        plant.setStatus(GameEnum.PlantStatus.COMPLETED.getValue());
        plant.setNextTimeToPick(nextTimeToPick);
        return plant;
    }

    static long secondsFromNow(long offset) {
        return System.currentTimeMillis() / 1000 + offset;
    }

    static JsonObject protectPayload(int type) {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", type);
        return payload;
    }

    void givenACompletedPlantWaitingUntil(long nextTimeToPick) {
        UserPlantModel plant = completedPlant(nextTimeToPick);
        when(userPlantRepository.findByUserId(USER_ID)).thenReturn(plant);
        when(userWateringCanRepository.findByUserId(USER_ID)).thenReturn(new UserWateringCanModel(USER_ID));
        lenient().when(userPlantMapper.toDTO(any())).thenReturn(new UserPlantDTO());
        lenient().when(userWateringCanMapper.toDTO(any())).thenReturn(new UserWateringCanDTO());
    }

    static void assertRefused(ResultDTO response, ApiErrorEnum error) {
        assertEquals(GrpcStatus.ABORTED.code, response.getStatus());
        assertNull(response.getData());
        assertNotNull(response.getError());
        assertEquals(error.getCode(), response.getError().getCode());
    }

    // ------------------------------------------------------------------ getInfo

    @Test
    void getInfoCreatesTheRowsAPlayerDoesNotHaveYet() {
        when(userPlantRepository.findByUserId(USER_ID)).thenReturn(null);
        when(userWateringCanRepository.findByUserId(USER_ID)).thenReturn(null);
        lenient().when(userPlantMapper.toDTO(any())).thenReturn(new UserPlantDTO());
        lenient().when(userWateringCanMapper.toDTO(any())).thenReturn(new UserWateringCanDTO());

        ResultDTO response = service.getInfo(USER_ID);

        assertEquals(GrpcStatus.OK.code, response.getStatus());
        assertNotNull(response.getData());
        verify(userPlantRepository).persist(any(UserPlantModel.class));
        verify(userWateringCanRepository).persist(any(UserWateringCanModel.class));
    }

    @Test
    void getInfoLeavesWhatThePlayerAlreadyHasAlone() {
        when(userPlantRepository.findByUserId(USER_ID)).thenReturn(new UserPlantModel(USER_ID));
        when(userWateringCanRepository.findByUserId(USER_ID)).thenReturn(new UserWateringCanModel(USER_ID));
        lenient().when(userPlantMapper.toDTO(any())).thenReturn(new UserPlantDTO());
        lenient().when(userWateringCanMapper.toDTO(any())).thenReturn(new UserWateringCanDTO());

        assertEquals(GrpcStatus.OK.code, service.getInfo(USER_ID).getStatus());

        verify(userPlantRepository, never()).persist(any(UserPlantModel.class));
        verify(userWateringCanRepository, never()).persist(any(UserWateringCanModel.class));
    }

    // -------------------------------------------------------------- picking fruit

    /**
     * The countdown is the whole point of a completed plant: it becomes pickable when the timestamp
     * it carries has passed, and not before. These two tests are the pair that says so - one on each
     * side of now - because a comparison written the wrong way round passes the second and fails the
     * first, and both directions have to be checked to see it.
     */
    @Test
    void pickingFruitIsRefusedWhileTheCountdownIsStillRunning() {
        givenACompletedPlantWaitingUntil(secondsFromNow(60));

        assertRefused(service.pickingFruit(USER_ID), ApiErrorEnum.NOT_ALREADY_TIME_TO_PICKING_FRUIT);
    }

    @Test
    void pickingFruitGetsPastTheGuardOnceTheCountdownHasElapsed() {
        givenACompletedPlantWaitingUntil(secondsFromNow(-1));
        // The config lookups below the guard are not stubbed: what matters here is which error came
        // back, and "not yet time" would be the guard talking.
        lenient().when(configService.getSeasonConfig()).thenReturn(null);

        ResultDTO response = service.pickingFruit(USER_ID);

        if (response.getError() != null) {
            assertTrue(response.getError().getCode() != ApiErrorEnum.NOT_ALREADY_TIME_TO_PICKING_FRUIT.getCode(),
                    "a plant whose countdown has passed was still refused as not yet ready");
        }
    }

    @Test
    void pickingFruitIsRefusedForAPlantThatIsStillGrowing() {
        UserPlantModel growing = new UserPlantModel(USER_ID);
        growing.sowSeed(1); // IS_GROWING
        when(userPlantRepository.findByUserId(USER_ID)).thenReturn(growing);
        when(userWateringCanRepository.findByUserId(USER_ID)).thenReturn(new UserWateringCanModel(USER_ID));

        assertRefused(service.pickingFruit(USER_ID), ApiErrorEnum.NOT_ALREADY_TIME_TO_PICKING_FRUIT);
    }

    @Test
    void pickingFruitIsRefusedWhenThePlayerHasNoPlantRowAtAll() {
        when(userPlantRepository.findByUserId(USER_ID)).thenReturn(null);
        when(userWateringCanRepository.findByUserId(USER_ID)).thenReturn(new UserWateringCanModel(USER_ID));

        assertRefused(service.pickingFruit(USER_ID), ApiErrorEnum.ITEM_NOT_FOUND);
    }

    // ----------------------------------------------------------- protect resource

    /**
     * Protecting is the same shape as picking: the plant has to be ripe, and each of the three
     * protections can only be applied once. An unknown type is accepted quietly - the switch has no
     * default - which is recorded here rather than changed, since refusing it is a contract decision.
     */
    @Test
    void protectResourceMarksTheOneThatWasAskedFor() {
        UserPlantModel plant = completedPlant(secondsFromNow(-1));
        when(userPlantRepository.findByUserId(USER_ID)).thenReturn(plant);
        when(userWateringCanRepository.findByUserId(USER_ID)).thenReturn(new UserWateringCanModel(USER_ID));
        lenient().when(userPlantMapper.toDTO(any())).thenReturn(new UserPlantDTO());
        lenient().when(userWateringCanMapper.toDTO(any())).thenReturn(new UserWateringCanDTO());

        assertEquals(GrpcStatus.OK.code, service.protectResource(USER_ID, protectPayload(RequestConstants.ProtectType.GEM).toString()).getStatus());
        assertTrue(plant.isProtectedGem(), "protecting the gem did not mark the plant");

        assertEquals(GrpcStatus.OK.code, service.protectResource(USER_ID, protectPayload(RequestConstants.ProtectType.WATER).toString()).getStatus());
        assertTrue(plant.isProtectedWater(), "protecting the water did not mark the plant");

        assertEquals(GrpcStatus.OK.code, service.protectResource(USER_ID, protectPayload(RequestConstants.ProtectType.PLANT).toString()).getStatus());
        assertTrue(plant.isProtectedPlant(), "protecting the plant did not mark it");

        verify(userPlantRepository, times(3)).persist(any(UserPlantModel.class));
    }

    @Test
    void protectResourceRefusesASecondProtectionOfTheSameKind() {
        UserPlantModel plant = completedPlant(secondsFromNow(-1));
        plant.setProtectedGem(true);
        when(userPlantRepository.findByUserId(USER_ID)).thenReturn(plant);
        when(userWateringCanRepository.findByUserId(USER_ID)).thenReturn(new UserWateringCanModel(USER_ID));

        assertRefused(service.protectResource(USER_ID, protectPayload(RequestConstants.ProtectType.GEM).toString()),
                ApiErrorEnum.ALREADY_PROTECTED_GEM);
    }

    @Test
    void protectResourceIsRefusedWhileTheCountdownIsStillRunning() {
        UserPlantModel plant = completedPlant(secondsFromNow(60));
        when(userPlantRepository.findByUserId(USER_ID)).thenReturn(plant);
        when(userWateringCanRepository.findByUserId(USER_ID)).thenReturn(new UserWateringCanModel(USER_ID));

        assertRefused(service.protectResource(USER_ID, protectPayload(RequestConstants.ProtectType.WATER).toString()),
                ApiErrorEnum.NOT_ALREADY_TIME_TO_PICKING_FRUIT);
    }

    @Test
    void protectResourceWithATypeNobodyKnowsChangesNothingButSucceeds() {
        UserPlantModel plant = completedPlant(secondsFromNow(-1));
        when(userPlantRepository.findByUserId(USER_ID)).thenReturn(plant);
        when(userWateringCanRepository.findByUserId(USER_ID)).thenReturn(new UserWateringCanModel(USER_ID));
        lenient().when(userPlantMapper.toDTO(any())).thenReturn(new UserPlantDTO());
        lenient().when(userWateringCanMapper.toDTO(any())).thenReturn(new UserWateringCanDTO());

        ResultDTO response = service.protectResource(USER_ID, protectPayload(9999).toString());

        assertEquals(GrpcStatus.OK.code, response.getStatus());
        assertTrue(!plant.isProtectedGem() && !plant.isProtectedWater() && !plant.isProtectedPlant(),
                "an unknown protect type marked something");
        verify(userPlantRepository, never()).persist(any(UserPlantModel.class));
    }

    // ------------------------------------------------------- the config lookups

    // ------------------------------------------------------------------- sow seed

    static UserPlantModel emptyPlant() {
        return new UserPlantModel(USER_ID); // CAN_SOW until a seed is sown
    }

    static ItemModel seedItem() {
        ItemModel item = new ItemModel();
        item.setResourceType(GameEnum.Resource.SEED_RESOURCE.getValue());
        item.setResourceId(1);
        return item;
    }

    static SeedConfig seedConfigFor(int plants) {
        SeedConfig config = new SeedConfig();
        List<SeedConfigData> data = new java.util.ArrayList<>();
        for (int i = 0; i < plants; i++) {
            SeedConfigData entry = new SeedConfigData();
            entry.setPlantId(i + 1);
            entry.setRequiredExp(List.of(10, 20));
            data.add(entry);
        }
        config.setData(data);
        return config;
    }

    /** Everything a sowing request needs to get as far as the config check. */
    void givenEverythingSowingNeeds() {
        when(userPlantRepository.findByUserId(USER_ID)).thenReturn(emptyPlant());
        when(userWateringCanRepository.findByUserId(USER_ID)).thenReturn(new UserWateringCanModel(USER_ID));
        when(itemRepository.findById("seed-1")).thenReturn(seedItem());
        when(userInventoryRepository.findByUserIdAndItemId(USER_ID, "seed-1"))
                .thenReturn(new UserInventoryModel(USER_ID, "seed-1", 3, 1));
        when(configService.getListSeedConfig()).thenReturn(seedConfigFor(1));
        lenient().when(userPlantMapper.toDTO(any())).thenReturn(new UserPlantDTO());
        lenient().when(userWateringCanMapper.toDTO(any())).thenReturn(new UserWateringCanDTO());
    }

    @Test
    void sowSeedRefusesAPayloadThatIsNotJson() {
        assertRefused(service.sowSeed(USER_ID, "this is not json at all"), ApiErrorEnum.INVALID_REQUEST);
    }

    @Test
    void sowSeedIsRefusedWhenThePlayerHasNoPlantOrNoWateringCan() {
        when(userPlantRepository.findByUserId(USER_ID)).thenReturn(null);
        when(userWateringCanRepository.findByUserId(USER_ID)).thenReturn(new UserWateringCanModel(USER_ID));
        assertRefused(service.sowSeed(USER_ID, "{\"itemId\":\"seed-1\"}"), ApiErrorEnum.ITEM_NOT_FOUND);

        when(userPlantRepository.findByUserId(USER_ID)).thenReturn(emptyPlant());
        when(userWateringCanRepository.findByUserId(USER_ID)).thenReturn(null);
        assertRefused(service.sowSeed(USER_ID, "{\"itemId\":\"seed-1\"}"), ApiErrorEnum.ITEM_NOT_FOUND);
    }

    @Test
    void sowSeedIsRefusedWhileAPlantIsAlreadyInTheGround() {
        UserPlantModel alreadySown = emptyPlant();
        alreadySown.sowSeed(1); // IS_GROWING
        when(userPlantRepository.findByUserId(USER_ID)).thenReturn(alreadySown);
        when(userWateringCanRepository.findByUserId(USER_ID)).thenReturn(new UserWateringCanModel(USER_ID));

        assertRefused(service.sowSeed(USER_ID, "{\"itemId\":\"seed-1\"}"), ApiErrorEnum.CAN_NOT_SOW_SEED);
    }

    @Test
    void sowSeedIsRefusedForAnItemThatDoesNotExist() {
        when(userPlantRepository.findByUserId(USER_ID)).thenReturn(emptyPlant());
        when(userWateringCanRepository.findByUserId(USER_ID)).thenReturn(new UserWateringCanModel(USER_ID));
        when(itemRepository.findById("seed-1")).thenReturn(null);

        assertRefused(service.sowSeed(USER_ID, "{\"itemId\":\"seed-1\"}"), ApiErrorEnum.ITEM_NOT_FOUND);
    }

    @Test
    void sowSeedIsRefusedForAnItemThatIsNotASeed() {
        ItemModel fruit = new ItemModel();
        fruit.setResourceType(GameEnum.Resource.FRUIT_RESOURCE.getValue());
        fruit.setResourceId(1);
        when(userPlantRepository.findByUserId(USER_ID)).thenReturn(emptyPlant());
        when(userWateringCanRepository.findByUserId(USER_ID)).thenReturn(new UserWateringCanModel(USER_ID));
        when(itemRepository.findById("seed-1")).thenReturn(fruit);

        assertRefused(service.sowSeed(USER_ID, "{\"itemId\":\"seed-1\"}"), ApiErrorEnum.INVALID_RESOURCE);
    }

    @Test
    void sowSeedIsRefusedWhenThePlayerHasNoSeedsLeft() {
        when(userPlantRepository.findByUserId(USER_ID)).thenReturn(emptyPlant());
        when(userWateringCanRepository.findByUserId(USER_ID)).thenReturn(new UserWateringCanModel(USER_ID));
        when(itemRepository.findById("seed-1")).thenReturn(seedItem());

        when(userInventoryRepository.findByUserIdAndItemId(USER_ID, "seed-1")).thenReturn(null);
        assertRefused(service.sowSeed(USER_ID, "{\"itemId\":\"seed-1\"}"), ApiErrorEnum.NOT_ENOUGH_QUANTITY_TO_SOW_SEED);

        when(userInventoryRepository.findByUserIdAndItemId(USER_ID, "seed-1"))
                .thenReturn(new UserInventoryModel(USER_ID, "seed-1", 0, 1));
        assertRefused(service.sowSeed(USER_ID, "{\"itemId\":\"seed-1\"}"), ApiErrorEnum.NOT_ENOUGH_QUANTITY_TO_SOW_SEED);
    }

    @Test
    void sowSeedIsRefusedWhenTheSeedConfigIsMissing() {
        givenEverythingSowingNeeds();
        when(configService.getListSeedConfig()).thenReturn(null);

        assertRefused(service.sowSeed(USER_ID, "{\"itemId\":\"seed-1\"}"), ApiErrorEnum.INVALID_RESOURCE);
    }

    /** Where the check used to stop: a seed the config knows now goes into the ground. */
    @Test
    void sowingASeedTakesItFromTheInventoryAndPlantsIt() {
        givenEverythingSowingNeeds();
        UserPlantDTO dto = new UserPlantDTO();
        when(userPlantMapper.toDTO(any())).thenReturn(dto);

        ResultDTO response = service.sowSeed(USER_ID, "{\"itemId\":\"seed-1\"}");

        assertEquals(GrpcStatus.OK.code, response.getStatus());
        UserPlantModel plant = verify(userPlantRepository).findByUserId(USER_ID) == null ? null : null; // read back below
        plant = null;
        // the plant the service was handed, checked through the repository it wrote to
        var saved = org.mockito.ArgumentCaptor.forClass(UserPlantModel.class);
        verify(userPlantRepository).persist(saved.capture());
        assertEquals(GameEnum.PlantStatus.IS_GROWING.getValue(), saved.getValue().getStatus());
        assertEquals(1, saved.getValue().getPlantId());

        var inventory = org.mockito.ArgumentCaptor.forClass(UserInventoryModel.class);
        verify(userInventoryRepository).persist(inventory.capture());
        assertEquals(2, inventory.getValue().getQuantity(), "the seed was not taken out of the inventory");

        assertEquals(20, dto.getMaxExp(), "maxExp should be the last required exp of the config");
        assertEquals(0, dto.getCurrentIndex());
    }

    @Test
    void sowingASeedIsRefusedForAPlantIdTheConfigDoesNotReach() {
        givenEverythingSowingNeeds();
        ItemModel tooLow = seedItem();
        tooLow.setResourceId(0);
        when(itemRepository.findById("seed-1")).thenReturn(tooLow);
        assertRefused(service.sowSeed(USER_ID, "{\"itemId\":\"seed-1\"}"), ApiErrorEnum.INVALID_RESOURCE);

        ItemModel tooHigh = seedItem();
        tooHigh.setResourceId(5); // the config lists one plant
        when(itemRepository.findById("seed-1")).thenReturn(tooHigh);
        assertRefused(service.sowSeed(USER_ID, "{\"itemId\":\"seed-1\"}"), ApiErrorEnum.INVALID_RESOURCE);
    }

    // ------------------------------------------------------------------ spray water

    static UserPlantModel growingPlant() {
        UserPlantModel plant = new UserPlantModel(USER_ID);
        plant.sowSeed(1); // IS_GROWING
        return plant;
    }

    static UserWateringCanModel canHolding(int water) {
        UserWateringCanModel can = new UserWateringCanModel(USER_ID);
        can.setQuantity(water);
        return can;
    }

    void givenEverythingWateringNeeds() {
        when(userPlantRepository.findByUserId(USER_ID)).thenReturn(growingPlant());
        when(userWateringCanRepository.findByUserId(USER_ID)).thenReturn(canHolding(10));
        when(configService.getListSeedConfig()).thenReturn(seedConfigFor(1));
        lenient().when(userPlantMapper.toDTO(any())).thenReturn(new UserPlantDTO());
        lenient().when(userWateringCanMapper.toDTO(any())).thenReturn(new UserWateringCanDTO());
    }

    @Test
    void sprayWaterRefusesAPayloadThatIsNotJson() {
        assertRefused(service.sprayWater(USER_ID, "{{{"), ApiErrorEnum.INVALID_REQUEST);
    }

    @Test
    void sprayWaterIsRefusedWhenThePlayerHasNoWateringCan() {
        when(userPlantRepository.findByUserId(USER_ID)).thenReturn(growingPlant());
        when(userWateringCanRepository.findByUserId(USER_ID)).thenReturn(null);

        assertRefused(service.sprayWater(USER_ID, "{\"quantity\":1}"), ApiErrorEnum.ITEM_NOT_FOUND);
    }

    @Test
    void sprayWaterIsRefusedWhenTheCanDoesNotHoldThatMuch() {
        UserWateringCanModel can = new UserWateringCanModel(USER_ID);
        can.setQuantity(2);
        when(userPlantRepository.findByUserId(USER_ID)).thenReturn(growingPlant());
        when(userWateringCanRepository.findByUserId(USER_ID)).thenReturn(can);

        assertRefused(service.sprayWater(USER_ID, "{\"quantity\":99}"), ApiErrorEnum.NOT_ENOUGH_QUANTITY_TO_SPRAY_WATER);
    }

    @Test
    void sprayWaterIsRefusedWhenNothingIsPlanted() {
        UserWateringCanModel can = new UserWateringCanModel(USER_ID);
        can.setQuantity(10);
        when(userPlantRepository.findByUserId(USER_ID)).thenReturn(null);
        when(userWateringCanRepository.findByUserId(USER_ID)).thenReturn(can);

        assertRefused(service.sprayWater(USER_ID, "{\"quantity\":1}"), ApiErrorEnum.ITEM_NOT_FOUND);
    }

    @Test
    void sprayWaterIsRefusedWhenThePlantIsNotGrowing() {
        UserWateringCanModel can = new UserWateringCanModel(USER_ID);
        can.setQuantity(10);
        UserPlantModel ready = completedPlant(secondsFromNow(-1));
        when(userPlantRepository.findByUserId(USER_ID)).thenReturn(ready);
        when(userWateringCanRepository.findByUserId(USER_ID)).thenReturn(can);

        assertRefused(service.sprayWater(USER_ID, "{\"quantity\":1}"), ApiErrorEnum.CAN_NOT_SPRAY_WATER);
    }

    @Test
    void sprayWaterIsRefusedWhenTheSeedConfigIsMissing() {
        UserWateringCanModel can = new UserWateringCanModel(USER_ID);
        can.setQuantity(10);
        when(userPlantRepository.findByUserId(USER_ID)).thenReturn(growingPlant());
        when(userWateringCanRepository.findByUserId(USER_ID)).thenReturn(can);
        when(configService.getListSeedConfig()).thenReturn(null);

        assertRefused(service.sprayWater(USER_ID, "{\"quantity\":1}"), ApiErrorEnum.INVALID_RESOURCE);
    }

    static PickingFruitCountdownConfig countdownConfig(int... seconds) {
        PickingFruitCountdownConfig config = new PickingFruitCountdownConfig();
        PickingFruitCountdownConfigData data = new PickingFruitCountdownConfigData();
        data.setFruitTimeCountdown(java.util.Arrays.stream(seconds).boxed().toList());
        config.setData(data);
        return config;
    }

    /** Where the check used to stop: the water leaves the can and the plant grows. */
    @Test
    void wateringAPlantTakesTheWaterOutOfTheCanAndAddsTheExperience() {
        givenEverythingWateringNeeds();
        when(configService.getPickingFruitCountdownConfig()).thenReturn(countdownConfig(60));

        ResultDTO response = service.sprayWater(USER_ID, "{\"quantity\":3}");

        assertEquals(GrpcStatus.OK.code, response.getStatus());

        var plant = org.mockito.ArgumentCaptor.forClass(UserPlantModel.class);
        verify(userPlantRepository).persist(plant.capture());
        assertEquals(3, plant.getValue().getCurrentExp());

        var can = org.mockito.ArgumentCaptor.forClass(UserWateringCanModel.class);
        verify(userWateringCanRepository).persist(can.capture());
        assertEquals(7, can.getValue().getQuantity(), "the water was not taken out of the can");
    }

    /**
     * The whole of the water-over-time rule, end to end: a can that has been empty for ten minutes
     * arrives at the request holding the two water that came back with the clock, and the spray that
     * used to be refused is allowed. Before refill() existed the answer was
     * NOT_ENOUGH_QUANTITY_TO_SPRAY_WATER, which is the answer a real player would always get, since a
     * new can holds nothing.
     */
    @Test
    void waterThatCameBackWithTheClockIsEnoughToWaterWith() {
        UserWateringCanModel can = canHolding(0);
        can.setNextTimeToReset((int) secondsFromNow(-600));
        when(userPlantRepository.findByUserId(USER_ID)).thenReturn(growingPlant());
        when(userWateringCanRepository.findByUserId(USER_ID)).thenReturn(can);
        when(configService.getListSeedConfig()).thenReturn(seedConfigFor(1));
        when(configService.getPickingFruitCountdownConfig()).thenReturn(countdownConfig(60));
        lenient().when(userPlantMapper.toDTO(any())).thenReturn(new UserPlantDTO());
        lenient().when(userWateringCanMapper.toDTO(any())).thenReturn(new UserWateringCanDTO());

        ResultDTO response = service.sprayWater(USER_ID, "{\"quantity\":1}");

        assertEquals(GrpcStatus.OK.code, response.getStatus());
        assertEquals(1, can.getQuantity(), "two water came back, one was used");
    }

    @Test
    void wateringIsRefusedForAPlantIdTheConfigDoesNotReach() {
        UserWateringCanModel can = canHolding(10);
        UserPlantModel plant = new UserPlantModel(USER_ID);
        plant.sowSeed(5); // the config lists one plant
        when(userPlantRepository.findByUserId(USER_ID)).thenReturn(plant);
        when(userWateringCanRepository.findByUserId(USER_ID)).thenReturn(can);
        when(configService.getListSeedConfig()).thenReturn(seedConfigFor(1));
        lenient().when(configService.getPickingFruitCountdownConfig()).thenReturn(countdownConfig(60, 120, 180, 240, 300));

        assertRefused(service.sprayWater(USER_ID, "{\"quantity\":1}"), ApiErrorEnum.INVALID_RESOURCE);
    }

    /**
     * The countdown check reads the same way - by position - and this is the test that says so: the
     * plant is the third one, the countdown is in seconds, and the two only line up if the check asks
     * whether position 2 exists rather than whether the list holds the number 2.
     */
    @Test
    void wateringAPlantWhoseNumberIsAlsoACountdownIsAllowed() {
        UserWateringCanModel can = canHolding(10);
        UserPlantModel plant = new UserPlantModel(USER_ID);
        plant.sowSeed(3);
        when(userPlantRepository.findByUserId(USER_ID)).thenReturn(plant);
        when(userWateringCanRepository.findByUserId(USER_ID)).thenReturn(can);
        when(configService.getListSeedConfig()).thenReturn(seedConfigFor(3));
        when(configService.getPickingFruitCountdownConfig()).thenReturn(countdownConfig(60, 120, 180));
        lenient().when(userPlantMapper.toDTO(any())).thenReturn(new UserPlantDTO());
        lenient().when(userWateringCanMapper.toDTO(any())).thenReturn(new UserWateringCanDTO());

        assertEquals(GrpcStatus.OK.code, service.sprayWater(USER_ID, "{\"quantity\":2}").getStatus());
        assertEquals(8, can.getQuantity());
    }

    @Test
    void wateringIsRefusedWhenTheCountdownConfigIsMissing() {
        UserWateringCanModel can = canHolding(10);
        when(userPlantRepository.findByUserId(USER_ID)).thenReturn(growingPlant());
        when(userWateringCanRepository.findByUserId(USER_ID)).thenReturn(can);
        when(configService.getListSeedConfig()).thenReturn(seedConfigFor(1));
        when(configService.getPickingFruitCountdownConfig()).thenReturn(null);

        assertRefused(service.sprayWater(USER_ID, "{\"quantity\":1}"), ApiErrorEnum.INVALID_RESOURCE);
    }

    // --------------------------------------------------------------- picking fruit

    static OpenFruitRateConfig rateConfig(int plants) {
        OpenFruitRateConfig config = new OpenFruitRateConfig();
        OpenFruitRateConfigData data = new OpenFruitRateConfigData();
        java.util.List<java.util.List<Integer>> rates = new java.util.ArrayList<>();
        for (int i = 0; i < plants; i++) {
            rates.add(List.of(1, 2, 3));
        }
        data.setRates(rates);
        config.setData(data);
        return config;
    }

    static ItemModel fruit() {
        ItemModel item = new ItemModel();
        item.setId(UUID.randomUUID());
        item.setResourceType(GameEnum.Resource.FRUIT_RESOURCE.getValue());
        item.setResourceId(1);
        return item;
    }

    void givenAPlantReadyToBePicked() {
        when(userPlantRepository.findByUserId(USER_ID)).thenReturn(completedPlant(secondsFromNow(-1)));
        when(userWateringCanRepository.findByUserId(USER_ID)).thenReturn(canHolding(10));
        SeasonConfig seasonConfig = new SeasonConfig();
        SeasonConfigData seasonData = new SeasonConfigData();
        seasonData.setCurrentSeason(3);
        seasonConfig.setData(seasonData);
        lenient().when(configService.getSeasonConfig()).thenReturn(seasonConfig);
        lenient().when(configService.getOpenFruitRateConfig()).thenReturn(rateConfig(1));
        lenient().when(userPlantMapper.toDTO(any())).thenReturn(new UserPlantDTO());
        lenient().when(userWateringCanMapper.toDTO(any())).thenReturn(new UserWateringCanDTO());
    }

    @Test
    void pickingPutsAFruitInTheInventoryAndClearsThePlant() {
        givenAPlantReadyToBePicked();
        when(itemRepository.findByResourceTypeAndResourceId(anyInt(), anyInt())).thenReturn(fruit());

        ResultDTO response = service.pickingFruit(USER_ID);

        assertEquals(GrpcStatus.OK.code, response.getStatus());

        var inventory = org.mockito.ArgumentCaptor.forClass(UserInventoryModel.class);
        verify(userInventoryRepository).persist(inventory.capture());
        assertEquals(1, inventory.getValue().getQuantity());
        assertEquals(3, inventory.getValue().getSeasonId(), "the fruit belongs to the season it was picked in");

        var plant = org.mockito.ArgumentCaptor.forClass(UserPlantModel.class);
        verify(userPlantRepository).persist(plant.capture());
        assertEquals(GameEnum.PlantStatus.CAN_SOW.getValue(), plant.getValue().getStatus());
        assertEquals(0, plant.getValue().getPlantId());
    }

    @Test
    void pickingIsRefusedWhenTheSeasonConfigIsMissing() {
        givenAPlantReadyToBePicked();
        when(configService.getSeasonConfig()).thenReturn(null);

        assertRefused(service.pickingFruit(USER_ID), ApiErrorEnum.INVALID_RESOURCE);
        verify(userInventoryRepository, never()).persist(any(UserInventoryModel.class));
    }

    @Test
    void pickingIsRefusedWhenTheFruitItemIsMissing() {
        givenAPlantReadyToBePicked();
        when(itemRepository.findByResourceTypeAndResourceId(anyInt(), anyInt())).thenReturn(null);

        assertRefused(service.pickingFruit(USER_ID), ApiErrorEnum.ITEM_NOT_FOUND);
        verify(userPlantRepository, never()).persist(any(UserPlantModel.class));
    }

    // ------------------------------------------------------------------ getInfo

    @Test
    void getInfoHandsBackTheWaterThatCameBackWithTheClock() {
        UserWateringCanModel can = canHolding(0);
        can.setNextTimeToReset((int) secondsFromNow(-300));
        when(userPlantRepository.findByUserId(USER_ID)).thenReturn(new UserPlantModel(USER_ID));
        when(userWateringCanRepository.findByUserId(USER_ID)).thenReturn(can);
        lenient().when(userPlantMapper.toDTO(any())).thenReturn(new UserPlantDTO());
        lenient().when(userWateringCanMapper.toDTO(any())).thenReturn(new UserWateringCanDTO());

        assertEquals(GrpcStatus.OK.code, service.getInfo(USER_ID).getStatus());

        assertEquals(1, can.getQuantity());
        verify(userWateringCanRepository).persist(can);
    }
}
