/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.inoutdetail.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 按入院科室加载病区选项请求（IF-RPT-IOE-01a）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "按入院科室加载病区选项请求")
public class ListInpatientInOutDetailWardOptionsRequest {

    /** 入院科室主键；空则返回空病区列表。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "入院科室主键")
    private Long organizationId;
}
