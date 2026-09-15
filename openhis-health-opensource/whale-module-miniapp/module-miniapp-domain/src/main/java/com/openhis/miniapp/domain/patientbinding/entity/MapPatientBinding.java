/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.miniapp.domain.patientbinding.entity;

import java.time.LocalDateTime;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 微信账号 ↔ 就诊人绑定（map_patient_binding）。
 */
@Getter
@Setter
@Entity
@Table(name = "map_patient_binding")
public class MapPatientBinding extends BaseAuditedEntity {

    @Column(name = "wechat_account_id", nullable = false)
    private Long wechatAccountId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "bind_channel_enum", nullable = false, length = 32)
    private String bindChannelEnum;

    @Column(name = "campus_ref_type", length = 32)
    private String campusRefType;

    @Column(name = "campus_ref_id")
    private Long campusRefId;

    @Column(name = "campus_biz_no", length = 64)
    private String campusBizNo;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = Boolean.FALSE;

    @Column(name = "bound_at", nullable = false)
    private LocalDateTime boundAt;

    @Column(name = "unbound_at")
    private LocalDateTime unboundAt;
}
