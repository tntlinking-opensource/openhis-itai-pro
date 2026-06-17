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

package com.openhis.health.domain.workflow.devicedispense.service;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.events.workflow.devicedispense.DeviceDispenseCancelledEvent;
import com.openhis.health.domain.share.events.workflow.devicedispense.DeviceDispenseCreatedEvent;
import com.openhis.health.domain.share.events.workflow.devicedispense.DeviceDispenseDeclinedEvent;
import com.openhis.health.domain.share.events.workflow.devicedispense.DeviceDispensePendingRefundCreatedEvent;
import com.openhis.health.domain.share.events.workflow.devicedispense.DeviceDispensePreparedEvent;
import com.openhis.health.domain.share.events.workflow.devicedispense.DeviceDispenseRefundedEvent;
import com.openhis.health.domain.share.events.workflow.devicedispense.DeviceDispenseReserveReleasedEvent;
import com.openhis.health.domain.share.events.workflow.devicedispense.DeviceDispenseStoppedEvent;
import com.openhis.health.domain.share.events.workflow.devicedispense.DeviceDispenseSummarizedEvent;
import com.openhis.health.domain.share.events.workflow.devicedispense.DeviceDispenseSummaryCancelledEvent;
import com.openhis.health.domain.share.events.workflow.devicedispense.DeviceDispenseSummaryCompletedEvent;
import com.openhis.health.domain.share.events.workflow.devicedispense.DeviceDispensedEvent;
import com.openhis.health.domain.workflow.devicedispense.command.AppendCompletedDeviceDispenseStepCommand;
import com.openhis.health.domain.workflow.devicedispense.command.CancelDeviceDispenseSummaryCommand;
import com.openhis.health.domain.workflow.devicedispense.command.CancelUndispensedDeviceDispenseCommand;
import com.openhis.health.domain.workflow.devicedispense.command.CreatePendingRefundDeviceDispenseCommand;
import com.openhis.health.domain.workflow.devicedispense.command.DeclinePendingRefundDeviceDispenseCommand;
import com.openhis.health.domain.workflow.devicedispense.command.DispenseDeviceCommand;
import com.openhis.health.domain.workflow.devicedispense.command.GenerateDeviceDispenseFromExecutionCommand;
import com.openhis.health.domain.workflow.devicedispense.command.GenerateDeviceDispenseFromTemporaryPathCommand;
import com.openhis.health.domain.workflow.devicedispense.command.GenerateIssuedDeviceDispenseFromExecutionCommand;
import com.openhis.health.domain.workflow.devicedispense.command.HoldDeviceDispenseCommand;
import com.openhis.health.domain.workflow.devicedispense.command.MatchDispenseLotNumberCommand;
import com.openhis.health.domain.workflow.devicedispense.command.PrepareDeviceDispenseCommand;
import com.openhis.health.domain.workflow.devicedispense.command.ReplaceFifoDeviceDispenseDraftsCommand;
import com.openhis.health.domain.workflow.devicedispense.command.PromoteDeviceDispenseToPreparationCommand;
import com.openhis.health.domain.workflow.devicedispense.command.RecallDispensedDeviceDispenseCommand;
import com.openhis.health.domain.workflow.devicedispense.command.RefundDispensedDeviceDispenseCommand;
import com.openhis.health.domain.workflow.devicedispense.command.ReleaseDeviceDispenseReserveCommand;
import com.openhis.health.domain.workflow.devicedispense.command.RemoveUndispensedDeviceDispenseCommand;
import com.openhis.health.domain.workflow.devicedispense.command.RevertCompletedBatchToPreparationCommand;
import com.openhis.health.domain.workflow.devicedispense.command.RevertPreparedBatchToPreparationCommand;
import com.openhis.health.domain.workflow.devicedispense.command.RollbackUndispensedDeviceDispenseCommand;
import com.openhis.health.domain.workflow.devicedispense.command.StopUndispensedDeviceDispenseCommand;
import com.openhis.health.domain.workflow.devicedispense.command.UpdateDispenseStatusCompletedFromSummaryCommand;
import com.openhis.health.domain.workflow.devicedispense.command.UpdateDispenseStatusSummarizedCommand;
import com.openhis.health.domain.workflow.devicedispense.entity.DeviceDispense;
import com.openhis.health.domain.workflow.devicedispense.repository.DeviceDispenseRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

/**
 * 耗材发放写侧领域服务。
 *
 * <p>说明：本类只收口本聚合事实与状态；库存扣减/回补、追溯轨迹、收费联动由应用层编排。</p>
 */
@Service
@RequiredArgsConstructor
public class DeviceDispenseDomainService {

    private static final Pattern COMMA_LONG_IDS = Pattern.compile("^(\\d+,)*\\d+$");

    private static final Pattern SINGLE_LONG = Pattern.compile("^\\d+$");

    private final DeviceDispenseRepository deviceDispenseRepository;
    private final IDomainEventPublisher domainEventPublisher;
    /** 耗材发放 busNo：业务流水按日递增，前缀 {@link AggregateTypeConstants#DEVICE_DISPENSE}。 */
    private final BusinessSequenceService businessSequenceService;


