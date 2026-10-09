package io.zw.game.api.models;

import jakarta.persistence.*;
import lombok.Data;
import org.joda.time.DateTime;

import java.util.Date;

@Entity
@Table(name = "user_watering_cans")
@Data
public class UserWateringCanModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "quantity", nullable = false, columnDefinition = "int2")
    private int quantity;

    @Column(name = "next_time_to_reset", nullable = false, columnDefinition = "int")
    private int nextTimeToReset;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamp default current_timestamp")
    private Date createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp default current_timestamp")
    private Date updatedAt;

    public UserWateringCanModel() {
    }

    public UserWateringCanModel(String userId) {
        this.userId = userId;
        this.quantity = 0;
        this.nextTimeToReset = 0;
        this.createdAt = DateTime.now().toDate();
        this.updatedAt = DateTime.now().toDate();
    }

    /** One water every five minutes, the only way a can fills up without the store. */
    public static final int SECONDS_PER_WATER = 5 * 60;

    /**
     * The can fills to fifty and stops. Nothing above this is ever added, and a can that is somehow
     * already above it - a store that sells water could do that - is left where it is rather than
     * brought down, because this rule is about not giving more.
     */
    public static final int MAX_WATER = 50;

    /**
     * Water arrives with the clock rather than from anywhere else: one every
     * {@link #SECONDS_PER_WATER}, counted from nextTimeToReset, up to {@link #MAX_WATER}. Whatever is
     * left over stays in the anchor, so two minutes spent waiting is not thrown away when a drop is
     * collected.
     *
     * A can that has never been refilled carries nextTimeToReset = 0, and this is where that is worth
     * being careful about: (now - 0) is around seventeen hundred million, which would be millions of
     * water from a brand new can. A zero anchor is therefore not a long wait, it is a can that has not
     * started counting yet, and the first call only sets it.
     *
     * A full can does not bank time. The anchor moves to now when the can is full or when the call
     * filled it, so an hour spent away at fifty is an hour nobody gets back: the clock starts again the
     * moment there is room for it to matter. The alternative - leaving the anchor behind to collect the
     * intervals that arrived while there was no room - would hand a player a burst for the first water
     * they spend, which is a different rule from the one this is written to.
     *
     * @return how much water the call added, so the caller can skip the write when nothing changed
     */
    public int refill(int nowSeconds) {
        if (this.nextTimeToReset == 0) {
            this.nextTimeToReset = nowSeconds;
            this.updatedAt = DateTime.now().toDate();
            return 0;
        }
        if (nowSeconds <= this.nextTimeToReset) {
            return 0;
        }
        int room = MAX_WATER - this.quantity;
        if (room <= 0) {
            this.nextTimeToReset = nowSeconds;
            this.updatedAt = DateTime.now().toDate();
            return 0;
        }
        int gained = (nowSeconds - this.nextTimeToReset) / SECONDS_PER_WATER;
        if (gained <= 0) {
            return 0;
        }
        int added = Math.min(gained, room);
        this.quantity += added;
        this.nextTimeToReset = this.quantity >= MAX_WATER
                ? nowSeconds
                : this.nextTimeToReset + added * SECONDS_PER_WATER;
        this.updatedAt = DateTime.now().toDate();
        return added;
    }

    public void use(int quantity) {
        this.quantity -= quantity;
        if (this.quantity < 0) this.quantity = 0;
        this.updatedAt = DateTime.now().toDate();
    }
}
