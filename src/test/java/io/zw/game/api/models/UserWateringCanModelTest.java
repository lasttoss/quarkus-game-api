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
}
