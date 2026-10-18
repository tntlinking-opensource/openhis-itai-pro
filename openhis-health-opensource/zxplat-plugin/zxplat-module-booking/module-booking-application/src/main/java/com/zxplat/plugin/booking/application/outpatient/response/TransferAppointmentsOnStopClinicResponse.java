/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.outpatient.response;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 停诊转交执行结果（2.19）。
 */
@Getter
@Setter
public class TransferAppointmentsOnStopClinicResponse {

    /** 成功条数。 */
    private Integer successCount;

    /** 失败条数。 */
    private Integer failedCount;

    /** 是否已完成日程停诊。 */
    private Boolean scheduleDeactivated;

    /** 部分停诊时已封锁号源数。 */
    private Integer blockedSlotCount;

    /** 成功明细。 */
    private List<StopClinicTransferResultItemDto> successItems = new ArrayList<>();

    /** 失败明细。 */
    private List<StopClinicTransferResultItemDto> failedItems = new ArrayList<>();
}
