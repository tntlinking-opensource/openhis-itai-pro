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

package com.openhis.health.domain.medication.medicationrequest.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 修改中药医嘱草稿命令（null 字段表示不变更，与 {@link ChangeMedicationOrderDraftCommand} 分轨）。
 */
@Getter
@Setter
public class ChangeHerbalMedicationOrderDraftCommand {
    /** 操作人标识。 */
    private Long operatorId;
    /** 药品申请标识。 */
    private Long id;
    /** 饮片药品定义标识。 */
    private Long medicationId;
    /** 中药付数。 */
    private BigDecimal chineseHerbsDoseQuantity;
    /** 代煎标志。 */
    private Boolean sufferingFlag;
    /** 请求数量（单剂 × 付数）。 */
    private BigDecimal quantity;
    /** 数量单位编码。 */
    private String unitCode;
    /** 单次剂量。 */
    private BigDecimal dose;
    /** 剂量单位编码。 */
    private String doseUnitCode;
    /** 用法编码。 */
    private String methodCode;
    /** 频次编码。 */
    private String rateCode;
    /** 用药天数。 */
    private Integer dispensePerDuration;
    /** 中药扩展内容。 */
    private String contentJson;
    /** 用药说明。 */
    private String dosageInstruction;
    /** 备注。 */
    private String remark;
    /** 绑定诊断定义主键。 */
    private Long conditionDefinitionId;
    /** 绑定就诊诊断关联主键。 */
    private Long encounterDiagnosisId;
    /** 费用性质/合同覆盖。 */
    private Long contractId;
    /** 开立机构主键。 */
    private Long orgId;
    /** 发放药房库位主键。 */
    private Long performLocation;
    /** 医生站列表排序号。 */
    private Integer sortNumber;
    /** 处方类别编码。 */
    private String rxTypeCode;
    /** 处方号。 */
    private String prescriptionNo;
}
