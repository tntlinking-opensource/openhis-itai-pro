/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.inpatientbilling.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * IF-IP-PRC-02：直接划价确认响应。
 *
 * <p>整批成功或整批回滚；id 列表含套餐展开产生的子诊疗请求。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "直接划价确认响应")
public class ConfirmDirectNurseStationPricingResponse {

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
