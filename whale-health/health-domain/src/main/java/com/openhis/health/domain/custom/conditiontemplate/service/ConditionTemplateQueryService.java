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

package com.openhis.health.domain.custom.conditiontemplate.service;

import com.openhis.health.domain.custom.conditiontemplate.entity.ConditionTemplate;
import com.openhis.health.domain.custom.conditiontemplate.queries.model.ConditionTemplateQueryModel;
import com.openhis.health.domain.custom.conditiontemplate.repository.ConditionTemplateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 诊断常用模板读侧查询服务。
 *
 * <p>职责：按绑定维度分页/列表加载模板，并将个人、科室、院级模板合并为去重后的诊断快捷项列表。</p>
 *
 * <p>边界：只读；合并时以诊断定义 id 为键，先个人后科室再院级，先出现的条目保留（覆盖策略）。</p>
 */
@Service
@RequiredArgsConstructor
public class ConditionTemplateQueryService {

    private final ConditionTemplateRepository repository;

    /**
     * 按绑定类型分页查询模板（如个人/科室/全院绑定枚举）。
     *
     * @param bindingEnum 绑定维度编码
     * @param pageable    分页参数
     * @return 分页查询模型
     */
    public Page<ConditionTemplateQueryModel> pageConditionTemplates(String bindingEnum, Pageable pageable) {
        Page<ConditionTemplate> page = repository.findAllByBindingEnum(bindingEnum, pageable);
        return page.map(this::toQueryModel);
    }

    /**
     * 列出某拥有者在指定绑定类型下的全部模板（按排序权重、id 升序）。
     *
     * @param objectId    拥有者主键（医生/科室/机构等，语义由 bindingEnum 解释）
     * @param bindingEnum 绑定维度编码
     * @return 模板列表
     */
    public List<ConditionTemplateQueryModel> listByOwnerAndType(Long objectId, String bindingEnum) {
        List<ConditionTemplate> rows = repository.findAllByObjectIdAndBindingEnumOrderBySortWeightAscIdAsc(objectId, bindingEnum);
        List<ConditionTemplateQueryModel> result = new ArrayList<>();
        for (ConditionTemplate row : rows) {
            result.add(toQueryModel(row));
        }
        return result;
    }

    /**
     * 合并个人、科室、院级模板为单一列表：同一诊断定义只保留一条，优先级为个人 &gt; 科室 &gt; 院级。
     *
     * @param personalObjectId   个人维度拥有者 id
     * @param departmentObjectId 科室维度拥有者 id
     * @param hospitalObjectId   院级维度拥有者 id
     * @return 合并后的模板列表（顺序为合并遍历顺序）
     */
    public List<ConditionTemplateQueryModel> listMergedTemplates(Long personalObjectId, Long departmentObjectId, Long hospitalObjectId) {
        Map<Long, ConditionTemplateQueryModel> byDefinition = new LinkedHashMap<>();
        mergeInto(byDefinition, listByOwnerAndType(personalObjectId, "1"));
        mergeInto(byDefinition, listByOwnerAndType(departmentObjectId, "2"));
        mergeInto(byDefinition, listByOwnerAndType(hospitalObjectId, "3"));
        return new ArrayList<>(byDefinition.values());
    }

    /**
     * 将 incoming 合入 map：仅当该诊断定义 id 尚未存在时放入，实现「高优先级来源已占坑则不再被低优先级覆盖」。
     *
     * @param byDefinition 诊断定义 id → 模板模型
     * @param incoming     待合并列表（已按优先级顺序调用）
     */
    private void mergeInto(Map<Long, ConditionTemplateQueryModel> byDefinition, List<ConditionTemplateQueryModel> incoming) {
        for (ConditionTemplateQueryModel item : incoming) {
            if (!byDefinition.containsKey(item.getDefinitionId())) {
                byDefinition.put(item.getDefinitionId(), item);
            }
        }
    }

    /**
     * 模板实体映射为读模型。
     *
     * @param row 持久化实体
     * @return 查询模型
     */
    private ConditionTemplateQueryModel toQueryModel(ConditionTemplate row) {
        ConditionTemplateQueryModel model = new ConditionTemplateQueryModel();
        model.setId(row.getId());
        model.setObjectId(row.getObjectId());
        model.setDefinitionId(row.getDefinitionId());
        model.setBindingEnum(row.getBindingEnum());
        model.setSortWeight(row.getSortWeight());
        model.setRemark(row.getRemark());
        return model;
    }
}
