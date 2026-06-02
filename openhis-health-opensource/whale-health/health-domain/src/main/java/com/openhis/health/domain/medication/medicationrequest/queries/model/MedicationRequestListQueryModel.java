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

package com.openhis.health.domain.medication.medicationrequest.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 医嘱列表查询模型。
 */
@Getter
@Setter
public class MedicationRequestListQueryModel {
    /** 主键。 */
    private Long id;
    /** 业务号。 */
    private String busNo;
    /** 处方号。 */
    private String prescriptionNo;
    /** 状态枚举编码。 */
    private String statusEnum;
    /** 原药品申请主键（退药逆向行非空；处置单仅展示正向行）。 */
    private Long refundMedicineId;
    /** 分组主键。 */
    private Long groupId;
    /** 就诊主键。 */
    private Long encounterId;
    /** 患者主键。 */
    private Long patientId;
    /** 药品定义主键。 */
    private Long medicationId;
    /** 生成来源持久化码。 */
    private String generateSourceEnum;
    /** 记录人主键（审签/补录等，与开嘱人可能不同）。 */
    private Long recorderId;
    /**
     * 开嘱人（申请方医师）主键，与写模型 {@code med_medication_request.requester_id} 对齐；未落库时由装配层回落 {@link #recorderId}。
     */
    private Long requesterId;
    /** 开立时间。 */
    private LocalDateTime reqAuthoredTime;
    /** 医生站列表展示排序号。 */
    private Integer sortNumber;
    /** 数量。 */
    private BigDecimal quantity;
    /** 单位编码。 */
    private String unitCode;
    /** 给药途径/用法编码。 */
    private String methodCode;
    /** 用药频次编码。 */
    private String rateCode;
    /** 单次剂量。 */
    private BigDecimal dose;
    /** 剂量单位编码。 */
    private String doseUnitCode;
    /** 产品批号。 */
    private String lotNumber;
    /** 用药天数。 */
    private Integer dispensePerDuration;
    /** 发药/执行位置 id（药品侧 perform_location）。 */
    private Long performLocation;
    /** 诊断定义主键（持久化 {@code med_medication_request.condition_definition_id}）。 */
    private Long conditionDefinitionId;
    /** 备注。 */
    private String remark;
    /** 请求扩展 JSON（编辑卡片回填）。 */
    private String contentJson;
    /** 处方类别编码。 */
    private String rxTypeCode;
    /** 用药说明编码（与定义/域列对齐）。 */
    private String dosageInstruction;
    /** 滴速编码。 */
    private String speedCode;
    /** 中药付数（持久化可能为小数，列表行再取整）。 */
    private BigDecimal chineseHerbsDoseQuantity;
    /** 代煎标识。 */
    private Boolean sufferingFlag;
    /** 是否皮试。 */
    private Boolean skinTestFlag;
    /** 是否输液/注射相关（域列 infusion_flag）。 */
    private Boolean infusionFlag;
    /** 前置业务锚表名（皮试关联等）。 */
    private String basedOnTable;
    /** 前置业务主键。 */
    private Long basedOnId;
    /** 校对人（持久化 performer_check_id，BR-MRQ-007）。 */
    private Long performerCheckId;
    /** 校对时间（持久化 check_time，BR-MRQ-007）。 */
    private LocalDateTime checkTime;
    /** 用药开始时间（持久化 effective_dose_start）。 */
    private LocalDateTime effectiveDoseStart;
    /** 停嘱/结束时刻（仅 STOPPED 时写入 effective_dose_end）。 */
    private LocalDateTime effectiveDoseEnd;
    /** 是否中医相关（付数等场景）。 */
    private Boolean tcmFlag;
    /** 治疗类型枚举编码（持久化 therapy_enum，与 {@code TherapyType#getCode()} 对齐）。 */
    private String therapyEnum;
}
