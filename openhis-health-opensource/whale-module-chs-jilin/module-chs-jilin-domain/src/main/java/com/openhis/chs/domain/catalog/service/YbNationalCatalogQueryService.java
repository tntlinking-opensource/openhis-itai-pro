/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.service;

import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.domain.share.enums.ValiFlag;
import com.openhis.chs.domain.share.enums.ListType;
import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;
import com.openhis.chs.domain.share.constants.ChsCatalogConstants;
import com.openhis.chs.domain.catalog.entity.YbNationalCatalogItem;
import com.openhis.chs.domain.catalog.queries.NationalCatalogPageQuery;
import com.openhis.chs.domain.catalog.repository.YbNationalCatalogItemLookup;
import com.openhis.chs.domain.catalog.repository.YbNationalCatalogItemRepository;
import static com.openhis.whale.core.application.util.StringUtils.trimToNull;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 国家医保目录镜像只读查询 */
@Service
@RequiredArgsConstructor
public class YbNationalCatalogQueryService {

    private final YbNationalCatalogItemRepository repository;
    private final YbNationalCatalogItemLookup catalogItemLookup;

    /**
     * 分页查询目录镜像；按 picker 模式路由，1301 明细 1312 去重分页      *
     * @param query 契约、筛选与分页
     * @return 目录实体分页 */
    public Page<YbNationalCatalogItem> page(NationalCatalogPageQuery query) {
        PageRequest pageable = PageRequest.of(
            query.springPageIndex(),
            query.getBoundedPageSize(),
            Sort.by(Sort.Direction.ASC, "hilistCode"));
        if (query.isPickerMode1301Detail()) {
            return page1301Detail(query, pageable);
        }
        if (query.isDedupeByHilistCode()) {
            return pageDistinctHilistCode(query, pageable);
        }
        return repository.findAll(buildSpec(query), pageable);
    }

    /**
     * 选码推荐候选池；药品有批准文号/规格时查 1301 明细，否则先 1312 摘要去重，
     * 空结果再按 listType 回退明细（101→1301，102→1302，201→1305，301→1306）。
     * <p>饮片(102)在 1302/本类型 1312 皆空时，再放宽到不限 list_type（兼容仅下了 1301/1312、
     * 饮片码落在 101 的现场）。</p>
     */
    public List<YbNationalCatalogItem> listForMedicationMatch(
            Long contractId,
            Long tenantId,
            String listType,
            String keyword,
            String aprvno,
            String spec,
            String hilistCodeExact,
            int limit) {
        if (contractId == null || tenantId == null) {
            return List.of();
        }
        String detailSource = resolveDetailSourceInfno(listType);
        int bound = Math.max(1, limit);
        // 仅西药中成药在有批准文号/规格时直查 1301 明细
        if ("1301".equals(detailSource) && prefers1301Detail(aprvno, spec)) {
            return catalogItemLookup.listDetailForMedicationMatch(
                contractId,
                tenantId,
                trimToNull(listType),
                detailSource,
                trimToNull(keyword),
                trimToNull(aprvno),
                trimToNull(spec),
                trimToNull(hilistCodeExact),
                ValiFlag.VALID.getCode(),
                bound);
        }
        List<YbNationalCatalogItem> pool1312 = catalogItemLookup.listDistinctForMedicationMatch(
            contractId,
            tenantId,
            trimToNull(listType),
            trimToNull(keyword),
            trimToNull(aprvno),
            trimToNull(spec),
            trimToNull(hilistCodeExact),
            bound);
        if (!pool1312.isEmpty()) {
            return pool1312;
        }
        // 按目录类型回退文件明细（饮片→1302，西药→1301 等）
        List<YbNationalCatalogItem> detailPool = catalogItemLookup.listDetailForMedicationMatch(
            contractId,
            tenantId,
            trimToNull(listType),
            detailSource,
            trimToNull(keyword),
            trimToNull(aprvno),
            trimToNull(spec),
            trimToNull(hilistCodeExact),
            ValiFlag.VALID.getCode(),
            bound);
        if (!detailPool.isEmpty()) {
            return detailPool;
        }
        // 饮片：未下 1302 或码落在 101 时，放宽 list_type 再搜 1312→1301
        if (ListType.HERB.getCode().equals(trimToNull(listType))) {
            List<YbNationalCatalogItem> relaxed1312 = catalogItemLookup.listDistinctForMedicationMatch(
                contractId,
                tenantId,
                null,
                trimToNull(keyword),
                trimToNull(aprvno),
                trimToNull(spec),
                trimToNull(hilistCodeExact),
                bound);
            if (!relaxed1312.isEmpty()) {
                return relaxed1312;
            }
            return catalogItemLookup.listDetailForMedicationMatch(
                contractId,
                tenantId,
                null,
                ChsInfNo.DRUG_CATALOG_DOWNLOAD,
                trimToNull(keyword),
                trimToNull(aprvno),
                trimToNull(spec),
                trimToNull(hilistCodeExact),
                ValiFlag.VALID.getCode(),
                bound);
        }
        return detailPool;
    }

