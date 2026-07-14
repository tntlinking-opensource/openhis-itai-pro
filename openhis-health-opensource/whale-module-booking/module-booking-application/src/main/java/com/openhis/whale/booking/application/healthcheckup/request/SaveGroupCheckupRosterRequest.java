/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存团检名单请求（9.5）。
 */
@Getter
@Setter
public class SaveGroupCheckupRosterRequest {

    /** 团检名单主键（更新时传入）。 */
    private Long id;

    /** 团检批次主键。 */
    @NotNull
    private Long batchId;

    /** 患者主键。 */
    private Long patientId;

    /** 姓名。 */
    @NotBlank
    private String fullName;

    /** 身份证号。 */
    private String idCardNo;

    /** 学号。 */
    private String studentNo;

    /** 手机号。 */
    private String mobilePhone;
}
