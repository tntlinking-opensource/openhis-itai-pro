/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.domain.invoke.support;

import com.zxplat.framework.core.infrastructure.id.SnowflakeId;
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
 * einv_boss 日志类表基类（无 is_deleted / updated_at）。
 */
@MappedSuperclass
@Getter
@Setter
public abstract class EinvBossLogRowEntity implements Persistable<Long> {

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

    @Override
    public Long getId() {
        return id;
    }

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
    }
}
