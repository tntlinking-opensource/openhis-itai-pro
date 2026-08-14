/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.costdetail.request;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * IF-IP-CDT-02：查询住院费用明细/汇总请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院护士站费用明细查询请求", requiredProperties = {
    "encounterId", "patientId", "dateFrom", "dateTo", "presentationMode", "statusEnum"
})
public class QueryNurseStationInpatientCostDetailRequest {

    /** 当前选中就诊（BR-IP-CDT-014 单就诊）。 */
    @NotNull(message = "就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 就诊标识。 */
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 左侧卡片患者 id，与就诊读模型交叉校验（BR-IP-CDT-001）。 */
    @NotNull(message = "患者标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 患者标识。 */
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /** 业务日期下界（闭区间，BR-IP-CDT-002/005）。 */
    @NotNull(message = "查询开始日期不能为空")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    /** 业务日期下界。 */
    @Schema(description = "业务日期下界", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate dateFrom;

    /** 业务日期上界（闭区间；须 dateFrom ≤ dateTo）。 */
    @NotNull(message = "查询结束日期不能为空")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    /** 业务日期上界。 */
    @Schema(description = "业务日期上界", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate dateTo;

    /** 展示模式：DETAIL 明细 / SUMMARY 汇总。 */
    @NotNull(message = "展示模式不能为空")
    /** 展示模式：DETAIL 或 SUMMARY。 */
    @Schema(description = "展示模式：DETAIL 或 SUMMARY", requiredMode = Schema.RequiredMode.REQUIRED)
    private String presentationMode;

    /** 费用状态筛选：PENDING_SETTLEMENT 待结算 / SETTLED 已结算（默认待结算）。 */
    @NotNull(message = "费用状态筛选不能为空")
    /** 费用状态筛选 code。 */
    @Schema(description = "费用状态筛选：PENDING_SETTLEMENT 或 SETTLED", requiredMode = Schema.RequiredMode.REQUIRED)
    private String statusEnum;

    /**
     * 项目关键字（可选）：对展示用产品名称、院内编码（productBusNo）做包含匹配（忽略大小写）；
     * 命中任一即可；空白表示不过滤（BR-IP-CDT-019）。
     */
    @Schema(description = "项目关键字：名称或院内编码模糊（可选）")
    private String keyword;

    /**
     * 费用清单打印：成对剔除冲销留痕（原正向行 + refund_id 冲销行均不进入结果；
     * 冲销行可在查询日期窗外，仍剔除窗内已被冲销的原行）。页面 Tab 不传，保持留痕可见。
     */
    @Schema(description = "是否成对剔除冲销留痕（费用清单打印用；默认 false）")
    private Boolean excludeOffsetPairs;
}
