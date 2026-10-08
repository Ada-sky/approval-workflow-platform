package com.ada.approval.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import com.ada.approval.persistence.EntityTimestampListener;

import java.io.Serializable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Stores scalar account-to-role grants. accountId references Account.id, even when an
 * administration operation starts from an employee ID.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "t_account_role")
@DynamicInsert
@DynamicUpdate
@EntityListeners(EntityTimestampListener.class)
@Schema(name = "AccountRole object", description = "")
public class AccountRole implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Schema(description = "Account id")
    @Column(name = "account_id")
    private Integer accountId;

    @Schema(description = "Role id")
    @Column(name = "role_id")
    private Integer roleId;
}
