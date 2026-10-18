/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.pharmacy.dailysettlement.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 住院药房日结页初始化请求（IF-IP-PDC-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院药房日结页初始化请求")
public class LoadInpatientPharmacyDailySettlementPageInitRequest {

    /** 可选科室；有值时药房下拉收窄为该科室下药房。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "可选科室主键")
    private Long orgId;
}
