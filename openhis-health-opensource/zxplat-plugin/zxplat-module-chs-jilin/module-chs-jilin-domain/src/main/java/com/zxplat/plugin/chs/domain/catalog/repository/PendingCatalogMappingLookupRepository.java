/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.repository;

import com.zxplat.plugin.chs.domain.share.enums.ListType;
import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.plugin.chs.domain.catalog.access.ChsInsuranceContractReader;
import com.zxplat.plugin.chs.domain.share.constants.ChsCatalogConstants;
import com.zxplat.plugin.chs.domain.catalog.command.PagePendingCatalogMappingCommand;
import com.zxplat.plugin.chs.domain.catalog.result.PagePendingCatalogMappingResult;
import com.zxplat.plugin.chs.domain.catalog.result.PendingCatalogMappingRowResult;
import com.zxplat.plugin.chs.domain.contract.ChsContractSnapshot;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * 医保目录对码 Workbench 只读查询。
 * <p>「待处理」相对平台 1317 镜像：院内启用品项在当前契约下无有效对照行。</p>
 */
@Repository
@RequiredArgsConstructor
public class PendingCatalogMappingLookupRepository implements PendingCatalogMappingLookup {

    /** 平台无对照（默认待处理） */
    private static final String STATUS_NOT_ON_PLATFORM = "NOT_ON_PLATFORM";
    /** 平台无对照且院内已有医保码（可 3301） */
    private static final String STATUS_PENDING = "PENDING_UPLOAD";
    /** 平台无对照且院内未选医保码 */
    private static final String STATUS_NO_YB = "NO_YB_NO";

    private final JdbcTemplate jdbcTemplate;
    private final ChsInsuranceContractReader contractReader;

    /** 待对码 Workbench 分页（药品/诊疗/耗材 UNION） */
    @Override
    public PagePendingCatalogMappingResult page(PagePendingCatalogMappingCommand command) {
        PagePendingCatalogMappingCommand request = command == null
            ? new PagePendingCatalogMappingCommand() : command;
        if (isEmpty(request.getContractBusNo())) {
            throw new BusinessException("CONTRACT_BUS_NO_REQUIRED", "请选择医保契约后再查询院内待处理");
        }
        ChsContractSnapshot contract = contractReader.requireContract(request.getContractBusNo(), null);
        Long contractId = contract.id();
        String mappingStatus = normalizeMappingStatus(request.getMappingStatus());
        String keyword = trimToNull(request.getKeyword());
        String productTable = normalizeProductTable(request.getProductTable());
        int pageNo = request.getPageNo() == null || request.getPageNo() < 1 ? 1 : request.getPageNo();
        int pageSize = request.getPageSize() == null || request.getPageSize() < 1
            ? 20 : Math.min(request.getPageSize(), 200);
        long tenantId = TenantUtils.getCurrentTenantId();

        List<Object> countArgs = new ArrayList<>();
        String countSql = buildUnionSql(
            mappingStatus, productTable, keyword, tenantId, contractId, true, countArgs);
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, countArgs.toArray());

        List<Object> pageArgs = new ArrayList<>();
        String pageSql = buildUnionSql(
            mappingStatus, productTable, keyword, tenantId, contractId, false, pageArgs)
            + " ORDER BY name ASC, product_id ASC LIMIT ? OFFSET ?";
        pageArgs.add(pageSize);
        pageArgs.add((long) (pageNo - 1) * pageSize);

        List<PendingCatalogMappingRowResult> rows = jdbcTemplate.query(
            pageSql,
            (rs, rowNum) -> {
                Boolean ybMatchFlag = rs.getObject("yb_match_flag") == null
                    ? null : rs.getBoolean("yb_match_flag");
                Integer chrgitmLv = rs.getObject("chrgitm_lv") == null
                    ? null : rs.getInt("chrgitm_lv");
                return new PendingCatalogMappingRowResult()
                    .setProductTable(rs.getString("product_table"))
                    .setProductId(rs.getLong("product_id"))
                    .setProductTypeLabel(rs.getString("product_type_label"))
                    .setBusNo(rs.getString("bus_no"))
                    .setName(rs.getString("name"))
                    .setYbNo(rs.getString("yb_no"))
                    .setYbMatchFlag(ybMatchFlag)
                    .setChrgitmLv(chrgitmLv)
                    .setListType(rs.getString("list_type"));
            },
            pageArgs.toArray());

