/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.medtech.result;

import lombok.Getter;
import lombok.Setter;

/**
 * 可预约医技医嘱摘要。
 */
@Getter
@Setter
public class BookableServiceRequestDto {

    /** 医嘱主键。 */
    private Long serviceRequestId;

    /** 医嘱业务编号。 */
    private String serviceRequestBusNo;

    /** 医嘱项目名称。 */
    private String activityName;

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 收费/项目主键。 */
    private Long activityId;

    /** 医嘱类别枚举 code。 */
    private String categoryEnum;

    /** 医嘱状态枚举 code。 */
    private String statusEnum;

    /** 申请原因/备注。 */
    private String reasonText;
}
