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

package com.openhis.health.domain.custom.tracenumberrecord.repository;

import com.openhis.health.domain.custom.tracenumberrecord.entity.TraceNumberRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 追溯码记录仓储。
 */
public interface TraceNumberRecordRepository extends JpaRepository<TraceNumberRecord, Long> {

    /**
     * 按追溯码、批号与位置查询记录，按主键升序。
     *
     * @param traceNo 追溯码
     * @param lotNumber 批号
     * @param locationId 位置主键
     * @return 记录列表
     */
    List<TraceNumberRecord> findAllByTraceNoAndLotNumberAndLocationIdOrderByIdAsc(
        String traceNo,
        String lotNumber,
        Long locationId
    );

    /**
     * 按业务项、批号与位置查询记录。
     *
     * @param itemTable 业务表名
     * @param itemId 业务主键
     * @param lotNumber 批号
     * @param locationId 位置主键
     * @return 记录列表
     */
    List<TraceNumberRecord> findAllByItemTableAndItemIdAndLotNumberAndLocationId(
        String itemTable,
        Long itemId,
        String lotNumber,
        Long locationId
    );

    /**
     * 按业务表与批号模糊匹配分页查询。
     *
     * @param itemTable 业务表名
     * @param lotNumber 批号（包含匹配）
     * @param pageable 分页
     * @return 分页结果
     */
    Page<TraceNumberRecord> findAllByItemTableAndLotNumberContaining(
        String itemTable,
        String lotNumber,
        Pageable pageable
    );

    /**
     * 判断指定追溯码集合中是否存在已出库记录。
     * 用于配药前校验追溯码未重复出库（AS-OP-PHA-001 BR-OPP-003）。
     *
     * @param traceNos 追溯码集合
     * @param outboundStatusEnum 出库状态枚举值（与 TraceDirection.getCode() 对齐）
     * @return 若存在任一追溯码已出库则返回 true
     */
    boolean existsByTraceNoInAndStatusEnum(
        java.util.Collection<String> traceNos,
        String statusEnum
    );
}

