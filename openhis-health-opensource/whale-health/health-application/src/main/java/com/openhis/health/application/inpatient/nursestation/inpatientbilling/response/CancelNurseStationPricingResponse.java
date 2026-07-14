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
 * IF-IP-PRC-06：取消划价响应。
 *
 * <p>整批成功或整批回滚；补费父医嘱不变（BR-IP-PRC-027）。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "取消划价响应")
public class CancelNurseStationPricingResponse {

    /** 整批成功。 */
    @Schema(description = "整批成功")
    private Boolean success;

    /** 成功取消行数。 */
    @Schema(description = "成功取消行数")
    private Integer processedCount;

    /** 已取消诊疗请求 id。 */
    @Schema(description = "已取消诊疗请求 id")
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> cancelledServiceRequestIds = new ArrayList<>();

    /** 已取消耗材请求 id。 */
    @Schema(description = "已取消耗材请求 id")
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> cancelledDeviceRequestIds = new ArrayList<>();
}
