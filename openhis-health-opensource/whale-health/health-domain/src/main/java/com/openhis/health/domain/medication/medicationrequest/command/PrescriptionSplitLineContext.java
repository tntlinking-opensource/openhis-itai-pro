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
 * 单条药品申请参与分方时的只读快照（由应用层装配，领域层不反查外域）。
 */
@Getter
@Setter
public class PrescriptionSplitLineContext {

    /** 药品申请主键。 */
    private Long requestId;

    /** 药品定义主键（品种计数用）。 */
    private Long medicationId;

    /**
     * 医保医疗类别（{@code adm_encounter_diagnosis.med_type}，{@link com.openhis.health.domain.share.enums.YbMedType#getCode()}）。
     */
    private String medType;

    /** 药品目录特殊管理分类（{@code part_attribute_enum}）。 */
    private String partAttributeEnum;

    /** 是否中药饮片方。 */
    private Boolean tcmFlag;

    /** 输液成组号；同组必须落入同一处方。 */
    private Long groupId;

    /** 签发 FIFO 解析出的主发放库位。 */
    private Long pharmacyLocationId;

    /** 本行账单金额合计（{@code ChargeItem.totalPrice}）。 */
    private BigDecimal chargeTotalPrice;
}
