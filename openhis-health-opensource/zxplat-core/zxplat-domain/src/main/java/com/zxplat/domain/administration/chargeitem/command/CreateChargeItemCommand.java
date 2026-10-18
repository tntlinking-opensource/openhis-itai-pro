/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.chargeitem.command;

import com.zxplat.domain.share.command.BatchCommand;
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
        /**
         * 结算快照处方号（对应 adm_charge_item.prescription_no）。
         * <p>药品业务真源在 {@code MedicationRequest.prescription_no}；签发挂费草稿<strong>不应</strong>预写本字段，
         * 由门诊结算盖章抄写。中药代煎费等方级费用可在挂费时写入以便未结算前按方定位。</p>
         */
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
        /**
         * 子项账单指向父收费明细主键（{@code adm_charge_item.parent_id}）。
         * <p>父行自身为空；带子项诊疗落账时必填，与 {@link #busNo} 点分扩展并存（DM-ADM-011 §4.4 / §9.1）。</p>
         */
        private Long parentId;
        /**
         * 业务流水号覆盖（可选）。
         * <p>空则写侧按日取号；子项账单建议传入 {@code 父busNo + "." + 子项稳定编码}，与 DM §9.1 一致。</p>
         */
        private String busNo;
    }
}
