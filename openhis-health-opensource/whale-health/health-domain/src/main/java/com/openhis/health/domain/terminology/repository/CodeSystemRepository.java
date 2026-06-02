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

package com.openhis.health.domain.terminology.repository;

import com.openhis.health.domain.share.enums.CodeSystemStatus;
import com.openhis.health.domain.terminology.entity.CodeSystem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 编码系统Repository
 *
 */
@Repository
public interface CodeSystemRepository
    extends JpaRepository<CodeSystem, Long>,
            JpaSpecificationExecutor<CodeSystem> {

    /**
     * 根据名称查找编码系统
     */
    Optional<CodeSystem> findByName(String name);

    /**
     * 根据名称和版本查找编码系统
     */
    Optional<CodeSystem> findByNameAndVersion(String name, String version);

    /**
     * 根据名称和状态查找编码系统（获取最新版本）
     */
    Optional<CodeSystem> findByNameAndStatus(String name, String status);

    /**
     * 检查编码系统是否存在
     */
    boolean existsByName(String name);

    /**
     * 根据状态查找所有编码系统
     */
    List<CodeSystem> findByStatus(String status);

    /**
     * 根据租户ID查找编码系统
     */
    List<CodeSystem> findByTenantId(Long tenantId);

}
