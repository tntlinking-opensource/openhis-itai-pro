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

package com.openhis.health.domain.administration.practitioner.repository;

import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.whale.core.infrastructure.transaction.SkipSoftDeleteFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Collection;
import java.util.List;
import java.util.Optional;


/**
 * 医务人员仓储。
 */
public interface PractitionerRepository extends JpaRepository<Practitioner, Long>, JpaSpecificationExecutor<Practitioner> {

    /**
     * 按主键查询。
     * @param id 条件
     * @return 查询结果
     */
    Optional<Practitioner> findById(Long id);

    /**
     * 判断是否存在满足条件的记录。
     * @param userId 条件
     * @return 查询结果
     */
    boolean existsByUserId(Long userId);

    /**
     * 按绑定用户查询（含已软删记录），创建前校验用户是否已绑定医务人员。
     */
    @SkipSoftDeleteFilter
    Optional<Practitioner> findFirstByUserIdOrderByIdAsc(Long userId);

    /**
     * 按绑定用户查询医务人员主档（未逻辑删除），用户与主档通常一对一。
     *
     * @param userId 系统用户主键
     * @return 从业人员实体
     */
    Optional<Practitioner> findFirstByUserIdAndIsDeletedFalseOrderByIdAsc(Long userId);

    /**
     * 按绑定用户主键批量查询未删除的医务人员（预交金流水操作员展示）。
     *
     * @param userIds 系统用户主键集合
     * @return 从业人员列表
     */
    List<Practitioner> findAllByUserIdInAndIsDeletedFalse(Collection<Long> userIds);

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
     * 按条件查询列表。
     * @param statusEnum 条件
     * @return 查询结果
     */
    List<Practitioner> findAllByStatusEnum(String statusEnum);

    /**
     * 按条件查询列表。
     * @param statusEnum 条件
     * @param orgId 条件
     * @return 查询结果
     */
    List<Practitioner> findAllByStatusEnumAndOrgId(String statusEnum, Long orgId);

    /**
     * 按条件查询列表。
     * @param pageable 条件
     * @return 查询结果
     */
    Page<Practitioner> findAllBy(Pageable pageable);
}

