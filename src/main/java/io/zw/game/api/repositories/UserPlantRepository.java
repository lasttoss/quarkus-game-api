package io.zw.game.api.repositories;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.zw.game.api.models.UserPlantModel;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class UserPlantRepository implements PanacheRepository<UserPlantModel> {

    public UserPlantModel findByUserId(String userId) {
        return find("userId", userId).firstResult();
    }
}
