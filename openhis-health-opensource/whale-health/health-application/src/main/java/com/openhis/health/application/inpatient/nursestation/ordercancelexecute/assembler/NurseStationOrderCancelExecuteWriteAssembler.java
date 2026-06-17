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

package com.openhis.health.application.inpatient.nursestation.ordercancelexecute.assembler;

import com.openhis.health.application.inpatient.nursestation.dispensesummary.assembler.NurseStationDispenseSummaryWriteAssembler;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.assembler.NurseStationDispenseSummaryWriteAssembler.SummaryMergeGroup;
import com.openhis.health.application.inpatient.nursestation.ordercancelexecute.dto.CancelExecutionSummaryRebuildContext;
import com.openhis.health.application.inpatient.nursestation.ordercancelexecute.dto.SummaryRebuildHint;
import com.openhis.health.application.inpatient.nursestation.ordercancelexecute.mapper.NurseStationOrderCancelExecuteMapper;
import com.openhis.health.module.querysupport.device.DeviceDispenseNurseWorkflowSupport;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.openhis.health.domain.clinical.procedure.service.ProcedureDomainService;
import com.openhis.health.domain.clinical.procedure.service.ProcedureQueryService;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseSummaryItemQueryModel;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseDomainService;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.EventStatus;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.devicerequest.queries.model.DeviceRequestListModel;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.openhis.health.domain.workflow.servicerequest.entity.ServiceRequest;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestDomainService;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyByBusNoQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
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
 */
@Component
@RequiredArgsConstructor
public class NurseStationOrderCancelExecuteWriteAssembler {

