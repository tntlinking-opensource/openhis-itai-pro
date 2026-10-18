/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.inpatientbilling.support;

import com.zxplat.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.zxplat.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.zxplat.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.zxplat.domain.clinical.procedure.command.CompleteProcedureRecordCommand;
import com.zxplat.domain.clinical.procedure.command.CreateProcedureRecordCommand;
import com.zxplat.domain.clinical.procedure.command.StartProcedureExecutionCommand;
import com.zxplat.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.zxplat.domain.clinical.procedure.service.ProcedureDomainService;
import com.zxplat.domain.clinical.procedure.service.ProcedureQueryService;
import com.zxplat.domain.share.constants.ChargeItemDmConstants;
import com.zxplat.domain.share.constants.InpatientNurseStationConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.GenerateSource;
import com.zxplat.domain.share.enums.RequestStatus;
import com.zxplat.domain.share.enums.TherapyType;
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.zxplat.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.zxplat.application.shared.advice.OrderChargePricingAssembler;
import com.zxplat.application.shared.activitydefinition.ActivityDefinitionPackageChargePersistSupport;
import com.zxplat.application.shared.nursestation.ProcedureExecutionPerformerOrchestrator;
import com.zxplat.application.shared.product.ClinicalCatalogPriceResolver;
import com.zxplat.application.shared.product.ClinicalCatalogUnitPriceQuery;
import com.zxplat.domain.share.query.ProductDefinitionBatchLookup;
import com.zxplat.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 住院滚费长期诊疗医嘱执行编排（AS-IP-NUR-013 IF-IP-ARG-08；对齐 IF-IP-EXE-02，非划价轨）。
 *
 * <p>对已有 VERIFIED 长期 ServiceRequest 登记 planTime 槽位 Procedure + {@code order-execute} 待结算 Charge；
 * Procedure 顺序与医嘱执行/划价诊疗一致：{@code create→start→挂主执行人→complete}（BR-IP-ARG-015 / DM-CLN-004）；
 * 主执行人=责任护士（BR-IP-ARG-019）。划价临时诊疗走
 * {@link NurseStationInpatientBillingProcedureOrchestrator}，共用
 * {@link ProcedureExecutionPerformerOrchestrator#registerPrimaryPerformer}。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InpatientAutoRollOrderExecutionOrchestrator {

    private final ServiceRequestQueryService serviceRequestQueryService;
    private final ProcedureDomainService procedureDomainService;
    private final ProcedureQueryService procedureQueryService;
    private final ProcedureExecutionPerformerOrchestrator procedureExecutionPerformerOrchestrator;
    private final ChargeItemDomainService chargeItemDomainService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final ActivityDefinitionPackageChargePersistSupport activityDefinitionPackageChargePersistSupport;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final ClinicalCatalogPriceResolver clinicalCatalogPriceResolver;

    /**
     * 滚费单槽位执行结果。
     */
    public enum Outcome {
        /** 新建 Procedure + Charge 成功。 */
        CREATED,
        /** 同日同医嘱已执行，幂等跳过。 */
        SKIPPED,
        /** 前置校验或落账失败。 */
        FAILED
    }

    /**
     * 对长期诊疗医嘱执行单个 billDate 计划点（IF-IP-EXE-02 同构，专用滚费入口）。
     *
     * <p>Procedure 编排固定 {@code create→start→挂主执行人→complete}；与划价
     * {@code registerCompletedNursePricingProcedure}、护士站医嘱执行同序（仅 category/来源不同）。
     * 计划点已被占用（EX-IP-EXE-301）时复用 COMPLETED 行落 Charge，不再挂执行人。</p>
     *
     * @param command 执行命令
     * @return 执行结果
     */
    public Outcome executeLongTermServiceOrderSlot(InpatientAutoRollServiceOrderExecuteSpec command) {
        if (command == null || command.getServiceRequestId() == null || command.getBillDate() == null) {
            return Outcome.FAILED;
        }
        Long serviceRequestId = command.getServiceRequestId();
        LocalDate billDate = command.getBillDate();
        // Gate：同日同 SR 已有 order-execute 费用则跳过（BR-IP-ARG-010）
        if (chargeItemQueryService.existsOrderExecuteChargeForServiceRequestBillDate(serviceRequestId, billDate)) {
            return Outcome.SKIPPED;
        }
        // Pre：加载诊疗申请明细
        ServiceRequestListModel serviceOrder = serviceRequestQueryService.mapListModelsByIds(
            List.of(serviceRequestId)).get(serviceRequestId);
        if (serviceOrder == null) {
            return Outcome.FAILED;
        }
        if (!RequestStatus.VERIFIED.getCode().equals(serviceOrder.getStatusEnum())) {
            return Outcome.FAILED;
        }
        Long performerId = command.getPerformerId();
        if (performerId == null) {
            performerId = command.getOperatorId();
        }
        if (performerId == null) {
            return Outcome.FAILED;
        }
        Long operatorId = command.getOperatorId();
        if (operatorId == null) {
            operatorId = performerId;
        }
        // 业务发生时刻：优先配置/调用方传入；空则日末（护理与兼容路径）
        LocalDateTime planTime = command.getOccurrenceTime();
        if (planTime == null) {
            planTime = billDate.atTime(InpatientNurseStationConstants.DAY_END_CLOCK);
        }
        BigDecimal quantity = command.getQuantity();
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            quantity = serviceOrder.getQuantity();
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            quantity = BigDecimal.ONE;
        }
        // Pre：单位取 SR 已落库 unit_code（与 IF-IP-EXE-02 手动执行同构）
        String quantityUnit = serviceOrder.getUnitCode();
        if (StringUtils.isEmpty(quantityUnit)) {
            quantityUnit = command.getQuantityUnit();
        }
        if (StringUtils.isEmpty(quantityUnit)) {
            log.warn(
                "住院滚费单位缺失 serviceRequestId={} activityId={} billDate={}",
                serviceRequestId,
                serviceOrder.getActivityId(),
                billDate);
            return Outcome.FAILED;
        }
        Long activityId = serviceOrder.getActivityId();
        if (activityId == null) {
            return Outcome.FAILED;
        }
        // Pre：解析执行定价行（配置锚定 definition 优先，回落目录择优）
        ChargeItemDefinition priceLine = this.resolveExecutionPriceLine(
            activityId, command.getPreferredDefinitionId(), command.getOrganizationId());
        if (priceLine == null) {
            return Outcome.FAILED;
        }
        ProductDefinitionDisplayDto productRow = this.loadActivityProduct(activityId);
        if (productRow == null) {
            log.warn(
                "住院滚费产品读模型缺失 serviceRequestId={} activityId={} billDate={}",
                serviceRequestId,
                activityId,
                billDate);
            return Outcome.FAILED;
        }
        Long procedureId;
        try {
            // Cmd：Procedure create → start → 挂主执行人 → complete（与 IF-IP-EXE-02 / 划价诊疗同序；
            // DM-CLN-004 assertPerformerAppendable：仅 PREPARATION/IN_PROGRESS 可挂执行人）
            CreateProcedureRecordCommand createCmd = new CreateProcedureRecordCommand();
            createCmd.setOperatorId(operatorId);
            createCmd.setSkipDuplicateRequestAnchorCheck(Boolean.TRUE);
            createCmd.setRequestId(serviceRequestId);
            createCmd.setRequestTable(TableNameConstants.WKF_SERVICE_REQUEST);
            createCmd.setEncounterId(command.getEncounterId());
            createCmd.setPatientId(command.getPatientId());
            createCmd.setCategoryEnum(TherapyType.LONG_TERM.getCode());
            createCmd.setOccurrenceTime(planTime);
            procedureId = procedureDomainService.createProcedureRecord(createCmd);
            StartProcedureExecutionCommand startCmd = new StartProcedureExecutionCommand();
            startCmd.setProcedureId(procedureId);
            startCmd.setOperatorId(operatorId);
            procedureDomainService.startProcedureExecution(startCmd);
            // Cmd：主执行人须在 COMPLETED 前写入（责任护士；startTime 对齐 planTime）
            procedureExecutionPerformerOrchestrator.registerPrimaryPerformer(
                procedureId, performerId, operatorId, planTime);
            CompleteProcedureRecordCommand completeCmd = new CompleteProcedureRecordCommand();
            completeCmd.setProcedureId(procedureId);
            completeCmd.setOperatorId(operatorId);
            completeCmd.setResultText(InpatientNurseStationConstants.INPATIENT_NURSE_EXECUTE_PROCEDURE_RESULT_TEXT);
            // 滚费自动执行：recorded_time / occurrence_time 同锚 planTime（配置业务时刻或日末）
            completeCmd.setRecordedTime(planTime);
            procedureDomainService.completeProcedureRecord(completeCmd);
        } catch (BusinessException ex) {
            // 计划点已占用：复用已有 COMPLETED 继续落 Charge（禁止空 SKIPPED 留下半成功）
            if (InpatientNurseStationConstants.EX_IP_EXE_301.equals(ex.getErrorCode())) {
                procedureId = this.resolveExistingCompletedProcedureId(
                    command.getEncounterId(), serviceRequestId, planTime);
                if (procedureId == null) {
                    log.warn(
                        "住院滚费计划点已占用但找不到 COMPLETED Procedure serviceRequestId={} billDate={} planTime={}",
                        serviceRequestId,
                        billDate,
                        planTime);
                    return Outcome.FAILED;
                }
                // 复用终态行：禁止再挂执行人（DM-CLN-004）；首登时已写入 performer
                log.info(
                    "住院滚费复用已占用计划点 Procedure 继续落费用 procedureId={} serviceRequestId={} billDate={}",
                    procedureId,
                    serviceRequestId,
                    billDate);
            } else {
                log.warn(
                    "住院滚费 Procedure 登记失败 serviceRequestId={} billDate={}",
                    serviceRequestId,
                    billDate,
                    ex);
                return Outcome.FAILED;
            }
        }
        // Cmd：新建 order-execute 待结算 Charge（与 Procedure 同事务；失败整单回滚）
        try {
            CreateChargeItemCommand chargeCmd = new CreateChargeItemCommand();
            chargeCmd.setOperatorId(operatorId);
            CreateChargeItemCommand.Item chargeItem = new CreateChargeItemCommand.Item();
            chargeItem.setPatientId(command.getPatientId());
            chargeItem.setEncounterId(command.getEncounterId());
            chargeItem.setAccountId(command.getAccountId());
            chargeItem.setServiceTable(TableNameConstants.WKF_SERVICE_REQUEST);
            chargeItem.setServiceId(serviceRequestId);
            chargeItem.setProductTable(TableNameConstants.WKF_ACTIVITY_DEFINITION);
            chargeItem.setProductId(activityId);
            chargeItem.setProcedureId(procedureId);
            chargeItem.setOccurrenceTime(planTime);
            chargeItem.setOrgId(command.getOrganizationId());
            chargeItem.setRequestingOrgId(command.getOrganizationId());
            chargeItem.setGenerateSourceEnum(GenerateSource.ORDER_EXECUTE.getCode());
            chargeItem.setContextEnum(ChargeItemDmConstants.CONTEXT_ACTIVITY);
            OrderChargePricingAssembler.applyOrderChargePricing(
                chargeItem, productRow, priceLine, quantity, quantityUnit);
            chargeCmd.setItems(List.of(chargeItem));
            Long parentChargeId = chargeItemDomainService.createPlannedChargeItemFromOrderExecution(chargeCmd);
            // Cmd：带子项诊疗滚费同样冻价写子项账单
            activityDefinitionPackageChargePersistSupport.persistPackageChildChargeItemsAfterParent(
                parentChargeId, activityId, operatorId);
            return Outcome.CREATED;
        } catch (BusinessException ex) {
            log.warn(
                "住院滚费 Charge 落账失败 serviceRequestId={} billDate={} unitCode={}",
                serviceRequestId,
                billDate,
                quantityUnit,
                ex);
            return Outcome.FAILED;
        }
    }

    /**
     * 按就诊 + 请求 + 计划点定位已占用的 COMPLETED 执行记录。
     *
     * @param encounterId      就诊主键
     * @param serviceRequestId 诊疗申请主键
     * @param planTime         计划点
     * @return Procedure 主键；未命中 null
     */
    private Long resolveExistingCompletedProcedureId(
        Long encounterId,
        Long serviceRequestId,
        LocalDateTime planTime) {
        if (encounterId == null || serviceRequestId == null || planTime == null) {
            return null;
        }
        List<ProcedureRecordQueryModel> rows =
            procedureQueryService.listNetRemainingCompletedProcedureRecords(
                encounterId,
                serviceRequestId,
                TableNameConstants.WKF_SERVICE_REQUEST);
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        for (ProcedureRecordQueryModel row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (planTime.equals(row.getOccurrenceTime())) {
                return row.getId();
            }
        }
        return null;
    }

    /**
     * 解析滚费执行定价行。
     *
     * @param activityId             活动定义主键
     * @param preferredDefinitionId  配置锚定定价；可空
     * @param organizationId         机构上下文
     * @return 定价行；不可解析时 null
     */
    private ChargeItemDefinition resolveExecutionPriceLine(
        Long activityId,
        Long preferredDefinitionId,
        Long organizationId) {
        if (preferredDefinitionId != null) {
            Map<Long, ChargeItemDefinition> byId =
                chargeItemDefinitionQueryService.findAllDefinitionsByIdIn(Collections.singleton(preferredDefinitionId));
            ChargeItemDefinition preferred = byId.get(preferredDefinitionId);
            if (preferred != null && preferred.getPriceValue() != null) {
                return preferred;
            }
        }
        ChargeItemDefinitionQueryCommand query = new ChargeItemDefinitionQueryCommand();
        query.setInstanceTable(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        query.setInstanceId(activityId);
        List<ChargeItemDefinition> lines = chargeItemDefinitionQueryService.findSaleItemByInstanceId(query);
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        ClinicalCatalogUnitPriceQuery priceCmd = ClinicalCatalogUnitPriceQuery.builder()
            .productTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION)
            .productDefinitionId(activityId)
            .organizationId(organizationId)
            .priceLines(lines)
            .build();
        ChargeItemDefinition picked = clinicalCatalogPriceResolver.resolveCatalogUnitPrice(priceCmd);
        if (picked == null || picked.getPriceValue() == null) {
            return null;
        }
        return picked;
    }

    /**
     * 加载诊疗活动产品读模型（Charge 比例换算用）。
     *
     * @param activityId 活动定义主键
     * @return 产品读模型；缺失时 null
     */
    private ProductDefinitionDisplayDto loadActivityProduct(Long activityId) {
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        lookup.setProductDefinitionId(activityId);
        Map<String, ProductDefinitionDisplayDto> loaded =
            productDefinitionDisplayTerminologyAssembler.loadAsMap(List.of(lookup));
        return loaded.get(ProductDefinitionKeyUtil.buildCompositeKey(
            TableNameConstants.WKF_ACTIVITY_DEFINITION, activityId));
    }
}
