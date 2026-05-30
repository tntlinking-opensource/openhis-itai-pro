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

package com.openhis.health.domain.diagnosis.bodystructure.repository;

import com.openhis.health.domain.diagnosis.bodystructure.entity.BodyStructure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 身体部位仓储。
 */
public interface BodyStructureRepository extends JpaRepository<BodyStructure, Long>, JpaSpecificationExecutor<BodyStructure> {

    /**
     * 按主键查询。
     * @param id 条件
     * @return 查询结果
     */
    Optional<BodyStructure> findById(Long id);

    /**
     * 按条件查询。
     * @param ids 条件
     * @return 查询结果
     */
    List<BodyStructure> findByIdIn(Collection<Long> ids);

    /**
     * 按业务号查询。
     * @param busNo 条件
     * @return 查询结果
     */
    boolean existsByBusNo(String busNo);

    /**
     * 按业务号查询。
     * @param busNo 条件
     * @param id 条件
     * @return 查询结果
     */
    boolean existsByBusNoAndIdNot(String busNo, Long id);

    /**
     * 按业务号查询。
     * @return 查询结果
     */
    List<BodyStructure> findAllByOrderByBusNoAsc();

}

