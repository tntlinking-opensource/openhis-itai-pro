/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.supplier.repository;

import com.openhis.health.domain.custom.supplier.entity.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 供应商仓储。
 */
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    /**
     * 按主键加载供应商（含已软删，供框架默认对齐）。
     *
     * @param id 供应商主键
     * @return 供应商实体
     */
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

    /**
     * 信用代码在有效记录内是否已占用（见类注释 BR-SUP-004）。
     *
     * @param creditCode 统一社会信用代码
     * @return {@code true} 表示冲突
     */
    boolean existsByCreditCode(String creditCode);

    /**
     * 列出全部有效供应商（主数据规模受控；下拉预热）。
     *
     * @return 供应商列表
     */
    List<Supplier> findAllBy();

    /**
     * 按供应商类型与状态筛选（采购/入库下拉）。
     *
     * @param typeEnum 类型持久化码
     * @param statusEnum 状态持久化码
     * @return 匹配的供应商列表
     */
    List<Supplier> findAllByTypeEnumAndStatusEnum(String typeEnum, String statusEnum);

    /**
     * 按名称模糊分页查询供应商。
     *
     * @param name 名称关键字
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<Supplier> findAllByNameContaining(String name, Pageable pageable);

    /**
     * 按主键集合批量加载（展示名解析、目录厂家回填）。
     *
     * @param ids 供应商标识集合
     * @return 命中的供应商实体
     */
    List<Supplier> findAllByIdIn(Collection<Long> ids);

    /**
     * 按精确名称与类型查找启用/有效主数据（导入文案匹配生产厂家）。
     *
     * @param name 名称（须与落库一致）
     * @param typeEnum 类型 code（生产厂家为 {@code 1}）
     * @return 命中实体；未命中为空
     */
    Optional<Supplier> findFirstByNameAndTypeEnum(String name, String typeEnum);
}
