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

package com.openhis.health.domain.clinical.procedure.service;

import com.alibaba.fastjson2.JSON;
import com.openhis.health.domain.clinical.procedure.command.*;
import com.openhis.health.domain.clinical.procedure.entity.Procedure;
import com.openhis.health.domain.clinical.procedure.entity.ProcedurePerformer;
import com.openhis.health.domain.clinical.procedure.repository.ProcedurePerformerRepository;
import com.openhis.health.domain.clinical.procedure.repository.ProcedureRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.events.clinical.procedure.*;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * 执行记录写侧服务。
 */
@Service
@RequiredArgsConstructor
public class ProcedureDomainService {
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
        }
        Procedure entity = new Procedure();
        entity.setRequestId(cmd.getRequestId());
        entity.setRequestTable(cmd.getRequestTable());
        entity.setEncounterId(cmd.getEncounterId());
        entity.setPatientId(cmd.getPatientId());
        entity.setCategoryEnum(cmd.getCategoryEnum());
        entity.setOccurrenceTime(cmd.getOccurrenceTime());
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
     */
    public void startProcedureExecution(StartProcedureExecutionCommand cmd) {
        Procedure entity = this.loadProcedure(cmd.getProcedureId());
        entity.startExecution();
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
     * 完成执行记录。
     *
     * @param cmd 完成命令
     */
    public void completeProcedureRecord(CompleteProcedureRecordCommand cmd) {
        Procedure entity = this.loadProcedure(cmd.getProcedureId());
        entity.setResultText(cmd.getResultText());
        entity.setReportJson(cmd.getReportJson());
        // Assert：BR-PRO-004 完成动作必须具备结果语义（结果/报告至少一项）。
        entity.complete();
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
     */
    public void cancelProcedureRecord(CancelProcedureRecordCommand cmd) {
        Procedure entity = this.loadProcedure(cmd.getProcedureId());
        entity.setStatusReasonText(cmd.getStatusReasonText());
        entity.setRefundId(cmd.getRefundId());
        // Assert：BR-PRO-005 取消动作必须可追溯（状态原因与链路标识）。
        entity.cancel();
        procedureRepository.save(entity);
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
        }
        Procedure entity = new Procedure();
        entity.setRequestId(cmd.getRequestId());
        entity.setRequestTable(cmd.getRequestTable());
        entity.setEncounterId(cmd.getEncounterId());
        entity.setPatientId(cmd.getPatientId());
        entity.setCategoryEnum(cmd.getCategoryEnum());
        entity.setOccurrenceTime(cmd.getOccurrenceTime());
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
     */
    private Procedure loadProcedure(Long procedureId) {
        return procedureRepository.findById(procedureId).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.PROCEDURE_NOT_FOUND, "执行记录不存在"));
    }
}
