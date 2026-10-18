/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.mrms.caserecord.service;

import com.zxplat.domain.mrms.caserecord.entity.CaseRecord;
import com.zxplat.domain.mrms.caserecord.queries.CaseRecordPageQuery;
import com.zxplat.domain.mrms.caserecord.queries.CaseRecordUploadPageQuery;
import com.zxplat.domain.mrms.caserecord.queries.model.CaseRecordListItemQueryModel;
import com.zxplat.domain.mrms.caserecord.repository.CaseRecordRepository;
import com.zxplat.domain.share.constants.MrmsSettleUploadFlags;
import com.zxplat.domain.share.enums.CaseRecordStatus;
import com.zxplat.domain.share.enums.ChsUploadStatus;
import com.zxplat.domain.share.utils.EnumUtil;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

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

    /** 前端「未上传」筛选项：对应主档上传状态为空。 */
    private static final String UPLOAD_STATUS_NONE = "none";

    /** 已出院：{@code dscg_time} 非空。 */
    private static final String DISCHARGED_YES = "1";

    /** 未出院：{@code dscg_time} 为空。 */
    private static final String DISCHARGED_NO = "0";

    private final CaseRecordRepository caseRecordRepository;

    /**
     * 按租户 + 就诊主键集合批量映射主档（仅本聚合 {@link CaseRecordRepository}）。
     *
     * <p>用于首页列表以住院就诊为数据源时 left join 回填病案号/状态/上传态；无档就诊不出现在 Map。</p>
     *
     * @param tenantId     租户
     * @param encounterIds 就诊主键集合；空则返回空 Map
     * @return encounterId → 未删除主档
     */
    public Map<Long, CaseRecord> mapByEncounterIds(Long tenantId, Collection<Long> encounterIds) {
        if (tenantId == null || encounterIds == null || encounterIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> idSet = new LinkedHashSet<>();
        for (Long encounterId : encounterIds) {
            // 跳过空主键，避免 IN 含 null
            if (encounterId != null) {
                idSet.add(encounterId);
            }
        }
        if (idSet.isEmpty()) {
            return Collections.emptyMap();
        }
        List<CaseRecord> records =
                caseRecordRepository.findByTenantIdAndEncounterIdInAndIsDeletedFalse(tenantId, idSet);
        Map<Long, CaseRecord> result = new HashMap<>(records.size());
        for (CaseRecord record : records) {
            // 同就诊多档理论上不应出现；后写覆盖前写，便于排查脏数据
            if (record != null && record.getEncounterId() != null) {
                result.put(record.getEncounterId(), record);
            }
        }
        return result;
    }

    /**
     * 分页检索病案列表，返回列表行 QueryModel（含列表冗余摘要字段）。
     *
     * @param query 租户、关键字、状态、科室、入/出院区间、出院标记、4401 上传状态等过滤条件
     * @param pageable 分页与排序（由应用层传入）
     * @return 分页 QueryModel
     */
    public Page<CaseRecordListItemQueryModel> queryPage(CaseRecordPageQuery query, Pageable pageable) {
        Specification<CaseRecord> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isDeleted"), false));
            predicates.add(cb.equal(root.get("tenantId"), query.getTenantId()));
            String kw = trimToNull(query.getKeyword());
            // 关键字：姓名/住院号/病案号/证件号任一模糊匹配
            if (kw != null) {
                String pattern = "%" + kw.toLowerCase() + "%";
                Predicate nameLike = cb.like(cb.lower(root.get("psnName")), pattern);
                Predicate iptLike = cb.like(cb.lower(root.get("iptNo")), pattern);
                Predicate medLike = cb.like(cb.lower(root.get("medcasno")), pattern);
                Predicate certLike = cb.like(cb.lower(root.get("certno")), pattern);
                predicates.add(cb.or(nameLike, iptLike, medLike, certLike));
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
            // 入院日起（含当日，主档冗余 adm_time）
            if (query.getAdmFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("admTime"), query.getAdmFrom()));
            }
            // 入院日止（含当日）
            if (query.getAdmTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("admTime"), query.getAdmTo()));
            }
            // 出院日起（含当日）
            if (query.getDscgFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dscgTime"), query.getDscgFrom()));
            }
            // 出院日止（含当日）
            if (query.getDscgTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dscgTime"), query.getDscgTo()));
            }
            String dischargedCode = trimToNull(query.getDischargedCode());
            // 是否出院：1=已出院，0=未出院
            if (DISCHARGED_YES.equals(dischargedCode)) {
                predicates.add(cb.isNotNull(root.get("dscgTime")));
            } else if (DISCHARGED_NO.equals(dischargedCode)) {
                predicates.add(cb.isNull(root.get("dscgTime")));
            }
            String uploadFilter = trimToNull(query.getChsUploadStatusCode());
            // 4401 上传状态：none=未上传（null/空串）；其余精确匹配
            if (uploadFilter != null) {
                if (UPLOAD_STATUS_NONE.equals(uploadFilter)) {
                    Predicate nullStatus = cb.isNull(root.get("chsUploadStatusCode"));
                    Predicate emptyStatus = cb.equal(root.get("chsUploadStatusCode"), "");
                    predicates.add(cb.or(nullStatus, emptyStatus));
                } else {
                    predicates.add(cb.equal(root.get("chsUploadStatusCode"), uploadFilter));
                }
            }
            // 二期：settleUploadStatus / settledCode 仅占位接收，本期不下推结算过滤
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return caseRecordRepository.findAll(spec, pageable).map(this::toListItemQueryModel);
    }

    /**
     * 待归档审批分页：强制 status=PENDING_ARCHIVE（DM-MRMS-001 §6.3）。
     *
     * <p>复用 {@link #queryPage} 条件装配；调用方仍可传关键字/科室/出院日等。</p>
     *
     * @param query 列表查询入参（status 字段会被覆盖为 PENDING_ARCHIVE）
     * @param pageable 分页排序
     * @return 待审病案列表
     */
    public Page<CaseRecordListItemQueryModel> pageForArchiveApproval(
            CaseRecordPageQuery query, Pageable pageable) {
        // Gate：待审列表固定状态，忽略调用方传入的其他 status 以免混入草稿/已归档
        CaseRecordPageQuery pendingQuery = query;
        if (pendingQuery == null) {
            pendingQuery = new CaseRecordPageQuery();
        }
        pendingQuery.setStatusCode(CaseRecordStatus.PENDING_ARCHIVE.getCode());
        pendingQuery.setStatusCodes(null);
        return queryPage(pendingQuery, pageable);
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
        model.setEncounterId(record.getEncounterId());
        model.setMedcasno(record.getMedcasno());
        model.setPsnName(record.getPsnName());
        model.setIptNo(record.getIptNo());
        model.setGendCode(record.getGendCode());
        model.setCertno(record.getCertno());
        model.setAdmTime(record.getAdmTime());
        model.setDscgTime(record.getDscgTime());
        model.setAdmCatyName(record.getAdmCatyName());
        model.setDscgCatyName(record.getDscgCatyName());
        model.setChfdrName(record.getChfdrName());
        model.setMainDiagName(record.getMainDiagName());
        model.setStatusCode(record.getStatusCode());
        model.setUpdatedBy(record.getUpdatedBy());
        model.setChsUploadStatusCode(record.getChsUploadStatusCode());
        model.setSettleUploadStatusCode(record.getSettleUploadStatusCode());
        return model;
    }

    /**
     * 将单状态或多状态入参归一为枚举列表；非法 code 静默忽略。
     *
     * @param statusCode  单状态 code；多状态为空时回落使用
     * @param statusCodes 多状态 code 列表，优先
     * @return 去重后的合法状态枚举列表，可能为空
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
     * 医保办上报台分页：按交易号分流 4401（归档+CHS 态）与 4101A/4102（结算上传标志）。
     *
     * @param query 租户、交易号、上传状态、出院区间、关键字
     * @param pageable 分页排序
     * @return 病案主档分页
     */
    public Page<CaseRecord> queryUploadPage(CaseRecordUploadPageQuery query, Pageable pageable) {
        Specification<CaseRecord> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isDeleted"), false));
            predicates.add(cb.equal(root.get("tenantId"), query.getTenantId()));
            String infno = trimToNull(query.getInfno());
            boolean homepage4401 = "4401".equals(infno);
            // 4401：仅已归档可上报；结算清单不要求归档
            if (homepage4401) {
                predicates.add(cb.equal(root.get("statusEnum"), CaseRecordStatus.ARCHIVED));
                this.appendChsUploadStatusFilter(predicates, cb, root, query.getUploadStatusCode());
            } else {
                this.appendSettleUploadStatusFilter(predicates, cb, root, query.getUploadStatusCode(), infno);
            }
            // 出院时间窗口
            if (query.getDscgTimeFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dscgTime"), query.getDscgTimeFrom()));
            }
            if (query.getDscgTimeTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dscgTime"), query.getDscgTimeTo()));
            }
            String kw = trimToNull(query.getKeyword());
            // 关键字：姓名 / 住院号 / 病案号
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

    /**
     * 4401 CHS 上传状态条件下推。
     *
     * @param predicates 谓词累加
     * @param cb CriteriaBuilder
     * @param root 根实体
     * @param uploadFilter 状态 code；空不过滤
     */
    private void appendChsUploadStatusFilter(
            List<Predicate> predicates,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            jakarta.persistence.criteria.Root<CaseRecord> root,
            String uploadFilter) {
        String filter = trimToNull(uploadFilter);
        if (filter == null) {
            return;
        }
        if (ChsUploadStatus.PENDING.getCode().equals(filter)) {
            // pending：未传/待传/失败可重试
            Predicate nullStatus = cb.isNull(root.get("chsUploadStatusCode"));
            Predicate pending = cb.equal(root.get("chsUploadStatusCode"), ChsUploadStatus.PENDING.getCode());
            Predicate failed = cb.equal(root.get("chsUploadStatusCode"), ChsUploadStatus.FAILED.getCode());
            predicates.add(cb.or(nullStatus, pending, failed));
            return;
        }
        if (UPLOAD_STATUS_NONE.equals(filter)) {
            Predicate nullStatus = cb.isNull(root.get("chsUploadStatusCode"));
            Predicate emptyStatus = cb.equal(root.get("chsUploadStatusCode"), "");
            predicates.add(cb.or(nullStatus, emptyStatus));
            return;
        }
        predicates.add(cb.equal(root.get("chsUploadStatusCode"), filter));
    }

    /**
     * 4101A/4102 结算上传标志条件下推（对齐运维台 none/failed/success/pending）。
     *
     * @param predicates 谓词累加
     * @param cb CriteriaBuilder
     * @param root 根实体
     * @param uploadFilter 前端状态 code；空不过滤
     * @param infno 交易号；4102 时 success 仅已提交
     */
    private void appendSettleUploadStatusFilter(
            List<Predicate> predicates,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            jakarta.persistence.criteria.Root<CaseRecord> root,
            String uploadFilter,
            String infno) {
        String filter = trimToNull(uploadFilter);
        if (filter == null) {
            return;
        }
        Predicate flagNull = cb.isNull(root.get("settleUploadStatusCode"));
        Predicate flagEmpty = cb.equal(root.get("settleUploadStatusCode"), "");
        Predicate flagNone = cb.equal(root.get("settleUploadStatusCode"), MrmsSettleUploadFlags.NONE);
        Predicate notUploaded = cb.or(flagNull, flagEmpty, flagNone);
        Predicate uploaded = cb.equal(root.get("settleUploadStatusCode"), MrmsSettleUploadFlags.UPLOADED);
        Predicate committed = cb.equal(root.get("settleUploadStatusCode"), MrmsSettleUploadFlags.COMMITTED);
        Predicate hasError = cb.and(
                cb.isNotNull(root.get("settleUploadError")),
                cb.notEqual(root.get("settleUploadError"), ""));
        if (UPLOAD_STATUS_NONE.equals(filter)) {
            // 未上传且无失败摘要
            predicates.add(cb.and(notUploaded, cb.not(hasError)));
            return;
        }
        if (ChsUploadStatus.FAILED.getCode().equals(filter)) {
            // 有失败摘要（无论当前标志，便于补传排查）
            predicates.add(hasError);
            return;
        }
        if (ChsUploadStatus.SUCCESS.getCode().equals(filter)) {
            // 4102：仅已正式提交；4101A/全部：已传或已提交
            if ("4102".equals(infno)) {
                predicates.add(committed);
            } else {
                predicates.add(cb.or(uploaded, committed));
            }
            return;
        }
        if (ChsUploadStatus.PENDING.getCode().equals(filter)) {
            // 待传：未上传，或 4102 场景下已传未提交
            if ("4102".equals(infno)) {
                predicates.add(uploaded);
            } else {
                predicates.add(cb.and(notUploaded, cb.not(hasError)));
            }
            return;
        }
        // 精确匹配 0/1/2
        predicates.add(cb.equal(root.get("settleUploadStatusCode"), filter));
    }

    /**
     * 按租户 + 病案号精确查未删除主档。
     *
     * @param tenantId 租户
     * @param medcasno 院内病案号
     * @return 主档；未命中 empty
     */
    public Optional<CaseRecord> findOptionalByMedcasno(Long tenantId, String medcasno) {
        String no = trimToNull(medcasno);
        if (tenantId == null || no == null) {
            return Optional.empty();
        }
        return caseRecordRepository.findByTenantIdAndMedcasnoAndIsDeletedFalse(tenantId, no);
    }

}
