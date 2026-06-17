package com.openhis.whale.booking.domain.policy.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
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

    @Column(name = "organization_id")
    private Long organizationId;

    @Column(name = "policy_code", nullable = false)
    private String policyCode;

    @Column(name = "policy_name", nullable = false)
    private String policyName;

    @Column(name = "advance_days", nullable = false)
    private Integer advanceDays = 7;

    @Column(name = "slot_minutes", nullable = false)
    private Integer slotMinutes = 30;

    @Column(name = "capacity_per_slot", nullable = false)
    private Integer capacityPerSlot = 10;

    @Column(name = "channel_quota_json")
    private String channelQuotaJson;
}
