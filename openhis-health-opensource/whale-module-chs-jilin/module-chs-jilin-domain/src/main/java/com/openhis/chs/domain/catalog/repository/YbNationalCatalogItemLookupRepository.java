/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.repository;

import com.openhis.chs.domain.catalog.entity.YbNationalCatalogItem;
import com.openhis.chs.domain.catalog.repository.support.YbNationalCatalogItemRowMapper;
import com.openhis.chs.domain.catalog.support.NationalCatalogPackHint;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 国家目录镜像复杂只读查询（JdbcTemplate；Repository 接口号{@code @Query}） */
@Repository
@RequiredArgsConstructor
public class YbNationalCatalogItemLookupRepository implements YbNationalCatalogItemLookup {

    private static final String PICKER_COLUMNS = """
        id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by,
        tenant_id, contract_id, list_type, hilist_code, hilist_name, chrgitm_lv, lmt_used_flag,
        begndate, enddate, vali_flag, spec, dosform, aprvno, prodentp_name, pinyin,
        NULL::text AS raw_json, source_infno, updt_time, source_file_qury_no, source_line_no,
        min_pac_cnt, min_pacunt, min_prepunt, min_prcunt
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
        // 不用 (? = false) 布尔绑定：部分 PG/JDBC 组合会报无法推断参数类型 → bad SQL grammar
        if (picker1312Only) {
            filter.andRaw("(source_infno = '1312' OR source_line_no IS NULL)");
        }
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
        filter.andOptionalHilistCodeMatch(hilistCodeExact);

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
        filter.andOptionalHilistCodeMatch(hilistCodeExact);

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

    /**
     * 按国家码批量取包装/计价单位（DISTINCT ON hilist_code；优先 1301 且已落列有值的行）。
     *
     * <p>读持久化列 {@code min_pac_*} / {@code min_prcunt}，禁止再扫 {@code raw_json}。</p>
     */
    @Override
    public Map<String, NationalCatalogPackHint> loadPackHintsByHilistCodes(
        Long contractId,
        Long tenantId,
        Collection<String> hilistCodes) {
        Map<String, NationalCatalogPackHint> out = new LinkedHashMap<>();
        if (contractId == null || tenantId == null || hilistCodes == null || hilistCodes.isEmpty()) {
            return out;
        }
        Set<String> codes = new LinkedHashSet<>();
        for (String code : hilistCodes) {
            if (code != null && !code.isBlank()) {
                codes.add(code.trim());
            }
        }
        if (codes.isEmpty()) {
            return out;
        }
        String placeholders = String.join(",", codes.stream().map(c -> "?").toList());
        String sql = """
            SELECT DISTINCT ON (hilist_code)
              hilist_code, min_pac_cnt, min_pacunt, min_prepunt, min_prcunt
            FROM yb_national_catalog_item
            WHERE contract_id = ?
              AND tenant_id = ?
              AND is_deleted = false
              AND hilist_code IN (%s)
              AND (
                min_pac_cnt IS NOT NULL
                OR min_pacunt IS NOT NULL
                OR min_prepunt IS NOT NULL
                OR min_prcunt IS NOT NULL
              )
            ORDER BY hilist_code,
              CASE WHEN source_infno = '1301' THEN 0 ELSE 1 END,
              updated_at DESC NULLS LAST
            """.formatted(placeholders);
        List<Object> args = new ArrayList<>(2 + codes.size());
        args.add(contractId);
        args.add(tenantId);
        args.addAll(codes);
        jdbcTemplate.query(sql, rs -> {
            NationalCatalogPackHint hint = NationalCatalogPackHint.of(
                rs.getString("hilist_code"),
                rs.getString("min_pac_cnt"),
                rs.getString("min_pacunt"),
                rs.getString("min_prepunt"),
                rs.getString("min_prcunt"));
            // 无计价/包装任一字段则跳过（等同未命中目录包装）
            if (hint == null || (!hint.hasPricingUnit() && hint.minPacCntAsDecimal() == null)) {
                return;
            }
            out.put(hint.hilistCode(), hint);
        }, args.toArray());
        return out;
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

        /**
         * 医保码匹配：精确或包含（如输入数字段命中带字母前缀的码）。
         *
         * @param hilistCode 医保目录编码，null/blank 时跳过
         */
        void andOptionalHilistCodeMatch(String hilistCode) {
            if (hilistCode == null || hilistCode.isBlank()) {
                return;
            }
            String code = hilistCode.trim();
            clauses.add("(hilist_code = ? OR hilist_code ILIKE ?)");
            params.add(code);
            params.add("%" + code + "%");
        }

        /** 可选前缀匹配：prefix 为 null/blank 时跳过 */
        void andOptionalPrefix(String column, String prefix) {
            if (prefix == null || prefix.isBlank()) {
                return;
            }
            // 完整模式作绑定参数，避免 CONCAT(?, '%') 导致 PG 无法推断 ? 类型
            clauses.add(column + " LIKE ?");
            params.add(prefix + "%");
        }

        /** 可选模糊匹配（ILIKE，可走 pg_trgm） */
        void andOptionalLikeLower(String column, String value) {
            if (value == null || value.isBlank()) {
                return;
            }
            clauses.add(column + " ILIKE ?");
            params.add("%" + value + "%");
        }

        /** 可选关键词：hilist_name / pinyin / hilist_code（ILIKE） */
        void andOptionalKeyword(String keyword) {
            if (keyword == null || keyword.isBlank()) {
                return;
            }
            // 完整 '%kw%' 绑定为 text，避免 CONCAT('%', ?, '%') 触发 PG「无法推断参数类型」
            String pattern = "%" + keyword + "%";
            clauses.add("""
                (
                  hilist_name ILIKE ?
                  OR pinyin ILIKE ?
                  OR hilist_code ILIKE ?
                )
                """);
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }

        /**
         * 可选关键词：名称 / 拼音 / 医保编码（与 {@link #andOptionalKeyword} 同口径）。
         * <p>选码 1312 去重候选也须能按 hilist_code 检索。</p>
         */
        void andOptionalKeywordNamePinyin(String keyword) {
            andOptionalKeyword(keyword);
        }

        /**
         * 排除平台已过期：与 {@code NationalCatalogSelectableComposer} / JPA Criteria 一致
         * （enddate 空或 ≤0001-12-31 视为无结束日；结束日当天仍可选）。
         * <p>不用 EXTRACT(?)/布尔绑定，减少 PG「无法推断参数类型」被 Spring 包成 bad SQL grammar。</p>
         */
        void andNotPlatformExpired(LocalDate asOf) {
            LocalDate day = asOf == null ? LocalDate.now() : asOf;
            clauses.add("(enddate IS NULL OR enddate <= DATE '0001-12-31' OR enddate >= ?)");
            params.add(java.sql.Date.valueOf(day));
        }
    }
}
