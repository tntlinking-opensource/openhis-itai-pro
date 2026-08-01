/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.inventory.service;

import com.openhis.chs.domain.inventory.access.Inv3507Invoker;
import com.openhis.chs.domain.inventory.access.Inv35xxQueryInvoker;
import com.openhis.chs.domain.inventory.access.InventoryCompareAccess;
import com.openhis.chs.domain.inventory.access.InventoryInitializeAccess;
import com.openhis.chs.domain.inventory.access.InventoryInitializeFillAccess;
import com.openhis.chs.domain.inventory.access.InventoryRetryAccess;
import com.openhis.chs.domain.inventory.access.InventoryUploadAccess;
import com.openhis.chs.domain.inventory.command.InitializeInventoryCommand;
import com.openhis.chs.domain.inventory.command.InvDelete3507Command;
import com.openhis.chs.domain.inventory.command.InvUpload3501Command;
import com.openhis.chs.domain.inventory.command.InvUpload3502Command;
import com.openhis.chs.domain.inventory.command.InvUpload3503Command;
import com.openhis.chs.domain.inventory.command.InvUpload3504Command;
import com.openhis.chs.domain.inventory.command.InvUpload3505Command;
import com.openhis.chs.domain.inventory.command.InvUpload3506Command;
import com.openhis.chs.domain.inventory.command.RetryInvUploadWithRequestCommand;
import com.openhis.chs.domain.inventory.command.RetryPendingInvUploadsCommand;
import com.openhis.chs.domain.inventory.entity.YbInvUpload;
import com.openhis.chs.domain.inventory.queries.CompareLocalVsPlatformQuery;
import com.openhis.chs.domain.inventory.queries.ListLocalInvUploadRecordsQuery;
import com.openhis.chs.domain.inventory.queries.QueryPlatformInvQuery;
import com.openhis.chs.domain.inventory.result.CompareLocalVsPlatformResult;
import com.openhis.chs.domain.inventory.result.InitializeInventoryFillMeta;
import com.openhis.chs.domain.inventory.result.InitializeInventoryResult;
import com.openhis.chs.domain.inventory.result.InvDelete3507Result;
import com.openhis.chs.domain.inventory.result.InvUpload3501Result;
import com.openhis.chs.domain.inventory.result.InvUpload3502Result;
import com.openhis.chs.domain.inventory.result.InvUpload3503Result;
import com.openhis.chs.domain.inventory.result.InvUpload3504Result;
import com.openhis.chs.domain.inventory.result.InvUpload3505Result;
import com.openhis.chs.domain.inventory.result.InvUpload3506Result;
import com.openhis.chs.domain.inventory.result.LocalInvUploadResult;
import com.openhis.chs.domain.inventory.result.PlatformInvQueryResult;
import com.openhis.chs.domain.inventory.result.RetryInvUploadWithRequestResult;
import com.openhis.chs.domain.inventory.result.RetryPendingInvUploadsResult;
import com.openhis.chs.domain.inventory.service.YbInvUploadDomainService;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 进销存运维台 UI（{@code POST /api/app/insuranceInventory/*}）。
 * <p>Tab 分组：本地流水 / 3501–3502 手工与批初始化 / 失败重试与单条补传；3507/3508–3513/差异比对 API 保留、UI 本期隐藏。</p>
 */
@Service
@RequiredArgsConstructor
public class InsuranceInventoryAppService {

    private final InventoryUploadAccess uploadWorkflow;
    private final InventoryInitializeAccess initializeWorkflow;
    private final InventoryInitializeFillAccess initializeUploadContextLoader;
    private final Inv3507Invoker inv3507Invoker;
    private final Inv35xxQueryInvoker inv35xxQueryInvoker;
    private final InventoryCompareAccess compareWorkflow;
    private final InventoryRetryAccess retryWorkflow;
    private final YbInvUploadDomainService uploadDomainService;

    /** 【3505】商品销售上报。 */
    public List<InvUpload3505Result> uploadSale3505(InvUpload3505Command command) {
        return uploadWorkflow.uploadSale3505(command);
    }

    /** 【3506】商品销售退货上报。 */
    public List<InvUpload3506Result> uploadSaleReturn3506(InvUpload3506Command command) {
        return uploadWorkflow.uploadSaleReturn3506(command);
    }

    /** 【3503】商品采购入库上报。 */
    public List<InvUpload3503Result> uploadPurchase3503(InvUpload3503Command command) {
        return uploadWorkflow.uploadPurchase3503(command);
    }

    /** 【3504】商品采购退货上报。 */
    public List<InvUpload3504Result> uploadPurchaseReturn3504(InvUpload3504Command command) {
        return uploadWorkflow.uploadPurchaseReturn3504(command);
    }

    /** 【3501】商品盘存上报。 */
    public List<InvUpload3501Result> uploadStocktake3501(InvUpload3501Command command) {
        return uploadWorkflow.uploadStocktake3501(command);
    }

    /** 【3502】商品库存变更上报。 */
    public List<InvUpload3502Result> uploadInvChange3502(InvUpload3502Command command) {
        return uploadWorkflow.uploadInvChange3502(command);
    }

    /** 【3501+3502】批初始化进销存（盘存+变更配对）。 */
    public InitializeInventoryResult initializeInventory(InitializeInventoryCommand command) {
        InitializeInventoryFillMeta meta = initializeUploadContextLoader.fillPairs(command);
        InitializeInventoryResult result = initializeWorkflow.initialize(command);
        applyFillMeta(result, meta);
        return result;
    }

    /** 【3507】进销存数据删除。 */
    public InvDelete3507Result deleteInvData3507(InvDelete3507Command command) {
        return inv3507Invoker.invoke(command);
    }

    /**
     * 本地查询：筛选 yb_inv_upload 上报流水（含 request/response 报文，供运维补传）。
     *
     * @param query 筛选条件；null 时按租户取最近流水
     * @return 本地上报流水列表
     */
    public List<LocalInvUploadResult> listLocalUploadRecords(ListLocalInvUploadRecordsQuery query) {
        // Gate：租户上下文必有，否则流水隔离失效
        Long tenantId = TenantUtils.getCurrentTenantId();
        // Pre：空查询条件时回落为空对象，避免 NPE
        ListLocalInvUploadRecordsQuery safeQuery = new ListLocalInvUploadRecordsQuery();
        if (query != null) {
            safeQuery = query;
        }
        // Cmd：按条件读本地流水
        List<YbInvUpload> rows = uploadDomainService.listUploadRecords(safeQuery, tenantId);
        // Asm：实体转运维 DTO（含可编辑 request_json）
        List<LocalInvUploadResult> dtos = new ArrayList<>(rows.size());
        for (YbInvUpload row : rows) {
            dtos.add(uploadDomainService.toLocalDto(row));
        }
        return dtos;
    }

    /**
     * 按流水主键取本地上报详情（含完整 request/response，供补传弹窗回显）。
     *
     * @param uploadId 上报流水主键
     * @return 单行详情；不存在或不属于当前租户时 null
     */
    public LocalInvUploadResult getLocalUploadRecord(Long uploadId) {
        // Gate：租户隔离
        Long tenantId = TenantUtils.getCurrentTenantId();
        // Cmd：按 id + 租户加载
        return uploadDomainService.findByIdForTenant(uploadId, tenantId)
            .map(uploadDomainService::toLocalDto)
            .orElse(null);
    }

    /** 【3501–3506】失败/待报记录重试。 */
    public RetryPendingInvUploadsResult retryPendingUploads(RetryPendingInvUploadsCommand command) {
        return retryWorkflow.retry(command);
    }

    /**
     * 单条补传：可选编辑 request_json 后重放（不从业务表重建）。
     *
     * @param command 含 uploadId 与可选 requestJson
     * @return 补传结果
     */
    public RetryInvUploadWithRequestResult retryUploadWithRequest(RetryInvUploadWithRequestCommand command) {
        return retryWorkflow.retryOne(command);
    }

    /** 【3508】平台库存信息查询。 */
    public PlatformInvQueryResult queryPlatformInventory3508(QueryPlatformInvQuery query) {
        return inv35xxQueryInvoker.query3508(query);
    }

    /** 【3509】平台库存变更记录查询。 */
    public PlatformInvQueryResult queryPlatformInvChange3509(QueryPlatformInvQuery query) {
        return inv35xxQueryInvoker.query3509(query);
    }

    /** 【3510】平台采购信息查询。 */
    public PlatformInvQueryResult queryPlatformPurchase3510(QueryPlatformInvQuery query) {
        return inv35xxQueryInvoker.query3510(query);
    }

    /** 【3511】平台销售信息查询。 */
    public PlatformInvQueryResult queryPlatformSale3511(QueryPlatformInvQuery query) {
        return inv35xxQueryInvoker.query3511(query);
    }

    /** 【3512】平台入库追溯信息查询。 */
    public PlatformInvQueryResult queryPlatformInboundTrace3512(QueryPlatformInvQuery query) {
        return inv35xxQueryInvoker.query3512(query);
    }

    /** 【3513】平台销售追溯信息查询。 */
    public PlatformInvQueryResult queryPlatformSaleTrace3513(QueryPlatformInvQuery query) {
        return inv35xxQueryInvoker.query3513(query);
    }

    /** 本地查询：本地流水 vs 平台 3508–3513 差异比对。 */
    public CompareLocalVsPlatformResult compareLocalVsPlatform(CompareLocalVsPlatformQuery query) {
        return compareWorkflow.compare(query);
    }

    private static void applyFillMeta(InitializeInventoryResult result, InitializeInventoryFillMeta meta) {
        if (result == null || meta == null) {
            return;
        }
        result.setPageNo(meta.getPageNo());
        result.setNextPageNo(meta.getNextPageNo());
        result.setHasMore(meta.getHasMore());
        result.setTotalElements(meta.getTotalElements());
        result.setTotalPages(meta.getTotalPages());
        result.setSkippedAlreadyInitialized(meta.getSkippedAlreadyInitialized());
    }
}
