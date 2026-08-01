/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.registration.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 创建个检登记响应（REG-02）。
 */
@Data
public class CreateRegistrationResponse {

    /** 就诊主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 体检流水号。 */
    private String busNo;

    /** 服务申请条数。 */
    private Integer serviceRequestCount;
}
