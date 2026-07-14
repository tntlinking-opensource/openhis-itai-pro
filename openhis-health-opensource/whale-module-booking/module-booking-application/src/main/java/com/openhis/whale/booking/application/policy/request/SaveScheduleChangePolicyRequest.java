/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.policy.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存停诊变更策略请求（2.20）。
 */
@Getter
@Setter
public class SaveScheduleChangePolicyRequest {

    /** 主键（更新时）。 */
    private Long id;

    /** 科室/机构主键。 */
    @NotNull
    private Long organizationId;

    /** 停诊默认处置：TRANSFER / RESCHEDULE / CANCEL / MANUAL。 */
    @NotNull
    private String onPractitionerStopMode;

    /** 转交匹配规则。 */
    @NotNull
    private String transferMatchRule;

    /** 转交是否要求同号源类型。 */
    @NotNull
    private Boolean transferRequireSameService;

    /** 是否允许跨医师转交。 */
    @NotNull
    private Boolean transferAllowCrossPractitioner;

    /** 转交患者确认模式：NONE / SMS_CONFIRM / WINDOW_CONFIRM。 */
    @NotNull
    private String transferPatientConfirmMode;

    /** 未处置预约时禁止完成停诊。 */
    @NotNull
    private Boolean blockDeactivateIfUnhandled;

    /** 转交后是否通知患者。 */
    @NotNull
    private Boolean notifyOnTransfer;

    /** 停诊取消预约时是否通知。 */
    @NotNull
    private Boolean notifyOnCancelStop;

    /** 预览后冻结源 Slot 新预约。 */
    @NotNull
    private Boolean freezeSourceSlotOnPreview;

    /** 冻结超时分钟数。 */
    @NotNull
    private Integer freezeTtlMinutes;
}
