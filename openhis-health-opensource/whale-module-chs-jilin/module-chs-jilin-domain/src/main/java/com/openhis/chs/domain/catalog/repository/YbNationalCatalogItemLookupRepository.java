/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.repository;

import com.openhis.chs.domain.catalog.entity.YbNationalCatalogItem;
import com.openhis.chs.domain.catalog.repository.support.YbNationalCatalogItemRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 国家目录镜像复杂只读查询（JdbcTemplate；Repository 接口号{@code @Query}） */
@Repository
@RequiredArgsConstructor
public class YbNationalCatalogItemLookupRepository implements YbNationalCatalogItemLookup {

    /**
     * 选码/分页列表列：显式排除 raw_json，避免大字段拖慢网络与反序列化；
     * 仍保留 raw_json 列别名（NULL）以兼容 {@link YbNationalCatalogItemRowMapper}。
     */
    private static final String PICKER_COLUMNS = """
        id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by,
        tenant_id, contract_id, list_type, hilist_code, hilist_name, chrgitm_lv, lmt_used_flag,
        begndate, enddate, vali_flag, spec, dosform, aprvno, prodentp_name, pinyin,
        NULL::text AS raw_json, source_infno, updt_time, source_file_qury_no, source_line_no
        """;

    private final JdbcTemplate jdbcTemplate;

    /** 1312 摘要去重分页（DISTINCT ON hilist_code） */
    @Override
    public Page<YbNationalCatalogItem> pageDistinctHilistCode(
        Long contractId,
        Long tenantId,
        String listType,
        boolean picker1312Only,
        String sourceInfno,
        String valiFlag,
        String keyword,
        String hilistCodePrefix,
        String aprvno,
        String spec,
        boolean excludePlatformExpired,
        Pageable pageable) {
        FilterBuilder filter = baseFilter(contractId, tenantId, listType);
        filter.and("(? = false OR source_infno = '1312' OR source_line_no IS NULL)", picker1312Only);
        filter.andOptionalEq("source_infno", sourceInfno);
        filter.andOptionalEq("vali_flag", valiFlag);
        filter.andOptionalKeyword(keyword);
        filter.andOptionalPrefix("hilist_code", hilistCodePrefix);
        filter.andOptionalLikeLower("aprvno", aprvno);
        filter.andOptionalLikeLower("spec", spec);
        if (excludePlatformExpired) {
            filter.andNotPlatformExpired(LocalDate.now());
        }

        String dataSql = """
            WITH deduped AS (
              SELECT DISTINCT ON (hilist_code)
            """
            + PICKER_COLUMNS
            + """
              FROM yb_national_catalog_item
              WHERE """
            + " "
            + filter.whereSql()
            + """
              ORDER BY hilist_code, updt_time DESC NULLS LAST, id DESC
            )
            SELECT d.*, t.total_count AS _total_count
            FROM deduped d
            CROSS JOIN (SELECT COUNT(*)::bigint AS total_count FROM deduped) t
            ORDER BY d.hilist_code
            LIMIT ? OFFSET ?
            """;

        List<Object> dataArgs = filter.argsCopy();
        dataArgs.add(pageable.getPageSize());
        dataArgs.add(pageable.getOffset());
        long[] totalHolder = { -1L };
        List<YbNationalCatalogItem> content = jdbcTemplate.query(
            dataSql,
            (rs, rowNum) -> {
                if (totalHolder[0] < 0) {
                    totalHolder[0] = rs.getLong("_total_count");
                }
                return YbNationalCatalogItemRowMapper.INSTANCE.mapRow(rs, rowNum);
            },
            dataArgs.toArray());
        long total = resolvePagedTotal(content, totalHolder[0], pageable, filter, true);
        return new PageImpl<>(content, pageable, total);
    }

    /** 药品选码：1312 摘要去重候选列表 */
    @Override
    public List<YbNationalCatalogItem> listDistinctForMedicationMatch(
        Long contractId,
        Long tenantId,
        String listType,
        String keyword,
        String aprvno,
        String spec,
        String hilistCodeExact,
        int limit) {
        FilterBuilder filter = baseFilter(contractId, tenantId, listType);
        filter.andRaw("(source_infno = '1312' OR source_line_no IS NULL)");
        filter.andRaw("vali_flag = '1'");
        filter.andNotPlatformExpired(LocalDate.now());
        filter.andOptionalKeywordNamePinyin(keyword);
        filter.andOptionalLikeLower("aprvno", aprvno);
        filter.andOptionalLikeLower("spec", spec);
        filter.andOptionalEq("hilist_code", hilistCodeExact);

        String sql = """
            SELECT * FROM (
              SELECT DISTINCT ON (hilist_code)
            """
            + PICKER_COLUMNS
            + """
              FROM yb_national_catalog_item
              WHERE """
            + " "
            + filter.whereSql()
            + """
              ORDER BY hilist_code, updt_time DESC NULLS LAST, id DESC
            ) deduped
            ORDER BY hilist_code
            LIMIT ?
            """;
        List<Object> args = filter.argsCopy();
        args.add(Math.max(1, limit));
        return jdbcTemplate.query(sql, YbNationalCatalogItemRowMapper.INSTANCE, args.toArray());
    }

