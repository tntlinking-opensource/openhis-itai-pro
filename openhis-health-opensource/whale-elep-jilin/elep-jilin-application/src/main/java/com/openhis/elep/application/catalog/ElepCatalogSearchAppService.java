/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.elep.application.catalog;

import com.openhis.elep.application.catalog.cache.ElepCatalogCacheManager;
import com.openhis.elep.application.catalog.support.ElepCatalogMapper;
import com.openhis.elep.domain.catalog.ElepCatalogConstants;
import com.openhis.elep.domain.catalog.dto.ElepCatalogCodeRequest;
import com.openhis.elep.domain.catalog.dto.ElepCatalogInvalidLineDto;
import com.openhis.elep.domain.catalog.dto.ElepCatalogItemDto;
import com.openhis.elep.domain.catalog.dto.ElepCatalogPageResult;
import com.openhis.elep.domain.catalog.dto.ElepCatalogSearchRequest;
import com.openhis.elep.domain.catalog.dto.ElepCatalogValidateCommand;
import com.openhis.elep.domain.catalog.facade.ElepCatalogSearchOperations;
import com.openhis.elep.domain.catalog.model.ElepNationalCatalogRow;
import com.openhis.elep.domain.catalog.repository.ElepNationalCatalogReadRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 医保目录搜索 + Caffeine 缓存（只读 CHS 镜像表）。 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.elep.deployment.mode", havingValue = "embedded", matchIfMissing = true)
public class ElepCatalogSearchAppService implements ElepCatalogSearchOperations {

    private static final Logger log = LoggerFactory.getLogger(ElepCatalogSearchAppService.class);

    private final ElepNationalCatalogReadRepository catalogReadRepository;
    private final ElepCatalogCacheManager cacheManager;

    @Override
    public ElepCatalogPageResult searchDrug(ElepCatalogSearchRequest request) {
        QueryContext ctx = resolveQueryContext(request);
        warnIfCatalogEmpty(ctx);
        String searchKey = buildSearchKey("DRUG", ctx.keyword(), ctx.aprvno(), ctx.spec(), ctx.page(), ctx.pageSize());
        Optional<ElepCatalogPageResult> cached = cacheManager.getSearch(ctx.cache(), "DRUG", searchKey);
        if (cached.isPresent()) {
            return cached.get();
        }
        List<ElepNationalCatalogRow> rows;
        long total;
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
        ElepCatalogPageResult result = toPageResult(rows, total, ctx.page(), ctx.pageSize());
        cacheManager.putSearch(ctx.cache(), "DRUG", searchKey, result);
        return result;
    }

    @Override
    public ElepCatalogPageResult searchDiagnosis(ElepCatalogSearchRequest request) {
        QueryContext ctx = resolveQueryContext(request);
        warnIfCatalogEmpty(ctx);
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
        cacheManager.putSearch(ctx.cache(), "DIAG", searchKey, result);
        return result;
    }

    @Override
    public Optional<ElepCatalogItemDto> getDrugByCode(ElepCatalogCodeRequest request) {
        QueryContext ctx = resolveCodeContext(request.getContractId(), request.getContractBusNo());
        if (StringUtils.isBlank(request.getHilistCode())) {
            return Optional.empty();
        }
        String codeKey = buildCodeKey("DRUG", request.getHilistCode(), request.getAprvno(), request.getSpec());
        Optional<ElepCatalogItemDto> cached = cacheManager.getCode(ctx.cache(), "DRUG", codeKey);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<ElepNationalCatalogRow> row = catalogReadRepository.findDrugByCode(
                ctx.contractId(), ctx.tenantId(),
                request.getHilistCode().trim(),
                StringUtils.trimToNull(request.getAprvno()),
                StringUtils.trimToNull(request.getSpec()));
        Optional<ElepCatalogItemDto> dto = row.map(ElepCatalogMapper::toDto);
        dto.ifPresent(item -> cacheManager.putCode(ctx.cache(), "DRUG", codeKey, item));
        return dto;
    }

    @Override
    public Optional<ElepCatalogItemDto> getDiagnosisByCode(ElepCatalogCodeRequest request) {
        QueryContext ctx = resolveCodeContext(request.getContractId(), request.getContractBusNo());
        if (StringUtils.isBlank(request.getHilistCode())) {
            return Optional.empty();
        }
        String codeKey = buildCodeKey("DIAG", request.getHilistCode(), null, null);
        Optional<ElepCatalogItemDto> cached = cacheManager.getCode(ctx.cache(), "DIAG", codeKey);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<ElepNationalCatalogRow> row = catalogReadRepository.findDiagnosisByCode(
                ctx.contractId(), ctx.tenantId(), request.getHilistCode().trim());
        Optional<ElepCatalogItemDto> dto = row.map(ElepCatalogMapper::toDto);
        dto.ifPresent(item -> cacheManager.putCode(ctx.cache(), "DIAG", codeKey, item));
        return dto;
    }

    @Override
    public List<ElepCatalogInvalidLineDto> validateLines(ElepCatalogValidateCommand command) {
        if (command.getContractId() == null) {
            throw new BusinessException("ELEP_CONTRACT_REQUIRED", "contractId 不能为空");
        }
        QueryContext ctx = resolveCodeContext(command.getContractId(), command.getContractBusNo());
        List<ElepCatalogInvalidLineDto> invalid = new ArrayList<>();
        boolean drug = !ElepCatalogValidateCommand.KIND_DIAGNOSIS.equalsIgnoreCase(command.getCatalogKind());
        for (ElepCatalogValidateCommand.Line line : command.getLines()) {
            if (line == null || StringUtils.isBlank(line.getHilistCode())) {
                continue;
            }
            Optional<ElepCatalogItemDto> found;
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
                invalid.add(new ElepCatalogInvalidLineDto()
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

    private QueryContext resolveCodeContext(Long contractId, String contractBusNo) {
        if (contractId == null) {
            throw new BusinessException("ELEP_CONTRACT_REQUIRED", "contractId 不能为空");
        }
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

    private void warnIfCatalogEmpty(QueryContext ctx) {
        if (catalogReadRepository.countRows(ctx.contractId(), ctx.tenantId()) > 0) {
            return;
        }
        log.warn("yb_national_catalog_item 为空或未挂载 CHS 目录，contractId={} tenantId={}；请先完成 CHS 1301/1307 下载",
                ctx.contractId(), ctx.tenantId());
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