    /** Procedure 写侧：CANCEL 对冲 create/start/cancel。 */
    private final ProcedureDomainService procedureDomainService;
    /** Procedure 读侧：对冲检测与绑定派生 Procedure 加载。 */
    private final ProcedureQueryService procedureQueryService;
    /** 药品发放写侧：删/回退/退药/撤汇总/重建汇总。 */
    private final MedicationDispenseDomainService medicationDispenseDomainService;
    /** 药品发放读侧：汇总行预读与重建分组。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    /** Charge 写侧：ORDER_EXECUTE 按 procedureId 逻辑删。 */
    private final ChargeItemDomainService chargeItemDomainService;
    /** Charge 读侧：过滤重建单价只读。 */
    private final ChargeItemQueryService chargeItemQueryService;
    /** 诊疗申请读侧：based_on 反查绑定 SR。 */
    private final ServiceRequestQueryService serviceRequestQueryService;
    /** 诊疗申请写侧：派生 SR 逻辑删除。 */
    private final ServiceRequestDomainService serviceRequestDomainService;
    /** 耗材请求读侧：based_on 反查绑定 DeviceRequest。 */
    private final DeviceRequestQueryService deviceRequestQueryService;
    /** 绑定耗材发放对称回退与库存累计（BR-IP-CXL-014）。 */
    private final DeviceDispenseNurseWorkflowSupport deviceDispenseNurseWorkflowSupport;
    /** Supply 读侧：汇总单态乐观校验。 */
    private final SupplyRequestQueryService supplyRequestQueryService;
    /** Supply 写侧：撤单/驳回/重建建单。 */
    private final SupplyRequestDomainService supplyRequestDomainService;
    /** 汇总过滤重建合并组（复用 AS-IP-NUR-010）。 */
    private final NurseStationDispenseSummaryWriteAssembler nurseStationDispenseSummaryWriteAssembler;
    /** 药品定义批量加载（重建分组展示校验）。 */
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
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
     * @param allCharges 主医嘱与绑定项关联的全部 Charge
     * @throws BusinessException EX-IP-CXL-203
     */
    public void assertSettlementGate(List<ChargeItem> allCharges) {
        if (allCharges == null) {
            return;
        }
        for (ChargeItem charge : allCharges) {
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
     * 按 based_on 反查绑定诊疗派生 ServiceRequest。
     *
     * @param basedOnTable 主医嘱 requestTable
     * @param basedOnIds   主医嘱 requestId 集合
     * @return 派生 ServiceRequest 列表
     */
    public List<ServiceRequest> loadBindingServiceRequests(String basedOnTable, Collection<Long> basedOnIds) {
        if (StringUtils.isBlank(basedOnTable) || basedOnIds == null || basedOnIds.isEmpty()) {
            return new ArrayList<>();
        }
        return serviceRequestQueryService.listServiceRequestsByBasedOnAnchors(basedOnTable, basedOnIds);
    }

    /**
     * 按 based_on 反查绑定耗材 DeviceRequest。
     *
     * @param basedOnTable 主医嘱 requestTable
     * @param basedOnIds   主医嘱 requestId 集合
     * @return 绑定耗材请求列表
     */
    public List<DeviceRequestListModel> loadBindingDeviceRequests(String basedOnTable, Collection<Long> basedOnIds) {
        if (StringUtils.isBlank(basedOnTable) || basedOnIds == null || basedOnIds.isEmpty()) {
            return new ArrayList<>();
        }
        return deviceRequestQueryService.listDeviceRequestsByBasedOnAnchors(basedOnTable, basedOnIds);
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
     * 追加 CANCEL 对冲 Procedure（create → start → cancel）。
     *
     * @param completedRow               被对冲 COMPLETED 行（occurrence_time 继承来源，BR-IP-CXL-003）
     * @param statusReasonText           取消原因
     * @param operatorId                 操作人
     * @param offsetCompletedProcedureId 被对冲 COMPLETED 行主键
     * @return 新 CANCEL procedureId
     */
    public Long appendProcedureCancelOffset(
        ProcedureRecordQueryModel completedRow,
        String statusReasonText,
        Long operatorId,
        Long offsetCompletedProcedureId) {
        LocalDateTime now = LocalDateTime.now();
        Long newProcedureId = procedureDomainService.createProcedureRecord(
            nurseStationOrderCancelExecuteMapper.toCreateProcedureRecordCommand(completedRow, operatorId, now));
        procedureDomainService.startProcedureExecution(
            nurseStationOrderCancelExecuteMapper.toStartProcedureExecutionCommand(newProcedureId));
        procedureDomainService.cancelProcedureRecord(
            nurseStationOrderCancelExecuteMapper.toCancelProcedureRecordCommand(
                newProcedureId, statusReasonText, offsetCompletedProcedureId, operatorId));
        return newProcedureId;
    }

    /**
     * 绑定诊疗派生 SR 回退：逻辑删 + 派生 Procedure CANCEL + Charge 分轨。
     *
     * @param derived          派生 ServiceRequest
     * @param statusReasonText 取消原因
     * @param operatorId       操作人
     * @param bindingCharges   派生关联 Charge
     */
    public void rollbackBindingServiceRequest(
        ServiceRequest derived,
        String statusReasonText,
        Long operatorId,
        List<ChargeItem> bindingCharges) {
        if (derived == null || derived.getId() == null) {
            return;
        }
        // 写库点 A：派生 SR 逻辑删除
        List<Long> srIds = new ArrayList<>();
        srIds.add(derived.getId());
        serviceRequestDomainService.removeExecutedServiceRequest(
            nurseStationOrderCancelExecuteMapper.toRemoveExecutedServiceRequestCommand(srIds, operatorId));
        // 派生 Procedure 若 COMPLETED 则追加 CANCEL 对冲
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
            List<ChargeItem> derivedCharges = this.filterChargesByProcedureId(bindingCharges, derivedProcedure.getId());
            this.rollbackOrderExecuteCharges(derivedCharges, operatorId, derivedProcedure.getId());
        }
        if (derivedProcedures.isEmpty()) {
            this.rollbackOrderExecuteCharges(bindingCharges, operatorId, null);
        }
    }

    /**
     * 绑定耗材回退：DeviceRequest 不变，仅回退发放行并累计库存回补。
     *
     * @param deviceReq                绑定耗材请求
     * @param operatorId               操作人
     * @param inventoryIncreaseAcc     库存回补累计 Map
     */
    public void rollbackBindingDeviceRequest(
        DeviceRequestListModel deviceReq,
        Long operatorId,
        Map<Long, BigDecimal> inventoryIncreaseAcc) {
        if (deviceReq == null || deviceReq.getId() == null) {
            return;
        }
        deviceDispenseNurseWorkflowSupport.revertInpatientBindingDeviceDispenses(
            deviceReq.getId(), operatorId, inventoryIncreaseAcc);
    }

    /**
     * 主医嘱 Dispense/Charge 回退；SUMMARIZED 时登记重建上下文。
     *
     * @param dispense              主医嘱关联发放行（可 null，纯诊疗）
     * @param completedProcedureId  原 COMPLETED procedureId
     * @param newCancelProcedureId  新 CANCEL procedureId
     * @param operatorId            操作人
     * @param charges               主医嘱 Charge 列表
     * @param rebuildContext        汇总重建上下文
     * @return SUMMARIZED 提示；非 SUMMARIZED 时 null
     */
    public SummaryRebuildHint rollbackMainDispenseAndCharge(
        MedicationDispense dispense,
        Long completedProcedureId,
        Long newCancelProcedureId,
        Long operatorId,
        List<ChargeItem> charges,
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
        // 主医嘱 Charge 回退（BR-IP-CXL-008/009）
        this.rollbackOrderExecuteCharges(charges, operatorId, completedProcedureId);
        return hint;
    }

    /**
     * 纯诊疗主医嘱 Charge 回退（无 Dispense 时单独调用，BR-IP-CXL-009）。
     *
     * @param charges              Charge 列表
     * @param operatorId           操作人
     * @param completedProcedureId 执行记录主键
     */
    public void rollbackServiceChargeOnly(
        List<ChargeItem> charges,
        Long operatorId,
        Long completedProcedureId) {
        this.rollbackOrderExecuteCharges(charges, operatorId, completedProcedureId);
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
            if (StringUtils.isBlank(summaryNo)) {
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
            // 18b：AGREE 或已核发发放 → 拒绝
            if (SupplyStatus.AGREE.getCode().equals(snapshotStatus) || anyCompletedDispense) {
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
            // 18c / 18d：按 Supply 态撤整单
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
            } else {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_CXL_204,
                    "汇总单状态已变更，请刷新后重试");
            }
            // 18e.1：撤汇总后剔除行已回 PREPARATION，须按长临嘱删/回退，避免待发池残留重复行（BR-IP-CXL-007）
            this.rollbackExcludedDispensesAfterSummaryWithdrawal(excludedIds, operatorId);
            // 18e：剔除取消行后剩余发放行
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
            // 18f：过滤重建
            List<MedicationDispense> remainingDispenses =
                medicationDispenseQueryService.listMedicationDispensesByIds(remainingDispenseIds);
            Map<Long, List<Long>> dispenseIdsByEncounter = new HashMap<>();
            for (MedicationDispense dispense : remainingDispenses) {
                if (dispense == null || dispense.getId() == null || dispense.getEncounterId() == null) {
                    continue;
                }
                List<Long> bucket = dispenseIdsByEncounter.get(dispense.getEncounterId());
                if (bucket == null) {
                    bucket = new ArrayList<>();
                    dispenseIdsByEncounter.put(dispense.getEncounterId(), bucket);
                }
                bucket.add(dispense.getId());
            }
            Map<Long, ChargeItem> chargeByDispenseId = new HashMap<>();
            for (Map.Entry<Long, List<Long>> encEntry : dispenseIdsByEncounter.entrySet()) {
                List<ChargeItem> pricingRows = chargeItemQueryService.listChargeItemsForMedicationDispensePricing(
                    encEntry.getKey(), encEntry.getValue());
                for (ChargeItem charge : pricingRows) {
                    if (charge == null || charge.getServiceId() == null) {
                        continue;
                    }
                    chargeByDispenseId.put(charge.getServiceId(), charge);
                }
            }
            List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
            for (MedicationDispense dispense : remainingDispenses) {
                if (dispense == null || dispense.getMedicationId() == null) {
                    continue;
                }
                ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
                lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
                lookup.setProductDefinitionId(dispense.getMedicationId());
                lookups.add(lookup);
            }
            Map<String, ProductDefinitionDisplayDto> productByMedId =
                productDefinitionBatchLoader.loadAsMap(lookups);
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
                String newSummaryNo = medicationDispenseDomainService.summarizeMedicationDispenses(
                    nurseStationOrderCancelExecuteMapper.toSummarizeMedicationDispensesCommand(
                        locationId, locationDispenseIds, operatorId));
                supplyRequestDomainService.createSummarySupplyRequest(
                    nurseStationOrderCancelExecuteMapper.toCreateSummarySupplyRequestCommand(
                        newSummaryNo, locationGroups, applicantId, operatorId));
                rebuiltSummaryNos.add(newSummaryNo);
            }
        }
        return rebuiltSummaryNos;
    }

