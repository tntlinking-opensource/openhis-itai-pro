/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.insurance.coordination.service;

import com.openhis.health.domain.insurance.coordination.entity.YbCoordination;
import com.openhis.health.domain.insurance.coordination.entity.YbCoordinationStep;
import com.openhis.health.domain.insurance.coordination.repository.YbCoordinationRepository;
import com.openhis.health.domain.insurance.coordination.repository.YbCoordinationStepRepository;
import com.openhis.health.domain.share.enums.YbCoordinationState;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 医保协同单读侧查询服务。
 *
 * <p>职责：按主键、paymentId 加载协同单与步骤链，并为 Worker 提供可调度批次查询；只读不写库。</p>
 */
@Service
@RequiredArgsConstructor
public class YbCoordinationQueryService {

    private final YbCoordinationRepository coordinationRepository;
    private final YbCoordinationStepRepository stepRepository;

    /**
     * 按主键加载协同单；id 为空或记录不存在/已软删时返回 null。
     *
     * @param id 协同单主键
     * @return 协同单或 null
     */
    public YbCoordination getById(Long id) {
        if (id == null) {
            return null;
        }
        return coordinationRepository.findByIdAndIsDeletedFalse(id).orElse(null);
    }

    /**
     * 按支付单主键倒序列出关联协同单（最新在前）。
     *
     * @param paymentId 支付单主键
     * @return 协同单列表；paymentId 为空时返回空列表
     */
    public List<YbCoordination> listByPaymentId(Long paymentId) {
        if (paymentId == null) {
            return List.of();
        }
        return coordinationRepository.findByPaymentIdAndIsDeletedFalseOrderByIdDesc(paymentId);
    }

    /**
     * 按协同单主键升序列出步骤链（多 infno 调用顺序）。
     *
     * @param coordinationId 协同单主键
     * @return 步骤列表；coordinationId 为空时返回空列表
     */
    public List<YbCoordinationStep> listSteps(Long coordinationId) {
        if (coordinationId == null) {
            return List.of();
        }
        return stepRepository.findByCoordinationIdAndIsDeletedFalseOrderByStepOrderAsc(coordinationId);
    }

    /**
     * 查询 Worker 可调度协同单批次：状态为 CREATED 或 LOCAL_PENDING，且到达重试窗口。
     *
     * @param batchSize 单批最大条数，由 Worker 配置传入
     * @return 按优先级与创建时间排序的可运行协同单列表
     */
    public List<YbCoordination> findRunnable(int batchSize) {
        List<String> states = Arrays.asList(
            YbCoordinationState.CREATED.getCode(),
            YbCoordinationState.LOCAL_PENDING.getCode());
        return coordinationRepository.findRunnable(states, LocalDateTime.now(), PageRequest.of(0, batchSize));
    }
}
