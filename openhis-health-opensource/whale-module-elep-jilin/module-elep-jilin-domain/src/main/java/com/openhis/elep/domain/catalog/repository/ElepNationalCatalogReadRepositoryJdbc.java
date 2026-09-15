/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.catalog.repository;

import com.openhis.elep.domain.catalog.ElepCatalogConstants;
import com.openhis.elep.domain.catalog.model.ElepNationalCatalogRow;
import com.openhis.elep.domain.catalog.repository.support.ElepNationalCatalogRowMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** {@link ElepNationalCatalogReadRepository} JdbcTemplate 实现。 */
@Repository
@RequiredArgsConstructor
public class ElepNationalCatalogReadRepositoryJdbc implements ElepNationalCatalogReadRepository {

    private static final Logger log = LoggerFactory.getLogger(ElepNationalCatalogReadRepositoryJdbc.class);

    /** 选品展示列；含厂家，便于同一通用名下区分真不同品。 */
    private static final String DRUG_SELECT_COLUMNS =
            "hilist_code, hilist_name, spec, dosform, aprvno, pinyin, vali_flag, source_infno, list_type, prodentp_name";

    /**
     * 同一药品商品身份：名称 + 规格 + 批准文号 + 厂家。
     * <p>1301 增量会给同一商品写入多条历史 {@code hilist_code}，选品只取最新生效行。</p>
     */
    private static final String DRUG_PRODUCT_DISTINCT_ON = """
            COALESCE(hilist_name, ''),
            COALESCE(spec, ''),
            COALESCE(aprvno, ''),
            COALESCE(prodentp_name, '')
            """;

    private static final String DRUG_PRODUCT_ORDER_BY = """
            COALESCE(hilist_name, ''),
            COALESCE(spec, ''),
            COALESCE(aprvno, ''),
            COALESCE(prodentp_name, ''),
            begndate DESC NULLS LAST,
            hilist_code DESC,
            id DESC
            """;

    private final JdbcTemplate jdbcTemplate;

