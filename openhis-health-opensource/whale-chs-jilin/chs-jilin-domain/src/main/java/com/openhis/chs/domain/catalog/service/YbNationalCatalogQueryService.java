/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.catalog.service;

import com.openhis.chs.domain.catalog.ChsCatalogConstants;
import com.openhis.chs.domain.catalog.entity.YbNationalCatalogItem;
import com.openhis.chs.domain.catalog.queries.NationalCatalogPageQuery;
import com.openhis.chs.domain.catalog.repository.YbNationalCatalogItemLookup;
import com.openhis.chs.domain.catalog.repository.YbNationalCatalogItemRepository;
import com.openhis.chs.domain.support.ChsTextSupport;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
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
 * 国家医保目录镜像只读查询。
 */
@Service
@RequiredArgsConstructor
public class YbNationalCatalogQueryService {

    private final YbNationalCatalogItemRepository repository;
    private final YbNationalCatalogItemLookup catalogItemLookup;

    /**
     * 分页查询目录镜像；按 picker 模式路由至 1301 明细或 1312 去重分页。
     *
     * @param query 契约、筛选与分页
     * @return 目录实体分页
     */
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
     * 药品选码推荐候选池；有批准文号/规格时查 1301 明细，否则 1312 摘要去重。
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
        if (prefers1301Detail(aprvno, spec)) {
            return catalogItemLookup.list1301DetailForMedicationMatch(
                contractId,
                tenantId,
                ChsTextSupport.blankToNull(listType),
                ChsTextSupport.blankToNull(keyword),
                ChsTextSupport.blankToNull(aprvno),
                ChsTextSupport.blankToNull(spec),
                ChsTextSupport.blankToNull(hilistCodeExact),
                ChsCatalogConstants.DEFAULT_VALI_FLAG,
                Math.max(1, limit));
        }
        return catalogItemLookup.listDistinctForMedicationMatch(
            contractId,
            tenantId,
            ChsTextSupport.blankToNull(listType),
            ChsTextSupport.blankToNull(keyword),
            ChsTextSupport.blankToNull(aprvno),
            ChsTextSupport.blankToNull(spec),
            ChsTextSupport.blankToNull(hilistCodeExact),
            Math.max(1, limit));
    }

    private Page<YbNationalCatalogItem> page1301Detail(
            NationalCatalogPageQuery query,
            PageRequest pageable) {
        String keyword = ChsTextSupport.blankToNull(query.getKeyword());
        String hilistPrefix = ChsTextSupport.blankToNull(query.getHilistCode());
        return catalogItemLookup.page1301Detail(
            query.getContractId(),
            query.getTenantId(),
            ChsTextSupport.blankToNull(query.getListType()),
            ChsTextSupport.blankToNull(query.getValiFlag()),
            keyword,
            hilistPrefix,
            ChsTextSupport.blankToNull(query.getAprvno()),
            ChsTextSupport.blankToNull(query.getSpec()),
            pageable);
    }

    private Page<YbNationalCatalogItem> pageDistinctHilistCode(
            NationalCatalogPageQuery query,
            PageRequest pageable) {
        String keyword = ChsTextSupport.blankToNull(query.getKeyword());
        String hilistPrefix = ChsTextSupport.blankToNull(query.getHilistCode());
        boolean picker1312 = query.isPickerMode1312Only()
            || ChsCatalogConstants.SOURCE_INFNO_1312.equals(ChsTextSupport.blankToNull(query.getSourceInfno()));
        return catalogItemLookup.pageDistinctHilistCode(
            query.getContractId(),
            query.getTenantId(),
            ChsTextSupport.blankToNull(query.getListType()),
            picker1312,
            ChsTextSupport.blankToNull(query.getSourceInfno()),
            ChsTextSupport.blankToNull(query.getValiFlag()),
            keyword,
            hilistPrefix,
            ChsTextSupport.blankToNull(query.getAprvno()),
            ChsTextSupport.blankToNull(query.getSpec()),
            pageable);
    }

    /**
     * 统计契约下某 list_type 的镜像行数（游标 DTO 展示用）。
     *
     * @param contractId 契约主键
     * @param listType   目录类别
     * @param tenantId   租户
     * @return 行数，参数无效时 0
     */
    public long countByContractAndListType(Long contractId, String listType, Long tenantId) {
        if (contractId == null || tenantId == null || StringUtils.isBlank(listType)) {
            return 0L;
        }
        return repository.countByContractIdAndListTypeAndTenantIdAndIsDeletedFalse(
            contractId, listType.trim(), tenantId);
    }

    /**
     * 查询本地镜像最大 updt_time（1312 增量游标默认值）。
     *
     * @param contractId 契约主键
     * @param tenantId   租户
     * @return 最大更新日期，无数据时 empty
     */
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

    private Specification<YbNationalCatalogItem> buildSpec(NationalCatalogPageQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query.getTenantId() != null) {
                predicates.add(cb.equal(root.get("tenantId"), query.getTenantId()));
            }
            if (query.getContractId() != null) {
                predicates.add(cb.equal(root.get("contractId"), query.getContractId()));
            }
            if (StringUtils.isNotBlank(query.getListType())) {
                predicates.add(cb.equal(root.get("listType"), query.getListType().trim()));
            }
            if (StringUtils.isNotBlank(query.getHilistCode())) {
                String prefix = query.getHilistCode().trim() + "%";
                predicates.add(cb.like(root.get("hilistCode"), prefix));
            }
            if (StringUtils.isNotBlank(query.getSourceInfno())) {
                predicates.add(cb.equal(root.get("sourceInfno"), query.getSourceInfno().trim()));
            }
            if (query.isPickerMode1312Only()) {
                predicates.add(cb.or(
                    cb.equal(root.get("sourceInfno"), ChsCatalogConstants.SOURCE_INFNO_1312),
                    cb.isNull(root.get("sourceLineNo"))));
            }
            if (query.isPickerMode1301Detail()) {
                predicates.add(cb.equal(root.get("sourceInfno"), ChsCatalogConstants.SOURCE_INFNO_1301));
                predicates.add(cb.isNotNull(root.get("sourceLineNo")));
            }
            if (StringUtils.isNotBlank(query.getValiFlag())) {
                predicates.add(cb.equal(root.get("valiFlag"), query.getValiFlag().trim()));
            }
            if (StringUtils.isNotBlank(query.getAprvno())) {
                String pattern = "%" + query.getAprvno().trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("aprvno")), pattern));
            }
            if (StringUtils.isNotBlank(query.getSpec())) {
                String pattern = "%" + query.getSpec().trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("spec")), pattern));
            }
            if (StringUtils.isNotBlank(query.getKeyword())) {
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

    public static boolean prefers1301Detail(String aprvno, String spec) {
        return StringUtils.isNotBlank(aprvno) || StringUtils.isNotBlank(spec);
    }
}
