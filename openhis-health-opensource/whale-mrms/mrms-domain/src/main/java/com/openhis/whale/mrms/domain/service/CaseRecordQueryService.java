/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.domain.service;

import com.openhis.whale.mrms.domain.entity.CaseRecord;
import com.openhis.whale.mrms.domain.enums.CaseRecordStatus;
import com.openhis.whale.mrms.domain.repository.CaseRecordRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 病案主档读侧查询服务（不写库）。
 */
@Service
@RequiredArgsConstructor
public class CaseRecordQueryService {

    private final CaseRecordRepository caseRecordRepository;

    /**
     * 分页检索病案列表。
     *
     * @param tenantId 租户
     * @param keyword 关键字（姓名/住院号/病案号）
     * @param statusCode 状态过滤（单值，与 statusCodes 二选一）
     * @param statusCodes 状态过滤（多值 IN）
     * @param admCatyName 科室过滤
     * @param attendingPractitionerId 主治医师过滤
     * @param dscgFrom 出院日期起
     * @param dscgTo 出院日期止
     * @param pageable 分页
     * @return 分页结果
     */
    public Page<CaseRecord> queryPage(
            Long tenantId,
            String keyword,
            String statusCode,
            List<String> statusCodes,
            String admCatyName,
            Long attendingPractitionerId,
            LocalDateTime dscgFrom,
            LocalDateTime dscgTo,
            Pageable pageable) {
        Specification<CaseRecord> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isDeleted"), false));
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            String kw = trimToNull(keyword);
            if (kw != null) {
                String pattern = "%" + kw.toLowerCase() + "%";
                Predicate nameLike = cb.like(cb.lower(root.get("psnName")), pattern);
                Predicate iptLike = cb.like(cb.lower(root.get("iptNo")), pattern);
                Predicate medLike = cb.like(cb.lower(root.get("medcasno")), pattern);
                predicates.add(cb.or(nameLike, iptLike, medLike));
            }
            List<CaseRecordStatus> statuses = normalizeStatusCodes(statusCode, statusCodes);
            if (!statuses.isEmpty()) {
                if (statuses.size() == 1) {
                    predicates.add(cb.equal(root.get("statusEnum"), statuses.get(0)));
                } else {
                    predicates.add(root.get("statusEnum").in(statuses));
                }
            }
            String dept = trimToNull(admCatyName);
            if (dept != null) {
                predicates.add(cb.equal(root.get("admCatyName"), dept));
            }
            if (attendingPractitionerId != null) {
                predicates.add(cb.equal(root.get("atddrPractitionerId"), attendingPractitionerId));
            }
            if (dscgFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dscgTime"), dscgFrom));
            }
            if (dscgTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dscgTime"), dscgTo));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return caseRecordRepository.findAll(spec, pageable);
    }

    private List<CaseRecordStatus> normalizeStatusCodes(String statusCode, List<String> statusCodes) {
        List<CaseRecordStatus> normalized = new ArrayList<>();
        if (statusCodes != null) {
            for (String code : statusCodes) {
                CaseRecordStatus status = CaseRecordStatus.fromCode(trimToNull(code));
                if (status != null && !normalized.contains(status)) {
                    normalized.add(status);
                }
            }
        }
        if (normalized.isEmpty()) {
            CaseRecordStatus single = CaseRecordStatus.fromCode(trimToNull(statusCode));
            if (single != null) {
                normalized.add(single);
            }
        }
        return normalized;
    }
}
