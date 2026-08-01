/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.dict.queries;

import com.openhis.whale.core.application.util.SpecificationUtils;
import com.openhis.whale.crb.domain.dict.CrbDeptCodeMap;
import com.openhis.whale.crb.domain.dict.CrbDeptCodeMapRepository;
import com.openhis.whale.crb.domain.dict.CrbDrugCodeMap;
import com.openhis.whale.crb.domain.dict.CrbDrugCodeMapRepository;
import com.openhis.whale.crb.domain.dict.CrbLabItemMap;
import com.openhis.whale.crb.domain.dict.CrbLabItemMapRepository;
import com.openhis.whale.crb.domain.dict.queries.model.DeptCodeMapRowModel;
import com.openhis.whale.crb.domain.dict.queries.model.DrugCodeMapRowModel;
import com.openhis.whale.crb.domain.dict.queries.model.DrugResolveModel;
import com.openhis.whale.crb.domain.dict.queries.model.LabItemMapRowModel;
import com.openhis.whale.crb.domain.dict.queries.model.LabItemResolveModel;
import com.openhis.whale.crb.domain.dict.queries.model.NationalDeptResolveModel;
import com.openhis.whale.crb.domain.dict.queries.model.UnmappedCountModel;
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
 * 编码对照读侧门面（分页、未对照统计、Mapper resolve）。
 */
@Service
@RequiredArgsConstructor
public class CrbCodeMapQueryService {

    private final CrbDeptCodeMapRepository deptCodeMapRepository;
    private final CrbLabItemMapRepository labItemMapRepository;
    private final CrbDrugCodeMapRepository drugCodeMapRepository;

    /**
     * 科室对照分页查询。
     *
     * @param criteria 查询条件
     * @param pageable 分页参数（默认 updatedAt DESC）
     * @return 分页行模型
     */
    public Page<DeptCodeMapRowModel> pageDeptCodeMaps(DeptCodeMapPageCriteria criteria, Pageable pageable) {
        // 构建查询条件（含软删过滤）→ 单次 SQL/仓储调用并完成分页统计 → 组装 QueryModel
        Long tenantId = extractTenantId(criteria);
        Specification<CrbDeptCodeMap> spec = buildDeptPageSpec(tenantId, criteria);
        Page<CrbDeptCodeMap> page = deptCodeMapRepository.findAll(spec, pageable);
        return page.map(this::toDeptRowModel);
    }

    /**
     * 检验项目对照分页查询。
     *
     * @param criteria 查询条件
     * @param pageable 分页参数（默认 updatedAt DESC）
     * @return 分页行模型
     */
    public Page<LabItemMapRowModel> pageLabItemMaps(LabItemMapPageCriteria criteria, Pageable pageable) {
        // 构建查询条件（含软删过滤）→ 单次 SQL/仓储调用并完成分页统计 → 组装 QueryModel
        Long tenantId = extractTenantId(criteria);
        Specification<CrbLabItemMap> spec = buildLabPageSpec(tenantId, criteria);
        Page<CrbLabItemMap> page = labItemMapRepository.findAll(spec, pageable);
        return page.map(this::toLabRowModel);
    }

    /**
     * 药品对照分页查询。
     *
     * @param criteria 查询条件
     * @param pageable 分页参数（默认 updatedAt DESC）
     * @return 分页行模型
     */
    public Page<DrugCodeMapRowModel> pageDrugCodeMaps(DrugCodeMapPageCriteria criteria, Pageable pageable) {
        // 构建查询条件（含软删过滤）→ 单次 SQL/仓储调用并完成分页统计 → 组装 QueryModel
        Long tenantId = extractTenantId(criteria);
        Specification<CrbDrugCodeMap> spec = buildDrugPageSpec(tenantId, criteria);
        Page<CrbDrugCodeMap> page = drugCodeMapRepository.findAll(spec, pageable);
        return page.map(this::toDrugRowModel);
    }

