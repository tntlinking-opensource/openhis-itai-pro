/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.chargeitemautogen.service;


import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.utils.KeywordExactFirstOrderSupport;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.health.domain.custom.chargeitemautogen.entity.ChargeItemAutoGen;
import com.openhis.health.domain.custom.chargeitemautogen.queries.ChargeItemAutoGenPageQuery;
import com.openhis.health.domain.custom.chargeitemautogen.queries.model.ChargeItemAutoGenActiveRuleQueryModel;
import com.openhis.health.domain.custom.chargeitemautogen.queries.model.ChargeItemAutoGenPageItemQueryModel;
import com.openhis.health.domain.custom.chargeitemautogen.queries.model.ChargeItemAutoGenRuleQueryModel;
import com.openhis.health.domain.custom.chargeitemautogen.repository.ChargeItemAutoGenRepository;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
 *
 * <p>聚合边界：仅本聚合 {@link ChargeItemAutoGenRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class ChargeItemAutoGenQueryService {

    private final ChargeItemAutoGenRepository repository;

    /**
     * 按住院就诊查询自动计费配置（划价页「自动计费」Tab、定时滚费任务）。
     *
     * @param encounterId 住院就诊标识
     * @return 未软删配置列表，按 priority、id 升序
     */
    public List<ChargeItemAutoGenRuleQueryModel> findRulesByEncounterId(Long encounterId) {
        List<ChargeItemAutoGen> rows = repository.findAllByEncounterIdAndIsDeletedFalseOrderByPriorityAscIdAsc(encounterId);
        List<ChargeItemAutoGenRuleQueryModel> result = new ArrayList<>();
        for (ChargeItemAutoGen row : rows) {
            result.add(toRuleModel(row));
        }
        return result;
    }

    /**
     * 按住院就诊解析当前启用的自动计费配置。
     *
     * @param encounterId     住院就诊标识
     * @param performingOrgId 上下文执行科室
     * @return 激活规则列表
     */
    public List<ChargeItemAutoGenActiveRuleQueryModel> findActiveRulesByEncounter(Long encounterId, Long performingOrgId) {
        List<ChargeItemAutoGen> candidates = repository.findAllByEncounterIdAndIsDeletedFalseOrderByPriorityAscIdAsc(encounterId);
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
        List<ChargeItemAutoGen> candidates = repository.findAllByTriggerSourceTableAndTriggerSourceIdAndIsDeletedFalseOrderByPriorityAscIdAsc(
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
     * @param chargeDefinitionId 收费项定价定义主键
     * @return 规则视图列表，按 id 升序
     */
    public List<ChargeItemAutoGenRuleQueryModel> findRulesByChargeDefinitionId(Long chargeDefinitionId) {
        // 查询：按目标定价反查未软删规则
        List<ChargeItemAutoGen> rows = repository.findAllByChargeDefinitionIdAndIsDeletedFalseOrderByIdAsc(chargeDefinitionId);
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
                if (query.getEncounterId() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.ENCOUNTER_ID), query.getEncounterId()));
                }
                if (query.getTriggerSourceTable() != null && !query.getTriggerSourceTable().trim().isEmpty()) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.TRIGGER_SOURCE_TABLE), query.getTriggerSourceTable().trim()));
                }
                if (query.getTriggerSourceId() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.TRIGGER_SOURCE_ID), query.getTriggerSourceId()));
                }
                if (query.getChargeDefinitionId() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.CHARGE_DEFINITION_ID), query.getChargeDefinitionId()));
                }
                if (query.getPerformingOrgId() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.PERFORMING_ORG_ID), query.getPerformingOrgId()));
                }
                if (StringUtils.isNotEmpty(query.getRuleNameKeyword())) {
                    String trimmed = query.getRuleNameKeyword().trim();
                    String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed);
                    predicates.add(cb.like(cb.lower(root.get(FieldNameConstants.RULE_NAME)), likeIc));
                    // B.2.1：规则名精确全等置顶
                    KeywordExactFirstOrderSupport.applyExactFirstOrder(
                        cq,
                        cb,
                        trimmed,
                        List.of(cb.desc(root.get(FieldNameConstants.ID))),
                        root.get(FieldNameConstants.RULE_NAME));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        // 关键字非空时剥掉 Pageable Sort，保留 Spec 精确优先 ORDER BY
        Pageable effective = pageable;
        if (query != null && StringUtils.isNotEmpty(query.getRuleNameKeyword())
            && pageable != null && pageable.getSort().isSorted()) {
            effective = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        }
        Page<ChargeItemAutoGen> page = repository.findAll(specification, effective);
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
        model.setEncounterId(row.getEncounterId());
        model.setTriggerSourceTable(row.getTriggerSourceTable());
        model.setTriggerSourceId(row.getTriggerSourceId());
        model.setChargeDefinitionId(row.getChargeDefinitionId());
        model.setPerformingOrgId(row.getPerformingOrgId());
        model.setQuantityValue(row.getQuantityValue());
        model.setPriority(row.getPriority());
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
        model.setEncounterId(row.getEncounterId());
        model.setTriggerSourceTable(row.getTriggerSourceTable());
        model.setTriggerSourceId(row.getTriggerSourceId());
        model.setChargeDefinitionId(row.getChargeDefinitionId());
        model.setPerformingOrgId(row.getPerformingOrgId());
        model.setQuantityValue(row.getQuantityValue());
        model.setPriority(row.getPriority());
        model.setRemark(row.getRemark());
        model.setCreatedAt(row.getCreatedAt());
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
        model.setEncounterId(row.getEncounterId());
        model.setTriggerSourceTable(row.getTriggerSourceTable());
        model.setTriggerSourceId(row.getTriggerSourceId());
        model.setChargeDefinitionId(row.getChargeDefinitionId());
        model.setPerformingOrgId(row.getPerformingOrgId());
        model.setQuantityValue(row.getQuantityValue());
        return model;
    }
}
