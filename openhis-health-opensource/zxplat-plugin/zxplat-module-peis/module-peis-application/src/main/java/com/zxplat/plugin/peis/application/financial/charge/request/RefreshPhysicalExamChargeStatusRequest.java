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

/**
 * 刷新体检收费态请求（IF-PE-CHG-07）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "刷新体检收费态请求")
public class RefreshPhysicalExamChargeStatusRequest {

    /** 体检就诊主键。 */
    @Schema(description = "体检就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;
}
