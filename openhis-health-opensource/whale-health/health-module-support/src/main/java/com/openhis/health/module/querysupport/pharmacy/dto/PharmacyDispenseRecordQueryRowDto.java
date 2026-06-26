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

package com.openhis.health.module.querysupport.pharmacy.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 发退药记录平铺行（querysupport 中性 DTO；应用层映射为 {@code PharmacyDispenseRecordRowDto}）。
 */
@Getter
@Setter
@Accessors(chain = true)
public class PharmacyDispenseRecordQueryRowDto {

    /** 发放单主键。 */
    private Long dispenseId;

    /** 记录类型 code：{@code DISPENSE} / {@code REFUND}。 */
    private String recordTypeCode;

    /** 记录类型展示文本。 */
    private String recordTypeText;

    /** 发药时间展示（yyyy-MM-dd HH:mm:ss）。 */
    private String dispenseTime;

    /** 药房库位主键。 */
    private Long pharmacyLocationId;

    /** 药房名称。 */
    private String pharmacyLocationName;

    /** 发药人主键。 */
    private Long dispenserPractitionerId;

    /** 发药人姓名。 */
    private String dispenserName;

    /** 患者主键。 */
    private Long patientId;

    /** 患者姓名。 */
    private String patientName;

    /** 就诊主键。 */
    private Long encounterId;

    /** 门诊号 / 就诊业务号。 */
    private String encounterBusNo;

    /** 处方号。 */
    private String prescriptionNo;

    /** 药品定义主键。 */
    private Long medicationId;

    /** 药品编码。 */
    private String medicationCode;

    /** 药品名称。 */
    private String medicationName;

    /** 规格展示。 */
    private String medicationSpec;

    /** 数量（绝对值）。 */
    private BigDecimal quantity;

    /** 单位 code。 */
    private String unitCode;

    /** 单位展示。 */
    private String unitText;

    /** 单价；无 charge 为空。 */
    private BigDecimal unitPrice;

    /** 金额；退药为负；无 charge 为空。 */
    private BigDecimal totalAmount;

    /** 批号。 */
    private String lotNumber;

    /** 追溯码。 */
    private String traceNo;

    /** 开单科室主键。 */
    private Long orderingDepartmentId;

    /** 开单科室名称。 */
    private String orderingDepartmentName;

    /** 用法用量摘要。 */
    private String usageText;

    /** 汇总单号。 */
    private String summaryNo;

    /** 药嘱主键。 */
    private Long medReqId;
}
