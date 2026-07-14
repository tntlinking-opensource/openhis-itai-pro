/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.inpatientbilling.request;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * IF-IP-PRC-05：已划价列表请求（BR-IP-PRC-015）。
 *
 * <p>仅 nurse-pricing 来源的护士划价 OrderInfo；可按时间窗与父医嘱 id 收窄补费子行。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "已划价列表请求")
public class ListNurseStationPricedOrdersRequest {

    /** 多选就诊主键。 */
    @NotEmpty(message = "就诊列表不能为空")
    @Schema(description = "多选就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> encounterIds;

    /** 划价时间下限（含，对应 OrderInfo.createdAt）。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 划价时间起。 */
    @Schema(description = "划价时间起")
    private LocalDateTime pricedTimeFrom;

    /** 划价时间上限（含）。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 划价时间止。 */
    @Schema(description = "划价时间止")
    private LocalDateTime pricedTimeTo;

    /** 父医嘱 requestId；仅返回 based_on 指向该父的补费划价行。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 父医嘱 id 过滤（补费子行）。 */
    @Schema(description = "父医嘱 id 过滤（补费子行）")
    private Long parentRequestId;
}
