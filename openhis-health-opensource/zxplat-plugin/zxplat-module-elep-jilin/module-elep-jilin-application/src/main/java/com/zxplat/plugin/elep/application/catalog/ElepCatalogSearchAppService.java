/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.application.catalog;

import com.zxplat.plugin.elep.application.catalog.cache.ElepCatalogCacheManager;
import com.zxplat.plugin.elep.application.catalog.support.ElepCatalogMapper;
import com.zxplat.plugin.elep.domain.catalog.ElepCatalogConstants;
import com.zxplat.plugin.elep.domain.catalog.command.ElepCatalogCodeRequest;
import com.zxplat.plugin.elep.domain.catalog.model.ElepCatalogInvalidLine;
import com.zxplat.plugin.elep.domain.catalog.model.ElepCatalogItem;
import com.zxplat.plugin.elep.domain.catalog.model.ElepCatalogPageResult;
import com.zxplat.plugin.elep.domain.catalog.command.ElepCatalogSearchRequest;
import com.zxplat.plugin.elep.domain.catalog.command.ElepCatalogValidateCommand;
import com.zxplat.plugin.elep.domain.catalog.access.ElepCatalogSearchOperations;
import com.zxplat.plugin.elep.domain.catalog.model.ElepNationalCatalogRow;
import com.zxplat.plugin.elep.domain.catalog.repository.ElepNationalCatalogReadRepository;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 医保目录搜索 + Caffeine 缓存（只读 CHS 镜像表）。 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zxplat.elep.deployment.mode", havingValue = "embedded", matchIfMissing = true)
public class ElepCatalogSearchAppService implements ElepCatalogSearchOperations {

    private final ElepNationalCatalogReadRepository catalogReadRepository;
    private final ElepCatalogCacheManager cacheManager;

    /**
     * 药品选品。必须带关键字，走药名 GIN / 拼音前缀；厂家仅在药名未命中时另查。
     *
     * @param request 检索条件，keyword 为空返回空页
     * @return 分页目录
     */
    @Override
    public ElepCatalogPageResult searchDrug(ElepCatalogSearchRequest request) {
        QueryContext ctx = resolveQueryContext(request);
        // Gate：无关键字禁止扫全表。药品 OR 厂家曾把 GIN 打掉，下拉空搜会超时变空。
        if (ctx.keyword() == null || ctx.keyword().isBlank()) {
            return toPageResult(List.of(), 0, ctx.page(), ctx.pageSize());
        }
        String searchKey = buildSearchKey("DRUG", ctx.keyword(), ctx.aprvno(), ctx.spec(), ctx.page(), ctx.pageSize());
        // Pre：命中选品缓存则不再查库
        Optional<ElepCatalogPageResult> cached = cacheManager.getSearch(ctx.cache(), "DRUG", searchKey);
        if (cached.isPresent()) {
            return cached.get();
        }
        List<ElepNationalCatalogRow> rows;
        long total;
        // Cmd：有文号/规格走 1301 明细，否则 1312 摘要（药名未中再按厂家）
        if (ElepCatalogConstants.prefers1301Detail(ctx.aprvno(), ctx.spec())) {
            rows = catalogReadRepository.page1301DetailDrug(
                    ctx.contractId(), ctx.tenantId(), ctx.keyword(), null,
                    ctx.aprvno(), ctx.spec(), ctx.pageSize(), ctx.offset());
            total = catalogReadRepository.count1301DetailDrug(
                    ctx.contractId(), ctx.tenantId(), ctx.keyword(), null,
                    ctx.aprvno(), ctx.spec());
        } else {
            rows = catalogReadRepository.pageDistinctDrug(
                    ctx.contractId(), ctx.tenantId(), ctx.keyword(), null,
                    ctx.pageSize(), ctx.offset());
            total = catalogReadRepository.countDistinctDrug(
                    ctx.contractId(), ctx.tenantId(), ctx.keyword(), null);
        }
        // Asm：有命中才写缓存；SQL 失败时仓储返回空页，缓存空结果会把后续选品一直钉死
        ElepCatalogPageResult result = toPageResult(rows, total, ctx.page(), ctx.pageSize());
        if (!rows.isEmpty()) {
            cacheManager.putSearch(ctx.cache(), "DRUG", searchKey, result);
        }
        return result;
    }

