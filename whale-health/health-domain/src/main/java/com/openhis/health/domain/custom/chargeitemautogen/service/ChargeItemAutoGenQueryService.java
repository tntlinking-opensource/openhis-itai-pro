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

package com.openhis.health.domain.custom.chargeitemautogen.service;


import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.health.domain.custom.chargeitemautogen.entity.ChargeItemAutoGen;
import com.openhis.health.domain.custom.chargeitemautogen.queries.ChargeItemAutoGenPageQuery;
import com.openhis.health.domain.custom.chargeitemautogen.queries.model.ChargeItemAutoGenActiveRuleQueryModel;
import com.openhis.health.domain.custom.chargeitemautogen.queries.model.ChargeItemAutoGenPageItemQueryModel;
import com.openhis.health.domain.custom.chargeitemautogen.queries.model.ChargeItemAutoGenRuleQueryModel;
import com.openhis.health.domain.custom.chargeitemautogen.repository.ChargeItemAutoGenRepository;
import com.openhis.health.domain.share.enums.PublicationStatus;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * 收费明细自动生成规则读侧查询服务。
 *
 * <p>职责：按触发源解析启用规则、按目标定价反查规则、分页检索规则维护列表。</p>
 *
 * <p>边界：只读；科室匹配语义见 {@link #isOrgMatched(Long, Long)}（{@code null} 执行科室表示全院规则）。</p>
 */
@Service
@RequiredArgsConstructor
public class ChargeItemAutoGenQueryService {

    private final ChargeItemAutoGenRepository repository;

    /**
     * 按触发源与执行科室解析当前可用的自动生成规则（已发布且科室匹配）。
     *
     * @param triggerSourceTable 触发来源表名
     * @param triggerSourceId    触发来源主键
     * @param performingOrgId    上下文执行科室；可与规则上的执行科室做匹配
     * @return 激活规则视图列表，按仓储序（优先级、id）
     */
    public List<ChargeItemAutoGenActiveRuleQueryModel> findActiveRulesByTrigger(
        String triggerSourceTable,
        Long triggerSourceId,
        Long performingOrgId
    ) {
        // 查询：按触发键从数据库获取候选集，并保证优先级与稳定顺序（PR-CAG-002）
        List<ChargeItemAutoGen> candidates = repository.findAllByTriggerSourceTableAndTriggerSourceIdOrderByPriorityAscIdAsc(
            triggerSourceTable,
            triggerSourceId
        );

        // 过滤：只保留 active 且满足科室匹配规则（BR-CAG-002）
        List<ChargeItemAutoGenActiveRuleQueryModel> result = new ArrayList<>();
        for (ChargeItemAutoGen row : candidates) {
            if (!PublicationStatus.ACTIVE.getCode().equals(row.getStatusEnum())) {
                continue;
            }
            if (!isOrgMatched(row.getPerformingOrgId(), performingOrgId)) {
                continue;
            }
            result.add(toActiveRuleModel(row));
        }
        return result;
    }

    /**
     * 按目标收费项定义反查关联的生成规则（维护台「谁会在开单时带出本条价项」）。
     *
     * @param targetDefinitionId 目标定价定义主键
     * @return 规则视图列表，按 id 升序
     */
    public List<ChargeItemAutoGenRuleQueryModel> findRulesByTargetDefinitionId(Long targetDefinitionId) {
        // 查询：按目标定价反查未软删规则
        List<ChargeItemAutoGen> rows = repository.findAllByTargetDefinitionIdOrderByIdAsc(targetDefinitionId);
        List<ChargeItemAutoGenRuleQueryModel> result = new ArrayList<>();
        for (ChargeItemAutoGen row : rows) {
            result.add(toRuleModel(row));
        }
        return result;
    }

    /**
     * 分页查询生成规则，条件下推到 {@link Specification}（含软删过滤）。
     *
     * @param query    可选筛选条件
     * @param pageable 分页与排序
     * @return 分页行模型
     */
    public Page<ChargeItemAutoGenPageItemQueryModel> pageChargeItemAutoGenRules(ChargeItemAutoGenPageQuery query, Pageable pageable) {
        // 查询：动态条件下推到数据库，避免内存过滤/手工分页
        Specification<ChargeItemAutoGen> specification = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (query != null) {
                if (query.getStatusEnum() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), query.getStatusEnum()));
                }
                if (query.getTriggerSourceTable() != null && !query.getTriggerSourceTable().trim().isEmpty()) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.TRIGGER_SOURCE_TABLE), query.getTriggerSourceTable().trim()));
                }
                if (query.getTriggerSourceId() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.TRIGGER_SOURCE_ID), query.getTriggerSourceId()));
                }
                if (query.getTargetDefinitionId() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.TARGET_DEFINITION_ID), query.getTargetDefinitionId()));
                }
                if (query.getPerformingOrgId() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.PERFORMING_ORG_ID), query.getPerformingOrgId()));
                }
                if (StringUtils.isNotBlank(query.getRuleNameKeyword())) {
                    String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getRuleNameKeyword().trim());
                    predicates.add(cb.like(cb.lower(root.get(FieldNameConstants.RULE_NAME)), likeIc));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<ChargeItemAutoGen> page = repository.findAll(specification, pageable);
        return page.map(this::toPageItemModel);
    }

    /**
     * 规则执行科室与上下文科室是否匹配：规则侧 {@code null} 表示不限科室；规则侧非空则必须与上下文相等。
     *
     * @param rulePerformingOrgId 规则上配置的执行科室
     * @param contextOrgId        当前开单/触发上下文科室
     * @return 是否允许该规则命中
     */
    private boolean isOrgMatched(Long rulePerformingOrgId, Long contextOrgId) {
        // 全局规则：任意上下文（含空）均可命中
        if (rulePerformingOrgId == null) {
            return true;
        }
        // 科室限定规则：上下文科室为空不降级为全局，必须显式不命中
        if (contextOrgId == null) {
            return false;
        }
        return rulePerformingOrgId.equals(contextOrgId);
    }

    /**
     * 映射为「触发命中」用的精简规则模型（不含数量等业务参数时可另查详情）。
     */
    private ChargeItemAutoGenActiveRuleQueryModel toActiveRuleModel(ChargeItemAutoGen row) {
        ChargeItemAutoGenActiveRuleQueryModel model = new ChargeItemAutoGenActiveRuleQueryModel();
        model.setId(row.getId());
        model.setStatusEnum(row.getStatusEnum());
        model.setRuleName(row.getRuleName());
        model.setTriggerSourceTable(row.getTriggerSourceTable());
        model.setTriggerSourceId(row.getTriggerSourceId());
        model.setTargetDefinitionId(row.getTargetDefinitionId());
        model.setPerformingOrgId(row.getPerformingOrgId());
        return model;
    }

    /**
     * 映射为维护台展示用的完整规则行（含数量、优先级、备注等）。
     */
    private ChargeItemAutoGenRuleQueryModel toRuleModel(ChargeItemAutoGen row) {
        ChargeItemAutoGenRuleQueryModel model = new ChargeItemAutoGenRuleQueryModel();
        model.setId(row.getId());
        model.setStatusEnum(row.getStatusEnum());
        model.setRuleName(row.getRuleName());
        model.setTriggerSourceTable(row.getTriggerSourceTable());
        model.setTriggerSourceId(row.getTriggerSourceId());
        model.setTargetDefinitionId(row.getTargetDefinitionId());
        model.setPerformingOrgId(row.getPerformingOrgId());
        model.setQuantityValue(row.getQuantityValue());
        model.setPriority(row.getPriority());
        model.setRemark(row.getRemark());
        return model;
    }

    /**
     * 映射为分页列表行（字段子集，减轻列表传输）。
     */
    private ChargeItemAutoGenPageItemQueryModel toPageItemModel(ChargeItemAutoGen row) {
        ChargeItemAutoGenPageItemQueryModel model = new ChargeItemAutoGenPageItemQueryModel();
        model.setId(row.getId());
        model.setStatusEnum(row.getStatusEnum());
        model.setRuleName(row.getRuleName());
        model.setTriggerSourceTable(row.getTriggerSourceTable());
        model.setTriggerSourceId(row.getTriggerSourceId());
        model.setTargetDefinitionId(row.getTargetDefinitionId());
        model.setPerformingOrgId(row.getPerformingOrgId());
        model.setQuantityValue(row.getQuantityValue());
        return model;
    }
}
