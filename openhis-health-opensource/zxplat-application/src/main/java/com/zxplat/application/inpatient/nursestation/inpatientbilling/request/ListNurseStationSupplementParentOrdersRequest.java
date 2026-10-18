/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.inpatientbilling.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-PRC-03：医嘱补费父医嘱列表请求。
 *
 * <p>仅返回医生开立（doctor-prescription）的药/诊父医嘱；含停嘱、无执行槽位展开。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医嘱补费父医嘱列表请求")
public class ListNurseStationSupplementParentOrdersRequest {

    /** 多选就诊主键。 */
    @NotEmpty(message = "就诊列表不能为空")
    @Schema(description = "多选就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> encounterIds;

    /** 长临筛选：all/long/temp。 */
    @Schema(description = "长临筛选：all/long/temp")
    private String therapyType;

    /** 医嘱名称关键字。 */
    @Schema(description = "医嘱名称关键字")
    private String keyword;

    /** 开立科室 id（科室维度在 OrderInfo 查询已下推时，此处仅配合关键字过滤）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 开立科室筛选。 */
    @Schema(description = "开立科室筛选")
    private Long orgId;
}