    @Override
    public ElepCatalogPageResult searchDiagnosis(ElepCatalogSearchRequest request) {
        QueryContext ctx = resolveQueryContext(request);
        String searchKey = buildSearchKey("DIAG", ctx.keyword(), null, null, ctx.page(), ctx.pageSize());
        Optional<ElepCatalogPageResult> cached = cacheManager.getSearch(ctx.cache(), "DIAG", searchKey);
        if (cached.isPresent()) {
            return cached.get();
        }
        List<ElepNationalCatalogRow> rows = catalogReadRepository.pageDiagnosis(
                ctx.contractId(), ctx.tenantId(), ctx.keyword(), null,
                ctx.pageSize(), ctx.offset());
        long total = catalogReadRepository.countDiagnosis(
                ctx.contractId(), ctx.tenantId(), ctx.keyword(), null);
        ElepCatalogPageResult result = toPageResult(rows, total, ctx.page(), ctx.pageSize());
        // Asm：有命中才写缓存；SQL 失败空页不可缓存
        if (!rows.isEmpty()) {
            cacheManager.putSearch(ctx.cache(), "DIAG", searchKey, result);
        }
        return result;
    }

    @Override
    public Optional<ElepCatalogItem> getDrugByCode(ElepCatalogCodeRequest request) {
        QueryContext ctx = resolveCodeContext(request.getContractId(), request.getContractBusNo());
        if (StringUtils.isBlank(request.getHilistCode())) {
            return Optional.empty();
        }
        String codeKey = buildCodeKey("DRUG", request.getHilistCode(), request.getAprvno(), request.getSpec());
        Optional<ElepCatalogItem> cached = cacheManager.getCode(ctx.cache(), "DRUG", codeKey);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<ElepNationalCatalogRow> row = catalogReadRepository.findDrugByCode(
                ctx.contractId(), ctx.tenantId(),
                request.getHilistCode().trim(),
                StringUtils.trimToNull(request.getAprvno()),
                StringUtils.trimToNull(request.getSpec()));
        Optional<ElepCatalogItem> dto = row.map(ElepCatalogMapper::toDto);
        dto.ifPresent(item -> cacheManager.putCode(ctx.cache(), "DRUG", codeKey, item));
        return dto;
    }

    @Override
    public Optional<ElepCatalogItem> getDiagnosisByCode(ElepCatalogCodeRequest request) {
        QueryContext ctx = resolveCodeContext(request.getContractId(), request.getContractBusNo());
        if (StringUtils.isBlank(request.getHilistCode())) {
            return Optional.empty();
        }
        String codeKey = buildCodeKey("DIAG", request.getHilistCode(), null, null);
        Optional<ElepCatalogItem> cached = cacheManager.getCode(ctx.cache(), "DIAG", codeKey);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<ElepNationalCatalogRow> row = catalogReadRepository.findDiagnosisByCode(
                ctx.contractId(), ctx.tenantId(), request.getHilistCode().trim());
        Optional<ElepCatalogItem> dto = row.map(ElepCatalogMapper::toDto);
        dto.ifPresent(item -> cacheManager.putCode(ctx.cache(), "DIAG", codeKey, item));
        return dto;
    }

