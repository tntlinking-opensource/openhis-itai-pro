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
 * 停诊影响预览请求（2.18）。
 */
@Getter
@Setter
public class PreviewStopClinicImpactRequest {

    /** 科室主键。 */
    @NotNull
    private Long organizationId;

    /** 待停诊日程主键。 */
    @NotNull
    private Long scheduleId;

    /** 部分停诊时的号源主键列表；空表示整日日程下全部号源。 */
    private List<Long> slotIds;

    /** 转交预览时的目标医师主键。 */
    private Long targetPractitionerId;

    /** 预览处置方式：TRANSFER / CANCEL / MANUAL；可空则取院点策略。 */
    private String dispositionMode;
}
