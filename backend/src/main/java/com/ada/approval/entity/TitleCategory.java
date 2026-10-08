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
 * Stores hierarchical job-title references used by employee profiles and organizational workflow
 * identity. Persisted title compatibility is handled explicitly without rewriting historical
 * records.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "t_title_category")
@DynamicInsert
@DynamicUpdate
@EntityListeners(EntityTimestampListener.class)
@Schema(name = "TitleCategory object", description = "")
public class TitleCategory implements Serializable, TimestampedEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Job title id")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Schema(description = "Job title number")
    @Column(name = "title_num")
    private String titleNum;

    @Schema(description = "Job title name")
    @Column(name = "title_name")
    private String titleName;

    @Schema(description = "Parent id")
    @Column(name = "parent_id")
    private Integer parentId;

    @Schema(description = "Created at")
    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Schema(description = "Updated at")
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Schema(description = "0:Delete  1: Active   2: Disabled")
    @Column(name = "status")
    private Integer status;

    @Schema(description = "Level")
    @Column(name = "level")
    private Integer level;
}
