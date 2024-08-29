package io.zw.game.api.mappers;

import io.zw.game.api.dto.mapper.UserWateringCanDTO;
import io.zw.game.api.models.UserWateringCanModel;
import org.mapstruct.Mapper;


@Mapper(componentModel = "cdi")
public interface UserWateringCanMapper {

    UserWateringCanDTO toDTO(UserWateringCanModel item);

    UserWateringCanModel toDAO(UserWateringCanDTO item);
}
