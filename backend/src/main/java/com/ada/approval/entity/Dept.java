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
 * Stores the department hierarchy with scalar parent and manager IDs. managerId references an
 * employee, not a login account; no bidirectional entity graph is introduced.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "t_dept")
@DynamicInsert
@DynamicUpdate
@EntityListeners(EntityTimestampListener.class)
@Schema(name = "Dept object", description = "")
public class Dept implements Serializable, TimestampedEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Department id")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Schema(description = "Department number")
    @Column(name = "dept_num")
    private String deptNum;

    @Schema(description = "Department name")
    @Column(name = "dept_name")
    private String deptName;

    @Schema(description = "Parent department id")
    @Column(name = "parent_id")
    private Integer parentId;

    @Schema(description = "Department level")
    @Column(name = "level")
    private Integer level;

    @Schema(description = "Department Manager")
    @Column(name = "manager_id")
    private Integer managerId;

    @Schema(description = "Created at")
    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Schema(description = "Updated at")
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Schema(description = "0: Delete   1 : Enabled   2: Disabled")
    @Column(name = "status")
    private Integer status;

    @Schema(description = "Department Manager name")
    @Transient
    private String manager;

    @Schema(description = "Parent department name")
    @Transient
    private String parentDeptName;
}
