package io.zw.game.api.repositories;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.zw.game.api.models.ItemModel;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ItemRepository implements PanacheRepository<ItemModel> {

    public ItemModel findById(String id) {
        return find("id", id).firstResult();
    }

    public ItemModel findByResourceTypeAndResourceId(int resourceType, int resourceId) {
        return find("resourceType = ?1 and resourceId = ?2", resourceType, resourceId).firstResult();
    }
}
