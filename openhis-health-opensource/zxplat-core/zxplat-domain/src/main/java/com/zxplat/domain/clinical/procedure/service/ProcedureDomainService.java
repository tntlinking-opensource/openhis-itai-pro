/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.procedure.service;

import com.alibaba.fastjson2.JSON;
import com.zxplat.domain.clinical.procedure.command.*;
import com.zxplat.domain.clinical.procedure.entity.Procedure;
import com.zxplat.domain.clinical.procedure.entity.ProcedurePerformer;
import com.zxplat.domain.clinical.procedure.repository.ProcedurePerformerRepository;
import com.zxplat.domain.clinical.procedure.repository.ProcedureRepository;
import com.zxplat.domain.share.constants.ClinicalProcedureConstants;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.InpatientNurseStationConstants;
import com.zxplat.domain.share.enums.EventStatus;
import com.zxplat.domain.share.events.clinical.procedure.*;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.domain.event.IDomainEventPublisher;
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
        // 执行科室：护士/医技均写登录工作科室；分侧按组织 class_enum（历史空值兼容护士侧）
        entity.setOrgId(cmd.getOrgId());
        // 手术/麻醉执行横表：手术时间与 occurrence_time 分离；麻醉起止仅麻醉弹窗
        entity.setProcedureTime(cmd.getProcedureTime());
        entity.setAnesthesiaStartTime(cmd.getAnesthesiaStartTime());
        entity.setAnesthesiaEndTime(cmd.getAnesthesiaEndTime());
        entity.setIncisionTypeCode(cmd.getIncisionTypeCode());
        entity.setSendLabFlag(cmd.getSendLabFlag());
        entity.setSpecimen(cmd.getSpecimen());
        entity.setNote(cmd.getNote());
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
        // 前置：仅准备中/执行中可挂入执行人（DM-CLN-004 §6.1.3）
        procedure.assertPerformerAppendable();
        for (AppendProcedurePerformerCommand.ProcedurePerformerItemCommand item : cmd.resolveItems()) {
            ProcedurePerformer performer;
            // 业务分支：无子表 id 则新增执行人行
            if (item.getId() == null) {
                performer = new ProcedurePerformer();
                performer.setProcedureId(procedure.getId());
            } else {
                // 业务分支：有子表 id 则更新同 procedure 下已有执行人行
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
     * 完成执行记录；当 {@code operatorId} 非空时写入 {@code recorder_id}；
     * {@code recorded_time} 优先取命令入参，否则仅在实体尚未写入时回落当前时刻（DM-CLN-004 §6.1.4）。
     *
     * @param cmd 完成命令；{@code operatorId} 为 {@code adm_practitioner.id}，供工作量统计闭区间筛选；
     *            {@code recordedTime} 为护士认定实际执行时刻（可空）
     */
    public void completeProcedureRecord(CompleteProcedureRecordCommand cmd) {
        Procedure entity = this.loadProcedure(cmd.getProcedureId());
        entity.setResultText(cmd.getResultText());
        entity.setReportJson(cmd.getReportJson());
        // 完成时写入记录人与记录时间，供 AS-RPT-002 recorded_time 闭区间与列表展示
        if (cmd.getOperatorId() != null) {
            entity.setRecorderId(cmd.getOperatorId());
        }
        // 住院执行等调用方可显式传入 recordedTime（actualTime）；未传则保持实体已有值，再回落 now
        if (cmd.getRecordedTime() != null) {
            entity.setRecordedTime(cmd.getRecordedTime());
        } else if (entity.getRecordedTime() == null) {
            entity.setRecordedTime(LocalDateTime.now());
        }
        // 手术执行横表：完成时可补实填字段（非空覆盖）
        this.applySurgeryExecutionFieldsOnComplete(entity, cmd);
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
     * <p>当 {@code operatorId} 非空时写入 {@code recorder_id}（审计/追溯字段）；
     * 列表终态槽位操作人展示（BR-IP-EXE-031）取 {@code cln_procedure_performer} 主执行人，
     * <strong>不以</strong> {@code recorder_id} 回落。{@code recorded_time} 为空时补写当前时刻。</p>
     *
     * @param cmd 取消命令
     * @throws BusinessException 执行记录不存在或状态不允许取消时
     */
    public void cancelProcedureRecord(CancelProcedureRecordCommand cmd) {
        // 加载/组装：加载执行记录并写入取消原因
        Procedure entity = this.loadProcedure(cmd.getProcedureId());
        entity.setStatusReasonText(cmd.getStatusReasonText());
        entity.setRefundId(cmd.getRefundId());
        // 取消操作人写入 recorder（审计）；展示姓名走执行人子表 PRIMARY
        if (cmd.getOperatorId() != null) {
            entity.setRecorderId(cmd.getOperatorId());
        }
        if (entity.getRecordedTime() == null) {
            entity.setRecordedTime(LocalDateTime.now());
        }
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
     * <p>编排同构于执行/取消：{@code create(PREPARATION)→start(IN_PROGRESS)→挂主执行人→STOPPED}，
     * 主执行人取 {@code operatorId}（须在终态前写入，DM-CLN-004 {@code assertPerformerAppendable}）。</p>
     *
     * @param cmd 不执行命令
     * @return 执行记录主键
     */
    public Long skipProcedureRecord(SkipProcedureRecordCommand cmd) {
        // 前置校验：默认同 request 仅一条；多计划点须显式放开并校验 occurrence 槽位
        if (!Boolean.TRUE.equals(cmd.getSkipDuplicateRequestAnchorCheck())) {
            List<Procedure> existed = procedureRepository.findByRequestIdAndRequestTable(cmd.getRequestId(), cmd.getRequestTable());
            // 未放开重复门禁且已有执行记录：拒绝新建不执行行
            if (!existed.isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.PROCEDURE_RULE_CONFLICT, "同请求已存在执行记录");
            }
        } else if (cmd.getOccurrenceTime() != null) {
            // 多计划点：该 occurrence 若已占终态槽则拒绝
            this.assertOccurrenceSlotAvailable(cmd.getRequestId(), cmd.getRequestTable(), cmd.getOccurrenceTime());
        }
        // 前置校验：操作人必填（兼 recorder 与 PRIMARY 主执行人）
        if (cmd.getOperatorId() == null) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_INVALID_ARGUMENT, "不执行操作人不能为空");
        }
        // 加载/组装：新建 PREPARATION 行并绑定请求/就诊/计划点
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
        // 持久化：先落 PREPARATION，供 start / 挂执行人
        Procedure saved = procedureRepository.save(entity);
        // 状态迁移：进入 IN_PROGRESS（与执行/取消同构，终态前可挂人）
        saved.startExecution();
        procedureRepository.save(saved);
        // 聚合内影响：主执行人须在 STOPPED 前写入 cln_procedure_performer
        AppendProcedurePerformerCommand appendCmd = new AppendProcedurePerformerCommand();
        appendCmd.setProcedureId(saved.getId());
        appendCmd.setOperatorId(cmd.getOperatorId());
        AppendProcedurePerformerCommand.ProcedurePerformerItemCommand item =
            new AppendProcedurePerformerCommand.ProcedurePerformerItemCommand();
        item.setPractitionerId(cmd.getOperatorId());
        item.setFunctionCode(ClinicalProcedureConstants.PROCEDURE_PERFORMER_FUNCTION_PRIMARY);
        item.setStartTime(cmd.getRecordedTime());
        appendCmd.setItem(item);
        this.appendProcedurePerformer(appendCmd);
        // 状态迁移：收口 STOPPED
        Procedure toSkip = this.loadProcedure(saved.getId());
        toSkip.skip();
        // 持久化：终态落库后发事件
        Procedure finalRow = procedureRepository.save(toSkip);
        // 发布事件：计划点不执行已登记
        ProcedureRecordSkippedEvent event = new ProcedureRecordSkippedEvent(
            finalRow.getId(), finalRow.getRequestId(), finalRow.getRequestTable(), finalRow.getStatusEnum());
        event.setOccurrenceTime(finalRow.getOccurrenceTime());
        event.setRecordedTime(finalRow.getRecordedTime());
        domainEventPublisher.publish(event);
        return finalRow.getId();
    }

    /**
     * 修正已完成执行记录的客户可见时刻（仅 {@code recorded_time}，DM-CLN-004 §6.1.7）。
     *
     * <p>不改 {@code occurrence_time}；不发布额外领域事件（展示轴修正，非状态迁移）。</p>
     *
     * @param cmd 执行记录主键与新 recorded_time
     * @throws BusinessException 记录不存在、非 COMPLETED 或时刻为空
     */
    public void amendProcedureRecordedTime(AmendProcedureRecordedTimeCommand cmd) {
        // 前置校验：命令与主键必填
        if (cmd == null || cmd.getProcedureId() == null) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_INVALID_ARGUMENT, "执行记录主键不能为空");
        }
        // 加载/组装：加载 COMPLETED 行并仅改 recorded
        Procedure entity = this.loadProcedure(cmd.getProcedureId());
        entity.amendRecordedTime(cmd.getRecordedTime());
        // 持久化：展示时刻落库；occurrence 保持不变
        procedureRepository.save(entity);
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
     *
     * @param requestId      来源请求主键
     * @param requestTable   来源请求表名
     * @param occurrenceTime 计划执行时刻
     * @throws BusinessException 该计划点已执行或已登记不执行时
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

    /**
     * 完成执行时按「非空覆盖」写入手术横表实填字段。
     *
     * @param entity 执行记录
     * @param cmd    完成命令
     */
    private void applySurgeryExecutionFieldsOnComplete(Procedure entity, CompleteProcedureRecordCommand cmd) {
        // 手术时间非空则覆盖（与 occurrence_time 分离）
        if (cmd.getProcedureTime() != null) {
            entity.setProcedureTime(cmd.getProcedureTime());
        }
        // 麻醉开始非空则覆盖
        if (cmd.getAnesthesiaStartTime() != null) {
            entity.setAnesthesiaStartTime(cmd.getAnesthesiaStartTime());
        }
        // 麻醉结束非空则覆盖
        if (cmd.getAnesthesiaEndTime() != null) {
            entity.setAnesthesiaEndTime(cmd.getAnesthesiaEndTime());
        }
        // 切口类别非空则覆盖
        if (cmd.getIncisionTypeCode() != null) {
            entity.setIncisionTypeCode(cmd.getIncisionTypeCode());
        }
        // 是否送检非空则覆盖
        if (cmd.getSendLabFlag() != null) {
            entity.setSendLabFlag(cmd.getSendLabFlag());
        }
        // 样本非空则覆盖（与备注分列）
        if (cmd.getSpecimen() != null) {
            entity.setSpecimen(cmd.getSpecimen());
        }
        // 备注非空则覆盖（与样本分列）
        if (cmd.getNote() != null) {
            entity.setNote(cmd.getNote());
        }
    }
}
