/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.chs.application.catalog;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.api.ChsInfNo;
import com.openhis.chs.api.ChsPlatformCodes;
import com.openhis.chs.api.InvokeContext;
import com.openhis.chs.api.InsuranceGatewayClient;
import com.openhis.chs.api.ResultBody;
import com.openhis.chs.api.dto.catalog.Catalog1312RowOutput;
import com.openhis.chs.api.support.ChsOutputParser;
import com.openhis.chs.application.catalog.mapper.NationalCatalogMapper;
import com.openhis.chs.application.catalog.support.ChsPagedResultBuilder;
import com.openhis.chs.application.gateway.session.ChsSignCredentialResolver;
import com.openhis.chs.application.gateway.support.ChsCurrentOperatorResolver;
import com.openhis.chs.domain.catalog.dto.request.Catalog1312FilterRequest;
import com.openhis.chs.domain.catalog.dto.request.QueryNationalCatalogRequest;
import com.openhis.chs.domain.catalog.dto.request.Refresh1312Request;
import com.openhis.chs.domain.catalog.dto.request.SuggestMedicationCatalogMappingRequest;
import com.openhis.chs.domain.catalog.dto.response.CatalogMatchCandidateDto;
import com.openhis.chs.domain.catalog.dto.response.MedicationCatalogMappingSuggestDto;
import com.openhis.chs.domain.catalog.dto.response.CatalogDownloadCursorDto;
import com.openhis.chs.domain.catalog.dto.response.CatalogDownloadPreviewDto;
import com.openhis.chs.domain.catalog.dto.response.CatalogDownloadVersionItemDto;
import com.openhis.chs.domain.catalog.dto.response.NationalCatalogItemDto;
import com.openhis.chs.domain.catalog.support.CatalogDownloadDisplaySupport;
import com.openhis.chs.domain.support.ChsTextSupport;
import com.openhis.chs.application.catalog.assembler.Catalog1312InputAssembler;
import com.openhis.chs.domain.catalog.port.ChsContractPropertiesPort;
import com.openhis.chs.domain.catalog.port.ChsInsuranceContractPort;
import com.openhis.chs.domain.catalog.port.ChsContractSnapshot;
import com.openhis.chs.api.dto.catalog.CatalogFileDownloadOutput;
import com.openhis.chs.domain.catalog.dto.request.StartCatalogDownloadRequest;
import com.openhis.chs.domain.catalog.dto.response.CatalogDownloadJobProgressDto;
import com.openhis.chs.domain.catalog.dto.response.CatalogDownloadJobStartDto;
import com.openhis.chs.application.catalog.dto.CatalogDownloadVersionManifest;
import com.openhis.chs.application.catalog.support.CatalogDownloadJobRegistry;
import com.openhis.chs.application.catalog.support.CatalogMedicationMatchScorer;
import com.openhis.chs.application.catalog.support.CatalogFileParserRegistry;
import com.openhis.chs.domain.catalog.support.CatalogDownloadStartVerSupport;
import com.openhis.chs.application.catalog.support.CatalogDownloadZipCache;
import com.openhis.chs.application.catalog.dto.CatalogDownloadPhase1Context;
import com.openhis.chs.application.catalog.dto.CatalogDownloadPhase1Result;
import com.openhis.chs.application.catalog.dto.CatalogDownloadVersionBundle;
import com.openhis.chs.domain.catalog.entity.YbCatalogDownloadCursor;
import com.openhis.chs.domain.catalog.service.YbCatalogDownloadCursorDomainService;
import com.openhis.chs.domain.catalog.entity.YbNationalCatalogItem;
import com.openhis.chs.domain.catalog.queries.NationalCatalogPageQuery;
import com.openhis.chs.domain.catalog.service.YbCatalogDownloadCursorQueryService;
import com.openhis.chs.domain.catalog.service.YbNationalCatalogDomainService;
import com.openhis.chs.domain.catalog.service.YbNationalCatalogQueryService;
import com.openhis.chs.domain.ChsErrorCodes;
import com.openhis.chs.domain.catalog.ChsCatalogConstants;
import com.openhis.chs.domain.catalog.facade.ChsNationalCatalogOperations;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 国家医保目录应用服务（C1：1312 查询入库 + 本地分页；C2：13xx+9102 文件型目录下载入库）。
 *
 * <h3>调试说明（建议开启 DEBUG 日志）</h3>
 * <pre>
 * logging.level.com.openhis.chs.application.catalog=DEBUG
 * logging.level.com.openhis.chs.application.catalog.support=DEBUG
 * </pre>
 *
 * <h3>文件型目录下载（{@link #startCatalogDownload} / {@link #previewCatalogDownload}）</h3>
 * <pre>
 * 阶段1 {@link CatalogDownloadPhase1Service}：停链依赖平台应答，无轮次硬上限
 * 阶段2/3 {@link CatalogDownloadImportService}：异步按版本解压 ZIP → 解析 → upsert + 游标
 * </pre>
 *
 * <h3>关键表</h3>
 * <ul>
 *   <li>{@code yb_catalog_download_cursor} — 每次成功入库追加一行；最新一条的 local_ver 为下次 13xx 起点</li>
 *   <li>{@code yb_national_catalog_item} — 文件型目录行键：source_file_qury_no + source_line_no；1312：hilist_code</li>
 * </ul>
 *
 * <h3>配置与契约</h3>
 * <ul>
 *   <li>{@link ChsInsuranceContractPort} — fin_contract 仅 id / bus_no</li>
 *   <li>{@link ChsContractPropertiesPort} — fixmedins_code、adm_vs 等仅 chs-contract.properties</li>
 *   <li>{@link CatalogDownloadZipCache} — …/versions/{catalogVer}.zip，每版本一个 ZIP</li>
 * </ul>
 *
 * <p>HTTP：embedded 为 {@code /api/app/insuranceNationalCatalog/*}（bridge）；chs 独立为 {@code /api/app/chsNationalCatalog/*}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class ChsNationalCatalogAppService implements ChsNationalCatalogOperations {

    /** 1312 平台网关客户端。 */
    private final InsuranceGatewayClient gatewayClient;

    /** 1312 入参组装。 */
    private final Catalog1312InputAssembler catalog1312InputAssembler;

    /** 目录下载阶段一（13xx + 9102/缓存）。 */
    private final CatalogDownloadPhase1Service catalogDownloadPhase1Service;

    /** 阶段二/三：异步入库与进度。 */
    private final CatalogDownloadImportService catalogDownloadImportService;

    private final CatalogDownloadJobRegistry catalogDownloadJobRegistry;

    /** 国家目录镜像查询。 */
    private final YbNationalCatalogQueryService catalogQueryService;

    /** 国家目录镜像写入（含事务）。 */
    private final YbNationalCatalogDomainService catalogDomainService;

    /** 目录下载游标查询。 */
    private final YbCatalogDownloadCursorQueryService downloadCursorQueryService;

    /** 目录下载游标写入。 */
    private final YbCatalogDownloadCursorDomainService downloadCursorDomainService;

    /** 医保契约解析。 */
    private final ChsInsuranceContractPort contractPort;

    /** 医保终端配置（仅 chs-contract.properties，键为 contract.bus_no）。 */
    private final ChsContractPropertiesPort contractPropertiesPort;

    /** 目录 DTO / Command 映射。 */
    private final NationalCatalogMapper nationalCatalogMapper;

    /** 13xx 目录 ZIP 本地缓存（按 catalogVer 每版本一个 zip，命中则跳过 9102）。 */
    private final CatalogDownloadZipCache catalogDownloadZipCache;

    /** 当前操作人解析（嵌入 HIS / 独立 chs-server）。 */
    private final ChsCurrentOperatorResolver currentOperatorResolver;

    private final ChsSignCredentialResolver signCredentialResolver;

    /**
     * 分页查询国家医保目录镜像；本地无数据且 {@code queryPlatformIfMiss=true} 时触发 1312 补库后再查。
     *
     * @param request 分页与筛选条件
     * @return 分页目录行
     */
    public PagedResult<NationalCatalogItemDto> queryNationalCatalogPage(QueryNationalCatalogRequest request) {
        // Pre：解析契约，构造领域分页入参
        ChsContractSnapshot contract = contractPort.requireContract(
            request.getContractBusNo(), request.getContractId());
        NationalCatalogPageQuery query = toPageQuery(request, contract.id());
        Page<YbNationalCatalogItem> page = catalogQueryService.page(query);
        // 本地镜像为空且允许补库：调 1312 入库后重查同一页
        if (page.isEmpty() && request.isQueryPlatformIfMiss()) {
            Refresh1312Request refresh = copyToRefresh1312(request, contractPort.requireContractBusNo(contract));
            refreshFromPlatform1312(refresh);
            page = catalogQueryService.page(query);
        }
        List<NationalCatalogItemDto> rows = new ArrayList<>(page.getContent().size());
        for (YbNationalCatalogItem item : page.getContent()) {
            rows.add(nationalCatalogMapper.toDto(item));
        }
        return ChsPagedResultBuilder.build(new PagedResult<>(), rows, page);
    }

    /**
     * 院内药品与国家目录智能对照建议（本地镜像打分；可触发 1312 补库）。
     *
     * @param request 药品名称、批准文号、规格等匹配键
     * @return 推荐等级、提示语与候选 hilist_code 列表
     */
    @Override
    public MedicationCatalogMappingSuggestDto suggestMedicationCatalogMapping(
            SuggestMedicationCatalogMappingRequest request) {
        // Pre：契约、listType、候选池上限
        ChsContractSnapshot contract = contractPort.requireContract(
            request.getContractBusNo(), null);
        String contractBusNo = contractPort.requireContractBusNo(contract);
        Long contractId = contract.id();
        Long tenantId = TenantUtils.getCurrentTenantId();
        String listType = ChsCatalogConstants.LIST_TYPE_WESTERN_DRUG;
        if (StringUtils.isNotBlank(request.getListType())) {
            listType = request.getListType().trim();
        }
        int max = ChsCatalogConstants.MEDICATION_MATCH_DEFAULT_MAX;
        if (request.getMaxCandidates() != null && request.getMaxCandidates() > 0) {
            max = request.getMaxCandidates();
        }
        // Pre：本地镜像候选池；空且允许补库时触发 1312
        List<YbNationalCatalogItem> pool = loadMedicationMatchPool(
            request, contractId, tenantId, contractBusNo, listType);
        // Cmd：打分排序
        List<CatalogMatchCandidateDto> candidates = CatalogMedicationMatchScorer.scoreAndSort(
            pool, request, max);
        String level = CatalogMedicationMatchScorer.resolveRecommendLevel(candidates);
        // Asm
        return new MedicationCatalogMappingSuggestDto()
            .setRecommendLevel(level)
            .setHint(CatalogMedicationMatchScorer.buildHint(level, candidates))
            .setCandidates(candidates);
    }

    /**
     * 按 1312 条件从平台拉取目录并 upsert 至本地镜像。
     *
     * @param request 1312 筛选与平台分页
     * @return 本次 upsert 行数
     * @throws BusinessException 平台返回失败或契约无效时
     */
    public int refreshFromPlatform1312(Refresh1312Request request) {
        // Pre：契约、操作员、adm_vs、1312 入参
        ChsContractSnapshot contract = contractPort.requireContract(
            request.getContractBusNo(), request.getContractId());
        String contractBusNo = contractPort.requireContractBusNo(contract);
        Long contractId = contract.id();
        Long operatorId = currentOperatorResolver.requireOperatorId("请先登录后再刷新医保目录");
        String insuAdmdvs = requireAdmVs(contractBusNo);
        // Cmd：调平台 1312
        JSONObject input = catalog1312InputAssembler.assemble(
            insuAdmdvs,
            request,
            resolveUpdtTime(contractId, request.getUpdtTime()),
            request.getPlatformPageNum(),
            request.getPlatformPageSize()
        );
        ResultBody body = gatewayClient.invoke(
            contractBusNo,
            ChsInfNo.CATALOG_QUERY,
            input,
            buildInvokeContext(operatorId)
        );
        assertPlatformSuccess(body);
        List<Catalog1312RowOutput> rows = ChsOutputParser.parseCatalog1312Rows(body);
        // Cmd：领域 upsert 本地镜像
        return catalogDomainService.upsertRows(
            contractId,
            TenantUtils.getCurrentTenantId(),
            operatorId,
            nationalCatalogMapper.toUpsertCommands(rows)
        );
    }

    /**
     * 列出指定契约下的目录下载游标。
     *
     * @param contractBusNo 契约 bus_no
     * @return 游标 DTO 列表
     */
    public List<CatalogDownloadCursorDto> listDownloadCursors(String contractBusNo) {
        // Pre：契约与租户
        ChsContractSnapshot contract = contractPort.requireContract(contractBusNo, null);
        Long tenantId = TenantUtils.getCurrentTenantId();
        Long contractId = contract.id();
        // Cmd：查 yb_catalog_download_cursor
        List<YbCatalogDownloadCursor> cursors = downloadCursorQueryService
                .listByContractIdAndTenantId(contractId, tenantId);
        // Asm： enrich 展示字段（infno 标签、ZIP 缓存状态、本地行数）
        Map<String, Long> catalogRowCountByListType = new HashMap<>(4);
        List<CatalogDownloadCursorDto> result = new ArrayList<>(cursors.size());
        for (YbCatalogDownloadCursor cursor : cursors) {
            result.add(enrichCursorDto(cursor, contractId, tenantId, catalogRowCountByListType));
        }
        return result;
    }

    /**
     * 探测待下载版本数：只执行阶段 1（13xx + 9102/缓存），<b>不入库</b>。
     *
     * <p>与 {@link #startCatalogDownload} 的阶段 1 逻辑相同，用于 UI 展示 pendingVersionCount。
     * 调试时关注日志前缀 {@code [catalog-dl][preview]}。</p>
     */
    public CatalogDownloadPreviewDto previewCatalogDownload(StartCatalogDownloadRequest request) {
        // Pre：契约、startVer、infno（默认 1301 药品）
        DownloadContext ctx = prepareDownloadContext(request, true);
        Integer previewMaxRounds = resolvePreviewMaxRounds(request);
        log.info("[catalog-dl][preview] contractBusNo={} infno={} startVer={} previewMaxRounds={}",
            ctx.contractBusNo(), ctx.infno(), ctx.startVer(), previewMaxRounds);
        // Cmd：阶段1 仅拉 ZIP/TXT，不入库
        CatalogDownloadPhase1Result phase1 = runPhase1(ctx, previewMaxRounds);
        // Asm
        CatalogDownloadPreviewDto dto = buildPreviewDto(ctx.infno(), ctx.startVer(), phase1);
        log.info("[catalog-dl][preview] 结束 pendingCount={} hasMore={}",
            dto.getPendingVersionCount(), dto.isHasMore());
        return dto;
    }

    /**
     * 文件型目录下载完整流水线。
     *
     * <ol>
     *   <li>阶段 1：拉全版本 ZIP/TXT（平台驱动停链，无轮次上限）</li>
     *   <li>阶段 2/3：提交 {@link CatalogDownloadImportService} 异步任务（前端轮询进度）</li>
     * </ol>
     *
     * <p>同 {@code requestVer} 的 ZIP 已落在 {@code versions/{catalogVer}.zip} 时跳过 9102。</p>
     */
    @Override
    public CatalogDownloadJobStartDto startCatalogDownload(StartCatalogDownloadRequest request) {
        // Pre：契约、游标 startVer、是否清 ZIP 缓存
        DownloadContext ctx = prepareDownloadContext(request, true);
        log.info("[catalog-dl][start] contractBusNo={} contractId={} infno={} startVer={}",
            ctx.contractBusNo(), ctx.contractId(), ctx.infno(), ctx.startVer());

        // Cmd：阶段1 — 13xx 轮询 + 9102 落 ZIP（平台停链为止）
        CatalogDownloadPhase1Result phase1 = runPhase1(ctx, null);
        List<CatalogDownloadVersionManifest> manifests = new ArrayList<>(phase1.bundles().size());
        for (CatalogDownloadVersionBundle bundle : phase1.bundles()) {
            manifests.add(CatalogDownloadVersionManifest.fromBundle(bundle));
        }
        log.info("[catalog-dl][phase1-done] versionCount={} 提交异步入库", manifests.size());

        // 无新版本：不提交异步任务
        if (manifests.isEmpty()) {
            return new CatalogDownloadJobStartDto()
                .setInfno(ctx.infno())
                .setTotalVersions(0)
                .setMessage("无新版本 ZIP，未提交入库任务");
        }

        // Cmd：阶段2/3 — 异步解压解析 upsert + 更新游标
        String jobId = catalogDownloadImportService.submitImport(
            new CatalogDownloadImportService.ImportJobRequest(
                ctx.contractId(),
                ctx.tenantId(),
                ctx.operatorId(),
                ctx.infno(),
                ctx.startVer(),
                manifests));

        // Asm
        return new CatalogDownloadJobStartDto()
            .setJobId(jobId)
            .setInfno(ctx.infno())
            .setTotalVersions(manifests.size())
            .setMessage("ZIP 拉取完成，正在后台解压、解析并入库，请轮询进度");
    }

    /**
     * 轮询文件型目录异步入库任务进度。
     *
     * @param jobId {@link #startCatalogDownload} 返回的任务 id
     * @return 进度 DTO（含 phase、已处理版本数等）
     */
    @Override
    public CatalogDownloadJobProgressDto getCatalogDownloadJobProgress(String jobId) {
        // Gate：jobId 必填
        if (StringUtils.isBlank(jobId)) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "jobId 不能为空");
        }
        // Cmd：内存注册表查询（任务过期后 null）
        CatalogDownloadJobProgressDto progress = catalogDownloadJobRegistry.get(jobId.trim());
        if (progress == null) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "入库任务不存在或已过期: " + jobId);
        }
        return progress;
    }

    /**
     * 解析下载/探测公共上下文。
     *
     * @param requireLogin true 时无登录用户抛错；false 时 operatorId 可为 0（一般仅内部用）
     */
    private DownloadContext prepareDownloadContext(StartCatalogDownloadRequest request, boolean requireLogin) {
        ChsContractSnapshot contract = contractPort.requireContract(
            request.getContractBusNo(), request.getContractId());
        String contractBusNo = contractPort.requireContractBusNo(contract);
        Long contractId = contract.id();
        Long tenantId = TenantUtils.getCurrentTenantId();
        Long operatorId;
        if (requireLogin) {
            operatorId = currentOperatorResolver.requireOperatorId("请先登录后再下载医保目录");
        } else {
            operatorId = currentOperatorResolver.findOperatorIdOrNull();
        }
        if (operatorId == null) {
            operatorId = 0L;
        }
        String infno = StringUtils.defaultIfBlank(request.getInfno(), ChsInfNo.DRUG_CATALOG_DOWNLOAD).trim();
        CatalogFileParserRegistry.require(infno);
        String verBranch = trimToNull(request.getVerBranch());
        String cursorVer = Boolean.TRUE.equals(request.getForceStartFromZero())
            ? null
            : downloadCursorDomainService.findLatest(contractId, infno, tenantId)
                .map(YbCatalogDownloadCursor::getLocalVer)
                .filter(StringUtils::isNotBlank)
                .orElse(null);
        String startVer = CatalogDownloadStartVerSupport.resolveStartVer(
            infno,
            verBranch,
            Boolean.TRUE.equals(request.getForceStartFromZero()),
            request.getStartLocalVer(),
            cursorVer);
        if (Boolean.TRUE.equals(request.getForceStartFromZero())) {
            log.info("[catalog-dl][ctx] forceStartFromZero=true，忽略游标，从 ver={} 开始 infno={}", startVer, infno);
        }
        boolean clearZipCache = Boolean.TRUE.equals(request.getClearZipCache());
        if (clearZipCache) {
            log.info("[catalog-dl][ctx] clearZipCache=true，将清空 {} 本地 ZIP 缓存后重新 9102", infno);
        }
        InvokeContext invokeContext = buildInvokeContext(operatorId);
        String fixmedinsCode = requireFixmedinsCode(contractBusNo);
        log.debug("[catalog-dl][ctx] tenantId={} infno={} startVer={} fixmedinsCode={} clearZipCache={}",
            tenantId, infno, startVer, fixmedinsCode, clearZipCache);
        return new DownloadContext(
            contractBusNo, contractId, tenantId, operatorId, infno,
            startVer, invokeContext, fixmedinsCode, clearZipCache);
    }

    /** 探测可选轮次上限；未传则与正式下载一样无上限（仅平台停链）。 */
    private static Integer resolvePreviewMaxRounds(StartCatalogDownloadRequest request) {
        if (request.getMaxVersions() != null && request.getMaxVersions() > 0) {
            return request.getMaxVersions();
        }
        return null;
    }

    private CatalogDownloadPhase1Result runPhase1(DownloadContext ctx, Integer maxRounds) {
        CatalogDownloadPhase1Context phase1Context = new CatalogDownloadPhase1Context(
            ctx.contractBusNo(),
            ctx.contractId(),
            ctx.tenantId(),
            ctx.infno(),
            ctx.startVer(),
            ctx.fixmedinsCode(),
            ctx.invokeContext(),
            ctx.clearZipCache(),
            maxRounds);
        return catalogDownloadPhase1Service.collectVersionBundles(phase1Context);
    }

    private static List<VersionBundle> toVersionBundles(List<CatalogDownloadVersionBundle> bundles) {
        List<VersionBundle> list = new ArrayList<>(bundles.size());
        for (CatalogDownloadVersionBundle bundle : bundles) {
            list.add(new VersionBundle(
                bundle.requestVer(),
                bundle.meta(),
                bundle.zipFromCache(),
                bundle.txtLineCount(),
                bundle.txtVersionName(),
                bundle.nextLocalVer()));
        }
        return list;
    }

    private CatalogDownloadCursorDto enrichCursorDto(
            YbCatalogDownloadCursor cursor,
            Long contractId,
            Long tenantId,
            Map<String, Long> catalogRowCountByListType) {
        CatalogDownloadCursorDto dto = nationalCatalogMapper.toDto(cursor);
        if (dto == null) {
            return null;
        }
        String infno = StringUtils.defaultIfBlank(cursor.getInfno(), ChsInfNo.DRUG_CATALOG_DOWNLOAD);
        dto.setInfnoLabel(CatalogDownloadDisplaySupport.infnoLabel(infno));
        dto.setLocalVerShort(CatalogDownloadDisplaySupport.shortenVer(cursor.getLocalVer()));
        dto.setSyncStatusText(CatalogDownloadDisplaySupport.formatSuccessAt(cursor.getLastSuccessAt()));
        dto.setLastFilenameShort(CatalogDownloadDisplaySupport.formatFilename(cursor.getLastFilename()));
        // 下一批 13xx 将使用的 ver；若该版本 ZIP 已缓存（探测/中断重试）则 UI 显示「已缓存」
        boolean zipCached = StringUtils.isNotBlank(cursor.getLocalVer())
            && catalogDownloadZipCache.exists(tenantId, contractId, infno, cursor.getLocalVer());
        dto.setLastZipCached(zipCached);
        String listType = CatalogDownloadDisplaySupport.listTypeForInfno(infno);
        dto.setLocalCatalogRowCount(catalogRowCountByListType.computeIfAbsent(
            listType,
            lt -> catalogQueryService.countByContractAndListType(contractId, lt, tenantId)));
        return dto;
    }

    private CatalogDownloadPreviewDto buildPreviewDto(
            String infno, String startVer, CatalogDownloadPhase1Result phase1) {
        List<VersionBundle> bundles = toVersionBundles(phase1.bundles());
        List<CatalogDownloadVersionItemDto> versions = toVersionItemDtosFromBundles(bundles);
        return new CatalogDownloadPreviewDto()
            .setInfno(infno)
            .setStartLocalVer(startVer)
            .setStartLocalVerShort(CatalogDownloadDisplaySupport.shortenVer(startVer))
            .setPendingVersionCount(versions.size())
            .setHasMore(phase1.stoppedByPreviewRoundCap())
            .setVersions(versions);
    }

    private List<CatalogDownloadVersionItemDto> toVersionItemDtosFromBundles(List<VersionBundle> bundles) {
        List<CatalogDownloadVersionItemDto> versions = new ArrayList<>(bundles.size());
        int index = 0;
        for (VersionBundle bundle : bundles) {
            index++;
            versions.add(buildVersionItemDto(index, bundle, bundle.txtLineCount()));
        }
        return versions;
    }

    private CatalogDownloadVersionItemDto buildVersionItemDto(
            int index, VersionBundle bundle, int parsedRowCount) {
        String txtVer = bundle.txtVersionName();
        CatalogFileDownloadOutput meta = bundle.meta();
        return new CatalogDownloadVersionItemDto()
            .setIndex(index)
            .setRequestVer(bundle.requestVer())
            .setRequestVerShort(CatalogDownloadDisplaySupport.shortenVer(bundle.requestVer()))
            .setTxtVersionName(txtVer)
            .setTxtVersionNameShort(CatalogDownloadDisplaySupport.shortenVer(txtVer))
            .setNextVersion(txtVer)
            .setNextVersionShort(CatalogDownloadDisplaySupport.shortenVer(txtVer))
            .setNextDownloadVer(bundle.nextLocalVer())
            .setNextDownloadVerShort(CatalogDownloadDisplaySupport.shortenVer(bundle.nextLocalVer()))
            .setParsedRowCount(parsedRowCount)
            .setFileQuryNo(meta.getFileQuryNo())
            .setFilename(meta.getFilename())
            .setDataCnt(meta.getDataCnt())
            .setZipFromCache(bundle.zipFromCache());
    }

    /** 单次下载/探测的请求级上下文（不可变，贯穿三阶段）。 */
    private record DownloadContext(
        String contractBusNo,
        Long contractId,
        Long tenantId,
        Long operatorId,
        String infno,
        String startVer,
        InvokeContext invokeContext,
        String fixmedinsCode,
        boolean clearZipCache) {
    }

    /** 单版本：本包行数 + 版本名 + 建议下一轮 data.ver（TXT 仅存于 ZIP 缓存）。 */
    private record VersionBundle(
        String requestVer,
        CatalogFileDownloadOutput meta,
        boolean zipFromCache,
        int txtLineCount,
        String txtVersionName,
        String nextLocalVer) {
    }

    /**
     * HTTP 请求映射为领域分页查询入参。
     *
     * @param request    HTTP 请求
     * @param contractId 契约主键
     * @return 领域分页入参
     */
    private NationalCatalogPageQuery toPageQuery(QueryNationalCatalogRequest request, Long contractId) {
        NationalCatalogPageQuery query = new NationalCatalogPageQuery();
        query.setContractId(contractId);
        query.setTenantId(TenantUtils.getCurrentTenantId());
        query.setListType(request.getListType());
        query.setKeyword(request.getKeyword());
        query.setHilistCode(request.getHilistCode());
        query.setPageNo(request.getPageNo());
        query.setPageSize(request.getPageSize());
        String valiFlag;
        if (StringUtils.isNotBlank(request.getValiFlag())) {
            valiFlag = request.getValiFlag();
        } else if (request.isDedupeByHilistCode()) {
            valiFlag = ChsCatalogConstants.DEFAULT_VALI_FLAG;
        } else {
            valiFlag = null;
        }
        query.setValiFlag(valiFlag);
        query.setAprvno(request.getAprvno());
        query.setSpec(request.getSpec());
        if (YbNationalCatalogQueryService.prefers1301Detail(request.getAprvno(), request.getSpec())) {
            query.setPickerMode1301Detail(true);
            query.setPickerMode1312Only(false);
            query.setDedupeByHilistCode(false);
            query.setSourceInfno(ChsCatalogConstants.SOURCE_INFNO_1301);
        } else if (request.isDedupeByHilistCode()) {
            query.setDedupeByHilistCode(true);
            String source;
            if (StringUtils.isNotBlank(request.getSourceInfno())) {
                source = request.getSourceInfno().trim();
            } else {
                source = ChsCatalogConstants.SOURCE_INFNO_1312;
            }
            query.setSourceInfno(source);
            query.setPickerMode1312Only(ChsCatalogConstants.SOURCE_INFNO_1312.equals(source));
        } else if (StringUtils.isNotBlank(request.getSourceInfno())) {
            query.setSourceInfno(request.getSourceInfno().trim());
            query.setPickerMode1312Only(
                ChsCatalogConstants.SOURCE_INFNO_1312.equals(request.getSourceInfno().trim()));
        }
        return query;
    }

    private List<YbNationalCatalogItem> loadMedicationMatchPool(
            SuggestMedicationCatalogMappingRequest request,
            Long contractId,
            Long tenantId,
            String contractBusNo,
            String listType) {
        String keyword = ChsTextSupport.blankToNull(request.getName());
        String aprvno = ChsTextSupport.blankToNull(request.getApprovalNumber());
        String spec = ChsTextSupport.blankToNull(request.getTotalVolume());
        String codeExact = ChsTextSupport.blankToNull(request.getNationalDrugCode());
        List<YbNationalCatalogItem> pool = catalogQueryService.listForMedicationMatch(
            contractId,
            tenantId,
            listType,
            keyword,
            aprvno,
            spec,
            codeExact,
            ChsCatalogConstants.MEDICATION_MATCH_CANDIDATE_POOL);
        if (!pool.isEmpty() || !request.isQueryPlatformIfMiss()) {
            return pool;
        }
        if (YbNationalCatalogQueryService.prefers1301Detail(aprvno, spec)) {
            return pool;
        }
        Refresh1312Request refresh = new Refresh1312Request();
        refresh.setContractBusNo(contractBusNo);
        refresh.setListType(listType);
        refresh.setValiFlag(ChsCatalogConstants.DEFAULT_VALI_FLAG);
        refresh.setHilistCode(codeExact);
        refresh.setHilistName(keyword);
        refresh.setPlatformPageNum(1);
        refresh.setPlatformPageSize(ChsCatalogConstants.DEFAULT_PLATFORM_PAGE_SIZE);
        refreshFromPlatform1312(refresh);
        return catalogQueryService.listForMedicationMatch(
            contractId,
            tenantId,
            listType,
            keyword,
            aprvno,
            spec,
            codeExact,
            ChsCatalogConstants.MEDICATION_MATCH_CANDIDATE_POOL);
    }

    /**
     * 构造医保网关调用上下文（操作员 id 为字符串）。
     *
     * @param operatorId 已门控的操作人主键
     * @return 调用上下文
     */
    private InvokeContext buildInvokeContext(Long operatorId) {
        String operatorIdText;
        if (operatorId != null) {
            operatorIdText = String.valueOf(operatorId);
        } else {
            operatorIdText = null;
        }
        InvokeContext partial = InvokeContext.builder()
            .operatorId(operatorIdText)
            .build();
        return InvokeContext.builder()
            .operatorId(signCredentialResolver.resolveOperatorId(partial))
            .operatorName(signCredentialResolver.resolveOperatorName(partial))
            .build();
    }

    /**
     * 解析 1312 增量游标：请求优先，否则取本地最大 updt_time，再无则默认起始日。
     *
     * @param contractId      契约主键
     * @param requestUpdtTime 请求中的 updt_time
     * @return yyyy-MM-dd 游标
     */
    private String resolveUpdtTime(Long contractId, String requestUpdtTime) {
        if (StringUtils.isNotBlank(requestUpdtTime)) {
            return requestUpdtTime.trim();
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        return catalogQueryService.findMaxUpdtTime(contractId, tenantId)
            .map(ChsCatalogConstants.UPDT_TIME_DATE_FORMATTER::format)
            .orElse(ChsCatalogConstants.DEFAULT_CATALOG_UPDT_TIME);
    }

    /**
     * 将分页查询请求复制为 1312 刷新请求（补库场景）。
     *
     * @param request       分页查询请求
     * @param contractBusNo 契约 bus_no
     * @return 1312 刷新请求
     */
    private static Refresh1312Request copyToRefresh1312(QueryNationalCatalogRequest request, String contractBusNo) {
        Refresh1312Request refresh = new Refresh1312Request();
        refresh.setContractBusNo(contractBusNo);
        copy1312Filter(request, refresh);
        refresh.setPlatformPageNum(1);
        int pageSize = ChsCatalogConstants.DEFAULT_PLATFORM_PAGE_SIZE;
        if (request.getPageSize() != null) {
            pageSize = request.getPageSize();
        }
        refresh.setPlatformPageSize(pageSize);
        return refresh;
    }

    /**
     * 复制 1312 筛选字段（不含契约与平台分页）。
     *
     * @param from 源筛选
     * @param to   目标筛选
     */
    private static void copy1312Filter(Catalog1312FilterRequest from, Catalog1312FilterRequest to) {
        to.setListType(from.getListType());
        to.setKeyword(from.getKeyword());
        to.setHilistCode(from.getHilistCode());
        to.setHilistName(from.getHilistName());
        to.setPinyin(from.getPinyin());
        to.setUpdtTime(from.getUpdtTime());
        to.setValiFlag(from.getValiFlag());
        to.setChrgitmLv(from.getChrgitmLv());
        to.setLmtUsedFlag(from.getLmtUsedFlag());
        to.setQueryDate(from.getQueryDate());
        to.setBegndate(from.getBegndate());
    }

    /**
     * 校验 1312 平台应答 infcode 为成功。
     *
     * @param body 平台应答
     * @throws BusinessException infcode 非成功时
     */
    private static void assertPlatformSuccess(ResultBody body) {
        if (body == null || !ChsPlatformCodes.INFCODE_SUCCESS.equals(body.getInfcode())) {
            String msg = "empty response";
            if (body != null) {
                msg = body.getErrMsg();
            }
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "1312 查询失败: " + msg);
        }
    }

    /**
     * 按 bus_no 从 chs-contract.properties 读取 fixmedins_code（不读 fin_contract）。
     */
    private String requireFixmedinsCode(String contractBusNo) {
        String value = contractPropertiesPort.findFixmedinsCode(contractBusNo).orElse("");
        if (StringUtils.isBlank(value)) {
            throw new BusinessException(
                ChsErrorCodes.PLATFORM_FAILED,
                "chs-contract.properties 未配置 contract." + contractBusNo + ".fixmedins_code，无法执行 9102 文件下载");
        }
        return value.trim();
    }

    /**
     * 按 bus_no 从 chs-contract.properties 读取 adm_vs（不读 fin_contract）。
     */
    private String requireAdmVs(String contractBusNo) {
        return contractPropertiesPort.findAdmVs(contractBusNo).orElse("").trim();
    }
}