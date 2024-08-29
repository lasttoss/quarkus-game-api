package io.zw.game.api.dto.response;

import lombok.Data;

@Data
public class ResultDTO {

    private Object data;

    private int status;

    private ErrorDTO error;
}