    /**
     * 按 procedureId 逻辑删除 ORDER_EXECUTE 待结算 Charge（长临同构，BR-IP-CXL-008）。
     *
     * @param charges              Charge 列表（当前实现按 completedProcedureId 过滤后调用域批量删）
     * @param operatorId           操作人
     * @param completedProcedureId 执行记录主键
     */
    private void rollbackOrderExecuteCharges(
        List<ChargeItem> charges,
        Long operatorId,
        Long completedProcedureId) {
        // 主医嘱 Charge：按 procedureId 逻辑删 ORDER_EXECUTE 行（长临同构，BR-IP-CXL-008）
        if (charges == null || charges.isEmpty()) {
            return;
        }
        if (completedProcedureId == null) {
            return;
        }
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
            if (line != null && StringUtils.isNotBlank(line.getStatusEnum())) {
                return line.getStatusEnum().trim();
            }
        }
        return null;
    }

    /**
     * 按 procedureId 过滤 Charge 列表。
     *
     * @param charges     源 Charge 列表
     * @param procedureId 执行记录主键
     * @return 匹配的 Charge 子集
     */
    private List<ChargeItem> filterChargesByProcedureId(List<ChargeItem> charges, Long procedureId) {
        List<ChargeItem> matched = new ArrayList<>();
        if (charges == null || procedureId == null) {
            return matched;
        }
        for (ChargeItem charge : charges) {
            if (charge == null) {
                continue;
            }
            if (procedureId.equals(charge.getProcedureId())) {
                matched.add(charge);
            }
        }
        return matched;
    }
}
