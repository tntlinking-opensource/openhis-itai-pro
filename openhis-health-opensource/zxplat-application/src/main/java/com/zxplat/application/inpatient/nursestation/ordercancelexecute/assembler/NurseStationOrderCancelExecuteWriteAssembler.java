/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.ordercancelexecute.assembler;

import com.zxplat.application.inpatient.nursestation.dispensesummary.assembler.NurseStationDispenseSummaryWriteAssembler;
import com.zxplat.application.inpatient.nursestation.dispensesummary.assembler.NurseStationDispenseSummaryWriteAssembler.SummaryMergeGroup;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.mapper.NurseStationInpatientBillingMapper;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.support.NurseStationInpatientBillingProcedureOrchestrator;
import com.zxplat.application.inpatient.nursestation.ordercancelexecute.dto.CancelExecutionSummaryRebuildContext;
import com.zxplat.application.inpatient.nursestation.ordercancelexecute.dto.SummaryRebuildHint;
import com.zxplat.application.inpatient.nursestation.ordercancelexecute.mapper.NurseStationOrderCancelExecuteMapper;
import com.zxplat.application.inpatient.pharmacy.dispense.support.InpatientMedicationDispenseChargePricingLoader;
import com.zxplat.application.shared.nursestation.DeviceDispenseNurseWorkflowOrchestrator;
import com.zxplat.application.shared.nursestation.ProcedureExecutionPerformerOrchestrator;
import com.zxplat.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.administration.chargeitem.command.OffsetNursePricingChargeItemsByServiceAnchorsCommand;
import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.zxplat.domain.clinical.procedure.service.ProcedureDomainService;
import com.zxplat.domain.clinical.procedure.service.ProcedureQueryService;
import com.zxplat.domain.medication.medicationdispense.entity.MedicationDispense;
import com.zxplat.domain.medication.medicationdispense.queries.model.MedicationDispenseSummaryItemQueryModel;
import com.zxplat.domain.medication.medicationdispense.service.MedicationDispenseDomainService;
import com.zxplat.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.zxplat.domain.share.constants.InpatientNurseStationConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.ChargeItemStatus;
import com.zxplat.domain.share.enums.DispenseStatus;
import com.zxplat.domain.share.enums.EventStatus;
import com.zxplat.domain.share.enums.GenerateSource;
import com.zxplat.domain.share.enums.SupplyStatus;
import com.zxplat.domain.share.enums.SupplyType;
import com.zxplat.domain.share.query.ProductDefinitionBatchLookup;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.domain.workflow.devicerequest.queries.model.DeviceRequestListModel;
import com.zxplat.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.zxplat.domain.workflow.servicerequest.entity.ServiceRequest;
import com.zxplat.domain.workflow.servicerequest.service.ServiceRequestDomainService;
import com.zxplat.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.zxplat.domain.workflow.supplyrequest.queries.SupplyByBusNoQuery;
import com.zxplat.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.zxplat.domain.workflow.supplyrequest.service.SupplyRequestDomainService;
import com.zxplat.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * IF-IP-CXL-01 写路径专项编排：结算 Gate、绑定项回退、Dispense/Charge 回退与汇总过滤重建。
 *
 * <p>Cmd 顺序遵循 BR-IP-CXL-006：绑定回退 → CANCEL 对冲 → Dispense/Charge 回退 → 汇总重建。</p>
 * <p>CANCEL 对冲编排同构执行：{@code create→start→挂主执行人→cancel}（操作人为主执行人）。</p>
 */
@Component
@RequiredArgsConstructor
public class NurseStationOrderCancelExecuteWriteAssembler {

    /** Procedure 写侧：CANCEL 对冲 create/start/cancel。 */
    private final ProcedureDomainService procedureDomainService;
    /** CANCEL 行主执行人登记（须在 CANCEL 终态前挂入）。 */
    private final ProcedureExecutionPerformerOrchestrator procedureExecutionPerformerOrchestrator;
    /** Procedure 读侧：对冲检测与绑定派生 Procedure 加载。 */
    private final ProcedureQueryService procedureQueryService;
    /** 药品发放写侧：删/回退/退药/撤汇总/重建汇总。 */
    private final MedicationDispenseDomainService medicationDispenseDomainService;
    /** 药品发放读侧：汇总行预读与重建分组。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    /** Charge 写侧：执行族（order-execute / anesthesia-execute）按 procedureId 逻辑删。 */
    private final ChargeItemDomainService chargeItemDomainService;
    /** Charge 读侧：结算 Gate 再生链过滤。 */
    private final ChargeItemQueryService chargeItemQueryService;
    /** 住院汇总重建定价：仅 PENDING_SETTLEMENT，按 dispenseId 锚定（同 IF-IP-SUM-04）。 */
    private final InpatientMedicationDispenseChargePricingLoader inpatientMedicationDispenseChargePricingLoader;
    /** 诊疗申请读侧：based_on 反查绑定 SR。 */
    private final ServiceRequestQueryService serviceRequestQueryService;
    /** 诊疗申请写侧：历史 order-execute 派生 SR 逻辑删除。 */
    private final ServiceRequestDomainService serviceRequestDomainService;
    /** 耗材请求读侧：based_on 反查绑定 DeviceRequest。 */
    private final DeviceRequestQueryService deviceRequestQueryService;
    /** 绑定耗材发放对称回退与库存累计（BR-IP-CXL-014）。 */
    private final DeviceDispenseNurseWorkflowOrchestrator deviceDispenseNurseWorkflowOrchestrator;
    /** 护士划价诊疗 Procedure CANCEL 对冲（执行自动带=补费同构）。 */
    private final NurseStationInpatientBillingProcedureOrchestrator nurseStationInpatientBillingProcedureOrchestrator;
    /** 护士划价冲销命令装配。 */
    private final NurseStationInpatientBillingMapper nurseStationInpatientBillingMapper;
    /** Supply 读侧：汇总单态乐观校验。 */
    private final SupplyRequestQueryService supplyRequestQueryService;
    /** Supply 写侧：撤单/驳回/重建建单。 */
    private final SupplyRequestDomainService supplyRequestDomainService;
    /** 汇总过滤重建合并组（复用 AS-IP-NUR-010）。 */
    private final NurseStationDispenseSummaryWriteAssembler nurseStationDispenseSummaryWriteAssembler;
    /** 药品定义批量加载（重建分组展示校验）。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    /** Request/QueryModel → Command 字段映射。 */
    private final NurseStationOrderCancelExecuteMapper nurseStationOrderCancelExecuteMapper;

