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

package com.openhis.health.domain.clinical.conditiondefinition.service;

import com.openhis.health.domain.clinical.condition.queries.model.DiagnosisCandidateQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.entity.ConditionDefinition;
import com.openhis.health.domain.clinical.conditiondefinition.queries.DiseasePageQuery;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionBatchItemQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionDetailQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionSearchItemQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.DiseaseDetailQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.DiseasePageItemQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.DoctorStationConditionDefinitionRowModel;
import com.openhis.health.domain.clinical.conditiondefinition.repository.ConditionDefinitionRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 诊断定义（目录）读侧查询服务。
 *
 * <p>职责：目录详情、批量加载、关键字搜索、分页列表及疾病视图详情。</p>
 *
 * <p>边界：只读；强依赖主键的方法在记录缺失时抛 {@link BusinessException}。
 * 医生站诊断录入分页与批量行装配（IF-DOR-07）由本服务收口，避免应用层直接拼 Specification。</p>
 */
@Service
@RequiredArgsConstructor
public class ConditionDefinitionQueryService {

    /** 诊断定义（目录）实体仓储：Criteria 与分页均由本服务组装。 */
    private final ConditionDefinitionRepository repository;

    /**
     * 按主键查询目录详情。
     *
     * @param id 主键
     * @return 目录详情
     * @throws BusinessException 主键不存在时抛出
     */
    public ConditionDefinitionDetailQueryModel getConditionDefinitionListById(Long id) {
        // 目录详情强依赖：不存在则阻断，避免 UI 展示空壳。
        ConditionDefinition entity = repository.findById(id).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.CONDITION_DEFINITION_NOT_FOUND, "诊断定义不存在"));
        ConditionDefinitionDetailQueryModel model = new ConditionDefinitionDetailQueryModel();
        model.setId(entity.getId());
        model.setConditionCode(entity.getConditionCode());
        model.setName(entity.getName());
        model.setSourceEnum(entity.getSourceEnum());
        model.setStatusEnum(entity.getStatusEnum());
        return model;
    }

    /**
     * 按主键集合批量查询目录。
     *
     * @param ids 主键集合
     * @return 命中列表；当 ids 为空时返回空集合
     */
    public List<ConditionDefinitionBatchItemQueryModel> getConditionDefinitionListByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        List<ConditionDefinition> entities = repository.findByIdIn(ids);
        List<ConditionDefinitionBatchItemQueryModel> models = new ArrayList<>();
        for (ConditionDefinition entity : entities) {
            if (Boolean.TRUE.equals(entity.getIsDeleted())) {
                continue;
            }
            ConditionDefinitionBatchItemQueryModel model = new ConditionDefinitionBatchItemQueryModel();
            model.setId(entity.getId());
            model.setConditionCode(entity.getConditionCode());
            model.setYbNo(entity.getYbNo());
            model.setName(entity.getName());
            model.setStatusEnum(entity.getStatusEnum());
            model.setSourceEnum(entity.getSourceEnum());
            models.add(model);
        }
        return models;
    }

    /**
     * 按关键字搜索目录。
     *
     * @param keyword 关键字
     * @return 命中列表；关键字为空白时返回空集合
     */
    public List<ConditionDefinitionSearchItemQueryModel> getConditionDefinitionListBySearchKey(String keyword) {
        if (StringUtils.isBlank(keyword)) {
            return new ArrayList<>();
        }
        List<ConditionDefinition> entities = repository.findByNameContainingOrConditionCodeContaining(keyword, keyword, Pageable.unpaged()).getContent();
        List<ConditionDefinitionSearchItemQueryModel> models = new ArrayList<>();
        for (ConditionDefinition entity : entities) {
            ConditionDefinitionSearchItemQueryModel model = new ConditionDefinitionSearchItemQueryModel();
            model.setId(entity.getId());
            model.setConditionCode(entity.getConditionCode());
            model.setName(entity.getName());
            model.setPyStr(entity.getPyStr());
            model.setWbStr(entity.getWbStr());
            model.setStatusEnum(entity.getStatusEnum());
            models.add(model);
        }
        return models;
    }

    /**
     * 分页查询目录列表。
     *
     * @param query 查询条件
     * @param pageable 分页参数
     * @return 分页结果
     */
    public Page<DiseasePageItemQueryModel> getDiseaseList(DiseasePageQuery query, Pageable pageable) {
        // 关键字缺省为空串，由仓储派生方法解释为「不限定名称/编码」（与既有语义一致）。
        String keyword = "";
        if (query != null && StringUtils.isNotBlank(query.getKeyword())) {
            keyword = query.getKeyword().trim();
        }
        return repository.findByNameContainingOrConditionCodeContaining(keyword, keyword, pageable)
            .map(this::toDiseasePageItem);
    }

    /**
     * 医生站诊断录入：分页检索已发布诊断定义（IF-DOR-07）。
     *
     * <p>过滤 {@link PublicationStatus#ACTIVE}；可选按目录 {@code sourceEnum} 集合收窄；
     * 关键字同时匹配名称、编码、拼音与五笔列；按 {@code updatedAt} 倒序。</p>
     *
     * @param keyword       关键字，可空白
     * @param sourceEnumIn  来源分类编码集合，空或 null 表示不按来源过滤
     * @param pageNum       页码（从 1 开始）
     * @param pageSize      页大小
     * @return 分页行
     */
    public Page<DoctorStationConditionDefinitionRowModel> pageActiveDoctorStationDefinitions(
        String keyword,
        List<String> sourceEnumIn,
        int pageNum,
        int pageSize) {
        // 页码/页大小防御：避免非法 PageRequest 与 UI 传入 0 导致异常或空页语义混乱。
        int resolvedPageNum = pageNum;
        if (resolvedPageNum < 1) {
            resolvedPageNum = 1;
        }
        int resolvedPageSize = pageSize;
        if (resolvedPageSize < 1) {
            resolvedPageSize = 10;
        }
        // 医生站检索：最近维护的目录项优先，同时间戳下按主键倒序保证稳定顺序。
        Pageable pageable = PageRequest.of(
            resolvedPageNum - 1,
            resolvedPageSize,
            Sort.by(Sort.Order.desc(FieldNameConstants.UPDATED_AT), Sort.Order.desc(FieldNameConstants.ID)));
        Specification<ConditionDefinition> specification = this.buildDoctorStationDefinitionSpec(keyword, sourceEnumIn);
        return repository.findAll(specification, pageable).map(this::toDoctorStationRow);
    }

    /**
     * 按主键批量加载医生站展示用目录行（仅保留已启用且未软删）。
     *
     * <p><b>规模（B.2）</b>：单次 {@code findByIdIn} 后在内存过滤软删与非 ACTIVE；典型入参来自模板/快照
     * 的有限 id 集合。若调用方传入超大 id 列表，应考虑分批或改走分页检索接口。</p>
     *
     * @param ids 诊断定义主键集合
     * @return 行模型列表；顺序不保证与入参一致
     */
    public List<DoctorStationConditionDefinitionRowModel> listActiveDoctorStationRowsByIds(List<Long> ids) {
        List<DoctorStationConditionDefinitionRowModel> result = new ArrayList<>();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        List<ConditionDefinition> entities = repository.findByIdIn(ids);
        for (ConditionDefinition entity : entities) {
            // 与动态查询一致：软删行不出现在医生站装配结果中。
            if (Boolean.TRUE.equals(entity.getIsDeleted())) {
                continue;
            }
            // 未发布定义不允许作为已选行回填展示（与录入检索口径一致）。
            if (!PublicationStatus.ACTIVE.getCode().equals(entity.getStatusEnum())) {
                continue;
            }
            result.add(this.toDoctorStationRow(entity));
        }
        return result;
    }

    /**
     * 构建医生站诊断定义动态查询条件（软删 + 已发布 + 可选来源 IN + 可选关键字四列 OR）。
     *
     * @param keyword      模糊匹配关键字；空白时不追加 LIKE 谓词（仅状态/来源过滤）
     * @param sourceEnumIn 来源集合；null 或空时不追加 IN 谓词
     * @return 可复用于分页与全量 findAll 的 Specification
     */
    private Specification<ConditionDefinition> buildDoctorStationDefinitionSpec(String keyword, List<String> sourceEnumIn) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), PublicationStatus.ACTIVE.getCode()));
            if (sourceEnumIn != null && !sourceEnumIn.isEmpty()) {
                // 中西医等目录来源收窄：与前端 tab 传入的 source 编码集合对齐。
                predicates.add(root.get(FieldNameConstants.SOURCE_ENUM).in(sourceEnumIn));
            }
            if (StringUtils.isNotBlank(keyword)) {
                String trimmed = keyword.trim();
                String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed);
                List<Predicate> ors = new ArrayList<>();
                ors.add(cb.like(cb.lower(root.get(FieldNameConstants.NAME)), likeIc));
                ors.add(cb.like(cb.lower(root.get(FieldNameConstants.CONDITION_CODE)), likeIc));
                ors.add(cb.like(cb.lower(root.get(FieldNameConstants.PY_STR)), likeIc));
                ors.add(cb.like(cb.lower(root.get(FieldNameConstants.WB_STR)), likeIc));
                predicates.add(cb.or(ors.toArray(new Predicate[0])));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 将目录实体投影为医生站表格行（展示列子集，不含大段备注等非表格字段）。
     *
     * @param entity 目录实体
     * @return 行模型
     */
    private DoctorStationConditionDefinitionRowModel toDoctorStationRow(ConditionDefinition entity) {
        DoctorStationConditionDefinitionRowModel model = new DoctorStationConditionDefinitionRowModel();
        model.setId(entity.getId());
        model.setConditionCode(entity.getConditionCode());
        model.setName(entity.getName());
        model.setPyStr(entity.getPyStr());
        model.setWbStr(entity.getWbStr());
        model.setSourceEnum(entity.getSourceEnum());
        model.setStatusEnum(entity.getStatusEnum());
        model.setYbFlag(entity.getYbFlag());
        model.setYbNo(entity.getYbNo());
        return model;
    }

    /**
     * 按主键读取目录详情。
     *
     * @param id 主键
     * @return 详情模型
     * @throws BusinessException 主键不存在时抛出
     */
    public DiseaseDetailQueryModel getDiseaseOne(Long id) {
        // 疾病详情与目录详情同源强依赖，缺失即失败。
        ConditionDefinition entity = repository.findById(id).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.CONDITION_DEFINITION_NOT_FOUND, "诊断定义不存在"));
        DiseaseDetailQueryModel model = new DiseaseDetailQueryModel();
        model.setId(entity.getId());
        model.setSourceEnum(entity.getSourceEnum());
        model.setConditionCode(entity.getConditionCode());
        model.setName(entity.getName());
        model.setPyStr(entity.getPyStr());
        model.setWbStr(entity.getWbStr());
        model.setTypeCode(entity.getTypeCode());
        model.setDescription(entity.getDescription());
        model.setYbFlag(entity.getYbFlag());
        model.setDiseaseCode(entity.getDiseaseCode());
        model.setYbNo(entity.getYbNo());
        model.setYbMatchFlag(entity.getYbMatchFlag());
        model.setStatusEnum(entity.getStatusEnum());
        return model;
    }

    /**
     * 按关键字从诊断定义目录检索候选条目（非分页全量候选集）。
     *
     * @param keyword 匹配名称/编码/拼音/五笔的子串；前后空白会 trim
     * @return 命中列表；keyword 为空白时返回空集合（不访问库）
     */
    public List<DiagnosisCandidateQueryModel> getDiagnosisList(String keyword) {
        if (StringUtils.isBlank(keyword)) {
            return new ArrayList<>();
        }
        Specification<ConditionDefinition> specification = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), PublicationStatus.ACTIVE.getCode()));
            String trimmed = keyword.trim();
            String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed);
            predicates.add(cb.or(
                cb.like(cb.lower(root.get(FieldNameConstants.NAME)), likeIc),
                cb.like(cb.lower(root.get(FieldNameConstants.CONDITION_CODE)), likeIc),
                cb.like(cb.lower(root.get(FieldNameConstants.PY_STR)), likeIc),
                cb.like(cb.lower(root.get(FieldNameConstants.WB_STR)), likeIc)
            ));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ConditionDefinition> definitions = repository.findAll(specification);
        List<DiagnosisCandidateQueryModel> models = new ArrayList<>();
        for (ConditionDefinition definition : definitions) {
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
     * 将目录实体映射为疾病分页行（含医保标识等列表展示字段）。
     *
     * @param entity 诊断定义实体
     * @return 分页行模型
     */
    private DiseasePageItemQueryModel toDiseasePageItem(ConditionDefinition entity) {
        DiseasePageItemQueryModel model = new DiseasePageItemQueryModel();
        model.setId(entity.getId());
        model.setConditionCode(entity.getConditionCode());
        model.setName(entity.getName());
        model.setSourceEnum(entity.getSourceEnum());
        model.setStatusEnum(entity.getStatusEnum());
        model.setYbFlag(entity.getYbFlag());
        return model;
    }
}
