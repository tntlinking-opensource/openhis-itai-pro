/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.hisproxy.support;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.chs.domain.catalog.ChsCatalogConstants;
import com.openhis.chs.domain.catalog.dto.request.PagePendingCatalogMappingRequest;
import com.openhis.chs.domain.catalog.dto.response.PagePendingCatalogMappingResult;
import com.openhis.chs.domain.catalog.dto.response.PendingCatalogMappingRowDto;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 院内产品医保对码 Workbench 只读查询（UNION 三主表）。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class PendingCatalogMappingLookup {

    private static final String STATUS_PENDING = "PENDING_UPLOAD";
    private static final String STATUS_MATCHED = "MATCHED";
    private static final String STATUS_NO_YB = "NO_YB_NO";
    private static final String ACTIVE = PublicationStatus.ACTIVE.getCode();

    private final JdbcTemplate jdbcTemplate;

    public PagePendingCatalogMappingResult page(PagePendingCatalogMappingRequest request) {
        String mappingStatus = normalizeMappingStatus(request.getMappingStatus());
        String keyword = trimToNull(request.getKeyword());
        String productTable = normalizeProductTable(request.getProductTable());
        int pageNo;
        if (request.getPageNo() == null || request.getPageNo() < 1) {
            pageNo = 1;
        } else {
            pageNo = request.getPageNo();
        }
        int pageSize;
        if (request.getPageSize() == null || request.getPageSize() < 1) {
            pageSize = 20;
        } else {
            pageSize = Math.min(request.getPageSize(), 200);
        }
        long tenantId = TenantUtils.getCurrentTenantId();

        List<Object> countArgs = new ArrayList<>();
        String countSql = buildUnionSql(mappingStatus, productTable, keyword, tenantId, true, countArgs);
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, countArgs.toArray());

        List<Object> pageArgs = new ArrayList<>();
        String pageSql = buildUnionSql(mappingStatus, productTable, keyword, tenantId, false, pageArgs)
            + " ORDER BY name ASC, product_id ASC LIMIT ? OFFSET ?";
        pageArgs.add(pageSize);
        pageArgs.add((long) (pageNo - 1) * pageSize);

        List<PendingCatalogMappingRowDto> rows = jdbcTemplate.query(
            pageSql,
            (rs, rowNum) -> {
                Boolean ybMatchFlag;
                if (rs.getObject("yb_match_flag") == null) {
                    ybMatchFlag = null;
                } else {
                    ybMatchFlag = rs.getBoolean("yb_match_flag");
                }
                Integer chrgitmLv;
                if (rs.getObject("chrgitm_lv") == null) {
                    chrgitmLv = null;
                } else {
                    chrgitmLv = rs.getInt("chrgitm_lv");
                }
                return new PendingCatalogMappingRowDto()
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

        PagePendingCatalogMappingResult result = new PagePendingCatalogMappingResult();
        result.setRows(rows);
        if (total == null) {
            result.setTotal(0L);
        } else {
            result.setTotal(total);
        }
        result.setPageNo(pageNo);
        result.setPageSize(pageSize);
        result.setPendingUploadCount(countByStatus(STATUS_PENDING, productTable, keyword, tenantId));
        result.setMatchedCount(countByStatus(STATUS_MATCHED, productTable, keyword, tenantId));
        result.setNoYbNoCount(countByStatus(STATUS_NO_YB, productTable, keyword, tenantId));
        return result;
    }

    private long countByStatus(String mappingStatus, String productTable, String keyword, long tenantId) {
        List<Object> args = new ArrayList<>();
        String sql = buildUnionSql(mappingStatus, productTable, keyword, tenantId, true, args);
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args.toArray());
        if (count == null) {
            return 0L;
        }
        return count;
    }

    private String buildUnionSql(
            String mappingStatus,
            String productTable,
            String keyword,
            long tenantId,
            boolean countOnly,
            List<Object> args) {
        List<String> branches = new ArrayList<>();
        if (productTable == null || TableNameConstants.MED_MEDICATION_DEFINITION.equals(productTable)) {
            branches.add(medicationBranch(mappingStatus, keyword, tenantId, args));
        }
        if (productTable == null || TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(productTable)) {
            branches.add(activityBranch(mappingStatus, keyword, tenantId, args));
        }
        if (productTable == null || TableNameConstants.ADM_DEVICE_DEFINITION.equals(productTable)) {
            branches.add(deviceBranch(mappingStatus, keyword, tenantId, args));
        }
        if (branches.isEmpty()) {
            if (countOnly) {
                return "SELECT 0";
            }
            return "SELECT NULL AS product_table WHERE 1=0";
        }
        String union = String.join(" UNION ALL ", branches);
        if (countOnly) {
            return "SELECT COUNT(*) FROM (" + union + ") t";
        }
        return "SELECT product_table, product_id, product_type_label, bus_no, name, yb_no, yb_match_flag, chrgitm_lv, list_type FROM ("
            + union
            + ") t";
    }

    private String medicationBranch(String mappingStatus, String keyword, long tenantId, List<Object> args) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT '").append(TableNameConstants.MED_MEDICATION_DEFINITION).append("' AS product_table, ");
        sql.append("id AS product_id, '药品' AS product_type_label, bus_no, name, yb_no, yb_match_flag, chrgitm_lv, '");
        sql.append(ChsCatalogConstants.LIST_TYPE_WESTERN_DRUG).append("' AS list_type ");
        sql.append("FROM med_medication_definition WHERE is_deleted = FALSE AND tenant_id = ? AND status_enum = ? ");
        args.add(tenantId);
        args.add(ACTIVE);
        appendMappingStatus(sql, mappingStatus, args);
        appendKeyword(sql, keyword, args);
        return sql.toString();
    }

    private String activityBranch(String mappingStatus, String keyword, long tenantId, List<Object> args) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT '").append(TableNameConstants.WKF_ACTIVITY_DEFINITION).append("' AS product_table, ");
        sql.append("id AS product_id, '诊疗' AS product_type_label, bus_no, name, yb_no, yb_match_flag, chrgitm_lv, '");
        sql.append(ChsCatalogConstants.LIST_TYPE_MEDICAL_SERVICE).append("' AS list_type ");
        sql.append("FROM wkf_activity_definition WHERE is_deleted = FALSE AND tenant_id = ? AND status_enum = ? ");
        args.add(tenantId);
        args.add(ACTIVE);
        appendMappingStatus(sql, mappingStatus, args);
        appendKeyword(sql, keyword, args);
        return sql.toString();
    }

    private String deviceBranch(String mappingStatus, String keyword, long tenantId, List<Object> args) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT '").append(TableNameConstants.ADM_DEVICE_DEFINITION).append("' AS product_table, ");
        sql.append("id AS product_id, '耗材' AS product_type_label, bus_no, name, yb_no, yb_match_flag, chrgitm_lv, '");
        sql.append(ChsCatalogConstants.LIST_TYPE_MEDICAL_CONSUMABLE).append("' AS list_type ");
        sql.append("FROM adm_device_definition WHERE is_deleted = FALSE AND tenant_id = ? AND status_enum = ? ");
        args.add(tenantId);
        args.add(ACTIVE);
        appendMappingStatus(sql, mappingStatus, args);
        appendKeyword(sql, keyword, args);
        return sql.toString();
    }

    private void appendMappingStatus(StringBuilder sql, String mappingStatus, List<Object> args) {
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

    private static String normalizeMappingStatus(String raw) {
        if (StringUtils.isBlank(raw)) {
            return STATUS_PENDING;
        }
        String normalized = raw.trim().toUpperCase();
        if (STATUS_MATCHED.equals(normalized) || STATUS_NO_YB.equals(normalized)) {
            return normalized;
        }
        return STATUS_PENDING;
    }

    private static String normalizeProductTable(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(trimmed)
            || TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(trimmed)
            || TableNameConstants.ADM_DEVICE_DEFINITION.equals(trimmed)) {
            return trimmed;
        }
        return null;
    }
}