    /**
     * 按目录类别解析明细回退源：医疗服务→1305，耗材→1306，中药饮片→1302，其余（西药中成药）→1301。
     */
    public static String resolveDetailSourceInfno(String listType) {
        String code = trimToNull(listType);
        if (ListType.MEDICAL_SERVICE.getCode().equals(code)) {
            return ChsInfNo.MEDICAL_SERVICE_CATALOG_DOWNLOAD;
        }
        if (ListType.MEDICAL_CONSUMABLE.getCode().equals(code)) {
            return ChsInfNo.MEDICAL_CONSUMABLE_CATALOG_DOWNLOAD;
        }
        if (ListType.HERB.getCode().equals(code)) {
            return ChsInfNo.HERB_CATALOG_DOWNLOAD;
        }
        return ChsInfNo.DRUG_CATALOG_DOWNLOAD;
    }

    /** 1301 明细模式分页（含 source_line_no） */
    private Page<YbNationalCatalogItem> page1301Detail(
            NationalCatalogPageQuery query,
            PageRequest pageable) {
        String keyword = trimToNull(query.getKeyword());
        String hilistPrefix = trimToNull(query.getHilistCode());
        return catalogItemLookup.page1301Detail(
            query.getContractId(),
            query.getTenantId(),
            trimToNull(query.getListType()),
            trimToNull(query.getValiFlag()),
            keyword,
            hilistPrefix,
            trimToNull(query.getAprvno()),
            trimToNull(query.getSpec()),
            query.isExcludePlatformExpired(),
            pageable);
    }

    /** 1312 摘要去重分页（DISTINCT ON hilist_code） */
    private Page<YbNationalCatalogItem> pageDistinctHilistCode(
            NationalCatalogPageQuery query,
            PageRequest pageable) {
        String keyword = trimToNull(query.getKeyword());
        String hilistPrefix = trimToNull(query.getHilistCode());
        boolean picker1312 = query.isPickerMode1312Only()
            || ChsInfNo.CATALOG_QUERY.equals(trimToNull(query.getSourceInfno()));
        return catalogItemLookup.pageDistinctHilistCode(
            query.getContractId(),
            query.getTenantId(),
            trimToNull(query.getListType()),
            picker1312,
            trimToNull(query.getSourceInfno()),
            trimToNull(query.getValiFlag()),
            keyword,
            hilistPrefix,
            trimToNull(query.getAprvno()),
            trimToNull(query.getSpec()),
            query.isExcludePlatformExpired(),
            pageable);
    }

    /**
     * 统计契约下某 list_type 的镜像行数（游标 DTO 展示用）     *
     * @param contractId 契约主键
     * @param listType   目录类别
     * @param tenantId   租户
     * @return 行数，参数无效时 0 */
    public long countByContractAndListType(Long contractId, String listType, Long tenantId) {
        if (contractId == null || tenantId == null || isEmpty(listType)) {
            return 0L;
        }
        return repository.countByContractIdAndListTypeAndTenantIdAndIsDeletedFalse(
            contractId, listType.trim(), tenantId);
    }

