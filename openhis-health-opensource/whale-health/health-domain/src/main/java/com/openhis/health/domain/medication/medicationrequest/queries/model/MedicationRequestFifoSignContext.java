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

/**
 * 门诊医生站药品签发后 FIFO 发放编排所需的医嘱只读切片（避免应用层直连仓储加载聚合）。
 */
@Getter
@Setter
public class MedicationRequestFifoSignContext {

    /** 药品申请主键。 */
    private Long medReqId;

    /** 药品定义主键。 */
    private Long medicationId;

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 开立数量。 */
    private BigDecimal quantity;

    /** 数量单位编码。 */
    private String unitCode;

    /** 目录 categoryCode（药品 med_category_code），用于签发相位 FIFO 模板解析。 */
    private String catalogCategoryCode;

    /** 给药途径编码（模板 calcScore 维度）。 */
    private String methodCode;
}
