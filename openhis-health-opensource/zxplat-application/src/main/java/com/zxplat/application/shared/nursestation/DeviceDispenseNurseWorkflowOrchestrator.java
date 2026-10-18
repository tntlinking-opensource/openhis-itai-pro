/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.nursestation;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.OutpatientNurseStationConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.DispenseStatus;
import com.zxplat.domain.workflow.devicedispense.command.CreatePendingRefundDeviceDispenseCommand;
import com.zxplat.domain.workflow.devicedispense.command.DispenseDeviceCommand;
import com.zxplat.domain.workflow.devicedispense.command.MatchDispenseLotNumberCommand;
import com.zxplat.domain.workflow.devicedispense.command.PrepareDeviceDispenseCommand;
import com.zxplat.domain.workflow.devicedispense.command.RefundDispensedDeviceDispenseCommand;
import com.zxplat.domain.workflow.devicedispense.command.RemoveUndispensedDeviceDispenseCommand;
import com.zxplat.domain.workflow.devicedispense.command.ReplaceFifoDeviceDispenseDraftsCommand;
import com.zxplat.domain.workflow.devicedispense.command.RevertCompletedBatchToPreparationCommand;
import com.zxplat.domain.workflow.devicedispense.command.RevertPreparedBatchToPreparationCommand;
import com.zxplat.domain.workflow.devicedispense.command.RollbackUndispensedDeviceDispenseCommand;
import com.zxplat.domain.workflow.devicedispense.command.UpdateDeviceDispenseLocationCommand;
import com.zxplat.application.shared.product.DoctorStationAdviceFifoAllocationAssembler;
import com.zxplat.domain.workflow.devicedispense.queries.model.DeviceDispenseModel;
import com.zxplat.domain.workflow.devicedispense.queries.model.DeviceDispenseValidationModel;
import com.zxplat.domain.workflow.devicedispense.service.DeviceDispenseDomainService;
import com.zxplat.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.zxplat.domain.workflow.devicerequest.queries.model.DeviceRequestListModel;
import com.zxplat.domain.workflow.devicerequest.queries.model.DeviceRequestQueryModel;
import com.zxplat.application.shared.product.DoctorStationAdviceInventoryUnitQuantityHelper;
import com.zxplat.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.zxplat.domain.administration.product.support.ProductDefinitionDisplaySupport;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.workflow.devicerequest.service.DeviceRequestDomainService;
import com.zxplat.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.zxplat.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 护士站耗材发放编排：门诊与住院分轨，共用「耗材是否自动扣库」开关。
 *
 * <ul>
 *   <li><strong>门诊护士执行</strong>：医生站签发已 FIFO 锁定批号/库房；护士换房仅改 {@code location_id}，
 *       禁止重跑 FIFO（BR-ORD-033 / BR-NUR-006）。</li>
 *   <li><strong>住院临时路径</strong>（护士划价 / 绑定执行 / 医技划价）：请求已带目录选中
 *       {@code lotNumber} 时按该批定批扣库；无批号时按 {@code performLocation} FIFO 定批
 *       （BR-IP-PRC-008 / BR-IP-PRC-019）。同事务多行待扣量参与预减，禁止重复抢同一库存行。</li>
 * </ul>
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
    /** 库存读侧：解析 inventoryItemId 与扣减回补。 */
    private final InventoryItemQueryService inventoryItemQueryService;
    /** 住院临时路径 FIFO 分段装配（门诊护士执行不调用）。 */
    private final DoctorStationAdviceFifoAllocationAssembler doctorStationAdviceFifoAllocationAssembler;
    /** 产品目录展示装配：库存不足提示点名耗材名。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

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
     * 门诊自动扣库门禁：护士已传耗材房时直接放行；未传时须每条待发发放行已锁定库房且具备真实批号。
     *
     * @param dispenseIds                勾选的发放行主键
     * @param requestInventoryLocationId 护士指定耗材房；非空时优先，执行时仅就地改 location_id
     */
    public void validateOutpatientAutoDeductInventoryLocation(
        List<Long> dispenseIds,
        Long requestInventoryLocationId) {
        // Gate：无勾选发放行则无需校验耗材房
        if (dispenseIds == null || dispenseIds.isEmpty()) {
            return;
        }
        // Gate：护士已指定耗材房，执行路径只改 location_id，仍须逐行确认已有真实批号
        for (Long dispenseId : dispenseIds) {
            if (dispenseId == null) {
                continue;
            }
            DeviceDispenseModel dispense = this.deviceDispenseQueryService.loadDeviceDispenseById(dispenseId);
            if (dispense == null) {
                continue;
            }
            // Gate：换房不改批号——签发必须已锁真实批号
            if (!this.hasRealLotNumber(dispense)) {
                throw new BusinessException(
                    ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "发放批号缺失，请医生改签后再执行");
            }
            // 护士已选房：执行时写该房，此处不再要求原 location
            if (requestInventoryLocationId != null) {
                continue;
            }
            // 未选房：须能从发放单解析库房
            if (this.resolveDispenseLocationId(dispense, null) == null) {
                throw new BusinessException(
                    ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "请选择耗材房后再执行发放");
            }
        }
    }

    /**
     * 门诊护士批量执行耗材：不自动扣库仅拣配为已配药；自动扣库则（可选）就地改库房后发放并累计扣减量。
     *
     * <p>护士传入 {@code nurseInventoryLocationId} 时：校验该房签发批号库存够量后，就地更新发放行
     * {@code location_id} 并回写请求 {@code location_id}/{@code perform_location}（与发放行一致），
     * <strong>不</strong>改批号、<strong>不</strong>重跑 FIFO、禁止软删重建；
     * 未传时沿用医生开立默认发材房与签发批号，仍回写请求使与发放行一致。</p>
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
        // Pre：同批「目标房+品规+批号」累计待扣，避免多行各自过闸后合计超库存
        Map<String, BigDecimal> batchReservedByLocationLot = new HashMap<>();
        try {
            for (Long dispenseId : sortedIds) {
                // Gate：仍须为可拣配态，防止并发已发/已配后重复执行
                DeviceDispenseValidationModel preparable =
                    this.deviceDispenseQueryService.validateDeviceDispensePreparable(dispenseId);
                if (preparable == null || Boolean.FALSE.equals(preparable.getAllow())) {
                    throw new BusinessException(
                        ErrorCodeConstants.DEVICE_DISPENSE_INVALID_STATUS,
                        "耗材发放单状态已变更，请刷新后重试");
                }
                DeviceDispenseModel dispense = this.deviceDispenseQueryService.mustGetDeviceDispense(dispenseId);
                // 分支：自动扣库开 → 可选就地改库房后发放+累计扣减；关 → 仅拣配为已配药
                if (this.deviceAutoInventoryDeductSettingResolver.isEnabled()) {
                    // Cmd：换房只改 location；写前按目标房+签发批号断言库存
                    this.applyInPlaceLocationIfNeeded(
                        dispense, nurseInventoryLocationId, operatorId, batchReservedByLocationLot);
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
     * 回退<strong>保留</strong>医生签发 {@code lot_number}（不重跑 FIFO、不置空批号）；仅清空拣配/发放操作字段。
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
     * 自动扣库：住院临时路径按选中批号或 FIFO 定批后拣配、发放并累计扣减。
     *
     * <p>与门诊护士执行不同：此处草稿发放行通常无医生签发批号；请求已落目录选中
     * {@code lotNumber} 时锁批定批，否则按库房 FIFO 写入 {@code lot_number} 再扣库。
     * 同事务 {@code inventoryDecreaseAccumulator} 参与定批预减。</p>
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
        // Gate：临时路径发放须指定库房，否则无法锁批/FIFO
        if (request.getPerformLocation() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "耗材发放库房不能为空");
        }
        // Cmd：住院临时路径——锁批或 FIFO 软删旧草稿/待发并写入新分段（含 location_id / lot_number）
        this.replaceFifoDraftsForInpatientTemporaryPath(
            request, request.getPerformLocation(), operatorId, inventoryDecreaseAccumulator);
        this.deviceDispenseDomainService.promoteDraftDispensesForDeviceRequests(List.of(request.getId()), operatorId);
        Long performerId = dispensePerformerId;
        // 未传发放人时回落操作人，保证拣配/发放人字段可落库
        if (performerId == null) {
            performerId = operatorId;
        }
        List<Long> dispenseIds = this.deviceDispenseQueryService.listDeviceDispenseIdsByDeviceRequest(request.getId());
        for (Long dispenseId : dispenseIds) {
            DeviceDispenseModel dispense = this.deviceDispenseQueryService.mustGetDeviceDispense(dispenseId);
            // Pre：定批分段须已落库真实批号（锁批或 FIFO 写入后）
            String lockedLot = trimToNull(dispense.getLotNumber());
            if (lockedLot == null
                || OutpatientNurseStationConstants.OUTPATIENT_NURSE_STATION_DEVICE_LOT_PLACEHOLDER.equals(lockedLot)) {
                throw new BusinessException(
                    ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "耗材发放批号未锁定，请确认库存批号后重试");
            }
            this.deviceDispenseDomainService.prepareDeviceDispense(
                this.buildPrepareCommand(
                    dispenseId,
                    operatorId,
                    lockedLot,
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
        // Pre：自动扣库须用签发真实批号拣配，禁止占位批号覆盖签发值
        String lot = this.requireSignedLotNumber(dispense);
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
     * 门诊护士执行：指定耗材房时断言目标房签发批号库存后，就地只回写 {@code location_id}。
     *
     * <p>仅门诊路径调用。禁止改批号、禁止 FIFO、禁止软删重建、禁止动账单。未指定房时仍按签发库房断言库存。</p>
     *
     * @param dispense                     发放行读模型
     * @param nurseInventoryLocationId     护士指定耗材房；空则用签发库房
     * @param operatorId                   操作人
     * @param batchReservedByLocationLot   本批同键已累计待扣量（库房|品规|批号 → 数量）
     */
    private void applyInPlaceLocationIfNeeded(
        DeviceDispenseModel dispense,
        Long nurseInventoryLocationId,
        Long operatorId,
        Map<String, BigDecimal> batchReservedByLocationLot) {
        // Gate：发放行与数量须有效
        if (dispense == null || dispense.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "发放记录无效");
        }
        // Gate：换房/扣库不改批号——须已有真实签发批号
        if (!this.hasRealLotNumber(dispense)) {
            throw new BusinessException(
                ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "发放批号缺失，请医生改签后再执行");
        }
        Long targetLocationId = this.resolveDispenseLocationId(dispense, nurseInventoryLocationId);
        // Gate：解析不到发放库房则拒绝
        if (targetLocationId == null) {
            throw new BusinessException(
                ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "请选择耗材房后再执行发放");
        }
        BigDecimal lineQty = dispense.getQuantity();
        if (lineQty == null || lineQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "发放数量无效");
        }
        String lotTrim = trimToNull(dispense.getLotNumber());
        String reserveKey =
            targetLocationId + "|" + dispense.getDeviceDefId() + "|" + lotTrim;
        BigDecimal alreadyReserved = BigDecimal.ZERO;
        // Pre：读取本批同键已预占量
        if (batchReservedByLocationLot != null && batchReservedByLocationLot.containsKey(reserveKey)) {
            alreadyReserved = batchReservedByLocationLot.get(reserveKey);
        }
        // Gate：目标耗材房（护士选中或默认）下签发批号可用量须够；不够点名耗材与批号
        String deviceDisplayName = this.resolveDeviceDisplayNameForStockHint(dispense.getDeviceDefId());
        this.inventoryItemQueryService.assertSufficientForDeviceDispenseAtLocationLot(
            TableNameConstants.ADM_DEVICE_DEFINITION,
            dispense.getDeviceDefId(),
            targetLocationId,
            lotTrim,
            lineQty,
            alreadyReserved,
            deviceDisplayName);
        // Pre：记入本批预占，供后续同行合计
        if (batchReservedByLocationLot != null) {
            batchReservedByLocationLot.put(reserveKey, alreadyReserved.add(lineQty));
        }
        // Cmd：护士换房且与发放行不同 → 就地改发放 location_id（批号不变）
        boolean locationChangedOnDispense = nurseInventoryLocationId != null
            && !nurseInventoryLocationId.equals(dispense.getLocationId());
        if (locationChangedOnDispense) {
            UpdateDeviceDispenseLocationCommand cmd = new UpdateDeviceDispenseLocationCommand();
            cmd.setId(dispense.getId());
            cmd.setLocationId(nurseInventoryLocationId);
            cmd.setOperatorId(operatorId);
            this.deviceDispenseDomainService.updateLocationForNurseExecute(cmd);
        }
        // Cmd：请求发材房与发放行 location_id 必须一致（选中或默认均回写）
        if (dispense.getDeviceReqId() != null) {
            this.deviceRequestDomainService.rebindPerformLocationForOutpatientNurseExecute(
                dispense.getDeviceReqId(), targetLocationId);
        }
    }

    /**
     * 住院临时路径：按 performLocation 对请求下草稿/待发发放行做定批替换。
     *
     * <p>请求 {@code lotNumber} 非空时锁该批；空则 FIFO。同事务待扣累加器参与账面预减。</p>
     *
     * @param request                        耗材请求列表行
     * @param performLocationId              执行库房位置主键
     * @param operatorId                     操作人
     * @param inventoryDecreaseAccumulator   同事务待扣累计；可空
     */
    private void replaceFifoDraftsForInpatientTemporaryPath(
        DeviceRequestListModel request,
        Long performLocationId,
        Long operatorId,
        Map<Long, BigDecimal> inventoryDecreaseAccumulator) {
        String lockedLot = trimToNull(request.getLotNumber());
        List<ReplaceFifoDeviceDispenseDraftsCommand.Segment> segments =
            this.doctorStationAdviceFifoAllocationAssembler.allocateDeviceFifoAtPerformLocation(
                request.getEncounterId(),
                performLocationId,
                request.getDeviceDefId(),
                request.getUnitCode(),
                request.getQuantity(),
                lockedLot,
                inventoryDecreaseAccumulator);
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
     * 解析库存不足提示用的耗材展示名（通用名优先）；目录缺失时回落「耗材」。
     *
     * @param deviceDefinitionId 耗材定义主键
     * @return 非空展示名
     */
    private String resolveDeviceDisplayNameForStockHint(Long deviceDefinitionId) {
        // Gate：无定义主键则无法查目录名
        if (deviceDefinitionId == null) {
            return "耗材";
        }
        try {
            // Pre：目录读模型与医生站 FIFO 点名口径一致
            ProductDefinitionDisplayDto productRow =
                DoctorStationAdviceInventoryUnitQuantityHelper.requireProductDefinition(
                    this.productDefinitionDisplayTerminologyAssembler,
                    TableNameConstants.ADM_DEVICE_DEFINITION,
                    deviceDefinitionId);
            return ProductDefinitionDisplaySupport.resolveProductDisplayName(productRow, "耗材");
        } catch (BusinessException ex) {
            // 分支：目录缺失时仍抛库存不足主因，点名回落通用「耗材」
            return "耗材";
        }
    }

    /**
     * 发放行是否已有真实批号（非空且非占位）。
     *
     * @param dispense 发放行读模型
     * @return 有真实批号时为 true
     */
    private boolean hasRealLotNumber(DeviceDispenseModel dispense) {
        // Gate：空发放行无批号
        if (dispense == null) {
            return false;
        }
        String lot = trimToNull(dispense.getLotNumber());
        if (lot == null) {
            return false;
        }
        return !OutpatientNurseStationConstants.OUTPATIENT_NURSE_STATION_DEVICE_LOT_PLACEHOLDER.equals(lot);
    }

    /**
     * 自动扣库拣配须使用医生签发真实批号；缺失则拒绝（禁止占位批号覆盖签发值）。
     *
     * @param dispense 发放行读模型
     * @return 签发批号（已 trim）
     * @throws BusinessException 批号缺失或为占位时
     */
    private String requireSignedLotNumber(DeviceDispenseModel dispense) {
        // Gate：无真实批号则无法按已收费口径扣库
        if (!this.hasRealLotNumber(dispense)) {
            throw new BusinessException(
                ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "发放批号缺失，请医生改签后再执行");
        }
        return trimToNull(dispense.getLotNumber());
    }

    /**
     * 解析发放行库房：护士指定耗材房优先；未传时回落发放单锁定 {@code location_id}。
     *
     * @param dispense                   发放行读模型
     * @param nurseInventoryLocationId   护士指定耗材房
     * @return 库房位置主键；无法解析时返回 null
     */
    private Long resolveDispenseLocationId(DeviceDispenseModel dispense, Long nurseInventoryLocationId) {
        // 护士指定优先
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