    /**
     * 查询本地镜像最updt_time312 增量游标默认值）     *
     * @param contractId 契约主键
     * @param tenantId   租户
     * @return 最大更新日期，无数据时 empty */
    public Optional<LocalDate> findMaxUpdtTime(Long contractId, Long tenantId) {
        if (contractId == null) {
            return Optional.empty();
        }
        NationalCatalogPageQuery query = new NationalCatalogPageQuery();
        query.setContractId(contractId);
        query.setTenantId(tenantId);
        List<YbNationalCatalogItem> rows = repository.findAll(
            buildSpec(query),
            PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "updtTime"))
        ).getContent();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        YbNationalCatalogItem first = rows.get(0);
        if (first.getUpdtTime() == null) {
            return Optional.empty();
        }
        return Optional.of(first.getUpdtTime().toLocalDate());
    }

    /** JPA Specification 动态筛选条件 */
    private Specification<YbNationalCatalogItem> buildSpec(NationalCatalogPageQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query.getTenantId() != null) {
                predicates.add(cb.equal(root.get("tenantId"), query.getTenantId()));
            }
            if (query.getContractId() != null) {
                predicates.add(cb.equal(root.get("contractId"), query.getContractId()));
            }
            if (isNotEmpty(query.getListType())) {
                predicates.add(cb.equal(root.get("listType"), query.getListType().trim()));
            }
            if (isNotEmpty(query.getHilistCode())) {
                String prefix = query.getHilistCode().trim() + "%";
                predicates.add(cb.like(root.get("hilistCode"), prefix));
            }
            if (isNotEmpty(query.getSourceInfno())) {
                predicates.add(cb.equal(root.get("sourceInfno"), query.getSourceInfno().trim()));
            }
            if (query.isPickerMode1312Only()) {
                predicates.add(cb.or(
                    cb.equal(root.get("sourceInfno"), ChsInfNo.CATALOG_QUERY),
                    cb.isNull(root.get("sourceLineNo"))));
            }
            if (query.isPickerMode1301Detail()) {
                predicates.add(cb.equal(root.get("sourceInfno"), ChsInfNo.DRUG_CATALOG_DOWNLOAD));
                predicates.add(cb.isNotNull(root.get("sourceLineNo")));
            }
            if (isNotEmpty(query.getValiFlag())) {
                predicates.add(cb.equal(root.get("valiFlag"), query.getValiFlag().trim()));
            }
            if (query.isExcludePlatformExpired()) {
                LocalDate day = LocalDate.now();
                // 与 SQL/Composer 一致：enddate 空或≤0001-12-31 视为无结束日
                predicates.add(cb.or(
                    cb.isNull(root.get("enddate")),
                    cb.lessThanOrEqualTo(root.get("enddate"), LocalDate.of(1, 12, 31)),
                    cb.greaterThanOrEqualTo(root.get("enddate"), day)
                ));
            }
            if (isNotEmpty(query.getAprvno())) {
                String pattern = "%" + query.getAprvno().trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("aprvno")), pattern));
            }
            if (isNotEmpty(query.getSpec())) {
                String pattern = "%" + query.getSpec().trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("spec")), pattern));
            }
            if (isNotEmpty(query.getKeyword())) {
                String trimmed = query.getKeyword().trim().toLowerCase();
                String pattern = "%" + trimmed + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("hilistName")), pattern),
                    cb.like(cb.lower(root.get("pinyin")), pattern),
                    cb.like(cb.lower(root.get("hilistCode")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** 有批准文号或规格时优先查 1301 明细 */
    public static boolean prefers1301Detail(String aprvno, String spec) {
        return isNotEmpty(aprvno) || isNotEmpty(spec);
    }
}
