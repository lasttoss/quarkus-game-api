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
     * Water arrives with the clock rather than from anywhere else: one every
     * {@link #SECONDS_PER_WATER}, counted from nextTimeToReset, which is the anchor of the next drop.
     * Whatever is left over stays in the anchor, so two minutes spent waiting is not thrown away when
     * a drop is collected.
     *
     * A can that has never been refilled carries nextTimeToReset = 0, and this is where that is worth
     * being careful about: (now - 0) is around seventeen hundred million, which would be millions of
     * water from a brand new can. A zero anchor is therefore not a long wait, it is a can that has not
     * started counting yet, and the first call only sets it.
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
        int gained = (nowSeconds - this.nextTimeToReset) / SECONDS_PER_WATER;
        if (gained <= 0) {
            return 0;
        }
        this.quantity += gained;
        this.nextTimeToReset += gained * SECONDS_PER_WATER;
        this.updatedAt = DateTime.now().toDate();
        return gained;
    }

    public void use(int quantity) {
        this.quantity -= quantity;
        if (this.quantity < 0) this.quantity = 0;
        this.updatedAt = DateTime.now().toDate();
    }
}
