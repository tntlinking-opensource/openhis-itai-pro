package com.openhis.chs.domain.catalog.facade;

import com.openhis.chs.domain.catalog.dto.request.QueryNationalCatalogRequest;
import com.openhis.chs.domain.catalog.dto.request.Refresh1312Request;
import com.openhis.chs.domain.catalog.dto.request.StartCatalogDownloadRequest;
import com.openhis.chs.domain.catalog.dto.request.SuggestMedicationCatalogMappingRequest;
import com.openhis.chs.domain.catalog.dto.response.MedicationCatalogMappingSuggestDto;
import com.openhis.chs.domain.catalog.dto.response.CatalogDownloadCursorDto;
import com.openhis.chs.domain.catalog.dto.response.CatalogDownloadJobProgressDto;
import com.openhis.chs.domain.catalog.dto.response.CatalogDownloadJobStartDto;
import com.openhis.chs.domain.catalog.dto.response.CatalogDownloadPreviewDto;
import com.openhis.chs.domain.catalog.dto.response.CatalogDownloadResultDto;
import com.openhis.chs.domain.catalog.dto.response.NationalCatalogItemDto;
import com.openhis.whale.core.application.dto.PagedResult;

import java.util.List;

/**
 * 国家医保目录应用能力（chs-application 实现；bridge 1A 代理）。
 *
 * <p>文件下载相关：</p>
 * <ul>
 *   <li>{@link #listDownloadCursors} — 读 yb_catalog_download_cursor</li>
 *   <li>{@link #previewCatalogDownload} — 仅阶段 1，返回待下载版本数</li>
 *   <li>{@link #startCatalogDownload} — 阶段 1 拉 ZIP，阶段 2/3 异步入库</li>
 *   <li>{@link #getCatalogDownloadJobProgress} — 轮询入库进度</li>
 * </ul>
 */
public interface ChsNationalCatalogOperations {

    PagedResult<NationalCatalogItemDto> queryNationalCatalogPage(QueryNationalCatalogRequest request);

    /**
     * 药品维护：按院内字段推荐国家目录候选（不自动写 yb_no）。
     */
    MedicationCatalogMappingSuggestDto suggestMedicationCatalogMapping(
        SuggestMedicationCatalogMappingRequest request);

    int refreshFromPlatform1312(Refresh1312Request request);

    List<CatalogDownloadCursorDto> listDownloadCursors(String contractBusNo);

    /** 探测版本（1301+ZIP，不入库）；日志前缀 [catalog-dl][preview] */
    CatalogDownloadPreviewDto previewCatalogDownload(StartCatalogDownloadRequest request);

    /** 拉取 ZIP 并提交异步入库任务；日志前缀 [catalog-dl] */
    CatalogDownloadJobStartDto startCatalogDownload(StartCatalogDownloadRequest request);

    /** 查询异步入库任务进度（解压/解析/写库条数） */
    CatalogDownloadJobProgressDto getCatalogDownloadJobProgress(String jobId);
}
