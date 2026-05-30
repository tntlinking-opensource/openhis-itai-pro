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
 * 医嘱详情查询模型。
 */
@Getter
@Setter
public class MedicationRequestDetailQueryModel {
    /** 主键。 */
    private Long id;
    /** 状态枚举编码。 */
    private String statusEnum;
    /** therapy枚举编码。 */
    private String therapyEnum;
    /**
     * 数量（与 {@link #unitCode} 成套；长临共用字段、语义由 {@code therapy_enum} 区分：
     * 长期=一次执行发药量，临时=开立总量）。
     */
    private BigDecimal quantity;
    /** 计划执行次数（持久化 execute_num，DM-MED-003；护士站 BR-NUR-005）。 */
    private Integer executeNum;
    /** 发药/开立单位（与 {@link #quantity} 成套；不参与剂量 {@code dose} 字段）。 */
    private String unitCode;
}

