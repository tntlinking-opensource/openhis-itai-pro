/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.miniapp.domain.session.entity;

import java.time.LocalDateTime;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 小程序会话（map_session）。
 */
@Getter
@Setter
@Entity
@Table(name = "map_session")
public class MapSession extends BaseAuditedEntity {

    @Column(name = "wechat_account_id", nullable = false)
    private Long wechatAccountId;

    @Column(name = "token_hash", nullable = false, length = 128)
    private String tokenHash;

    @Column(name = "current_patient_id")
    private Long currentPatientId;

    @Column(name = "current_practitioner_id")
    private Long currentPractitionerId;

    @Column(name = "client_type_enum", nullable = false, length = 32)
    private String clientTypeEnum = "miniapp";

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
}
