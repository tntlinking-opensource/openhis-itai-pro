/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.ordertemplate.repository;

import com.openhis.health.domain.custom.ordertemplate.entity.OrderTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 组套模板头仓储。
 */
public interface OrderTemplateRepository extends JpaRepository<OrderTemplate, Long>, JpaSpecificationExecutor<OrderTemplate> {

    /**
     * 按主键加载组套模板（含已软删，供框架默认 findById 对齐）。
     *
     * @param id 组套模板主键
     * @return 模板实体
     */
    Optional<OrderTemplate> findById(Long id);

    /**
     * BR-软删唯一性语义分析表。
     *
     * <p>说明：模板编码（templateCode）在“归属对象 + 绑定类型”范围内需保持唯一。
     * 当前按“有效记录内唯一”处理：仅对 {@code isDeleted=false} 判冲突；若文档要求“含已删仍占用”，需去掉软删过滤并标记 DOC-CONFLICT。</p>
     *
     * <pre>
     * | BR 编号 | 原文关键词 | 含已逻辑删除记录？ | 结论 Repository 方法签名 | DOC-CONFLICT 留痕 |
     * | BR-OTL-003 | templateCode 唯一 | 模糊（默认按有效记录） | existsByTemplateCodeAndObjectIdAndBindingEnum | DOC-CONFLICT: 唯一性是否含已删待文档确认 |
     * </pre>
     *
     * @param templateCode 模板编码
     * @param objectId 归属对象标识
     * @param bindingEnum 绑定类型枚举值
     * @return true 表示编码冲突存在
     */
    boolean existsByTemplateCodeAndObjectIdAndBindingEnum(String templateCode, Long objectId, String bindingEnum);

    /**
     * 编辑场景：除当前模板外是否仍存在相同编码（有效记录内唯一）。
     *
     * @param templateCode 模板编码
     * @param objectId 归属对象标识
     * @param bindingEnum 绑定类型枚举值
     * @param id 当前模板主键
     * @return {@code true} 表示编码冲突
     */
    boolean existsByTemplateCodeAndObjectIdAndBindingEnumAndIdNot(String templateCode, Long objectId, String bindingEnum, Long id);

    /**
     * 按绑定类型、场景与状态分页列出模板（按 sort_weight、id 升序）。
     *
     * @param bindingEnum 绑定类型
     * @param sceneEnum 场景枚举
     * @param statusEnum 状态持久化码
     * @param pageable 分页参数
     * @return 模板分页结果
     */
    Page<OrderTemplate> findAllByBindingEnumAndSceneEnumAndStatusEnumOrderBySortWeightAscIdAsc(
        String bindingEnum,
        com.openhis.health.domain.share.enums.OrderTemplateScene sceneEnum,
        String statusEnum,
        Pageable pageable
    );

    /**
     * 按绑定类型、场景、状态与模板种类分页列出模板。
     *
     * @param bindingEnum 绑定类型
     * @param sceneEnum 场景枚举
     * @param statusEnum 状态持久化码
     * @param templateKindEnum 模板种类持久化码
     * @param pageable 分页参数
     * @return 模板分页结果
     */
    Page<OrderTemplate> findAllByBindingEnumAndSceneEnumAndStatusEnumAndTemplateKindEnumOrderBySortWeightAscIdAsc(
        String bindingEnum,
        com.openhis.health.domain.share.enums.OrderTemplateScene sceneEnum,
        String statusEnum,
        String templateKindEnum,
        Pageable pageable
    );

    /**
     * 按归属对象与绑定类型列出指定状态的模板（不分页，按 sort_weight 排序）。
     *
     * @param objectId 归属对象标识
     * @param bindingEnum 绑定类型
     * @param statusEnum 状态持久化码
     * @return 模板列表
     */
    List<OrderTemplate> findAllByObjectIdAndBindingEnumAndStatusEnumOrderBySortWeightAscIdAsc(
        Long objectId,
        String bindingEnum,
        String statusEnum
    );

