/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.repository;

import com.openhis.whale.core.application.util.StringUtils;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;
import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.chs.domain.catalog.command.UpsertNationalCatalogRowCommand;
import com.openhis.chs.domain.catalog.support.CatalogRowFieldNormalizer;
import com.openhis.chs.domain.catalog.support.NationalCatalogPackFieldExtractor;
import com.openhis.whale.core.infrastructure.id.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 国家目录镜像 PostgreSQL 批量写入（纯 INSERT / INSERT ON CONFLICT），避免逐行 SELECT */
@Repository
@RequiredArgsConstructor
public class YbNationalCatalogBulkUpsertRepository {

    private static final String INSERT_FILE_PACKAGE = """
        INSERT INTO yb_national_catalog_item (
            id, contract_id, list_type, tenant_id,
            source_file_qury_no, source_line_no, hilist_code, hilist_name,
            chrgitm_lv, lmt_used_flag, begndate, enddate, vali_flag,
            spec, dosform, aprvno, prodentp_name, pinyin, raw_json,
            min_pac_cnt, min_pacunt, min_prepunt, min_prcunt,
            source_infno, updt_time,
            created_at, updated_at, created_by, updated_by, is_deleted
        ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,false)
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
            min_pac_cnt = EXCLUDED.min_pac_cnt,
            min_pacunt = EXCLUDED.min_pacunt,
            min_prepunt = EXCLUDED.min_prepunt,
            min_prcunt = EXCLUDED.min_prcunt,
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
            spec, dosform, aprvno, prodentp_name, pinyin, raw_json,
            min_pac_cnt, min_pacunt, min_prepunt, min_prcunt,
            source_infno, updt_time,
            created_at, updated_at, created_by, updated_by, is_deleted
        ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,false)
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
            min_pac_cnt = EXCLUDED.min_pac_cnt,
            min_pacunt = EXCLUDED.min_pacunt,
            min_prepunt = EXCLUDED.min_prepunt,
            min_prcunt = EXCLUDED.min_prcunt,
            source_infno = EXCLUDED.source_infno,
            updt_time = EXCLUDED.updt_time,
            updated_at = EXCLUDED.updated_at,
            updated_by = EXCLUDED.updated_by
        """;

    private final JdbcTemplate jdbcTemplate;

    /** 13xx 文件包首次入库（INSERT，键 source_file_qury_no + source_line_no） */
    public int bulkInsertFilePackageRows(
            Long contractId,
            Long tenantId,
            Long operatorId,
            String sourceFileQuryNo,
            List<UpsertNationalCatalogRowCommand> rows) {
        return executeBatch(INSERT_FILE_PACKAGE, contractId, tenantId, operatorId, sourceFileQuryNo, rows);
    }

    /** 13xx 文件包重导（INSERT ON CONFLICT DO UPDATE） */
    public int bulkUpsertFilePackageRows(
            Long contractId,
            Long tenantId,
            Long operatorId,
            String sourceFileQuryNo,
            List<UpsertNationalCatalogRowCommand> rows) {
        return executeBatch(UPSERT_FILE_PACKAGE, contractId, tenantId, operatorId, sourceFileQuryNo, rows);
    }

    /** 1312 平台拉取结果批量 upsert（键 hilist_code） */
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

    /** 执行 JDBC batch 并返回有效行数 */
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

    /** 过滤 hilist_code 为空的行 */
    private static List<UpsertNationalCatalogRowCommand> filterValid(List<UpsertNationalCatalogRowCommand> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<UpsertNationalCatalogRowCommand> valid = new ArrayList<>(rows.size());
        for (UpsertNationalCatalogRowCommand row : rows) {
            if (row != null && isNotEmpty(row.getHilistCode())) {
                valid.add(row);
            }
        }
        return valid;
    }

    /** 绑定 13xx 文件包行参数 */
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

    /** 绑定 1312 行参数（source_line_no 为空） */
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

