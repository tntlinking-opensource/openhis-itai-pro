/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.insurance.inpatient.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
/** 启动住院医保结算协同单响应。 */
@Schema(description = "启动住院医保结算协同单响应")
public class StartIpInsuranceSettleResponse {

    /** 支付草稿主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentId;

    /** 支付状态 code（启动后为 SETTLING）。 */
    private String paymentStatus;

    /** 结算模式（INSURANCE_ASYNC）。 */
    private String settlementMode;

    /** 创建的协同单 id 列表。 */
    private List<Long> coordinationIds;
}
