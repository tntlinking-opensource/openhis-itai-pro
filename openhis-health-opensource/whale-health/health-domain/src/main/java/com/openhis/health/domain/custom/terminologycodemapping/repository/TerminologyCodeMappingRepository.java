/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.terminologycodemapping.repository;

import com.openhis.health.domain.custom.terminologycodemapping.entity.TerminologyCodeMapping;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 术语编码映射仓储。
 */
public interface TerminologyCodeMappingRepository extends JpaRepository<TerminologyCodeMapping, Long> {

    /**
     * 按主键加载映射行（含已软删，供框架默认对齐）。
     *
     * @param id 映射主键
     * @return 映射实体
     */
    Optional<TerminologyCodeMapping> findById(Long id);

    /**
     * BR-软删唯一性语义分析表。
     *
     * <p>说明：此处用于“源术语 + 目标术语”的有效映射幂等/唯一性判断。
     * 当前仓储按“仅对 activeFlag=true 且 isDeleted=false 的记录判冲突”处理；
     * 若文档要求“历史停用记录亦占用唯一键”或“含已删占用”，需调整条件并标记 DOC-CONFLICT。</p>
     *
     * <pre>
     * | BR 编号 | 原文关键词 | 含已逻辑删除记录？ | 结论 Repository 方法签名 | DOC-CONFLICT 留痕 |
     * | BR-TCM-001 | active 映射唯一/幂等 | 模糊（默认按有效映射） | existsBySourceItemTableAndSourceItemNoAndTargetItemNoAndActiveFlagTrue | DOC-CONFLICT: 唯一性范围待文档确认 |
     * </pre>
     *
     * @param sourceItemTable 源术语所在表名
     * @param sourceItemNo 源术语编码/编号
     * @param targetItemNo 目标术语编码/编号
     * @return true 表示有效映射已存在
     */
    boolean existsBySourceItemTableAndSourceItemNoAndTargetItemNoAndActiveFlagTrue(
        String sourceItemTable,
        String sourceItemNo,
        String targetItemNo
    );

    /**
     * 按源术语查全部有效映射（正向对照列表）。
     *
     * @param sourceItemTable 源术语表名
     * @param sourceItemNo 源术语编码
     * @return 映射列表
     */
    List<TerminologyCodeMapping> findAllBySourceItemTableAndSourceItemNoAndActiveFlagTrue(
        String sourceItemTable,
        String sourceItemNo
    );

    /**
     * 按目标术语反查有效映射（反向对照）。
     *
     * @param sourceItemTable 源术语表名（限定域）
     * @param targetItemNo 目标术语编码
     * @return 映射列表
     */
    List<TerminologyCodeMapping> findAllBySourceItemTableAndTargetItemNoAndActiveFlagTrue(
        String sourceItemTable,
        String targetItemNo
    );

    /**
     * 源编码模糊分页查询（维护页检索）。
     *
     * @param sourceItemTable 源术语表名
     * @param sourceItemNo 源编码关键字
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<TerminologyCodeMapping> findAllBySourceItemTableAndSourceItemNoContaining(
        String sourceItemTable,
        String sourceItemNo,
        Pageable pageable
    );
}
