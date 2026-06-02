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

package com.openhis.health.domain.clinical.condition.service;


import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.health.domain.clinical.condition.entity.Condition;
import com.openhis.health.domain.clinical.condition.queries.model.ConditionBatchItemQueryModel;
import com.openhis.health.domain.clinical.condition.queries.model.DiagnosisCandidateQueryModel;
import com.openhis.health.domain.clinical.condition.queries.model.EncounterDiagnosisQueryModel;
import com.openhis.health.domain.clinical.condition.repository.ConditionRepository;
import com.openhis.health.domain.clinical.conditiondefinition.entity.ConditionDefinition;
import com.openhis.health.domain.clinical.conditiondefinition.repository.ConditionDefinitionRepository;
import com.openhis.health.domain.share.enums.PublicationStatus;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * 诊断（Condition）事实读侧查询服务。
 *
 * <p>职责：批量/按就诊加载诊断事实，以及从诊断定义目录检索候选条目（与病情事实表分离，见 BR-CND-004）。</p>
 *
 * <p>边界：只读；空入参返回空集合；不修改实体。与医生站关系：医生站列表/快照主要走读模型与
 * {@link com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService}，
 * 本服务供其它场景复用（批量卡片、通用候选检索等）。</p>
 */
@Service
@RequiredArgsConstructor
public class ConditionQueryService {

    /** 病情事实（Condition）仓储：按 id/就诊等派生查询，条件在方法名与底座软删中体现。 */
    private final ConditionRepository repository;

    /** 诊断定义（目录）仓储：候选检索走 Criteria，与事实查询分离。 */
    private final ConditionDefinitionRepository conditionDefinitionRepository;

    /**
     * 按主键集合批量查询诊断记录。
     *
     * <p><b>规模（B.2）</b>：依赖仓储 {@code IN} 查询；若 {@code ids} 体量极大，需由调用方分批或在库侧评估索引与 SQL 长度上限。</p>
     *
     * @param ids 诊断记录主键集合
     * @return 命中列表；ids 为空时返回空集合
     */
    public List<ConditionBatchItemQueryModel> getConditionListById(List<Long> ids) {
        // 无 id 时不访问仓储，保持批量接口空入参安全。
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        // 单次 IN 查询，满足 A.9.1 批量读；不在循环内按 id 单条查。
        List<Condition> entities = repository.findByIdIn(ids);
        List<ConditionBatchItemQueryModel> models = new ArrayList<>();
        for (Condition entity : entities) {
            if (Boolean.TRUE.equals(entity.getIsDeleted())) {
                continue;
            }
            // 装配为只读模型：仅暴露列表/卡片所需字段，避免把整实体泄漏到应用边界外。
            ConditionBatchItemQueryModel model = new ConditionBatchItemQueryModel();
            model.setId(entity.getId());
            model.setPatientId(entity.getPatientId());
            model.setDefinitionId(entity.getDefinitionId());
            model.setVerificationStatusEnum(entity.getVerificationStatusEnum());
            model.setClinicalStatusEnum(entity.getClinicalStatusEnum());
            model.setDescription(entity.getDescription());
            model.setYbNo(entity.getYbNo());
            models.add(model);
        }
        return models;
    }

    /**
     * 按就诊查询诊断记录。
     *
     * @param encounterId 就诊标识
     * @return 命中列表；encounterId 为空时返回空集合
     */
    public List<EncounterDiagnosisQueryModel> getEncounterDiagnosis(Long encounterId) {
        if (encounterId == null) {
            return new ArrayList<>();
        }
        // 就诊维度事实列表：由仓储派生方法下推 encounter 条件，不在内存再筛就诊。
        List<Condition> entities = repository.findByEncounterId(encounterId);
        List<EncounterDiagnosisQueryModel> models = new ArrayList<>();
        for (Condition entity : entities) {
            EncounterDiagnosisQueryModel model = new EncounterDiagnosisQueryModel();
            model.setConditionId(entity.getId());
            model.setEncounterId(entity.getEncounterId());
            model.setPatientId(entity.getPatientId());
            model.setDefinitionId(entity.getDefinitionId());
            model.setDescription(entity.getDescription());
            model.setRecordedDatetime(entity.getRecordedDatetime());
            models.add(model);
        }
        return models;
    }

    /**
     * 按关键字从诊断定义目录检索候选条目（非分页全量候选集）。
     *
     * <p><b>规模风险（B.2）</b>：使用 {@code findAll(Specification)} 且无 LIMIT，目录很大且关键字过短（如单字）
     * 时可能一次载入较多行。开放搜索场景应限制关键字最小长度、分页或改用医生站专用分页接口
     * {@link com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService#pageActiveDoctorStationDefinitions}。</p>
     *
     * @param keyword 匹配名称/编码/拼音/五笔的子串；前后空白会 trim
     * @return 命中列表；keyword 为空白时返回空集合（不访问库）
     */
    public List<DiagnosisCandidateQueryModel> getDiagnosisList(String keyword) {
        if (StringUtils.isBlank(keyword)) {
            return new ArrayList<>();
        }
        Specification<ConditionDefinition> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            // BR-CND-004：候选行必须来自目录聚合，不得从病情事实表「猜」编码。
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            // 仅已发布定义可被选，避免临床使用未生效目录码。
            predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), PublicationStatus.ACTIVE.getCode()));
            String trimmed = keyword.trim();
            String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed);
            // 四列 OR：与医生站检索列口径一致，命中任一即入选；关键字检索一律不区分大小写。
            predicates.add(cb.or(
                cb.like(cb.lower(root.get(FieldNameConstants.NAME)), likeIc),
                cb.like(cb.lower(root.get(FieldNameConstants.CONDITION_CODE)), likeIc),
                cb.like(cb.lower(root.get(FieldNameConstants.PY_STR)), likeIc),
                cb.like(cb.lower(root.get(FieldNameConstants.WB_STR)), likeIc)
            ));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        List<ConditionDefinition> definitions = conditionDefinitionRepository.findAll(specification);
        List<DiagnosisCandidateQueryModel> models = new ArrayList<>();
        for (ConditionDefinition definition : definitions) {
            // 行级投影：候选接口不需要目录全部字段，减少跨层耦合。
            DiagnosisCandidateQueryModel model = new DiagnosisCandidateQueryModel();
            model.setDefinitionId(definition.getId());
            model.setConditionCode(definition.getConditionCode());
            model.setName(definition.getName());
            model.setSourceEnum(definition.getSourceEnum());
            model.setStatusEnum(definition.getStatusEnum());
            models.add(model);
        }
        return models;
    }

    /**
     * 按患者加载历史诊断事实（医生站「病人历史诊断」快捷数据源）。
     *
     * @param patientId 患者主键
     * @return 诊断事实列表；{@code patientId} 为空时返回空集合
     */
    public List<Condition> findByPatientId(Long patientId) {
        if (patientId == null) {
            return new ArrayList<>();
        }
        return repository.findByPatientId(patientId);
    }

    /**
     * 统计指定诊断被医嘱引用的数量（删除前门控，见 BR-CND-003）。
     *
     * @param conditionId 诊断主键
     * @return 引用计数；{@code conditionId} 为空时返回 0
     */
    public int countAdviceRefByConditionId(Long conditionId) {
        if (conditionId == null) {
            return 0;
        }
        return repository.countAdviceRefByConditionId(conditionId);
    }
}
