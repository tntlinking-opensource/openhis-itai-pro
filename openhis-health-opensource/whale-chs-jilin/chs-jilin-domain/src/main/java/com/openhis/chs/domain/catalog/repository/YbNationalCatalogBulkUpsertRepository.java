/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.catalog.repository;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.chs.domain.catalog.command.UpsertNationalCatalogRowCommand;
import com.openhis.chs.domain.catalog.support.CatalogRowFieldNormalizer;
import com.openhis.whale.core.infrastructure.id.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 国家目录镜像 PostgreSQL 批量写入（纯 INSERT / INSERT ON CONFLICT），避免逐行 SELECT。
 */
@Repository
@RequiredArgsConstructor
public class YbNationalCatalogBulkUpsertRepository {

    private static final String INSERT_FILE_PACKAGE = """
        INSERT INTO yb_national_catalog_item (
            id, contract_id, list_type, tenant_id,
            source_file_qury_no, source_line_no, hilist_code, hilist_name,
            chrgitm_lv, lmt_used_flag, begndate, enddate, vali_flag,
            spec, dosform, aprvno, prodentp_name, pinyin, raw_json, source_infno, updt_time,
            created_at, updated_at, created_by, updated_by, is_deleted
        ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,false)
        """;

    private static final String UPSERT_FILE_PACKAGE = INSERT_FILE_PACKAGE + """
        ON CONFLICT (contract_id, list_type, tenant_id, source_file_qury_no, source_line_no)
            WHERE is_deleted = false AND source_line_no IS NOT NULL
        DO UPDATE SET
            hilist_code = EXCLUDED.hilist_code,
            hilist_name = EXCLUDED.hilist_name,
            chrgitm_lv = EXCLUDED.chrgitm_lv,
            lmt_used_flag = EXCLUDED.lmt_used_flag,
            begndate = EXCLUDED.begndate,
            enddate = EXCLUDED.enddate,
            vali_flag = EXCLUDED.vali_flag,
            spec = EXCLUDED.spec,
            dosform = EXCLUDED.dosform,
            aprvno = EXCLUDED.aprvno,
            prodentp_name = EXCLUDED.prodentp_name,
            pinyin = EXCLUDED.pinyin,
            raw_json = EXCLUDED.raw_json,
            source_infno = EXCLUDED.source_infno,
            updt_time = EXCLUDED.updt_time,
            updated_at = EXCLUDED.updated_at,
            updated_by = EXCLUDED.updated_by
        """;

    private static final String UPSERT_1312 = """
        INSERT INTO yb_national_catalog_item (
            id, contract_id, list_type, tenant_id,
            source_file_qury_no, source_line_no, hilist_code, hilist_name,
            chrgitm_lv, lmt_used_flag, begndate, enddate, vali_flag,
            spec, dosform, aprvno, prodentp_name, pinyin, raw_json, source_infno, updt_time,
            created_at, updated_at, created_by, updated_by, is_deleted
        ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,false)
        ON CONFLICT (contract_id, list_type, hilist_code, tenant_id)
            WHERE is_deleted = false AND source_line_no IS NULL
        DO UPDATE SET
            hilist_name = EXCLUDED.hilist_name,
            chrgitm_lv = EXCLUDED.chrgitm_lv,
            lmt_used_flag = EXCLUDED.lmt_used_flag,
            begndate = EXCLUDED.begndate,
            enddate = EXCLUDED.enddate,
            vali_flag = EXCLUDED.vali_flag,
            spec = EXCLUDED.spec,
            dosform = EXCLUDED.dosform,
            aprvno = EXCLUDED.aprvno,
            prodentp_name = EXCLUDED.prodentp_name,
            pinyin = EXCLUDED.pinyin,
            raw_json = EXCLUDED.raw_json,
            source_infno = EXCLUDED.source_infno,
            updt_time = EXCLUDED.updt_time,
            updated_at = EXCLUDED.updated_at,
            updated_by = EXCLUDED.updated_by
        """;

    private final JdbcTemplate jdbcTemplate;

    /** 13xx 文件包首次入库（纯 INSERT，键 source_file_qury_no + source_line_no）。 */
    public int bulkInsertFilePackageRows(
            Long contractId,
            Long tenantId,
            Long operatorId,
            String sourceFileQuryNo,
            List<UpsertNationalCatalogRowCommand> rows) {
        return executeBatch(INSERT_FILE_PACKAGE, contractId, tenantId, operatorId, sourceFileQuryNo, rows);
    }

    /** 13xx 文件包重导（INSERT ON CONFLICT DO UPDATE）。 */
    public int bulkUpsertFilePackageRows(
            Long contractId,
            Long tenantId,
            Long operatorId,
            String sourceFileQuryNo,
            List<UpsertNationalCatalogRowCommand> rows) {
        return executeBatch(UPSERT_FILE_PACKAGE, contractId, tenantId, operatorId, sourceFileQuryNo, rows);
    }

