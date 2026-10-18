/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.outpatient.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 停诊转交并执行停诊请求（2.19）。
 */
@Getter
@Setter
public class TransferAppointmentsOnStopClinicRequest {

    /** 科室主键。 */
    @NotNull
    private Long organizationId;

    /** 待停诊日程主键。 */
    @NotNull
    private Long scheduleId;

    /** 部分停诊号源；空表示整日。 */
    private List<Long> slotIds;

    /** 处置方式：TRANSFER / CANCEL / MANUAL。 */
    @NotNull
    private String dispositionMode;

    /** 自动匹配转交时的目标医师。 */
    private Long targetPractitionerId;

    /** 显式转交映射（来自预览表）；可空则按规则自动匹配。 */
    private List<StopClinicTransferItemRequest> transferItems;

    /** 停诊原因。 */
    private String reason;

    /** MANUAL 模式下仍执行停诊（跳过 block 门禁）。 */
    private Boolean deferManualHandling;
}
