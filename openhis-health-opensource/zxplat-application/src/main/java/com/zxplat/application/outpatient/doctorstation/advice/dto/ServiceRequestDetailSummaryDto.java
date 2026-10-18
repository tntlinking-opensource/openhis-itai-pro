/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.advice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 服务申请明细摘要（部位/样本）。
 *
 * <p>供申请单列表、医嘱统一列表（IF-DOR-10）及 LIS/PACS 外发（BR-REQ-008）复用。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "服务申请明细摘要")
public class ServiceRequestDetailSummaryDto {

    /** 目标表名。 */
    @Schema(description = "目标表名")
    private String targetTable;

    /** 目标主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "目标主键")
    private Long targetId;

    /** 目标展示名。 */
    @Schema(description = "目标展示名")
    private String targetDisplayName;

    /** 与该目标同行的补充说明。 */
    @Schema(description = "与该目标同行的补充说明")
    private String remark;
}