    /** 绑定目录镜像公共列（含字段规范化与包装单位落列） */
    private static void bindCommonColumns(
            java.sql.PreparedStatement ps,
            int startIndex,
            Long operatorId,
            LocalDateTime now,
            UpsertNationalCatalogRowCommand row) throws java.sql.SQLException {
        int i = startIndex;
        ps.setString(i++, CatalogRowFieldNormalizer.truncateVarchar(
            row.getHilistCode(), CatalogRowFieldNormalizer.LEN_HILIST_CODE));
        ps.setString(i++, CatalogRowFieldNormalizer.truncateVarchar(
            row.getHilistName(), CatalogRowFieldNormalizer.LEN_HILIST_NAME));
        ps.setString(i++, CatalogRowFieldNormalizer.normalizeChrgitmLv(row.getChrgitmLv()));
        ps.setString(i++, CatalogRowFieldNormalizer.normalizeLmtUsedFlag(row.getLmtUsedFlag()));
        ps.setDate(i++, toSqlDate(row.getBegndate()));
        ps.setDate(i++, toSqlDate(row.getEnddate()));
        ps.setString(i++, CatalogRowFieldNormalizer.normalizeValiFlag(row.getValiFlag()));
        ps.setString(i++, CatalogRowFieldNormalizer.truncateVarchar(
            row.getSpec(), CatalogRowFieldNormalizer.LEN_SPEC));
        ps.setString(i++, CatalogRowFieldNormalizer.truncateVarchar(
            row.getDosform(), CatalogRowFieldNormalizer.LEN_DOSFORM));
        ps.setString(i++, CatalogRowFieldNormalizer.truncateVarchar(
            row.getAprvno(), CatalogRowFieldNormalizer.LEN_APRVNO));
        ps.setString(i++, CatalogRowFieldNormalizer.truncateVarchar(
            row.getProdentpName(), CatalogRowFieldNormalizer.LEN_PRODENTP_NAME));
        ps.setString(i++, CatalogRowFieldNormalizer.truncateVarchar(
            row.getPinyin(), CatalogRowFieldNormalizer.LEN_PINYIN));
        ps.setString(i++, row.getRawJson());
        NationalCatalogPackFieldExtractor.PackFields pack = resolvePackFields(row);
        ps.setString(i++, CatalogRowFieldNormalizer.truncateVarchar(
            pack.minPacCnt(), CatalogRowFieldNormalizer.LEN_MIN_PAC_CNT));
        ps.setString(i++, CatalogRowFieldNormalizer.truncateVarchar(
            pack.minPacunt(), CatalogRowFieldNormalizer.LEN_MIN_PACUNT));
        ps.setString(i++, CatalogRowFieldNormalizer.truncateVarchar(
            pack.minPrepunt(), CatalogRowFieldNormalizer.LEN_MIN_PREPUNT));
        ps.setString(i++, CatalogRowFieldNormalizer.truncateVarchar(
            pack.minPrcunt(), CatalogRowFieldNormalizer.LEN_MIN_PRCUNT));
        ps.setString(i++, row.getSourceInfno());
        ps.setTimestamp(i++, toTimestamp(row.getUpdtTime()));
        ps.setTimestamp(i++, Timestamp.valueOf(now));
        ps.setTimestamp(i++, Timestamp.valueOf(now));
        ps.setObject(i++, operatorId);
        ps.setObject(i, operatorId);
    }

    /**
     * 优先用命令显式包装字段；否则从 {@code raw_json} 数组下标回落（1301 文件 / 历史兼容）。
     */
    private static NationalCatalogPackFieldExtractor.PackFields resolvePackFields(
            UpsertNationalCatalogRowCommand row) {
        if (row.getMinPacCnt() != null
            || row.getMinPacunt() != null
            || row.getMinPrepunt() != null
            || row.getMinPrcunt() != null) {
            return new NationalCatalogPackFieldExtractor.PackFields(
                row.getMinPacCnt(),
                row.getMinPacunt(),
                row.getMinPrepunt(),
                row.getMinPrcunt());
        }
        return NationalCatalogPackFieldExtractor.fromRawJson(row.getRawJson());
    }

    /** {@link java.time.LocalDate} 转 {@link java.sql.Date} */
    private static Date toSqlDate(java.time.LocalDate value) {
        if (value == null) {
            return null;
        }
        return Date.valueOf(value);
    }

    /** {@link LocalDateTime} 转 {@link Timestamp} */
    private static Timestamp toTimestamp(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return Timestamp.valueOf(value);
    }
}
