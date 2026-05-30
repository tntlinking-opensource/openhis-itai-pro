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

package com.openhis.health.domain.custom.ordertemplate.repository;

import com.openhis.health.domain.custom.ordertemplate.entity.OrderTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 医嘱组套模板头仓储。
 */
public interface OrderTemplateRepository extends JpaRepository<OrderTemplate, Long> {

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

    boolean existsByTemplateCodeAndObjectIdAndBindingEnumAndIdNot(String templateCode, Long objectId, String bindingEnum, Long id);

    Page<OrderTemplate> findAllByBindingEnumAndSceneEnumAndStatusEnumOrderBySortWeightAscIdAsc(
        String bindingEnum,
        com.openhis.health.domain.share.enums.OrderTemplateScene sceneEnum,
        String statusEnum,
        Pageable pageable
    );

    List<OrderTemplate> findAllByObjectIdAndBindingEnumAndStatusEnumOrderBySortWeightAscIdAsc(
        Long objectId,
        String bindingEnum,
        String statusEnum
    );

    List<OrderTemplate> findAllByObjectIdAndBindingEnumAndStatusEnumAndSceneEnumInOrderBySortWeightAscIdAsc(
        Long objectId,
        String bindingEnum,
        String statusEnum,
        Collection<com.openhis.health.domain.share.enums.OrderTemplateScene> sceneEnums
    );
}
