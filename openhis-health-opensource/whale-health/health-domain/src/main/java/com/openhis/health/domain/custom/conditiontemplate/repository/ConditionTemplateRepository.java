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

    List<ConditionTemplate> findAllByObjectIdAndBindingEnumOrderBySortWeightAscIdAsc(Long objectId, String bindingEnum);

    List<ConditionTemplate> findAllByIdIn(Collection<Long> ids);

    Page<ConditionTemplate> findAllByBindingEnum(String bindingEnum, Pageable pageable);
}
