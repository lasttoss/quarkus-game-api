package io.zw.game.api.models;

import jakarta.persistence.*;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.*;

import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "items")
@Data
public class ItemModel {

    @Id
    @GeneratedValue(generator = "UUID")
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "UUID default gen_random_uuid()")
    private UUID id;

    @Column(nullable = false, columnDefinition = "varchar(255) default 'a'")
    private String name;

    @Column(name = "describe", nullable = true, columnDefinition = "text")
    private String describe;

    @Column(name = "image_url", nullable = false, columnDefinition = "varchar(255) default 'a'")
    private String imageUrl;

    @Column(name = "resource_id", nullable = false, columnDefinition = "int8 default 0")
    private int resourceId;

    @Column(name = "resource_type", nullable = false, columnDefinition = "int8 default 0")
    private int resourceType;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamp default current_timestamp")
    private Date createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp default current_timestamp")
    private Date updatedAt;
}
