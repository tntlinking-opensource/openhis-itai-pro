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

package com.openhis.health.application.inpatient.nursestation.ordercancelexecute.service;

import com.openhis.health.module.common.validator.CurrentManagedWardLocationIdsResolver;
import com.openhis.health.module.common.validator.CurrentPractitionerResolver;
import com.openhis.health.application.inpatient.nursestation.ordercancelexecute.assembler.NurseStationOrderCancelExecuteWriteAssembler;
import com.openhis.health.application.inpatient.nursestation.ordercancelexecute.dto.CancelExecutionSummaryRebuildContext;
import com.openhis.health.application.inpatient.nursestation.ordercancelexecute.mapper.NurseStationOrderCancelExecuteMapper;
import com.openhis.health.application.inpatient.nursestation.ordercancelexecute.request.CancelNurseStationOrderExecutionsRequest;
import com.openhis.health.application.inpatient.nursestation.ordercancelexecute.response.CancelNurseStationOrderExecutionsResponse;
import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.module.querysupport.nursestation.InpatientNurseStationTcmSupport;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.clinical.procedure.queries.model.ProcedureDetailQueryModel;
import com.openhis.health.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.openhis.health.domain.clinical.procedure.service.ProcedureQueryService;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.EventStatus;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemDomainService;
import com.openhis.health.domain.workflow.servicerequest.entity.ServiceRequest;
import com.openhis.health.domain.workflow.devicerequest.queries.model.DeviceRequestListModel;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 住院护士站取消执行应用服务（AS-IP-NUR-012，IF-IP-CXL-01）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li><b>IF-IP-CXL-01</b>：已执行 Tab 批量取消——绑定项回退 → CANCEL 对冲 → Dispense/Charge 分轨 → 汇总过滤重建</li>
 *   <li>{@link EncounterContextFacade} + 病区 Resolver：就诊可见性 Gate（BR-IP-CXL-001）</li>
 *   <li>{@link NurseStationOrderCancelExecuteWriteAssembler}：结算 Gate、绑定/Dispense/Charge/汇总重建写编排</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} →
 * {@code POST /api/app/nurseStationOrderCancelExecute/cancelNurseStationOrderExecutions}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NurseStationOrderCancelExecuteAppService {

    /** 写路径操作人门控（B.6.1）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;
    /** 写路径医务人员/applicantId 解析（汇总重建建单）。 */
    /** 护士站管辖病区收窄（BR-IP-CXL-001）。 */
    private final CurrentManagedWardLocationIdsResolver managedWardLocationIdsResolver;
    /** 住院就诊上下文可见性。 */
    private final EncounterContextFacade encounterContextFacade;
    /** Procedure 读侧：Pre 加载与对冲检测。 */
    private final ProcedureQueryService procedureQueryService;
    /** 药品发放读侧：Pre 索引与汇总重建。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    /** Charge 读侧：Pre 结算 Gate 与回退定位。 */
    private final ChargeItemQueryService chargeItemQueryService;
    /** 库存回补写侧（绑定耗材已发路径）。 */
    private final InventoryItemDomainService inventoryItemDomainService;
    /** 字段映射：Request/QueryModel → Command、Response 装配。 */
    private final NurseStationOrderCancelExecuteMapper nurseStationOrderCancelExecuteMapper;
    /** 写路径专项编排：Gate 后 Cmd 分轨与汇总重建。 */
    private final NurseStationOrderCancelExecuteWriteAssembler nurseStationOrderCancelExecuteWriteAssembler;

    /** 中药整方扩行（同 planTime 取消执行）。 */
    private final InpatientNurseStationTcmSupport inpatientNurseStationTcmSupport;

    /** 药品申请读侧（方级扩行索引）。 */
    private final MedicationRequestQueryService medicationRequestQueryService;

    /**
     * IF-IP-CXL-01：批量取消已执行医嘱计划点。
     *
     * @param request 就诊、procedureId 与取消原因
     * @return 成功对冲 id 与可选重建 summaryNo
     * @throws BusinessException EX-IP-CXL-201～205
     */
    public CancelNurseStationOrderExecutionsResponse cancelNurseStationOrderExecutions(
        CancelNurseStationOrderExecutionsRequest request) {
        try {
            // Gate ①：入参非空、procedureId 去重、批上限、取消原因（EX-IP-CXL-201）
            if (request == null) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_CXL_201,
                    "请选择已执行且未取消的计划点");
            }
            List<Long> distinctProcedureIds =
                nurseStationOrderCancelExecuteWriteAssembler.normalizeDistinctProcedureIds(request.getProcedureIds());
            this.assertCancelExecuteGate(request, distinctProcedureIds);
            List<Long> requestedEncounterIds = this.distinctEncounterIds(request.getEncounterIds());
            // Gate ②：就诊须在护士管辖病区可见范围内（BR-IP-CXL-001）
            Set<Long> visibleEncounterIds = this.resolveVisibleEncounterIdSet(requestedEncounterIds);
            if (visibleEncounterIds.isEmpty()) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_CXL_201,
                    "请选择已执行且未取消的计划点");
            }
            // Pre·中药方：按 prescription_no + occurrence 扩全方已执行 procedureId（扩行前 Gate 已校验条数）
            List<Long> expandedProcedureIds =
                this.expandCancelProcedureIdsByTcmPrescription(distinctProcedureIds, visibleEncounterIds);
            Long operatorId = currentPractitionerResolver.requireOperatorId(
                InpatientNurseStationConstants.EX_IP_CXL_201,
                "未登录或登录已失效，无法取消执行");
            Long applicantId = currentPractitionerResolver.requirePractitionerId(
                InpatientNurseStationConstants.EX_IP_CXL_201,
                "未解析到当前医务人员，无法取消执行");
            String statusReasonText = request.getStatusReasonText().trim();
            // Pre ③：批量加载 COMPLETED 行并校验可取消（EX-IP-CXL-202）
            Map<Long, ProcedureRecordQueryModel> completedRowByProcedureId = new HashMap<>();
            Map<String, Set<Long>> basedOnIdsByTable = new HashMap<>();
            for (Long procedureId : expandedProcedureIds) {
                ProcedureRecordQueryModel row = this.mustResolveCancellableCompletedProcedure(
                    procedureId, visibleEncounterIds);
                completedRowByProcedureId.put(procedureId, row);
                if (row.getRequestTable() != null && row.getRequestId() != null) {
                    Set<Long> bucket = basedOnIdsByTable.get(row.getRequestTable());
                    if (bucket == null) {
                        bucket = new HashSet<>();
                        basedOnIdsByTable.put(row.getRequestTable(), bucket);
                    }
                    bucket.add(row.getRequestId());
                }
            }
            // Pre ⑤⑥：主医嘱发放行与 Charge 索引
            List<MedicationDispense> dispensesByProcedure =
                medicationDispenseQueryService.listMedicationDispensesByProcedureIds(expandedProcedureIds);
            Map<Long, List<MedicationDispense>> dispensesByProcedureId = new HashMap<>();
            for (MedicationDispense dispense : dispensesByProcedure) {
                if (dispense == null || dispense.getProcedureId() == null) {
                    continue;
                }
                List<MedicationDispense> bucket = dispensesByProcedureId.get(dispense.getProcedureId());
                if (bucket == null) {
                    bucket = new ArrayList<>();
                    dispensesByProcedureId.put(dispense.getProcedureId(), bucket);
                }
                bucket.add(dispense);
            }
            List<ChargeItem> mainCharges =
                chargeItemQueryService.listChargeItemsByProcedureIds(expandedProcedureIds);
            Map<Long, List<ChargeItem>> chargesByProcedureId = this.indexChargesByProcedureId(mainCharges);
            // Pre ⑧⑨：based_on 反查绑定诊疗/耗材（按 requestTable 批量 IN）
            List<ServiceRequest> bindingServiceRequests = new ArrayList<>();
            List<DeviceRequestListModel> bindingDeviceRequests = new ArrayList<>();
            for (Map.Entry<String, Set<Long>> basedOnEntry : basedOnIdsByTable.entrySet()) {
                String basedOnTable = basedOnEntry.getKey();
                Set<Long> basedOnIds = basedOnEntry.getValue();
                bindingServiceRequests.addAll(
                    nurseStationOrderCancelExecuteWriteAssembler.loadBindingServiceRequests(
                        basedOnTable, basedOnIds));
                bindingDeviceRequests.addAll(
                    nurseStationOrderCancelExecuteWriteAssembler.loadBindingDeviceRequests(
                        basedOnTable, basedOnIds));
            }
            // Pre ⑩：绑定 Charge / 派生 Procedure 预加载并入结算 Gate
            Map<Long, Set<Long>> bindingProcedureIdsByServiceRequestId =
                this.indexBindingProcedureIdsByServiceRequestId(bindingServiceRequests);
            List<ChargeItem> allChargesForGate = new ArrayList<>(mainCharges);
            List<Long> bindingProcedureIds = new ArrayList<>();
            for (Set<Long> procedureIdSet : bindingProcedureIdsByServiceRequestId.values()) {
                if (procedureIdSet != null) {
                    bindingProcedureIds.addAll(procedureIdSet);
                }
            }
            if (!bindingProcedureIds.isEmpty()) {
                allChargesForGate.addAll(chargeItemQueryService.listChargeItemsByProcedureIds(bindingProcedureIds));
            }
            // Pre ⑦：结算 Gate（EX-IP-CXL-203）
            nurseStationOrderCancelExecuteWriteAssembler.assertSettlementGate(allChargesForGate);
            Map<Long, List<ChargeItem>> bindingChargesByServiceRequestId =
                this.indexBindingChargesByServiceRequestId(allChargesForGate, bindingProcedureIdsByServiceRequestId);
            CancelExecutionSummaryRebuildContext rebuildContext = new CancelExecutionSummaryRebuildContext();
            Map<Long, BigDecimal> inventoryIncreaseAcc = new HashMap<>();
            // Cmd ⑪～⑬：先回退绑定项（BR-IP-CXL-006）
            for (ServiceRequest derived : bindingServiceRequests) {
                List<ChargeItem> derivedCharges = bindingChargesByServiceRequestId.get(derived.getId());
                if (derivedCharges == null) {
                    derivedCharges = new ArrayList<>();
                }
                nurseStationOrderCancelExecuteWriteAssembler.rollbackBindingServiceRequest(
                    derived, statusReasonText, operatorId, derivedCharges);
            }
            for (DeviceRequestListModel deviceReq : bindingDeviceRequests) {
                nurseStationOrderCancelExecuteWriteAssembler.rollbackBindingDeviceRequest(
                    deviceReq, operatorId, inventoryIncreaseAcc);
            }
            // Cmd ⑭：绑定耗材已发库存回补
            if (!inventoryIncreaseAcc.isEmpty()) {
                inventoryItemDomainService.increaseQuantities(
                    nurseStationOrderCancelExecuteMapper.toIncreaseQuantityCommand(operatorId, inventoryIncreaseAcc));
            }
            List<Long> cancelledProcedureIds = new ArrayList<>();
            // Cmd ⑮～⑰：主医嘱 CANCEL 对冲 + Dispense/Charge 回退（按入参稳定顺序）
            for (Long completedProcedureId : expandedProcedureIds) {
                ProcedureRecordQueryModel completedRow = completedRowByProcedureId.get(completedProcedureId);
                if (completedRow == null) {
                    continue;
                }
                // 写库点 C：追加 CANCEL 对冲（occurrence_time 继承 COMPLETED 行，BR-IP-CXL-003）
                Long newCancelProcedureId = nurseStationOrderCancelExecuteWriteAssembler.appendProcedureCancelOffset(
                    completedRow, statusReasonText, operatorId, completedProcedureId);
                List<MedicationDispense> dispenses = dispensesByProcedureId.get(completedProcedureId);
                List<ChargeItem> procedureCharges = chargesByProcedureId.get(completedProcedureId);
                if (procedureCharges == null) {
                    procedureCharges = new ArrayList<>();
                }
                if (dispenses != null && !dispenses.isEmpty()) {
                    List<ChargeItem> emptyChargesForDispenseLoop = Collections.emptyList();
                    for (MedicationDispense dispense : dispenses) {
                        nurseStationOrderCancelExecuteWriteAssembler.rollbackMainDispenseAndCharge(
                            dispense,
                            completedProcedureId,
                            newCancelProcedureId,
                            operatorId,
                            emptyChargesForDispenseLoop,
                            rebuildContext);
                    }
                    nurseStationOrderCancelExecuteWriteAssembler.rollbackServiceChargeOnly(
                        procedureCharges,
                        operatorId,
                        completedProcedureId);
                } else {
                    nurseStationOrderCancelExecuteWriteAssembler.rollbackServiceChargeOnly(
                        procedureCharges,
                        operatorId,
                        completedProcedureId);
                }
                cancelledProcedureIds.add(completedProcedureId);
            }
            // Cmd ⑱：汇总过滤重建
            List<String> rebuiltSummaryNos = nurseStationOrderCancelExecuteWriteAssembler.withdrawAndRebuildSummaryIfNeeded(
                rebuildContext, operatorId, applicantId);
            log.info(
                "NurseStationOrderCancelExecute operatorId={} encounterIds={} procedureIds={} rebuiltSummaryNos={}",
                operatorId,
                requestedEncounterIds,
                expandedProcedureIds,
                rebuiltSummaryNos);
            return nurseStationOrderCancelExecuteMapper.toCancelNurseStationOrderExecutionsResponse(
                cancelledProcedureIds, rebuiltSummaryNos);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("cancelNurseStationOrderExecutions failed", ex);
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_CXL_205,
                "取消执行失败，请稍后重试");
        }
    }

    /**
     * Gate：取消执行入参组合校验（EX-IP-CXL-201）。
     *
     * @param request               HTTP 请求
     * @param distinctProcedureIds  去重后的 procedureId
     */
    private void assertCancelExecuteGate(
        CancelNurseStationOrderExecutionsRequest request,
        List<Long> distinctProcedureIds) {
        if (request == null) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_CXL_201,
                "请选择已执行且未取消的计划点");
        }
        if (request.getEncounterIds() == null || request.getEncounterIds().isEmpty()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_CXL_201,
                "请选择已执行且未取消的计划点");
        }
        if (request.getProcedureIds() == null || request.getProcedureIds().isEmpty()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_CXL_201,
                "请选择已执行且未取消的计划点");
        }
        if (distinctProcedureIds.isEmpty()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_CXL_201,
                "请选择已执行且未取消的计划点");
        }
        int nonNullCount = 0;
        for (Long procedureId : request.getProcedureIds()) {
            if (procedureId != null) {
                nonNullCount++;
            }
        }
        if (distinctProcedureIds.size() != nonNullCount) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_CXL_201,
                "请选择已执行且未取消的计划点");
        }
        if (distinctProcedureIds.size() > InpatientNurseStationConstants.ORDER_CANCEL_EXECUTE_BATCH_MAX) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_CXL_201,
                "请选择已执行且未取消的计划点");
        }
        if (StringUtils.isBlank(request.getStatusReasonText())) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_CXL_201,
                "请选择已执行且未取消的计划点");
        }
    }

    /**
     * 解析前端勾选就诊在护士站可见范围内的 id 集合。
     *
     * @param requestedEncounterIds 前端勾选就诊
     * @return 可见 encounterId 集合
     */
    private Set<Long> resolveVisibleEncounterIdSet(List<Long> requestedEncounterIds) {
        List<Long> managedWardIds = managedWardLocationIdsResolver.listManagedWardLocationIdsOrEmpty();
        InpatientEncounterPageQuery visibilityQuery =
            nurseStationOrderCancelExecuteMapper.toInpatientEncounterPageQuery(requestedEncounterIds, managedWardIds);
        List<InpatientEncounterContextQueryModel> visibleRows =
            encounterContextFacade.listInpatientEncounterContexts(visibilityQuery);
        Set<Long> visibleIdSet = new HashSet<>();
        if (visibleRows != null) {
            for (InpatientEncounterContextQueryModel row : visibleRows) {
                if (row != null && row.getEncounterId() != null) {
                    visibleIdSet.add(row.getEncounterId());
                }
            }
        }
        Set<Long> result = new LinkedHashSet<>();
        for (Long encounterId : requestedEncounterIds) {
            if (encounterId != null && visibleIdSet.contains(encounterId)) {
                result.add(encounterId);
            }
        }
        return result;
    }

    /**
     * 校验并加载可取消的 COMPLETED 执行记录。
     *
     * @param procedureId          待取消 procedureId
     * @param visibleEncounterIds  可见就诊集合
     * @return COMPLETED 行
     */
    private ProcedureRecordQueryModel mustResolveCancellableCompletedProcedure(
        Long procedureId,
        Set<Long> visibleEncounterIds) {
        ProcedureDetailQueryModel detail = procedureQueryService.loadProcedureById(procedureId);
        if (detail == null || detail.getProcedure() == null) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_CXL_202,
                "该计划点不可取消，请刷新后重试");
        }
        ProcedureRecordQueryModel row = detail.getProcedure();
        if (!EventStatus.COMPLETED.getCode().equals(row.getStatusEnum())) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_CXL_202,
                "该计划点不可取消，请刷新后重试");
        }
        if (row.getEncounterId() == null || !visibleEncounterIds.contains(row.getEncounterId())) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_CXL_202,
                "该计划点不可取消，请刷新后重试");
        }
        if (!this.isNurseStationCancelProcedureRequestTable(row.getRequestTable())) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_CXL_202,
                "该计划点不可取消，请刷新后重试");
        }
        if (nurseStationOrderCancelExecuteWriteAssembler.isProcedureAlreadyOffset(
            row.getRequestId(), row.getRequestTable(), procedureId)) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_CXL_202,
                "该计划点不可取消，请刷新后重试");
        }
        return row;
    }

    /**
     * 判断 requestTable 是否属于护士站药/诊取消执行链路。
     *
     * @param requestTable 请求表名
     * @return 允许时 true
     */
    private boolean isNurseStationCancelProcedureRequestTable(String requestTable) {
        if (TableNameConstants.MED_MEDICATION_REQUEST.equals(requestTable)) {
            return true;
        }
        if (TableNameConstants.WKF_SERVICE_REQUEST.equals(requestTable)) {
            return true;
        }
        return false;
    }

    /**
     * encounterIds 去重并保持顺序。
     *
     * @param encounterIds 原始列表
     * @return 去重列表
     */
    private List<Long> distinctEncounterIds(List<Long> encounterIds) {
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        if (encounterIds != null) {
            for (Long encounterId : encounterIds) {
                if (encounterId != null) {
                    distinct.add(encounterId);
                }
            }
        }
        return new ArrayList<>(distinct);
    }

    /**
     * 按 procedureId 索引 Charge 列表。
     *
     * @param charges Charge 全量
     * @return procedureId → Charge 列表
     */
    private Map<Long, List<ChargeItem>> indexChargesByProcedureId(List<ChargeItem> charges) {
        Map<Long, List<ChargeItem>> index = new HashMap<>();
        if (charges == null) {
            return index;
        }
        for (ChargeItem charge : charges) {
            if (charge == null || charge.getProcedureId() == null) {
                continue;
            }
            List<ChargeItem> bucket = index.get(charge.getProcedureId());
            if (bucket == null) {
                bucket = new ArrayList<>();
                index.put(charge.getProcedureId(), bucket);
            }
            bucket.add(charge);
        }
        return index;
    }

    /**
     * 绑定诊疗 SR → 其派生 Procedure 主键集合。
     *
     * @param bindingServiceRequests 绑定诊疗列表
     * @return serviceRequestId → 派生 procedureId 集合
     */
    private Map<Long, Set<Long>> indexBindingProcedureIdsByServiceRequestId(
        List<ServiceRequest> bindingServiceRequests) {
        Map<Long, Set<Long>> index = new HashMap<>();
        if (bindingServiceRequests == null) {
            return index;
        }
        for (ServiceRequest derived : bindingServiceRequests) {
            if (derived == null || derived.getId() == null) {
                continue;
            }
            List<ProcedureRecordQueryModel> derivedProcedures = procedureQueryService.getProcedureRecords(
                derived.getId(), TableNameConstants.WKF_SERVICE_REQUEST);
            Set<Long> procedureIdSet = new HashSet<>();
            for (ProcedureRecordQueryModel derivedProcedure : derivedProcedures) {
                if (derivedProcedure != null && derivedProcedure.getId() != null) {
                    procedureIdSet.add(derivedProcedure.getId());
                }
            }
            index.put(derived.getId(), procedureIdSet);
        }
        return index;
    }

    /**
     * 绑定诊疗 SR → 其派生 Procedure 关联 Charge 索引。
     *
     * @param allCharges                            全部 Charge（含绑定）
     * @param bindingProcedureIdsByServiceRequestId 派生 procedureId 索引
     * @return serviceRequestId → Charge 列表
     */
    private Map<Long, List<ChargeItem>> indexBindingChargesByServiceRequestId(
        List<ChargeItem> allCharges,
        Map<Long, Set<Long>> bindingProcedureIdsByServiceRequestId) {
        Map<Long, List<ChargeItem>> index = new HashMap<>();
        if (bindingProcedureIdsByServiceRequestId == null || allCharges == null) {
            return index;
        }
        Map<Long, Long> serviceRequestIdByProcedureId = new HashMap<>();
        for (Map.Entry<Long, Set<Long>> entry : bindingProcedureIdsByServiceRequestId.entrySet()) {
            Long serviceRequestId = entry.getKey();
            Set<Long> procedureIdSet = entry.getValue();
            if (procedureIdSet == null) {
                continue;
            }
            for (Long procedureId : procedureIdSet) {
                if (procedureId != null) {
                    serviceRequestIdByProcedureId.put(procedureId, serviceRequestId);
                }
            }
        }
        for (ChargeItem charge : allCharges) {
            if (charge == null || charge.getProcedureId() == null) {
                continue;
            }
            Long serviceRequestId = serviceRequestIdByProcedureId.get(charge.getProcedureId());
            if (serviceRequestId == null) {
                continue;
            }
            List<ChargeItem> bucket = index.get(serviceRequestId);
            if (bucket == null) {
                bucket = new ArrayList<>();
                index.put(serviceRequestId, bucket);
            }
            bucket.add(charge);
        }
        return index;
    }

    /**
     * 中药方勾选 procedureId 时按 prescription_no + occurrence 扩成全方同计划点可取消 procedureId。
     *
     * @param seedProcedureIds     前端勾选 procedureId（已去重）
     * @param visibleEncounterIds  可见就诊
     * @return 扩行后的 procedureId（去重保序）
     */
    private List<Long> expandCancelProcedureIdsByTcmPrescription(
        List<Long> seedProcedureIds,
        Set<Long> visibleEncounterIds) {
        List<Long> expanded = new ArrayList<>();
        if (seedProcedureIds == null || seedProcedureIds.isEmpty()) {
            return expanded;
        }
        Set<Long> encounterIdsForMedLoad = new LinkedHashSet<>();
        Map<Long, ProcedureRecordQueryModel> seedRowByProcedureId = new LinkedHashMap<>();
        for (Long seedProcedureId : seedProcedureIds) {
            if (seedProcedureId == null) {
                continue;
            }
            ProcedureDetailQueryModel detail = procedureQueryService.loadProcedureById(seedProcedureId);
            if (detail == null || detail.getProcedure() == null) {
                continue;
            }
            ProcedureRecordQueryModel row = detail.getProcedure();
            seedRowByProcedureId.put(seedProcedureId, row);
            if (row.getEncounterId() != null && visibleEncounterIds.contains(row.getEncounterId())) {
                encounterIdsForMedLoad.add(row.getEncounterId());
            }
        }
        Map<Long, List<MedicationRequestListQueryModel>> medByEncounter =
            this.loadMedByEncounterForCancelWrite(encounterIdsForMedLoad);
        Set<String> dedupe = new LinkedHashSet<>();
        for (Long seedProcedureId : seedProcedureIds) {
            if (seedProcedureId == null) {
                continue;
            }
            ProcedureRecordQueryModel seedRow = seedRowByProcedureId.get(seedProcedureId);
            if (seedRow == null) {
                continue;
            }
            if (!TableNameConstants.MED_MEDICATION_REQUEST.equals(seedRow.getRequestTable())) {
                // 诊疗计划点不参与方级扩行
                this.appendCancelProcedureDedupe(expanded, dedupe, seedProcedureId);
                continue;
            }
            Long encounterId = seedRow.getEncounterId();
            Long seedRequestId = seedRow.getRequestId();
            LocalDateTime occurrenceTime = seedRow.getOccurrenceTime();
            List<MedicationRequestListQueryModel> encounterMeds = null;
            if (encounterId != null) {
                encounterMeds = medByEncounter.get(encounterId);
            }
            MedicationRequestListQueryModel seedMed = this.findMedDetail(seedRequestId, encounterMeds);
            if (seedMed == null || !Boolean.TRUE.equals(seedMed.getTcmFlag())) {
                // 非中药或明细缺失时仅取消勾选行
                this.appendCancelProcedureDedupe(expanded, dedupe, seedProcedureId);
                continue;
            }
            List<Long> requestIds = inpatientNurseStationTcmSupport.expandMedRequestIdsByPrescriptionNo(
                encounterId, seedRequestId, encounterMeds);
            boolean expandedAny = false;
            for (Long requestId : requestIds) {
                Long resolvedProcedureId = this.resolveNetCompletedProcedureIdForCancel(
                    requestId,
                    TableNameConstants.MED_MEDICATION_REQUEST,
                    occurrenceTime);
                if (resolvedProcedureId == null) {
                    continue;
                }
                this.appendCancelProcedureDedupe(expanded, dedupe, resolvedProcedureId);
                expandedAny = true;
            }
            if (!expandedAny) {
                // 方内无同 occurrence 可取消行时回退种子 procedureId
                this.appendCancelProcedureDedupe(expanded, dedupe, seedProcedureId);
            }
        }
        return expanded;
    }

    /**
     * 加载取消写路径涉及的就诊药品明细（整方扩行用）。
     *
     * @param encounterIds 就诊主键集合
     * @return 就诊 → 药品明细
     */
    private Map<Long, List<MedicationRequestListQueryModel>> loadMedByEncounterForCancelWrite(Set<Long> encounterIds) {
        Map<Long, List<MedicationRequestListQueryModel>> map = new HashMap<>();
        if (encounterIds == null) {
            return map;
        }
        String doctorSource = GenerateSource.DOCTOR_PRESCRIPTION.getCode();
        for (Long encounterId : encounterIds) {
            if (encounterId == null) {
                continue;
            }
            map.put(encounterId,
                medicationRequestQueryService.listMedicationRequestsByEncounterForDoctorStation(
                    encounterId, doctorSource));
        }
        return map;
    }

    /**
     * 从就诊药品明细中定位单条申请。
     *
     * @param requestId     药品申请主键
     * @param encounterMeds 本就诊药品明细
     * @return 明细行；未命中 null
     */
    private MedicationRequestListQueryModel findMedDetail(
        Long requestId,
        List<MedicationRequestListQueryModel> encounterMeds) {
        if (requestId == null || encounterMeds == null) {
            return null;
        }
        for (MedicationRequestListQueryModel row : encounterMeds) {
            if (row != null && requestId.equals(row.getId())) {
                return row;
            }
        }
        return null;
    }

    /**
     * 解析同 request + occurrence 下可取消的净剩 COMPLETED procedureId。
     *
     * @param requestId      药品/诊疗申请主键
     * @param requestTable   请求表名
     * @param occurrenceTime 计划点 occurrence
     * @return 可取消 procedureId；无则 null
     */
    private Long resolveNetCompletedProcedureIdForCancel(
        Long requestId,
        String requestTable,
        LocalDateTime occurrenceTime) {
        if (requestId == null || StringUtils.isBlank(requestTable) || occurrenceTime == null) {
            return null;
        }
        List<ProcedureRecordQueryModel> rows = procedureQueryService.getProcedureRecords(requestId, requestTable);
        Long candidate = null;
        for (ProcedureRecordQueryModel row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (!EventStatus.COMPLETED.getCode().equals(row.getStatusEnum())) {
                continue;
            }
            if (!occurrenceTime.equals(row.getOccurrenceTime())) {
                continue;
            }
            if (nurseStationOrderCancelExecuteWriteAssembler.isProcedureAlreadyOffset(
                requestId, requestTable, row.getId())) {
                continue;
            }
            if (candidate == null || row.getId() > candidate) {
                candidate = row.getId();
            }
        }
        return candidate;
    }

    /**
     * 按 procedureId 去重追加扩行结果。
     *
     * @param expanded     目标列表
     * @param dedupe       去重键集合
     * @param procedureId  procedure 主键
     */
    private void appendCancelProcedureDedupe(List<Long> expanded, Set<String> dedupe, Long procedureId) {
        if (procedureId == null) {
            return;
        }
        String key = String.valueOf(procedureId);
        if (dedupe.contains(key)) {
            return;
        }
        dedupe.add(key);
        expanded.add(procedureId);
    }
}