    @Override
    public List<ElepCatalogInvalidLine> validateLines(ElepCatalogValidateCommand command) {
        if (command.getContractId() == null) {
            throw new BusinessException("ELEP_CONTRACT_REQUIRED", "contractId 不能为空");
        }
        QueryContext ctx = resolveCodeContext(command.getContractId(), command.getContractBusNo());
        List<ElepCatalogInvalidLine> invalid = new ArrayList<>();
        boolean drug = !ElepCatalogValidateCommand.KIND_DIAGNOSIS.equalsIgnoreCase(command.getCatalogKind());
        for (ElepCatalogValidateCommand.Line line : command.getLines()) {
            if (line == null || StringUtils.isBlank(line.getHilistCode())) {
                continue;
            }
            Optional<ElepCatalogItem> found;
            if (drug) {
                found = getDrugByCode(new ElepCatalogCodeRequest()
                        .setContractId(command.getContractId())
                        .setContractBusNo(command.getContractBusNo())
                        .setHilistCode(line.getHilistCode())
                        .setAprvno(line.getAprvno())
                        .setSpec(line.getSpec()));
            } else {
                found = getDiagnosisByCode(new ElepCatalogCodeRequest()
                        .setContractId(command.getContractId())
                        .setContractBusNo(command.getContractBusNo())
                        .setHilistCode(line.getHilistCode()));
            }
            if (found.isEmpty()) {
                invalid.add(new ElepCatalogInvalidLine()
                        .setLineNo(line.getLineNo())
                        .setHilistCode(line.getHilistCode())
                        .setReason("目录编码无效或已失效"));
            }
        }
        return invalid;
    }

    private QueryContext resolveQueryContext(ElepCatalogSearchRequest request) {
        QueryContext ctx = resolveCodeContext(request.getContractId(), request.getContractBusNo());
        int page = Math.max(request.getPage(), 0);
        int pageSize = request.getPageSize() <= 0
                ? ElepCatalogConstants.DEFAULT_PAGE_SIZE
                : Math.min(request.getPageSize(), ElepCatalogConstants.MAX_PAGE_SIZE);
        String keyword = normalizeKeyword(request.getKeyword());
        return new QueryContext(
                ctx.contractId(),
                ctx.tenantId(),
                ctx.cache(),
                keyword,
                StringUtils.trimToNull(request.getAprvno()),
                StringUtils.trimToNull(request.getSpec()),
                page,
                pageSize);
    }

    /**
     * 解析目录检索租户与缓存上下文。
     * 开发阶段允许无契约主键：按租户检索目录，便于开方页下拉出数。
     *
     * @param contractId    费用性质主键，可空
     * @param contractBusNo 契约 bus_no，可空
     * @return 检索上下文
     */
    private QueryContext resolveCodeContext(Long contractId, String contractBusNo) {
        Long tenantId = TenantUtils.getCurrentTenantId();
        ElepCatalogCacheManager.CacheContext cache = cacheManager.resolveContext(contractId, tenantId, contractBusNo);
        return new QueryContext(contractId, tenantId, cache, null, null, null, 0, ElepCatalogConstants.DEFAULT_PAGE_SIZE);
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static ElepCatalogPageResult toPageResult(
            List<ElepNationalCatalogRow> rows,
            long total,
            int page,
            int pageSize) {
        ElepCatalogPageResult result = new ElepCatalogPageResult()
                .setTotal(total)
                .setPage(page)
                .setPageSize(pageSize);
        for (ElepNationalCatalogRow row : rows) {
            result.getItems().add(ElepCatalogMapper.toDto(row));
        }
        return result;
    }

    private static String buildSearchKey(
            String kind,
            String keyword,
            String aprvno,
            String spec,
            int page,
            int pageSize) {
        return kind + ":"
                + StringUtils.defaultString(keyword) + ":"
                + StringUtils.defaultString(aprvno) + ":"
                + StringUtils.defaultString(spec) + ":p"
                + page + ":s" + pageSize;
    }

    private static String buildCodeKey(String kind, String code, String aprvno, String spec) {
        return kind + ":" + code + ":" + StringUtils.defaultString(aprvno) + ":" + StringUtils.defaultString(spec);
    }

    private record QueryContext(
            Long contractId,
            Long tenantId,
            ElepCatalogCacheManager.CacheContext cache,
            String keyword,
            String aprvno,
            String spec,
            int page,
            int pageSize) {

        int offset() {
            return page * pageSize;
        }
    }
}
