/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.policy.entity;

import com.zxplat.plugin.booking.domain.share.StopClinicConstants;
import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 停诊与预约处置院点策略（bkg_schedule_change_policy，07 §4.2）。
 */
@Getter
@Setter
@Entity
@Table(name = "bkg_schedule_change_policy")
public class ScheduleChangePolicy extends BaseAuditedEntity {

    /** 科室主键；空表示尚未落库的全院默认占位。 */
    @Column(name = "organization_id")
    private Long organizationId;

    /** 停诊默认处置：TRANSFER / RESCHEDULE / CANCEL / MANUAL。 */
    @Column(name = "on_practitioner_stop_mode", nullable = false)
    private String onPractitionerStopMode = StopClinicConstants.STOP_MODE_MANUAL;

    /** 转交匹配规则。 */
    @Column(name = "transfer_match_rule", nullable = false)
    private String transferMatchRule = StopClinicConstants.MATCH_SAME_SERVICE_SAME_SLOT;

    /** 转交是否要求同号源类型。 */
    @Column(name = "transfer_require_same_service", nullable = false)
    private Boolean transferRequireSameService = Boolean.TRUE;

    /** 是否允许跨医师转交。 */
    @Column(name = "transfer_allow_cross_practitioner", nullable = false)
    private Boolean transferAllowCrossPractitioner = Boolean.TRUE;

    /** 转交患者确认模式：NONE / SMS_CONFIRM / WINDOW_CONFIRM。 */
    @Column(name = "transfer_patient_confirm_mode", nullable = false)
    private String transferPatientConfirmMode = StopClinicConstants.CONFIRM_NONE;

    /** 未处置预约时禁止完成停诊。 */
    @Column(name = "block_deactivate_if_unhandled", nullable = false)
    private Boolean blockDeactivateIfUnhandled = Boolean.TRUE;

    /** 转交后是否通知患者。 */
    @Column(name = "notify_on_transfer", nullable = false)
    private Boolean notifyOnTransfer = Boolean.TRUE;

    /** 停诊取消预约时是否通知。 */
    @Column(name = "notify_on_cancel_stop", nullable = false)
    private Boolean notifyOnCancelStop = Boolean.TRUE;

    /** 预览后冻结源 Slot 新预约。 */
    @Column(name = "freeze_source_slot_on_preview", nullable = false)
    private Boolean freezeSourceSlotOnPreview = Boolean.TRUE;

    /** 冻结超时分钟数。 */
    @Column(name = "freeze_ttl_minutes", nullable = false)
    private Integer freezeTtlMinutes = 15;

    /**
     * 院点无配置时的内存默认策略。
     *
     * @param organizationId 科室主键
     * @return 默认策略快照
     */
    public static ScheduleChangePolicy defaultForOrganization(Long organizationId) {
        ScheduleChangePolicy policy = new ScheduleChangePolicy();
        policy.setOrganizationId(organizationId);
        return policy;
    }
}
