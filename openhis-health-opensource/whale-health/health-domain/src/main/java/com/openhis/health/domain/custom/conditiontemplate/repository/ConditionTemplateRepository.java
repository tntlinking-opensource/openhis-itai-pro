/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.conditiontemplate.repository;

import com.openhis.health.domain.custom.conditiontemplate.entity.ConditionTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 诊断常用模板仓储。
 */
public interface ConditionTemplateRepository extends JpaRepository<ConditionTemplate, Long> {

    /**
     * 按主键加载模板行（含已软删，供框架默认对齐）。
     *
     * @param id 模板主键
     * @return 模板实体
     */
    Optional<ConditionTemplate> findById(Long id);

    /**
     * BR-软删唯一性语义分析表。
     *
     * <p>说明：此处用于“同一对象 + 绑定类型 + 诊断定义”维度的幂等/唯一性判断。
     * 由于文档原文未在此处内联，默认按“有效记录内唯一”处理：仅对 {@code isDeleted=false} 判冲突。</p>
     *
     * <pre>
     * | BR 编号 | 原文关键词 | 含已逻辑删除记录？ | 结论 Repository 方法签名 | DOC-CONFLICT 留痕 |
     * | BR-CTP-001 | 绑定唯一/幂等 | 模糊（默认按有效记录） | existsByObjectIdAndDefinitionIdAndBindingEnum | DOC-CONFLICT: 唯一性是否含已删待文档确认 |
     * </pre>
     *
     * @param objectId 归属对象标识
     * @param definitionId 诊断定义主键
     * @param bindingEnum 绑定类型枚举值
     * @return true 表示冲突存在
     */
    boolean existsByObjectIdAndDefinitionIdAndBindingEnum(Long objectId, Long definitionId, String bindingEnum);

    /**
     * 按归属对象与绑定类型列出模板行（维护页排序展示）。
     *
     * @param objectId 归属对象标识
     * @param bindingEnum 绑定类型枚举值
     * @return 模板行列表
     */
    List<ConditionTemplate> findAllByObjectIdAndBindingEnumOrderBySortWeightAscIdAsc(Long objectId, String bindingEnum);

    /**
     * 按主键集合批量加载（应用层 enrich 用；调用方须过滤软删）。
     *
     * @param ids 主键集合
     * @return 模板行列表
     */
    List<ConditionTemplate> findAllByIdIn(Collection<Long> ids);

    /**
     * 按绑定类型分页列出模板（管理端）。
     *
     * @param bindingEnum 绑定类型
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<ConditionTemplate> findAllByBindingEnum(String bindingEnum, Pageable pageable);
}
