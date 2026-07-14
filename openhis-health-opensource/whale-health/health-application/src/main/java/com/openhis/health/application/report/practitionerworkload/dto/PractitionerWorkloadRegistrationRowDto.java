/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.practitionerworkload.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 工作量汇总挂号号源行 DTO（IF-RPT-WKL-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "工作量汇总挂号号源行")
public class PractitionerWorkloadRegistrationRowDto {

    /** 号源类型 id。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "号源类型 id")
    private Long serviceTypeId;

    /** 号源类型名称。 */
    @Schema(description = "号源类型名称")
    private String serviceTypeName;

    /** 净挂号收费笔数。 */
    @Schema(description = "净挂号收费笔数")
    private Integer netChargeCount;
}
