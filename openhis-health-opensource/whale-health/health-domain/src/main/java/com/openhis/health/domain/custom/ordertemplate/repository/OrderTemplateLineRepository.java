/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.ordertemplate.repository;

import com.openhis.health.domain.custom.ordertemplate.entity.OrderTemplateLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 组套模板行仓储。
 */
public interface OrderTemplateLineRepository extends JpaRepository<OrderTemplateLine, Long> {

    /**
     * 按主键查询。
     * @param id 条件
     * @return 查询结果
     */
    Optional<OrderTemplateLine> findById(Long id);

    /**
     * 按条件查询列表。
     * @param orderTemplateId 条件
     * @return 查询结果
     */
    List<OrderTemplateLine> findAllByOrderTemplateIdOrderBySortOrderAscIdAsc(Long orderTemplateId);

    /**
     * 按条件查询列表。
     * @param orderTemplateId 条件
     * @return 查询结果
     */
    List<OrderTemplateLine> findAllByOrderTemplateId(Long orderTemplateId);

    List<OrderTemplateLine> findAllByOrderTemplateIdAndIsDeletedFalseOrderBySortOrderAscIdAsc(Long orderTemplateId);
}