        return new PagePendingCatalogMappingResult()
            .setRows(rows)
            .setTotal(total == null ? 0L : total)
            .setPageNo(pageNo)
            .setPageSize(pageSize)
            .setPendingUploadCount(
                countByStatus(STATUS_PENDING, productTable, keyword, tenantId, contractId))
            .setNotOnPlatformCount(
                countByStatus(STATUS_NOT_ON_PLATFORM, productTable, keyword, tenantId, contractId))
            .setMatchedCount(
                countByStatus(STATUS_NOT_ON_PLATFORM, productTable, keyword, tenantId, contractId))
            .setNoYbNoCount(
                countByStatus(STATUS_NO_YB, productTable, keyword, tenantId, contractId));
    }

    /** 按对码状态统计行数 */
    private long countByStatus(
            String mappingStatus,
            String productTable,
            String keyword,
            long tenantId,
            Long contractId) {
        List<Object> args = new ArrayList<>();
        String sql = buildUnionSql(
            mappingStatus, productTable, keyword, tenantId, contractId, true, args);
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    /** 组装三主表 UNION SQL（count 或分页列） */
    private String buildUnionSql(
        String mappingStatus,
        String productTable,
        String keyword,
        long tenantId,
        Long contractId,
        boolean countOnly,
        List<Object> args
    ) {
        List<String> branches = new ArrayList<>();
        if (productTable == null || ChsCatalogConstants.TABLE_MED_MEDICATION_DEFINITION.equals(productTable)) {
            branches.add(medicationBranch(mappingStatus, keyword, tenantId, contractId, args));
        }
        if (productTable == null || ChsCatalogConstants.TABLE_WKF_ACTIVITY_DEFINITION.equals(productTable)) {
            branches.add(activityBranch(mappingStatus, keyword, tenantId, contractId, args));
        }
        if (productTable == null || ChsCatalogConstants.TABLE_ADM_DEVICE_DEFINITION.equals(productTable)) {
            branches.add(deviceBranch(mappingStatus, keyword, tenantId, contractId, args));
        }
        if (branches.isEmpty()) {
            return countOnly ? "SELECT 0" : "SELECT NULL AS product_table WHERE 1=0";
        }
        String union = String.join(" UNION ALL ", branches);
        if (countOnly) {
            return "SELECT COUNT(*) FROM (" + union + ") t";
        }
        return "SELECT product_table, product_id, product_type_label, bus_no, name, yb_no, yb_match_flag, chrgitm_lv, list_type FROM ("
            + union + ") t";
    }

    /** 药品主表分支 SQL */
    private String medicationBranch(
            String mappingStatus, String keyword, long tenantId, Long contractId, List<Object> args) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT '").append(ChsCatalogConstants.TABLE_MED_MEDICATION_DEFINITION).append("' AS product_table, ");
        sql.append("id AS product_id, '药品' AS product_type_label, bus_no, name, yb_no, yb_match_flag, chrgitm_lv, '");
        sql.append(ListType.WESTERN_DRUG.getCode()).append("' AS list_type ");
        sql.append("FROM med_medication_definition WHERE is_deleted = FALSE AND tenant_id = ? AND status_enum = ? ");
        args.add(tenantId);
        args.add(ChsCatalogConstants.PRODUCT_STATUS_ACTIVE);
        appendNotOnPlatform(sql, tenantId, contractId, args);
        appendYbFilter(sql, mappingStatus);
        appendKeyword(sql, keyword, args);
        return sql.toString();
    }

    /** 诊疗主表分支 SQL */
    private String activityBranch(
            String mappingStatus, String keyword, long tenantId, Long contractId, List<Object> args) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT '").append(ChsCatalogConstants.TABLE_WKF_ACTIVITY_DEFINITION).append("' AS product_table, ");
        sql.append("id AS product_id, '诊疗' AS product_type_label, bus_no, name, yb_no, yb_match_flag, chrgitm_lv, '");
        sql.append(ListType.MEDICAL_SERVICE.getCode()).append("' AS list_type ");
        sql.append("FROM wkf_activity_definition WHERE is_deleted = FALSE AND tenant_id = ? AND status_enum = ? ");
        args.add(tenantId);
        args.add(ChsCatalogConstants.PRODUCT_STATUS_ACTIVE);
        appendNotOnPlatform(sql, tenantId, contractId, args);
        appendYbFilter(sql, mappingStatus);
        appendKeyword(sql, keyword, args);
        return sql.toString();
    }

    /** 耗材主表分支 SQL */
    private String deviceBranch(
            String mappingStatus, String keyword, long tenantId, Long contractId, List<Object> args) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT '").append(ChsCatalogConstants.TABLE_ADM_DEVICE_DEFINITION).append("' AS product_table, ");
        sql.append("id AS product_id, '耗材' AS product_type_label, bus_no, name, yb_no, yb_match_flag, chrgitm_lv, '");
        sql.append(ListType.MEDICAL_CONSUMABLE.getCode()).append("' AS list_type ");
        sql.append("FROM adm_device_definition WHERE is_deleted = FALSE AND tenant_id = ? AND status_enum = ? ");
        args.add(tenantId);
        args.add(ChsCatalogConstants.PRODUCT_STATUS_ACTIVE);
        appendNotOnPlatform(sql, tenantId, contractId, args);
        appendYbFilter(sql, mappingStatus);
        appendKeyword(sql, keyword, args);
        return sql.toString();
    }

    /**
     * 平台镜像无有效对照：medins_list_codg = 院内 bus_no。
     */
    private static void appendNotOnPlatform(
            StringBuilder sql, long tenantId, Long contractId, List<Object> args) {
        sql.append("AND NOT EXISTS (")
            .append("SELECT 1 FROM yb_institution_catalog_match m ")
            .append("WHERE m.is_deleted = FALSE AND m.tenant_id = ? AND m.contract_id = ? ")
            .append("AND m.medins_list_codg = bus_no ")
            .append("AND (m.vali_flag IS NULL OR btrim(m.vali_flag) = '' OR m.vali_flag = '1')")
            .append(") ");
        args.add(tenantId);
        args.add(contractId);
    }

    /** 在「平台无」前提下，再按是否已选医保码细分 */
    private static void appendYbFilter(StringBuilder sql, String mappingStatus) {
        if (STATUS_PENDING.equals(mappingStatus)) {
            sql.append("AND yb_no IS NOT NULL AND btrim(yb_no) <> '' ");
            return;
        }
        if (STATUS_NO_YB.equals(mappingStatus)) {
            sql.append("AND (yb_no IS NULL OR btrim(yb_no) = '') ");
            return;
        }
        // NOT_ON_PLATFORM：不再按本地 yb_match_flag 过滤
    }

    /** 追加 name/bus_no/yb_no 关键词 LIKE 条件 */
    private void appendKeyword(StringBuilder sql, String keyword, List<Object> args) {
        if (keyword == null) {
            return;
        }
        String like = "%" + keyword.toLowerCase() + "%";
        sql.append("AND (LOWER(name) LIKE ? OR LOWER(bus_no) LIKE ? OR LOWER(COALESCE(yb_no, '')) LIKE ?) ");
        args.add(like);
        args.add(like);
        args.add(like);
    }

    /** 对码状态归一化，默认 NOT_ON_PLATFORM（相对平台待处理） */
    private static String normalizeMappingStatus(String raw) {
        if (isEmpty(raw)) {
            return STATUS_NOT_ON_PLATFORM;
        }
        String normalized = raw.trim().toUpperCase();
        if (STATUS_PENDING.equals(normalized) || STATUS_NO_YB.equals(normalized)) {
            return normalized;
        }
        return STATUS_NOT_ON_PLATFORM;
    }

    /** 产品表名校验，非法值视为 null（查全部） */
    private static String normalizeProductTable(String raw) {
        if (isEmpty(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        if (ChsCatalogConstants.TABLE_MED_MEDICATION_DEFINITION.equals(trimmed)
            || ChsCatalogConstants.TABLE_WKF_ACTIVITY_DEFINITION.equals(trimmed)
            || ChsCatalogConstants.TABLE_ADM_DEVICE_DEFINITION.equals(trimmed)) {
            return trimmed;
        }
        return null;
    }
}