    /**
     * 统计三表未对照行数。
     *
     * @param tenantId 租户 id
     * @return 未对照计数
     */
    public UnmappedCountModel countUnmapped(Long tenantId) {
        // 构建查询条件（含软删过滤）→ 单次 SQL/仓储调用并完成分页统计 → 组装 QueryModel
        Long resolvedTenantId = requireTenantId(tenantId);
        UnmappedCountModel model = new UnmappedCountModel();
        model.setDeptUnmapped((int) deptCodeMapRepository.count(buildDeptUnmappedSpec(resolvedTenantId)));
        model.setLabUnmapped((int) labItemMapRepository.count(buildLabUnmappedSpec(resolvedTenantId)));
        model.setDrugUnmapped((int) drugCodeMapRepository.count(buildDrugUnmappedSpec(resolvedTenantId)));
        return model;
    }

    /**
     * 按院内科室业务码解析国家科室码。
     *
     * @param tenantId 租户 id
     * @param locationBusNo 院内科室业务码
     * @return 解析结果
     */
    public NationalDeptResolveModel resolveNationalDept(Long tenantId, String locationBusNo) {
        // 构建查询条件（含软删过滤）→ 单次 SQL/仓储调用并完成分页统计 → 组装 QueryModel
        NationalDeptResolveModel model = new NationalDeptResolveModel();
        model.setFound(false);
        Long resolvedTenantId = requireTenantId(tenantId);
        String busNo = trimToNull(locationBusNo);
        if (busNo == null) {
            return model;
        }
        return deptCodeMapRepository.findByTenantIdAndLocationBusNoAndIsDeletedFalse(resolvedTenantId, busNo)
                .map(row -> {
                    NationalDeptResolveModel resolved = new NationalDeptResolveModel();
                    resolved.setTargetDeptCode(row.getTargetDeptCode());
                    resolved.setTargetDeptName(row.getTargetDeptName());
                    resolved.setFound(true);
                    return resolved;
                })
                .orElse(model);
    }

    /**
     * 按院内项目业务码解析检验对照。
     *
     * @param tenantId 租户 id
     * @param itemBusNo 院内项目业务码
     * @return 解析结果
     */
    public LabItemResolveModel resolveLabItem(Long tenantId, String itemBusNo) {
        // 构建查询条件（含软删过滤）→ 单次 SQL/仓储调用并完成分页统计 → 组装 QueryModel
        LabItemResolveModel model = new LabItemResolveModel();
        model.setFound(false);
        Long resolvedTenantId = requireTenantId(tenantId);
        String busNo = trimToNull(itemBusNo);
        if (busNo == null) {
            return model;
        }
        return labItemMapRepository.findByTenantIdAndItemBusNoAndIsDeletedFalse(resolvedTenantId, busNo)
                .map(row -> {
                    LabItemResolveModel resolved = new LabItemResolveModel();
                    resolved.setNationalItemCode(row.getNationalItemCode());
                    resolved.setResultShape(row.getResultShape());
                    resolved.setAnnex2Scope(row.getAnnex2Scope());
                    resolved.setPushEnabled(row.getPushEnabled());
                    resolved.setFound(true);
                    return resolved;
                })
                .orElse(model);
    }

    /**
     * 按院内药品业务码解析国家药品码。
     *
     * @param tenantId 租户 id
     * @param medBusNo 院内药品业务码
     * @return 解析结果
     */
    public DrugResolveModel resolveDrug(Long tenantId, String medBusNo) {
        // 构建查询条件（含软删过滤）→ 单次 SQL/仓储调用并完成分页统计 → 组装 QueryModel
        DrugResolveModel model = new DrugResolveModel();
        model.setFound(false);
        Long resolvedTenantId = requireTenantId(tenantId);
        String busNo = trimToNull(medBusNo);
        if (busNo == null) {
            return model;
        }
        return drugCodeMapRepository.findByTenantIdAndMedBusNoAndIsDeletedFalse(resolvedTenantId, busNo)
                .map(row -> {
                    DrugResolveModel resolved = new DrugResolveModel();
                    resolved.setNationalDrugCode(row.getNationalDrugCode());
                    resolved.setNationalDrugName(row.getNationalDrugName());
                    resolved.setFound(true);
                    return resolved;
                })
                .orElse(model);
    }

    /**
     * 从分页条件提取租户 id。
     *
     * @param criteria 查询条件
     * @return 租户 id
     */
    private Long extractTenantId(DeptCodeMapPageCriteria criteria) {
        if (criteria == null) {
            return requireTenantId(null);
        }
        return requireTenantId(criteria.getTenantId());
    }

