package com.ada.approval.persistence;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.LocalDateTime;

/**
 * Maintains application timestamps through JPA lifecycle callbacks. Insert fills only missing
 * timestamps; update always refreshes updateTime and leaves createTime unchanged.
 */
public class EntityTimestampListener {
    @PrePersist
    public void beforeInsert(Object entity) {
        if (entity instanceof TimestampedEntity timestamped) {
            LocalDateTime now = LocalDateTime.now();
            if (timestamped.getCreateTime() == null) timestamped.setCreateTime(now);
            if (timestamped.getUpdateTime() == null) timestamped.setUpdateTime(now);
        }
    }

    @PreUpdate
    public void beforeUpdate(Object entity) {
        if (entity instanceof TimestampedEntity timestamped) {
            timestamped.setUpdateTime(LocalDateTime.now());
        }
    }
}
