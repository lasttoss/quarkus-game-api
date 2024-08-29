package io.zw.game.api.dto.mapper;

import lombok.Data;

import java.util.Date;

@Data
public class UserWateringCanDTO {

    private int quantity;

    private int nextTimeToReset;

    private Date updateAt;

}
