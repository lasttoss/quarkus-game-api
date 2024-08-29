package io.zw.game.api.dto.config;

import lombok.Data;

import java.util.List;

@Data
public class OpenFruitRateConfigData {

    private List<List<Integer>> rates;
}
