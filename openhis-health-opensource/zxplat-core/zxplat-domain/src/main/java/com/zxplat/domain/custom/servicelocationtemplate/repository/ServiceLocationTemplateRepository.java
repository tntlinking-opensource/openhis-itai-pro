/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.servicelocationtemplate.repository;

import com.zxplat.domain.custom.servicelocationtemplate.entity.ServiceLocationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * 取药科室模板仓储。
 */
public interface ServiceLocationTemplateRepository extends JpaRepository<ServiceLocationTemplate, Long> {

    /**
     * 按开立科室查询未删除规则列表。
     *
     * @param organizationId 开立科室标识
     * @return 规则列表
     */
    List<ServiceLocationTemplate> findAllByOrganizationId(Long organizationId);

    /**
     * 按开立科室集合查询规则。
     *
     * @param organizationIds 开立科室标识集合
     * @return 规则列表
     */
    List<ServiceLocationTemplate> findAllByOrganizationIdIn(Collection<Long> organizationIds);

    /**
     * 查询全部未删除规则并按开立科室、优先级、主键排序（用于全局列表）。
     *
     * @return 规则列表
     */
    List<ServiceLocationTemplate> findAllByOrderByOrganizationIdAscPriorityAscIdAsc();
}
