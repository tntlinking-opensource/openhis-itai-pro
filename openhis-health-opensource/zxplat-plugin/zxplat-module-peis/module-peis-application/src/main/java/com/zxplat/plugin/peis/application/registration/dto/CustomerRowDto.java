/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.registration.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 体检客户检索行（REG-01）。
 */
@Data
public class CustomerRowDto {

    /** 患者主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 姓名。 */
    private String name;

    /** 身份证号。 */
    private String idCardNo;

    /** 性别编码。 */
    private String genderCode;

    /** 联系电话。 */
    private String phone;
}
