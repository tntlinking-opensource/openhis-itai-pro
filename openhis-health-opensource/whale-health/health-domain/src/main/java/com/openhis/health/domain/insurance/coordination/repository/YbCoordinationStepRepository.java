/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.insurance.coordination.repository;

import com.openhis.health.domain.insurance.coordination.entity.YbCoordinationStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 医保协同单步骤仓储（{@code yb_coordination_step}）。
 *
 * <p>步骤链按 {@code step_order} 升序回放单次 infno 调用；规模随协同单 infno 链长度线性增长，通常个位数。</p>
 */
public interface YbCoordinationStepRepository extends JpaRepository<YbCoordinationStep, Long> {

    /**
     * 按协同单主键升序列出未软删步骤（完整 infno 调用链）。
     *
     * @param coordinationId 协同单主键
     * @return 步骤列表
     */
    List<YbCoordinationStep> findByCoordinationIdAndIsDeletedFalseOrderByStepOrderAsc(Long coordinationId);

    /**
     * 取协同单最新一步（step_order 最大），用于续调下一 infno。
     *
     * @param coordinationId 协同单主键
     * @return 最新步骤或 empty
     */
    Optional<YbCoordinationStep> findFirstByCoordinationIdAndIsDeletedFalseOrderByStepOrderDesc(Long coordinationId);

    /**
     * 统计协同单下未软删步骤数，用于分配下一步 stepOrder。
     *
     * @param coordinationId 协同单主键
     * @return 步骤计数
     */
    int countByCoordinationIdAndIsDeletedFalse(Long coordinationId);
}
