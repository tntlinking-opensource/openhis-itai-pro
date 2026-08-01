/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.service;

import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.chs.domain.catalog.access.ChsInsuranceContractReader;
import com.openhis.chs.domain.catalog.command.PageInstitutionCatalogMatchCommand;
import com.openhis.chs.domain.catalog.entity.YbInstitutionCatalogMatch;
import com.openhis.chs.domain.catalog.repository.HospitalCatalogPresenceLookup;
import com.openhis.chs.domain.catalog.repository.YbInstitutionCatalogMatchRepository;
import com.openhis.chs.domain.catalog.result.HospitalCatalogPresence;
import com.openhis.chs.domain.catalog.result.PageInstitutionCatalogMatchResult;
import com.openhis.chs.domain.contract.ChsContractSnapshot;
import com.openhis.chs.domain.share.constants.ChsCatalogConstants;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 1317 医药机构目录匹配镜像只读查询（含院内启用/停用/无匹配标注）。
 */
@Service
@RequiredArgsConstructor
public class YbInstitutionCatalogMatchQueryService {

    private static final String FILTER_UNUSED = "UNUSED";
    private static final String FILTER_ACTIVE = "ACTIVE";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";
    private static final String STATUS_MISSING = "MISSING";

    private final YbInstitutionCatalogMatchRepository repository;
    private final ChsInsuranceContractReader contractReader;
    private final HospitalCatalogPresenceLookup hospitalCatalogPresenceLookup;

