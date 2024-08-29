package io.zw.game.api.models;

import jakarta.persistence.*;
import lombok.Data;

import java.util.Date;

@Entity
@Table(name = "users")
@Data
public class UserModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "social_id", nullable = false, columnDefinition = "varchar(255)")
    private String socialId;

    @Column(name = "social_type", nullable = false, columnDefinition = "int8")
    private int type;

    @Column(name = "display_name", nullable = false, columnDefinition = "varchar(255)")
    private String displayName;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamp default current_timestamp")
    private Date createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp default current_timestamp")
    private Date updatedAt;

    @Column(name = "last_login_time", nullable = false, columnDefinition = "timestamp default current_timestamp")
    private Date lastLoginTime;

    public UserModel() {
    }
}
