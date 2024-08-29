package io.zw.game.api.constants;

public enum ApiErrorEnum {

    EXIST_USER(1, "EXIST_USER"),
    USER_NOT_FOUND(2, "USER_NOT_FOUND"),
    ITEM_NOT_FOUND(3, "ITEM_NOT_FOUND"),
    OPCODE_NOT_FOUND(4, "OPCODE_NOT_FOUND"),
    CAN_NOT_SOW_SEED(5, "CAN_NOT_SOW_SEED"),
    INVALID_REQUEST(6, "INVALID_REQUEST"),
    INVALID_RESOURCE(7, "INVALID_RESOURCE"),
    CAN_NOT_SPRAY_WATER(8, "CAN_NOT_SPRAY_WATER"),
    NOT_ALREADY_TIME_TO_PICKING_FRUIT(9, "NOT_ALREADY_TIME_TO_PICKING_FRUIT"),
    ALREADY_PROTECTED_GEM(10, "ALREADY_PROTECTED_GEM"),
    ALREADY_PROTECTED_WATER(11, "ALREADY_PROTECTED_WATER"),
    ALREADY_PROTECTED_PLANT(12, "ALREADY_PROTECTED_PLANT"),
    ERROR_CODE(999, "ERROR_CODE");

    private final int code;

    private final String message;

    ApiErrorEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public int getCode() {
        return code;
    }

    @Override
    public String toString() {
        return code + ": " + message;
    }
}
