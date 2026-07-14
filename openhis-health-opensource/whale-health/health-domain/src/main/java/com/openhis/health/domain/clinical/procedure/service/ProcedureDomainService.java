/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.procedure.service;

import com.alibaba.fastjson2.JSON;
import com.openhis.health.domain.clinical.procedure.command.*;
import com.openhis.health.domain.clinical.procedure.entity.Procedure;
import com.openhis.health.domain.clinical.procedure.entity.ProcedurePerformer;
import com.openhis.health.domain.clinical.procedure.repository.ProcedurePerformerRepository;
import com.openhis.health.domain.clinical.procedure.repository.ProcedureRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.enums.EventStatus;
import com.openhis.health.domain.share.events.clinical.procedure.*;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/**
 * 执行记录（{@code cln_procedure}）聚合写侧领域服务。
 *
 * <p>职责：创建/启动/完成/取消/不执行及执行人轨迹维护；同 request 锚点幂等与 occurrence 占用校验（BR-PRO/BR-NUR）。</p>
 * <p>不变量：完成须具结果语义（BR-PRO-004）；持久化先于 Procedure 领域事件发布。</p>
 * <p>仅注入本聚合 {@link ProcedureRepository}、{@link ProcedurePerformerRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class ProcedureDomainService {
    private static final Set<String> OCCURRENCE_TERMINAL_STATUSES = Set.of(
        EventStatus.COMPLETED.getCode(),
        EventStatus.STOPPED.getCode());

    private final ProcedureRepository procedureRepository;
    private final ProcedurePerformerRepository performerRepository;
    private final IDomainEventPublisher domainEventPublisher;

    /**
     * 创建执行记录。
     *
     * @param cmd 创建命令
     * @return 执行记录主键
     */
    public Long createProcedureRecord(CreateProcedureRecordCommand cmd) {
        // BR-PRO-001：默认同 requestId+requestTable 幂等保护；门诊护士站多次执行登记通过 skipDuplicateRequestAnchorCheck 显式放开（BR-NUR-005）。
        if (!Boolean.TRUE.equals(cmd.getSkipDuplicateRequestAnchorCheck())) {
            List<Procedure> existed = procedureRepository.findByRequestIdAndRequestTable(cmd.getRequestId(), cmd.getRequestTable());
            if (!existed.isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.PROCEDURE_RULE_CONFLICT, "同请求已存在执行记录");
            }
        } else if (cmd.getOccurrenceTime() != null
            && !Boolean.TRUE.equals(cmd.getSkipOccurrenceSlotCheck())) {
            this.assertOccurrenceSlotAvailable(cmd.getRequestId(), cmd.getRequestTable(), cmd.getOccurrenceTime());
        }
        Procedure entity = new Procedure();
        entity.setRequestId(cmd.getRequestId());
        entity.setRequestTable(cmd.getRequestTable());
        entity.setEncounterId(cmd.getEncounterId());
        entity.setPatientId(cmd.getPatientId());
        entity.setCategoryEnum(cmd.getCategoryEnum());
        entity.setOccurrenceTime(cmd.getOccurrenceTime());
        entity.setOccurrenceSlotActive(true);
        // BR-PRO-002: 执行记录必须绑定就诊与患者。
        entity.assertRequestBound();
        entity.initPreparationStatus();
        Procedure saved = procedureRepository.save(entity);
        ProcedureRecordCreatedEvent event = new ProcedureRecordCreatedEvent(
            saved.getId(), saved.getRequestId(), saved.getRequestTable(), saved.getStatusEnum());
        event.setCategoryEnum(saved.getCategoryEnum());
        event.setPatientId(saved.getPatientId());
        event.setEncounterId(saved.getEncounterId());
        event.setOrgId(saved.getOrgId());
        event.setLocationId(saved.getLocationId());
        event.setRecorderId(saved.getRecorderId());
        event.setRecordedTime(saved.getRecordedTime());
        event.setOrderRequesterId(cmd.getOperatorId());
        event.setOrderAuthoredTime(saved.getCreatedAt());
        domainEventPublisher.publish(event);
        return saved.getId();
    }

    /**
     * 启动执行。
     *
     * @param cmd 启动命令
     * @throws BusinessException 执行记录不存在或状态不允许启动时
     */
    public void startProcedureExecution(StartProcedureExecutionCommand cmd) {
        // 加载/组装：加载执行记录并迁移至执行中
        Procedure entity = this.loadProcedure(cmd.getProcedureId());
        entity.startExecution();
        // 持久化
        procedureRepository.save(entity);
    }

    /**
     * 维护执行人轨迹。
     *
     * @param cmd 执行人维护命令
     */
    public void appendProcedurePerformer(AppendProcedurePerformerCommand cmd) {
        Procedure procedure = this.loadProcedure(cmd.getProcedureId());
        for (AppendProcedurePerformerCommand.ProcedurePerformerItemCommand item : cmd.resolveItems()) {
            ProcedurePerformer performer;
            // 业务分支：新增路径。
            if (item.getId() == null) {
                performer = new ProcedurePerformer();
                performer.setProcedureId(procedure.getId());
            } else {
                performer = performerRepository.findByIdAndProcedureId(item.getId(), procedure.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCodeConstants.PROCEDURE_NOT_FOUND, "执行人记录不存在"));
            }
            performer.setPractitionerId(item.getPractitionerId());
            performer.setFunctionCode(item.getFunctionCode());
            performer.setStartTime(item.getStartTime());
            performer.setEndTime(item.getEndTime());
            performer.setOrgFromId(item.getOrgFromId());
            ProcedurePerformer saved = performerRepository.save(performer);
            ProcedurePerformerEditedEvent event = new ProcedurePerformerEditedEvent(
                procedure.getId(), saved.getId(), saved.getFunctionCode(), saved.getStartTime(), saved.getEndTime());
            event.setPerformerListJson(JSON.toJSONString(performerRepository.findAllByProcedureId(procedure.getId())));
            domainEventPublisher.publish(event);
        }
    }

    /**
     * 完成执行记录；当 {@code operatorId} 非空时写入 {@code recorder_id} 与 {@code recorded_time}（DM-CLN-004 §6.1.4）。
     *
     * @param cmd 完成命令；{@code operatorId} 为 {@code adm_practitioner.id}，供工作量统计闭区间筛选
     */
    public void completeProcedureRecord(CompleteProcedureRecordCommand cmd) {
        Procedure entity = this.loadProcedure(cmd.getProcedureId());
        entity.setResultText(cmd.getResultText());
        entity.setReportJson(cmd.getReportJson());
        // 完成时写入记录人与记录时间，供 AS-RPT-002 recorded_time 闭区间与列表展示
        if (cmd.getOperatorId() != null) {
            entity.setRecorderId(cmd.getOperatorId());
        }
        if (entity.getRecordedTime() == null) {
            entity.setRecordedTime(LocalDateTime.now());
        }
        // Assert：BR-PRO-004 完成动作必须具备结果语义（结果/报告至少一项）。
        entity.complete();
        // 持久化：状态 COMPLETED 与 recorder/recorded 同事务提交
        procedureRepository.save(entity);
        ProcedureRecordCompletedEvent event = new ProcedureRecordCompletedEvent(
            entity.getId(), entity.getStatusEnum(), entity.getResultText(), entity.getReportJson());
        event.setOccurrenceTime(entity.getOccurrenceTime());
        event.setReportJson(entity.getReportJson());
        domainEventPublisher.publish(event);
    }

    /**
     * 取消执行记录。
     *
     * @param cmd 取消命令
     * @throws BusinessException 执行记录不存在或状态不允许取消时
     */
    public void cancelProcedureRecord(CancelProcedureRecordCommand cmd) {
        // 加载/组装：加载执行记录并写入取消原因
        Procedure entity = this.loadProcedure(cmd.getProcedureId());
        entity.setStatusReasonText(cmd.getStatusReasonText());
        entity.setRefundId(cmd.getRefundId());
        // Assert：BR-PRO-005 取消动作必须可追溯（状态原因与链路标识）。
        entity.cancel();
        // 持久化
        procedureRepository.save(entity);
        this.releaseOccurrenceSlotIfRefunded(cmd.getRefundId());
        // 发布事件：执行记录已取消
        domainEventPublisher.publish(new ProcedureRecordCancelledEvent(entity.getId(), entity.getStatusEnum(), entity.getStatusReasonText(), entity.getRefundId()));
    }

    /**
     * 登记计划点不执行（STOPPED；不停嘱、不写发药/账单）。
     *
     * @param cmd 不执行命令
     * @return 执行记录主键
     */
    public Long skipProcedureRecord(SkipProcedureRecordCommand cmd) {
        if (!Boolean.TRUE.equals(cmd.getSkipDuplicateRequestAnchorCheck())) {
            List<Procedure> existed = procedureRepository.findByRequestIdAndRequestTable(cmd.getRequestId(), cmd.getRequestTable());
            if (!existed.isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.PROCEDURE_RULE_CONFLICT, "同请求已存在执行记录");
            }
        } else if (cmd.getOccurrenceTime() != null) {
            this.assertOccurrenceSlotAvailable(cmd.getRequestId(), cmd.getRequestTable(), cmd.getOccurrenceTime());
        }
        Procedure entity = new Procedure();
        entity.setRequestId(cmd.getRequestId());
        entity.setRequestTable(cmd.getRequestTable());
        entity.setEncounterId(cmd.getEncounterId());
        entity.setPatientId(cmd.getPatientId());
        entity.setCategoryEnum(cmd.getCategoryEnum());
        entity.setOccurrenceTime(cmd.getOccurrenceTime());
        entity.setOccurrenceSlotActive(true);
        entity.setRecordedTime(cmd.getRecordedTime());
        entity.setRecorderId(cmd.getOperatorId());
        entity.setStatusReasonText(cmd.getStatusReasonText());
        entity.assertRequestBound();
        entity.initPreparationStatus();
        entity.skip();
        Procedure saved = procedureRepository.save(entity);
        ProcedureRecordSkippedEvent event = new ProcedureRecordSkippedEvent(
            saved.getId(), saved.getRequestId(), saved.getRequestTable(), saved.getStatusEnum());
        event.setOccurrenceTime(saved.getOccurrenceTime());
        event.setRecordedTime(saved.getRecordedTime());
        domainEventPublisher.publish(event);
        return saved.getId();
    }

    /**
     * 加载执行记录实体。
     *
     * @param procedureId 执行记录主键
     * @return 执行记录聚合根
     * @throws BusinessException 记录不存在时
     */
    private Procedure loadProcedure(Long procedureId) {
        return procedureRepository.findById(procedureId).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.PROCEDURE_NOT_FOUND, "执行记录不存在"));
    }

    /**
     * 同锚点 occurrence 仍被终态占用时拒绝（GAP-EXE-002，EX-IP-EXE-301）。
     */
    private void assertOccurrenceSlotAvailable(Long requestId, String requestTable, java.time.LocalDateTime occurrenceTime) {
        boolean occupied = procedureRepository.existsByRequestIdAndRequestTableAndOccurrenceTimeAndOccurrenceSlotActiveTrueAndStatusEnumInAndIsDeletedFalse(
            requestId, requestTable, occurrenceTime, OCCURRENCE_TERMINAL_STATUSES);
        if (occupied) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_EXE_301,
                "该计划点已执行或已登记不执行，请刷新后重试");
        }
    }

    /**
     * CANCEL 对冲后释放被指向 COMPLETED/STOPPED 行的计划点占用，允许同 occurrence 再执行。
     */
    private void releaseOccurrenceSlotIfRefunded(Long refundProcedureId) {
        if (refundProcedureId == null) {
            return;
        }
        procedureRepository.findById(refundProcedureId).ifPresent(refunded -> {
            if (!OCCURRENCE_TERMINAL_STATUSES.contains(refunded.getStatusEnum())) {
                return;
            }
            if (!Boolean.TRUE.equals(refunded.getOccurrenceSlotActive())) {
                return;
            }
            refunded.setOccurrenceSlotActive(false);
            procedureRepository.save(refunded);
        });
    }
}
