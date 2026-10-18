/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.policy.entity;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 放号策略（bkg_release_policy）。
 */
@Getter
@Setter
@Entity
@Table(name = "bkg_release_policy")
public class ReleasePolicy extends BaseAuditedEntity {

    /** 机构标识；空=租户默认。 */
    @Column(name = "organization_id")
    private Long organizationId;

    /** 策略编码（机构内唯一）。 */
    @Column(name = "policy_code", nullable = false)
    private String policyCode;

    /** 策略名称。 */
    @Column(name = "policy_name", nullable = false)
    private String policyName;

    /** 可预约提前天数。 */
    @Column(name = "advance_days", nullable = false)
    private Integer advanceDays = 7;

    /** 默认时段粒度（分钟）。 */
    @Column(name = "slot_minutes", nullable = false)
    private Integer slotMinutes = 30;

    /** 默认每时段容量。 */
    @Column(name = "capacity_per_slot", nullable = false)
    private Integer capacityPerSlot = 10;

    /** 渠道配额 JSON，如 {"WINDOW":60,"APP":40}。 */
    @Column(name = "channel_quota_json")
    private String channelQuotaJson;
}
