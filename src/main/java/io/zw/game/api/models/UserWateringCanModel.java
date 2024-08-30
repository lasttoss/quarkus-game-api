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

    public UserWateringCanModel() {}

    public UserWateringCanModel(String userId) {
        this.userId = userId;
        this.quantity = 0;
        this.nextTimeToReset = 0;
        this.createdAt = DateTime.now().toDate();
        this.updatedAt = DateTime.now().toDate();
    }
}
