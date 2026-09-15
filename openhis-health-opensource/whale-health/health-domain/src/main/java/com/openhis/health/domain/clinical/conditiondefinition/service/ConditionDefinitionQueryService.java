/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.conditiondefinition.service;

import com.openhis.health.domain.clinical.condition.queries.model.DiagnosisCandidateQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.entity.ConditionDefinition;
import com.openhis.health.domain.clinical.conditiondefinition.queries.DiseasePageQuery;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.ClinicalDiagnosisPresentationQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.ConditionClinicBindingQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionBatchItemQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionDetailQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionSearchItemQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.DiseaseDetailQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.DiseasePageItemQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.DoctorStationConditionDefinitionRowModel;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.ConditionYbMappingQueryModel;
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
import java.util.HashSet;
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
     * 按国临码批量解析国家临床版诊断名称（目录列表「国临诊断名称」列）。
     *
     * <p>仅查 {@code clinical_v2}：国临主码即 {@code condition_code}；无法命中的码不出现在结果中。</p>
     *
     * @param clinicCodes 国临码集合
     * @return 国临码 → 国临诊断名称
     */
    public Map<String, String> mapNameByClinicCodes(Collection<String> clinicCodes) {
        Map<String, String> result = new LinkedHashMap<>();
        if (clinicCodes == null || clinicCodes.isEmpty()) {
            return result;
        }
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (String code : clinicCodes) {
            if (StringUtils.isNotBlank(code)) {
                codes.add(code.trim());
            }
        }
        if (codes.isEmpty()) {
            return result;
        }
        List<ConditionDefinition> rows = repository.findBySourceEnumAndConditionCodeIn(
            DoctorStationDiagnosisConstants.SOURCE_CLINICAL_V2, codes);
        for (ConditionDefinition entity : rows) {
            String code = StringUtils.trimToNull(entity.getConditionCode());
            String name = StringUtils.trimToNull(entity.getName());
            if (code == null || name == null) {
                continue;
            }
            if (!result.containsKey(code)) {
                result.put(code, name);
            }
        }
        return result;
    }

    /**
     * 按医保码反查国临对照（国临→医保真源：{@code clinical_v2.yb_no}）。
     *
     * <p>同一医保码可被多个国临绑定；返回码/名按国临码排序后英文逗号拼接。
     * 无国临绑定时该医保码不出现在结果中。</p>
     *
     * @param ybNos 医保码集合（通常为医保目录 condition_code）
     * @return 医保码 → 国临码/名称拼接
     */
    public Map<String, ConditionClinicBindingQueryModel> mapClinicBindingsByYbNos(Collection<String> ybNos) {
        Map<String, ConditionClinicBindingQueryModel> result = new LinkedHashMap<>();
        if (ybNos == null || ybNos.isEmpty()) {
            return result;
        }
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (String ybNo : ybNos) {
            if (StringUtils.isNotBlank(ybNo)) {
                codes.add(ybNo.trim());
            }
        }
        if (codes.isEmpty()) {
            return result;
        }
        // Pre：国临目录中 yb_no 命中的行（一对多）
        Map<String, List<ConditionDefinition>> clinicalByYb = new HashMap<>();
        for (ConditionDefinition entity : repository.findBySourceEnumAndYbNoIn(
                DoctorStationDiagnosisConstants.SOURCE_CLINICAL_V2, codes)) {
            if (entity == null || Boolean.TRUE.equals(entity.getIsDeleted())) {
                continue;
            }
            String ybKey = StringUtils.trimToNull(entity.getYbNo());
            if (ybKey == null) {
                continue;
            }
            List<ConditionDefinition> list = clinicalByYb.get(ybKey);
            if (list == null) {
                list = new ArrayList<>();
                clinicalByYb.put(ybKey, list);
            }
            list.add(entity);
        }
        // Asm：同医保码下按国临码排序后逗号拼接
        for (Map.Entry<String, List<ConditionDefinition>> entry : clinicalByYb.entrySet()) {
            List<ConditionDefinition> list = entry.getValue();
            list.sort(Comparator.comparing(
                    e -> {
                        String code = StringUtils.trimToNull(e.getConditionCode());
                        if (code == null) {
                            return "";
                        }
                        return code;
                    },
                    String::compareTo));
            // Asm：码与名一一对应拼接（勿用 Set 去重名称，否则同名多码会少显示）
            List<String> clinicCodes = new ArrayList<>();
            List<String> clinicNames = new ArrayList<>();
            Set<String> seenCodes = new LinkedHashSet<>();
            for (ConditionDefinition clinical : list) {
                String clinicCode = StringUtils.trimToNull(clinical.getConditionCode());
                if (clinicCode == null) {
                    continue;
                }
                // 同码只保留一条
                if (!seenCodes.add(clinicCode)) {
                    continue;
                }
                clinicCodes.add(clinicCode);
                String clinicName = StringUtils.trimToNull(clinical.getName());
                if (clinicName == null) {
                    clinicNames.add("");
                } else {
                    clinicNames.add(clinicName);
                }
            }
            if (clinicCodes.isEmpty()) {
                continue;
            }
            ConditionClinicBindingQueryModel row = new ConditionClinicBindingQueryModel();
            row.setClinicCodes(String.join(",", clinicCodes));
            row.setClinicNames(String.join(",", clinicNames));
            result.put(entry.getKey(), row);
        }
        return result;
    }

    /**
     * 按医保码反查国临目录主键列表（一对多；门诊医保诊断转入住院时展开用）。
     *
     * <p>真源：{@code clinical_v2.yb_no = 医保码}；同码多行按国临 {@code condition_code} 排序去重。</p>
     *
     * @param ybNos 医保码集合
     * @return 医保码 → 国临定义主键列表；无绑定时该码不出现
     */
    public Map<String, List<Long>> mapClinicalDefinitionIdsByYbNos(Collection<String> ybNos) {
        Map<String, List<Long>> result = new LinkedHashMap<>();
        if (ybNos == null || ybNos.isEmpty()) {
            return result;
        }
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (String ybNo : ybNos) {
            if (StringUtils.isNotBlank(ybNo)) {
                codes.add(ybNo.trim());
            }
        }
        if (codes.isEmpty()) {
            return result;
        }
        // Pre：国临目录中 yb_no 命中的行
        Map<String, List<ConditionDefinition>> clinicalByYb = new HashMap<>();
        for (ConditionDefinition entity : repository.findBySourceEnumAndYbNoIn(
                DoctorStationDiagnosisConstants.SOURCE_CLINICAL_V2, codes)) {
            if (entity == null || Boolean.TRUE.equals(entity.getIsDeleted())) {
                continue;
            }
            String ybKey = StringUtils.trimToNull(entity.getYbNo());
            if (ybKey == null) {
                continue;
            }
            List<ConditionDefinition> list = clinicalByYb.get(ybKey);
            if (list == null) {
                list = new ArrayList<>();
                clinicalByYb.put(ybKey, list);
            }
            list.add(entity);
        }
        // Asm：按国临码排序，输出主键列表（同码只保留一条）
        for (Map.Entry<String, List<ConditionDefinition>> entry : clinicalByYb.entrySet()) {
            List<ConditionDefinition> list = entry.getValue();
            list.sort(Comparator.comparing(
                    e -> {
                        String code = StringUtils.trimToNull(e.getConditionCode());
                        if (code == null) {
                            return "";
                        }
                        return code;
                    },
                    String::compareTo));
            List<Long> ids = new ArrayList<>();
            Set<String> seenCodes = new LinkedHashSet<>();
            for (ConditionDefinition clinical : list) {
                if (clinical.getId() == null) {
                    continue;
                }
                String clinicCode = StringUtils.trimToNull(clinical.getConditionCode());
                if (clinicCode == null) {
                    continue;
                }
                if (!seenCodes.add(clinicCode)) {
                    continue;
                }
                ids.add(clinical.getId());
            }
            if (!ids.isEmpty()) {
                result.put(entry.getKey(), ids);
            }
        }
        return result;
    }

    /**
     * 按诊断编码批量推导中西医维度（结算清单预览列）。
     *
     * <p>与医生站一致：目录 {@code source_enum} → {@link DoctorStationDiagnosisConstants#resolveDiagnosisKindFromSource}。
     * 同码多目录时<strong>中医优先</strong>（病/证目录命中即 TCM）。</p>
     *
     * @param conditionCodes 首页/就诊落库诊断编码
     * @return 编码 → {@code WESTERN}/{@code TCM}；无法归类的码不出现
     */
    public Map<String, String> mapDiagnosisKindByConditionCodes(Collection<String> conditionCodes) {
        Map<String, String> result = new LinkedHashMap<>();
        if (conditionCodes == null || conditionCodes.isEmpty()) {
            return result;
        }
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (String code : conditionCodes) {
            if (StringUtils.isNotBlank(code)) {
                codes.add(code.trim());
            }
        }
        if (codes.isEmpty()) {
            return result;
        }
        // Pre：同码可能出现在多目录，先收集每个码下的最优 kind
        Map<String, String> bestKindByCode = new HashMap<>();
        for (ConditionDefinition entity : repository.findByConditionCodeIn(codes)) {
            if (entity == null || Boolean.TRUE.equals(entity.getIsDeleted())) {
                continue;
            }
            String code = StringUtils.trimToNull(entity.getConditionCode());
            if (code == null) {
                continue;
            }
            String kind = DoctorStationDiagnosisConstants.resolveDiagnosisKindFromSource(entity.getSourceEnum());
            if (kind == null) {
                continue;
            }
            String existing = bestKindByCode.get(code);
            if (existing == null) {
                bestKindByCode.put(code, kind);
                continue;
            }
            // 业务分支：已有中医则保留；西医可被中医覆盖
            if (DoctorStationDiagnosisConstants.DIAGNOSIS_KIND_TAG_WESTERN.equals(existing)
                    && DoctorStationDiagnosisConstants.DIAGNOSIS_KIND_TAG_TCM.equals(kind)) {
                bestKindByCode.put(code, kind);
            }
        }
        result.putAll(bestKindByCode);
        return result;
    }

    /**
     * 由目录定义解析病案首页/结算清单应展示的国临与医保口径。
     *
     * <p>国临目录原样；中医主列直接落目录码/名（无双码展示）；西医医保目录反查首个国临绑码；
     * <strong>西医无国临绑码时主列留空</strong>（不把医保码塞进国临列）。</p>
     *
     * @param definition 目录批量行
     * @return 展示口径；definition 为空时 null
     */
    public ClinicalDiagnosisPresentationQueryModel resolveClinicalPresentationFromDefinition(
            ConditionDefinitionBatchItemQueryModel definition) {
        // Gate：无定义无法分流
        if (definition == null) {
            return null;
        }
        String source = StringUtils.trimToNull(definition.getSourceEnum());
        String kind = DoctorStationDiagnosisConstants.resolveDiagnosisKindFromSource(source);
        ClinicalDiagnosisPresentationQueryModel row = new ClinicalDiagnosisPresentationQueryModel();
        row.setDiagnosisKind(kind);
        String conditionCode = StringUtils.trimToNull(definition.getConditionCode());
        String conditionName = StringUtils.trimToNull(definition.getName());
        // 业务分支：国临目录 → 国临列原样
        if (DoctorStationDiagnosisConstants.SOURCE_CLINICAL_V2.equals(source)) {
            row.setClinicCode(conditionCode);
            row.setClinicName(conditionName);
            row.setClinicalDefinitionId(definition.getId());
            this.fillWesternYbFromClinicalCode(row, conditionCode);
            return row;
        }
        // 业务分支：中医 → 无国临/医保双码，名称/编码正常落主列；yb 同值供上传出站
        if (source != null && DoctorStationDiagnosisConstants.CONDITION_SOURCE_CODES_TCM.contains(source)) {
            row.setClinicCode(conditionCode);
            row.setClinicName(conditionName);
            row.setYbCode(conditionCode);
            row.setYbName(conditionName);
            return row;
        }
        // 业务分支：西医医保目录 → 反查国临；无绑码则国临列空
        String ybKey = DoctorStationDiagnosisConstants.resolveWesternInsuranceYbKey(
                source, definition.getYbNo(), definition.getConditionCode());
        // 业务分支：有显式 yb_no 用其作反查键，否则用目录主码
        if (ybKey != null) {
            row.setYbCode(ybKey);
        } else {
            row.setYbCode(conditionCode);
        }
        row.setYbName(conditionName);
        Long clinicalId = this.pickFirstClinicalDefinitionId(ybKey);
        // 业务分支：反查到国临则填主列
        if (clinicalId != null) {
            ConditionDefinitionBatchItemQueryModel clinical = this.requireBatchItemById(clinicalId);
            row.setClinicCode(StringUtils.trimToNull(clinical.getConditionCode()));
            row.setClinicName(StringUtils.trimToNull(clinical.getName()));
            row.setClinicalDefinitionId(clinicalId);
        }
        return row;
    }

    /**
     * 按首页/结算清单已落库诊断编码批量解析国临+医保展示口径。
     *
     * @param storedCodes 落库诊断编码集合
     * @return 落库码 → 展示口径
     */
    public Map<String, ClinicalDiagnosisPresentationQueryModel> mapClinicalPresentationByStoredCodes(
            Collection<String> storedCodes) {
        return this.mapClinicalPresentationByStoredCodes(storedCodes, null);
    }

    /**
     * 按落库编码批量解析国临+医保展示口径（可带就诊诊断来源提示）。
     *
     * <p>同码既在国临又在医保时：若 {@code sourceHintByCode} 标明西医医保来源，
     * 则走医保路径（反查国临；无绑码国临列空），禁止把医保码当作国临展示。</p>
     *
     * @param storedCodes       落库诊断编码集合
     * @param sourceHintByCode  编码 → 目录 {@code source_enum}；可空
     * @return 落库码 → 展示口径
     */
    public Map<String, ClinicalDiagnosisPresentationQueryModel> mapClinicalPresentationByStoredCodes(
            Collection<String> storedCodes, Map<String, String> sourceHintByCode) {
        Map<String, ClinicalDiagnosisPresentationQueryModel> result = new LinkedHashMap<>();
        if (storedCodes == null || storedCodes.isEmpty()) {
            return result;
        }
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (String raw : storedCodes) {
            String code = StringUtils.trimToNull(raw);
            if (code != null) {
                codes.add(code);
            }
        }
        if (codes.isEmpty()) {
            return result;
        }
        Map<String, ConditionDefinition> clinicalByCode = new LinkedHashMap<>();
        for (ConditionDefinition entity : repository.findBySourceEnumAndConditionCodeIn(
                DoctorStationDiagnosisConstants.SOURCE_CLINICAL_V2, codes)) {
            if (entity == null || Boolean.TRUE.equals(entity.getIsDeleted())) {
                continue;
            }
            String clinicKey = StringUtils.trimToNull(entity.getConditionCode());
            if (clinicKey != null && !clinicalByCode.containsKey(clinicKey)) {
                clinicalByCode.put(clinicKey, entity);
            }
        }
        Map<String, String> kindByCode = this.mapDiagnosisKindByConditionCodes(codes);
        Map<String, ConditionYbMappingQueryModel> ybByClinic =
                this.mapYbMappingByClinicCodes(clinicalByCode.keySet());
        Set<String> ybKeysNeedBinding = new LinkedHashSet<>();
        for (String code : codes) {
            String hint = null;
            if (sourceHintByCode != null) {
                hint = sourceHintByCode.get(code);
            }
            boolean forceInsurance = hint != null
                    && DoctorStationDiagnosisConstants.CONDITION_SOURCE_CODES_WESTERN.contains(hint);
            // 确认为国临且无医保来源强制覆盖 → 不进反查集合
            if (clinicalByCode.containsKey(code) && !forceInsurance) {
                continue;
            }
            String kind = kindByCode.get(code);
            if (DoctorStationDiagnosisConstants.DIAGNOSIS_KIND_TAG_TCM.equals(kind)) {
                continue;
            }
            ybKeysNeedBinding.add(code);
        }
        Map<String, List<Long>> clinicalIdsByYb =
                this.mapClinicalDefinitionIdsByYbNos(ybKeysNeedBinding);
        Map<Long, ConditionDefinitionBatchItemQueryModel> clinicalById = new HashMap<>();
        Set<Long> clinicalIdSet = new LinkedHashSet<>();
        for (List<Long> ids : clinicalIdsByYb.values()) {
            if (ids != null) {
                clinicalIdSet.addAll(ids);
            }
        }
        if (!clinicalIdSet.isEmpty()) {
            for (ConditionDefinitionBatchItemQueryModel item
                    : this.getConditionDefinitionListByIds(new ArrayList<>(clinicalIdSet))) {
                if (item != null && item.getId() != null) {
                    clinicalById.put(item.getId(), item);
                }
            }
        }
        // Pre：反查出国临码后补拉国临→医保，供医保名称回填
        Set<String> reverseClinicCodes = new LinkedHashSet<>();
        for (ConditionDefinitionBatchItemQueryModel item : clinicalById.values()) {
            String clinicCode = StringUtils.trimToNull(item.getConditionCode());
            if (clinicCode != null && !ybByClinic.containsKey(clinicCode)) {
                reverseClinicCodes.add(clinicCode);
            }
        }
        if (!reverseClinicCodes.isEmpty()) {
            ybByClinic.putAll(this.mapYbMappingByClinicCodes(reverseClinicCodes));
        }
        for (String code : codes) {
            ClinicalDiagnosisPresentationQueryModel row = new ClinicalDiagnosisPresentationQueryModel();
            row.setDiagnosisKind(kindByCode.get(code));
            String hint = null;
            if (sourceHintByCode != null) {
                hint = sourceHintByCode.get(code);
            }
            boolean forceInsurance = hint != null
                    && DoctorStationDiagnosisConstants.CONDITION_SOURCE_CODES_WESTERN.contains(hint);
            if (hint != null) {
                String hintKind = DoctorStationDiagnosisConstants.resolveDiagnosisKindFromSource(hint);
                if (hintKind != null) {
                    row.setDiagnosisKind(hintKind);
                }
            }
            ConditionDefinition clinicalEntity = clinicalByCode.get(code);
            // 业务分支：已是国临码，且就诊来源未强制为西医医保
            if (clinicalEntity != null && !forceInsurance) {
                row.setClinicCode(code);
                row.setClinicName(StringUtils.trimToNull(clinicalEntity.getName()));
                row.setClinicalDefinitionId(clinicalEntity.getId());
                this.fillWesternYbFromMappingRow(row, ybByClinic.get(code));
                result.put(code, row);
                continue;
            }
            // 业务分支：中医目录码 → 主列正常显示编码/名称（无国临医保双码）
            if (DoctorStationDiagnosisConstants.DIAGNOSIS_KIND_TAG_TCM.equals(row.getDiagnosisKind())) {
                row.setClinicCode(code);
                String tcmName = this.lookupNameByConditionCode(code);
                row.setClinicName(tcmName);
                row.setYbCode(code);
                row.setYbName(tcmName);
                result.put(code, row);
                continue;
            }
            // 业务分支：西医医保（含同码强制覆盖）→ 医保列落原码；有绑码才填国临列
            row.setYbCode(code);
            row.setYbName(this.lookupNameByConditionCode(code));
            Long clinicalId = this.pickFirstClinicalDefinitionId(clinicalIdsByYb.get(code));
            if (clinicalId != null) {
                ConditionDefinitionBatchItemQueryModel clinical = clinicalById.get(clinicalId);
                if (clinical != null) {
                    row.setClinicCode(StringUtils.trimToNull(clinical.getConditionCode()));
                    row.setClinicName(StringUtils.trimToNull(clinical.getName()));
                    row.setClinicalDefinitionId(clinicalId);
                    ConditionYbMappingQueryModel mapped = ybByClinic.get(row.getClinicCode());
                    if (mapped != null) {
                        this.fillWesternYbFromMappingRow(row, mapped);
                        row.setYbCode(code);
                        if (StringUtils.trimToNull(row.getYbName()) == null) {
                            row.setYbName(this.lookupNameByConditionCode(code));
                        }
                    }
                }
            }
            result.put(code, row);
        }
        return result;
    }

    /**
     * 按国临码批量解析医保诊断编码（4401 上传用）。
     *
     * <p>仅认「国临目录绑医保」真源（见 {@link #mapYbMappingByClinicCodes}）；
     * 未对码的国临不出现在结果中。</p>
     *
     * @param clinicCodes 国临码集合
     * @return 国临码 → 医保码；无法映射的码不出现在结果中
     */
    public Map<String, String> mapYbNoByClinicCodes(Collection<String> clinicCodes) {
        Map<String, String> result = new LinkedHashMap<>();
        Map<String, ConditionYbMappingQueryModel> full = this.mapYbMappingByClinicCodes(clinicCodes);
        for (Map.Entry<String, ConditionYbMappingQueryModel> entry : full.entrySet()) {
            ConditionYbMappingQueryModel row = entry.getValue();
            if (row != null && StringUtils.isNotBlank(row.getYbNo())) {
                result.put(entry.getKey(), row.getYbNo().trim());
            }
        }
        return result;
    }

    /**
     * 按国临码批量解析医保编码与名称（结算清单回显 / 上传 diag_name）。
     *
     * <p>业务口径（现阶段只做国临→医保）：</p>
     * <ol>
     *   <li>主体：{@code clinical_v2.condition_code=国临}，已写 {@code yb_no} 视为已对码；</li>
     *   <li>补齐：非国临目录 {@code clinic_code=国临} 且已有医保主码（对侧同步缺口）；</li>
     *   <li><b>禁止</b>：用「医保目录 condition_code 与国临同码」冒充已对码（导入时常把 yb_no=自身编码）。</li>
     * </ol>
     * <p>凡目录中存在国临行，即使未对码也会返回条目（带 {@code clinicalDefinitionId} 供前端对码）。</p>
     *
     * @param clinicCodes 国临码集合
     * @return 国临码 → 映射行（可能仅有 clinicalDefinitionId）
     */
    public Map<String, ConditionYbMappingQueryModel> mapYbMappingByClinicCodes(Collection<String> clinicCodes) {
        Map<String, ConditionYbMappingQueryModel> result = new LinkedHashMap<>();
        if (clinicCodes == null || clinicCodes.isEmpty()) {
            return result;
        }
        // Pre：去重保序，空码跳过
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (String code : clinicCodes) {
            if (StringUtils.isNotBlank(code)) {
                codes.add(code.trim());
            }
        }
        if (codes.isEmpty()) {
            return result;
        }
        // Pre：国临目录主体（对码写回主键）
        Map<String, ConditionDefinition> clinicalByCode = new LinkedHashMap<>();
        for (ConditionDefinition entity : repository.findBySourceEnumAndConditionCodeIn(
                DoctorStationDiagnosisConstants.SOURCE_CLINICAL_V2, codes)) {
            // 跳过软删；同码多行保留先命中
            if (entity == null || Boolean.TRUE.equals(entity.getIsDeleted())) {
                continue;
            }
            String clinicKey = StringUtils.trimToNull(entity.getConditionCode());
            if (clinicKey == null) {
                continue;
            }
            if (!clinicalByCode.containsKey(clinicKey)) {
                clinicalByCode.put(clinicKey, entity);
            }
        }
        // Pre：医保侧已挂国临码（对侧同步缺口补齐；排除 clinical_v2 自身）
        Map<String, ConditionDefinition> peerByClinic = new HashMap<>();
        for (ConditionDefinition entity : repository.findByClinicCodeIn(codes)) {
            putBestPeerYbCatalog(peerByClinic, entity);
        }
        // Asm：先落国临主键与医保码，名称稍后批量回填
        Set<String> ybNosNeedName = new LinkedHashSet<>();
        for (String clinic : codes) {
            ConditionDefinition clinical = clinicalByCode.get(clinic);
            ConditionDefinition peer = peerByClinic.get(clinic);
            // 无国临行且无对侧挂接 → 不产出（调用方按未对码处理）
            if (clinical == null && peer == null) {
                continue;
            }
            ConditionYbMappingQueryModel row = new ConditionYbMappingQueryModel();
            if (clinical != null) {
                row.setClinicalDefinitionId(clinical.getId());
            }
            String ybNo = null;
            String ybName = null;
            // 业务分支：优先国临行已绑的 yb_no（国临→医保真源）
            if (clinical != null) {
                ybNo = StringUtils.trimToNull(clinical.getYbNo());
            }
            // 业务分支：国临尚未写 yb_no 时，用医保目录 clinic_code 挂接补齐
            if (ybNo == null && peer != null) {
                ybNo = StringUtils.trimToNull(peer.getYbNo());
                if (ybNo == null) {
                    ybNo = StringUtils.trimToNull(peer.getConditionCode());
                }
                ybName = StringUtils.trimToNull(peer.getName());
            } else if (ybNo != null && peer != null) {
                // 国临已绑医保：对侧目录同码名称直接可用（对码保存后常见）
                String peerYb = StringUtils.trimToNull(peer.getYbNo());
                if (peerYb == null) {
                    peerYb = StringUtils.trimToNull(peer.getConditionCode());
                }
                if (ybNo.equals(peerYb)) {
                    ybName = StringUtils.trimToNull(peer.getName());
                }
            }
            if (ybNo != null) {
                row.setYbNo(ybNo);
                if (ybName != null) {
                    row.setYbName(ybName);
                } else {
                    ybNosNeedName.add(ybNo);
                }
            }
            result.put(clinic, row);
        }
        // Asm：仍缺名称的，按医保码反查非国临目录名称
        this.fillYbNamesFromYbCatalog(result, ybNosNeedName);
        return result;
    }

    /**
     * 在「国临键 → 医保侧目录行」中择优：排除国临自身；须能解析出医保主码；general_dx 优先。
     *
     * @param peerByClinic 累计择优表
     * @param entity       候选目录行
     */
    private void putBestPeerYbCatalog(
            Map<String, ConditionDefinition> peerByClinic, ConditionDefinition entity) {
        if (entity == null || Boolean.TRUE.equals(entity.getIsDeleted())) {
            return;
        }
        // Gate：国临行 clinic_code=自身编码，不能当作「对侧已绑」
        if (DoctorStationDiagnosisConstants.SOURCE_CLINICAL_V2.equals(entity.getSourceEnum())) {
            return;
        }
        String clinicKey = StringUtils.trimToNull(entity.getClinicCode());
        if (clinicKey == null) {
            return;
        }
        String ybNo = StringUtils.trimToNull(entity.getYbNo());
        if (ybNo == null) {
            ybNo = StringUtils.trimToNull(entity.getConditionCode());
        }
        // Gate：无医保主码则不可用于补齐
        if (ybNo == null) {
            return;
        }
        ConditionDefinition existing = peerByClinic.get(clinicKey);
        if (existing == null) {
            peerByClinic.put(clinicKey, entity);
            return;
        }
        boolean existingIsGeneral =
                DoctorStationDiagnosisConstants.SOURCE_GENERAL_DX.equals(existing.getSourceEnum());
        boolean currentIsGeneral =
                DoctorStationDiagnosisConstants.SOURCE_GENERAL_DX.equals(entity.getSourceEnum());
        // 业务分支：已有 general_dx 则保留；否则用当前 general_dx 替换
        if (!existingIsGeneral && currentIsGeneral) {
            peerByClinic.put(clinicKey, entity);
        }
    }

    /**
     * 按医保码批量回填尚未有 ybName 的映射行。
     *
     * <p>优先疾病与诊断目录（general_dx）同 {@code condition_code}；
     * 再回退其它非国临目录同码名称。</p>
     *
     * @param mapping       国临→映射行
     * @param ybNosNeedName 待查医保码
     */
    private void fillYbNamesFromYbCatalog(
            Map<String, ConditionYbMappingQueryModel> mapping, Set<String> ybNosNeedName) {
        if (mapping == null || mapping.isEmpty() || ybNosNeedName == null || ybNosNeedName.isEmpty()) {
            return;
        }
        Map<String, String> nameByYb = this.mapNamesByYbNos(ybNosNeedName);
        for (ConditionYbMappingQueryModel row : mapping.values()) {
            if (row == null || StringUtils.isNotBlank(row.getYbName())) {
                continue;
            }
            String ybNo = StringUtils.trimToNull(row.getYbNo());
            if (ybNo == null) {
                continue;
            }
            String name = nameByYb.get(ybNo);
            if (name != null) {
                row.setYbName(name);
            }
        }
    }

    /**
     * 按医保诊断编码批量解析目录名称（疾病目录列表 / 结算清单回显）。
     *
     * <p>优先 {@code general_dx} 同码名称；仍缺则其它非国临目录同码。无对照码不出现在结果中。</p>
     *
     * @param ybNos 医保码集合
     * @return 医保码 → 医保诊断名称
     */
    public Map<String, String> mapNamesByYbNos(Collection<String> ybNos) {
        Map<String, String> nameByYb = new HashMap<>();
        if (ybNos == null || ybNos.isEmpty()) {
            return nameByYb;
        }
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (String ybNo : ybNos) {
            if (StringUtils.isNotBlank(ybNo)) {
                codes.add(ybNo.trim());
            }
        }
        if (codes.isEmpty()) {
            return nameByYb;
        }
        // Pre：先扫 general_dx（对码选码主目录）
        for (ConditionDefinition entity : repository.findBySourceEnumAndConditionCodeIn(
                DoctorStationDiagnosisConstants.SOURCE_GENERAL_DX, codes)) {
            putYbCatalogName(nameByYb, entity, true);
        }
        // Pre：仍缺则扫全目录同码（排除国临）
        Set<String> stillNeed = new LinkedHashSet<>();
        for (String ybNo : codes) {
            if (!nameByYb.containsKey(ybNo)) {
                stillNeed.add(ybNo);
            }
        }
        if (!stillNeed.isEmpty()) {
            for (ConditionDefinition entity : repository.findByConditionCodeIn(stillNeed)) {
                putYbCatalogName(nameByYb, entity, false);
            }
        }
        return nameByYb;
    }

    /**
     * 将非国临目录行的名称写入「医保码 → 名称」；general_dx 可覆盖已有。
     *
     * @param nameByYb     累计表
     * @param entity       目录行
     * @param preferWrite  true=强制写入（general_dx）；false=仅空缺时写入
     */
    private void putYbCatalogName(
            Map<String, String> nameByYb, ConditionDefinition entity, boolean preferWrite) {
        if (entity == null || Boolean.TRUE.equals(entity.getIsDeleted())) {
            return;
        }
        // Gate：国临目录名称不是医保诊断名
        if (DoctorStationDiagnosisConstants.SOURCE_CLINICAL_V2.equals(entity.getSourceEnum())) {
            return;
        }
        String code = StringUtils.trimToNull(entity.getConditionCode());
        String name = StringUtils.trimToNull(entity.getName());
        if (code == null || name == null) {
            return;
        }
        if (preferWrite || !nameByYb.containsKey(code)) {
            nameByYb.put(code, name);
        }
        // 若 yb_no 与 condition_code 不同，一并挂到 yb_no 键上
        String ybNo = StringUtils.trimToNull(entity.getYbNo());
        if (ybNo != null && !ybNo.equals(code)) {
            if (preferWrite || !nameByYb.containsKey(ybNo)) {
                nameByYb.put(ybNo, name);
            }
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
        String statusEnum = null;
        if (query != null && StringUtils.isNotBlank(query.getStatusEnum())) {
            statusEnum = query.getStatusEnum().trim();
        }
        Specification<ConditionDefinition> specification = this.buildDiseasePageSpec(keyword, sourceEnum, statusEnum);
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
     * @param statusEnum 发布状态编码；空白时不按状态过滤
     * @return 分页 Specification
     */
    private Specification<ConditionDefinition> buildDiseasePageSpec(
        String keyword,
        String sourceEnum,
        String statusEnum) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (StringUtils.isNotBlank(sourceEnum)) {
                predicates.add(cb.equal(root.get(FieldNameConstants.SOURCE_ENUM), sourceEnum.trim()));
            }
            String normalizedStatus = this.normalizePublicationStatusFilter(statusEnum);
            if (normalizedStatus != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), normalizedStatus));
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

    /**
     * 规范化目录发布状态筛选：仅 ACTIVE/INACTIVE；其余视为不筛选。
     *
     * @param statusEnum 请求状态码
     * @return ACTIVE/INACTIVE；无法识别时 null
     */
    private String normalizePublicationStatusFilter(String statusEnum) {
        if (StringUtils.isEmpty(statusEnum)) {
            return null;
        }
        String trimmed = statusEnum.trim();
        if (PublicationStatus.ACTIVE.getCode().equals(trimmed)) {
            return PublicationStatus.ACTIVE.getCode();
        }
        if (PublicationStatus.INACTIVE.getCode().equals(trimmed)) {
            return PublicationStatus.INACTIVE.getCode();
        }
        // 未知状态码：不收窄，避免误滤成空目录
        return null;
    }

    /**
     * 按医保主码取排序后首个国临目录主键。
     *
     * @param ybKey 医保主码
     * @return 国临定义主键；无绑码时 null
     */
    private Long pickFirstClinicalDefinitionId(String ybKey) {
        String key = StringUtils.trimToNull(ybKey);
        if (key == null) {
            return null;
        }
        List<Long> ids = this.mapClinicalDefinitionIdsByYbNos(List.of(key)).get(key);
        return this.pickFirstClinicalDefinitionId(ids);
    }

    /**
     * 从国临主键列表取首条（已按国临码排序）。
     *
     * @param clinicalIds 国临定义主键列表
     * @return 首条主键；空列表时 null
     */
    private Long pickFirstClinicalDefinitionId(List<Long> clinicalIds) {
        if (clinicalIds == null || clinicalIds.isEmpty()) {
            return null;
        }
        return clinicalIds.get(0);
    }

    /**
     * 按主键加载目录批量行；缺失时抛业务异常。
     *
     * @param definitionId 目录主键
     * @return 批量行
     */
    private ConditionDefinitionBatchItemQueryModel requireBatchItemById(Long definitionId) {
        if (definitionId == null) {
            throw new BusinessException(
                    ErrorCodeConstants.CONDITION_DEFINITION_INVALID_ARGUMENT, "诊断目录主键不能为空");
        }
        List<ConditionDefinitionBatchItemQueryModel> list =
                this.getConditionDefinitionListByIds(List.of(definitionId));
        if (list == null || list.isEmpty() || list.get(0) == null) {
            throw new BusinessException(
                    ErrorCodeConstants.CONDITION_DEFINITION_NOT_FOUND, "诊断目录不存在：" + definitionId);
        }
        return list.get(0);
    }

    /**
     * 按编码取首个未删目录名称（展示回落用）。
     *
     * @param conditionCode 目录编码
     * @return 名称或 null
     */
    private String lookupNameByConditionCode(String conditionCode) {
        String code = StringUtils.trimToNull(conditionCode);
        if (code == null) {
            return null;
        }
        for (ConditionDefinition entity : repository.findByConditionCodeIn(Set.of(code))) {
            if (entity == null || Boolean.TRUE.equals(entity.getIsDeleted())) {
                continue;
            }
            return StringUtils.trimToNull(entity.getName());
        }
        return null;
    }

    /**
     * 国临码 → 填充西医医保列（目录对照）。
     *
     * @param row         展示行
     * @param clinicCode  国临码
     */
    private void fillWesternYbFromClinicalCode(
            ClinicalDiagnosisPresentationQueryModel row, String clinicCode) {
        String code = StringUtils.trimToNull(clinicCode);
        if (code == null || row == null) {
            return;
        }
        this.fillWesternYbFromMappingRow(row, this.mapYbMappingByClinicCodes(List.of(code)).get(code));
    }

    /**
     * 由国临→医保映射行填充西医医保列。
     *
     * @param row     展示行
     * @param mapping 映射行
     */
    private void fillWesternYbFromMappingRow(
            ClinicalDiagnosisPresentationQueryModel row, ConditionYbMappingQueryModel mapping) {
        if (row == null || mapping == null) {
            return;
        }
        row.setYbCode(StringUtils.trimToNull(mapping.getYbNo()));
        row.setYbName(StringUtils.trimToNull(mapping.getYbName()));
    }
}
