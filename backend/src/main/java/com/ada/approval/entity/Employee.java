package com.ada.approval.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import com.ada.approval.persistence.EntityTimestampListener;
import com.ada.approval.persistence.TimestampedEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.io.Serializable;
import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Stores employee profile and scalar department, title and employment-status references. The
 * record-validity flag is separate from the employment-status reference and does not implement
 * deactivation policy.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "t_employee")
@DynamicInsert
@DynamicUpdate
@EntityListeners(EntityTimestampListener.class)
@Schema(name = "Employee object", description = "")
public class Employee implements Serializable, TimestampedEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Employee id")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Schema(description = "Employee number")
    @Column(name = "emp_num")
    private String empNum;

    @Schema(description = "Employee name")
    @Column(name = "emp_name")
    private String empName;

    @Schema(description = "Gender")
    @Column(name = "gender")
    private String gender;

    @Schema(description = "Date of birth")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @Column(name = "birthday")
    @Temporal(TemporalType.DATE)
    private Date birthday;

    @Schema(description = "Work location")
    @Column(name = "location")
    private String location;

    @Schema(description = "Employment date")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @Column(name = "on_board_date")
    @Temporal(TemporalType.DATE)
    private Date onBoardDate;

    @Schema(description = "Mobile number")
    @Column(name = "mobile")
    private String mobile;

    @Schema(description = "QQ ID")
    @Column(name = "qq")
    private String qq;

    @Schema(description = "Email")
    @Column(name = "email")
    private String email;

    @Schema(description = "WeChat ID")
    @Column(name = "weixin")
    private String weixin;

    @Schema(description = "Department number")
    @Column(name = "dept_id")
    private Integer deptId;

    @Schema(description = "Job title category ID")
    @Column(name = "title_category_id")
    private Integer titleCategoryId;

    @Schema(description = "Job title number")
    @Column(name = "title_id")
    private Integer titleId;

    @Schema(description = "Employee status id")
    @Column(name = "employ_status_id")
    private Integer employStatusId;

    @Schema(description = "Graduating institution")
    @Column(name = "graduate_school")
    private String graduateSchool;

    @Schema(description = "Education")
    @Column(name = "education")
    private String education;

    @Schema(description = "0:Probationary;1:Confirmed")
    @Column(name = "formal_status")
    private String formalStatus;

    @Schema(description = "0:Delete;1:Active")
    @Column(name = "status")
    private Integer status;

    @Schema(description = "Created at")
    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Schema(description = "Updated at")
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
