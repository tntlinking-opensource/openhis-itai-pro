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

package com.openhis.health.module.querysupport.product.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 产品定义读侧展示 DTO（由主表批量加载，替代 {@code ProductDefinitionReadModel} 用于应用层装配）。
 */
@Getter
@Setter
public class ProductDefinitionDisplayDto {

    private String productTableName;
    private Long productDefinitionId;

    private String productBusNo;
    private String productName;
    private String merchandiseName;

    private String categoryCode;
    private String categoryCodeDictText;

    private String purchaseUnitCode;
    private String purchaseUnitCodeDictText;
    private BigDecimal purchaseUnitRatio;

    private String splitUnitCode;
    private String splitUnitCodeDictText;

    private String packageUnitCode;
    private String packageUnitCodeDictText;
    private BigDecimal packageUnitRatio;

    private String stockUnitCode;
    private String stockUnitCodeDictText;
    private BigDecimal stockUnitRatio;

    private String doseUnitCode;
    private String doseUnitCodeDictText;
    private BigDecimal doseUnitRatio;

    private String defEncounterUnitCode;
    private String defEncounterUnitCodeDictText;
    private String defInhospitalUnitCode;

    private String totalVolume;
    private BigDecimal price;

    private String manufacturer;
    private Long locationId;
    private Long supplierId;
    private String statusEnum;
    private Boolean activeFlag;
    private Boolean rxFlag;
    private String ybNo;

    /** 批准文号/国药准字（药品定义 {@code approval_number}）。 */
    private String approvalNumber;

    private String pyStr;
    private String wbStr;

    private Integer chrgitmLv;

    /** 医保等级展示名（术语 {@code CHRGITM_LV}，由应用层按 {@link #chrgitmLv} 回填）。 */
    private String chrgitmLvText;

    private Long chargeItemDefinitionId;

    private String rateCode;
    private String rateCodeDictText;
    private String methodCode;
    private String methodCodeDictText;
    private String doseFormCode;
    private String doseFormCodeDictText;
    private String defInhospitalUnitCodeDictText;
    private Boolean restrictedFlag;
    private String restrictedScope;
    private String restrictedEnum;
    private String restrictedEnumDictText;
    private String partAttributeEnum;
    private String partAttributeEnumDictText;
    private String thoPartAttributeEnum;
    private String thoPartAttributeEnumDictText;
    private Boolean skinTestFlag;
    private Boolean injectFlag;
    /** 诊疗类型（活动定义 {@code type_enum} 或读模型投影整型）。 */
    private String activityTypeCode;
    private String locationName;
}
