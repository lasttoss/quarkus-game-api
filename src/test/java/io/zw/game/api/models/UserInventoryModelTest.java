package io.zw.game.api.models;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Inventory quantities are what a player spends, so the two rules that matter are pinned down here:
 * adding goes up, using goes down and stops at zero. The second one is a choice worth knowing about -
 * using more than you hold is silently clamped rather than refused, so the caller is what stops a
 * player from spending items they do not have.
 */
class UserInventoryModelTest {

    @Test
    void aNewStackCarriesWhatItWasBuiltWith() {
        UserInventoryModel item = new UserInventoryModel("user-1", "item-9", 5, 3);

        assertEquals("user-1", item.getUserId());
        assertEquals("item-9", item.getItemId());
        assertEquals(5, item.getQuantity());
        assertEquals(3, item.getSeasonId(), "a stack belongs to the season it was earned in");
        assertNotNull(item.getCreatedAt());
    }

    @Test
    void addQuantityGoesUp() {
        UserInventoryModel item = new UserInventoryModel("user-1", "item-9", 5, 3);

        item.addQuantity(7);

        assertEquals(12, item.getQuantity());
    }

    @Test
    void useGoesDown() {
        UserInventoryModel item = new UserInventoryModel("user-1", "item-9", 5, 3);

        item.use(2);

        assertEquals(3, item.getQuantity());
    }

    @Test
    void useMoreThanYouHoldStopsAtZeroInsteadOfGoingNegative() {
        UserInventoryModel item = new UserInventoryModel("user-1", "item-9", 2, 3);

        item.use(99);

        assertEquals(0, item.getQuantity(), "a negative stack would be a debt the player cannot see");
    }
}
