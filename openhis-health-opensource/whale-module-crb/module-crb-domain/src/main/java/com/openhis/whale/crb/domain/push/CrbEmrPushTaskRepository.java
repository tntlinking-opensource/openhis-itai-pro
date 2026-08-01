/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.push;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 推送任务队列仓储。
 */
public interface CrbEmrPushTaskRepository extends JpaRepository<CrbEmrPushTask, Long>,
        JpaSpecificationExecutor<CrbEmrPushTask> {

    /**
     * 按幂等键查找未删除任务。
     *
     * @param tenantId 租户
     * @param resource receive 段
     * @param businessKey 业务键
     * @param operation 操作类型 code
     * @return 已有任务
     */
    Optional<CrbEmrPushTask> findFirstByTenantIdAndResourceAndBusinessKeyAndOperationAndIsDeletedFalse(
            Long tenantId, String resource, String businessKey, String operation);

    /**
     * 扫描到期且待处理的任务（Worker 批消费）。
     *
     * @param tenantId 租户
     * @param status 状态 code
     * @param dueAtBefore 截止时刻上界
     * @return 任务列表
     */
    List<CrbEmrPushTask> findTop50ByTenantIdAndStatusAndDueAtLessThanEqualAndIsDeletedFalseOrderByDueAtAsc(
            Long tenantId, String status, LocalDateTime dueAtBefore);

    /**
     * 按主键与租户加载未删除任务。
     *
     * @param id 任务 id
     * @param tenantId 租户 id
     * @return 任务
     */
    Optional<CrbEmrPushTask> findByIdAndTenantIdAndIsDeletedFalse(Long id, Long tenantId);
}
