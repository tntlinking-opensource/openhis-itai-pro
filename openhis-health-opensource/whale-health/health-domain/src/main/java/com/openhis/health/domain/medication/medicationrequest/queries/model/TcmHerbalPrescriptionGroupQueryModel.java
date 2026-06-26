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
import java.util.ArrayList;
import java.util.List;

/**
 * 就诊维度中药处方按方聚合读模型（IF-DOR-34 / IF-IP-TCM-34 读侧真源）。
 */
@Getter
@Setter
public class TcmHerbalPrescriptionGroupQueryModel {
    /** 前端方标识（自 {@code content_json} 解析，未签发草稿簇分组键）。 */
    private String prescriptionGroupKey;
    /** 处方号（已签发生效行共享）。 */
    private String prescriptionNo;
    /** 绑定的中医证候组号（自 {@code content_json} 或主表字段推导）。 */
    private String tcmSyndromeGroup;
    /** 中药付数（方头口径，取组内首行）。 */
    private BigDecimal chineseHerbsDoseQuantity;
    /** 代煎标志。 */
    private Boolean sufferingFlag;
    /** 用法编码。 */
    private String methodCode;
    /** 用药频次编码。 */
    private String rateCode;
    /** 用药天数。 */
    private Integer dispensePerDuration;
    /** 备注。 */
    private String remark;
    /** 处方类别编码。 */
    private String rxTypeCode;
    /** 治疗类型枚举编码（方头口径，取组内首行 {@code therapy_enum}）。 */
    private String therapyEnum;
    /** 方级展示状态（取组内行最高优先级状态）。 */
    private String statusEnum;
    /** 方级开立时间（取组内最早 {@code req_authored_time}）。 */
    private LocalDateTime reqAuthoredTime;
    /** 方内药品申请行（扁平行，供应用层 enrich 目录名与费用态）。 */
    private List<MedicationRequestListQueryModel> lines = new ArrayList<>();
}
