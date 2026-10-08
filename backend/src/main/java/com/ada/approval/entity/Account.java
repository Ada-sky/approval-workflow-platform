package com.ada.approval.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import com.ada.approval.persistence.EntityTimestampListener;
import com.ada.approval.persistence.TimestampedEntity;

import java.time.LocalDateTime;
import java.io.Serializable;
import java.util.Collection;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Stores a login account independently of its optional employee record. empId is an Employee ID,
 * not the account ID. Password hashes are excluded from JSON; authorities are transient.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "t_account")
@DynamicInsert
@DynamicUpdate
@EntityListeners(EntityTimestampListener.class)
@Schema(name = "Account object", description = "")
public class Account implements Serializable, TimestampedEntity, UserDetails {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Schema(description = "Username")
    @Column(name = "user_name")
    private String userName;

    @Schema(description = "Login password")
    @Column(name = "password")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String password;

    @Schema(description = "Created at")
    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Schema(description = "Updated at")
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Schema(description = "Employee id")
    @Column(name = "emp_id")
    private Integer empId;

    @Schema(description = "0-Disabled  1-Enabled")
    @Column(name = "status")
    private Integer status;

    @Schema(description = "Permission")
    @Transient
    private Collection<? extends GrantedAuthority> grantedAuthority;

    // Permissions are assigned separately
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return this.grantedAuthority; // Granted user permissions
    }

    @Override
    public String getUsername() {
        return this.userName; // Get username
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; // Account is not expired
    }

    @Override
    public boolean isAccountNonLocked() {
        return true; // Account is not locked
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true; // Credentials are not expired
    }

    @Override
    public boolean isEnabled() {
        return Integer.valueOf(1).equals(this.status); // 0-Disabled  1-Enabled
    }
}