    /** 药品选码：1301 明细候选列表（兼容旧调用） */
    @Override
    public List<YbNationalCatalogItem> list1301DetailForMedicationMatch(
        Long contractId,
        Long tenantId,
        String listType,
        String keyword,
        String aprvno,
        String spec,
        String hilistCodeExact,
        String valiFlag,
        int limit) {
        return listDetailForMedicationMatch(
            contractId,
            tenantId,
            listType,
            "1301",
            keyword,
            aprvno,
            spec,
            hilistCodeExact,
            valiFlag,
            limit);
    }

    /** 按 source_infno 查明细候选（1301/1305/1306） */
    @Override
    public List<YbNationalCatalogItem> listDetailForMedicationMatch(
        Long contractId,
        Long tenantId,
        String listType,
        String sourceInfno,
        String keyword,
        String aprvno,
        String spec,
        String hilistCodeExact,
        String valiFlag,
        int limit) {
        FilterBuilder filter = detailSourceFilter(contractId, tenantId, listType, sourceInfno, valiFlag);
        filter.andNotPlatformExpired(LocalDate.now());
        filter.andOptionalKeyword(keyword);
        filter.andOptionalLikeLower("aprvno", aprvno);
        filter.andOptionalLikeLower("spec", spec);
        filter.andOptionalEq("hilist_code", hilistCodeExact);

        String sql = """
            SELECT
            """
            + PICKER_COLUMNS
            + """
            FROM yb_national_catalog_item
            WHERE """
            + " "
            + filter.whereSql()
            + """
            ORDER BY hilist_code, updt_time DESC NULLS LAST, id DESC
            LIMIT ?
            """;
        List<Object> args = filter.argsCopy();
        args.add(Math.max(1, limit));
        return jdbcTemplate.query(sql, YbNationalCatalogItemRowMapper.INSTANCE, args.toArray());
    }

    /** 1301 明细分页（含 source_line_no） */
    @Override
    public Page<YbNationalCatalogItem> page1301Detail(
        Long contractId,
        Long tenantId,
        String listType,
        String valiFlag,
        String keyword,
        String hilistCodePrefix,
        String aprvno,
        String spec,
        boolean excludePlatformExpired,
        Pageable pageable) {
        FilterBuilder filter = detail1301Filter(contractId, tenantId, listType, valiFlag);
        if (excludePlatformExpired) {
            filter.andNotPlatformExpired(LocalDate.now());
        }
        filter.andOptionalKeyword(keyword);
        filter.andOptionalPrefix("hilist_code", hilistCodePrefix);
        filter.andOptionalLikeLower("aprvno", aprvno);
        filter.andOptionalLikeLower("spec", spec);

        String dataSql = """
            WITH filtered AS (
              SELECT
            """
            + PICKER_COLUMNS
            + """
              FROM yb_national_catalog_item
              WHERE """
            + " "
            + filter.whereSql()
            + """
            )
            SELECT f.*, t.total_count AS _total_count
            FROM filtered f
            CROSS JOIN (SELECT COUNT(*)::bigint AS total_count FROM filtered) t
            ORDER BY f.hilist_code, f.updt_time DESC NULLS LAST, f.id DESC
            LIMIT ? OFFSET ?
            """;

        List<Object> dataArgs = filter.argsCopy();
        dataArgs.add(pageable.getPageSize());
        dataArgs.add(pageable.getOffset());
        long[] totalHolder = { -1L };
        List<YbNationalCatalogItem> content = jdbcTemplate.query(
            dataSql,
            (rs, rowNum) -> {
                if (totalHolder[0] < 0) {
                    totalHolder[0] = rs.getLong("_total_count");
                }
                return YbNationalCatalogItemRowMapper.INSTANCE.mapRow(rs, rowNum);
            },
            dataArgs.toArray());
        long total = resolvePagedTotal(content, totalHolder[0], pageable, filter, false);
        return new PageImpl<>(content, pageable, total);
    }

    /** 契约+租户+list_type 基础 WHERE 片段 */
    private static FilterBuilder baseFilter(Long contractId, Long tenantId, String listType) {
        FilterBuilder filter = new FilterBuilder();
        filter.andRaw("contract_id = ?");
        filter.args().add(contractId);
        filter.andRaw("tenant_id = ?");
        filter.args().add(tenantId);
        filter.andRaw("is_deleted = false");
        filter.andOptionalEq("list_type", listType);
        return filter;
    }

    /** 文件明细筛选（指定 source_infno，且 source_line_no 非空） */
    private static FilterBuilder detailSourceFilter(
        Long contractId,
        Long tenantId,
        String listType,
        String sourceInfno,
        String valiFlag) {
        FilterBuilder filter = baseFilter(contractId, tenantId, listType);
        String infno = sourceInfno == null || sourceInfno.isBlank() ? "1301" : sourceInfno.trim();
        filter.and("source_infno = ?", infno);
        filter.andRaw("source_line_no IS NOT NULL");
        filter.andOptionalEq("vali_flag", valiFlag);
        return filter;
    }

