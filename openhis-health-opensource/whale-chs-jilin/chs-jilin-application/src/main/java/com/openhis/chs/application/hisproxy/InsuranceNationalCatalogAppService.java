/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.hisproxy;

import com.openhis.chs.domain.catalog.dto.request.QueryNationalCatalogRequest;
import com.openhis.chs.domain.catalog.dto.request.Refresh1312Request;
import com.openhis.chs.domain.catalog.dto.request.StartCatalogDownloadRequest;
import com.openhis.chs.domain.catalog.dto.request.SuggestMedicationCatalogMappingRequest;
import com.openhis.chs.domain.catalog.dto.response.CatalogDownloadCursorDto;
import com.openhis.chs.domain.catalog.dto.response.CatalogDownloadJobProgressDto;
import com.openhis.chs.domain.catalog.dto.response.CatalogDownloadJobStartDto;
import com.openhis.chs.domain.catalog.dto.response.CatalogDownloadPreviewDto;
import com.openhis.chs.domain.catalog.dto.response.MedicationCatalogMappingSuggestDto;
import com.openhis.chs.domain.catalog.dto.response.NationalCatalogItemDto;
import com.openhis.chs.domain.catalog.facade.ChsNationalCatalogOperations;
import com.openhis.whale.core.application.dto.PagedResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 国家医保目录 bridge 代理（方案 1A：前端仍调 {@code /api/app/insuranceNationalCatalog/*}）。
 *
 * <p>embedded：委托同 JVM {@link ChsNationalCatalogOperations}；remote：委托 HTTP 至 chs-application。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InsuranceNationalCatalogAppService {

    private final ChsNationalCatalogOperations catalogOperations;

    /**
     * 分页查询国家目录镜像。
     *
     * @param request 分页与筛选
     * @return 目录行分页
     */
    public PagedResult<NationalCatalogItemDto> queryNationalCatalogPage(QueryNationalCatalogRequest request) {
        // Cmd：委托 facade
        return catalogOperations.queryNationalCatalogPage(request);
    }

    /**
     * 药品选码推荐（本地镜像打分）。
     *
     * @param request 药品名称、规格、批准文号等
     * @return 推荐等级与候选列表
     */
    public MedicationCatalogMappingSuggestDto suggestMedicationCatalogMapping(
        SuggestMedicationCatalogMappingRequest request) {
        // Cmd：委托 facade
        return catalogOperations.suggestMedicationCatalogMapping(request);
    }

    /**
     * 1312 刷新入库。
     *
     * @param request 1312 筛选条件
     * @return upsert 行数
     */
    public int refreshFromPlatform1312(Refresh1312Request request) {
        // Cmd：委托 facade
        return catalogOperations.refreshFromPlatform1312(request);
    }

    /**
     * 列出目录下载游标。
     *
     * @param contractBusNo 契约 bus_no
     * @return 游标列表
     */
    public List<CatalogDownloadCursorDto> listDownloadCursors(String contractBusNo) {
        // Cmd：委托 facade
        return catalogOperations.listDownloadCursors(contractBusNo);
    }

    /**
     * 探测待下载版本数（不入库）。
     *
     * @param request 下载参数
     * @return 预览 DTO
     */
    public CatalogDownloadPreviewDto previewCatalogDownload(StartCatalogDownloadRequest request) {
        // Cmd：委托 facade
        return catalogOperations.previewCatalogDownload(request);
    }

    /**
     * 启动文件型目录下载（阶段一同步 + 阶段二三异步）。
     *
     * @param request 下载参数
     * @return jobId 与版本数
     */
    public CatalogDownloadJobStartDto startCatalogDownload(StartCatalogDownloadRequest request) {
        // Cmd：委托 facade
        return catalogOperations.startCatalogDownload(request);
    }

    /**
     * 轮询异步入库进度。
     *
     * @param jobId 任务 id
     * @return 进度 DTO
     */
    public CatalogDownloadJobProgressDto getCatalogDownloadJobProgress(String jobId) {
        // Cmd：委托 facade
        return catalogOperations.getCatalogDownloadJobProgress(jobId);
    }
}
