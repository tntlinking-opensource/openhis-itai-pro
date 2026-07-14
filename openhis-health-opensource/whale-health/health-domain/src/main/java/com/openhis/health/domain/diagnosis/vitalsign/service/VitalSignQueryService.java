/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.diagnosis.vitalsign.service;

import com.openhis.health.domain.diagnosis.vitalsign.entity.VitalSign;
import com.openhis.health.domain.diagnosis.vitalsign.entity.VitalSignDetail;
import com.openhis.health.domain.diagnosis.vitalsign.queries.VitalSignEncounterQuery;
import com.openhis.health.domain.diagnosis.vitalsign.queries.VitalSignPatientDateRangeQuery;
import com.openhis.health.domain.diagnosis.vitalsign.queries.VitalSignTemperatureSeriesQuery;
import com.openhis.health.domain.diagnosis.vitalsign.queries.model.VitalSignDetailRowQueryModel;
import com.openhis.health.domain.diagnosis.vitalsign.queries.model.VitalSignHeaderQueryModel;
import com.openhis.health.domain.diagnosis.vitalsign.queries.model.VitalSignLatestItemQueryModel;
import com.openhis.health.domain.diagnosis.vitalsign.queries.model.VitalSignTemperaturePointQueryModel;
import com.openhis.health.domain.diagnosis.vitalsign.queries.model.VitalSignWithDetailsQueryModel;
import com.openhis.health.domain.diagnosis.vitalsign.repository.VitalSignDetailRepository;
import com.openhis.health.domain.diagnosis.vitalsign.repository.VitalSignRepository;
import com.openhis.health.domain.diagnosis.vitalsign.support.VitalSignDetailSupport;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.VitalSignConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 生命体征读侧查询服务（DM-DIA-002 v2.1 §6.2）。
 *
 * <p>聚合边界：仅本聚合 {@link VitalSignRepository} / {@link VitalSignDetailRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class VitalSignQueryService {

    private final VitalSignRepository headerRepository;
    private final VitalSignDetailRepository detailRepository;

    /**
     * 按就诊查询主表列表（TC-DIA-VS-Q-001）。
     *
     * @param query 查询条件
     * @return 主表读模型，recordedAt 降序
     */
    public List<VitalSignHeaderQueryModel> listHeadersByEncounterId(VitalSignEncounterQuery query) {
        if (query == null || query.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.VITAL_SIGN_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        Specification<VitalSign> spec = buildHeaderSpecification(query);
        List<VitalSign> rows = headerRepository.findAll(
            spec, Sort.by(Sort.Direction.DESC, FieldNameConstants.RECORDED_AT));
        List<VitalSignHeaderQueryModel> result = new ArrayList<>();
        for (VitalSign row : rows) {
            result.add(toHeaderModel(row));
        }
        return result;
    }

    /**
     * 体温单曲线点（明细 TEMP，TC-DIA-VS-Q-002）。
     *
     * @param query 查询条件
     * @return 曲线点列表，recordedAt 升序
     */
    public List<VitalSignTemperaturePointQueryModel> listTemperaturePointsByEncounterId(
        VitalSignTemperatureSeriesQuery query) {
        if (query == null || query.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.VITAL_SIGN_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        VitalSignEncounterQuery headerQuery = new VitalSignEncounterQuery();
        headerQuery.setEncounterId(query.getEncounterId());
        headerQuery.setTypeEnum(query.getTypeEnum());
        headerQuery.setRecordedAtFrom(query.getRecordedAtFrom());
        headerQuery.setRecordedAtTo(query.getRecordedAtTo());
        Specification<VitalSign> spec = buildHeaderSpecification(headerQuery);
        List<VitalSign> rows = headerRepository.findAll(
            spec, Sort.by(Sort.Direction.ASC, FieldNameConstants.RECORDED_AT));
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> headerIds = new ArrayList<>();
        Map<Long, VitalSign> headerMap = new HashMap<>();
        for (VitalSign row : rows) {
            headerIds.add(row.getId());
            headerMap.put(row.getId(), row);
        }
        List<VitalSignDetail> details = detailRepository.findAllByVitalSignIdInAndIsDeletedFalse(headerIds);
        List<VitalSignTemperaturePointQueryModel> result = new ArrayList<>();
        for (VitalSignDetail detail : details) {
            if (!VitalSignConstants.TEMP.equals(detail.getItemCode())) {
                continue;
            }
            VitalSign header = headerMap.get(detail.getVitalSignId());
            if (header == null || trimToNull(detail.getItemValue()) == null) {
                continue;
            }
            VitalSignTemperaturePointQueryModel model = new VitalSignTemperaturePointQueryModel();
            model.setId(header.getId());
            model.setRecordedAt(header.getRecordedAt());
            model.setTemperature(detail.getItemValue());
            model.setTypeEnum(header.getTypeEnum());
            result.add(model);
        }
        result.sort(Comparator.comparing(VitalSignTemperaturePointQueryModel::getRecordedAt));
        return result;
    }

    /**
     * 按主表 id 加载单据及明细（TC-DIA-VS-Q-003）。
     *
     * @param vitalSignId 主表 id
     * @return 组合读模型
     */
    public VitalSignWithDetailsQueryModel getWithDetailsById(Long vitalSignId) {
        if (vitalSignId == null) {
            throw new BusinessException(ErrorCodeConstants.VITAL_SIGN_INVALID_ARGUMENT, "主表标识不能为空");
        }
        VitalSign header = headerRepository.findByIdAndIsDeletedFalse(vitalSignId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.VITAL_SIGN_NOT_FOUND, "生命体征单据不存在"));
        VitalSignWithDetailsQueryModel result = new VitalSignWithDetailsQueryModel();
        result.setHeader(toHeaderModel(header));
        List<VitalSignDetail> details =
            detailRepository.findAllByVitalSignIdAndIsDeletedFalseOrderByTermCodeAscItemCodeAsc(vitalSignId);
        for (VitalSignDetail detail : details) {
            result.getDetails().add(toDetailModel(detail, header));
        }
        return result;
    }

    /**
     * 按就诊查询明细行（join 主表，TC-DIA-VS-Q-004）。
     *
     * @param query 查询条件
     * @return 明细读模型
     */
    public List<VitalSignDetailRowQueryModel> listDetailsByEncounterId(VitalSignEncounterQuery query) {
        List<VitalSignHeaderQueryModel> headers = listHeadersByEncounterId(query);
        if (headers.isEmpty()) {
            return List.of();
        }
        List<Long> headerIds = new ArrayList<>();
        Map<Long, VitalSignHeaderQueryModel> headerById = new HashMap<>();
        for (VitalSignHeaderQueryModel header : headers) {
            headerIds.add(header.getId());
            headerById.put(header.getId(), header);
        }
        List<VitalSignDetail> details = detailRepository.findAllByVitalSignIdInAndIsDeletedFalse(headerIds);
        List<VitalSignDetailRowQueryModel> result = new ArrayList<>();
        for (VitalSignDetail detail : details) {
            // 业务分支：可选按字典项 code 过滤明细行
            if (query.getItemCodes() != null && !query.getItemCodes().isEmpty()) {
                if (!query.getItemCodes().contains(detail.getItemCode())) {
                    continue;
                }
            }
            VitalSignHeaderQueryModel header = headerById.get(detail.getVitalSignId());
            // 业务分支：主表已不在结果集时跳过孤儿明细
            if (header == null) {
                continue;
            }
            VitalSignDetailRowQueryModel model = new VitalSignDetailRowQueryModel();
            model.setId(detail.getId());
            model.setVitalSignId(detail.getVitalSignId());
            model.setRecordedAt(header.getRecordedAt());
            model.setTypeEnum(header.getTypeEnum());
            model.setTermCode(detail.getTermCode());
            model.setItemCode(detail.getItemCode());
            model.setItemValue(detail.getItemValue());
            model.setUnits(detail.getUnits());
            model.setRemark(detail.getRemark());
            result.add(model);
        }
        result.sort(Comparator.comparing(VitalSignDetailRowQueryModel::getRecordedAt)
            .thenComparing(VitalSignDetailRowQueryModel::getItemCode, Comparator.nullsLast(String::compareTo)));
        return result;
    }

    /**
     * 按患者与时间区间查主表（TC-DIA-VS-Q-005）。
     *
     * @param query 患者 + 时间区间
     * @return 主表读模型
     */
    public List<VitalSignHeaderQueryModel> listHeadersByPatientAndDateRange(VitalSignPatientDateRangeQuery query) {
        if (query == null || query.getPatientId() == null || query.getFrom() == null || query.getTo() == null) {
            throw new BusinessException(ErrorCodeConstants.VITAL_SIGN_INVALID_ARGUMENT, "患者与时间区间不能为空");
        }
        List<VitalSign> rows = headerRepository.findAllByPatientIdAndRecordedAtBetweenAndIsDeletedFalseOrderByRecordedAtDesc(
            query.getPatientId(), query.getFrom(), query.getTo());
        List<VitalSignHeaderQueryModel> result = new ArrayList<>();
        for (VitalSign row : rows) {
            result.add(toHeaderModel(row));
        }
        return result;
    }

    /**
     * 查询就诊最新体征摘要：各字典项取明细分组最新（TC-DIA-VS-Q-006）。
     *
     * @param encounterId 就诊 id
     * @return 摘要项列表
     */
    public List<VitalSignLatestItemQueryModel> getLatestByEncounterId(Long encounterId) {
        if (encounterId == null) {
            throw new BusinessException(ErrorCodeConstants.VITAL_SIGN_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        List<VitalSignLatestItemQueryModel> result = new ArrayList<>();

        List<VitalSign> headers = headerRepository.findAllByEncounterIdAndIsDeletedFalseOrderByRecordedAtDesc(encounterId);
        List<Long> headerIds = new ArrayList<>();
        Map<Long, VitalSign> headerMap = new HashMap<>();
        for (VitalSign header : headers) {
            headerIds.add(header.getId());
            headerMap.put(header.getId(), header);
        }
        if (headerIds.isEmpty()) {
            return result;
        }
        List<VitalSignDetail> details = detailRepository.findAllByVitalSignIdInAndIsDeletedFalse(headerIds);
        Map<String, VitalSignDetailRowQueryModel> latestByKey = new HashMap<>();
        for (VitalSignDetail detail : details) {
            VitalSign header = headerMap.get(detail.getVitalSignId());
            if (header == null) {
                continue;
            }
            VitalSignDetailRowQueryModel candidate = toDetailModel(detail, header);
            String naturalKey = VitalSignDetailSupport.buildNaturalKey(detail);
            VitalSignDetailRowQueryModel current = latestByKey.get(naturalKey);
            // 业务分支：同自然键取 recorded_at 更晚的主表时点
            if (current == null || candidate.getRecordedAt().isAfter(current.getRecordedAt())) {
                latestByKey.put(naturalKey, candidate);
            }
        }
        List<VitalSignDetailRowQueryModel> latestRows = new ArrayList<>(latestByKey.values());
        latestRows.sort(Comparator.comparing(VitalSignDetailRowQueryModel::getItemCode,
            Comparator.nullsLast(String::compareTo)));
        for (VitalSignDetailRowQueryModel row : latestRows) {
            VitalSignLatestItemQueryModel model = new VitalSignLatestItemQueryModel();
            model.setTermCode(row.getTermCode());
            model.setItemCode(row.getItemCode());
            model.setItemValue(row.getItemValue());
            model.setUnits(row.getUnits());
            model.setRecordedAt(row.getRecordedAt());
            result.add(model);
        }
        return result;
    }

    /**
     * 判断就诊在某记录时点是否存在有效主表。
     *
     * @param encounterId 就诊 id
     * @param recordedAt  记录时点
     * @param babyNo      婴儿序号（可空）
     * @return 是否存在
     */
    public boolean existsAtRecordedTime(Long encounterId, LocalDateTime recordedAt, Integer babyNo) {
        if (encounterId == null || recordedAt == null) {
            throw new BusinessException(ErrorCodeConstants.VITAL_SIGN_INVALID_ARGUMENT, "就诊与记录时点不能为空");
        }
        if (babyNo == null) {
            return headerRepository.existsByEncounterIdAndRecordedAtAndIsDeletedFalse(encounterId, recordedAt);
        }
        // 业务分支：带婴儿序号时须比对归一化后的 baby_no
        Integer normalizedBabyNo = VitalSign.normalizeBabyNo(babyNo);
        List<VitalSign> rows = headerRepository.findAllByEncounterIdAndRecordedAtAndIsDeletedFalse(encounterId, recordedAt);
        for (VitalSign row : rows) {
            if (Objects.equals(VitalSign.normalizeBabyNo(row.getBabyNo()), normalizedBabyNo)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 体征主表分页/列表查询谓词：就诊、记录时间窗、类型与婴儿序号。
     *
     * @param query 就诊侧查询条件
     * @return JPA Specification
     */
    private static Specification<VitalSign> buildHeaderSpecification(VitalSignEncounterQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            preds.add(cb.equal(root.get(FieldNameConstants.ENCOUNTER_ID), query.getEncounterId()));
            if (query.getRecordedAtFrom() != null) {
                preds.add(cb.greaterThanOrEqualTo(root.get(FieldNameConstants.RECORDED_AT), query.getRecordedAtFrom()));
            }
            if (query.getRecordedAtTo() != null) {
                preds.add(cb.lessThanOrEqualTo(root.get(FieldNameConstants.RECORDED_AT), query.getRecordedAtTo()));
            }
            if (StringUtils.isNotEmpty(query.getTypeEnum())) {
                preds.add(cb.equal(root.get(FieldNameConstants.TYPE_ENUM), query.getTypeEnum()));
            }
            if (query.getBabyNo() != null) {
                Integer normalizedBabyNo = VitalSign.normalizeBabyNo(query.getBabyNo());
                if (normalizedBabyNo == 0) {
                    preds.add(cb.or(
                        cb.isNull(root.get(FieldNameConstants.BABY_NO)),
                        cb.equal(root.get(FieldNameConstants.BABY_NO), 0)
                    ));
                } else {
                    preds.add(cb.equal(root.get(FieldNameConstants.BABY_NO), normalizedBabyNo));
                }
            }
            return cb.and(preds.toArray(Predicate[]::new));
        };
    }

    /**
     * 体征主表实体 → 头读模型。
     *
     * @param row 主表实体
     * @return 头读模型
     */
    private static VitalSignHeaderQueryModel toHeaderModel(VitalSign row) {
        VitalSignHeaderQueryModel model = new VitalSignHeaderQueryModel();
        model.setId(row.getId());
        model.setPatientId(row.getPatientId());
        model.setEncounterId(row.getEncounterId());
        model.setRecordedAt(row.getRecordedAt());
        model.setTypeEnum(row.getTypeEnum());
        model.setBusNo(row.getBusNo());
        model.setLocationId(row.getLocationId());
        model.setBabyNo(row.getBabyNo());
        model.setRecorderId(row.getRecorderId());
        model.setRemark(row.getRemark());
        return model;
    }

    /**
     * 体征明细实体与主表头 → 明细行读模型（曲线/表格展示用）。
     *
     * @param detail 明细实体
     * @param header 所属主表头
     * @return 明细行读模型
     */
    private static VitalSignDetailRowQueryModel toDetailModel(VitalSignDetail detail, VitalSign header) {
        VitalSignDetailRowQueryModel model = new VitalSignDetailRowQueryModel();
        model.setId(detail.getId());
        model.setVitalSignId(detail.getVitalSignId());
        model.setRecordedAt(header.getRecordedAt());
        model.setTypeEnum(header.getTypeEnum());
        model.setTermCode(detail.getTermCode());
        model.setItemCode(detail.getItemCode());
        model.setItemValue(detail.getItemValue());
        model.setUnits(detail.getUnits());
        model.setRemark(detail.getRemark());
        return model;
    }
}
