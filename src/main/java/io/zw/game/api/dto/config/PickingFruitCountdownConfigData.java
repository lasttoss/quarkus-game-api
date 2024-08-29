package io.zw.game.api.dto.config;

import lombok.Data;

import java.util.List;

@Data
public class PickingFruitCountdownConfigData {

    private List<Integer> fruitTimeCountdown;
}
