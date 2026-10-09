package io.zw.game.api.models;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The watering can is the resource a plant spends, with the same clamp as an inventory stack: it
 * cannot go below zero, and the daily refill is the service's business rather than the model's.
 */
class UserWateringCanModelTest {

    @Test
    void aNewCanStartsEmptyAndUnrefilled() {
        UserWateringCanModel can = new UserWateringCanModel("user-1");

        assertEquals("user-1", can.getUserId());
        assertEquals(0, can.getQuantity());
        assertEquals(0, can.getNextTimeToReset());
        assertNotNull(can.getCreatedAt());
    }

    @Test
    void useGoesDownAndStopsAtZero() {
        UserWateringCanModel can = new UserWateringCanModel("user-1");
        can.setQuantity(3);

        can.use(2);
        assertEquals(1, can.getQuantity());

        can.use(5);
        assertEquals(0, can.getQuantity(), "using a can you do not have is clamped, not refused");
    }

    @Test
    void useMovesTheUpdatedTimeForward() {
        UserWateringCanModel can = new UserWateringCanModel("user-1");
        can.setQuantity(1);
        long before = can.getUpdatedAt().getTime();

        can.use(1);

        assertTrue(can.getUpdatedAt().getTime() >= before,
                "the row is written back on every use, so the timestamp has to move");
    }

    /**
     * The watering can fills from the clock: one water every five minutes, counted from the anchor the
     * previous drop left behind. A new can is the case worth being careful about - its anchor is zero,
     * and a zero is not "a very long wait", it is "this can has not started counting", so the first call
     * sets the anchor instead of handing over the seventeen hundred million water that now - 0 works out
     * to. That test is the one that would fail if the zero were treated as a timestamp.
     */
    @Test
    void refillGivesOneWaterEveryFiveMinutes() {
        UserWateringCanModel can = new UserWateringCanModel("user-1");

        assertEquals(0, can.refill(1_000), "a fresh can starts counting, it does not pour");
        assertEquals(1_000, can.getNextTimeToReset());
        assertEquals(0, can.refill(1_000 + 299), "four minutes fifty-nine is not five minutes");
        assertEquals(1, can.refill(1_000 + 300));
        assertEquals(1, can.getQuantity());
    }

    @Test
    void refillKeepsTheTimeThatWasNotEnoughForAWater() {
        UserWateringCanModel can = new UserWateringCanModel("user-1");
        can.refill(1_000);

        assertEquals(1, can.refill(1_300));
        assertEquals(1_300, can.getNextTimeToReset(), "the anchor moves by exactly one interval");

        assertEquals(0, can.refill(1_599), "the two minutes already waited are not thrown away");
        assertEquals(1, can.refill(1_600));
        assertEquals(2, can.getQuantity());
    }

    @Test
    void refillHandsOverEverythingTheClockOwed() {
        UserWateringCanModel can = new UserWateringCanModel("user-1");
        can.refill(1_000);

        assertEquals(12, can.refill(1_000 + 3_600), "an hour away is twelve water");
        assertEquals(12, can.getQuantity());
    }

    @Test
    void refillDoesNothingWhileTheClockStandsStillOrGoesBackwards() {
        UserWateringCanModel can = new UserWateringCanModel("user-1");
        can.setNextTimeToReset(5_000);
        can.setQuantity(3);

        assertEquals(0, can.refill(5_000));
        assertEquals(0, can.refill(4_900), "a clock that went backwards is not a reason to pour");
        assertEquals(3, can.getQuantity());
    }

    @Test
    void aCanThatHasNeverBeenRefilledIsNotGivenMillionsOfWater() {
        UserWateringCanModel can = new UserWateringCanModel("user-1");

        assertEquals(0, can.refill(1_700_000_000));
        assertEquals(0, can.getQuantity());
        assertEquals(1_700_000_000, can.getNextTimeToReset());
    }

    /**
     * Fifty is the ceiling and the clock does not bank time behind it. Both halves matter: a can that
     * filled to fifty and stopped is the rule, and a can that filled to fifty, waited an hour and then
     * paid out twelve water for the first one spent would be a different rule wearing the same ceiling.
     */
    @Test
    void refillStopsAtTheCeiling() {
        UserWateringCanModel can = new UserWateringCanModel("user-1");
        can.setQuantity(50);
        can.setNextTimeToReset(1_000);

        assertEquals(0, can.refill(1_000 + 3_600), "a full can was topped up");
        assertEquals(50, can.getQuantity());
        assertEquals(1_000 + 3_600, can.getNextTimeToReset(), "a full can starts its clock again");
    }

    @Test
    void refillFillsUpToTheCeilingAndNoFurther() {
        UserWateringCanModel can = new UserWateringCanModel("user-1");
        can.setQuantity(49);
        can.setNextTimeToReset(1_000);

        assertEquals(1, can.refill(1_000 + 3_600), "an hour owed twelve water, only one fit");
        assertEquals(50, can.getQuantity());
        assertEquals(1_000 + 3_600, can.getNextTimeToReset());
    }

    @Test
    void anHourSpentFullIsNotPaidOutLater() {
        UserWateringCanModel can = new UserWateringCanModel("user-1");
        can.setQuantity(50);
        can.setNextTimeToReset(1_000);
        can.refill(1_000 + 3_600); // full, the clock restarts

        can.use(1);
        assertEquals(49, can.getQuantity());

        assertEquals(0, can.refill(1_000 + 3_660), "a minute after being full is a minute");
        assertEquals(1, can.refill(1_000 + 3_900), "five minutes after being full is a water");
        assertEquals(50, can.getQuantity());
    }

    @Test
    void aCanAlreadyAboveTheCeilingIsLeftWhereItIs() {
        UserWateringCanModel can = new UserWateringCanModel("user-1");
        can.setQuantity(60); // nothing in this repository can do this; a store elsewhere might
        can.setNextTimeToReset(1_000);

        assertEquals(0, can.refill(1_000 + 3_600));
        assertEquals(60, can.getQuantity(), "the rule is about not giving more, not about taking away");
    }

    @Test
    void refillStillWorksBelowTheCeiling() {
        UserWateringCanModel can = new UserWateringCanModel("user-1");
        can.setQuantity(40);
        can.setNextTimeToReset(1_000);

        assertEquals(2, can.refill(1_000 + 600));
        assertEquals(42, can.getQuantity());
        assertEquals(1_600, can.getNextTimeToReset(), "the anchor moves by what was handed over");
    }
}