    /**
     * 拣配：准备态下写入拣配人、批号/追溯码与数量，发布已准备事件。
     *
     * @param command 批次内一条，须含发放记录 id 与拣配信息
     * @throws BusinessException 批次条数、参数或状态不允许拣配时
     */
    public void prepareDeviceDispense(PrepareDeviceDispenseCommand command) {
        List<PrepareDeviceDispenseCommand.PrepareDeviceDispenseItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "拣配一次仅允许操作一条发放记录");
        }
        PrepareDeviceDispenseCommand.PrepareDeviceDispenseItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "id 不能为空");
        }

        DeviceDispense dispense = this.mustGetById(item.getId());
        dispense.assertPreparable();

        LocalDateTime now = LocalDateTime.now();
        dispense.preparationToPreparedOrInProgress(item.getPreparerId(), item.getLotNumber(), item.getTraceNo(), item.getQuantity(), now);
        deviceDispenseRepository.save(dispense);

        domainEventPublisher.publish(new DeviceDispensePreparedEvent(dispense.getId(), dispense.getBusNo(), item.getPreparerId(), now, command.getOperatorId()));
    }

    /**
     * 核对发放：推进完成/部分完成并发布已发放事件。
     *
     * @param command 批次内一条，须含 id、发放数量与执行人
     * @throws BusinessException 参数非法或状态不允许时
     */
    public void dispenseDevice(DispenseDeviceCommand command) {
        List<DispenseDeviceCommand.DispenseDeviceItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "核对发放一次仅允许操作一条发放记录");
        }
        DispenseDeviceCommand.DispenseDeviceItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "id 不能为空");
        }
        // 业务分支处理。
        if (item.getDispenseQuantity() == null || item.getDispenseQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "dispenseQuantity 必须大于 0");
        }

        DeviceDispense dispense = this.mustGetById(item.getId());
        dispense.assertDispensable();

        LocalDateTime now = LocalDateTime.now();
        dispense.toCompletedOrPartCompleted(item.getPerformerId(), item.getDispenseQuantity(), now);
        deviceDispenseRepository.save(dispense);

        domainEventPublisher.publish(new DeviceDispensedEvent(
            dispense.getId(), dispense.getBusNo(), item.getDispenseQuantity(), dispense.getLotNumber(), dispense.getTraceNo(), command.getOperatorId()));
    }

    /**
     * 作废未发放记录：写入未执行原因并发布拒绝事件。
     *
     * @param command 批次内一条，须含 id 与 notPerformedReasonEnum
     * @throws BusinessException 参数或状态不允许时
     */
    public void cancelUndispensedDeviceDispense(CancelUndispensedDeviceDispenseCommand command) {
        List<CancelUndispensedDeviceDispenseCommand.CancelUndispensedDeviceDispenseItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "作废一次仅允许操作一条发放记录");
        }
        CancelUndispensedDeviceDispenseCommand.CancelUndispensedDeviceDispenseItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "id 不能为空");
        }
        // 业务分支处理。
        if (item.getNotPerformedReasonEnum() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "notPerformedReasonEnum 不能为空");
        }

        DeviceDispense dispense = this.mustGetById(item.getId());
        dispense.toDeclined(item.getNotPerformedReasonEnum());
        deviceDispenseRepository.save(dispense);

        domainEventPublisher.publish(new DeviceDispenseDeclinedEvent(dispense.getId(), dispense.getBusNo(), item.getNotPerformedReasonEnum(), command.getOperatorId()));
    }

    /**
     * 退耗确认：待退记录转为已退，发布退款事件。
     *
     * @param command 批次内多条；每条须含 id 与 refundQuantity
     * @throws BusinessException 不可退或参数非法时
     */
    public void refundDispensedDeviceDispense(RefundDispensedDeviceDispenseCommand command) {
        List<RefundDispensedDeviceDispenseCommand.RefundDispensedDeviceDispenseItemCommand> items = command.resolveItems();
        List<DeviceDispense> toSave = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (RefundDispensedDeviceDispenseCommand.RefundDispensedDeviceDispenseItemCommand item : items) {
            if (item.getId() == null) {
                throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "id 不能为空");
            }
            if (item.getRefundQuantity() == null || item.getRefundQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "refundQuantity 必须大于 0");
            }
            DeviceDispense dispense = this.mustGetById(item.getId());
            dispense.assertRefundableAndIdempotent();
            Long performerId = item.getPractitionerId();
            if (performerId == null) {
                performerId = command.getOperatorId();
            }
            dispense.pendingRefundToRefundedWithAudit(performerId, now, item.getReasonText());
            toSave.add(dispense);
        }
        deviceDispenseRepository.saveAll(toSave);
        for (DeviceDispense dispense : toSave) {
            domainEventPublisher.publish(
                new DeviceDispenseRefundedEvent(dispense.getId(), dispense.getBusNo(), dispense.getDispenseQuantity(), command.getOperatorId()));
        }
    }

    /**
     * 门诊药房拒绝耗材退药：待退记录推进为已拒绝终态。
     *
     * @param command 拒绝命令
     */
    public void declinePendingRefundDeviceDispense(DeclinePendingRefundDeviceDispenseCommand command) {
        List<DeclinePendingRefundDeviceDispenseCommand.Item> items = command.resolveItems();
        List<DeviceDispense> toSave = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (DeclinePendingRefundDeviceDispenseCommand.Item item : items) {
            DeviceDispense dispense = this.mustGetById(item.getId());
            dispense.pendingRefundToDeclined(item.getDeclineReasonText(), item.getPractitionerId(), now);
            toSave.add(dispense);
        }
        deviceDispenseRepository.saveAll(toSave);
        for (DeviceDispense dispense : toSave) {
            domainEventPublisher.publish(
                new DeviceDispenseDeclinedEvent(dispense.getId(), dispense.getBusNo(), dispense.getNotPerformedReasonEnum(), command.getOperatorId()));
        }
    }

    /**
     * 临时路径生成草稿发放单：同请求仅允许一条草稿，分配 busNo 后发布创建事件。
     *
     * @param command 批次内一条，须含 deviceReqId、设备定义、就诊患者及数量单位等
     * @return 新发放记录主键
     * @throws BusinessException 已存在草稿或参数非法时
     */
    public Long generateDeviceDispenseFromTemporaryPath(GenerateDeviceDispenseFromTemporaryPathCommand command) {
        List<GenerateDeviceDispenseFromTemporaryPathCommand.GenerateDeviceDispenseFromTemporaryPathItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "生成草稿一次仅允许生成一条发放记录");
        }
        GenerateDeviceDispenseFromTemporaryPathCommand.GenerateDeviceDispenseFromTemporaryPathItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getDeviceReqId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "deviceReqId 不能为空");
        }
        // 业务分支处理。
        if (item.getDeviceDefId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "deviceDefId 不能为空");
        }
        // 业务分支处理。
        if (item.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "encounterId 不能为空");
        }
        // 业务分支处理。
        if (item.getPatientId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "patientId 不能为空");
        }
        // 业务分支处理。
        if (item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "quantity 必须大于 0");
        }
        // 业务分支处理。
        if (item.getUnitCode() == null || item.getUnitCode().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "unitCode 不能为空");
        }

        // 业务分支处理。
        if (deviceDispenseRepository.countByDeviceReqIdAndStatusEnum(item.getDeviceReqId(), DispenseStatus.DRAFT.getCode()) > 0) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_RULE_CONFLICT, "同请求已存在草稿发放单");
        }

        DeviceDispense dispense = new DeviceDispense();
        dispense.setBusNo(businessSequenceService.getSeqByDay(AggregateTypeConstants.DEVICE_DISPENSE, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH));
        dispense.setDeviceReqId(item.getDeviceReqId());
        dispense.setDeviceDefId(item.getDeviceDefId());
        dispense.setEncounterId(item.getEncounterId());
        dispense.setPatientId(item.getPatientId());
        dispense.setQuantity(item.getQuantity());
        dispense.setUnitCode(item.getUnitCode());
        dispense.setLimitTime(item.getLimitTime());
        dispense.assertTemporaryGenerateAllowed();
        dispense.initDraft();

        DeviceDispense saved = deviceDispenseRepository.save(dispense);
        domainEventPublisher.publish(new DeviceDispenseCreatedEvent(saved.getId(), saved.getBusNo(), saved.getDeviceReqId(), saved.getStatusEnum(), command.getOperatorId()));
        return saved.getId();
    }

    /**
     * 门诊医生站耗材签发后：按 FIFO 分段替换草稿/待发发放事实（软删旧行后批量插入）。
     *
     * @param command 分段清单与请求上下文
     * @return 新建发放主键列表（与分段顺序一致）
     * @throws BusinessException 参数不完整、分段为空或存在不可覆盖状态时
     */
    public List<Long> replaceFifoDraftDispensesAfterDoctorStationSign(ReplaceFifoDeviceDispenseDraftsCommand command) {
        List<ReplaceFifoDeviceDispenseDraftsCommand.Segment> segments = command.resolveSegments();
        if (command.getOperatorId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "operatorId 不能为空");
        }
        if (command.getDeviceReqId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "deviceReqId 不能为空");
        }
        if (command.getDeviceDefId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "deviceDefId 不能为空");
        }
        if (command.getEncounterId() == null || command.getPatientId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "encounterId/patientId 不能为空");
        }
        if (command.getUnitCode() == null || command.getUnitCode().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "unitCode 不能为空");
        }
        if (segments.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "FIFO 分段不能为空");
        }
        List<DeviceDispense> siblings = deviceDispenseRepository.findByDeviceReqIdIn(List.of(command.getDeviceReqId()));
        List<DeviceDispense> toDelete = new ArrayList<>();
        for (DeviceDispense row : siblings) {
            // 已拣配/已发等非草稿链路不允许医生站签发相位重写。
            if (!dispenseStatusEquals(row, DispenseStatus.DRAFT) && !dispenseStatusEquals(row, DispenseStatus.PREPARATION)) {
                throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_RULE_CONFLICT, "耗材医嘱已存在执行中的发放记录，禁止签发重置");
            }
            if (dispenseStatusEquals(row, DispenseStatus.DRAFT) || dispenseStatusEquals(row, DispenseStatus.PREPARATION)) {
                toDelete.add(row);
            }
        }
        Long anchorForReleaseEvent = null;
        if (!toDelete.isEmpty()) {
            anchorForReleaseEvent = toDelete.get(0).getId();
            deviceDispenseRepository.deleteAll(toDelete);
            domainEventPublisher.publish(new DeviceDispenseReserveReleasedEvent(
                anchorForReleaseEvent, String.valueOf(command.getDeviceReqId()), command.getOperatorId()));
        }
        List<String> busSeqList = businessSequenceService.getSeqByDay(
            AggregateTypeConstants.DEVICE_DISPENSE, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH, segments.size());
        List<DeviceDispense> created = new ArrayList<>();
        int seqIndex = 0;
        for (ReplaceFifoDeviceDispenseDraftsCommand.Segment segment : segments) {
            if (segment.getLocationId() == null) {
                throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "FIFO 分段 locationId 不能为空");
            }
            if (segment.getQuantity() == null || segment.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "FIFO 分段 quantity 必须大于 0");
            }
            DeviceDispense dispense = new DeviceDispense();
            dispense.setBusNo(busSeqList.get(seqIndex));
            seqIndex = seqIndex + 1;
            dispense.setDeviceReqId(command.getDeviceReqId());
            dispense.setDeviceDefId(command.getDeviceDefId());
            dispense.setEncounterId(command.getEncounterId());
            dispense.setPatientId(command.getPatientId());
            dispense.setQuantity(segment.getQuantity());
            dispense.setUnitCode(command.getUnitCode());
            dispense.setLocationId(segment.getLocationId());
            dispense.setLotNumber(segment.getLotNumber());
            dispense.initDraft();
            created.add(dispense);
        }
        List<DeviceDispense> savedRows = deviceDispenseRepository.saveAll(created);
        List<Long> createdIds = new ArrayList<>();
        for (DeviceDispense saved : savedRows) {
            createdIds.add(saved.getId());
            domainEventPublisher.publish(new DeviceDispenseCreatedEvent(
                saved.getId(), saved.getBusNo(), saved.getDeviceReqId(), saved.getStatusEnum(), command.getOperatorId()));
        }
        return createdIds;
    }

    /**
     * 判断耗材发放是否处于给定状态（字符串码与枚举对齐）。
     *
     * @param row    发放聚合
     * @param status 期望状态
     * @return 是否一致
     */
    private static boolean dispenseStatusEquals(DeviceDispense row, DispenseStatus status) {
        if (row == null || row.getStatusEnum() == null || status == null) {
            return false;
        }
        return status.getCode().equals(row.getStatusEnum());
    }

    /**
     * 批量将草稿推进为准备态（拣配前）。
     *
     * @param command 须含 idList，且全部 id 须存在
     * @throws BusinessException id 缺失或部分记录不存在时
     */
    public void promoteDeviceDispenseToPreparation(PromoteDeviceDispenseToPreparationCommand command) {
        // 业务分支处理。
        if (command.getIdList() == null || command.getIdList().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "idList 不能为空");
        }
        Set<Long> distinct = new LinkedHashSet<>(command.getIdList());
        List<DeviceDispense> rows = deviceDispenseRepository.findByIdIn(new ArrayList<>(distinct));
        // 业务分支处理。
        if (rows.size() != distinct.size()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_NOT_FOUND, "部分发放记录不存在");
        }
        for (DeviceDispense dispense : rows) {
            dispense.draftToPreparation();
        }
        deviceDispenseRepository.saveAll(rows);
    }

    /**
     * 门诊收费后：将给定耗材请求下仍处于草稿的发放记录批量推进为待发（PREPARATION）。
     *
     * @param deviceReqIds 耗材请求主键集合
     * @param operatorId   操作人（命令透传）
     */
    public void promoteDraftDispensesForDeviceRequests(Collection<Long> deviceReqIds, Long operatorId) {
        if (deviceReqIds == null || deviceReqIds.isEmpty()) {
            return;
        }
        List<Long> distinctIds = deviceReqIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (distinctIds.isEmpty()) {
            return;
        }
        List<DeviceDispense> rows = deviceDispenseRepository.findByDeviceReqIdIn(distinctIds);
        List<Long> draftDispenseIds = new ArrayList<>();
        for (DeviceDispense row : rows) {
            if (DispenseStatus.DRAFT.getCode().equals(row.getStatusEnum())) {
                draftDispenseIds.add(row.getId());
            }
        }
        if (draftDispenseIds.isEmpty()) {
            return;
        }
        PromoteDeviceDispenseToPreparationCommand cmd = new PromoteDeviceDispenseToPreparationCommand();
        cmd.setIdList(draftDispenseIds);
        cmd.setOperatorId(operatorId);
        promoteDeviceDispenseToPreparation(cmd);
    }

    /**
     * 由执行上下文生成待发记录：复制同请求模板上下文，新 busNo，初始化准备态并发布创建事件。
     *
     * @param command 批次内一条，须含 deviceReqId 及执行侧计划信息
     * @return 新发放记录主键
     * @throws BusinessException 缺少模板上下文或不允许生成时
     */
    public Long generateDeviceDispenseFromExecution(GenerateDeviceDispenseFromExecutionCommand command) {
        List<GenerateDeviceDispenseFromExecutionCommand.GenerateDeviceDispenseFromExecutionItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "一次仅允许生成一条发放记录");
        }
        GenerateDeviceDispenseFromExecutionCommand.GenerateDeviceDispenseFromExecutionItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getDeviceReqId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "deviceReqId 不能为空");
        }
        DeviceDispense template = this.mustGetTemplateForDeviceReq(item.getDeviceReqId());
        template.assertExecutionGenerateAllowed();

        DeviceDispense created = this.shallowCopyForNew(template);
        created.setBusNo(businessSequenceService.getSeqByDay(AggregateTypeConstants.DEVICE_DISPENSE, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH));
        created.initPreparation();
        created.setProcedureId(item.getProcedureId());
        created.setLocationId(item.getLocationId());
        created.setPlannedDispenseTime(item.getPlannedDispenseTime());

        DeviceDispense saved = deviceDispenseRepository.save(created);
        domainEventPublisher.publish(new DeviceDispenseCreatedEvent(saved.getId(), saved.getBusNo(), saved.getDeviceReqId(), saved.getStatusEnum(), command.getOperatorId()));
        return saved.getId();
    }

    /**
     * 执行路径直发：基于同请求首条上下文复制后直接置完成态，发布已发放事件。
     *
     * @param command 批次内一条，须含 deviceReqId、发放数量及批号追溯等
     * @return 新发放记录主键
     * @throws BusinessException 不允许直发或参数非法时
     */
    public Long generateIssuedDeviceDispenseFromExecution(GenerateIssuedDeviceDispenseFromExecutionCommand command) {
        List<GenerateIssuedDeviceDispenseFromExecutionCommand.GenerateIssuedDeviceDispenseFromExecutionItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "一次仅允许直发一条发放记录");
        }
        GenerateIssuedDeviceDispenseFromExecutionCommand.GenerateIssuedDeviceDispenseFromExecutionItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getDeviceReqId() == null || item.getDispenseQuantity() == null || item.getDispenseQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "deviceReqId 与 dispenseQuantity 必填且数量大于 0");
        }
        DeviceDispense template = this.mustGetTemplateForDeviceReq(item.getDeviceReqId());
        DeviceDispense created = this.shallowCopyForNew(template);
        created.setBusNo(businessSequenceService.getSeqByDay(AggregateTypeConstants.DEVICE_DISPENSE, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH));
        created.setLotNumber(item.getLotNumber());
        created.setTraceNo(item.getTraceNo());
        created.setProcedureId(item.getProcedureId());
        created.setQuantity(item.getDispenseQuantity());
        created.assertDirectIssueAllowed();
        LocalDateTime now = LocalDateTime.now();
        created.initCompleted(command.getOperatorId(), item.getDispenseQuantity(), now);

        DeviceDispense saved = deviceDispenseRepository.save(created);
        domainEventPublisher.publish(new DeviceDispensedEvent(
            saved.getId(), saved.getBusNo(), item.getDispenseQuantity(), saved.getLotNumber(), saved.getTraceNo(), command.getOperatorId()));
        return saved.getId();
    }

    /**
     * 基于原发放单创建待退行：新 busNo 带 {@code -R} 后缀，关联源单，发布待退创建事件。
     *
     * @param command 批次内一条，须含 sourceDispenseId 与 refundQuantity
     * @return 待退记录主键
     * @throws BusinessException 源单不可建待退或参数非法时
     */
    public Long createPendingRefundDeviceDispense(CreatePendingRefundDeviceDispenseCommand command) {
        List<CreatePendingRefundDeviceDispenseCommand.CreatePendingRefundDeviceDispenseItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "一次仅允许创建一条待退记录");
        }
        CreatePendingRefundDeviceDispenseCommand.CreatePendingRefundDeviceDispenseItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getSourceDispenseId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "sourceDispenseId 不能为空");
        }
        DeviceDispense source = this.mustGetById(item.getSourceDispenseId());
        source.assertPendingRefundCreatable(item.getRefundQuantity());

        DeviceDispense line = this.shallowCopyForNew(source);
        line.setBusNo(businessSequenceService.getSeqByDay(AggregateTypeConstants.DEVICE_DISPENSE, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH) + "-R");
        line.setBasedOnTable("wkf_device_dispense");
        line.setBasedOnId(source.getId());
        line.toPendingRefund(item.getRefundQuantity());

        DeviceDispense saved = deviceDispenseRepository.save(line);
        domainEventPublisher.publish(new DeviceDispensePendingRefundCreatedEvent(
            saved.getId(), saved.getBusNo(), source.getId(), item.getRefundQuantity(), command.getOperatorId()));
        return saved.getId();
    }

    /**
     * 释放预留：按灵活键解析多条发放记录，将仍处于可释放态的行停发并发布预留释放事件。
     *
     * <p>与 {@link com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseDomainService#releaseDispenseReserve}
     * 对称：未命中或已全部释放时幂等静默返回，避免门诊签退重复调用报错。</p>
     *
     * @param command 须含 idListOrRequestKey（逗号分隔发放 id、单 id 或 deviceReqId/supportInfo）
     * @throws BusinessException 定位键为空时
     */
    public void releaseDeviceDispenseReserve(ReleaseDeviceDispenseReserveCommand command) {
        List<DeviceDispense> rows = this.resolveFlexibleKey(command.getIdListOrRequestKey());
        // 业务分支处理：未命中任何发放行时幂等返回（签退路径可能已无待发事实）。
        if (rows.isEmpty()) {
            return;
        }
        List<DeviceDispense> released = new ArrayList<>();
        for (DeviceDispense dispense : rows) {
            // 仅草稿/待发/已准备态释放预占；已停发、已发放等行跳过。
            if (dispenseStatusEquals(dispense, DispenseStatus.DRAFT)
                || dispenseStatusEquals(dispense, DispenseStatus.PREPARATION)
                || dispenseStatusEquals(dispense, DispenseStatus.PREPARED)) {
                dispense.toStoppedOrDeleted(null);
                released.add(dispense);
            }
        }
        // 业务分支处理：无可释放行时静默返回。
        if (released.isEmpty()) {
            return;
        }
        deviceDispenseRepository.saveAll(released);
        Long anchor = released.get(0).getId();
        domainEventPublisher.publish(new DeviceDispenseReserveReleasedEvent(anchor, command.getIdListOrRequestKey(), command.getOperatorId()));
    }

    /**
     * 批号匹配门控：解析目标行后保持准备态（业务上用于锁定拣配口径前的校验路径）。
     *
     * @param command 与 {@link #releaseDeviceDispenseReserve} 相同的灵活定位键
     * @throws BusinessException 未命中记录时
     */
    public void matchDispenseLotNumber(MatchDispenseLotNumberCommand command) {
        List<DeviceDispense> rows = this.resolveFlexibleKey(command.getIdListOrRequestKey());
        // 业务分支处理。
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_NOT_FOUND, "未命中发放记录");
        }
        for (DeviceDispense dispense : rows) {
            dispense.keepPreparation();
        }
    }

    /**
     * 将目标行推进为「已汇总」态，写入汇总锚点并发布汇总事件。
     *
     * @param command id 列表或 summaryKey 解析目标集合
     * @throws BusinessException 未命中或不可汇总时
     */
    public void updateDispenseStatusSummarized(UpdateDispenseStatusSummarizedCommand command) {
        List<DeviceDispense> rows = this.resolveSummarizedTargets(command.getIdListOrSummaryKey());
        // 业务分支处理。
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_NOT_FOUND, "未命中发放记录");
        }
        String anchor = command.getIdListOrSummaryKey().trim();
        for (DeviceDispense dispense : rows) {
            dispense.assertSummarizable();
            dispense.toSummarized(anchor);
        }
        deviceDispenseRepository.saveAll(rows);
        domainEventPublisher.publish(new DeviceDispenseSummarizedEvent(rows.get(0).getId(), anchor, command.getOperatorId()));
    }

    /**
     * 汇总批次完成：解析 {@code batchKey|id1,id2} 格式，全部行由汇总态转完成并发布完成事件。
     *
     * @param command summaryKeyOrIdList，id 段为逗号分隔主键列表
     * @throws BusinessException 格式非法、记录缺失或不可完成时
     */
    public void updateDispenseStatusCompletedFromSummary(UpdateDispenseStatusCompletedFromSummaryCommand command) {
        SummaryCompletedParse parsed = this.parseSummaryCompleted(command.getSummaryKeyOrIdList());
        List<DeviceDispense> rows = deviceDispenseRepository.findByIdIn(parsed.idList());
        // 业务分支处理。
        if (rows.size() != parsed.idList().size()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_NOT_FOUND, "部分发放记录不存在");
        }
        for (DeviceDispense dispense : rows) {
            dispense.assertSummaryCompletable();
            dispense.summarizedToCompleted();
        }
        deviceDispenseRepository.saveAll(rows);
        // 存在批次键时使用批次键作为事件标识，否则以首条 ID 作为兜底批次键。
        String batchKey;
        if (parsed.batchKey() != null) {
            batchKey = parsed.batchKey();
        } else {
            batchKey = String.valueOf(parsed.idList().get(0));
        }
        domainEventPublisher.publish(new DeviceDispenseSummaryCompletedEvent(rows.get(0).getId(), batchKey, parsed.idList(), command.getOperatorId()));
    }

    /**
     * 按汇总批次键取消汇总：supportInfo 命中的行从汇总态回退准备态，发布取消汇总事件。
     *
     * @param command 须非空 summaryKey
     * @throws BusinessException 批次不存在或不可取消时
     */
    public void cancelDeviceDispenseSummary(CancelDeviceDispenseSummaryCommand command) {
        // 业务分支处理。
        if (command.getSummaryKey() == null || command.getSummaryKey().isBlank()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "summaryKey 不能为空");
        }
        List<DeviceDispense> rows = deviceDispenseRepository.findBySupportInfo(command.getSummaryKey().trim());
        // 业务分支处理。
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_NOT_FOUND, "未找到汇总批次");
        }
        for (DeviceDispense dispense : rows) {
            dispense.assertSummaryCancellable();
            dispense.summarizedToPreparation();
        }
        deviceDispenseRepository.saveAll(rows);
        domainEventPublisher.publish(new DeviceDispenseSummaryCancelledEvent(rows.get(0).getId(), command.getSummaryKey().trim(), command.getOperatorId()));
    }

    /**
     * 未发放批量回退草稿态。
     *
     * @param command 须含非空 idList 且全部存在
     * @throws BusinessException 部分 id 不存在时
     */
    public void rollbackUndispensedDeviceDispense(RollbackUndispensedDeviceDispenseCommand command) {
        // 业务分支处理。
        if (command.getIdList() == null || command.getIdList().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "idList 不能为空");
        }
        List<DeviceDispense> rows = deviceDispenseRepository.findByIdIn(command.getIdList());
        // 业务分支处理。
        if (rows.size() != command.getIdList().size()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_NOT_FOUND, "部分发放记录不存在");
        }
        for (DeviceDispense dispense : rows) {
            dispense.toDraft();
        }
        deviceDispenseRepository.saveAll(rows);
    }

    /**
     * 未发放逻辑删除：灵活键解析后逐条 logicalDelete，并发布预留释放事件。
     *
     * @param command idListOrRequestKey
     * @throws BusinessException 未命中时
     */
    public void removeUndispensedDeviceDispense(RemoveUndispensedDeviceDispenseCommand command) {
        List<DeviceDispense> rows = this.resolveFlexibleKey(command.getIdListOrRequestKey());
        // 业务分支处理。
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_NOT_FOUND, "未命中发放记录");
        }
        for (DeviceDispense dispense : rows) {
            dispense.logicalDelete();
        }
        deviceDispenseRepository.saveAll(rows);
        domainEventPublisher.publish(new DeviceDispenseReserveReleasedEvent(rows.get(0).getId(), command.getIdListOrRequestKey(), command.getOperatorId()));
    }

    /**
     * 未发放批量停止：写入未执行原因并逐条发布停止事件。
     *
     * @param command 须含 idList 与 notPerformedReasonEnum
     * @throws BusinessException 参数非法或记录缺失时
     */
    public void stopUndispensedDeviceDispense(StopUndispensedDeviceDispenseCommand command) {
        // 业务分支处理。
        if (command.getIdList() == null || command.getIdList().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "idList 不能为空");
        }
        // 业务分支处理。
        if (command.getNotPerformedReasonEnum() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "notPerformedReasonEnum 不能为空");
        }
        List<DeviceDispense> rows = deviceDispenseRepository.findByIdIn(command.getIdList());
        // 业务分支处理。
        if (rows.size() != command.getIdList().size()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_NOT_FOUND, "部分发放记录不存在");
        }
        for (DeviceDispense dispense : rows) {
            dispense.toStopped(command.getNotPerformedReasonEnum());
        }
        deviceDispenseRepository.saveAll(rows);
        for (DeviceDispense dispense : rows) {
            domainEventPublisher.publish(new DeviceDispenseStoppedEvent(
                dispense.getId(), dispense.getBusNo(), dispense.getDeviceReqId(), command.getNotPerformedReasonEnum(), command.getOperatorId()));
        }
    }

    /**
     * 批量挂起发放行。
     *
     * @param command 须含 idList 且全部存在
     * @throws BusinessException 部分记录不存在时
     */
    public void holdDeviceDispense(HoldDeviceDispenseCommand command) {
        // 业务分支处理。
        if (command.getIdList() == null || command.getIdList().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "idList 不能为空");
        }
        List<DeviceDispense> rows = deviceDispenseRepository.findByIdIn(command.getIdList());
        // 业务分支处理。
        if (rows.size() != command.getIdList().size()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_NOT_FOUND, "部分发放记录不存在");
        }
        for (DeviceDispense dispense : rows) {
            dispense.toOnHold(command.getReason());
        }
        deviceDispenseRepository.saveAll(rows);
    }

    /**
     * 已发放撤回：完成态转取消并发布取消事件。
     *
     * @param command 须含 id 与原因
     * @throws BusinessException 参数非法或状态不允许时
     */
    public void recallDispensedDeviceDispense(RecallDispensedDeviceDispenseCommand command) {
        // 业务分支处理。
        if (command.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "id 不能为空");
        }
        DeviceDispense dispense = this.mustGetById(command.getId());
        dispense.completedToCancelled(command.getReason());
        deviceDispenseRepository.save(dispense);
        domainEventPublisher.publish(new DeviceDispenseCancelledEvent(dispense.getId(), dispense.getBusNo(), command.getOperatorId()));
    }

    /**
     * 分次发放追加子单：子 busNo 为 {@code parentBusNo.stepNo}，父单累计部分完成，子单直接完成并发布发放事件。
     *
     * @param command 批次内一条，须含 parentBusNo、stepNo、数量及追溯信息
     * @return 子单主键
     * @throws BusinessException 父单不存在、分次号冲突或不可追加时
     */
    public Long appendCompletedDeviceDispenseStep(AppendCompletedDeviceDispenseStepCommand command) {
        List<AppendCompletedDeviceDispenseStepCommand.AppendCompletedDeviceDispenseStepItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "一次仅允许追加一条分次记录");
        }
        AppendCompletedDeviceDispenseStepCommand.AppendCompletedDeviceDispenseStepItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getParentBusNo() == null || item.getParentBusNo().isBlank() || item.getStepNo() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "parentBusNo 与 stepNo 不能为空");
        }
        // 业务分支处理。
        if (item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "quantity 必须大于 0");
        }
        DeviceDispense parent = deviceDispenseRepository.findByBusNo(item.getParentBusNo().trim()).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_NOT_FOUND, "父发放单不存在"));
        String childBusNo = item.getParentBusNo().trim() + "." + item.getStepNo();
        parent.assertStepAppendable(childBusNo);
        // 业务分支处理。
        if (deviceDispenseRepository.findByBusNo(childBusNo).isPresent()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_RULE_CONFLICT, "分次业务号已存在");
        }

        DeviceDispense child = this.shallowCopyForNew(parent);
        child.setBusNo(childBusNo);
        child.setQuantity(item.getQuantity());
        child.setLotNumber(item.getLotNumber());
        child.setTraceNo(item.getTraceNo());
        LocalDateTime now = LocalDateTime.now();
        child.initCompleted(command.getOperatorId(), item.getQuantity(), now);

        DeviceDispense saved = deviceDispenseRepository.save(child);
        parent.partCompletedAccumulate();
        deviceDispenseRepository.save(parent);

        domainEventPublisher.publish(new DeviceDispensedEvent(
            saved.getId(), saved.getBusNo(), item.getQuantity(), saved.getLotNumber(), saved.getTraceNo(), command.getOperatorId()));
        return saved.getId();
    }

    /**
     * 批量将已完成记录回退为准备态（纠错/冲正入口，由调用方约束业务许可）。
     *
     * @param command 须含非空 idList 且全部存在
     * @throws BusinessException 部分 id 不存在时
     */
    public void revertCompletedBatchToPreparation(RevertCompletedBatchToPreparationCommand command) {
        // 业务分支处理。
        if (command.getIdList() == null || command.getIdList().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "idList 不能为空");
        }
        List<DeviceDispense> rows = deviceDispenseRepository.findByIdIn(command.getIdList());
        // 业务分支处理。
        if (rows.size() != command.getIdList().size()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_NOT_FOUND, "部分发放记录不存在");
        }
        for (DeviceDispense dispense : rows) {
            dispense.completedToPreparation();
        }
        deviceDispenseRepository.saveAll(rows);
    }

    /**
     * 批量将已配药记录回退为待发（护士取消执行对称入口，由调用方约束业务许可）。
     *
     * @param command 须含非空 idList 且全部存在
     * @throws BusinessException 部分 id 不存在或状态不允许回退时
     */
    public void revertPreparedBatchToPreparation(RevertPreparedBatchToPreparationCommand command) {
        if (command.getIdList() == null || command.getIdList().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "idList 不能为空");
        }
        List<DeviceDispense> rows = deviceDispenseRepository.findByIdIn(command.getIdList());
        if (rows.size() != command.getIdList().size()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_NOT_FOUND, "部分发放记录不存在");
        }
        for (DeviceDispense dispense : rows) {
            dispense.preparedToPreparation();
        }
        deviceDispenseRepository.saveAll(rows);
    }

    /**
     * 按主键加载发放记录，不存在则抛出业务异常。
     *
     * @param id 发放记录主键
     * @return 发放实体
     * @throws BusinessException 发放记录不存在时
     */
    private DeviceDispense mustGetById(Long id) {
        return deviceDispenseRepository.findById(id).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_NOT_FOUND, "耗材发放不存在"));
    }

    /**
     * 取同设备请求下任一条既有发放记录作为复制模板；无则抛出业务异常。
     *
     * <p>用于 generateDeviceDispenseFromExecution 和 generateIssuedDeviceDispenseFromExecution 路径，
     * 复制既有记录的上下文（患者、请求、数量单位等）。</p>
     *
     * @param deviceReqId 设备请求标识
     * @return 同请求下的既有发放记录
     * @throws BusinessException 无既有记录时
     */
    private DeviceDispense mustGetTemplateForDeviceReq(Long deviceReqId) {
        // Pre：查询同设备请求下的既有发放记录。
        List<DeviceDispense> siblings = deviceDispenseRepository.findByDeviceReqIdIn(List.of(deviceReqId));
        if (siblings.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_NOT_FOUND, "缺少同请求发放上下文，请先生成草稿或待发记录");
        }
        return siblings.get(0);
    }

    /**
     * 浅拷贝源发放单上与患者、请求、数量单位等相关的字段到新实体（不含主键与 busNo）。
     *
     * <p>用于退耗、分次追加等场景，复制源单业务上下文生成新记录。</p>
     *
     * @param src 源发放实体
     * @return 新发放实体（未设置主键和 busNo）
     */
    private DeviceDispense shallowCopyForNew(DeviceDispense src) {
        // Asm：复制源单业务上下文字段到新实体。
        DeviceDispense t = new DeviceDispense();
        t.setDeviceReqId(src.getDeviceReqId());
        t.setBasedOnTable(src.getBasedOnTable());
        t.setBasedOnId(src.getBasedOnId());
        t.setNotPerformedReasonEnum(src.getNotPerformedReasonEnum());
        t.setDispenseCategoryEnum(src.getDispenseCategoryEnum());
        t.setQuantity(src.getQuantity());
        t.setUnitCode(src.getUnitCode());
        t.setLotNumber(src.getLotNumber());
        t.setDeviceDefId(src.getDeviceDefId());
        t.setPatientId(src.getPatientId());
        t.setEncounterId(src.getEncounterId());
        t.setPerformerId(src.getPerformerId());
        t.setLocationId(src.getLocationId());
        t.setPartTypeCode(src.getPartTypeCode());
        t.setDispenseQuantity(src.getDispenseQuantity());
        t.setPreparerId(src.getPreparerId());
        t.setPrepareTime(src.getPrepareTime());
        t.setDispenseTime(src.getDispenseTime());
        t.setLimitTime(src.getLimitTime());
        t.setPlannedDispenseTime(src.getPlannedDispenseTime());
        t.setDesLocationId(src.getDesLocationId());
        t.setRecPractitionerId(src.getRecPractitionerId());
        t.setSupportInfo(src.getSupportInfo());
        t.setUsageInstruction(src.getUsageInstruction());
        t.setDispenseFrequencyCode(src.getDispenseFrequencyCode());
        t.setTraceNo(src.getTraceNo());
        t.setProcedureId(src.getProcedureId());
        return t;
    }

    /**
     * 将逗号分隔数字串解析为 Long 列表（忽略空段）。
     *
     * @param raw 逗号分隔的 ID 字符串
     * @return ID 列表
     */
    private List<Long> parseCommaLongIds(String raw) {
        String[] parts = raw.split(",");
        List<Long> ids = new ArrayList<>();
        // Loop：遍历各段，忽略空段并解析为 Long。
        for (String p : parts) {
            String s = p.trim();
            // 业务分支处理：空段跳过。
            if (s.isEmpty()) {
                continue;
            }
            ids.add(Long.parseLong(s));
        }
        return ids;
    }

    /**
     * 灵活定位：逗号 id 列表 → 按 id 查；单数字 → 先 id 再 deviceReqId；否则按 supportInfo 查。
     *
     * <p>支持三种定位方式：</p>
     * <ul>
     *   <li>逗号分隔的 ID 列表：按主键批量查询</li>
     *   <li>单个数字：先按主键查，未命中再按 deviceReqId 查</li>
     *   <li>其他字符串：按 supportInfo 模糊查询</li>
     * </ul>
     *
     * @param key 定位键
     * @return 发放记录列表
     * @throws BusinessException 定位键为空时
     */
    private List<DeviceDispense> resolveFlexibleKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "定位键不能为空");
        }
        String t = key.trim();
        // 业务分支处理：逗号分隔 ID 列表 → 按主键查询。
        if (COMMA_LONG_IDS.matcher(t).matches()) {
            List<Long> ids = this.parseCommaLongIds(t);
            // 业务分支处理：解析结果为空时返回空列表。
            if (ids.isEmpty()) {
                return List.of();
            }
            return deviceDispenseRepository.findByIdIn(ids);
        }
        // 业务分支处理：单个数字 → 先按主键查，未命中再按 deviceReqId 查。
        if (SINGLE_LONG.matcher(t).matches()) {
            long n = Long.parseLong(t);
            Optional<DeviceDispense> byId = deviceDispenseRepository.findById(n);
            // 业务分支处理：主键命中则返回，否则尝试 deviceReqId。
            if (byId.isPresent()) {
                return List.of(byId.get());
            }
            return deviceDispenseRepository.findByDeviceReqIdIn(List.of(n));
        }
        // 默认按 supportInfo 查询。
        return deviceDispenseRepository.findBySupportInfo(t);
    }

    /**
     * 汇总目标解析：逗号 id 列表则按 id；否则按 supportInfo。
     *
     * @param raw 输入字符串（逗号 ID 列表或 supportInfo）
     * @return 发放记录列表
     * @throws BusinessException 输入为空时
     */
    private List<DeviceDispense> resolveSummarizedTargets(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "idListOrSummaryKey 不能为空");
        }
        String t = raw.trim();
        // 业务分支处理：逗号分隔 ID 列表 → 按主键批量查询。
        if (COMMA_LONG_IDS.matcher(t).matches()) {
            List<Long> ids = this.parseCommaLongIds(t);
            return deviceDispenseRepository.findByIdIn(ids);
        }
        // 默认按 supportInfo 查询。
        return deviceDispenseRepository.findBySupportInfo(t);
    }

    /**
     * 解析 {@code batchKey|id1,id2,...} 或纯 id 列表为批次键与 id 列表。
     *
     * <p>支持两种格式：</p>
     * <ul>
     *   <li>batchKey|id1,id2,...：带批次键的 ID 列表</li>
     *   <li>id1,id2,...：纯 ID 列表（批次键为 null）</li>
     * </ul>
     *
     * @param raw 输入字符串
     * @return 解析结果（批次键 + ID 列表）
     * @throws BusinessException 格式非法或 ID 列表为空时
     */
    private SummaryCompletedParse parseSummaryCompleted(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "summaryKeyOrIdList 不能为空");
        }
        String t = raw.trim();
        int pipe = t.indexOf('|');
        String batchKey;
        String idPart;
        // 业务分支处理：存在分隔符时解析批次键和 ID 段。
        if (pipe >= 0) {
            batchKey = t.substring(0, pipe).trim();
            idPart = t.substring(pipe + 1).trim();
        } else {
            batchKey = null;
            idPart = t;
        }
        if (!COMMA_LONG_IDS.matcher(idPart).matches()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "summaryKeyOrIdList 的 id 段格式无效");
        }
        List<Long> ids = this.parseCommaLongIds(idPart);
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "未解析到发放标识");
        }
        return new SummaryCompletedParse(batchKey, ids);
    }

    private record SummaryCompletedParse(String batchKey, List<Long> idList) {
    }
}
