/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.outpatient.nursestation.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 门诊护士站处置单行 DTO（三列表字段名一致，IF-NUR-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊护士站处置单行")
public class NurseStationTreatmentLineDto {

    /**
     * 就诊主键。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键")
    private Long encounterId;

    /**
     * 请求主键。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "请求主键")
    private Long requestId;

    /**
     * 发放锚点；诊疗可空。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "发放锚点；诊疗可空")
    private Long dispenseId;

    /**
     * 请求表语义短码（SERVICE/MEDICATION/DEVICE）。
     */
    @Schema(description = "请求表语义短码")
    private String requestTable;

    /**
     * 与 requestTable 同义展示（可选）。
     */
    @Schema(description = "与 requestTable 同义展示（可选）")
    private String orderType;

    /**
     * 目录编码。
     */
    @Schema(description = "目录编码")
    private String itemCode;

    /**
     * 名称。
     */
    @Schema(description = "名称")
    private String itemName;

    /**
     * 规格。
     */
    @Schema(description = "规格")
    private String specText;

    /**
     * 拼音检索码。
     */
    @Schema(description = "拼音检索码")
    private String pyStr;

    /**
     * 五笔检索码。
     */
    @Schema(description = "五笔检索码")
    private String wbStr;

    /**
     * 诊疗类目码（仅诊疗列表有意义）。
     */
    @Schema(description = "诊疗类目码（仅诊疗列表有意义）")
    private String serviceCategoryCode;

    /**
     * 诊疗类目展示文案。
     */
    @Schema(description = "诊疗类目展示文案")
    private String serviceCategoryCodeText;

    /**
     * 耗材类目码（仅耗材列表有意义）。
     */
    @Schema(description = "耗材类目码（仅耗材列表有意义）")
    private String deviceCategoryCode;

    /**
     * 耗材类目展示文案。
     */
    @Schema(description = "耗材类目展示文案")
    private String deviceCategoryCodeText;

    /**
     * 药品类目码（仅药品列表有意义）。
     */
    @Schema(description = "药品类目码（仅药品列表有意义）")
    private String medCategoryCode;

    /**
     * 药品类目展示文案。
     */
    @Schema(description = "药品类目展示文案")
    private String medCategoryCodeText;

    /**
     * 数量。
     */
    @Schema(description = "数量")
    private BigDecimal quantity;

    /**
     * 单位编码。
     */
    @Schema(description = "单位编码")
    private String unitCode;

    /**
     * 仅诊疗：计划执行次数。
     */
    @Schema(description = "仅诊疗：计划执行次数")
    private Integer plannedPerformCount;

    /**
     * 仅药品：execute_num。
     */
    @Schema(description = "仅药品：execute_num")
    private Integer medicationExecuteNum;

    /**
     * 仅诊疗：完成次数。
     */
    @Schema(description = "仅诊疗：完成次数")
    private Integer performCount;

    /**
     * 仅诊疗：取消次数。
     */
    @Schema(description = "仅诊疗：取消次数")
    private Integer cancelCount;

    /**
     * 关联收费明细归并状态（同请求锚点多行按收费态优先级归并，含发药/发材映射）。
     */
    @Schema(description = "关联收费明细归并状态")
    private String chargeStatusEnum;

    /**
     * 关联收费明细归并状态展示文案。
     */
    @Schema(description = "关联收费明细归并状态展示文案")
    private String chargeStatusEnumText;

    /**
     * 列表「状态」列展示码：收费退费时为 REFUNDING/REFUNDED，否则由前端按执行进度展示。
     */
    @Schema(description = "列表状态列展示码（退费态）")
    private String displayStatusEnum;

    /**
     * 列表「状态」列展示文案：退费时为待退费/已退费。
     */
    @Schema(description = "列表状态列展示文案（退费态）")
    private String displayStatusEnumText;

    /**
     * 关联收费明细金额之和。
     */
    @Schema(description = "关联收费明细金额之和")
    private BigDecimal displayTotalPrice;

    /**
     * 单价（可选）。
     */
    @Schema(description = "单价（可选）")
    private BigDecimal unitPrice;

    /**
     * 发放状态持久化码。
     */
    @Schema(description = "发放状态持久化码")
    private String dispenseStatusEnum;

    /**
     * 发放状态展示文案。
     */
    @Schema(description = "发放状态展示文案")
    private String dispenseStatusEnumText;

    /**
     * 退费关联请求主键。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "退费关联请求主键")
    private Long refundPairRequestId;

    /**
     * 成组主键。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "成组主键")
    private Long groupId;

    /**
     * 排序号。
     */
    @Schema(description = "排序号")
    private Integer sortNumber;
}
