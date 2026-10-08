package com.ada.approval.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import com.ada.approval.persistence.EntityTimestampListener;
import com.ada.approval.persistence.TimestampedEntity;

import java.time.LocalDateTime;
import java.io.Serializable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Stores numeric permission resources and URL grant identities, not just navigation labels.
 * Retiring a frontend page does not retire the authorization meaning of its grant.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "t_menu")
@DynamicInsert
@DynamicUpdate
@EntityListeners(EntityTimestampListener.class)
@Schema(name = "Menu object", description = "")
public class Menu implements Serializable, TimestampedEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Resource ID")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Schema(description = "Resource name")
    @Column(name = "menu_name")
    private String menuName;

    @Schema(description = "Resource style")
    @Column(name = "menu_style")
    private String menuStyle;

    @Schema(description = "Resource URL")
    @Column(name = "url")
    private String url;

    @Schema(description = "Parent id")
    @Column(name = "parent_id")
    private Integer parentId;

    @Schema(description = "Parent menu permission")
    @Column(name = "parent_opt_value")
    private String parentOptValue;

    @Schema(description = "Level")
    @Column(name = "grade")
    private Integer grade;

    @Schema(description = "Permission value")
    @Column(name = "opt_value")
    private String optValue;

    @Schema(description = "Sort order")
    @Column(name = "orders")
    private Integer orders;

    @Schema(description = "Validity flag, 0 = valid, 1 = deleted")
    @Column(name = "is_valid")
    private Integer isValid;

    @Schema(description = "Created at")
    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Schema(description = "Updated at")
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Schema(description = "Parent menu name")
    @Transient
    private String parentName;
}
