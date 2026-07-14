/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.support;

import com.openhis.whale.core.infrastructure.id.SnowflakeId;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

/**
 * crb_* 追加写日志表基类（无 updated_at，对齐 {@code crb_emr_push_log} 等只 insert 的表结构）。
 */
@MappedSuperclass
@Getter
@Setter
public abstract class CrbLogRowEntity implements Persistable<Long> {

    @Id
    @SnowflakeId
    @Column(name = "id", nullable = false)
    private Long id;

    @Transient
    private boolean newEntity = true;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Override
    public boolean isNew() {
        return newEntity || id == null;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.newEntity = false;
    }

    @PrePersist
    void onPrePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.isDeleted == null) {
            this.isDeleted = false;
        }
    }
}
