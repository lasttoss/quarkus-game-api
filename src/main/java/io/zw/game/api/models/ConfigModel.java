package io.zw.game.api.models;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UpdateTimestamp;
import org.joda.time.DateTime;

import java.util.Date;

@Entity
@Table(name = "configs")
@Data
public class ConfigModel {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "key", nullable = false, columnDefinition = "varchar(255) default 'a'")
    private String key;

    @Column(name = "data", nullable = true, columnDefinition = "text")
    private String data;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamp default current_timestamp")
    private Date createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp default current_timestamp")
    private Date updatedAt;
}
