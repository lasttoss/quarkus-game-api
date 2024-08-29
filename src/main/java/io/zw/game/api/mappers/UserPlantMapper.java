package io.zw.game.api.mappers;

import io.zw.game.api.dto.mapper.UserPlantDTO;
import io.zw.game.api.models.UserPlantModel;
import org.mapstruct.Mapper;


@Mapper(componentModel = "cdi")
public interface UserPlantMapper {

    UserPlantDTO toDTO(UserPlantModel item);

    UserPlantModel toDAO(UserPlantDTO item);
}
