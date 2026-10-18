/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.financial.charge.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 体检退费预览请求（IF-PE-CHG-08）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "体检退费预览请求")
public class PreviewPhysicalExamRefundRequest {

    /** 体检就诊主键。 */
    @Schema(description = "体检就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 拟退收费明细主键；服务端按原支付整单展开。 */
    @Schema(description = "拟退收费明细主键")
    private List<Long> chargeItemIds;
}
