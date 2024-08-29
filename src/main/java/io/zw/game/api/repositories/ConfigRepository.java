package io.zw.game.api.repositories;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.zw.game.api.models.ConfigModel;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ConfigRepository implements PanacheRepository<ConfigModel> {

    public ConfigModel findByKey(String key) {
        return find("key", key).firstResult();
    }
}