    /**
     * 目录变更时钟，供搜索缓存失效。按租户取 1301/1307/1312 最新时间，不按挂号契约。
     *
     * @param contractId 挂号契约，选品缓存忽略
     * @param tenantId   租户
     * @return epoch 毫秒，失败为 0
     */
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
                    WHERE tenant_id = ?
                      AND is_deleted = false
                      AND source_infno IN (?, ?, ?)
                    """,
                    Long.class,
                    tenantId,
                    ElepCatalogConstants.SOURCE_INFNO_1301,
                    ElepCatalogConstants.SOURCE_INFNO_1307,
                    ElepCatalogConstants.SOURCE_INFNO_1312);
            if (epoch == null) {
                return 0L;
            }
            return epoch;
        } catch (DataAccessException ex) {
            log.warn("读取目录 epoch 失败，返回 0: {}", ex.getMessage());
            return 0L;
        }
    }

    /**
     * 租户下未删除目录行数，用于空表告警。
     *
     * @param contractId 挂号契约，计数忽略
     * @param tenantId   租户
     * @return 行数
     */
    @Override
    public long countRows(Long contractId, Long tenantId) {
        try {
            Long count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM yb_national_catalog_item
                    WHERE tenant_id = ?
                      AND is_deleted = false
                    """,
                    Long.class,
                    tenantId);
            if (count == null) {
                return 0L;
            }
            return count;
        } catch (DataAccessException ex) {
            log.warn("统计目录行数失败，返回 0: {}", ex.getMessage());
            return 0L;
        }
    }

    /**
     * 1312 摘要分页：同一商品只出最新生效行。
     *
     * @param contractId       挂号契约，选品忽略
     * @param tenantId         租户
     * @param keyword          药名/厂家/首字母
     * @param hilistCodeExact  精确编码
     * @param limit            页大小
     * @param offset           偏移
     * @return 当前页
     */
    @Override
    public List<ElepNationalCatalogRow> pageDistinctDrug(
            Long contractId,
            Long tenantId,
            String keyword,
            String hilistCodeExact,
            int limit,
            int offset) {
        // 无关键字不扫 28 万摘要，避免下拉一开就超时
        if (isBlank(keyword) && isBlank(hilistCodeExact)) {
            return List.of();
        }
        FilterBuilder filter = drugDistinctFilter(contractId, tenantId, keyword, hilistCodeExact);
        List<ElepNationalCatalogRow> rows = queryDrugLatestPage(filter, isBlank(hilistCodeExact), limit, offset);
        if (!rows.isEmpty()) {
            return rows;
        }
        // 药名未命中且是中文时，再按厂家查（单独条件才能走 GIN）
        if (!shouldRetryManufacturer(keyword, hilistCodeExact)) {
            return rows;
        }
        FilterBuilder manufacturerFilter = drugManufacturerFilter(contractId, tenantId, keyword);
        return queryDrugLatestPage(manufacturerFilter, true, limit, offset);
    }

    /**
     * 与 {@link #pageDistinctDrug} 同口径计数。
     *
     * @param contractId       挂号契约，选品忽略
     * @param tenantId         租户
     * @param keyword          药名/厂家/首字母
     * @param hilistCodeExact  精确编码
     * @return 去重后行数
     */
    @Override
    public long countDistinctDrug(Long contractId, Long tenantId, String keyword, String hilistCodeExact) {
        if (isBlank(keyword) && isBlank(hilistCodeExact)) {
            return 0L;
        }
        FilterBuilder filter = drugDistinctFilter(contractId, tenantId, keyword, hilistCodeExact);
        long total = queryCount(buildDrugLatestCountSql(filter, isBlank(hilistCodeExact)), filter.args());
        // 药名计数为 0 时改按厂家计数，与 pageDistinctDrug 同口径
        if (total > 0L || !shouldRetryManufacturer(keyword, hilistCodeExact)) {
            return total;
        }
        FilterBuilder manufacturerFilter = drugManufacturerFilter(contractId, tenantId, keyword);
        return queryCount(buildDrugLatestCountSql(manufacturerFilter, true), manufacturerFilter.args());
    }

    /**
     * 1301 明细分页：有文号/规格时走明细行。
     *
     * @param contractId       挂号契约，选品忽略
     * @param tenantId         租户
     * @param keyword          药名/厂家/首字母
     * @param hilistCodeExact  精确编码
     * @param aprvno           批准文号
     * @param spec             规格
     * @param limit            页大小
     * @param offset           偏移
     * @return 当前页
     */
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
        boolean productLatest = isBlank(hilistCodeExact);
        String sql = buildDrugLatestPageSql(filter, productLatest);
        List<Object> args = filter.argsCopy();
        args.add(limit);
        args.add(offset);
        return queryList(sql, args);
    }

    /**
     * 与 {@link #page1301DetailDrug} 同口径计数。
     *
     * @param contractId       挂号契约，选品忽略
     * @param tenantId         租户
     * @param keyword          药名/厂家/首字母
     * @param hilistCodeExact  精确编码
     * @param aprvno           批准文号
     * @param spec             规格
     * @return 行数
     */
    @Override
    public long count1301DetailDrug(
            Long contractId,
            Long tenantId,
            String keyword,
            String hilistCodeExact,
            String aprvno,
            String spec) {
        FilterBuilder filter = drug1301Filter(contractId, tenantId, keyword, hilistCodeExact, aprvno, spec);
        boolean productLatest = isBlank(hilistCodeExact);
        return queryCount(buildDrugLatestCountSql(filter, productLatest), filter.args());
    }

    /**
     * 1307 诊断/病种分页。按租户共享，不按挂号契约过滤。
     *
     * @param contractId       挂号契约，选品忽略
     * @param tenantId         租户
     * @param keyword          诊断名/拼音
     * @param hilistCodeExact  精确编码
     * @param limit            页大小
     * @param offset           偏移
     * @return 当前页
     */
    @Override
    public List<ElepNationalCatalogRow> pageDiagnosis(
            Long contractId,
            Long tenantId,
            String keyword,
            String hilistCodeExact,
            int limit,
            int offset) {
        FilterBuilder filter = diagnosisFilter(contractId, tenantId, keyword, hilistCodeExact);
        // 诊断 SQL 必须用 joinSql：text block + whereSql 会把 ? 和 ORDER BY 粘成 ?ORDER BY
        String sql = joinSql(
                "SELECT " + DRUG_SELECT_COLUMNS,
                "FROM yb_national_catalog_item",
                "WHERE " + filter.whereSql(),
                "ORDER BY hilist_code",
                "LIMIT ? OFFSET ?");
        List<Object> args = filter.argsCopy();
        args.add(limit);
        args.add(offset);
        return queryList(sql, args);
    }

    /**
     * 与 {@link #pageDiagnosis} 同口径计数。
     *
     * @param contractId       挂号契约，选品忽略
     * @param tenantId         租户
     * @param keyword          诊断名/拼音
     * @param hilistCodeExact  精确编码
     * @return 行数
     */
    @Override
    public long countDiagnosis(Long contractId, Long tenantId, String keyword, String hilistCodeExact) {
        FilterBuilder filter = diagnosisFilter(contractId, tenantId, keyword, hilistCodeExact);
        String sql = joinSql(
                "SELECT COUNT(*)",
                "FROM yb_national_catalog_item",
                "WHERE " + filter.whereSql());
        return queryCount(sql, filter.args());
    }

    /**
     * 按编码取一条药品，供上传组装校验。
     *
     * @param contractId 挂号契约，选品忽略
     * @param tenantId   租户
     * @param hilistCode 医保目录编码
     * @param aprvno     批准文号
     * @param spec       规格
     * @return 命中行
     */
    @Override
    public Optional<ElepNationalCatalogRow> findDrugByCode(
            Long contractId,
            Long tenantId,
            String hilistCode,
            String aprvno,
            String spec) {
        // 有文号/规格时走 1301 明细，避免摘要码对不上包装规格
        if (ElepCatalogConstants.prefers1301Detail(aprvno, spec)) {
            List<ElepNationalCatalogRow> rows = page1301DetailDrug(
                    contractId, tenantId, null, hilistCode, aprvno, spec, 1, 0);
            // 明细未命中视为目录无此码
            if (rows.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(rows.get(0));
        }
        List<ElepNationalCatalogRow> rows = pageDistinctDrug(
                contractId, tenantId, null, hilistCode, 1, 0);
        // 摘要未命中视为目录无此码
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.get(0));
    }

    /**
     * 按编码取一条诊断，供上传组装校验。
     *
     * @param contractId 挂号契约，选品忽略
     * @param tenantId   租户
     * @param hilistCode 诊断编码
     * @return 命中行
     */
    @Override
    public Optional<ElepNationalCatalogRow> findDiagnosisByCode(
            Long contractId,
            Long tenantId,
            String hilistCode) {
        List<ElepNationalCatalogRow> rows = pageDiagnosis(contractId, tenantId, null, hilistCode, 1, 0);
        // 编码未命中视为目录无此诊断
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.get(0));
    }

    /**
     * 有批准文号或规格时走 1301 明细，与 CHS 选码口径一致。
     *
     * @param aprvno 批准文号
     * @param spec   规格
     * @return true 时查 1301
     */
    static boolean prefers1301Detail(String aprvno, String spec) {
        return ElepCatalogConstants.prefers1301Detail(aprvno, spec);
    }

    /**
     * 西药 1312 摘要选品条件：有效、未过期、关键字。
     *
     * @param contractId       挂号契约，选品忽略
     * @param tenantId         租户
     * @param keyword          药名/厂家/首字母
     * @param hilistCodeExact  精确编码
     * @return 过滤条件
     */
    private static FilterBuilder drugDistinctFilter(
            Long contractId,
            Long tenantId,
            String keyword,
            String hilistCodeExact) {
        FilterBuilder filter = baseFilter(contractId, tenantId);
        filter.andRaw("list_type = ?");
        filter.args().add(ElepCatalogConstants.LIST_TYPE_WESTERN_DRUG);
        filter.andRaw("source_infno = ?");
        filter.args().add(ElepCatalogConstants.SOURCE_INFNO_1312);
        filter.andRaw("vali_flag = ?");
        filter.args().add(ElepCatalogConstants.DEFAULT_VALI_FLAG);
        filter.andNotPlatformExpired();
        filter.andDrugKeywordSearch(keyword, hilistCodeExact);
        return filter;
    }

    /**
     * 西药 1301 明细选品条件：有规格/文号时走明细行。
     *
     * @param contractId       挂号契约，选品忽略
     * @param tenantId         租户
     * @param keyword          药名/厂家/首字母
     * @param hilistCodeExact  精确编码
     * @param aprvno           批准文号模糊
     * @param spec             规格模糊
     * @return 过滤条件
     */
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
        filter.andNotPlatformExpired();
        filter.andDrugKeywordSearch(keyword, hilistCodeExact);
        filter.andOptionalLikeLower("aprvno", aprvno);
        filter.andOptionalLikeLower("spec", spec);
        return filter;
    }

    /**
     * 厂家兜底：药名/拼音未命中时，再按厂家模糊查 1312。
     * 单独一条条件才能走 {@code prodentp_name} 的 GIN，禁止和药名 OR 在一起。
     *
     * @param contractId 挂号契约，选品忽略
     * @param tenantId   租户
     * @param keyword    厂家关键字
     * @return 过滤条件
     */
    private static FilterBuilder drugManufacturerFilter(Long contractId, Long tenantId, String keyword) {
        FilterBuilder filter = baseFilter(contractId, tenantId);
        filter.andRaw("list_type = ?");
        filter.args().add(ElepCatalogConstants.LIST_TYPE_WESTERN_DRUG);
        filter.andRaw("source_infno = ?");
        filter.args().add(ElepCatalogConstants.SOURCE_INFNO_1312);
        filter.andRaw("vali_flag = ?");
        filter.args().add(ElepCatalogConstants.DEFAULT_VALI_FLAG);
        filter.andNotPlatformExpired();
        filter.andOptionalLikeLower("prodentp_name", keyword);
        return filter;
    }

    /**
     * 药名未命中且关键字是中文时，再按厂家搜。精确编码和拼音检索不走厂家。
     *
     * @param keyword          检索词
     * @param hilistCodeExact  精确目录编码
     * @return true 时补一次厂家查询
     */
    private static boolean shouldRetryManufacturer(String keyword, String hilistCodeExact) {
        if (hilistCodeExact != null && !hilistCodeExact.isBlank()) {
            return false;
        }
        if (keyword == null || keyword.isBlank()) {
            return false;
        }
        return !FilterBuilder.isAsciiKeyword(keyword.trim());
    }

    /**
     * 按商品去重后分页取药。
     *
     * @param filter         过滤条件
     * @param productLatest  true 按商品身份去重
     * @param limit          页大小
     * @param offset         偏移
     * @return 当前页
     */
    private List<ElepNationalCatalogRow> queryDrugLatestPage(
            FilterBuilder filter,
            boolean productLatest,
            int limit,
            int offset) {
        String sql = buildDrugLatestPageSql(filter, productLatest);
        List<Object> args = filter.argsCopy();
        args.add(limit);
        args.add(offset);
        return queryList(sql, args);
    }

    /**
     * 诊断/病种 1307 选品条件。
     *
     * @param contractId       挂号契约，选品忽略
     * @param tenantId         租户
     * @param keyword          诊断名/拼音
     * @param hilistCodeExact  精确编码
     * @return 过滤条件
     */
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

    /**
     * 关键字选品：同一商品只保留 begndate / hilist_code 最新的一条。
     * 精确按编码查时不去商品身份，避免把旧码替换成同品新码。
     *
     * @param filter         过滤条件
     * @param productLatest  true 按商品身份去重
     * @return 分页 SQL
     */
    private static String buildDrugLatestPageSql(FilterBuilder filter, boolean productLatest) {
        String distinctOn;
        String innerOrderBy;
        if (productLatest) {
            distinctOn = DRUG_PRODUCT_DISTINCT_ON;
            innerOrderBy = DRUG_PRODUCT_ORDER_BY;
        } else {
            distinctOn = "hilist_code";
            innerOrderBy = "hilist_code, begndate DESC NULLS LAST, id DESC";
        }
        // 禁止 text block 与列名/WHERE 紧挨 +：会变成 prodentp_nameFROM、?ORDER BY，PG 直接 bad SQL grammar
        return joinSql(
                "SELECT * FROM (",
                "  SELECT DISTINCT ON (",
                distinctOn,
                "  )",
                "  " + DRUG_SELECT_COLUMNS,
                "  FROM yb_national_catalog_item",
                "  WHERE " + filter.whereSql(),
                "  ORDER BY " + innerOrderBy,
                ") latest",
                "ORDER BY hilist_code",
                "LIMIT ? OFFSET ?");
    }

    /**
     * 与 {@link #buildDrugLatestPageSql} 同口径的去重计数。
     *
     * @param filter         过滤条件
     * @param productLatest  true 按商品身份去重
     * @return COUNT SQL
     */
    private static String buildDrugLatestCountSql(FilterBuilder filter, boolean productLatest) {
        String distinctOn;
        String innerOrderBy;
        if (productLatest) {
            distinctOn = DRUG_PRODUCT_DISTINCT_ON;
            innerOrderBy = DRUG_PRODUCT_ORDER_BY;
        } else {
            distinctOn = "hilist_code";
            innerOrderBy = "hilist_code, begndate DESC NULLS LAST, id DESC";
        }
        return joinSql(
                "SELECT COUNT(*) FROM (",
                "  SELECT DISTINCT ON (",
                distinctOn,
                "  ) hilist_code",
                "  FROM yb_national_catalog_item",
                "  WHERE " + filter.whereSql(),
                "  ORDER BY " + innerOrderBy,
                ") c");
    }

    /**
     * 用换行拼接 SQL 片段。
     * <p>Java text block 紧挨 {@code +} 时会丢掉换行，生成 {@code prodentp_nameFROM}、
     * {@code ?ORDER BY}，选品接口捕获后返回空列表，页面表现为搜不到药。</p>
     *
     * @param parts SQL 片段，按出现顺序拼接
     * @return 可执行 SQL
     */
    private static String joinSql(String... parts) {
        return String.join("\n", parts);
    }

    /**
     * 空串视为未传。
     *
     * @param value 待判断字符串
     * @return null 或空白为 true
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 目录公共过滤：租户 + 未删除。
     * 国家目录按租户共享，不按挂号契约切片；本地往往只灌了省医保一份，
     * 市医保/工伤挂号若带 contract_id 会把诊断和药品全部滤空。
     *
     * @param contractId 挂号契约主键，选品忽略，保留入参与仓储接口对齐
     * @param tenantId   租户
     * @return 过滤条件
     */
    private static FilterBuilder baseFilter(Long contractId, Long tenantId) {
        FilterBuilder filter = new FilterBuilder();
        filter.andRaw("tenant_id = ?");
        filter.args().add(tenantId);
        filter.andRaw("is_deleted = false");
        return filter;
    }

    /**
     * 执行目录分页查询；表未挂载或 SQL 失败时记日志并返回空列表，避免选品接口 500。
     *
     * @param sql  已拼好的 SELECT
     * @param args 绑定参数
     * @return 行列表，失败时为空
     */
    private List<ElepNationalCatalogRow> queryList(String sql, List<Object> args) {
        try {
            return jdbcTemplate.query(sql, ElepNationalCatalogRowMapper.INSTANCE, args.toArray());
        } catch (DataAccessException ex) {
            log.warn("电子处方目录分页查询失败，返回空列表: {}", ex.getMessage(), ex);
            return List.of();
        }
    }

    /**
     * 执行目录计数；失败时记日志并返回 0。
     *
     * @param sql  已拼好的 COUNT
     * @param args 绑定参数
     * @return 行数
     */
    private long queryCount(String sql, List<Object> args) {
        try {
            Long count = jdbcTemplate.queryForObject(sql, Long.class, args.toArray());
            if (count == null) {
                return 0L;
            }
            return count;
        } catch (DataAccessException ex) {
            log.warn("电子处方目录计数失败，返回 0: {}", ex.getMessage(), ex);
            return 0L;
        }
    }

    /** 拼装 WHERE 子句与绑定参数。 */
    private static final class FilterBuilder {

        private final List<String> clauses = new ArrayList<>();
        private final List<Object> params = new ArrayList<>();

        /**
         * 当前绑定参数列表（可继续追加）。
         *
         * @return 可变参数列表
         */
        List<Object> args() {
            return params;
        }

        /**
         * 复制绑定参数，避免分页 LIMIT/OFFSET 污染原列表。
         *
         * @return 新列表
         */
        List<Object> argsCopy() {
            return new ArrayList<>(params);
        }

        /**
         * 用 AND 连接全部条件。
         *
         * @return WHERE 片段
         */
        String whereSql() {
            return String.join(" AND ", clauses);
        }

        /**
         * 追加一条已写好的 SQL 片段。
         *
         * @param clause 不含 AND 前缀的条件
         */
        void andRaw(String clause) {
            clauses.add(clause);
        }

        /**
         * 可选模糊匹配；空值不加条件。绑定完整 {@code %v%} 避免 PG 无法推断 CONCAT 参数类型。
         *
         * @param column 列名
         * @param value  模糊值
         */
        void andOptionalLikeLower(String column, String value) {
            if (value == null || value.isBlank()) {
                return;
            }
            clauses.add(column + " ILIKE ?");
            params.add("%" + value.trim() + "%");
        }

        /**
         * 可选等值；空值不加条件。
         *
         * @param column 列名
         * @param value  等值
         */
        void andOptionalEq(String column, String value) {
            if (value == null || value.isBlank()) {
                return;
            }
            clauses.add(column + " = ?");
            params.add(value.trim());
        }

        /**
         * 排除平台已过期：enddate 空或占位日视为无结束日，结束日当天仍可选。
         */
        void andNotPlatformExpired() {
            clauses.add("(enddate IS NULL OR enddate <= DATE '0001-12-31' OR enddate >= ?)");
            params.add(java.sql.Date.valueOf(LocalDate.now()));
        }

        /**
         * 药品选品关键字：中文只走药名 GIN；字母只走拼音前缀。
         * 药名和厂家不能写在同一个 OR 里，否则规划器放弃索引、全表扫 160 万行，选品超时变空。
         * 厂家由外层在药名未命中时另查。
         *
         * @param keyword          检索词，可空
         * @param hilistCodeExact  精确目录编码，有值时不再模糊
         */
        void andDrugKeywordSearch(String keyword, String hilistCodeExact) {
            if (hilistCodeExact != null && !hilistCodeExact.isBlank()) {
                andOptionalEq("hilist_code", hilistCodeExact);
                return;
            }
            if (keyword == null || keyword.isBlank()) {
                return;
            }
            String kw = keyword.trim();
            if (isAsciiKeyword(kw)) {
                clauses.add("pinyin ILIKE ?");
                params.add(kw + "%");
                return;
            }
            clauses.add("hilist_name ILIKE ?");
            params.add("%" + kw + "%");
        }

        /**
         * 诊断/病种选品：名称、编码模糊；纯英文按拼音首字母前缀。
         * 绑定完整 {@code %kw%} 文本，避免 CONCAT(?, '%') 让 PG 无法推断参数类型。
         * 单字也走模糊（「高」能命中高血压），不再当成精确编码。
         *
         * @param keyword          检索词，可空
         * @param hilistCodeExact  精确目录编码，有值时不再模糊
         */
        void andSearch(String keyword, String hilistCodeExact) {
            if (hilistCodeExact != null && !hilistCodeExact.isBlank()) {
                andOptionalEq("hilist_code", hilistCodeExact);
                return;
            }
            if (keyword == null || keyword.isBlank()) {
                return;
            }
            String kw = keyword.trim();
            if (isAsciiKeyword(kw)) {
                clauses.add("""
                    (
                      pinyin ILIKE ?
                      OR hilist_name ILIKE ?
                      OR hilist_code ILIKE ?
                    )
                    """);
                params.add(kw + "%");
                params.add("%" + kw + "%");
                params.add("%" + kw + "%");
                return;
            }
            String pattern = "%" + kw + "%";
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
         * 是否为纯 ASCII 检索词（拼音首字母 / 编码走前缀，中文走名称模糊）。
         *
         * @param keyword 已 trim 的关键字
         * @return 全部字符码点 ≤127 时为 true
         */
        static boolean isAsciiKeyword(String keyword) {
            for (int i = 0; i < keyword.length(); i++) {
                if (keyword.charAt(i) > 127) {
                    return false;
                }
            }
            return true;
        }
    }
}
