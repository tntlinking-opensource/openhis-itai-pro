/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.frequency.repository;

import com.zxplat.domain.custom.frequency.entity.Frequency;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 频次仓储。
 */
public interface FrequencyRepository extends JpaRepository<Frequency, Long> {

    /**
     * 按主键查询未删除记录。
     *
     * @param id 主键
     * @return 频次
     */
    Optional<Frequency> findById(Long id);

    /**
     * 查询编码冲突。
     *
     * <p>BR-软删唯一性语义分析表：</p>
     *
     * <pre>
     * | BR 编号 | 原文关键词 | 含已逻辑删除记录？ | 结论 Repository 方法签名 | DOC-CONFLICT 留痕 |
     * | BR-FRQ-001 | rateCode 唯一 | 模糊（默认按有效记录） | existsByRateCode | DOC-CONFLICT: 唯一性是否含已删待文档确认 |
     * </pre>
     *
     * @param rateCode 频次编码
     * @return 是否冲突
     */
    boolean existsByRateCode(String rateCode);

    /**
     * 按编码查询可用频次。
     *
     * @param rateCode 频次编码
     * @return 频次
     */
    Optional<Frequency> findByRateCode(String rateCode);

    /**
     * 按名称分页查询。
     *
     * @param name 名称
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<Frequency> findAllByNameContaining(String name, Pageable pageable);

    /**
     * 查询全部启用频次，按排序号与主键升序（下拉与码表预热）。
     *
     * @return 启用态频次列表
     */
    List<Frequency> findByActiveFlagTrueOrderBySortNoAscIdAsc();
}
