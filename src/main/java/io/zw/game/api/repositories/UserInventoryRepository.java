package io.zw.game.api.repositories;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.zw.game.api.models.UserInventoryModel;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class UserInventoryRepository implements PanacheRepository<UserInventoryModel> {

    public UserInventoryModel findByUserIdAndItemId(String userId, String itemId) {
        return find("userId = ?1 and itemId = ?2", userId, itemId).firstResult();
    }

    public UserInventoryModel findByUserIdAndItemIdAndSeasonId(String userId, String itemId, int seasonId) {
        return find("userId = ?1 and itemId = ?2 and seasonId = ?3", userId, itemId, seasonId).firstResult();
    }
}
