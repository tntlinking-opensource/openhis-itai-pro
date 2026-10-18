/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.medtech.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 可预约医嘱行 DTO。
 */
@Getter
@Setter
public class BookableServiceRequestItemDto {

    /** 医嘱主键。 */
    private Long serviceRequestId;

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 活动主键。 */
    private Long activityId;

    /** 类别枚举。 */
    private String categoryEnum;

    /** 状态枚举。 */
    private String statusEnum;

    /** 医嘱原因说明。 */
    private String reasonText;

    /** 是否已预约。 */
    private Boolean bookedFlag;

    /** 关联预约主键。 */
    private Long appointmentId;
}