    public int bulkUpsert1312Rows(
            Long contractId,
            Long tenantId,
            Long operatorId,
            List<UpsertNationalCatalogRowCommand> rows) {
        List<UpsertNationalCatalogRowCommand> valid = filterValid(rows);
        if (valid.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.batchUpdate(UPSERT_1312, valid, valid.size(), (ps, row) -> {
            bind1312Row(ps, contractId, tenantId, operatorId, now, row);
        });
        return valid.size();
    }

    private int executeBatch(
            String sql,
            Long contractId,
            Long tenantId,
            Long operatorId,
            String sourceFileQuryNo,
            List<UpsertNationalCatalogRowCommand> rows) {
        List<UpsertNationalCatalogRowCommand> valid = filterValid(rows);
        if (valid.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        String fileQuryNo = trimToNull(sourceFileQuryNo);
        jdbcTemplate.batchUpdate(sql, valid, valid.size(), (ps, row) -> {
            bindFilePackageRow(ps, contractId, tenantId, operatorId, now, fileQuryNo, row);
        });
        return valid.size();
    }

    private static List<UpsertNationalCatalogRowCommand> filterValid(List<UpsertNationalCatalogRowCommand> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<UpsertNationalCatalogRowCommand> valid = new ArrayList<>(rows.size());
        for (UpsertNationalCatalogRowCommand row : rows) {
            if (row != null && StringUtils.isNotBlank(row.getHilistCode())) {
                valid.add(row);
            }
        }
        return valid;
    }

    private static void bindFilePackageRow(
            java.sql.PreparedStatement ps,
            Long contractId,
            Long tenantId,
            Long operatorId,
            LocalDateTime now,
            String sourceFileQuryNo,
            UpsertNationalCatalogRowCommand row) throws java.sql.SQLException {
        String listType = StringUtils.defaultIfBlank(row.getListType(), "101");
        ps.setLong(1, SnowflakeIdGenerator.nextId());
        ps.setLong(2, contractId);
        ps.setString(3, listType);
        ps.setLong(4, tenantId);
        ps.setString(5, sourceFileQuryNo);
        if (row.getSourceLineNo() != null) {
            ps.setInt(6, row.getSourceLineNo());
        } else {
            ps.setNull(6, java.sql.Types.INTEGER);
        }
        bindCommonColumns(ps, 7, operatorId, now, row);
    }

    private static void bind1312Row(
            java.sql.PreparedStatement ps,
            Long contractId,
            Long tenantId,
            Long operatorId,
            LocalDateTime now,
            UpsertNationalCatalogRowCommand row) throws java.sql.SQLException {
        String listType = StringUtils.defaultIfBlank(row.getListType(), "101");
        ps.setLong(1, SnowflakeIdGenerator.nextId());
        ps.setLong(2, contractId);
        ps.setString(3, listType);
        ps.setLong(4, tenantId);
        ps.setNull(5, java.sql.Types.VARCHAR);
        ps.setNull(6, java.sql.Types.INTEGER);
        bindCommonColumns(ps, 7, operatorId, now, row);
    }

    private static void bindCommonColumns(
            java.sql.PreparedStatement ps,
            int startIndex,
            Long operatorId,
            LocalDateTime now,
            UpsertNationalCatalogRowCommand row) throws java.sql.SQLException {
        int i = startIndex;
        ps.setString(i++, row.getHilistCode().trim());
        ps.setString(i++, row.getHilistName());
        ps.setString(i++, CatalogRowFieldNormalizer.normalizeChrgitmLv(row.getChrgitmLv()));
        ps.setString(i++, CatalogRowFieldNormalizer.normalizeLmtUsedFlag(row.getLmtUsedFlag()));
        ps.setDate(i++, toSqlDate(row.getBegndate()));
        ps.setDate(i++, toSqlDate(row.getEnddate()));
        ps.setString(i++, CatalogRowFieldNormalizer.normalizeValiFlag(row.getValiFlag()));
        ps.setString(i++, row.getSpec());
        ps.setString(i++, row.getDosform());
        ps.setString(i++, row.getAprvno());
        ps.setString(i++, row.getProdentpName());
        ps.setString(i++, row.getPinyin());
        ps.setString(i++, row.getRawJson());
        ps.setString(i++, row.getSourceInfno());
        ps.setTimestamp(i++, toTimestamp(row.getUpdtTime()));
        ps.setTimestamp(i++, Timestamp.valueOf(now));
        ps.setTimestamp(i++, Timestamp.valueOf(now));
        ps.setObject(i++, operatorId);
        ps.setObject(i, operatorId);
    }

    private static Date toSqlDate(java.time.LocalDate value) {
        if (value == null) {
            return null;
        }
        return Date.valueOf(value);
    }

    private static Timestamp toTimestamp(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return Timestamp.valueOf(value);
    }
}