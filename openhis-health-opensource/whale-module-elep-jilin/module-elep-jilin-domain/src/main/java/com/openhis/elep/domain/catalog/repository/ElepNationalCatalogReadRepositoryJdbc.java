/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.catalog.repository;

import com.openhis.elep.domain.catalog.ElepCatalogConstants;
import com.openhis.elep.domain.catalog.model.ElepNationalCatalogRow;
import com.openhis.elep.domain.catalog.repository.support.ElepNationalCatalogRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** {@link ElepNationalCatalogReadRepository} JdbcTemplate 实现。 */
@Repository
@RequiredArgsConstructor
public class ElepNationalCatalogReadRepositoryJdbc implements ElepNationalCatalogReadRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public long resolveCatalogEpochMillis(Long contractId, Long tenantId) {
        try {
            Long epoch = jdbcTemplate.queryForObject("""
                    SELECT COALESCE(
                      EXTRACT(EPOCH FROM MAX(GREATEST(
                        COALESCE(updt_time, updated_at),
                        COALESCE(updated_at, updt_time)
                      ))) * 1000,
                      0
                    )::bigint
                    FROM yb_national_catalog_item
                    WHERE contract_id = ?
                      AND tenant_id = ?
                      AND is_deleted = false
                      AND source_infno IN (?, ?)
                    """,
                    Long.class,
                    contractId,
                    tenantId,
                    ElepCatalogConstants.SOURCE_INFNO_1301,
                    ElepCatalogConstants.SOURCE_INFNO_1307);
            return epoch == null ? 0L : epoch;
        } catch (DataAccessException ex) {
            return 0L;
        }
    }

    @Override
    public long countRows(Long contractId, Long tenantId) {
        try {
            Long count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM yb_national_catalog_item
                    WHERE contract_id = ?
                      AND tenant_id = ?
                      AND is_deleted = false
                    """,
                    Long.class,
                    contractId,
                    tenantId);
            return count == null ? 0L : count;
        } catch (DataAccessException ex) {
            return 0L;
        }
    }

    @Override
    public List<ElepNationalCatalogRow> pageDistinctDrug(
            Long contractId,
            Long tenantId,
            String keyword,
            String hilistCodeExact,
            int limit,
            int offset) {
        FilterBuilder filter = drugDistinctFilter(contractId, tenantId, keyword, hilistCodeExact);
        String sql = """
            SELECT * FROM (
              SELECT DISTINCT ON (hilist_code)
                hilist_code, hilist_name, spec, dosform, aprvno, pinyin, vali_flag, source_infno, list_type
              FROM yb_national_catalog_item
              WHERE """
                + " "
                + filter.whereSql()
                + """
              ORDER BY hilist_code, updt_time DESC NULLS LAST, id DESC
            ) deduped
            ORDER BY hilist_code
            LIMIT ? OFFSET ?
            """;
        List<Object> args = filter.argsCopy();
        args.add(limit);
        args.add(offset);
        return queryList(sql, args);
    }

    @Override
    public long countDistinctDrug(Long contractId, Long tenantId, String keyword, String hilistCodeExact) {
        FilterBuilder filter = drugDistinctFilter(contractId, tenantId, keyword, hilistCodeExact);
        String sql = """
            SELECT COUNT(*) FROM (
              SELECT DISTINCT hilist_code
              FROM yb_national_catalog_item
              WHERE """
                + " "
                + filter.whereSql()
                + """
            ) c
            """;
        return queryCount(sql, filter.args());
    }

    @Override
    public List<ElepNationalCatalogRow> page1301DetailDrug(
            Long contractId,
            Long tenantId,
            String keyword,
            String hilistCodeExact,
            String aprvno,
            String spec,
            int limit,
            int offset) {
        FilterBuilder filter = drug1301Filter(contractId, tenantId, keyword, hilistCodeExact, aprvno, spec);
        String sql = """
            SELECT hilist_code, hilist_name, spec, dosform, aprvno, pinyin, vali_flag, source_infno, list_type
            FROM yb_national_catalog_item
            WHERE """
                + " "
                + filter.whereSql()
                + """
            ORDER BY hilist_code, updt_time DESC NULLS LAST, id DESC
            LIMIT ? OFFSET ?
            """;
        List<Object> args = filter.argsCopy();
        args.add(limit);
        args.add(offset);
        return queryList(sql, args);
    }

    @Override
    public long count1301DetailDrug(
            Long contractId,
            Long tenantId,
            String keyword,
            String hilistCodeExact,
            String aprvno,
            String spec) {
        FilterBuilder filter = drug1301Filter(contractId, tenantId, keyword, hilistCodeExact, aprvno, spec);
        String sql = """
            SELECT COUNT(*)
            FROM yb_national_catalog_item
            WHERE """
                + " "
                + filter.whereSql();
        return queryCount(sql, filter.args());
    }

    @Override
    public List<ElepNationalCatalogRow> pageDiagnosis(
            Long contractId,
            Long tenantId,
            String keyword,
            String hilistCodeExact,
            int limit,
            int offset) {
        FilterBuilder filter = diagnosisFilter(contractId, tenantId, keyword, hilistCodeExact);
        String sql = """
            SELECT hilist_code, hilist_name, spec, dosform, aprvno, pinyin, vali_flag, source_infno, list_type
            FROM yb_national_catalog_item
            WHERE """
                + " "
                + filter.whereSql()
                + """
            ORDER BY hilist_code
            LIMIT ? OFFSET ?
            """;
        List<Object> args = filter.argsCopy();
        args.add(limit);
        args.add(offset);
        return queryList(sql, args);
    }

    @Override
    public long countDiagnosis(Long contractId, Long tenantId, String keyword, String hilistCodeExact) {
        FilterBuilder filter = diagnosisFilter(contractId, tenantId, keyword, hilistCodeExact);
        String sql = """
            SELECT COUNT(*)
            FROM yb_national_catalog_item
            WHERE """
                + " "
                + filter.whereSql();
        return queryCount(sql, filter.args());
    }

    @Override
    public Optional<ElepNationalCatalogRow> findDrugByCode(
            Long contractId,
            Long tenantId,
            String hilistCode,
            String aprvno,
            String spec) {
        if (ElepCatalogConstants.prefers1301Detail(aprvno, spec)) {
            List<ElepNationalCatalogRow> rows = page1301DetailDrug(
                    contractId, tenantId, null, hilistCode, aprvno, spec, 1, 0);
            return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
        }
        List<ElepNationalCatalogRow> rows = pageDistinctDrug(
                contractId, tenantId, null, hilistCode, 1, 0);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public Optional<ElepNationalCatalogRow> findDiagnosisByCode(
            Long contractId,
            Long tenantId,
            String hilistCode) {
        List<ElepNationalCatalogRow> rows = pageDiagnosis(contractId, tenantId, null, hilistCode, 1, 0);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    static boolean prefers1301Detail(String aprvno, String spec) {
        return ElepCatalogConstants.prefers1301Detail(aprvno, spec);
    }

    private static FilterBuilder drugDistinctFilter(
            Long contractId,
            Long tenantId,
            String keyword,
            String hilistCodeExact) {
        FilterBuilder filter = baseFilter(contractId, tenantId);
        filter.andRaw("list_type = ?");
        filter.args().add(ElepCatalogConstants.LIST_TYPE_WESTERN_DRUG);
        filter.andRaw("(source_infno = ? OR source_line_no IS NULL)");
        filter.args().add(ElepCatalogConstants.SOURCE_INFNO_1312);
        filter.andRaw("vali_flag = ?");
        filter.args().add(ElepCatalogConstants.DEFAULT_VALI_FLAG);
        filter.andSearch(keyword, hilistCodeExact);
        return filter;
    }

    private static FilterBuilder drug1301Filter(
            Long contractId,
            Long tenantId,
            String keyword,
            String hilistCodeExact,
            String aprvno,
            String spec) {
        FilterBuilder filter = baseFilter(contractId, tenantId);
        filter.andRaw("list_type = ?");
        filter.args().add(ElepCatalogConstants.LIST_TYPE_WESTERN_DRUG);
        filter.andRaw("source_infno = ?");
        filter.args().add(ElepCatalogConstants.SOURCE_INFNO_1301);
        filter.andRaw("source_line_no IS NOT NULL");
        filter.andRaw("vali_flag = ?");
        filter.args().add(ElepCatalogConstants.DEFAULT_VALI_FLAG);
        filter.andSearch(keyword, hilistCodeExact);
        filter.andOptionalLikeLower("aprvno", aprvno);
        filter.andOptionalLikeLower("spec", spec);
        return filter;
    }

    private static FilterBuilder diagnosisFilter(
            Long contractId,
            Long tenantId,
            String keyword,
            String hilistCodeExact) {
        FilterBuilder filter = baseFilter(contractId, tenantId);
        filter.andRaw("source_infno = ?");
        filter.args().add(ElepCatalogConstants.SOURCE_INFNO_1307);
        filter.andRaw("vali_flag = ?");
        filter.args().add(ElepCatalogConstants.DEFAULT_VALI_FLAG);
        filter.andSearch(keyword, hilistCodeExact);
        return filter;
    }

    private static FilterBuilder baseFilter(Long contractId, Long tenantId) {
        FilterBuilder filter = new FilterBuilder();
        filter.andRaw("contract_id = ?");
        filter.args().add(contractId);
        filter.andRaw("tenant_id = ?");
        filter.args().add(tenantId);
        filter.andRaw("is_deleted = false");
        return filter;
    }

    private List<ElepNationalCatalogRow> queryList(String sql, List<Object> args) {
        try {
            return jdbcTemplate.query(sql, ElepNationalCatalogRowMapper.INSTANCE, args.toArray());
        } catch (DataAccessException ex) {
            return List.of();
        }
    }

    private long queryCount(String sql, List<Object> args) {
        try {
            Long count = jdbcTemplate.queryForObject(sql, Long.class, args.toArray());
            return count == null ? 0L : count;
        } catch (DataAccessException ex) {
            return 0L;
        }
    }

    private static final class FilterBuilder {

        private final List<String> clauses = new ArrayList<>();
        private final List<Object> params = new ArrayList<>();

        List<Object> args() {
            return params;
        }

        List<Object> argsCopy() {
            return new ArrayList<>(params);
        }

        String whereSql() {
            return String.join(" AND ", clauses);
        }

        void andRaw(String clause) {
            clauses.add(clause);
        }

        void andOptionalLikeLower(String column, String value) {
            if (value == null || value.isBlank()) {
                return;
            }
            clauses.add("LOWER(" + column + ") LIKE LOWER(CONCAT('%', ?, '%'))");
            params.add(value.trim());
        }

        void andOptionalEq(String column, String value) {
            if (value == null || value.isBlank()) {
                return;
            }
            clauses.add(column + " = ?");
            params.add(value.trim());
        }

        void andSearch(String keyword, String hilistCodeExact) {
            if (hilistCodeExact != null && !hilistCodeExact.isBlank()) {
                andOptionalEq("hilist_code", hilistCodeExact);
                return;
            }
            if (keyword == null || keyword.isBlank()) {
                return;
            }
            String kw = keyword.trim();
            if (kw.length() == 1) {
                clauses.add("hilist_code = ?");
                params.add(kw);
                return;
            }
            clauses.add("""
                (
                  LOWER(hilist_name) LIKE LOWER(CONCAT('%', ?, '%'))
                  OR LOWER(pinyin) LIKE LOWER(CONCAT('%', ?, '%'))
                  OR LOWER(hilist_code) LIKE LOWER(CONCAT('%', ?, '%'))
                )
                """);
            params.add(kw);
            params.add(kw);
            params.add(kw);
        }
    }
}
