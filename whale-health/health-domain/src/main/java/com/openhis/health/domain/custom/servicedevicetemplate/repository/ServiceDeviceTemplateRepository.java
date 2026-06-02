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

package com.openhis.health.domain.custom.servicedevicetemplate.repository;

import com.openhis.health.domain.custom.servicedevicetemplate.entity.ServiceDeviceTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 诊疗耗材绑定仓储。
 */
public interface ServiceDeviceTemplateRepository extends JpaRepository<ServiceDeviceTemplate, Long> {

    /**
     * 按范围、类型、业务项与状态查询绑定列表，按主键升序。
     *
     * @param rangeCode 范围编码
     * @param typeCode 类型编码
     * @param itemTable 业务表名
     * @param itemId 业务主键
     * @param statusEnum 状态枚举编码
     * @return 绑定列表
     */
    List<ServiceDeviceTemplate> findAllByRangeCodeAndTypeCodeAndItemTableAndItemIdAndStatusEnumOrderByIdAsc(
        String rangeCode,
        String typeCode,
        String itemTable,
        String itemId,
        String statusEnum
    );

    /**
     * 按范围、类型与业务项分页查询，按主键降序。
     *
     * @param rangeCode 范围编码
     * @param typeCode 类型编码
     * @param itemTable 业务表名
     * @param itemId 业务主键
     * @param pageable 分页
     * @return 分页结果
     */
    Page<ServiceDeviceTemplate> findAllByRangeCodeAndTypeCodeAndItemTableAndItemIdOrderByIdDesc(
        String rangeCode,
        String typeCode,
        String itemTable,
        String itemId,
        Pageable pageable
    );

    /**
     * 按范围、类型、业务项与状态分页查询，按主键降序。
     *
     * @param rangeCode 范围编码
     * @param typeCode 类型编码
     * @param itemTable 业务表名
     * @param itemId 业务主键
     * @param statusEnum 状态枚举编码
     * @param pageable 分页
     * @return 分页结果
     */
    Page<ServiceDeviceTemplate> findAllByRangeCodeAndTypeCodeAndItemTableAndItemIdAndStatusEnumOrderByIdDesc(
        String rangeCode,
        String typeCode,
        String itemTable,
        String itemId,
        String statusEnum,
        Pageable pageable
    );

    /**
     * 按范围与类型分页查询，按主键降序。
     *
     * @param rangeCode 范围编码
     * @param typeCode 类型编码
     * @param pageable 分页
     * @return 分页结果
     */
    Page<ServiceDeviceTemplate> findAllByRangeCodeAndTypeCodeOrderByIdDesc(
        String rangeCode,
        String typeCode,
        Pageable pageable
    );

    /**
     * 按范围、类型与状态分页查询，按主键降序。
     *
     * @param rangeCode 范围编码
     * @param typeCode 类型编码
     * @param statusEnum 状态枚举编码
     * @param pageable 分页
     * @return 分页结果
     */
    Page<ServiceDeviceTemplate> findAllByRangeCodeAndTypeCodeAndStatusEnumOrderByIdDesc(
        String rangeCode,
        String typeCode,
        String statusEnum,
        Pageable pageable
    );
}

