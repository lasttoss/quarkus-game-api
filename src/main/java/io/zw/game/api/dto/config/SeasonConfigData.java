package io.zw.game.api.dto.config;

import lombok.Data;

@Data
public class SeasonConfigData {

    private int currentSeason;

    private long startSeasonTime;

    private long endSeasonTime;

    private long startClaimFruitTime;

    private long endClaimFruitTime;

    private int previousSeason;

    private long previousStartSeasonTime;

    private long previousEndSeasonTime;

    private long previousStartClaimFruitTime;

    private long previousEndClaimFruitTime;
}