    /**
     * 从分页条件提取租户 id。
     *
     * @param criteria 查询条件
     * @return 租户 id
     */
    private Long extractTenantId(LabItemMapPageCriteria criteria) {
        if (criteria == null) {
            return requireTenantId(null);
        }
        return requireTenantId(criteria.getTenantId());
    }

    /**
     * 从分页条件提取租户 id。
     *
     * @param criteria 查询条件
     * @return 租户 id
     */
    private Long extractTenantId(DrugCodeMapPageCriteria criteria) {
        if (criteria == null) {
            return requireTenantId(null);
        }
        return requireTenantId(criteria.getTenantId());
    }

    /**
     * 校验租户 id 非空。
     *
     * @param tenantId 租户 id
     * @return 原值
     */
    private Long requireTenantId(Long tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId 不能为空");
        }
        return tenantId;
    }

    /**
     * 构建科室分页 Specification。
     *
     * @param tenantId 租户 id
     * @param criteria 查询条件
     * @return Specification
     */
    private Specification<CrbDeptCodeMap> buildDeptPageSpec(Long tenantId, DeptCodeMapPageCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            predicates.add(cb.equal(root.get("isDeleted"), false));
            if (criteria != null && Boolean.TRUE.equals(criteria.getUnmappedOnly())) {
                predicates.add(buildUnmappedCodePredicate(cb, root.get("targetDeptCode")));
            }
            if (criteria != null) {
                Predicate keywordPredicate = buildKeywordPredicate(cb, root,
                        criteria.getKeyword(),
                        "locationBusNo", "locationName", "targetDeptCode", "targetDeptName");
                if (keywordPredicate != null) {
                    predicates.add(keywordPredicate);
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 构建检验分页 Specification。
     *
     * @param tenantId 租户 id
     * @param criteria 查询条件
     * @return Specification
     */
    private Specification<CrbLabItemMap> buildLabPageSpec(Long tenantId, LabItemMapPageCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            predicates.add(cb.equal(root.get("isDeleted"), false));
            if (criteria != null && Boolean.TRUE.equals(criteria.getUnmappedOnly())) {
                predicates.add(buildUnmappedCodePredicate(cb, root.get("nationalItemCode")));
            }
            if (criteria != null) {
                Predicate keywordPredicate = buildKeywordPredicate(cb, root,
                        criteria.getKeyword(),
                        "itemBusNo", "itemName", "nationalItemCode");
                if (keywordPredicate != null) {
                    predicates.add(keywordPredicate);
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 构建药品分页 Specification。
     *
     * @param tenantId 租户 id
     * @param criteria 查询条件
     * @return Specification
     */
    private Specification<CrbDrugCodeMap> buildDrugPageSpec(Long tenantId, DrugCodeMapPageCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            predicates.add(cb.equal(root.get("isDeleted"), false));
            if (criteria != null && Boolean.TRUE.equals(criteria.getUnmappedOnly())) {
                predicates.add(buildUnmappedCodePredicate(cb, root.get("nationalDrugCode")));
            }
            if (criteria != null) {
                Predicate keywordPredicate = buildKeywordPredicate(cb, root,
                        criteria.getKeyword(),
                        "medBusNo", "medName", "nationalDrugCode", "nationalDrugName");
                if (keywordPredicate != null) {
                    predicates.add(keywordPredicate);
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 构建科室未对照计数 Specification。
     *
     * @param tenantId 租户 id
     * @return Specification
     */
    private Specification<CrbDeptCodeMap> buildDeptUnmappedSpec(Long tenantId) {
        return SpecificationUtils.and(
                SpecificationUtils.<CrbDeptCodeMap>equal("tenantId", tenantId),
                SpecificationUtils.notDeleted(),
                unmappedCodeSpec("targetDeptCode"));
    }

    /**
     * 构建检验未对照计数 Specification。
     *
     * @param tenantId 租户 id
     * @return Specification
     */
    private Specification<CrbLabItemMap> buildLabUnmappedSpec(Long tenantId) {
        return SpecificationUtils.and(
                SpecificationUtils.<CrbLabItemMap>equal("tenantId", tenantId),
                SpecificationUtils.notDeleted(),
                unmappedCodeSpec("nationalItemCode"));
    }

    /**
     * 构建药品未对照计数 Specification。
     *
     * @param tenantId 租户 id
     * @return Specification
     */
    private Specification<CrbDrugCodeMap> buildDrugUnmappedSpec(Long tenantId) {
        return SpecificationUtils.and(
                SpecificationUtils.<CrbDrugCodeMap>equal("tenantId", tenantId),
                SpecificationUtils.notDeleted(),
                unmappedCodeSpec("nationalDrugCode"));
    }

    /**
     * 国家码为空或空白的未对照条件。
     *
     * @param codeField 国家码字段名
     * @param <T> 实体类型
     * @return Specification
     */
    private static <T> Specification<T> unmappedCodeSpec(String codeField) {
        return (root, query, cb) -> buildUnmappedCodePredicate(cb, root.get(codeField));
    }

    /**
     * 构建国家码未对照 Predicate（null 或空白）。
     *
     * @param cb CriteriaBuilder
     * @param codePath 国家码路径
     * @return Predicate
     */
    private static Predicate buildUnmappedCodePredicate(
            jakarta.persistence.criteria.CriteriaBuilder cb,
            jakarta.persistence.criteria.Path<String> codePath) {
        return cb.or(cb.isNull(codePath), cb.equal(codePath, ""));
    }

    /**
     * 构建多字段关键字 OR 模糊匹配 Predicate。
     *
     * @param cb CriteriaBuilder
     * @param root 实体 Root
     * @param keyword 关键字
     * @param fields 字段名列表
     * @return Predicate；关键字为空则 null
     */
    private static Predicate buildKeywordPredicate(
            jakarta.persistence.criteria.CriteriaBuilder cb,
            jakarta.persistence.criteria.Root<?> root,
            String keyword,
            String... fields) {
        String trimmed = trimToNull(keyword);
        if (trimmed == null) {
            return null;
        }
        String pattern = "%" + trimmed + "%";
        List<Predicate> keywordPredicates = new ArrayList<>();
        for (String field : fields) {
            keywordPredicates.add(cb.like(root.get(field), pattern));
        }
        return cb.or(keywordPredicates.toArray(new Predicate[0]));
    }

    /**
     * 实体转科室分页行模型。
     *
     * @param entity 实体
     * @return 行模型
     */
    private DeptCodeMapRowModel toDeptRowModel(CrbDeptCodeMap entity) {
        DeptCodeMapRowModel row = new DeptCodeMapRowModel();
        row.setId(entity.getId());
        row.setLocationBusNo(entity.getLocationBusNo());
        row.setLocationName(entity.getLocationName());
        row.setTargetDeptCode(entity.getTargetDeptCode());
        row.setTargetDeptName(entity.getTargetDeptName());
        row.setMapped(entity.isMapped());
        return row;
    }

    /**
     * 实体转检验分页行模型。
     *
     * @param entity 实体
     * @return 行模型
     */
    private LabItemMapRowModel toLabRowModel(CrbLabItemMap entity) {
        LabItemMapRowModel row = new LabItemMapRowModel();
        row.setId(entity.getId());
        row.setItemBusNo(entity.getItemBusNo());
        row.setItemName(entity.getItemName());
        row.setNationalItemCode(entity.getNationalItemCode());
        row.setResultShape(entity.getResultShape());
        row.setAnnex2Scope(entity.getAnnex2Scope());
        row.setPushEnabled(entity.getPushEnabled());
        row.setMapped(entity.isMapped());
        return row;
    }

    /**
     * 实体转药品分页行模型。
     *
     * @param entity 实体
     * @return 行模型
     */
    private DrugCodeMapRowModel toDrugRowModel(CrbDrugCodeMap entity) {
        DrugCodeMapRowModel row = new DrugCodeMapRowModel();
        row.setId(entity.getId());
        row.setMedBusNo(entity.getMedBusNo());
        row.setMedName(entity.getMedName());
        row.setNationalDrugCode(entity.getNationalDrugCode());
        row.setNationalDrugName(entity.getNationalDrugName());
        row.setMapped(entity.isMapped());
        return row;
    }
}
