package io.zw.game.api.dto.mapper;

import lombok.Data;

import java.util.Date;

@Data
public class UserPlantDTO {

    private int plantId;

    private int status;

    private int currentExp;

    private int maxExp;

    private int currentIndex;

    private long nextTimeToPick;

    private boolean protectedGem;

    private boolean protectedWater;

    protected boolean protectedPlant;

    private Date updatedAt;
}
