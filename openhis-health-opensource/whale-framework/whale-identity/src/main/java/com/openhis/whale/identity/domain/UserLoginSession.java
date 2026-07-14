/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户登录后的服务端会话：租户 / 医院 / 科室选择（跨重启持久化）。
 */
@Entity
@Table(name = "whale_user_login_session")
@Getter
@Setter
public class UserLoginSession {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "hospital_organization_id")
    private Long hospitalOrganizationId;

    @Column(name = "organization_unit_id")
    private Long organizationUnitId;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
