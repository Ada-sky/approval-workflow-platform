package com.ada.approval.persistence;

import java.time.LocalDateTime;

/** Provides the timestamp contract consumed by the shared JPA lifecycle listener. */
public interface TimestampedEntity {
    LocalDateTime getCreateTime();

    void setCreateTime(LocalDateTime createTime);

    LocalDateTime getUpdateTime();

    void setUpdateTime(LocalDateTime updateTime);
}
