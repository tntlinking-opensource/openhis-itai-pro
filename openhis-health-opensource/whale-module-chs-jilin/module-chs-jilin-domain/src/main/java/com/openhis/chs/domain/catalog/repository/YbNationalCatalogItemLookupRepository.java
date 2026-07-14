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

import java.util.ArrayList;
import java.util.List;

/**
 * 国家目录镜像复杂只读查询（JdbcTemplate；Repository 接口号{@code @Query}） */
@Repository
@RequiredArgsConstructor
public class YbNationalCatalogItemLookupRepository implements YbNationalCatalogItemLookup {

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
        Pageable pageable) {
        FilterBuilder filter = baseFilter(contractId, tenantId, listType);
        filter.and("(? = false OR source_infno = '1312' OR source_line_no IS NULL)", picker1312Only);
        filter.andOptionalEq("source_infno", sourceInfno);
        filter.andOptionalEq("vali_flag", valiFlag);
        filter.andOptionalKeyword(keyword);
        filter.andOptionalPrefix("hilist_code", hilistCodePrefix);
        filter.andOptionalLikeLower("aprvno", aprvno);
        filter.andOptionalLikeLower("spec", spec);

        String inner = """
            SELECT DISTINCT ON (hilist_code) *
            FROM yb_national_catalog_item
            WHERE """
            + " "
            + filter.whereSql()
            + """
             ORDER BY hilist_code, updt_time DESC NULLS LAST, id DESC
            """;
        String dataSql = """
            SELECT * FROM (
            """
            + inner
            + """
            ) deduped
            ORDER BY hilist_code
            LIMIT ? OFFSET ?
            """;
        String countSql = """
            SELECT COUNT(*) FROM (
              SELECT DISTINCT hilist_code
              FROM yb_national_catalog_item
              WHERE """
            + " "
            + filter.whereSql()
            + """
            ) c
            """;

        long total = queryCount(countSql, filter.args());
        List<Object> dataArgs = filter.argsCopy();
        dataArgs.add(pageable.getPageSize());
        dataArgs.add(pageable.getOffset());
        List<YbNationalCatalogItem> content = jdbcTemplate.query(
            dataSql, YbNationalCatalogItemRowMapper.INSTANCE, dataArgs.toArray());
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
        filter.andOptionalKeywordNamePinyin(keyword);
        filter.andOptionalLikeLower("aprvno", aprvno);
        filter.andOptionalLikeLower("spec", spec);
        filter.andOptionalEq("hilist_code", hilistCodeExact);

        String sql = """
            SELECT * FROM (
              SELECT DISTINCT ON (hilist_code) *
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

    /** 药品选码：1301 明细候选列表 */
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
        FilterBuilder filter = detail1301Filter(contractId, tenantId, listType, valiFlag);
        filter.andOptionalKeyword(keyword);
        filter.andOptionalLikeLower("aprvno", aprvno);
        filter.andOptionalLikeLower("spec", spec);
        filter.andOptionalEq("hilist_code", hilistCodeExact);

        String sql = """
            SELECT *
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
        Pageable pageable) {
        FilterBuilder filter = detail1301Filter(contractId, tenantId, listType, valiFlag);
        filter.andOptionalKeyword(keyword);
        filter.andOptionalPrefix("hilist_code", hilistCodePrefix);
        filter.andOptionalLikeLower("aprvno", aprvno);
        filter.andOptionalLikeLower("spec", spec);

        String dataSql = """
            SELECT *
            FROM yb_national_catalog_item
            WHERE """
            + " "
            + filter.whereSql()
            + """
            ORDER BY hilist_code, updt_time DESC NULLS LAST, id DESC
            LIMIT ? OFFSET ?
            """;
        String countSql = """
            SELECT COUNT(*)
            FROM yb_national_catalog_item
            WHERE """
            + " "
            + filter.whereSql();

        long total = queryCount(countSql, filter.args());
        List<Object> dataArgs = filter.argsCopy();
        dataArgs.add(pageable.getPageSize());
        dataArgs.add(pageable.getOffset());
        List<YbNationalCatalogItem> content = jdbcTemplate.query(
            dataSql, YbNationalCatalogItemRowMapper.INSTANCE, dataArgs.toArray());
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

    /** 1301 明细筛选（source_infno=1301 且 source_line_no 非空） */
    private static FilterBuilder detail1301Filter(
        Long contractId,
        Long tenantId,
        String listType,
        String valiFlag) {
        FilterBuilder filter = baseFilter(contractId, tenantId, listType);
        filter.andRaw("source_infno = '1301'");
        filter.andRaw("source_line_no IS NOT NULL");
        filter.andOptionalEq("vali_flag", valiFlag);
        return filter;
    }

    /** COUNT 查询，null 视为 0 */
    private long queryCount(String sql, List<Object> args) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args.toArray());
        if (count == null) {
            return 0L;
        }
        return count;
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

        /** 可选等值：value 为 null 时跳过 */
        void andOptionalEq(String column, String value) {
            clauses.add("(? IS NULL OR " + column + " = ?)");
            params.add(value);
            params.add(value);
        }

        /** 可选前缀匹配：prefix 为 null 时跳过 */
        void andOptionalPrefix(String column, String prefix) {
            clauses.add("(? IS NULL OR " + column + " LIKE CONCAT(?, '%'))");
            params.add(prefix);
            params.add(prefix);
        }

        /** 可选模糊匹配（列转小写） */
        void andOptionalLikeLower(String column, String value) {
            clauses.add("(? IS NULL OR LOWER(" + column + ") LIKE LOWER(CONCAT('%', ?, '%')))");
            params.add(value);
            params.add(value);
        }

        /** 可选关键词：hilist_name / pinyin / hilist_code */
        void andOptionalKeyword(String keyword) {
            clauses.add("""
                (? IS NULL OR (
                  LOWER(hilist_name) LIKE LOWER(CONCAT('%', ?, '%'))
                  OR LOWER(pinyin) LIKE LOWER(CONCAT('%', ?, '%'))
                  OR LOWER(hilist_code) LIKE LOWER(CONCAT('%', ?, '%'))
                ))
                """);
            params.add(keyword);
            params.add(keyword);
            params.add(keyword);
            params.add(keyword);
        }

        /** 可选关键词：hilist_name / pinyin（药品摘要去重） */
        void andOptionalKeywordNamePinyin(String keyword) {
            clauses.add("""
                (? IS NULL OR (
                  LOWER(hilist_name) LIKE LOWER(CONCAT('%', ?, '%'))
                  OR LOWER(pinyin) LIKE LOWER(CONCAT('%', ?, '%'))
                ))
                """);
            params.add(keyword);
            params.add(keyword);
            params.add(keyword);
        }
    }
}
