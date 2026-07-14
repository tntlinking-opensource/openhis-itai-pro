/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.repository;

import com.openhis.chs.domain.share.enums.ListType;
import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.chs.domain.share.constants.ChsCatalogConstants;
import com.openhis.chs.domain.catalog.command.PagePendingCatalogMappingCommand;
import com.openhis.chs.domain.catalog.result.PagePendingCatalogMappingResult;
import com.openhis.chs.domain.catalog.result.PendingCatalogMappingRowResult;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/** 医保目录对码 Workbench 只读查询实现 */
@Repository
@RequiredArgsConstructor
public class PendingCatalogMappingLookupRepository implements PendingCatalogMappingLookup {

    private static final String STATUS_PENDING = "PENDING_UPLOAD";
    private static final String STATUS_MATCHED = "MATCHED";
    private static final String STATUS_NO_YB = "NO_YB_NO";

    private final JdbcTemplate jdbcTemplate;

    /** 待对码 Workbench 分页（药品/诊疗/耗材 UNION） */
    @Override
    public PagePendingCatalogMappingResult page(PagePendingCatalogMappingCommand command) {
        PagePendingCatalogMappingCommand request = command == null
            ? new PagePendingCatalogMappingCommand() : command;
        String mappingStatus = normalizeMappingStatus(request.getMappingStatus());
        String keyword = trimToNull(request.getKeyword());
        String productTable = normalizeProductTable(request.getProductTable());
        int pageNo = request.getPageNo() == null || request.getPageNo() < 1 ? 1 : request.getPageNo();
        int pageSize = request.getPageSize() == null || request.getPageSize() < 1
            ? 20 : Math.min(request.getPageSize(), 200);
        long tenantId = TenantUtils.getCurrentTenantId();

        List<Object> countArgs = new ArrayList<>();
        String countSql = buildUnionSql(mappingStatus, productTable, keyword, tenantId, true, countArgs);
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, countArgs.toArray());

        List<Object> pageArgs = new ArrayList<>();
        String pageSql = buildUnionSql(mappingStatus, productTable, keyword, tenantId, false, pageArgs)
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
            .setPendingUploadCount(countByStatus(STATUS_PENDING, productTable, keyword, tenantId))
            .setMatchedCount(countByStatus(STATUS_MATCHED, productTable, keyword, tenantId))
            .setNoYbNoCount(countByStatus(STATUS_NO_YB, productTable, keyword, tenantId));
    }

    /** 按对码状态统计行数 */
    private long countByStatus(String mappingStatus, String productTable, String keyword, long tenantId) {
        List<Object> args = new ArrayList<>();
        String sql = buildUnionSql(mappingStatus, productTable, keyword, tenantId, true, args);
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    /** 组装三主表 UNION SQL（count 或分页列） */
    private String buildUnionSql(
        String mappingStatus,
        String productTable,
        String keyword,
        long tenantId,
        boolean countOnly,
        List<Object> args
    ) {
        List<String> branches = new ArrayList<>();
        if (productTable == null || ChsCatalogConstants.TABLE_MED_MEDICATION_DEFINITION.equals(productTable)) {
            branches.add(medicationBranch(mappingStatus, keyword, tenantId, args));
        }
        if (productTable == null || ChsCatalogConstants.TABLE_WKF_ACTIVITY_DEFINITION.equals(productTable)) {
            branches.add(activityBranch(mappingStatus, keyword, tenantId, args));
        }
        if (productTable == null || ChsCatalogConstants.TABLE_ADM_DEVICE_DEFINITION.equals(productTable)) {
            branches.add(deviceBranch(mappingStatus, keyword, tenantId, args));
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
    private String medicationBranch(String mappingStatus, String keyword, long tenantId, List<Object> args) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT '").append(ChsCatalogConstants.TABLE_MED_MEDICATION_DEFINITION).append("' AS product_table, ");
        sql.append("id AS product_id, '药品' AS product_type_label, bus_no, name, yb_no, yb_match_flag, chrgitm_lv, '");
        sql.append(ListType.WESTERN_DRUG.getCode()).append("' AS list_type ");
        sql.append("FROM med_medication_definition WHERE is_deleted = FALSE AND tenant_id = ? AND status_enum = ? ");
        args.add(tenantId);
        args.add(ChsCatalogConstants.PRODUCT_STATUS_ACTIVE);
        appendMappingStatus(sql, mappingStatus);
        appendKeyword(sql, keyword, args);
        return sql.toString();
    }

    /** 诊疗主表分支 SQL */
    private String activityBranch(String mappingStatus, String keyword, long tenantId, List<Object> args) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT '").append(ChsCatalogConstants.TABLE_WKF_ACTIVITY_DEFINITION).append("' AS product_table, ");
        sql.append("id AS product_id, '诊疗' AS product_type_label, bus_no, name, yb_no, yb_match_flag, chrgitm_lv, '");
        sql.append(ListType.MEDICAL_SERVICE.getCode()).append("' AS list_type ");
        sql.append("FROM wkf_activity_definition WHERE is_deleted = FALSE AND tenant_id = ? AND status_enum = ? ");
        args.add(tenantId);
        args.add(ChsCatalogConstants.PRODUCT_STATUS_ACTIVE);
        appendMappingStatus(sql, mappingStatus);
        appendKeyword(sql, keyword, args);
        return sql.toString();
    }

    /** 耗材主表分支 SQL */
    private String deviceBranch(String mappingStatus, String keyword, long tenantId, List<Object> args) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT '").append(ChsCatalogConstants.TABLE_ADM_DEVICE_DEFINITION).append("' AS product_table, ");
        sql.append("id AS product_id, '耗材' AS product_type_label, bus_no, name, yb_no, yb_match_flag, chrgitm_lv, '");
        sql.append(ListType.MEDICAL_CONSUMABLE.getCode()).append("' AS list_type ");
        sql.append("FROM adm_device_definition WHERE is_deleted = FALSE AND tenant_id = ? AND status_enum = ? ");
        args.add(tenantId);
        args.add(ChsCatalogConstants.PRODUCT_STATUS_ACTIVE);
        appendMappingStatus(sql, mappingStatus);
        appendKeyword(sql, keyword, args);
        return sql.toString();
    }

    /** 追加对码状态 WHERE 片段 */
    private void appendMappingStatus(StringBuilder sql, String mappingStatus) {
        if (STATUS_MATCHED.equals(mappingStatus)) {
            sql.append("AND yb_match_flag = TRUE ");
            return;
        }
        if (STATUS_NO_YB.equals(mappingStatus)) {
            sql.append("AND (yb_no IS NULL OR btrim(yb_no) = '') ");
            return;
        }
        sql.append("AND yb_no IS NOT NULL AND btrim(yb_no) <> '' AND (yb_match_flag IS NULL OR yb_match_flag = FALSE) ");
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

    /** 对码状态归一化，默认 PENDING_UPLOAD */
    private static String normalizeMappingStatus(String raw) {
        if (isEmpty(raw)) {
            return STATUS_PENDING;
        }
        String normalized = raw.trim().toUpperCase();
        if (STATUS_MATCHED.equals(normalized) || STATUS_NO_YB.equals(normalized)) {
            return normalized;
        }
        return STATUS_PENDING;
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
