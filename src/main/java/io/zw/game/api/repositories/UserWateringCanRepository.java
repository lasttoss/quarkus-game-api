package io.zw.game.api.repositories;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.zw.game.api.models.UserWateringCanModel;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class UserWateringCanRepository implements PanacheRepository<UserWateringCanModel> {

    public UserWateringCanModel findByUserId(String userId) {
        return find("userId", userId).firstResult();
    }
}
