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

package com.openhis.health.domain.custom.supplier.repository;

import com.openhis.health.domain.custom.supplier.entity.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 供应商仓储。
 */
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Optional<Supplier> findById(Long id);

    /**
     * BR-软删唯一性语义分析表。
     *
     * <p>说明：供应商业务编码（busNo）通常用于业务识别；信用代码（creditCode）通常用于外部监管唯一标识。
     * 当前仓储按“有效记录内唯一”处理：仅对 {@code isDeleted=false} 判冲突；若文档要求“含已删仍占用”，需去掉软删过滤并标记 DOC-CONFLICT。</p>
     *
     * <pre>
     * | BR 编号 | 原文关键词 | 含已逻辑删除记录？ | 结论 Repository 方法签名 | DOC-CONFLICT 留痕 |
     * | BR-SUP-003 | busNo 唯一 | 模糊（默认按有效记录） | existsByBusNo | DOC-CONFLICT: 唯一性是否含已删待文档确认 |
     * | BR-SUP-004 | creditCode 唯一 | 模糊（默认按有效记录） | existsByCreditCode | DOC-CONFLICT: 唯一性是否含已删待文档确认 |
     * </pre>
     *
     * @param busNo 供应商业务编码
     * @return true 表示冲突存在
     */
    boolean existsByBusNo(String busNo);

    boolean existsByCreditCode(String creditCode);

    List<Supplier> findAllBy();

    List<Supplier> findAllByTypeEnumAndStatusEnum(String typeEnum, String statusEnum);

    Page<Supplier> findAllByNameContaining(String name, Pageable pageable);
}
