/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.healthcheckup.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 团检名单 DTO（9.5）。
 */
@Getter
@Setter
public class GroupCheckupRosterDto {

    /** 团检名单主键。 */
    private Long id;

    /** 团检批次主键。 */
    private Long batchId;

    /** 患者主键。 */
    private Long patientId;

    /** 姓名。 */
    private String fullName;

    /** 身份证号。 */
    private String idCardNo;

    /** 学号。 */
    private String studentNo;

    /** 手机号。 */
    private String mobilePhone;

    /** 预约主键。 */
    private Long appointmentId;

    /** 预约状态编码。 */
    private String bookingStatusCode;
}