    /**
     * 按归属对象、绑定类型、状态与场景集合列出模板。
     *
     * @param objectId 归属对象标识
     * @param bindingEnum 绑定类型
     * @param statusEnum 状态持久化码
     * @param sceneEnums 场景枚举集合
     * @return 模板列表
     */
    List<OrderTemplate> findAllByObjectIdAndBindingEnumAndStatusEnumAndSceneEnumInOrderBySortWeightAscIdAsc(
        Long objectId,
        String bindingEnum,
        String statusEnum,
        Collection<com.openhis.health.domain.share.enums.OrderTemplateScene> sceneEnums
    );

    /**
     * 按归属对象、绑定类型、状态、场景集合与模板种类列出模板。
     *
     * @param objectId 归属对象标识
     * @param bindingEnum 绑定类型
     * @param statusEnum 状态持久化码
     * @param sceneEnums 场景枚举集合
     * @param templateKindEnum 模板种类持久化码
     * @return 模板列表
     */
    List<OrderTemplate> findAllByObjectIdAndBindingEnumAndStatusEnumAndSceneEnumInAndTemplateKindEnumOrderBySortWeightAscIdAsc(
        Long objectId,
        String bindingEnum,
        String statusEnum,
        Collection<com.openhis.health.domain.share.enums.OrderTemplateScene> sceneEnums,
        String templateKindEnum
    );

    /**
     * 按绑定类型、场景、状态与模板来源分页列出模板。
     *
     * @param bindingEnum 绑定类型
     * @param sceneEnum 场景枚举
     * @param statusEnum 状态持久化码
     * @param templateSourceEnum 模板来源持久化码
     * @param pageable 分页参数
     * @return 模板分页结果
     */
    Page<OrderTemplate> findAllByBindingEnumAndSceneEnumAndStatusEnumAndTemplateSourceEnumOrderBySortWeightAscIdAsc(
        String bindingEnum,
        com.openhis.health.domain.share.enums.OrderTemplateScene sceneEnum,
        String statusEnum,
        String templateSourceEnum,
        Pageable pageable
    );

    /**
     * 按绑定类型、场景、状态、模板种类与来源分页列出模板。
     *
     * @param bindingEnum 绑定类型
     * @param sceneEnum 场景枚举
     * @param statusEnum 状态持久化码
     * @param templateKindEnum 模板种类持久化码
     * @param templateSourceEnum 模板来源持久化码
     * @param pageable 分页参数
     * @return 模板分页结果
     */
    Page<OrderTemplate> findAllByBindingEnumAndSceneEnumAndStatusEnumAndTemplateKindEnumAndTemplateSourceEnumOrderBySortWeightAscIdAsc(
        String bindingEnum,
        com.openhis.health.domain.share.enums.OrderTemplateScene sceneEnum,
        String statusEnum,
        String templateKindEnum,
        String templateSourceEnum,
        Pageable pageable
    );

    /**
     * 按归属对象、绑定类型、状态、场景集合、模板种类与来源列出模板（维护页下拉/复制源）。
     *
     * @param objectId 归属对象标识
     * @param bindingEnum 绑定类型
     * @param statusEnum 状态持久化码
     * @param sceneEnums 场景枚举集合
     * @param templateKindEnum 模板种类持久化码
     * @param templateSourceEnum 模板来源持久化码
     * @return 模板列表
     */
    List<OrderTemplate> findAllByObjectIdAndBindingEnumAndStatusEnumAndSceneEnumInAndTemplateKindEnumAndTemplateSourceEnumOrderBySortWeightAscIdAsc(
        Long objectId,
        String bindingEnum,
        String statusEnum,
        Collection<com.openhis.health.domain.share.enums.OrderTemplateScene> sceneEnums,
        String templateKindEnum,
        String templateSourceEnum
    );
}
