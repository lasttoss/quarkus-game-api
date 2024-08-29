package io.zw.game.api.dto.config;

import lombok.Data;

import java.util.List;

@Data
public class SeedConfigData {

    private int plantId;

    private List<Integer> requiredExp;
}
