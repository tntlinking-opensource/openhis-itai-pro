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

package com.openhis.health.domain.workflow.supplyrequest.repository;

import com.openhis.health.domain.workflow.supplyrequest.entity.SupplyRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * 供应请求聚合仓储（表 {@code wkf_supply_request}）。
 *
 * <p>约束：禁止 {@code @Query}；过滤与软删条件由方法名派生查询下推到数据库（项目 JPA 规约）。</p>
 *
 * <p>方法参数中 {@code statusEnum} 等与实体列一致，须传入各业务枚举的 {@link com.openhis.health.domain.share.enums.EnumInterface#getCode()}
 * 持久化字符串（B.10.8.1），禁止传排序号。</p>
 */
public interface SupplyRequestRepository extends JpaRepository<SupplyRequest, Long>, JpaSpecificationExecutor<SupplyRequest> {

    /**
     * 按业务单号加载全部未物理删除行（同号多行，顺序由持久化实现决定）。
     *
     * @param busNo 业务单号 {@code bus_no}
     * @return 行列表；无数据时返回空列表
     */
    List<SupplyRequest> findByBusNo(String busNo);

    /**
     * 按多个业务单号批量加载。
     *
     * @param busNoList 业务单号集合；空集合行为由调用方规避
     * @return 命中行列表
     */
    List<SupplyRequest> findByBusNoIn(List<String> busNoList);

    /**
     * 按来源/原始业务单号加载行（如订货号、关联原单号）。
     *
     * @param originalBusNo {@code original_bus_no}
     * @return 行列表
     */
    List<SupplyRequest> findByOriginalBusNo(String originalBusNo);

    /**
     * 按汇总主键集合加载（汇总类供应场景）。
     *
     * @param summaryIdList {@code summary_id} 列表
     * @return 行列表
     */
    List<SupplyRequest> findBySummaryIdIn(List<Long> summaryIdList);

    /**
     * 按主键集合批量加载。
     *
     * @param idList {@code id} 列表
     * @return 行列表
     */
    List<SupplyRequest> findByIdIn(List<Long> idList);

    /**
     * 按主键查询单条（与 {@link JpaRepository} 语义一致；若需软删过滤须在实体或规约层体现）。
     *
     * @param id 主键
     * @return 可选单条
     */
    Optional<SupplyRequest> findById(Long id);

    /**
     * 判断指定业务单号下是否已存在指定审批状态行（用于同意幂等等）。
     *
     * @param busNo       业务单号
     * @param statusEnum  {@code status_enum} 持久化值，如 {@link com.openhis.health.domain.share.enums.SupplyStatus#AGREE#getCode()}
     * @return 存在任一行则为 {@code true}
     */
    boolean existsByBusNoAndStatusEnum(String busNo, String statusEnum);
}
