/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.nursestation;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.application.shared.product.DoctorStationAdviceFifoAllocationAssembler;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.OutpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.workflow.devicedispense.command.CreatePendingRefundDeviceDispenseCommand;
import com.openhis.health.domain.workflow.devicedispense.command.DispenseDeviceCommand;
import com.openhis.health.domain.workflow.devicedispense.command.MatchDispenseLotNumberCommand;
import com.openhis.health.domain.workflow.devicedispense.command.PrepareDeviceDispenseCommand;
import com.openhis.health.domain.workflow.devicedispense.command.RefundDispensedDeviceDispenseCommand;
import com.openhis.health.domain.workflow.devicedispense.command.RemoveUndispensedDeviceDispenseCommand;
import com.openhis.health.domain.workflow.devicedispense.command.ReplaceFifoDeviceDispenseDraftsCommand;
import com.openhis.health.domain.workflow.devicedispense.command.RevertCompletedBatchToPreparationCommand;
import com.openhis.health.domain.workflow.devicedispense.command.RevertPreparedBatchToPreparationCommand;
import com.openhis.health.domain.workflow.devicedispense.command.RollbackUndispensedDeviceDispenseCommand;
import com.openhis.health.domain.workflow.devicedispense.queries.model.DeviceDispenseModel;
import com.openhis.health.domain.workflow.devicedispense.queries.model.DeviceDispenseValidationModel;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseDomainService;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.openhis.health.domain.workflow.devicerequest.queries.model.DeviceRequestListModel;
import com.openhis.health.domain.workflow.devicerequest.queries.model.DeviceRequestQueryModel;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestDomainService;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 护士站耗材发放编排：按「耗材是否自动扣库」开关在已配药/已发药+FIFO 扣库之间分支。
 *
 * <p>退耗发放确认后，对负向耗材申请（{@code refund_device_id} 非空）编排
 * {@link DeviceRequestDomainService#completeDeviceRequestAfterRefund}；其余 DeviceRequest 写路径仍不在此组件变更。</p>
 */
@Component
@RequiredArgsConstructor
public class DeviceDispenseNurseWorkflowOrchestrator {

    /** 耗材自动扣库开关解析器。 */
    private final DeviceAutoInventoryDeductSettingResolver deviceAutoInventoryDeductSettingResolver;
    /** 耗材请求读侧。 */
    private final DeviceRequestQueryService deviceRequestQueryService;
    /** 耗材请求写侧。 */
    private final DeviceRequestDomainService deviceRequestDomainService;
    /** 耗材发放读侧。 */
    private final DeviceDispenseQueryService deviceDispenseQueryService;
    /** 耗材发放写侧。 */
    private final DeviceDispenseDomainService deviceDispenseDomainService;
    /** 医生站 FIFO 分段装配器。 */
    private final DoctorStationAdviceFifoAllocationAssembler doctorStationAdviceFifoAllocationAssembler;
    /** 库存读侧：解析 inventoryItemId 与扣减回补。 */
    private final InventoryItemQueryService inventoryItemQueryService;

    /**
     * 住院临时路径（护士划价/绑定执行）：草稿发放单在 promote 后按开关推进为已配药或已发药。
     *
     * @param deviceReqId                   耗材请求主键
     * @param operatorId                    操作人（{@code adm_practitioner.id}）；不自动扣库时作为配药人
     * @param dispensePerformerId           自动扣库时的发放人（{@code adm_practitioner.id}）；允许 null 时回落 operatorId
     * @param inventoryDecreaseAccumulator  自动扣库时累计待扣库存；允许 null 则内部不落库扣减
     */
    public void finalizeInpatientTemporaryPathDispense(
        Long deviceReqId,
        Long operatorId,
        Long dispensePerformerId,
        Map<Long, BigDecimal> inventoryDecreaseAccumulator) {
        if (deviceReqId == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "deviceReqId 不能为空");
        }
        if (operatorId == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "operatorId 不能为空");
        }
        DeviceRequestListModel request = this.requireDeviceRequestListModel(deviceReqId);
        // 草稿统一先升待发，后续拣配/发放均从 PREPARATION 起步。
        this.deviceDispenseDomainService.promoteDraftDispensesForDeviceRequests(List.of(deviceReqId), operatorId);
        if (this.deviceAutoInventoryDeductSettingResolver.isEnabled()) {
            this.applyAutoDeductForInpatientRequest(request, operatorId, dispensePerformerId, inventoryDecreaseAccumulator);
            return;
        }
        this.applyPreparedOnlyForInpatientRequest(deviceReqId, operatorId);
    }

    /**
     * 门诊自动扣库门禁：护士已传耗材房时直接放行；未传时须每条待发发放行能解析库房
     * （发放单已锁定 {@code location_id}，或已有真实批号无需 FIFO）。
     *
     * @param dispenseIds                勾选的发放行主键
     * @param requestInventoryLocationId 护士指定耗材房；非空时优先，执行时强制按该房 FIFO
     */
    public void validateOutpatientAutoDeductInventoryLocation(
        List<Long> dispenseIds,
        Long requestInventoryLocationId) {
        // Gate：无勾选发放行则无需校验耗材房
        if (dispenseIds == null || dispenseIds.isEmpty()) {
            return;
        }
        // Gate：护士已指定耗材房，执行路径将强制按该房 FIFO，此处不再逐行要求锁定库房
        if (requestInventoryLocationId != null) {
            return;
        }
        // Pre：未指定耗材房时，逐行确认「需 FIFO 的行」仍能从发放单解析到库房
        for (Long dispenseId : dispenseIds) {
            // 跳过空主键，避免误查
            if (dispenseId == null) {
                continue;
            }
            DeviceDispenseModel dispense = this.deviceDispenseQueryService.loadDeviceDispenseById(dispenseId);
            // 发放行已不存在则跳过（并发软删等）；后续写路径会再校验
            if (dispense == null) {
                continue;
            }
            // 已有真实批号且未改房：无需 FIFO，允许不传耗材房
            if (!this.needsFifoReplaceBeforeDispense(dispense, null)) {
                continue;
            }
            // 需 FIFO 却解析不到库房：拒绝，提示护士选择耗材房（EX-NUR-204 语义）
            if (this.resolveDispenseLocationId(dispense, null) == null) {
                throw new BusinessException(
                    ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "请选择耗材房后再执行发放");
            }
        }
    }

    /**
     * 门诊护士批量执行耗材：不自动扣库仅拣配为已配药；自动扣库则 FIFO 后发放并累计扣减量。
     *
     * <p>护士传入 {@code nurseInventoryLocationId} 时：按该库房强制重做 FIFO、回写发放行 {@code location_id}/批号，
     * 并回写请求 {@code perform_location} 与 {@code lot_number}（首段批号）；未传时沿用医生签发已锁库房
     * （缺库房且需 FIFO 时由门禁拒绝）。</p>
     *
     * @param dispenseIds                    勾选的发放行主键（保持调用方顺序）
     * @param operatorId                     操作人
     * @param preparerId                     配药人（通常为执行护士）
     * @param performerId                    发放人
     * @param nurseInventoryLocationId       护士指定耗材房；可空（空则医生默认）
     * @param completedDispenseIdsOut        实际完成处理的发放行 id（输出）
     * @param inventoryDecreaseAccumulator   自动扣库时累计待扣库存
     */
    public void applyOutpatientNurseDeviceDispenses(
        List<Long> dispenseIds,
        Long operatorId,
        Long preparerId,
        Long performerId,
        Long nurseInventoryLocationId,
        List<Long> completedDispenseIdsOut,
        Map<Long, BigDecimal> inventoryDecreaseAccumulator) {
        // Gate：无勾选则直接返回
        if (dispenseIds == null || dispenseIds.isEmpty()) {
            return;
        }
        // Gate：操作人必填，后续拣配/发放写审计
        if (operatorId == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "operatorId 不能为空");
        }
        // Pre：排序保证同批处理顺序稳定，利于并发冲突复现
        List<Long> sortedIds = new ArrayList<>(dispenseIds);
        Collections.sort(sortedIds);
        // Pre：同一 DeviceRequest 在批内只 FIFO 一次；已软删的旧勾选 id 须在门禁前跳过
        Set<Long> fifoReplacedRequestIds = new HashSet<>();
        Set<Long> softDeletedDispenseIdsAfterFifo = new HashSet<>();
        try {
            for (Long dispenseId : sortedIds) {
                // 同请求 FIFO 后旧发放行已软删：跳过，避免「状态已变更」误伤同批后续勾选
                if (softDeletedDispenseIdsAfterFifo.contains(dispenseId)) {
                    continue;
                }
                // Gate：仍须为可拣配态，防止并发已发/已配后重复执行
                DeviceDispenseValidationModel preparable =
                    this.deviceDispenseQueryService.validateDeviceDispensePreparable(dispenseId);
                if (preparable == null || Boolean.FALSE.equals(preparable.getAllow())) {
                    throw new BusinessException(
                        ErrorCodeConstants.DEVICE_DISPENSE_INVALID_STATUS,
                        "耗材发放单状态已变更，请刷新后重试");
                }
                DeviceDispenseModel dispense = this.deviceDispenseQueryService.mustGetDeviceDispense(dispenseId);
                // 分支：自动扣库开 → FIFO/发放+累计扣减；关 → 仅拣配为已配药
                if (this.deviceAutoInventoryDeductSettingResolver.isEnabled()) {
                    Long deviceReqId = dispense.getDeviceReqId();
                    // 同请求已在本批 FIFO 并处理完全部新发放行：防御性跳过
                    if (deviceReqId != null && fifoReplacedRequestIds.contains(deviceReqId)) {
                        continue;
                    }
                    // 分支：需按库房重做 FIFO（护士指定房，或缺少真实批号）
                    if (deviceReqId != null
                        && this.needsFifoReplaceBeforeDispense(dispense, nurseInventoryLocationId)) {
                        DeviceRequestListModel request = this.requireDeviceRequestListModel(deviceReqId);
                        Long locationId = this.resolveDispenseLocationId(dispense, nurseInventoryLocationId);
                        // Gate：解析不到发放库房则拒绝（护士未选且签发未锁库房）
                        if (locationId == null) {
                            throw new BusinessException(
                                ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "请选择耗材房后再执行发放");
                        }
                        // Pre：记录替换前发放行，供同批后续勾选跳过
                        List<Long> idsBeforeFifo =
                            this.deviceDispenseQueryService.listDeviceDispenseIdsByDeviceRequest(deviceReqId);
                        // Cmd：按目标库房 FIFO 软删旧草稿/待发行并写入新分段（含 location_id / lot_number）
                        this.replaceFifoDraftsAtLocation(request, locationId, operatorId);
                        if (idsBeforeFifo != null) {
                            softDeletedDispenseIdsAfterFifo.addAll(idsBeforeFifo);
                        }
                        // Pre：取 FIFO 后首条发放行批号，供请求侧 lot_number 回写
                        String lotForRequest = null;
                        List<Long> idsAfterFifo =
                            this.deviceDispenseQueryService.listDeviceDispenseIdsByDeviceRequest(deviceReqId);
                        if (idsAfterFifo != null && !idsAfterFifo.isEmpty()) {
                            DeviceDispenseModel firstAfterFifo =
                                this.deviceDispenseQueryService.mustGetDeviceDispense(idsAfterFifo.get(0));
                            lotForRequest = firstAfterFifo.getLotNumber();
                        }
                        // Cmd：护士指定房时同步请求 perform_location + lot_number（扣库/回补仍以发放行为准）
                        if (nurseInventoryLocationId != null) {
                            this.deviceRequestDomainService.rebindPerformLocationAndLotForOutpatientNurseExecute(
                                deviceReqId, locationId, lotForRequest);
                        }
                        fifoReplacedRequestIds.add(deviceReqId);
                        // Cmd：新草稿升为待发后再逐条拣配+发放
                        this.deviceDispenseDomainService.promoteDraftDispensesForDeviceRequests(
                            List.of(deviceReqId), operatorId);
                        List<Long> activeIds =
                            this.deviceDispenseQueryService.listDeviceDispenseIdsByDeviceRequest(deviceReqId);
                        for (Long activeId : activeIds) {
                            this.processOutpatientAutoDeductLine(
                                activeId,
                                operatorId,
                                preparerId,
                                performerId,
                                completedDispenseIdsOut,
                                inventoryDecreaseAccumulator);
                        }
                        continue;
                    }
                    // Cmd：无需 FIFO 替换时，直接对当前勾选行拣配+发放
                    this.processOutpatientAutoDeductLine(
                        dispenseId,
                        operatorId,
                        preparerId,
                        performerId,
                        completedDispenseIdsOut,
                        inventoryDecreaseAccumulator);
                } else {
                    // Cmd：自动扣库关，仅推进为已配药，不扣库存
                    this.processOutpatientPreparedOnlyLine(
                        dispenseId, operatorId, preparerId, completedDispenseIdsOut);
                }
            }
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(
                ErrorCodeConstants.DEVICE_DISPENSE_RULE_CONFLICT,
                "耗材发放单已被他人处理，请刷新");
        }
    }

    /**
     * 住院护士划价取消耗材发放（BR-IP-PRC-025 / BR-IP-PRC-019 两轨）。
     *
     * <p>开关开（已发 COMPLETED/PART_COMPLETED）：创建待退耗材单并确认对冲，累计库存回补；
     * 开关关（未发 PREPARATION/PREPARED/DRAFT）：软删发放单。DeviceRequest 不在此变更。</p>
     *
     * @param deviceReqId                   耗材请求主键
     * @param operatorId                    操作人
     * @param inventoryIncreaseAccumulator  已发路径累计回补量；允许 null
     */
    public void revertInpatientPricingDeviceDispenses(
        Long deviceReqId,
        Long operatorId,
        Map<Long, BigDecimal> inventoryIncreaseAccumulator) {
        if (deviceReqId == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "deviceReqId 不能为空");
        }
        if (operatorId == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "operatorId 不能为空");
        }
        List<Long> dispenseIds = this.deviceDispenseQueryService.listDeviceDispenseIdsByDeviceRequest(deviceReqId);
        if (dispenseIds == null || dispenseIds.isEmpty()) {
            return;
        }
        List<Long> undispensedIds = new ArrayList<>();
        for (Long dispenseId : dispenseIds) {
            if (dispenseId == null) {
                continue;
            }
            DeviceDispenseModel dispense = this.deviceDispenseQueryService.mustGetDeviceDispense(dispenseId);
            String statusEnum = dispense.getStatusEnum();
            // 已发路径（开关开）：退耗材单对冲并累计回补，保留源单 COMPLETED 事实
            if (DispenseStatus.COMPLETED.getCode().equals(statusEnum)
                || DispenseStatus.PART_COMPLETED.getCode().equals(statusEnum)) {
                this.refundCompletedPricingDeviceDispense(dispense, operatorId, inventoryIncreaseAccumulator);
                continue;
            }
            // 未发路径（开关关）：仅收集待发/已配药/草稿行，后续批量软删
            if (DispenseStatus.PREPARATION.getCode().equals(statusEnum)
                || DispenseStatus.PREPARED.getCode().equals(statusEnum)
                || DispenseStatus.DRAFT.getCode().equals(statusEnum)) {
                undispensedIds.add(dispenseId);
            }
        }
        if (!undispensedIds.isEmpty()) {
            RemoveUndispensedDeviceDispenseCommand removeCmd = new RemoveUndispensedDeviceDispenseCommand();
            removeCmd.setOperatorId(operatorId);
            removeCmd.setIdListOrRequestKey(
                undispensedIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
            this.deviceDispenseDomainService.removeUndispensedDeviceDispense(removeCmd);
        }
    }

    /**
     * 住院绑定耗材取消执行回退：PREPARATION/PREPARED → 待发（不补库存）；COMPLETED → revert + 累计回补。
     *
     * <p>DeviceRequest 状态不在此变更（BR-IP-CXL-014）。</p>
     *
     * @param deviceReqId              绑定耗材请求主键
     * @param operatorId               操作人
     * @param inventoryIncreaseAccumulator 已发路径累计回补量；允许 null
     */
    public void revertInpatientBindingDeviceDispenses(
        Long deviceReqId,
        Long operatorId,
        Map<Long, BigDecimal> inventoryIncreaseAccumulator) {
        if (deviceReqId == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "deviceReqId 不能为空");
        }
        if (operatorId == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "operatorId 不能为空");
        }
        List<Long> dispenseIds = this.deviceDispenseQueryService.listDeviceDispenseIdsByDeviceRequest(deviceReqId);
        if (dispenseIds == null || dispenseIds.isEmpty()) {
            return;
        }
        List<Long> preparationIds = new ArrayList<>();
        List<Long> preparedIds = new ArrayList<>();
        List<Long> completedIds = new ArrayList<>();
        for (Long dispenseId : dispenseIds) {
            if (dispenseId == null) {
                continue;
            }
            DeviceDispenseModel dispense = this.deviceDispenseQueryService.mustGetDeviceDispense(dispenseId);
            String statusEnum = dispense.getStatusEnum();
            // 按发放态分桶：待发回退不补库存，已发 revert 并累计回补量
            if (DispenseStatus.PREPARATION.getCode().equals(statusEnum)) {
                preparationIds.add(dispenseId);
                continue;
            }
            if (DispenseStatus.PREPARED.getCode().equals(statusEnum)) {
                preparedIds.add(dispenseId);
                continue;
            }
            if (DispenseStatus.COMPLETED.getCode().equals(statusEnum)
                || DispenseStatus.PART_COMPLETED.getCode().equals(statusEnum)) {
                this.accumulateInventoryIncreaseForDispense(dispense, inventoryIncreaseAccumulator);
                completedIds.add(dispenseId);
            }
        }
        if (!preparationIds.isEmpty()) {
            // PREPARATION：回退至执行前待发
            RollbackUndispensedDeviceDispenseCommand rollbackCmd = new RollbackUndispensedDeviceDispenseCommand();
            rollbackCmd.setIdList(preparationIds);
            rollbackCmd.setOperatorId(operatorId);
            this.deviceDispenseDomainService.rollbackUndispensedDeviceDispense(rollbackCmd);
        }
        if (!preparedIds.isEmpty()) {
            RevertPreparedBatchToPreparationCommand preparedCmd = new RevertPreparedBatchToPreparationCommand();
            preparedCmd.setIdList(preparedIds);
            preparedCmd.setOperatorId(operatorId);
            this.deviceDispenseDomainService.revertPreparedBatchToPreparation(preparedCmd);
        }
        if (!completedIds.isEmpty()) {
            RevertCompletedBatchToPreparationCommand completedCmd = new RevertCompletedBatchToPreparationCommand();
            completedCmd.setIdList(completedIds);
            completedCmd.setOperatorId(operatorId);
            this.deviceDispenseDomainService.revertCompletedBatchToPreparation(completedCmd);
        }
    }

    /**
     * 门诊护士取消执行耗材：按当前发放态对称回退至执行前待发；已发药路径合并回补库存。
     *
     * <p>可回退态：{@link DispenseStatus#PREPARED}（不自动扣库执行）、
     * {@link DispenseStatus#COMPLETED}/{@link DispenseStatus#PART_COMPLETED}（自动扣库执行）。
     * DeviceRequest 状态不在此变更。</p>
     *
     * @param dispenseIds                   勾选的发放行主键
     * @param operatorId                    操作人
     * @param revertedDispenseIdsOut        实际回退成功的发放行 id（输出）
     * @param inventoryIncreaseAccumulator  已发药回退时累计待回补库存；允许 null
     */
    public void revertOutpatientNurseDeviceDispenses(
        List<Long> dispenseIds,
        Long operatorId,
        List<Long> revertedDispenseIdsOut,
        Map<Long, BigDecimal> inventoryIncreaseAccumulator) {
        if (dispenseIds == null || dispenseIds.isEmpty()) {
            return;
        }
        if (operatorId == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "operatorId 不能为空");
        }
        List<Long> sortedIds = new ArrayList<>(dispenseIds);
        Collections.sort(sortedIds);
        List<Long> completedDispenseIds = new ArrayList<>();
        List<Long> preparedDispenseIds = new ArrayList<>();
        for (Long dispenseId : sortedIds) {
            if (dispenseId == null) {
                continue;
            }
            this.deviceDispenseQueryService.loadDeviceDispenseById(dispenseId);
            DeviceDispenseModel dispense = this.deviceDispenseQueryService.mustGetDeviceDispense(dispenseId);
            String statusEnum = dispense.getStatusEnum();
            if (DispenseStatus.COMPLETED.getCode().equals(statusEnum)
                || DispenseStatus.PART_COMPLETED.getCode().equals(statusEnum)) {
                this.accumulateInventoryIncreaseForDispense(dispense, inventoryIncreaseAccumulator);
                completedDispenseIds.add(dispenseId);
                if (revertedDispenseIdsOut != null) {
                    revertedDispenseIdsOut.add(dispenseId);
                }
                continue;
            }
            if (DispenseStatus.PREPARED.getCode().equals(statusEnum)) {
                preparedDispenseIds.add(dispenseId);
                if (revertedDispenseIdsOut != null) {
                    revertedDispenseIdsOut.add(dispenseId);
                }
            }
        }
        if (!completedDispenseIds.isEmpty()) {
            RevertCompletedBatchToPreparationCommand completedCmd = new RevertCompletedBatchToPreparationCommand();
            completedCmd.setIdList(completedDispenseIds);
            completedCmd.setOperatorId(operatorId);
            this.deviceDispenseDomainService.revertCompletedBatchToPreparation(completedCmd);
        }
        if (!preparedDispenseIds.isEmpty()) {
            RevertPreparedBatchToPreparationCommand preparedCmd = new RevertPreparedBatchToPreparationCommand();
            preparedCmd.setIdList(preparedDispenseIds);
            preparedCmd.setOperatorId(operatorId);
            this.deviceDispenseDomainService.revertPreparedBatchToPreparation(preparedCmd);
        }
    }

    /**
     * 不自动扣库：住院请求下所有待发发放行拣配为已配药。
     *
     * @param deviceReqId 耗材请求主键
     * @param operatorId  配药人（操作人）
     */
    private void applyPreparedOnlyForInpatientRequest(Long deviceReqId, Long operatorId) {
        List<Long> dispenseIds = this.deviceDispenseQueryService.listDeviceDispenseIdsByDeviceRequest(deviceReqId);
        for (Long dispenseId : dispenseIds) {
            DeviceDispenseModel dispense = this.deviceDispenseQueryService.mustGetDeviceDispense(dispenseId);
            String lot = this.resolvePrepareLotNumber(dispense);
            this.deviceDispenseDomainService.prepareDeviceDispense(
                this.buildPrepareCommand(dispenseId, operatorId, lot, dispense.getQuantity(), operatorId));
        }
    }

    /**
     * 自动扣库：住院请求在指定库房 FIFO 替换分段后拣配、发放并累计扣减。
     *
     * @param request                        耗材请求列表行
     * @param operatorId                     操作人
     * @param dispensePerformerId            发放人；null 时回落 operatorId
     * @param inventoryDecreaseAccumulator   库存扣减累计 Map
     */
    private void applyAutoDeductForInpatientRequest(
        DeviceRequestListModel request,
        Long operatorId,
        Long dispensePerformerId,
        Map<Long, BigDecimal> inventoryDecreaseAccumulator) {
        if (request.getPerformLocation() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "耗材发放库房不能为空");
        }
        this.replaceFifoDraftsAtLocation(request, request.getPerformLocation(), operatorId);
        this.deviceDispenseDomainService.promoteDraftDispensesForDeviceRequests(List.of(request.getId()), operatorId);
        Long performerId = dispensePerformerId;
        if (performerId == null) {
            performerId = operatorId;
        }
        List<Long> dispenseIds = this.deviceDispenseQueryService.listDeviceDispenseIdsByDeviceRequest(request.getId());
        for (Long dispenseId : dispenseIds) {
            DeviceDispenseModel dispense = this.deviceDispenseQueryService.mustGetDeviceDispense(dispenseId);
            this.deviceDispenseDomainService.prepareDeviceDispense(
                this.buildPrepareCommand(
                    dispenseId,
                    operatorId,
                    dispense.getLotNumber(),
                    dispense.getQuantity(),
                    operatorId));
            this.deviceDispenseDomainService.dispenseDevice(
                this.buildDispenseCommand(dispenseId, performerId, dispense.getQuantity(), operatorId));
            this.accumulateInventoryDecreaseForDispense(dispenseId, inventoryDecreaseAccumulator);
        }
    }

    /**
     * 门诊自动扣库：单发放行拣配、发放并累计扣减。
     *
     * @param dispenseId                     发放行主键
     * @param operatorId                     操作人
     * @param preparerId                     配药人
     * @param performerId                    发放人
     * @param completedDispenseIdsOut        已完成发放行输出列表
     * @param inventoryDecreaseAccumulator   库存扣减累计 Map
     */
    private void processOutpatientAutoDeductLine(
        Long dispenseId,
        Long operatorId,
        Long preparerId,
        Long performerId,
        List<Long> completedDispenseIdsOut,
        Map<Long, BigDecimal> inventoryDecreaseAccumulator) {
        DeviceDispenseValidationModel preparable = this.deviceDispenseQueryService.validateDeviceDispensePreparable(dispenseId);
        if (preparable == null || Boolean.FALSE.equals(preparable.getAllow())) {
            return;
        }
        DeviceDispenseModel dispense = this.deviceDispenseQueryService.mustGetDeviceDispense(dispenseId);
        this.deviceDispenseDomainService.matchDispenseLotNumber(
            this.buildMatchLotCommand(dispenseId, operatorId));
        String lot = this.resolvePrepareLotNumber(dispense);
        // 自动扣库：配药人沿用操作人，发放人使用执行护士，避免拣配人与发放人同人校验冲突。
        this.deviceDispenseDomainService.prepareDeviceDispense(
            this.buildPrepareCommand(
                dispenseId,
                operatorId,
                lot,
                dispense.getQuantity(),
                operatorId));
        Long resolvedPerformerId = performerId;
        if (resolvedPerformerId == null) {
            resolvedPerformerId = operatorId;
        }
        this.deviceDispenseDomainService.dispenseDevice(
            this.buildDispenseCommand(dispenseId, resolvedPerformerId, dispense.getQuantity(), operatorId));
        if (completedDispenseIdsOut != null) {
            completedDispenseIdsOut.add(dispenseId);
        }
        this.accumulateInventoryDecreaseForDispense(dispenseId, inventoryDecreaseAccumulator);
    }

    /**
     * 门诊不自动扣库：单发放行仅拣配为已配药。
     *
     * @param dispenseId              发放行主键
     * @param operatorId            操作人
     * @param preparerId            配药人
     * @param completedDispenseIdsOut 已完成发放行输出列表
     */
    private void processOutpatientPreparedOnlyLine(
        Long dispenseId,
        Long operatorId,
        Long preparerId,
        List<Long> completedDispenseIdsOut) {
        DeviceDispenseModel dispense = this.deviceDispenseQueryService.mustGetDeviceDispense(dispenseId);
        this.deviceDispenseDomainService.matchDispenseLotNumber(
            this.buildMatchLotCommand(dispenseId, operatorId));
        Long resolvedPreparerId = preparerId;
        if (resolvedPreparerId == null) {
            resolvedPreparerId = operatorId;
        }
        String lot = this.resolvePrepareLotNumber(dispense);
        this.deviceDispenseDomainService.prepareDeviceDispense(
            this.buildPrepareCommand(
                dispenseId,
                resolvedPreparerId,
                lot,
                dispense.getQuantity(),
                operatorId));
        if (completedDispenseIdsOut != null) {
            completedDispenseIdsOut.add(dispenseId);
        }
    }

    /**
     * 护士划价取消：已发耗材创建待退单、确认对冲并累计库存回补量。
     *
     * @param sourceDispense               源已发发放行
     * @param operatorId                   操作人
     * @param inventoryIncreaseAccumulator 库存回补累计 Map
     */
    private void refundCompletedPricingDeviceDispense(
        DeviceDispenseModel sourceDispense,
        Long operatorId,
        Map<Long, BigDecimal> inventoryIncreaseAccumulator) {
        if (sourceDispense == null || sourceDispense.getId() == null) {
            return;
        }
        BigDecimal refundQty = this.resolveDispenseRestoreQuantity(sourceDispense);
        if (refundQty == null || refundQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "已发数量无效，无法取消划价");
        }
        // 须在创建待退行前采集批号/库位，供后续合并扣库回补
        this.accumulateInventoryIncreaseForDispense(sourceDispense, inventoryIncreaseAccumulator);
        CreatePendingRefundDeviceDispenseCommand createCmd = new CreatePendingRefundDeviceDispenseCommand();
        createCmd.setOperatorId(operatorId);
        CreatePendingRefundDeviceDispenseCommand.CreatePendingRefundDeviceDispenseItemCommand createItem =
            new CreatePendingRefundDeviceDispenseCommand.CreatePendingRefundDeviceDispenseItemCommand();
        createItem.setSourceDispenseId(sourceDispense.getId());
        createItem.setRefundQuantity(refundQty);
        createCmd.setItem(createItem);
        Long pendingRefundId = this.deviceDispenseDomainService.createPendingRefundDeviceDispense(createCmd);
        RefundDispensedDeviceDispenseCommand refundCmd = new RefundDispensedDeviceDispenseCommand();
        refundCmd.setOperatorId(operatorId);
        RefundDispensedDeviceDispenseCommand.RefundDispensedDeviceDispenseItemCommand refundItem =
            new RefundDispensedDeviceDispenseCommand.RefundDispensedDeviceDispenseItemCommand();
        refundItem.setId(pendingRefundId);
        refundItem.setRefundQuantity(refundQty);
        refundItem.setPractitionerId(operatorId);
        refundItem.setReasonText("护士划价取消");
        refundCmd.setItem(refundItem);
        this.refundDispensedDeviceDispenseAndCompleteNegativeRequests(refundCmd);
    }

    /**
     * 退耗发放确认编排：推进待退行 REFUNDED 后，对负向耗材申请执行退费收口（IF-REF-04 放行前置）。
     *
     * @param command 退耗确认命令
     */
    public void refundDispensedDeviceDispenseAndCompleteNegativeRequests(RefundDispensedDeviceDispenseCommand command) {
        LinkedHashSet<Long> negativeRequestIds = new LinkedHashSet<>();
        for (RefundDispensedDeviceDispenseCommand.RefundDispensedDeviceDispenseItemCommand item : command.resolveItems()) {
            if (item == null || item.getId() == null) {
                continue;
            }
            DeviceDispenseModel dispense = this.deviceDispenseQueryService.mustGetDeviceDispense(item.getId());
            Long deviceReqId = dispense.getDeviceReqId();
            if (deviceReqId == null) {
                continue;
            }
            DeviceRequestQueryModel request = this.deviceRequestQueryService.mustGetDeviceRequest(deviceReqId);
            // Pre：仅负向退耗单（refund_device_id 非空）须收口为 COMPLETED。
            if (request.getRefundDeviceId() != null) {
                negativeRequestIds.add(deviceReqId);
            }
        }
        this.deviceDispenseDomainService.refundDispensedDeviceDispense(command);
        for (Long requestId : negativeRequestIds) {
            this.deviceRequestDomainService.completeDeviceRequestAfterRefund(requestId);
        }
    }

    /**
     * 按 performLocation 对请求下草稿/待发发放行做 FIFO 替换。
     *
     * @param request            耗材请求列表行
     * @param performLocationId  执行库房位置主键
     * @param operatorId         操作人
     */
    private void replaceFifoDraftsAtLocation(
        DeviceRequestListModel request,
        Long performLocationId,
        Long operatorId) {
        List<ReplaceFifoDeviceDispenseDraftsCommand.Segment> segments =
            this.doctorStationAdviceFifoAllocationAssembler.allocateDeviceFifoAtPerformLocation(
                request.getEncounterId(),
                performLocationId,
                request.getDeviceDefId(),
                request.getUnitCode(),
                request.getQuantity());
        ReplaceFifoDeviceDispenseDraftsCommand replaceCmd = new ReplaceFifoDeviceDispenseDraftsCommand();
        replaceCmd.setOperatorId(operatorId);
        replaceCmd.setDeviceReqId(request.getId());
        replaceCmd.setDeviceDefId(request.getDeviceDefId());
        replaceCmd.setEncounterId(request.getEncounterId());
        replaceCmd.setPatientId(request.getPatientId());
        replaceCmd.setUnitCode(request.getUnitCode());
        replaceCmd.setSegments(segments);
        this.deviceDispenseDomainService.replaceFifoDraftDispensesAfterDoctorStationSign(replaceCmd);
    }

    /**
     * 判断门诊发放行是否须在发放前按库房重做 FIFO。
     *
     * <p>护士已指定耗材房：一律重做（覆盖医生签发已锁批号/库房，保证从所选房扣库并回写发放行）。
     * 未指定：仅当缺少真实批号（空或占位）时重做。</p>
     *
     * @param dispense                 发放行读模型
     * @param nurseInventoryLocationId 护士指定耗材房；可空
     * @return 需要 FIFO 替换时为 true
     */
    private boolean needsFifoReplaceBeforeDispense(DeviceDispenseModel dispense, Long nurseInventoryLocationId) {
        // 护士点选耗材房：强制按所选房 FIFO，避免沿用医生签发库房扣错库存
        if (nurseInventoryLocationId != null) {
            return true;
        }
        String lot = trimToNull(dispense.getLotNumber());
        // 已有真实批号：沿用医生签发分段，无需再 FIFO
        if (lot != null
            && !OutpatientNurseStationConstants.OUTPATIENT_NURSE_STATION_DEVICE_LOT_PLACEHOLDER.equals(lot)) {
            return false;
        }
        // 批号为空或占位：须按可解析库房补齐 FIFO 后再扣库
        return true;
    }

    /**
     * 解析发放行库房：护士指定耗材房优先；未传时回落发放单锁定 {@code location_id}。
     *
     * @param dispense                   发放行读模型
     * @param nurseInventoryLocationId   护士指定耗材房
     * @return 库房位置主键；无法解析时返回 null
     */
    private Long resolveDispenseLocationId(DeviceDispenseModel dispense, Long nurseInventoryLocationId) {
        // 护士指定优先：执行扣库与取消回补均落在该库房
        if (nurseInventoryLocationId != null) {
            return nurseInventoryLocationId;
        }
        // 未指定：回落医生签发写入发放单的锁定库房
        if (dispense != null && dispense.getLocationId() != null) {
            return dispense.getLocationId();
        }
        return null;
    }

    /**
     * 拣配批号：已有真实批号则沿用，否则使用占位批号（不触发扣库）。
     *
     * @param dispense 发放行读模型
     * @return 拣配批号
     */
    private String resolvePrepareLotNumber(DeviceDispenseModel dispense) {
        if (dispense == null) {
            return OutpatientNurseStationConstants.OUTPATIENT_NURSE_STATION_DEVICE_LOT_PLACEHOLDER;
        }
        String lot = trimToNull(dispense.getLotNumber());
        if (lot != null
            && !OutpatientNurseStationConstants.OUTPATIENT_NURSE_STATION_DEVICE_LOT_PLACEHOLDER.equals(lot)) {
            return lot;
        }
        return OutpatientNurseStationConstants.OUTPATIENT_NURSE_STATION_DEVICE_LOT_PLACEHOLDER;
    }

    /**
     * 将已发放行的扣减量合并进累计 Map（按 inventoryItemId 聚合）。
     *
     * @param dispenseId                   发放行主键
     * @param inventoryDecreaseAccumulator 库存扣减累计 Map
     */
    private void accumulateInventoryDecreaseForDispense(
        Long dispenseId,
        Map<Long, BigDecimal> inventoryDecreaseAccumulator) {
        if (inventoryDecreaseAccumulator == null) {
            return;
        }
        DeviceDispenseModel after = this.deviceDispenseQueryService.loadDeviceDispenseById(dispenseId);
        if (after == null) {
            return;
        }
        Long locationId = after.getLocationId();
        Long deviceDefId = after.getDeviceDefId();
        String lot = after.getLotNumber();
        if (locationId == null || deviceDefId == null || StringUtils.isEmpty(lot)) {
            return;
        }
        String lotTrim = lot.trim();
        if (OutpatientNurseStationConstants.OUTPATIENT_NURSE_STATION_DEVICE_LOT_PLACEHOLDER.equals(lotTrim)) {
            return;
        }
        Long inventoryItemId = this.inventoryItemQueryService.requireInventoryItemIdForDeviceDispenseBatch(
            TableNameConstants.ADM_DEVICE_DEFINITION, deviceDefId, locationId, lotTrim);
        BigDecimal decQty = this.resolveDispenseRestoreQuantity(after);
        if (decQty == null || decQty.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal existed = inventoryDecreaseAccumulator.get(inventoryItemId);
        if (existed == null) {
            inventoryDecreaseAccumulator.put(inventoryItemId, decQty);
            return;
        }
        inventoryDecreaseAccumulator.put(inventoryItemId, existed.add(decQty));
    }

    /**
     * 将已发药发放行的回补量合并进累计 Map（须在回退状态前采集批号/库位/数量）。
     *
     * @param dispense                     发放行读模型
     * @param inventoryIncreaseAccumulator 库存回补累计 Map
     */
    private void accumulateInventoryIncreaseForDispense(
        DeviceDispenseModel dispense,
        Map<Long, BigDecimal> inventoryIncreaseAccumulator) {
        if (inventoryIncreaseAccumulator == null || dispense == null) {
            return;
        }
        Long locationId = dispense.getLocationId();
        Long deviceDefId = dispense.getDeviceDefId();
        String lot = dispense.getLotNumber();
        if (locationId == null || deviceDefId == null || StringUtils.isEmpty(lot)) {
            return;
        }
        String lotTrim = lot.trim();
        if (OutpatientNurseStationConstants.OUTPATIENT_NURSE_STATION_DEVICE_LOT_PLACEHOLDER.equals(lotTrim)) {
            return;
        }
        Long inventoryItemId = this.inventoryItemQueryService.requireInventoryItemIdForDeviceDispenseBatch(
            TableNameConstants.ADM_DEVICE_DEFINITION, deviceDefId, locationId, lotTrim);
        BigDecimal incQty = this.resolveDispenseRestoreQuantity(dispense);
        if (incQty == null || incQty.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal existed = inventoryIncreaseAccumulator.get(inventoryItemId);
        if (existed == null) {
            inventoryIncreaseAccumulator.put(inventoryItemId, incQty);
            return;
        }
        inventoryIncreaseAccumulator.put(inventoryItemId, existed.add(incQty));
    }

    /**
     * 扣减/回补数量：优先实发量，否则回落计划量。
     *
     * @param dispense 发放行读模型
     * @return 扣减或回补数量
     */
    private BigDecimal resolveDispenseRestoreQuantity(DeviceDispenseModel dispense) {
        if (dispense.getDispenseQuantity() != null) {
            return dispense.getDispenseQuantity();
        }
        return dispense.getQuantity();
    }

    /**
     * 加载耗材请求列表行；不存在时抛业务异常。
     *
     * @param deviceReqId 耗材请求主键
     * @return 耗材请求列表行
     * @throws BusinessException 请求不存在时
     */
    private DeviceRequestListModel requireDeviceRequestListModel(Long deviceReqId) {
        Map<Long, DeviceRequestListModel> map = this.deviceRequestQueryService.mapListModelsByIds(List.of(deviceReqId));
        DeviceRequestListModel request = map.get(deviceReqId);
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_NOT_FOUND, "耗材请求不存在");
        }
        return request;
    }

    /**
     * 组装耗材配药（PREPARATION）领域命令：批号、数量与门诊占位 traceNo。
     *
     * @param dispenseId  发放行 id
     * @param preparerId  配药药师
     * @param lotNumber   批号
     * @param quantity    配药数量
     * @param operatorId  操作人
     * @return Prepare 命令
     */
    private PrepareDeviceDispenseCommand buildPrepareCommand(
        Long dispenseId,
        Long preparerId,
        String lotNumber,
        BigDecimal quantity,
        Long operatorId) {
        PrepareDeviceDispenseCommand command = new PrepareDeviceDispenseCommand();
        command.setOperatorId(operatorId);
        PrepareDeviceDispenseCommand.PrepareDeviceDispenseItemCommand item =
            new PrepareDeviceDispenseCommand.PrepareDeviceDispenseItemCommand();
        item.setId(dispenseId);
        item.setPreparerId(preparerId);
        item.setLotNumber(lotNumber);
        item.setTraceNo(OutpatientNurseStationConstants.OUTPATIENT_NURSE_STATION_DEVICE_TRACE_PLACEHOLDER);
        item.setQuantity(quantity);
        command.setItem(item);
        return command;
    }

    /**
     * 组装耗材发放（DISPENSE）领域命令。
     *
     * @param dispenseId   发放行 id
     * @param performerId  发放执行人
     * @param quantity     发放数量
     * @param operatorId   操作人
     * @return Dispense 命令
     */
    private DispenseDeviceCommand buildDispenseCommand(
        Long dispenseId,
        Long performerId,
        BigDecimal quantity,
        Long operatorId) {
        DispenseDeviceCommand command = new DispenseDeviceCommand();
        command.setOperatorId(operatorId);
        DispenseDeviceCommand.DispenseDeviceItemCommand item = new DispenseDeviceCommand.DispenseDeviceItemCommand();
        item.setId(dispenseId);
        item.setPerformerId(performerId);
        item.setDispenseQuantity(quantity);
        command.setItem(item);
        return command;
    }

    /**
     * 组装批号匹配领域命令（单发放行 id 字符串化 idListOrRequestKey）。
     *
     * @param dispenseId 发放行 id
     * @param operatorId 操作人
     * @return MatchLot 命令
     */
    private MatchDispenseLotNumberCommand buildMatchLotCommand(Long dispenseId, Long operatorId) {
        MatchDispenseLotNumberCommand command = new MatchDispenseLotNumberCommand();
        command.setIdListOrRequestKey(String.valueOf(dispenseId));
        command.setOperatorId(operatorId);
        return command;
    }
}
