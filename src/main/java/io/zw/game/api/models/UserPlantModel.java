package io.zw.game.api.models;

import io.zw.game.api.constants.GameEnum;
import io.zw.game.api.dto.config.PickingFruitCountdownConfigData;
import io.zw.game.api.dto.config.SeedConfigData;
import jakarta.persistence.*;
import lombok.Data;
import org.joda.time.DateTime;

import java.util.Date;

@Entity
@Table(name = "user_plants")
@Data
public class UserPlantModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "plant_id", nullable = false, columnDefinition = "int")
    private int plantId;

    @Column(name = "status", nullable = false, columnDefinition = "int2")
    private int status;

    @Column(name = "current_exp", nullable = false, columnDefinition = "int8")
    private int currentExp;

    @Column(name = "next_time_to_pick", nullable = false, columnDefinition = "int")
    private long nextTimeToPick;

    @Column(name = "protected_gem", nullable = false, columnDefinition = "boolean default false")
    private boolean protectedGem;

    @Column(name = "protected_water", nullable = false, columnDefinition = "boolean default false")
    private boolean protectedWater;

    @Column(name = "protected_plant", nullable = false, columnDefinition = "boolean default false")
    private boolean protectedPlant;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamp default current_timestamp")
    private Date createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp default current_timestamp")
    private Date updatedAt;

    public UserPlantModel() {}

    public UserPlantModel(String userId) {
        this.userId = userId;
        this.plantId = 0;
        this.status = GameEnum.PlantStatus.CAN_SOW.getValue();
        this.currentExp = 0;
        this.nextTimeToPick = 0;
        this.protectedGem = false;
        this.protectedWater = false;
        this.protectedPlant = false;
        this.createdAt = DateTime.now().toDate();
        this.updatedAt = DateTime.now().toDate();
    }

    public void addExp(int amount, SeedConfigData seedConfig, PickingFruitCountdownConfigData pickingFruitCountdownConfig) {
        this.currentExp += amount;
        this.updatedAt = DateTime.now().toDate();
        if (this.currentExp >= seedConfig.getRequiredExp().get(seedConfig.getRequiredExp().size() - 1)) {
            this.status = GameEnum.PlantStatus.COMPLETED.getValue();
            this.nextTimeToPick = DateTime.now().getMillis() / 1000 + pickingFruitCountdownConfig.getFruitTimeCountdown().get(this.plantId - 1);
        }
    }

    public void sowSeed (int plantId) {
        this.plantId = plantId;
        this.currentExp = 0;
        this.status = GameEnum.PlantStatus.IS_GROWING.getValue();
        this.updatedAt = DateTime.now().toDate();
    }

    public void reset () {
        this.plantId = 0;
        this.currentExp = 0;
        this.status = GameEnum.PlantStatus.CAN_SOW.getValue();
        this.nextTimeToPick = 0;
        this.updatedAt = DateTime.now().toDate();
    }
}
