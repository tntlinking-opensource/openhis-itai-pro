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

package com.openhis.health.domain.administration.healthcareservice.service;

import com.openhis.health.domain.administration.healthcareservice.entity.HealthcareService;
import com.openhis.health.domain.administration.healthcareservice.repository.HealthcareServiceRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * 医疗服务（号源/服务项定义）读侧查询服务。
 *
 * <p>职责：为挂号、排班等场景提供启用中的医疗服务列表及按主键加载详情。</p>
 *
 * <p>边界：只读；列表过滤条件为 {@code statusEnum="1"}（启用），与持久化状态码约定一致。</p>
 */
@Service
@RequiredArgsConstructor
public class HealthcareServiceQueryService {
    private final HealthcareServiceRepository repository;

    /**
     * 列出挂号场景可选的启用医疗服务（维护台已发布且启用）。
     *
     * @return 医疗服务列表，可能为空
     */
    public List<HealthcareService> listServicesForRegistration() {
        // 仅返回启用行，停用服务不出现在挂号下拉中。
        return repository.findAllByStatusEnum("1");
    }

    /**
     * 按主键加载医疗服务详情。
     *
     * @param id 医疗服务主键
     * @return 实体；不存在时 {@code null}，由应用层决定是否提示
     */
    public HealthcareService loadHealthcareServiceById(Long id) {
        return repository.findById(id).orElse(null);
    }
}
