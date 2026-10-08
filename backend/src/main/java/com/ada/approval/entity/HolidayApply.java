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
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Stores the application leave record and its correlation to an Activiti process instance. Workflow
 * status and record validity are separate; persisted timestamps support whole-calendar-day leave
 * dates.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "t_holiday_apply")
@DynamicInsert
@DynamicUpdate
@EntityListeners(EntityTimestampListener.class)
@Schema(name = "HolidayApply object", description = "")
public class HolidayApply implements Serializable, TimestampedEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Schema(description = "Applicant account id")
    @Column(name = "account_id")
    private Integer accountId;

    @Schema(description = "Leave request title")
    @Column(name = "title")
    private String title;

    @Schema(description = "Applicant name")
    @Column(name = "user_name")
    private String userName;

    @Schema(description = "Leave reason")
    @Column(name = "reason")
    private String reason;

    @Schema(description = "Leave duration in days")
    @Column(name = "days")
    private Integer days;

    @Schema(description = "Leave type")
    @Column(name = "holiday_type")
    private Integer holidayType;

    @Schema(description = "Submitted at")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @Column(name = "submit_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date submitTime;

    @Schema(description = "Leave request comment")
    @Column(name = "remark")
    private String remark;

    @Schema(description = "Leave request status")
    @Column(name = "status")
    private Integer status;

    @Schema(description = "Approval status")
    @Column(name = "approval_status")
    private Integer approvalStatus;

    @Schema(description = "Process instance id")
    @Column(name = "process_instance_id")
    private String processInstanceId;

    @Schema(description = "Leave start date")
    @Column(name = "start_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date startTime;

    @Schema(description = "Leave end date")
    @Column(name = "end_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date endTime;

    @Schema(description = "Created at")
    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Schema(description = "Updated at")
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Schema(description = "Validity flag")
    @Column(name = "is_valid")
    private Integer isValid;

    @Column(name = "applicant_archived", nullable = false)
    private boolean applicantArchived;

    @Schema(description = "Leave date range string")
    @Transient
    private String time;
}
