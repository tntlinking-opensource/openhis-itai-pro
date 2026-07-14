/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.medtech.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 可预约医嘱行 DTO。
 */
@Getter
@Setter
public class ServiceRequestItemDto {

    /** 医嘱主键。 */
    private Long serviceRequestId;

    /** 医嘱业务流水号。 */
    private String serviceRequestBusNo;

    /** 检查项目名称。 */
    private String activityName;

    /** 状态展示文案。 */
    private String statusView;

    /** 是否已预约。 */
    private Boolean bookedFlag;

    /** 关联预约主键。 */
    private Long appointmentId;
}
