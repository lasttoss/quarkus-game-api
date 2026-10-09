package io.zw.game.api.services;

import com.google.gson.JsonObject;
import io.vertx.grpc.common.GrpcStatus;
import io.zw.game.api.constants.ApiErrorEnum;
import io.zw.game.api.constants.GameEnum;
import io.zw.game.api.constants.RequestConstants;
import io.zw.game.api.dto.config.OpenFruitRateConfig;
import io.zw.game.api.dto.config.OpenFruitRateConfigData;
import io.zw.game.api.dto.config.SeasonConfig;
import io.zw.game.api.dto.config.SeasonConfigData;
import io.zw.game.api.dto.mapper.UserPlantDTO;
import io.zw.game.api.dto.mapper.UserWateringCanDTO;
import io.zw.game.api.dto.response.ResultDTO;
import io.zw.game.api.mappers.UserPlantMapper;
import io.zw.game.api.mappers.UserWateringCanMapper;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

    /**
     * Recorded, not fixed: every "is this plant in the config" check in this service searches a list
     * of objects with contains(int), so it can never be true - the list holds SeedConfigData and the
     * argument is (plantId - 1). The effect is that a request which passes every other check is still
     * answered with INVALID_RESOURCE. Fixing it means deciding what the config is meant to say about a
     * plant id, which is a question for whoever writes the config, so the test records the behaviour.
     */
    @Test
    void pickingFruitStopsAtTheConfigCheckEvenWhenEverythingElseIsRight() {
        givenACompletedPlantWaitingUntil(secondsFromNow(-1));
        SeasonConfig seasonConfig = new SeasonConfig();
        SeasonConfigData seasonData = new SeasonConfigData();
        seasonData.setCurrentSeason(3);
        seasonConfig.setData(seasonData);
        when(configService.getSeasonConfig()).thenReturn(seasonConfig);

        OpenFruitRateConfig rateConfig = new OpenFruitRateConfig();
        OpenFruitRateConfigData rateData = new OpenFruitRateConfigData();
        rateData.setRates(List.of(List.of(1, 2, 3)));
        rateConfig.setData(rateData);
        when(configService.getOpenFruitRateConfig()).thenReturn(rateConfig);

        assertRefused(service.pickingFruit(USER_ID), ApiErrorEnum.INVALID_RESOURCE);
    }
}
