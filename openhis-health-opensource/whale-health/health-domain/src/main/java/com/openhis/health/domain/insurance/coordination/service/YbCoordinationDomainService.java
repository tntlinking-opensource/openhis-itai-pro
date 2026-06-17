/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.insurance.coordination.service;

import com.openhis.health.domain.insurance.coordination.command.AppendYbCoordinationStepCommand;
import com.openhis.health.domain.insurance.coordination.command.CreateYbCoordinationCommand;
import com.openhis.health.domain.insurance.coordination.command.TransitionYbCoordinationCommand;
import com.openhis.health.domain.insurance.coordination.entity.YbCoordination;
import com.openhis.health.domain.insurance.coordination.entity.YbCoordinationStep;
import com.openhis.health.domain.insurance.coordination.repository.YbCoordinationRepository;
import com.openhis.health.domain.insurance.coordination.repository.YbCoordinationStepRepository;
import com.openhis.health.domain.share.constants.InsuranceCoordinationConstants;
import com.openhis.health.domain.share.enums.YbCoordinationBizType;
import com.openhis.health.domain.share.enums.YbCoordinationPriority;
import com.openhis.health.domain.share.enums.YbCoordinationState;
import com.openhis.health.domain.share.events.insurance.YbCoordinationCreatedEvent;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 医保协同单写侧领域服务（对齐 06-协同单与 Worker §2）。
 *
 * <p>不变式：同一 payment + account + bizType 在结算类业务下不可并存多条非终态协同单；
 * 状态迁移须通过 {@link YbCoordination#assertTransitionTo(YbCoordinationState)}；
 * 所有写操作遵循「先落库后发事件」（B.14）。</p>
 */
@Service
@RequiredArgsConstructor
public class YbCoordinationDomainService {

    /** 终态集合：已完成、已取消、已补偿的单据不再参与并发冲突检测。 */
    private static final List<String> TERMINAL_STATES = Arrays.asList(
        YbCoordinationState.COMPLETED.getCode(),
        YbCoordinationState.CANCELLED.getCode(),
        YbCoordinationState.COMPENSATED.getCode());

    private final YbCoordinationRepository coordinationRepository;
    private final YbCoordinationStepRepository stepRepository;
    private final IDomainEventPublisher domainEventPublisher;

    /**
     * 创建医保协同单，初始状态为 {@link YbCoordinationState#CREATED}。
     *
     * @param cmd 创建命令，须含 bizType、contractId、encounterId 与 msgid
     * @return 持久化后的协同单聚合根
     * @throws BusinessException INS_003 结算类业务存在进行中协同单；INS_012 必填缺失
     */
    public YbCoordination create(CreateYbCoordinationCommand cmd) {
        // 前置校验：创建命令必填维度不完整则拒绝，避免落库后 Worker 无法调度
        if (cmd.getBizType() == null || cmd.getContractId() == null || cmd.getEncounterId() == null) {
            throw new BusinessException(InsuranceCoordinationConstants.INS_012, "协同单创建参数不完整");
        }
        // 前置校验：结算类业务且携带 paymentId 时，禁止同一账户下并存非终态协同单（INS_003）
        if (cmd.getPaymentId() != null
            && (YbCoordinationBizType.IP_SETTLE.equals(cmd.getBizType())
                || YbCoordinationBizType.OP_SETTLE.equals(cmd.getBizType())
                || YbCoordinationBizType.OP_SETTLE_REVERSE.equals(cmd.getBizType()))) {
            boolean conflict = coordinationRepository.existsByPaymentIdAndAccountIdAndBizTypeAndStateNotInAndIsDeletedFalse(
                cmd.getPaymentId(),
                cmd.getAccountId(),
                cmd.getBizType().getCode(),
                TERMINAL_STATES);
            if (conflict) {
                throw new BusinessException(InsuranceCoordinationConstants.INS_003, "存在进行中的医保协同单");
            }
        }
        // 加载/组装聚合：从命令映射主档字段，初始状态 CREATED、重试计数归零
        YbCoordination row = new YbCoordination();
        row.setBizType(cmd.getBizType().getCode());
        String schemeCode;
        if (cmd.getSchemeCode() != null) {
            schemeCode = cmd.getSchemeCode().getCode();
        } else {
            schemeCode = null;
        }
        row.setSchemeCode(schemeCode);
        row.setState(YbCoordinationState.CREATED.getCode());
        row.setBizId(cmd.getBizId());
        row.setAccountId(cmd.getAccountId());
        row.setContractId(cmd.getContractId());
        row.setParentId(cmd.getParentId());
        row.setPaymentId(cmd.getPaymentId());
        row.setPaymentNo(cmd.getPaymentNo());
        row.setEncounterId(cmd.getEncounterId());
        row.setPatientId(cmd.getPatientId());
        row.setMsgid(cmd.getMsgid());
        String priorityCode;
        if (cmd.getPriority() != null) {
            priorityCode = cmd.getPriority().getCode();
        } else {
            priorityCode = YbCoordinationPriority.P0.getCode();
        }
        row.setPriority(priorityCode);
        row.setBlocking(cmd.isBlocking());
        row.setRequestSnapshot(cmd.getRequestSnapshot());
        row.setRetryCount(0);
        row.setManualTicket(Boolean.FALSE);
        row.setOperatorId(cmd.getOperatorId());
        row.assertPersistable();
        // 持久化：插入主档后获得主键，供事件载荷与 Worker 轮询使用
        row = coordinationRepository.save(row);
        // 发布事件：save 完成后投递创建事件，触发异步调度
        domainEventPublisher.publish(new YbCoordinationCreatedEvent(
            row.getId(), row.getPaymentId(), row.getEncounterId(), row.getBizType()));
        return row;
    }

    /**
     * 迁移协同单状态并可选更新平台回执、错误信息与重试窗口。
     *
     * @param cmd 含目标状态与可选回执字段；coordinationId 须存在且未软删
     * @return 更新后的协同单
     * @throws BusinessException INS_012 记录不存在或状态迁移非法
     */
    public YbCoordination transition(TransitionYbCoordinationCommand cmd) {
        // 加载/组装聚合：按主键装载未删记录
        YbCoordination row = this.requireById(cmd.getCoordinationId());
        // 状态迁移：聚合内校验状态机后再写入目标状态
        row.applyState(cmd.getTargetState());
        // 可选回执字段：仅命令非空时覆盖，避免 Worker 重试时清空已有平台结果
        if (cmd.getErrorCode() != null) {
            row.setErrorCode(cmd.getErrorCode());
        }
        if (cmd.getErrorMsg() != null) {
            row.setErrorMsg(cmd.getErrorMsg());
        }
        if (cmd.getPlatformSetlId() != null) {
            row.setPlatformSetlId(cmd.getPlatformSetlId());
        }
        if (cmd.getResponseSnapshot() != null) {
            row.setResponseSnapshot(cmd.getResponseSnapshot());
        }
        if (cmd.getNextRetryAt() != null) {
            row.setNextRetryAt(cmd.getNextRetryAt());
        }
        if (cmd.getRetryCount() != null) {
            row.setRetryCount(cmd.getRetryCount());
        }
        // 持久化：乐观锁 version 由 JPA 维护
        return coordinationRepository.save(row);
    }

    /**
     * 追加协同单步骤行，记录单次 infno 调用快照。
     *
     * @param cmd 步骤命令，须含 coordinationId、stepOrder 与 infno
     * @return 持久化后的步骤行
     */
    public YbCoordinationStep appendStep(AppendYbCoordinationStepCommand cmd) {
        // 加载/组装聚合：新建步骤行，关联父协同单
        YbCoordinationStep step = new YbCoordinationStep();
        step.setCoordinationId(cmd.getCoordinationId());
        step.setStepOrder(cmd.getStepOrder());
        step.setInfno(cmd.getInfno());
        step.setState(cmd.getState());
        step.setMsgid(cmd.getMsgid());
        step.setRequestSnapshot(cmd.getRequestSnapshot());
        step.setResponseSnapshot(cmd.getResponseSnapshot());
        step.setErrorCode(cmd.getErrorCode());
        step.setErrorMsg(cmd.getErrorMsg());
        // 持久化：步骤链按 stepOrder 升序回放
        return stepRepository.save(step);
    }

    /**
     * 按主键加载未软删协同单，不存在时抛业务异常。
     *
     * @param id 协同单主键
     * @return 协同单聚合根
     * @throws BusinessException INS_012 记录不存在或已软删
     */
    public YbCoordination requireById(Long id) {
        return coordinationRepository.findByIdAndIsDeletedFalse(id)
            .orElseThrow(() -> new BusinessException(InsuranceCoordinationConstants.INS_012, "协同单不存在"));
    }
}
