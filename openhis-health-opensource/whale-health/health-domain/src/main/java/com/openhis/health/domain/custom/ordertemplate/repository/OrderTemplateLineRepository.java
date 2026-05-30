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

package com.openhis.health.domain.custom.ordertemplate.repository;

import com.openhis.health.domain.custom.ordertemplate.entity.OrderTemplateLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 医嘱组套模板行仓储。
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
}
