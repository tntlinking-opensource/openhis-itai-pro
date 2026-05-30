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

package com.openhis.health.domain.custom.servicelocationtemplate.repository;

import com.openhis.health.domain.custom.servicelocationtemplate.entity.ServiceLocationTemplate;
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

