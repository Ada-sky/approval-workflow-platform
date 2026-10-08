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
 * @author Yan Min
 * @since 2023-12-19
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "t_role")
@DynamicInsert
@DynamicUpdate
@EntityListeners(EntityTimestampListener.class)
@Schema(name = "Role object", description = "")
public class Role implements Serializable, TimestampedEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Role number")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Schema(description = "Role name")
    @Column(name = "role_name")
    private String roleName;

    @Schema(description = "Created at")
    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Schema(description = "Updated at")
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Schema(description = "0:Delete  1: Active")
    @Column(name = "status")
    private Integer status;
}
