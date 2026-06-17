/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.insurance.coordination.repository;

import com.openhis.health.domain.insurance.coordination.entity.YbCoordination;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 医保协同单仓储（{@code yb_coordination}）。
 *
 * <p>写侧由 {@link com.openhis.health.domain.insurance.coordination.service.YbCoordinationDomainService} 编排；
 * Worker 轮询与结算并发检测依赖本接口自定义查询。</p>
 */
public interface YbCoordinationRepository extends JpaRepository<YbCoordination, Long> {

    /**
     * 按主键加载未软删协同单。
     *
     * @param id 协同单主键
     * @return 存在且未删时 non-empty
     */
    Optional<YbCoordination> findByIdAndIsDeletedFalse(Long id);

    /**
     * 按支付单主键倒序列出未软删协同单（对账/撤销场景按最新单据优先）。
     *
     * @param paymentId 支付单主键
     * @return 协同单列表，规模通常较小（同一 payment 并发协同单受 INS_003 约束）
     */
    List<YbCoordination> findByPaymentIdAndIsDeletedFalseOrderByIdDesc(Long paymentId);

    /**
     * 检测同一 payment + account + bizType 下是否存在非终态协同单（结算类并发门禁 INS_003）。
     *
     * @param paymentId 支付单主键
     * @param accountId 账户主键，可为 null
     * @param bizType 业务类型 code
     * @param terminalStates 终态 code 集合，命中则不计入冲突
     * @return true 表示存在进行中协同单
     */
    boolean existsByPaymentIdAndAccountIdAndBizTypeAndStateNotInAndIsDeletedFalse(
        Long paymentId,
        Long accountId,
        String bizType,
        Collection<String> terminalStates);

    /**
     * Worker 可调度批次：指定状态且到达重试窗口，按优先级与创建时间升序取前 N 条。
     *
     * @param states 可运行状态集合（通常 CREATED、LOCAL_PENDING）
     * @param now 当前时间，用于 nextRetryAt 窗口判断
     * @param pageable 分页窗口，batchSize 由调用方控制
     * @return 可运行协同单列表
     */
    @Query("""
        SELECT c FROM YbCoordination c
        WHERE c.isDeleted = false
          AND c.state IN :states
          AND (c.nextRetryAt IS NULL OR c.nextRetryAt <= :now)
        ORDER BY c.priority ASC, c.createdAt ASC
        """)
    List<YbCoordination> findRunnable(
        @Param("states") Collection<String> states,
        @Param("now") LocalDateTime now,
        org.springframework.data.domain.Pageable pageable);
}
