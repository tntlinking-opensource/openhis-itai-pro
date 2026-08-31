/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.chargeitem.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 批量创建费用明细命令：将计价结果落到指定账户/就诊上下文下（DM-ADM-011）。
 */
@Getter
@Setter
public class CreateChargeItemCommand extends BatchCommand<CreateChargeItemCommand.Item> {
    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 单条费用明细创建项。
     */
    @Getter
    @Setter
    public static class Item {
        /** 患者主键，对应 adm_charge_item.patient_id。 */
        private Long patientId;
        /** 就诊主键，对应 adm_charge_item.encounter_id。 */
        private Long encounterId;
        /** 账户主键，对应 adm_charge_item.account_id。 */
        private Long accountId;
        /** 项目定义主键，对应 adm_charge_item.definition_id。 */
        private Long definitionId;
        /** 总价，对应 adm_charge_item.total_price。 */
        private BigDecimal totalPrice;
        /** 单价，对应 adm_charge_item.unit_price。 */
        private BigDecimal unitPrice;
        /** 数量值，对应 adm_charge_item.quantity_value。 */
        private BigDecimal quantityValue;
        /** 数量单位，对应 adm_charge_item.quantity_unit。 */
        private String quantityUnit;
        /** 基准金额，对应 adm_charge_item.base_amount。 */
        private BigDecimal baseAmount;
        /** 折扣金额，对应 adm_charge_item.discount_amount。 */
        private BigDecimal discountAmount;
        /** 附加费金额，对应 adm_charge_item.surcharge_amount。 */
        private BigDecimal surchargeAmount;
        /** 费用发生时刻，对应 adm_charge_item.occurrence_time。 */
        private LocalDateTime occurrenceTime;
        /** 上游业务表名（BR-CHG-001），对应 adm_charge_item.service_table（门诊药耗：医嘱请求表）。 */
        private String serviceTable;
        /** 上游业务主键，对应 adm_charge_item.service_id（门诊药耗：药品/耗材申请主键）。 */
        private Long serviceId;
        /** 产品定义表名，对应 adm_charge_item.product_table（门诊药耗：定义表）。 */
        private String productTable;
        /** 产品定义主键，对应 adm_charge_item.product_id。 */
        private Long productId;
        /** 发放物理表名，对应 adm_charge_item.dispense_table。 */
        private String dispenseTable;
        /** 发放主键，对应 adm_charge_item.dispense_id。 */
        private Long dispenseId;
        /** 开立机构主键，对应 adm_charge_item.org_id。 */
        private Long orgId;
        /** 申请机构主键，对应 adm_charge_item.requesting_org_id。 */
        private Long requestingOrgId;
        /** 若为空，由写侧按事件类型补全（DM §4.2 / §4.3），对应 generate_source_enum。 */
        private String generateSourceEnum;
        /** 若为空，由写侧按事件类型补全，对应 context_enum。 */
        private String contextEnum;
        /**
         * 半退保留链：新行链回的源收费明细主键，对应 adm_charge_item.refund_id（BR-CHG-015）；非保留链场景留空。
         */
        private Long refundRootChargeItemId;
        /** 处方号（中药代煎费等方级费用追溯），对应 adm_charge_item.prescription_no。 */
        private String prescriptionNo;
        /** 执行记录主键，对应 adm_charge_item.procedure_id。 */
        private Long procedureId;
        /**
         * 绑定就诊诊断主键有序串（与上游请求同口径；挂费时可显式传入，缺省时由写侧按 service 锚点回查请求拷贝）。
         */
        private String encounterDiagnosisIds;
        /** 改价原因码（DM override_reason_code）。 */
        private String overrideReasonCode;
        /** 改价原因说明（DM override_reason_text）。 */
        private String overrideReasonText;
        /** 手工调价后金额（DM manual_adjusted_price）。 */
        private BigDecimal manualAdjustedPrice;
    }
}
