package com.ada.approval.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import com.ada.approval.persistence.EntityTimestampListener;

import java.time.LocalDateTime;
import java.io.Serializable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Stores numeric menu-operation grants associated with a role. The existing grant values are
 * interpreted by backend authorization rather than role-name conventions.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "t_permission")
@DynamicInsert
@DynamicUpdate
@EntityListeners(EntityTimestampListener.class)
@Schema(name = "Permission object", description = "")
public class Permission implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Permission ID")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Schema(description = "Role id")
    @Column(name = "role_id")
    private Integer roleId;

    @Schema(description = "Menu ID")
    @Column(name = "menu_id")
    private Integer menuId;

    @Schema(description = "Permission value")
    @Column(name = "acl_value")
    private String aclValue;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
