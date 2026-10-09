package io.zw.game.api.models;

import io.zw.game.api.constants.GameEnum;
import io.zw.game.api.dto.config.PickingFruitCountdownConfigData;
import io.zw.game.api.dto.config.SeedConfigData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A plant is the thing the player watches grow, so the rules of growing are worth pinning down
 * without a database or a Quarkus context: a new plant cannot be picked, sowing resets it, and
 * experience carries it to COMPLETED exactly once - at the last required threshold, not before.
 */
class UserPlantModelTest {

    private static final String USER_ID = "user-1";

    static SeedConfigData seedConfig(int... requiredExp) {
        SeedConfigData config = new SeedConfigData();
        config.setPlantId(1);
        config.setRequiredExp(java.util.Arrays.stream(requiredExp).boxed().toList());
        return config;
    }

    static PickingFruitCountdownConfigData countdown(int... secondsPerPlant) {
        PickingFruitCountdownConfigData config = new PickingFruitCountdownConfigData();
        config.setFruitTimeCountdown(java.util.Arrays.stream(secondsPerPlant).boxed().toList());
        return config;
    }

    @Test
    void aNewPlantIsWaitingForASeed() {
        UserPlantModel plant = new UserPlantModel(USER_ID);

        assertEquals(USER_ID, plant.getUserId());
        assertEquals(0, plant.getPlantId(), "no seed yet");
        assertEquals(GameEnum.PlantStatus.CAN_SOW.getValue(), plant.getStatus());
        assertEquals(0, plant.getCurrentExp());
        assertEquals(0, plant.getNextTimeToPick());
        assertFalse(plant.isProtectedGem());
        assertFalse(plant.isProtectedWater());
        assertFalse(plant.isProtectedPlant());
        assertNotNull(plant.getCreatedAt());
        assertNotNull(plant.getUpdatedAt());
    }

    @Test
    void sowingStartsThePlantFromZero() {
        UserPlantModel plant = new UserPlantModel(USER_ID);
        plant.setCurrentExp(500);

        plant.sowSeed(3);

        assertEquals(3, plant.getPlantId());
        assertEquals(0, plant.getCurrentExp(), "a new seed does not inherit the old growth");
        assertEquals(GameEnum.PlantStatus.IS_GROWING.getValue(), plant.getStatus());
    }

    @Test
    void experienceBelowTheLastThresholdKeepsTheplantGrowing() {
        UserPlantModel plant = new UserPlantModel(USER_ID);
        plant.sowSeed(1);

        plant.addExp(10, seedConfig(10, 20, 30), countdown(60));

        assertEquals(10, plant.getCurrentExp());
        assertEquals(GameEnum.PlantStatus.IS_GROWING.getValue(), plant.getStatus(),
                "the plant is only completed at the last threshold, not at any of them");
        assertEquals(0, plant.getNextTimeToPick(), "there is nothing to pick yet");
    }

    @Test
    void reachingTheLastThresholdCompletesThePlantAndSetsWhenItCanBePicked() {
        UserPlantModel plant = new UserPlantModel(USER_ID);
        plant.sowSeed(1);
        long before = System.currentTimeMillis() / 1000;

        plant.addExp(30, seedConfig(10, 20, 30), countdown(60, 120));

        assertEquals(GameEnum.PlantStatus.COMPLETED.getValue(), plant.getStatus());
        long wait = plant.getNextTimeToPick() - before;
        assertTrue(wait >= 60 && wait <= 62, "the countdown for this plant is 60s, waited " + wait + "s");
    }

    /** The countdown is indexed by plantId - 1, so the entry a plant waits for is its own. */
    @Test
    void theCountdownEntryBelongsToThePlantBeingGrown() {
        UserPlantModel plant = new UserPlantModel(USER_ID);
        plant.sowSeed(2);
        long before = System.currentTimeMillis() / 1000;

        plant.addExp(30, seedConfig(10, 20, 30), countdown(60, 120));

        long wait = plant.getNextTimeToPick() - before;
        assertTrue(wait >= 120 && wait <= 122, "plant 2 waits 120s, waited " + wait + "s");
    }

    @Test
    void resetTakesThePlantBackToWaitingForASeed() {
        UserPlantModel plant = new UserPlantModel(USER_ID);
        plant.sowSeed(1); // the countdown list is indexed by plantId - 1, so the data has to match
        plant.addExp(100, seedConfig(10), countdown(60));

        plant.reset();

        assertEquals(0, plant.getPlantId());
        assertEquals(0, plant.getCurrentExp());
        assertEquals(GameEnum.PlantStatus.CAN_SOW.getValue(), plant.getStatus());
        assertEquals(0, plant.getNextTimeToPick());
    }

    /**
     * Recorded: the completion check reads the last entry of the required-exp list, so a config that
     * arrived empty - a season nobody has filled in yet - throws instead of leaving the plant growing.
     * Whether that is a 500 or something the caller should handle is a decision, so the test says what
     * happens today.
     */
    @Test
    void growingWithAnEmptyConfigThrows() {
        UserPlantModel plant = new UserPlantModel(USER_ID);
        plant.sowSeed(1);

        org.junit.jupiter.api.Assertions.assertThrows(IndexOutOfBoundsException.class,
                () -> plant.addExp(10, seedConfig(), countdown(60)));
    }
}