    /**
     * 按契约与筛选条件分页查询本地镜像。
     *
     * @param command 分页查询命令
     * @return 分页结果
     */
    public PageInstitutionCatalogMatchResult page(PageInstitutionCatalogMatchCommand command) {
        PageInstitutionCatalogMatchCommand query = command;
        if (query == null) {
            query = new PageInstitutionCatalogMatchCommand();
        }
        ChsContractSnapshot contract = contractReader.requireContract(query.getContractBusNo(), null);
        Long contractId = contract.id();
        Long tenantId = TenantUtils.getCurrentTenantId();

        int pageNo = query.getPageNo() != null && query.getPageNo() > 0 ? query.getPageNo() : 1;
        int pageSize = query.getPageSize() != null && query.getPageSize() > 0
            ? query.getPageSize()
            : ChsCatalogConstants.DEFAULT_LOCAL_PAGE_SIZE;

        Specification<YbInstitutionCatalogMatch> spec = buildSpec(contractId, tenantId, query);
        String hospitalFilter = normalizeHospitalFilter(query.getHospitalMatchFilter());

        // UNUSED/ACTIVE 需对照院内状态，先全量取再内存分页（院内规模通常千级）
        if (FILTER_UNUSED.equals(hospitalFilter) || FILTER_ACTIVE.equals(hospitalFilter)) {
            List<YbInstitutionCatalogMatch> all = repository.findAll(
                spec, Sort.by(Sort.Direction.DESC, "updtTime", "id"));
            List<PageInstitutionCatalogMatchResult.InstitutionCatalogMatchRow> enriched =
                enrichRows(all);
            List<PageInstitutionCatalogMatchResult.InstitutionCatalogMatchRow> filtered =
                enriched.stream()
                    .filter(row -> matchesHospitalFilter(row.getHospitalMatchStatus(), hospitalFilter))
                    .toList();
            int from = Math.min((pageNo - 1) * pageSize, filtered.size());
            int to = Math.min(from + pageSize, filtered.size());
            PageInstitutionCatalogMatchResult result = new PageInstitutionCatalogMatchResult();
            result.setPageNo(pageNo);
            result.setPageSize(pageSize);
            result.setTotal(filtered.size());
            result.setRows(new ArrayList<>(filtered.subList(from, to)));
            return result;
        }

        Page<YbInstitutionCatalogMatch> page = repository.findAll(
            spec,
            PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Direction.DESC, "updtTime", "id")));
        PageInstitutionCatalogMatchResult result = new PageInstitutionCatalogMatchResult();
        result.setPageNo(pageNo);
        result.setPageSize(pageSize);
        result.setTotal(page.getTotalElements());
        result.setRows(enrichRows(page.getContent()));
        return result;
    }

    /**
     * 按主键加载未软删镜像。
     *
     * @param matchId 镜像主键
     * @return 实体；不存在返回 null
     */
    public YbInstitutionCatalogMatch findActiveById(Long matchId) {
        if (matchId == null) {
            return null;
        }
        return repository.findById(matchId)
            .filter(e -> e.getIsDeleted() == null || !e.getIsDeleted())
            .orElse(null);
    }

    /**
     * 撤销成功后将镜像标为无效（vali_flag=0），避免工作台继续当作有效对照。
     *
     * @param match 镜像实体
     */
    public void markInvalid(YbInstitutionCatalogMatch match) {
        if (match == null) {
            return;
        }
        match.setValiFlag("0");
        repository.save(match);
    }

    private List<PageInstitutionCatalogMatchResult.InstitutionCatalogMatchRow> enrichRows(
            List<YbInstitutionCatalogMatch> entities) {
        List<PageInstitutionCatalogMatchResult.InstitutionCatalogMatchRow> rows = new ArrayList<>();
        if (entities == null || entities.isEmpty()) {
            return rows;
        }
        Map<String, List<String>> busNosByListType = new LinkedHashMap<>();
        for (YbInstitutionCatalogMatch entity : entities) {
            String lt = entity.getListType() == null ? "" : entity.getListType().trim();
            String busNo = entity.getMedinsListCodg();
            if (busNo == null || busNo.isBlank()) {
                continue;
            }
            busNosByListType.computeIfAbsent(lt, k -> new ArrayList<>()).add(busNo.trim());
        }
        Map<String, HospitalCatalogPresence> presenceByKey = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : busNosByListType.entrySet()) {
            presenceByKey.putAll(hospitalCatalogPresenceLookup.resolve(entry.getKey(), entry.getValue()));
        }
        for (YbInstitutionCatalogMatch entity : entities) {
            PageInstitutionCatalogMatchResult.InstitutionCatalogMatchRow row = toRow(entity);
            String key = HospitalCatalogPresenceLookup.key(entity.getListType(), entity.getMedinsListCodg());
            HospitalCatalogPresence presence = presenceByKey.get(key);
            if (presence == null) {
                row.setHospitalMatchStatus(STATUS_MISSING);
            } else {
                row.setHospitalProductTable(presence.getProductTable());
                row.setHospitalProductId(presence.getProductId());
                row.setHospitalStatusEnum(presence.getStatusEnum());
                row.setHospitalMatchStatus(presence.isActive() ? STATUS_ACTIVE : STATUS_INACTIVE);
            }
            rows.add(row);
        }
        return rows;
    }

    private static boolean matchesHospitalFilter(String status, String filter) {
        if (FILTER_ACTIVE.equals(filter)) {
            return STATUS_ACTIVE.equals(status);
        }
        if (FILTER_UNUSED.equals(filter)) {
            return STATUS_INACTIVE.equals(status) || STATUS_MISSING.equals(status);
        }
        return true;
    }

    private static String normalizeHospitalFilter(String raw) {
        if (raw == null || raw.isBlank()) {
            return "ALL";
        }
        String normalized = raw.trim().toUpperCase();
        if (FILTER_UNUSED.equals(normalized) || FILTER_ACTIVE.equals(normalized)) {
            return normalized;
        }
        return "ALL";
    }

    private static Specification<YbInstitutionCatalogMatch> buildSpec(
            Long contractId,
            Long tenantId,
            PageInstitutionCatalogMatchCommand query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));
            predicates.add(cb.equal(root.get("contractId"), contractId));
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (isNotEmpty(query.getListType())) {
                predicates.add(cb.equal(root.get("listType"), query.getListType().trim()));
            }
            if (isNotEmpty(query.getValiFlag())) {
                predicates.add(cb.equal(root.get("valiFlag"), query.getValiFlag().trim()));
            }
            if (isNotEmpty(query.getKeyword())) {
                String like = "%" + query.getKeyword().trim() + "%";
                predicates.add(cb.or(
                    cb.like(root.get("medinsListCodg"), like),
                    cb.like(root.get("medinsListName"), like),
                    cb.like(root.get("medListCodg"), like)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static PageInstitutionCatalogMatchResult.InstitutionCatalogMatchRow toRow(
            YbInstitutionCatalogMatch entity) {
        return new PageInstitutionCatalogMatchResult.InstitutionCatalogMatchRow()
            .setId(entity.getId())
            .setFixmedinsCode(entity.getFixmedinsCode())
            .setMedinsListCodg(entity.getMedinsListCodg())
            .setMedinsListName(entity.getMedinsListName())
            .setListType(entity.getListType())
            .setMedListCodg(entity.getMedListCodg())
            .setValiFlag(entity.getValiFlag())
            .setBegndate(entity.getBegndate())
            .setEnddate(entity.getEnddate())
            .setAprvno(entity.getAprvno())
            .setSpec(entity.getSpec())
            .setDosform(entity.getDosform())
            .setMemo(entity.getMemo())
            .setUpdtTime(entity.getUpdtTime());
    }
}