    /**
     * 入参 procedureId 去重并保持稳定顺序。
     *
     * @param raw 原始入参
     * @return 去重后的 id 列表
     */
    public List<Long> normalizeDistinctProcedureIds(List<Long> raw) {
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        if (raw != null) {
            for (Long procedureId : raw) {
                if (procedureId != null) {
                    distinct.add(procedureId);
                }
            }
        }
        return new ArrayList<>(distinct);
    }

    /**
     * 结算 Gate：任一 Charge 为 SETTLED/REFUNDING/REFUNDED 则整批拒绝。
     *
     * <p>反结/半退再生链上源 {@code SETTLED} 终态行不参与 Gate（BR-REF-020 / BR-IP-RVS-011）。</p>
     *
     * @param allCharges 主医嘱与绑定项关联的全部 Charge
     * @throws BusinessException EX-IP-CXL-203
     */
    public void assertSettlementGate(List<ChargeItem> allCharges) {
        if (allCharges == null) {
            return;
        }
        List<ChargeItem> gatedCharges =
            chargeItemQueryService.filterOutSupersededSettledLinesGroupedByEncounter(allCharges);
        for (ChargeItem charge : gatedCharges) {
            if (charge == null || charge.getStatusEnum() == null) {
                continue;
            }
            String status = charge.getStatusEnum();
            if (ChargeItemStatus.SETTLED.getCode().equals(status)
                || ChargeItemStatus.REFUNDING.getCode().equals(status)
                || ChargeItemStatus.REFUNDED.getCode().equals(status)) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_CXL_203,
                    "费用已结算，请先办理退费后再取消执行");
            }
        }
    }

    /**
     * 药房配药中 Gate：关联 Dispense 为 {@code PREPARED}/{@code IN_PROGRESS} 时拒绝取消执行（GAP-STALE-004）。
     *
     * @param dispenses 主医嘱 procedureId 关联发放行
     * @throws BusinessException EX-IP-CXL-204
     */
    public void assertPharmacyPreparingDispenseGate(Collection<MedicationDispense> dispenses) {
        if (dispenses == null || dispenses.isEmpty()) {
            return;
        }
        for (MedicationDispense dispense : dispenses) {
            if (dispense == null || dispense.getStatusEnum() == null) {
                continue;
            }
            DispenseStatus status = dispense.getStatusEnum();
            if (DispenseStatus.PREPARED.equals(status) || DispenseStatus.IN_PROGRESS.equals(status)) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_CXL_204,
                    "药房配药中，请先作废配药或完成退药流程");
            }
        }
    }

    /**
     * 按 based_on 反查执行自动带出的绑定诊疗 ServiceRequest（BR-IP-CXL-013）。
     *
     * <p>保留 {@link GenerateSource#NURSE_PRICING}（现行：执行带出=医嘱补费同构）与历史
     * {@link GenerateSource#ORDER_EXECUTE}。皮试 companion（{@code doctor-prescription}）等
     * 其它 based_on 行不进入回退；手动补费亦为 nurse-pricing，须再按计划点/Procedure 关联过滤。</p>
     *
     * @param basedOnTable 主医嘱 requestTable
     * @param basedOnIds   主医嘱 requestId 集合
     * @return 候选绑定诊疗 SR 列表；无命中时为空列表
     */
    public List<ServiceRequest> loadBindingServiceRequests(String basedOnTable, Collection<Long> basedOnIds) {
        if (StringUtils.isEmpty(basedOnTable) || basedOnIds == null || basedOnIds.isEmpty()) {
            return new ArrayList<>();
        }
        // Pre：按主医嘱锚点批量反查全部 based_on 挂靠行
        List<ServiceRequest> candidates =
            serviceRequestQueryService.listServiceRequestsByBasedOnAnchors(basedOnTable, basedOnIds);
        String orderExecuteSource = GenerateSource.ORDER_EXECUTE.getCode();
        String nursePricingSource = GenerateSource.NURSE_PRICING.getCode();
        List<ServiceRequest> result = new ArrayList<>();
        for (ServiceRequest candidate : candidates) {
            // 跳过空行，避免 NPE 阻断整批取消
            if (candidate == null) {
                continue;
            }
            String source = candidate.getGenerateSourceEnum();
            // 仅执行自动带两类来源；皮试/其它 based_on 不进取消回退
            if (!orderExecuteSource.equals(source) && !nursePricingSource.equals(source)) {
                continue;
            }
            result.add(candidate);
        }
        return result;
    }

    /**
     * 按 based_on 反查绑定耗材 DeviceRequest（含 nurse-pricing 与历史 order-execute）。
     *
     * @param basedOnTable 主医嘱 requestTable
     * @param basedOnIds   主医嘱 requestId 集合
     * @return 绑定耗材请求列表
     */
    public List<DeviceRequestListModel> loadBindingDeviceRequests(String basedOnTable, Collection<Long> basedOnIds) {
        if (StringUtils.isEmpty(basedOnTable) || basedOnIds == null || basedOnIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<DeviceRequestListModel> candidates =
            deviceRequestQueryService.listDeviceRequestsByBasedOnAnchors(basedOnTable, basedOnIds);
        if (candidates == null || candidates.isEmpty()) {
            return new ArrayList<>();
        }
        String orderExecuteSource = GenerateSource.ORDER_EXECUTE.getCode();
        String nursePricingSource = GenerateSource.NURSE_PRICING.getCode();
        List<DeviceRequestListModel> result = new ArrayList<>();
        for (DeviceRequestListModel candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            String source = candidate.getGenerateSourceEnum();
            // 仅执行自动带耗材；其它 based_on 耗材不进本回退
            if (!orderExecuteSource.equals(source) && !nursePricingSource.equals(source)) {
                continue;
            }
            result.add(candidate);
        }
        return result;
    }

    /**
     * 收窄绑定诊疗：仅保留与主医嘱本槽计划点对齐的行（防误冲手动补费/其它槽）。
     *
     * <p>主关联：绑定 Procedure 净剩 COMPLETED 的 {@code occurrence_time} 与主槽**秒级**对齐
     *（与执行列表 {@code planTimesEqual} 同口径，避免纳秒差漏冲）。
     * 回落：绑定 Charge.{@code occurrence_time} 与主槽秒级对齐且仍为待冲销正向划价行。</p>
     *
     * @param candidates                     based_on 候选
     * @param mainCompletedProcedure         被取消的主医嘱 COMPLETED Procedure
     * @param bindingChargesByServiceRequestId 绑定 SR → 关联 Charge（可空；供 occurrence 回落）
     * @return 本槽应回退的绑定诊疗
     */
    public List<ServiceRequest> filterBindingServiceRequestsForSlot(
        List<ServiceRequest> candidates,
        ProcedureRecordQueryModel mainCompletedProcedure,
        Map<Long, List<ChargeItem>> bindingChargesByServiceRequestId) {
        List<ServiceRequest> result = new ArrayList<>();
        if (candidates == null || candidates.isEmpty() || mainCompletedProcedure == null) {
            return result;
        }
        LocalDateTime mainOccurrence = mainCompletedProcedure.getOccurrenceTime();
        // 主槽缺 occurrence：无法按计划点收窄，拒绝误冲全部 based_on
        if (mainOccurrence == null) {
            return result;
        }
        for (ServiceRequest candidate : candidates) {
            if (candidate == null || candidate.getId() == null) {
                continue;
            }
            // 主路径：绑定诊疗 Procedure.occurrence 与主槽 planTime 秒级对齐
            if (this.hasCompletedProcedureAtOccurrence(
                candidate.getId(), TableNameConstants.WKF_SERVICE_REQUEST, mainOccurrence)) {
                result.add(candidate);
                continue;
            }
            // 回落：Charge.occurrence 对齐（Procedure 读写精度异常时仍能按槽冲销）
            List<ChargeItem> charges = null;
            if (bindingChargesByServiceRequestId != null) {
                charges = bindingChargesByServiceRequestId.get(candidate.getId());
            }
            if (this.hasPendingPricingChargeAtOccurrence(charges, mainOccurrence)) {
                result.add(candidate);
            }
        }
        return result;
    }

    /**
     * 收窄绑定耗材：Charge 挂本主槽 Procedure，或账单 occurrence 与主槽秒级对齐。
     *
     * @param candidates               based_on 候选
     * @param mainCompletedProcedureId 主医嘱 COMPLETED procedureId
     * @param mainOccurrence           主槽计划点；可空则仅按 procedure_id 关联
     * @param deviceChargesByServiceId 耗材请求 id → 关联 Charge（已按 service 索引）
     * @return 本槽应回退的绑定耗材
     */
    public List<DeviceRequestListModel> filterBindingDeviceRequestsForSlot(
        List<DeviceRequestListModel> candidates,
        Long mainCompletedProcedureId,
        LocalDateTime mainOccurrence,
        Map<Long, List<ChargeItem>> deviceChargesByServiceId) {
        List<DeviceRequestListModel> result = new ArrayList<>();
        if (candidates == null || candidates.isEmpty() || mainCompletedProcedureId == null) {
            return result;
        }
        for (DeviceRequestListModel candidate : candidates) {
            if (candidate == null || candidate.getId() == null) {
                continue;
            }
            List<ChargeItem> charges = null;
            if (deviceChargesByServiceId != null) {
                charges = deviceChargesByServiceId.get(candidate.getId());
            }
            // 主路径：执行自动带耗材 Charge.procedure_id 挂主 Procedure
            if (this.hasChargeLinkedToProcedure(charges, mainCompletedProcedureId)) {
                result.add(candidate);
                continue;
            }
            // 回落：账单 occurrence 与主槽秒级对齐（历史脏数据 procedure_id 缺失时仍冲销）
            if (mainOccurrence != null
                && this.hasPendingPricingChargeAtOccurrence(charges, mainOccurrence)) {
                result.add(candidate);
            }
        }
        return result;
    }

    /**
     * 判定请求下是否存在指定 occurrence（秒级）的净 COMPLETED Procedure。
     *
     * @param requestId      请求主键
     * @param requestTable   请求表
     * @param occurrenceTime 计划点
     * @return 命中时 true
     */
    private boolean hasCompletedProcedureAtOccurrence(
        Long requestId, String requestTable, LocalDateTime occurrenceTime) {
        List<ProcedureRecordQueryModel> procedures =
            procedureQueryService.getProcedureRecords(requestId, requestTable);
        if (procedures == null || procedures.isEmpty()) {
            return false;
        }
        for (ProcedureRecordQueryModel procedure : procedures) {
            if (procedure == null || procedure.getId() == null) {
                continue;
            }
            // 仅净剩 COMPLETED 可作本槽自动带证据
            if (!EventStatus.COMPLETED.getCode().equals(procedure.getStatusEnum())) {
                continue;
            }
            // 已 CANCEL 对冲的 COMPLETED 不再回退（医嘱计费已冲销同构）
            if (this.isProcedureAlreadyOffset(requestId, requestTable, procedure.getId())) {
                continue;
            }
            // 秒级对齐：与执行列表槽位键同口径，避免纳秒差漏匹配
            if (this.planTimesEqual(occurrenceTime, procedure.getOccurrenceTime())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判定费用列表是否存在挂指定 Procedure 的正向待结算行。
     *
     * @param charges      费用行；可空
     * @param procedureId  Procedure 主键
     * @return 命中时 true
     */
    private boolean hasChargeLinkedToProcedure(List<ChargeItem> charges, Long procedureId) {
        if (charges == null || charges.isEmpty() || procedureId == null) {
            return false;
        }
        for (ChargeItem charge : charges) {
            if (charge == null) {
                continue;
            }
            if (!procedureId.equals(charge.getProcedureId())) {
                continue;
            }
            // 冲销负向留痕或非待结算不作为关联证据；反结再生正向仍可作为证据
            if (charge.isPricingOffsetLine()) {
                continue;
            }
            if (!ChargeItemStatus.PENDING_SETTLEMENT.getCode().equals(charge.getStatusEnum())) {
                continue;
            }
            // 数量非正：已是负向事实，不能再当本槽关联
            if (charge.getQuantityValue() == null || charge.getQuantityValue().signum() <= 0) {
                continue;
            }
            return true;
        }
        return false;
    }

    /**
     * 判定费用列表是否存在 occurrence 与计划点秒级对齐的待冲销正向划价行。
     *
     * @param charges          费用行；可空
     * @param occurrenceTime   主槽计划点
     * @return 命中时 true
     */
    private boolean hasPendingPricingChargeAtOccurrence(
        List<ChargeItem> charges, LocalDateTime occurrenceTime) {
        if (charges == null || charges.isEmpty() || occurrenceTime == null) {
            return false;
        }
        for (ChargeItem charge : charges) {
            if (charge == null) {
                continue;
            }
            // 护士划价/麻醉划价/医嘱执行/麻醉执行/医技划价正向待结算可作 occurrence 回落证据
            if (!GenerateSource.isNurseStationPricingFamilyCode(charge.getGenerateSourceEnum())
                && !GenerateSource.isOrderExecuteFamilyCode(charge.getGenerateSourceEnum())
                && !GenerateSource.MEDICAL_TECH_PRICING.getCode().equals(charge.getGenerateSourceEnum())) {
                continue;
            }
            // 冲销负向留痕跳过；反结再生正向（有 refund_id 但数量为正）仍可作证据
            if (charge.isPricingOffsetLine()) {
                continue;
            }
            if (!ChargeItemStatus.PENDING_SETTLEMENT.getCode().equals(charge.getStatusEnum())) {
                continue;
            }
            if (charge.getQuantityValue() == null || charge.getQuantityValue().signum() <= 0) {
                continue;
            }
            if (this.planTimesEqual(occurrenceTime, charge.getOccurrenceTime())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 计划点秒级相等（与医嘱执行列表槽位归并键同口径）。
     *
     * @param left  左时刻；可空
     * @param right 右时刻；可空
     * @return 二者非空且截断纳秒后相等
     */
    private boolean planTimesEqual(LocalDateTime left, LocalDateTime right) {
        if (left == null || right == null) {
            return false;
        }
        return left.withNano(0).equals(right.withNano(0));
    }

    /**
     * 判断 COMPLETED 行是否已被 CANCEL 对冲。
     *
     * @param requestId            请求主键
     * @param requestTable         请求表
     * @param completedProcedureId COMPLETED 行主键
     * @return 已对冲时 true
     */
    public boolean isProcedureAlreadyOffset(Long requestId, String requestTable, Long completedProcedureId) {
        List<ProcedureRecordQueryModel> siblings = procedureQueryService.getProcedureRecords(requestId, requestTable);
        for (ProcedureRecordQueryModel sibling : siblings) {
            if (sibling == null) {
                continue;
            }
            if (!EventStatus.isCancelExecutionStatusCode(sibling.getStatusEnum())) {
                continue;
            }
            if (completedProcedureId.equals(sibling.getRefundId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 追加 CANCEL 对冲 Procedure（create → start → 挂主执行人 → cancel）。
     *
     * @param completedRow               被对冲 COMPLETED 行（occurrence_time 继承来源，BR-IP-CXL-003）
     * @param statusReasonText           取消原因
     * @param operatorId                 操作人（兼 CANCEL 行主执行人）
     * @param offsetCompletedProcedureId 被对冲 COMPLETED 行主键
     * @return 新 CANCEL procedureId
     */
    public Long appendProcedureCancelOffset(
        ProcedureRecordQueryModel completedRow,
        String statusReasonText,
        Long operatorId,
        Long offsetCompletedProcedureId) {
        LocalDateTime now = LocalDateTime.now();
        // Cmd：新建 CANCEL 对冲锚点（PREPARATION）
        Long newProcedureId = procedureDomainService.createProcedureRecord(
            nurseStationOrderCancelExecuteMapper.toCreateProcedureRecordCommand(completedRow, operatorId, now));
        // Cmd：进入执行中，方可挂执行人
        procedureDomainService.startProcedureExecution(
            nurseStationOrderCancelExecuteMapper.toStartProcedureExecutionCommand(newProcedureId));
        // Cmd：取消操作人须在 CANCEL 前挂入（与执行 complete 前挂人同构；DM-CLN-004 §6.1.3）
        procedureExecutionPerformerOrchestrator.registerPrimaryPerformer(
            newProcedureId, operatorId, operatorId, now);
        // Cmd：收口 CANCEL，refund_id 指向被对冲 COMPLETED
        procedureDomainService.cancelProcedureRecord(
            nurseStationOrderCancelExecuteMapper.toCancelProcedureRecordCommand(
                newProcedureId, statusReasonText, offsetCompletedProcedureId, operatorId));
        return newProcedureId;
    }

    /**
     * 绑定诊疗回退：nurse-pricing 走冲销留痕；历史 order-execute 仍逻辑删 SR。
     *
     * @param derived          绑定 ServiceRequest
     * @param statusReasonText 取消原因（仅历史 order-execute Procedure CANCEL 文案）
     * @param operatorId       操作人
     * @param bindingCharges   绑定关联 Charge（历史 order-execute 用）
     */
    public void rollbackBindingServiceRequest(
        ServiceRequest derived,
        String statusReasonText,
        Long operatorId,
        List<ChargeItem> bindingCharges) {
        if (derived == null || derived.getId() == null) {
            return;
        }
        // 现行：执行自动带=医嘱补费 → Procedure CANCEL + 费用冲销留痕，Request 不软删
        if (GenerateSource.NURSE_PRICING.getCode().equals(derived.getGenerateSourceEnum())) {
            this.rollbackNursePricingBindingServiceRequest(derived, operatorId);
            return;
        }
        // 历史：order-execute 派生 → 逻辑删 SR + Procedure CANCEL + void Charge
        List<Long> srIds = new ArrayList<>();
        srIds.add(derived.getId());
        serviceRequestDomainService.removeExecutedServiceRequest(
            nurseStationOrderCancelExecuteMapper.toRemoveExecutedServiceRequestCommand(srIds, operatorId));
        List<ProcedureRecordQueryModel> derivedProcedures =
            procedureQueryService.getProcedureRecords(derived.getId(), TableNameConstants.WKF_SERVICE_REQUEST);
        for (ProcedureRecordQueryModel derivedProcedure : derivedProcedures) {
            if (derivedProcedure == null || derivedProcedure.getId() == null) {
                continue;
            }
            if (!EventStatus.COMPLETED.getCode().equals(derivedProcedure.getStatusEnum())) {
                continue;
            }
            if (this.isProcedureAlreadyOffset(
                derived.getId(), TableNameConstants.WKF_SERVICE_REQUEST, derivedProcedure.getId())) {
                continue;
            }
            this.appendProcedureCancelOffset(
                derivedProcedure, statusReasonText, operatorId, derivedProcedure.getId());
            // 历史 order-execute 派生：按派生 COMPLETED Procedure void 执行族费用
            this.rollbackOrderExecuteCharges(operatorId, derivedProcedure.getId());
        }
    }

    /**
     * 护士划价（执行自动带）绑定诊疗冲销：Procedure CANCEL + offsetNursePricing（Request 保持完成态）。
     *
     * @param derived    绑定诊疗请求
     * @param operatorId 操作人
     */
    public void rollbackNursePricingBindingServiceRequest(ServiceRequest derived, Long operatorId) {
        if (derived == null || derived.getId() == null) {
            return;
        }
        // Cmd：诊疗 Procedure CANCEL 对冲（与 IF-IP-PRC-06 同序）
        nurseStationInpatientBillingProcedureOrchestrator.cancelCompletedNursePricingProcedures(
            List.of(derived.getId()), operatorId);
        OffsetNursePricingChargeItemsByServiceAnchorsCommand offsetChargeCmd =
            nurseStationInpatientBillingMapper.toOffsetNursePricingChargeItemsByServiceAnchorsCommand(
                derived.getEncounterId(),
                TableNameConstants.WKF_SERVICE_REQUEST,
                derived.getId(),
                operatorId);
        // Cmd：费用冲销留痕（原行保留 + 负向行）
        chargeItemDomainService.offsetNursePricingChargeItemsByServiceAnchors(offsetChargeCmd);
    }

    /**
     * 绑定耗材回退：nurse-pricing 走划价两轨 Dispense + 费用冲销；历史 order-execute 仅回退发放。
     *
     * @param deviceReq            绑定耗材请求
     * @param operatorId           操作人
     * @param inventoryIncreaseAcc 库存回补累计 Map
     */
    public void rollbackBindingDeviceRequest(
        DeviceRequestListModel deviceReq,
        Long operatorId,
        Map<Long, BigDecimal> inventoryIncreaseAcc) {
        if (deviceReq == null || deviceReq.getId() == null) {
            return;
        }
        // 现行：补费同构 → 划价耗材 Dispense 回退 + 费用冲销；Request 不软删
        if (GenerateSource.NURSE_PRICING.getCode().equals(deviceReq.getGenerateSourceEnum())) {
            deviceDispenseNurseWorkflowOrchestrator.revertInpatientPricingDeviceDispenses(
                deviceReq.getId(), operatorId, inventoryIncreaseAcc);
            OffsetNursePricingChargeItemsByServiceAnchorsCommand offsetChargeCmd =
                nurseStationInpatientBillingMapper.toOffsetNursePricingChargeItemsByServiceAnchorsCommand(
                    deviceReq.getEncounterId(),
                    TableNameConstants.WKF_DEVICE_REQUEST,
                    deviceReq.getId(),
                    operatorId);
            chargeItemDomainService.offsetNursePricingChargeItemsByServiceAnchors(offsetChargeCmd);
            return;
        }
        // 历史：order-execute 绑定耗材仅回退发放；Charge 随主 Procedure void
        deviceDispenseNurseWorkflowOrchestrator.revertInpatientBindingDeviceDispenses(
            deviceReq.getId(), operatorId, inventoryIncreaseAcc);
    }

    /**
     * 主医嘱 Dispense 回退；SUMMARIZED 时登记重建上下文。
     *
     * <p>Charge 回退由调用方在发放循环结束后统一走 {@link #rollbackServiceChargeOnly}
     *（BR-IP-CXL-008/009），避免多发放行循环内重复 void。</p>
     *
     * @param dispense             主医嘱关联发放行（可 null，纯诊疗）
     * @param newCancelProcedureId 新 CANCEL procedureId（已发退药挂接）
     * @param operatorId           操作人
     * @param rebuildContext       汇总重建上下文
     * @return SUMMARIZED 提示；非 SUMMARIZED 时 null
     */
    public SummaryRebuildHint rollbackMainDispenseAndCharge(
        MedicationDispense dispense,
        Long newCancelProcedureId,
        Long operatorId,
        CancelExecutionSummaryRebuildContext rebuildContext) {
        SummaryRebuildHint hint = null;
        if (dispense != null && dispense.getId() != null) {
            String dispenseStatus = null;
            if (dispense.getStatusEnum() != null) {
                dispenseStatus = dispense.getStatusEnum().getCode();
            }
            if (DispenseStatus.PREPARATION.getCode().equals(dispenseStatus)) {
                // 未发药：软删执行新建发放行（长临同构，BR-IP-CXL-007）
                medicationDispenseDomainService.removeUndispensedDispense(
                    nurseStationOrderCancelExecuteMapper.toRemoveUndispensedDispenseCommand(
                        dispense.getId(), operatorId));
            } else if (DispenseStatus.SUMMARIZED.getCode().equals(dispenseStatus)) {
                // 已汇总：登记剔除集，整单撤+重建在 withdrawAndRebuildSummaryIfNeeded（BR-IP-CXL-011）
                hint = new SummaryRebuildHint();
                hint.setSummaryNo(dispense.getSummaryNo());
                hint.getExcludedDispenseIds().add(dispense.getId());
                if (rebuildContext != null && dispense.getSummaryNo() != null) {
                    rebuildContext.registerExcludedDispense(
                        dispense.getSummaryNo(), dispense.getId());
                }
            } else if (DispenseStatus.COMPLETED.getCode().equals(dispenseStatus)) {
                // 已核发：创建待退药单，procedure 挂新 CANCEL 行（BR-IP-CXL-010）
                BigDecimal qty = dispense.getQuantity();
                if (qty == null) {
                    qty = dispense.getDispenseQuantity();
                }
                medicationDispenseDomainService.createPendingRefundDispense(
                    nurseStationOrderCancelExecuteMapper.toCreatePendingRefundDispenseCommand(
                        dispense.getId(),
                        dispense.getMedReqId(),
                        newCancelProcedureId,
                        qty,
                        operatorId));
            }
        }
        return hint;
    }

    /**
     * 主医嘱 Charge 回退（有/无 Dispense 统一调用，BR-IP-CXL-008/009）。
     *
     * <p>按 procedureId 域查询执行族（{@code order-execute}/{@code anesthesia-execute}）
     * 待结算行并逻辑删除；不依赖编排预载列表。</p>
     *
     * @param operatorId           操作人
     * @param completedProcedureId 执行记录主键
     */
    public void rollbackServiceChargeOnly(Long operatorId, Long completedProcedureId) {
        this.rollbackOrderExecuteCharges(operatorId, completedProcedureId);
    }

    /**
     * 汇总过滤重建：按 summaryNo 撤整单后剔除取消行并重建 PENDING_APPROVAL 汇总单。
     *
     * @param ctx         重建上下文
     * @param operatorId  操作人
     * @param applicantId 汇总申请人（医务人员）
     * @return 新生成的 summaryNo 列表
     */
    public List<String> withdrawAndRebuildSummaryIfNeeded(
        CancelExecutionSummaryRebuildContext ctx,
        Long operatorId,
        Long applicantId) {
        List<String> rebuiltSummaryNos = new ArrayList<>();
        if (ctx == null || ctx.getExcludedDispenseIdsBySummaryNo().isEmpty()) {
            return rebuiltSummaryNos;
        }
        for (Map.Entry<String, Set<Long>> entry : ctx.getExcludedDispenseIdsBySummaryNo().entrySet()) {
            String summaryNo = entry.getKey();
            Set<Long> excludedIds = entry.getValue();
            if (StringUtils.isEmpty(summaryNo)) {
                continue;
            }
            // 18a：乐观读 Supply 态快照
            SupplyByBusNoQuery busNoQuery = new SupplyByBusNoQuery();
            busNoQuery.setBusNo(summaryNo.trim());
            List<SupplyRequestModel> supplyLines = supplyRequestQueryService.getSupplyByBusNo(busNoQuery);
            String snapshotStatus = this.resolveUniformSupplyStatus(supplyLines);
            ctx.getSupplyStatusSnapshotBySummaryNo().put(summaryNo, snapshotStatus);
            // 撤单前预读全量发放行 id（cancel 后会清 summary_no）
            List<MedicationDispenseSummaryItemQueryModel> allSummaryItems =
                medicationDispenseQueryService.listMedicationDispensesBySummaryNo(summaryNo);
            List<Long> allDispenseIds = new ArrayList<>();
            boolean anyCompletedDispense = false;
            for (MedicationDispenseSummaryItemQueryModel item : allSummaryItems) {
                if (item == null || item.getId() == null) {
                    continue;
                }
                allDispenseIds.add(item.getId());
                if (item.getStatusEnum() != null
                    && DispenseStatus.COMPLETED.equals(item.getStatusEnum())) {
                    anyCompletedDispense = true;
                }
            }
            // 18b：发放表已 COMPLETED → 拒绝撤单重建
            if (anyCompletedDispense) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_CXL_204,
                    "汇总单状态已变更，请刷新后重试");
            }
            // 写前再次读 Supply 态，并发改态则拒绝
            List<SupplyRequestModel> recheckLines = supplyRequestQueryService.getSupplyByBusNo(busNoQuery);
            String currentStatus = this.resolveUniformSupplyStatus(recheckLines);
            String priorSnapshot = ctx.getSupplyStatusSnapshotBySummaryNo().get(summaryNo);
            if (priorSnapshot != null && !priorSnapshot.equals(currentStatus)) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_CXL_204,
                    "汇总单状态已变更，请刷新后重试");
            }
            // 18c / 18d：按 Supply 态撤整单；APPROVAL 驳回后须再逻辑删 Supply，否则驳回残单仍含已取消药
            if (SupplyStatus.PENDING_APPROVAL.getCode().equals(currentStatus)) {
                medicationDispenseDomainService.cancelMedicationDispenseSummary(
                    nurseStationOrderCancelExecuteMapper.toCancelMedicationDispenseSummaryCommand(summaryNo, operatorId));
                supplyRequestDomainService.removeByBusNo(
                    nurseStationOrderCancelExecuteMapper.toRemoveByBusNoCommand(summaryNo, operatorId));
            } else if (SupplyStatus.APPROVAL.getCode().equals(currentStatus)) {
                supplyRequestDomainService.rejectRequest(
                    nurseStationOrderCancelExecuteMapper.toRejectRequestCommand(summaryNo, operatorId));
                medicationDispenseDomainService.cancelMedicationDispenseSummary(
                    nurseStationOrderCancelExecuteMapper.toCancelMedicationDispenseSummaryCommand(summaryNo, operatorId));
                // 驳回态允许删单：去掉含取消行的旧 Supply，避免汇总 Tab 仍展示「取消的药」
                supplyRequestDomainService.removeByBusNo(
                    nurseStationOrderCancelExecuteMapper.toRemoveByBusNoCommand(summaryNo, operatorId));
            } else {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_CXL_204,
                    "汇总单状态已变更，请刷新后重试");
            }
            // 18e.1：撤汇总后剔除行已回 PREPARATION，须按长临嘱删/回退，避免待发池残留重复行（BR-IP-CXL-007）
            this.rollbackExcludedDispensesAfterSummaryWithdrawal(excludedIds, operatorId);
            // 18e：剔除取消行后剩余发放行（取消行不得进入重建）
            Set<Long> excludedSafe = excludedIds;
            if (excludedSafe == null) {
                excludedSafe = new HashSet<>();
            }
            List<Long> remainingDispenseIds = new ArrayList<>();
            for (Long dispenseId : allDispenseIds) {
                if (dispenseId == null) {
                    continue;
                }
                if (!excludedSafe.contains(dispenseId)) {
                    remainingDispenseIds.add(dispenseId);
                }
            }
            if (remainingDispenseIds.isEmpty()) {
                continue;
            }
            // 18f：过滤重建（定价口径同 IF-IP-SUM-04：PENDING_SETTLEMENT + dispenseId）
            List<MedicationDispense> remainingDispenses =
                this.filterRemainingDispensesForRebuild(
                    medicationDispenseQueryService.listMedicationDispensesByIds(remainingDispenseIds),
                    excludedSafe);
            if (remainingDispenses.isEmpty()) {
                continue;
            }
            Map<Long, List<Long>> dispenseIdsByEncounter = new HashMap<>();
            for (MedicationDispense dispense : remainingDispenses) {
                if (dispense.getEncounterId() == null) {
                    continue;
                }
                List<Long> bucket = dispenseIdsByEncounter.get(dispense.getEncounterId());
                if (bucket == null) {
                    bucket = new ArrayList<>();
                    dispenseIdsByEncounter.put(dispense.getEncounterId(), bucket);
                }
                bucket.add(dispense.getId());
            }
            Map<Long, ChargeItem> chargeByDispenseId =
                inpatientMedicationDispenseChargePricingLoader.mapPendingSettlementChargeByDispenseIds(
                    dispenseIdsByEncounter);
            List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
            for (MedicationDispense dispense : remainingDispenses) {
                if (dispense.getMedicationId() == null) {
                    continue;
                }
                ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
                lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
                lookup.setProductDefinitionId(dispense.getMedicationId());
                lookups.add(lookup);
            }
            Map<String, ProductDefinitionDisplayDto> productByMedId =
                productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
            Map<Long, List<SummaryMergeGroup>> groupsByLocation =
                nurseStationDispenseSummaryWriteAssembler.buildMergeGroupsByLocationAndPrice(
                    remainingDispenses, chargeByDispenseId, productByMedId);
            for (Map.Entry<Long, List<SummaryMergeGroup>> locEntry : groupsByLocation.entrySet()) {
                Long locationId = locEntry.getKey();
                List<SummaryMergeGroup> locationGroups = locEntry.getValue();
                for (SummaryMergeGroup group : locationGroups) {
                    String productKey = ProductDefinitionKeyUtil.buildCompositeKey(
                        TableNameConstants.MED_MEDICATION_DEFINITION, group.getMedicationId());
                    ProductDefinitionDisplayDto product = productByMedId.get(productKey);
                    nurseStationDispenseSummaryWriteAssembler.validateMergeGroupUnitBands(group, product);
                }
                List<Long> locationDispenseIds = new ArrayList<>();
                for (SummaryMergeGroup group : locationGroups) {
                    if (group.getDispenseIds() != null) {
                        locationDispenseIds.addAll(group.getDispenseIds());
                    }
                }
                String newSummaryNo = supplyRequestDomainService.allocateBusNoForType(
                    SupplyType.MEDICATION_SUMMARY_ORDER.getCode());
                medicationDispenseDomainService.summarizeMedicationDispenses(
                    nurseStationOrderCancelExecuteMapper.toSummarizeMedicationDispensesCommand(
                        locationId, locationDispenseIds, operatorId, newSummaryNo));
                supplyRequestDomainService.createSummarySupplyRequest(
                    nurseStationOrderCancelExecuteMapper.toCreateSummarySupplyRequestCommand(
                        newSummaryNo, locationGroups, applicantId, operatorId));
                rebuiltSummaryNos.add(newSummaryNo);
            }
        }
        return rebuiltSummaryNos;
    }

    /**
     * 按 procedureId 逻辑删除执行族待结算 Charge（长临同构，BR-IP-CXL-008）。
     *
     * <p>域方法按 {@code order-execute}/{@code anesthesia-execute} 执行族 void；
     * 实际删行以 procedureId 域查询为准，不依赖编排预载列表是否含麻醉执行费。</p>
     *
     * @param operatorId           操作人
     * @param completedProcedureId 执行记录主键；空则跳过
     */
    private void rollbackOrderExecuteCharges(Long operatorId, Long completedProcedureId) {
        // 无执行锚点：无法按 procedure 定位，跳过
        if (completedProcedureId == null) {
            return;
        }
        // 主医嘱 Charge：按 procedureId 逻辑删执行族待结算行（含 anesthesia-execute，BR-IP-CXL-008）
        List<Long> procedureIds = new ArrayList<>();
        procedureIds.add(completedProcedureId);
        chargeItemDomainService.voidOrderExecuteChargeItemsByProcedureIds(
            nurseStationOrderCancelExecuteMapper.toVoidOrderExecuteChargeItemsByProcedureIdsCommand(
                procedureIds, operatorId));
    }

    /**
     * 撤汇总后对剔除发放行软删（与 PREPARATION 分支同构，BR-IP-CXL-007）。
     *
     * @param excludedIds  本次取消对应的发放行 id 集合
     * @param operatorId   操作人
     */
    private void rollbackExcludedDispensesAfterSummaryWithdrawal(
        Set<Long> excludedIds,
        Long operatorId) {
        if (excludedIds == null || excludedIds.isEmpty()) {
            return;
        }
        for (Long dispenseId : excludedIds) {
            if (dispenseId == null) {
                continue;
            }
            // 汇总剔除行：软删执行新建发放行（长临同构）
            medicationDispenseDomainService.removeUndispensedDispense(
                nurseStationOrderCancelExecuteMapper.toRemoveUndispensedDispenseCommand(
                    dispenseId, operatorId));
        }
    }

    /**
     * 重建前再次剔除已取消发放行（防御二次入组，避免取消药仍写入新汇总单）。
     *
     * @param loadedDispenses 按剩余 id 加载的发放行
     * @param excludedIds     本次取消剔除集
     * @return 可参与过滤重建的发放行
     */
    private List<MedicationDispense> filterRemainingDispensesForRebuild(
        List<MedicationDispense> loadedDispenses,
        Set<Long> excludedIds) {
        List<MedicationDispense> result = new ArrayList<>();
        if (loadedDispenses == null || loadedDispenses.isEmpty()) {
            return result;
        }
        Set<Long> excludedSafe = excludedIds;
        if (excludedSafe == null) {
            excludedSafe = new HashSet<>();
        }
        for (MedicationDispense dispense : loadedDispenses) {
            if (dispense == null || dispense.getId() == null) {
                continue;
            }
            // 已软删或属本次取消剔除集：不得进入新汇总
            if (Boolean.TRUE.equals(dispense.getIsDeleted())) {
                continue;
            }
            if (excludedSafe.contains(dispense.getId())) {
                continue;
            }
            result.add(dispense);
        }
        return result;
    }

    /**
     * 解析同 busNo 下 Supply 行的统一状态（不一致时取首行）。
     *
     * @param supplyLines Supply 行列表
     * @return 状态 code；无行时 null
     */
    private String resolveUniformSupplyStatus(List<SupplyRequestModel> supplyLines) {
        if (supplyLines == null || supplyLines.isEmpty()) {
            return null;
        }
        for (SupplyRequestModel line : supplyLines) {
            if (line != null && StringUtils.isNotEmpty(line.getStatusEnum())) {
                return line.getStatusEnum().trim();
            }
        }
        return null;
    }

}
