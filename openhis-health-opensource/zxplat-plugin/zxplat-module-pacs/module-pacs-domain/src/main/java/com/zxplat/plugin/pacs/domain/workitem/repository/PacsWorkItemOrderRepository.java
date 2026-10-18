/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.domain.workitem.repository;

import com.zxplat.plugin.pacs.domain.workitem.entity.PacsWorkItemOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 检查工单医嘱关联子实体仓储。
 */
public interface PacsWorkItemOrderRepository extends JpaRepository<PacsWorkItemOrder, Long> {

    Optional<PacsWorkItemOrder> findByWorkItemIdAndServiceRequestId(Long workItemId, Long serviceRequestId);

    List<PacsWorkItemOrder> findByWorkItemId(Long workItemId);

    Optional<PacsWorkItemOrder> findByServiceRequestId(Long serviceRequestId);

    boolean existsByServiceRequestId(Long serviceRequestId);

    /**
     * 是否存在引用该诊疗项目的医嘱挂接（底座已滤软删）。
     *
     * @param activityId 诊疗项目
     * @return 存在则为 true
     */
    boolean existsByActivityId(Long activityId);

    /**
     * 按工单主键批量查医嘱挂接（列表 orderCount 一次加载，禁 N+1）。
     *
     * @param workItemIds 工单主键集合
     * @return 挂接列表
     */
    List<PacsWorkItemOrder> findByWorkItemIdIn(Collection<Long> workItemIds);
}
