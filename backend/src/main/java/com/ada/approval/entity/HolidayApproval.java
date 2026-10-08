package com.ada.approval.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import com.ada.approval.persistence.EntityTimestampListener;
import com.ada.approval.persistence.TimestampedEntity;

import java.time.LocalDateTime;
import java.io.Serializable;
import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Stores actual approval or withdrawal audit decisions with account and task/process correlation.
 * Audit records remain when rejection or withdrawal terminates runtime execution.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "t_holiday_approval")
@DynamicInsert
@DynamicUpdate
@EntityListeners(EntityTimestampListener.class)
@Schema(name = "HolidayApproval object", description = "")
public class HolidayApproval implements Serializable, TimestampedEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Schema(description = "Process instance id")
    @Column(name = "process_instance_id")
    private String processInstanceId;

    @Schema(description = "Task id")
    @Column(name = "task_id")
    private String taskId;

    @Schema(description = "Approver id")
    @Column(name = "user_id")
    private Integer userId;

    @Schema(description = "Approval result(1-Approved 2-Rejected)")
    @Column(name = "result")
    private Integer result;

    @Schema(description = "Approval comment")
    @Column(name = "remark")
    private String remark;

    @Schema(description = "Approver")
    @Column(name = "user_name")
    private String userName;

    @Schema(description = "Task definition key")
    @Column(name = "task_def_key")
    private String taskDefKey;

    @Schema(description = "Created at")
    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Schema(description = "Updated at")
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
