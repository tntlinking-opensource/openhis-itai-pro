/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.inpatientbilling.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * IF-IP-PRC-04：医嘱补费确认响应。
 *
 * <p>字段语义与 {@link ConfirmDirectNurseStationPricingResponse} 相同；写路径 based_on 已写入父医嘱。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医嘱补费确认响应")
public class ConfirmSupplementNurseStationPricingResponse {

    @Schema(description = "整批成功")
    /** 整批成功。 */
    private Boolean success;

    @Schema(description = "成功行数")
    /** 成功行数。 */
    private Integer processedCount;

    /** 新建诊疗请求 id。 */
    @Schema(description = "新建诊疗请求 id")
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> createdServiceRequestIds = new ArrayList<>();

    /** 新建耗材请求 id。 */
    @Schema(description = "新建耗材请求 id")
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> createdDeviceRequestIds = new ArrayList<>();
}
