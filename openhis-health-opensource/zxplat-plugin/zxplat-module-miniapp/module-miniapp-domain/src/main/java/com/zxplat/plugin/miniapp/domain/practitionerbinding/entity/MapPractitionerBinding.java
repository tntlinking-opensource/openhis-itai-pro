/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.miniapp.domain.practitionerbinding.entity;

import java.time.LocalDateTime;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 微信账号 ↔ 医疗参与者绑定（map_practitioner_binding）。
 */
@Getter
@Setter
@Entity
@Table(name = "map_practitioner_binding")
public class MapPractitionerBinding extends BaseAuditedEntity {

    @Column(name = "wechat_account_id", nullable = false)
    private Long wechatAccountId;

    @Column(name = "practitioner_id", nullable = false)
    private Long practitionerId;

    @Column(name = "bind_channel_enum", nullable = false, length = 32)
    private String bindChannelEnum;

    @Column(name = "employee_no", length = 64)
    private String employeeNo;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = Boolean.FALSE;

    @Column(name = "bound_at", nullable = false)
    private LocalDateTime boundAt;

    @Column(name = "unbound_at")
    private LocalDateTime unboundAt;
}
