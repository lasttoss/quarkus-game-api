package io.zw.game.api.mappers;

import io.zw.game.api.dto.mapper.UserDTO;
import io.zw.game.api.models.UserModel;
import org.mapstruct.Mapper;


@Mapper(componentModel = "cdi")
public interface UserMapper {

    UserDTO toDTO(UserModel userModel);

    UserModel toDAO(UserDTO userDTO);
}
