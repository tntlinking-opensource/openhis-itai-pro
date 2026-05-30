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

package com.openhis.health.domain.administration.healthcareservice.repository;

import com.openhis.health.domain.administration.healthcareservice.entity.HealthcareService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * 医疗服务聚合根仓储。
 * <p>
 * 持久化与查询「医疗服务」主数据，支撑类型、状态筛选及按主键加载。
 */
public interface HealthcareServiceRepository extends JpaRepository<HealthcareService, Long>, JpaSpecificationExecutor<HealthcareService> {
    /** 按主键查询医疗服务。 */
    Optional<HealthcareService> findById(Long id);

    /** 按状态枚举查询全部医疗服务（如草稿、已发布、退役等）。 */
    List<HealthcareService> findAllByStatusEnum(String statusEnum);
}
