/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.OpspDiseCodeDefinitionMatchQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.repository.ConditionDefinitionRepository;
import com.openhis.health.domain.share.constants.DoctorStationDiagnosisConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.utils.KeywordExactFirstOrderSupport;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 诊断定义（目录）读侧查询服务。
 *
 * <p>职责：目录详情、批量加载、关键字搜索、分页列表及疾病视图详情。</p>
 *
 * <p>边界：只读；强依赖主键的方法在记录缺失时抛 {@link BusinessException}。
 * 医生站诊断录入分页与批量行装配（IF-DOR-07）由本服务收口，避免应用层直接拼 Specification。</p>
 *
 * <p>聚合边界：仅本聚合 {@link ConditionDefinitionRepository}。</p>
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
        return searchByKeywordAndSource(keyword, null);
    }

    /**
     * 按关键字检索国家临床版 2.0（病案首页 ICD-10 picker）。
     *
     * @param keyword 关键字
     * @return 命中列表；关键字为空白时返回空集合
     */
    public List<ConditionDefinitionSearchItemQueryModel> searchClinicalV2ByKeyword(String keyword) {
        return searchByKeywordAndSource(keyword, DoctorStationDiagnosisConstants.SOURCE_CLINICAL_V2);
    }

    /**
     * 按国临码批量解析医保诊断编码（4401 上传用）。
     *
     * <p>优先取 {@code clinic_code} 命中且 {@code yb_no} 非空的目录行；
     * 同码多行时优先非 {@code clinical_v2} 来源。</p>
     *
     * @param clinicCodes 国临码集合
     * @return 国临码 → 医保码；无法映射的码不出现在结果中
     */
    public Map<String, String> mapYbNoByClinicCodes(Collection<String> clinicCodes) {
        Map<String, String> result = new LinkedHashMap<>();
        if (clinicCodes == null || clinicCodes.isEmpty()) {
            return result;
        }
        List<String> codes = new ArrayList<>();
        for (String code : clinicCodes) {
            if (StringUtils.isNotBlank(code)) {
                codes.add(code.trim());
            }
        }
        if (codes.isEmpty()) {
            return result;
        }
        Map<String, ConditionDefinition> bestByClinic = new HashMap<>();
        // 优先：clinic_code 命中且 yb_no 非空
        for (ConditionDefinition entity : repository.findByClinicCodeIn(codes)) {
            putBestYbMapping(bestByClinic, entity, StringUtils.trimToNull(entity.getClinicCode()));
        }
        // 回退：condition_code=国临且 yb_no 非空（医保目录未填 clinic_code 时）
        for (ConditionDefinition entity : repository.findByConditionCodeIn(codes)) {
            putBestYbMapping(bestByClinic, entity, StringUtils.trimToNull(entity.getConditionCode()));
        }
        for (Map.Entry<String, ConditionDefinition> entry : bestByClinic.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getYbNo().trim());
        }
        return result;
    }

    /**
     * 在「国临键 → 最佳目录行」中择优：须有 yb_no；非国临分类优先于 clinical_v2。
     */
    private void putBestYbMapping(
            Map<String, ConditionDefinition> bestByClinic,
            ConditionDefinition entity,
            String clinicKey) {
        if (entity == null || Boolean.TRUE.equals(entity.getIsDeleted()) || clinicKey == null) {
            return;
        }
        String ybNo = StringUtils.trimToNull(entity.getYbNo());
        if (ybNo == null) {
            return;
        }
        ConditionDefinition existing = bestByClinic.get(clinicKey);
        if (existing == null) {
            bestByClinic.put(clinicKey, entity);
            return;
        }
        boolean existingIsClinical = DoctorStationDiagnosisConstants.SOURCE_CLINICAL_V2.equals(existing.getSourceEnum());
        boolean currentIsClinical = DoctorStationDiagnosisConstants.SOURCE_CLINICAL_V2.equals(entity.getSourceEnum());
        if (existingIsClinical && !currentIsClinical) {
            bestByClinic.put(clinicKey, entity);
        }
    }

    /**
     * 按关键字与可选来源检索目录（ACTIVE + 未软删）。
     *
     * @param keyword    关键字
     * @param sourceEnum 来源；null 表示不限来源
     * @return 搜索项列表
     */
    private List<ConditionDefinitionSearchItemQueryModel> searchByKeywordAndSource(String keyword, String sourceEnum) {
        if (StringUtils.isEmpty(keyword)) {
            return new ArrayList<>();
        }
        String trimmed = keyword.trim();
        String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed);
        Specification<ConditionDefinition> specification = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), PublicationStatus.ACTIVE.getCode()));
            if (StringUtils.isNotBlank(sourceEnum)) {
                predicates.add(cb.equal(root.get(FieldNameConstants.SOURCE_ENUM), sourceEnum.trim()));
            }
            predicates.add(cb.or(
                cb.like(cb.lower(root.get(FieldNameConstants.NAME)), likeIc),
                cb.like(cb.lower(root.get(FieldNameConstants.CONDITION_CODE)), likeIc),
                cb.like(cb.lower(root.get(FieldNameConstants.CLINIC_CODE)), likeIc),
                cb.like(cb.lower(root.get(FieldNameConstants.PY_STR)), likeIc),
                cb.like(cb.lower(root.get(FieldNameConstants.WB_STR)), likeIc)
            ));
            // B.2.1：诊断检索精确全等置顶
            KeywordExactFirstOrderSupport.applyExactFirstOrder(
                cq,
                cb,
                trimmed,
                List.of(cb.desc(root.get(FieldNameConstants.ID))),
                root.get(FieldNameConstants.NAME),
                root.get(FieldNameConstants.CONDITION_CODE),
                root.get(FieldNameConstants.CLINIC_CODE),
                root.get(FieldNameConstants.PY_STR),
                root.get(FieldNameConstants.WB_STR));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ConditionDefinition> entities = repository.findAll(specification);
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
     * @param query 查询条件（关键字、来源分类等）
     * @param pageable 分页参数
     * @return 分页结果
     */
    public Page<DiseasePageItemQueryModel> getDiseaseList(DiseasePageQuery query, Pageable pageable) {
        String keyword = "";
        if (query != null && StringUtils.isNotEmpty(query.getKeyword())) {
            keyword = query.getKeyword().trim();
        }
        String sourceEnum = null;
        if (query != null && StringUtils.isNotBlank(query.getSourceEnum())) {
            sourceEnum = query.getSourceEnum().trim();
        }
        Specification<ConditionDefinition> specification = this.buildDiseasePageSpec(keyword, sourceEnum);
        // B.2.1：有关键字时剥掉 Sort，保留 Spec 精确优先 ORDER BY
        Pageable effective = pageable;
        if (StringUtils.isNotEmpty(keyword) && pageable != null && pageable.getSort().isSorted()) {
            effective = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        }
        return repository.findAll(specification, effective).map(this::toDiseasePageItem);
    }

    /**
     * 构建疾病目录维护页分页查询条件（未软删 + 可选来源 + 可选名称/编码关键字）。
     *
     * @param keyword    名称或编码模糊匹配；空白时不追加 LIKE 谓词
     * @param sourceEnum 目录来源分类编码；空白时不按来源过滤
     * @return 分页 Specification
     */
    private Specification<ConditionDefinition> buildDiseasePageSpec(String keyword, String sourceEnum) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (StringUtils.isNotBlank(sourceEnum)) {
                predicates.add(cb.equal(root.get(FieldNameConstants.SOURCE_ENUM), sourceEnum.trim()));
            }
            if (StringUtils.isNotBlank(keyword)) {
                String trimmed = keyword.trim();
                String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed);
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get(FieldNameConstants.NAME)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.CONDITION_CODE)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.CLINIC_CODE)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.YB_NO)), likeIc)
                ));
                // B.2.1：目录维护关键字精确全等置顶
                KeywordExactFirstOrderSupport.applyExactFirstOrder(
                    cq,
                    cb,
                    trimmed,
                    List.of(cb.desc(root.get(FieldNameConstants.ID))),
                    root.get(FieldNameConstants.NAME),
                    root.get(FieldNameConstants.CONDITION_CODE),
                    root.get(FieldNameConstants.CLINIC_CODE),
                    root.get(FieldNameConstants.YB_NO));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
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
        // 医生站检索：无关键字时最近维护优先；有关键字时 Spec 内「精确优先 + updatedAt」写 ORDER BY（B.2.1）
        Pageable pageable;
        if (StringUtils.isNotEmpty(keyword)) {
            pageable = PageRequest.of(resolvedPageNum - 1, resolvedPageSize);
        } else {
            pageable = PageRequest.of(
                resolvedPageNum - 1,
                resolvedPageSize,
                Sort.by(Sort.Order.desc(FieldNameConstants.UPDATED_AT), Sort.Order.desc(FieldNameConstants.ID)));
        }
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
     * 按门慢门特病种码批量解析已启用医生站目录行（DM-CLN-002 §6.2.8 / IF-DOR-06B）。
     *
     * <p>匹配优先级：{@code yb_no} → {@code condition_code}；
     * 同优先级多行时优先 {@code chronic_spec}，再按 {@code updatedAt}/主键降序取一条；
     * 仍多条时 {@code ambiguous=true}。</p>
     *
     * @param opspDiseCodes 备案病种码集合
     * @return 命中投影；未命中码不出现在结果中
     */
    public List<OpspDiseCodeDefinitionMatchQueryModel> listActiveDoctorStationRowsByOpspDiseCodes(
        Collection<String> opspDiseCodes) {
        List<OpspDiseCodeDefinitionMatchQueryModel> result = new ArrayList<>();
        if (opspDiseCodes == null || opspDiseCodes.isEmpty()) {
            return result;
        }
        // 去空白并保序，避免重复码重复查库。
        Set<String> normalized = new LinkedHashSet<>();
        for (String raw : opspDiseCodes) {
            if (StringUtils.isBlank(raw)) {
                continue;
            }
            normalized.add(raw.trim());
        }
        if (normalized.isEmpty()) {
            return result;
        }
        List<String> codes = new ArrayList<>(normalized);
        // 一次拉取可能命中任一匹配列的启用目录，再在内存按码择优。
        Specification<ConditionDefinition> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), PublicationStatus.ACTIVE.getCode()));
            predicates.add(cb.or(
                root.get(FieldNameConstants.YB_NO).in(codes),
                root.get(FieldNameConstants.CONDITION_CODE).in(codes)));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ConditionDefinition> candidates = repository.findAll(specification);
        for (String code : codes) {
            OpspDiseCodeDefinitionMatchQueryModel match = this.resolveOpspDiseCodeMatch(code, candidates);
            // 无命中：调用方按未匹配处理，结果集不返回该码。
            if (match == null) {
                continue;
            }
            result.add(match);
        }
        return result;
    }

    /**
     * 对单个备案码在候选集中按 DM §6.2.8 优先级解析一条目录。
     *
     * @param code       归一化备案码
     * @param candidates 已启用候选实体
     * @return 匹配投影；无命中返回 {@code null}
     */
    private OpspDiseCodeDefinitionMatchQueryModel resolveOpspDiseCodeMatch(
        String code,
        List<ConditionDefinition> candidates) {
        List<ConditionDefinition> byYbNo = new ArrayList<>();
        List<ConditionDefinition> byConditionCode = new ArrayList<>();
        for (ConditionDefinition entity : candidates) {
            if (code.equals(StringUtils.trimToNull(entity.getYbNo()))) {
                byYbNo.add(entity);
            }
            if (code.equals(StringUtils.trimToNull(entity.getConditionCode()))) {
                byConditionCode.add(entity);
            }
        }
        // 优先医保编码。
        if (!byYbNo.isEmpty()) {
            return this.toOpspMatchModel(code, byYbNo, DoctorStationDiagnosisConstants.OPSP_MATCH_FIELD_YB_NO);
        }
        // 兜底诊断目录编码。
        if (!byConditionCode.isEmpty()) {
            return this.toOpspMatchModel(
                code, byConditionCode, DoctorStationDiagnosisConstants.OPSP_MATCH_FIELD_CONDITION_CODE);
        }
        return null;
    }

    /**
     * 同匹配列多候选时择优并标记歧义。
     *
     * @param code       备案码
     * @param tierRows   同优先级候选
     * @param matchField 命中列标识
     * @return 匹配投影
     */
    private OpspDiseCodeDefinitionMatchQueryModel toOpspMatchModel(
        String code,
        List<ConditionDefinition> tierRows,
        String matchField) {
        List<ConditionDefinition> preferred = new ArrayList<>();
        for (ConditionDefinition row : tierRows) {
            // 特慢病目录来源优先进入候选池。
            if (DoctorStationDiagnosisConstants.SOURCE_CHRONIC_SPEC.equals(row.getSourceEnum())) {
                preferred.add(row);
            }
        }
        List<ConditionDefinition> pool = tierRows;
        // 有特慢病目录命中时仅在该子集内择优。
        if (!preferred.isEmpty()) {
            pool = preferred;
        }
        // updatedAt 降序、主键降序，保证稳定择优。
        pool.sort(Comparator
            .comparing(ConditionDefinition::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(ConditionDefinition::getId, Comparator.nullsLast(Comparator.reverseOrder())));
        ConditionDefinition winner = pool.get(0);
        OpspDiseCodeDefinitionMatchQueryModel model = new OpspDiseCodeDefinitionMatchQueryModel();
        model.setOpspDiseCode(code);
        model.setConditionDefinitionId(winner.getId());
        model.setConditionCode(winner.getConditionCode());
        model.setName(winner.getName());
        model.setSourceEnum(winner.getSourceEnum());
        model.setMatchField(matchField);
        // 择优池仍多于 1：应用层禁止静默导入。
        model.setAmbiguous(pool.size() > 1);
        return model;
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
            if (StringUtils.isNotEmpty(keyword)) {
                String trimmed = keyword.trim();
                String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed);
                List<Predicate> ors = new ArrayList<>();
                ors.add(cb.like(cb.lower(root.get(FieldNameConstants.NAME)), likeIc));
                ors.add(cb.like(cb.lower(root.get(FieldNameConstants.CONDITION_CODE)), likeIc));
                ors.add(cb.like(cb.lower(root.get(FieldNameConstants.PY_STR)), likeIc));
                ors.add(cb.like(cb.lower(root.get(FieldNameConstants.WB_STR)), likeIc));
                predicates.add(cb.or(ors.toArray(new Predicate[0])));
                // B.2.1：医生站诊断下拉精确全等置顶，次级按更新时间倒序
                List<Order> secondary = List.of(cb.desc(root.get(FieldNameConstants.UPDATED_AT)));
                KeywordExactFirstOrderSupport.applyExactFirstOrder(
                    query,
                    cb,
                    trimmed,
                    secondary,
                    root.get(FieldNameConstants.NAME),
                    root.get(FieldNameConstants.CONDITION_CODE),
                    root.get(FieldNameConstants.PY_STR),
                    root.get(FieldNameConstants.WB_STR));
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
        model.setYbNo(entity.getYbNo());
        model.setYbMatchFlag(entity.getYbMatchFlag());
        model.setClinicCode(entity.getClinicCode());
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
        if (StringUtils.isEmpty(keyword)) {
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
            // B.2.1：诊断候选精确全等置顶
            KeywordExactFirstOrderSupport.applyExactFirstOrder(
                cq,
                cb,
                trimmed,
                List.of(cb.desc(root.get(FieldNameConstants.ID))),
                root.get(FieldNameConstants.NAME),
                root.get(FieldNameConstants.CONDITION_CODE),
                root.get(FieldNameConstants.PY_STR),
                root.get(FieldNameConstants.WB_STR));
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
        model.setYbNo(entity.getYbNo());
        model.setClinicCode(entity.getClinicCode());
        model.setDescription(entity.getDescription());
        return model;
    }
}
