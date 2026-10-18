/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * 带行级 {@code tenant_id} 的审计基类，用于业务实体多租户隔离。
 *
 * @see AuditedEntityBase 无租户列（如 {@code zxplat_users}）
 */
@MappedSuperclass
@Getter
@Setter
public abstract class BaseAuditedEntity extends AuditedEntityBase {

    @Column(name = "tenant_id")
    private Long tenantId;
}
