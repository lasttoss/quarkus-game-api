package io.zw.game.api.dto.response;

import io.zw.game.api.constants.ApiErrorEnum;
import lombok.Data;

@Data
public class ErrorDTO {

    private String message;

    private int code;

    public ErrorDTO() {}

    public ErrorDTO(ApiErrorEnum error) {
        this.code = error.getCode();
        this.message = error.getMessage();
    }
}
