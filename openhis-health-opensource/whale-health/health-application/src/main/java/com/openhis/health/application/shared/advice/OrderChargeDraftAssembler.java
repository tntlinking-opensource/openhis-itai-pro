/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.advice;

import com.openhis.health.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.share.constants.ChargeItemDmConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.whale.core.application.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 医嘱开立/签发费用草稿命令项装配（门诊/住院医生站、护士站执行挂费共用）。
 *
 * <p>从 {@code DoctorStationAdviceMapper} 抽离，供 {@code module.orchestration} 与 application Mapper 复用，
 * 避免 orchestration 依赖 application 层。</p>
 */
public final class OrderChargeDraftAssembler {

    /** 工具类禁止实例化。 */
    private OrderChargeDraftAssembler() {
    }

    /**
     * 装配单条费用草稿命令项；定价行校验失败时由 {@link OrderChargePricingAssembler} 抛出。
     *
     * @param encounterId            就诊主键
     * @param patientId              患者主键
     * @param accountId              账户主键
     * @param orgId                  机构主键
     * @param serviceId              上游锚点主键
     * @param serviceTable           上游表名
     * @param productDefinitionTable 产品定义表名（三大目录之一）
     * @param productDefinitionId    产品定义主键
     * @param priceLine              定价行
     * @param quantity               计价数量
     * @param quantityUnit           计价单位
     * @param productRow             产品定义读模型
     * @return 命令项
     */
    public static CreateChargeItemCommand.Item toCreateChargeItemDraftItemFromOrder(
        Long encounterId,
        Long patientId,
        Long accountId,
        Long orgId,
        Long serviceId,
        String serviceTable,
        String productDefinitionTable,
        Long productDefinitionId,
        ChargeItemDefinition priceLine,
        BigDecimal quantity,
        String quantityUnit,
        ProductDefinitionDisplayDto productRow
    ) {
        CreateChargeItemCommand.Item it = new CreateChargeItemCommand.Item();
        it.setPatientId(patientId);
        it.setEncounterId(encounterId);
        it.setAccountId(accountId);
        OrderChargePricingAssembler.applyOrderChargePricing(
            it, productRow, priceLine, quantity, quantityUnit);
        it.setOccurrenceTime(LocalDateTime.now());
        it.setServiceTable(serviceTable);
        it.setServiceId(serviceId);
        applyProductCatalogAnchor(it, productDefinitionTable, productDefinitionId);
        it.setOrgId(orgId);
        it.setRequestingOrgId(orgId);
        it.setGenerateSourceEnum(GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        it.setContextEnum(resolveChargeContext(serviceTable));
        return it;
    }

    /**
     * 装配签发 FIFO 单段费用草稿命令项。
     *
     * <p>先写入就诊上下文、金额与 {@code service_*}（医嘱请求锚点），再补齐 {@code product_*}、{@code dispense_*}。</p>
     *
     * @param encounterId            就诊主键
     * @param patientId              患者主键
     * @param accountId              账户主键
     * @param orgId                  机构主键
     * @param requestId              医嘱申请主键
     * @param requestServiceTable    医嘱请求表名
     * @param productDefinitionTable 产品定义表名
     * @param productDefinitionId    产品定义主键
     * @param dispenseTable          发放表名
     * @param dispenseId             发放主键
     * @param priceLine              定价行
     * @param quantity               计价数量
     * @param quantityUnit           计价单位
     * @param productRow             产品定义读模型
     * @return 费用草稿命令项
     */
    public static CreateChargeItemCommand.Item toCreateChargeItemDraftItemForFifoDispenseSegment(
        Long encounterId,
        Long patientId,
        Long accountId,
        Long orgId,
        Long requestId,
        String requestServiceTable,
        String productDefinitionTable,
        Long productDefinitionId,
        String dispenseTable,
        Long dispenseId,
        ChargeItemDefinition priceLine,
        BigDecimal quantity,
        String quantityUnit,
        ProductDefinitionDisplayDto productRow
    ) {
        CreateChargeItemCommand.Item it = toCreateChargeItemDraftItemFromOrder(
            encounterId, patientId, accountId, orgId, requestId, requestServiceTable,
            productDefinitionTable, productDefinitionId, priceLine, quantity, quantityUnit, productRow);
        it.setDispenseTable(dispenseTable);
        it.setDispenseId(dispenseId);
        return it;
    }

    /**
     * 写草稿 charge_item：合法产品表时回填 productTable/productId 锚点。
     *
     * @param item                    草稿行
     * @param productDefinitionTable  产品表名
     * @param productDefinitionId     产品 id
     */
    private static void applyProductCatalogAnchor(
        CreateChargeItemCommand.Item item,
        String productDefinitionTable,
        Long productDefinitionId) {
        if (item == null || productDefinitionId == null || StringUtils.isEmpty(productDefinitionTable)) {
            return;
        }
        String normalizedTable = productDefinitionTable.trim();
        if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(normalizedTable)
            && !TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(normalizedTable)
            && !TableNameConstants.ADM_DEVICE_DEFINITION.equals(normalizedTable)) {
            return;
        }
        item.setProductTable(normalizedTable);
        item.setProductId(productDefinitionId);
    }

    /**
     * 按 service_table 判定 charge context（药品/耗材/诊疗）。
     *
     * @param serviceTable 请求或发放表名
     * @return {@link ChargeItemDmConstants} context 码
     */
    private static String resolveChargeContext(String serviceTable) {
        if (TableNameConstants.MED_MEDICATION_REQUEST.equals(serviceTable)
            || TableNameConstants.MED_MEDICATION_DISPENSE.equals(serviceTable)) {
            return ChargeItemDmConstants.CONTEXT_MEDICATION;
        }
        if (TableNameConstants.WKF_DEVICE_REQUEST.equals(serviceTable)
            || TableNameConstants.WKF_DEVICE_DISPENSE.equals(serviceTable)) {
            return ChargeItemDmConstants.CONTEXT_DEVICE;
        }
        return ChargeItemDmConstants.CONTEXT_ACTIVITY;
    }
}
