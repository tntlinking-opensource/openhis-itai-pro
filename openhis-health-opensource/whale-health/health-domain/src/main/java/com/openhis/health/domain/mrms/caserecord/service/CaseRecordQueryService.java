/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.caserecord.service;

import com.openhis.health.domain.mrms.caserecord.entity.CaseRecord;
import com.openhis.health.domain.mrms.caserecord.queries.CaseRecordPageQuery;
import com.openhis.health.domain.mrms.caserecord.queries.CaseRecordUploadPageQuery;
import com.openhis.health.domain.mrms.caserecord.queries.model.CaseRecordListItemQueryModel;
import com.openhis.health.domain.mrms.caserecord.repository.CaseRecordRepository;
import com.openhis.health.domain.share.enums.CaseRecordStatus;
import com.openhis.health.domain.share.enums.ChsUploadStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 病案主档读侧查询服务（不写库）。
 *
 * <p>仅注入本聚合 {@link CaseRecordRepository}；条件下推 DB，禁止内存分页。</p>
 *
 * <p>聚合边界：仅本聚合 {@link CaseRecordRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class CaseRecordQueryService {

    private final CaseRecordRepository caseRecordRepository;

    /**
     * 分页检索病案列表，返回列表行 QueryModel（含列表冗余摘要字段）。
     *
     * @param query 租户、关键字、状态、科室、出院区间等过滤条件
     * @param pageable 分页与排序（由应用层传入）
     * @return 分页 QueryModel
     */
    public Page<CaseRecordListItemQueryModel> queryPage(CaseRecordPageQuery query, Pageable pageable) {
        Specification<CaseRecord> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isDeleted"), false));
            predicates.add(cb.equal(root.get("tenantId"), query.getTenantId()));
            String kw = trimToNull(query.getKeyword());
            // 关键字：姓名/住院号/病案号任一模糊匹配
            if (kw != null) {
                String pattern = "%" + kw.toLowerCase() + "%";
                Predicate nameLike = cb.like(cb.lower(root.get("psnName")), pattern);
                Predicate iptLike = cb.like(cb.lower(root.get("iptNo")), pattern);
                Predicate medLike = cb.like(cb.lower(root.get("medcasno")), pattern);
                predicates.add(cb.or(nameLike, iptLike, medLike));
            }
            List<CaseRecordStatus> statuses = normalizeStatusCodes(query.getStatusCode(), query.getStatusCodes());
            // 状态过滤：单值 eq / 多值 in
            if (!statuses.isEmpty()) {
                if (statuses.size() == 1) {
                    predicates.add(cb.equal(root.get("statusEnum"), statuses.get(0)));
                } else {
                    predicates.add(root.get("statusEnum").in(statuses));
                }
            }
            String dept = trimToNull(query.getAdmCatyName());
            // 入院科室精确匹配
            if (dept != null) {
                predicates.add(cb.equal(root.get("admCatyName"), dept));
            }
            // 主治医师过滤
            if (query.getAttendingPractitionerId() != null) {
                predicates.add(cb.equal(root.get("atddrPractitionerId"), query.getAttendingPractitionerId()));
            }
            // 出院日起（含当日）
            if (query.getDscgFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dscgTime"), query.getDscgFrom()));
            }
            // 出院日止（含当日）
            if (query.getDscgTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dscgTime"), query.getDscgTo()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return caseRecordRepository.findAll(spec, pageable).map(this::toListItemQueryModel);
    }

    /**
     * 病案实体 → 列表项读模型。
     *
     * @param record 病案聚合根
     * @return 列表读模型
     */
    private CaseRecordListItemQueryModel toListItemQueryModel(CaseRecord record) {
        CaseRecordListItemQueryModel model = new CaseRecordListItemQueryModel();
        model.setId(record.getId());
        model.setMedcasno(record.getMedcasno());
        model.setPsnName(record.getPsnName());
        model.setIptNo(record.getIptNo());
        model.setGendCode(record.getGendCode());
        model.setAdmTime(record.getAdmTime());
        model.setDscgTime(record.getDscgTime());
        model.setAdmCatyName(record.getAdmCatyName());
        model.setDscgCatyName(record.getDscgCatyName());
        model.setChfdrName(record.getChfdrName());
        model.setMainDiagName(record.getMainDiagName());
        model.setStatusCode(record.getStatusCode());
        return model;
    }

    /**
     * 将单状态或多状态入参归一为枚举列表；非法 code 静默忽略。
     */
    private List<CaseRecordStatus> normalizeStatusCodes(String statusCode, List<String> statusCodes) {
        List<CaseRecordStatus> normalized = new ArrayList<>();
        if (statusCodes != null) {
            for (String code : statusCodes) {
                CaseRecordStatus status = EnumUtil.fromCode(CaseRecordStatus.class, trimToNull(code));
                // 合法 code 去重入列表，非法 code 静默忽略
                if (status != null && !normalized.contains(status)) {
                    normalized.add(status);
                }
            }
        }
        // 多状态为空时回落单状态入参
        if (normalized.isEmpty()) {
            CaseRecordStatus single = EnumUtil.fromCode(CaseRecordStatus.class, trimToNull(statusCode));
            if (single != null) {
                normalized.add(single);
            }
        }
        return normalized;
    }

    /**
     * 4401 上传待传列表：仅已归档病案，按 CHS 上传状态与关键字分页。
     *
     * @param query 租户、上传状态、关键字（继承 {@link com.openhis.health.domain.share.query.AbstractPageQuery}）
     * @param pageable 分页排序
     * @return 病案主档分页
     */
    public Page<CaseRecord> queryUploadPage(CaseRecordUploadPageQuery query, Pageable pageable) {
        Specification<CaseRecord> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isDeleted"), false));
            predicates.add(cb.equal(root.get("tenantId"), query.getTenantId()));
            // 4401 仅已归档病案可上传
            predicates.add(cb.equal(root.get("statusEnum"), CaseRecordStatus.ARCHIVED));
            String uploadFilter = trimToNull(query.getChsUploadStatusCode());
            // 按 CHS 上传状态过滤
            if (uploadFilter != null) {
                if (ChsUploadStatus.PENDING.getCode().equals(uploadFilter)) {
                    // pending：未传/待传/失败可重试
                    Predicate nullStatus = cb.isNull(root.get("chsUploadStatusCode"));
                    Predicate pending = cb.equal(root.get("chsUploadStatusCode"), ChsUploadStatus.PENDING.getCode());
                    Predicate failed = cb.equal(root.get("chsUploadStatusCode"), ChsUploadStatus.FAILED.getCode());
                    predicates.add(cb.or(nullStatus, pending, failed));
                } else {
                    // 非 pending：按精确 status code 匹配（如 uploaded）
                    predicates.add(cb.equal(root.get("chsUploadStatusCode"), uploadFilter));
                }
            }
            String kw = trimToNull(query.getKeyword());
            // 关键字模糊匹配（同列表页口径）
            if (kw != null) {
                String pattern = "%" + kw.toLowerCase() + "%";
                Predicate nameLike = cb.like(cb.lower(root.get("psnName")), pattern);
                Predicate iptLike = cb.like(cb.lower(root.get("iptNo")), pattern);
                Predicate medLike = cb.like(cb.lower(root.get("medcasno")), pattern);
                predicates.add(cb.or(nameLike, iptLike, medLike));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return caseRecordRepository.findAll(spec, pageable);
    }

}
