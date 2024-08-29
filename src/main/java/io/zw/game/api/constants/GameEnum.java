package io.zw.game.api.constants;

import lombok.Getter;

public class GameEnum {

    @Getter
    public enum Resource {
        MONEY_RESOURCE(1), SEED_RESOURCE(2), CONSUME_RESOURCE(3),
        ENERGY_RESOURCE(4), GEM_RESOURCE(5), FRUIT_RESOURCE(6),
        JACK_SLOT_RESOURCE(7), CHEST_RESOURCE(8), TICKET_RESOURCE(9),
        HAMMER_RESOURCE(10);

        private int value;

        Resource(int value) {
            this.value = value;
        }
    }

    @Getter
    public enum ConfigKey {
        PICKING_FRUIT_COUNTDOWN_CONFIG("picking_fruit_countdown_config"),
        SEED_CONFIG("seed_config"),
        OPEN_FRUIT_RATE_CONFIG("open_fruit_rate_config"),
        SEASON_CONFIG("season_config");

        private String key;

        ConfigKey(String key) {
            this.key = key;
        }
    }

    @Getter
    public enum RedisKey {
        CONFIG_SEED_CONFIG("configs:seed"),
        CONFIG_PICKING_FRUIT_COUNTDOWN_CONFIG("configs:picking_fruit_countdown"),
        CONFIG_OPEN_FRUIT_RATE_CONFIG("configs:open_fruit_rate"),
        CONFIG_SEASON_CONFIG("configs:season");

        private String key;

        RedisKey(String key) {
            this.key = key;
        }
    }

    @Getter
    public enum PlantStatus {
        CAN_SOW(1), IS_GROWING(2), COMPLETED(3);

        private int value;

        PlantStatus(int value) {
            this.value = value;
        }
    }

    @Getter
    public enum SocialTypeLogin {
        AUTH_LOGIN(0), FACEBOOK_LOGIN(1), GOOGLE_LOGIN(2), APPLE_LOGIN(3), DEVICE_LOGIN(4);

        private int value;

        SocialTypeLogin(int value) {
            this.value = value;
        }
    }
}
