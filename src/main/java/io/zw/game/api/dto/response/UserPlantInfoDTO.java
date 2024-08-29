package io.zw.game.api.dto.response;

import io.zw.game.api.dto.mapper.UserPlantDTO;
import io.zw.game.api.dto.mapper.UserWateringCanDTO;
import lombok.Data;

@Data
public class UserPlantInfoDTO {

    private UserPlantDTO userPlant;

    private UserWateringCanDTO userWateringCan;

    public UserPlantInfoDTO() {}

    public UserPlantInfoDTO(UserPlantDTO userPlant, UserWateringCanDTO userWateringCan) {
        this.userPlant = userPlant;
        this.userWateringCan = userWateringCan;
    }
}
