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

package com.openhis.health.domain.administration.specimendefinition.repository;

import com.openhis.health.domain.administration.specimendefinition.entity.SpecimenDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 样本定义仓储（{@code adm_specimen_definition}）。
 *
 * <p>仅 JPA 接口；动态条件查询在 {@link com.openhis.health.domain.administration.specimendefinition.service.SpecimenDefinitionQueryService} 以 {@code Specification} 收敛。</p>
 */
public interface SpecimenDefinitionRepository extends JpaRepository<SpecimenDefinition, Long>, JpaSpecificationExecutor<SpecimenDefinition> {

    /**
     * 按主键集合查询。
     *
     * @param ids 主键集合
     * @return 命中记录
     */
    List<SpecimenDefinition> findByIdIn(Collection<Long> ids);

    /**
     * 判断业务编码是否已存在（BR-SPDEF-001；依赖库层 {@code uk_adm_specimen_definition_bus_no} 保障并发一致性）。
     *
     * @param busNo 业务编码
     * @return 是否存在
     */
    boolean existsByBusNo(String busNo);

    /**
     * 判断业务编码是否被其它记录占用。
     *
     * @param busNo 业务编码
     * @param id 排除的主键
     * @return 是否存在冲突
     */
    boolean existsByBusNoAndIdNot(String busNo, Long id);

    /**
     * 按业务编码精确查询。
     *
     * @param busNo 业务编码
     * @return 样本定义
     */
    Optional<SpecimenDefinition> findByBusNo(String busNo);
}