    /** 1301 明细筛选（兼容 page1301Detail） */
    private static FilterBuilder detail1301Filter(
        Long contractId,
        Long tenantId,
        String listType,
        String valiFlag) {
        return detailSourceFilter(contractId, tenantId, listType, "1301", valiFlag);
    }

    /** COUNT 查询，null 视为 0 */
    private long queryCount(String sql, List<Object> args) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args.toArray());
        if (count == null) {
            return 0L;
        }
        return count;
    }

    /**
     * 分页总数：优先取结果行上的窗口/CTE 计数；翻页越界导致空页时回退单独 COUNT。
     */
    private long resolvePagedTotal(
            List<YbNationalCatalogItem> content,
            long totalFromRows,
            Pageable pageable,
            FilterBuilder filter,
            boolean distinctHilistCode) {
        if (totalFromRows >= 0) {
            return totalFromRows;
        }
        if (content.isEmpty() && pageable.getOffset() == 0) {
            return 0L;
        }
        String countSql = distinctHilistCode
            ? """
                SELECT COUNT(*) FROM (
                  SELECT DISTINCT hilist_code
                  FROM yb_national_catalog_item
                  WHERE """
                + " "
                + filter.whereSql()
                + """
                ) c
                """
            : """
                SELECT COUNT(*)
                FROM yb_national_catalog_item
                WHERE """
                + " "
                + filter.whereSql();
        return queryCount(countSql, filter.args());
    }

    /** 动态 SQL WHERE 子句构建器 */
    private static final class FilterBuilder {

        private final List<String> clauses = new ArrayList<>();
        private final List<Object> params = new ArrayList<>();

        /** 绑定参数列表（可变） */
        List<Object> args() {
            return params;
        }

        /** 绑定参数副本（追加 LIMIT/OFFSET 用） */
        List<Object> argsCopy() {
            return new ArrayList<>(params);
        }

        /** AND 连接的 WHERE 片段 */
        String whereSql() {
            return String.join(" AND ", clauses);
        }

        /** 追加无参条件子句 */
        void andRaw(String clause) {
            clauses.add(clause);
        }

        /** 追加带单参数的条件子句 */
        void and(String clause, Object value) {
            clauses.add(clause);
            params.add(value);
        }

        /** 可选等值：value 为 null/blank 时跳过（避免 JDBC null 绑定导致 PG 无法推断 ? 类型） */
        void andOptionalEq(String column, String value) {
            if (value == null || value.isBlank()) {
                return;
            }
            clauses.add(column + " = ?");
            params.add(value);
        }

        /** 可选前缀匹配：prefix 为 null/blank 时跳过 */
        void andOptionalPrefix(String column, String prefix) {
            if (prefix == null || prefix.isBlank()) {
                return;
            }
            clauses.add(column + " LIKE CONCAT(?, '%')");
            params.add(prefix);
        }

        /** 可选模糊匹配（ILIKE，可走 pg_trgm） */
        void andOptionalLikeLower(String column, String value) {
            if (value == null || value.isBlank()) {
                return;
            }
            clauses.add(column + " ILIKE CONCAT('%', ?, '%')");
            params.add(value);
        }

        /** 可选关键词：hilist_name / pinyin / hilist_code（ILIKE + gin_trgm） */
        void andOptionalKeyword(String keyword) {
            if (keyword == null || keyword.isBlank()) {
                return;
            }
            clauses.add("""
                (
                  hilist_name ILIKE CONCAT('%', ?, '%')
                  OR pinyin ILIKE CONCAT('%', ?, '%')
                  OR hilist_code ILIKE CONCAT('%', ?, '%')
                )
                """);
            params.add(keyword);
            params.add(keyword);
            params.add(keyword);
        }

        /** 可选关键词：hilist_name / pinyin（药品摘要去重） */
        void andOptionalKeywordNamePinyin(String keyword) {
            if (keyword == null || keyword.isBlank()) {
                return;
            }
            clauses.add("""
                (
                  hilist_name ILIKE CONCAT('%', ?, '%')
                  OR pinyin ILIKE CONCAT('%', ?, '%')
                )
                """);
            params.add(keyword);
            params.add(keyword);
        }

        /**
         * 排除平台已过期：与 {@code NationalCatalogSelectableComposer} 一致
         * （enddate 为空或年份≤1 视为无结束日；结束日当天仍可选）。
         */
        void andNotPlatformExpired(LocalDate asOf) {
            LocalDate day = asOf == null ? LocalDate.now() : asOf;
            clauses.add("(enddate IS NULL OR EXTRACT(YEAR FROM enddate) <= 1 OR enddate >= ?)");
            params.add(java.sql.Date.valueOf(day));
        }
    }
}
