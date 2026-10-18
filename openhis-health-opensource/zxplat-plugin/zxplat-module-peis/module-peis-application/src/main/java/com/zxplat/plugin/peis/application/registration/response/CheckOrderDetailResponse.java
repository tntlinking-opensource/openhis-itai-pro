/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.registration.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.plugin.peis.application.registration.dto.ServiceRequestLineDto;
import lombok.Data;

import java.util.List;

/**
 * 体检单详情响应（REG-03）。
 */
@Data
public class CheckOrderDetailResponse {

    /** 就诊主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 体检流水号。 */
    private String busNo;

    /** 患者主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 套餐 ActivityDefinition 主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long packageActivityId;

    /** 服务申请行。 */
    private List<ServiceRequestLineDto> serviceRequestLines;
}
