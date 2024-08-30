package io.zw.game.api.models;

import jakarta.persistence.*;
import lombok.Data;
import org.joda.time.DateTime;

import java.util.Date;

@Entity
@Table(name = "user_inventories")
@Data
public class UserInventoryModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "item_id", nullable = false, columnDefinition = "varchar(255)")
    private String itemId;

    @Column(name = "quantity", nullable = false, columnDefinition = "int2")
    private int quantity;

    @Column(name = "season_id", nullable = false, columnDefinition = "int2")
    private int seasonId;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamp default current_timestamp")
    private Date createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp default current_timestamp")
    private Date updatedAt;

    public UserInventoryModel() {
    }

    public UserInventoryModel(String userId, String itemId, int quantity, int seasonId) {
        this.userId = userId;
        this.itemId = itemId;
        this.quantity = quantity;
        this.seasonId = seasonId;
        this.createdAt = DateTime.now().toDate();
        this.updatedAt = DateTime.now().toDate();
    }

    public void addQuantity(int quantity) {
        this.quantity += quantity;
        this.updatedAt = DateTime.now().toDate();
    }

    public void use(int quantity) {
        this.quantity -= quantity;
        if (this.quantity < 0) this.quantity = 0;
        this.updatedAt = DateTime.now().toDate();
    }
}
