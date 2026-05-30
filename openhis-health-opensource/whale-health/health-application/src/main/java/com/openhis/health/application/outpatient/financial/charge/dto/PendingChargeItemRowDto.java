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

package com.openhis.health.application.outpatient.financial.charge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 就诊待收费明细行 DTO。
 */
@Data
@Accessors(chain = true)
@Schema(description = "就诊待收费明细行")
public class PendingChargeItemRowDto {
    @Schema(description = "收费明细 ID")
    /**
     * chargeItemId。
     */
    private Long chargeItemId;
    @Schema(description = "来源业务表")
    /**
     * serviceTable。
     */
    private String serviceTable;
    @Schema(description = "来源业务 ID")
    /**
     * serviceId。
     */
    private Long serviceId;
    @Schema(description = "收费项展示名称（优先三大目录读模型 product_name，锚点：adm_charge_item.product 列；缺省时回落 adm_charge_item_definition 实例列；再其次 charge_name）")
    /**
     * itemName。
     */
    private String itemName;
    @Schema(description = "收费明细业务流水号（bus_no）")
    /**
     * busNo。
     */
    private String busNo;
    @Schema(description = "单价")
    /**
     * unitPrice。
     */
    private BigDecimal unitPrice;
    @Schema(description = "数量")
    /**
     * quantityValue。
     */
    private BigDecimal quantityValue;
    @Schema(description = "数量单位")
    /**
     * quantityUnit。
     */
    private String quantityUnit;
    @Schema(description = "数量展示（仅数值；单位见 quantityUnit）")
    /**
     * quantityDisplay。
     */
    private String quantityDisplay;
    @Schema(description = "费用分类编码（优先目录读模型 category_code，目录锚点同 itemName；其次 adm_charge_item_definition.type_code）")
    /**
     * chargeCategoryCode。
     */
    private String chargeCategoryCode;
    @Schema(description = "费用分类展示名（优先目录读模型 category_code_dict_text，否则与 ChargeTypeCategory 对齐时解析）")
    /**
     * chargeCategoryDisplay。
     */
    private String chargeCategoryDisplay;
    @Schema(description = "收费状态编码（ChargeItemStatus.code）")
    /**
     * statusCode。
     */
    private String statusCode;
    @Schema(description = "收费状态文案")
    /**
     * statusText。
     */
    private String statusText;
    @Schema(description = "总价")
    /**
     * totalPrice。
     */
    private BigDecimal totalPrice;
    @Schema(description = "收款人系统用户主键（仅 adm_charge_item.performer_id，不做 updated_by 回落）")
    /**
     * cashierUserId。
     */
    private Long cashierUserId;
    @Schema(description = "收款人姓名（能关联到医务人员主档时解析，否则为空）")
    /**
     * cashierName。
     */
    private String cashierName;
    @Schema(description = "折扣率展示")
    /**
     * discountRateDisplay。
     */
    private String discountRateDisplay;
    @Schema(description = "记账账户 ID")
    /**
     * accountId。
     */
    private Long accountId;

    @Schema(description = "费用性质名称（账户 contract_no → 费用性质合同名称）")
    private String contractName;

    @Schema(description = "费用性质展示（与 contractName 同源，供 IF-OPC-03 前端列 feeNatureDisplay）")
    private String feeNatureDisplay;

    @Schema(description = "医疗类别/医保诊疗类别（adm_encounter_diagnosis.med_type，按收费行 encounter_diagnosis_id 解析）")
    private String medTypeCode;

    @Schema(description = "医保目录编码（产品读模型 yb_no）")
    private String ybNo;

    @Schema(description = "医保编码展示（与 ybNo 同源，供前端列 medicalInsuranceCode）")
    private String medicalInsuranceCode;
}
