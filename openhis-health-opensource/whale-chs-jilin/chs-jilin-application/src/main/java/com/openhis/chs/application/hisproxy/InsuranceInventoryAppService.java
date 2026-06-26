/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.hisproxy;

import com.openhis.chs.domain.inventory.dto.request.CompareLocalVsPlatformRequest;
import com.openhis.chs.domain.inventory.dto.request.ListLocalInvUploadRecordsRequest;
import com.openhis.chs.domain.inventory.dto.request.QueryPlatformInvRequest;
import com.openhis.chs.domain.inventory.dto.request.RetryPendingInvUploadsRequest;
import com.openhis.chs.domain.inventory.dto.request.UploadPurchase3503Request;
import com.openhis.chs.domain.inventory.dto.request.UploadPurchaseReturn3504Request;
import com.openhis.chs.domain.inventory.dto.request.UploadSale3505Request;
import com.openhis.chs.domain.inventory.dto.request.UploadSaleReturn3506Request;
import com.openhis.chs.domain.inventory.dto.request.InitializeInventoryRequest;
import com.openhis.chs.domain.inventory.dto.request.UploadStocktake3501Request;
import com.openhis.chs.domain.inventory.dto.request.UploadInvChange3502Request;
import com.openhis.chs.domain.inventory.dto.request.DeleteInvData3507Request;
import com.openhis.chs.domain.inventory.dto.response.InvDelete3507Result;
import com.openhis.chs.domain.inventory.dto.response.InitializeInventoryFillMeta;
import com.openhis.chs.domain.inventory.dto.response.InitializeInventoryResult;
import com.openhis.chs.domain.inventory.dto.response.InvUpload3501Result;
import com.openhis.chs.domain.inventory.dto.response.InvUpload3502Result;
import com.openhis.chs.domain.inventory.dto.response.InvUpload3503Result;
import com.openhis.chs.domain.inventory.dto.response.InvUpload3504Result;
import com.openhis.chs.domain.inventory.dto.response.InvUpload3505Result;
import com.openhis.chs.domain.inventory.dto.response.InvUpload3506Result;
import com.openhis.chs.domain.inventory.dto.response.CompareLocalVsPlatformResult;
import com.openhis.chs.domain.inventory.dto.response.LocalInvUploadDto;
import com.openhis.chs.domain.inventory.dto.response.PlatformInvQueryResult;
import com.openhis.chs.domain.inventory.dto.response.RetryPendingInvUploadsResult;
import com.openhis.chs.domain.inventory.facade.ChsInventoryOperations;
import com.openhis.chs.application.hisproxy.support.InventoryInitializeUploadContextLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/** 进销存 bridge：{@code /api/app/insuranceInventory/*}。 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InsuranceInventoryAppService {

    private final ChsInventoryOperations inventoryOperations;
    private final InventoryInitializeUploadContextLoader initializeUploadContextLoader;

    /**
     * 【3505】上传销售数据。
     *
     * @param request 销售明细行
     * @return 逐行上传结果
     */
    public List<InvUpload3505Result> uploadSale3505(UploadSale3505Request request) {
        // Cmd：委托 facade
        return inventoryOperations.uploadSale3505(request);
    }

    /**
     * 【3506】上传销售退货数据。
     *
     * @param request 销售退货明细行
     * @return 逐行上传结果
     */
    public List<InvUpload3506Result> uploadSaleReturn3506(UploadSaleReturn3506Request request) {
        // Cmd：委托 facade
        return inventoryOperations.uploadSaleReturn3506(request);
    }

    /**
     * 【3503】上传采购数据。
     *
     * @param request 采购明细行
     * @return 逐行上传结果
     */
    public List<InvUpload3503Result> uploadPurchase3503(UploadPurchase3503Request request) {
        // Cmd：委托 facade
        return inventoryOperations.uploadPurchase3503(request);
    }

    /**
     * 【3504】上传采购退货数据。
     *
     * @param request 采购退货明细行
     * @return 逐行上传结果
     */
    public List<InvUpload3504Result> uploadPurchaseReturn3504(UploadPurchaseReturn3504Request request) {
        // Cmd：委托 facade
        return inventoryOperations.uploadPurchaseReturn3504(request);
    }

    /**
     * 【3501】上传盘存数据。
     *
     * @param request 盘存明细行
     * @return 逐行上传结果
     */
    public List<InvUpload3501Result> uploadStocktake3501(UploadStocktake3501Request request) {
        // Cmd：委托 facade
        return inventoryOperations.uploadStocktake3501(request);
    }

    /**
     * 【3502】上传库存变更数据。
     *
     * @param request 库存变更明细行
     * @return 逐行上传结果
     */
    public List<InvUpload3502Result> uploadInvChange3502(UploadInvChange3502Request request) {
        // Cmd：委托 facade
        return inventoryOperations.uploadInvChange3502(request);
    }

    /**
     * 首次初始化进销存（3501+3502 配对上传）。
     *
     * @param request 库房 ID、分页续跑与可选 pairs
     * @return 初始化上传结果含续跑元数据
     */
    public InitializeInventoryResult initializeInventory(InitializeInventoryRequest request) {
        // Pre：从库房现存药品库存组装 3501+3502 配对行
        InitializeInventoryFillMeta meta = initializeUploadContextLoader.fillPairs(request);
        // Cmd：委托 facade 执行配对上传
        InitializeInventoryResult result = inventoryOperations.initializeInventory(request);
        applyFillMeta(result, meta);
        return result;
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

    /**
     * 【3507】删除进销存数据。
     *
     * @param request 删除条件
     * @return 删除结果
     */
    public InvDelete3507Result deleteInvData3507(DeleteInvData3507Request request) {
        // Cmd：委托 facade
        return inventoryOperations.deleteInvData3507(request);
    }

    /**
     * 查询本地上传记录。
     *
     * @param request 分页与筛选条件
     * @return 本地上传记录列表
     */
    public List<LocalInvUploadDto> listLocalUploadRecords(ListLocalInvUploadRecordsRequest request) {
        // Cmd：委托 facade
        return inventoryOperations.listLocalUploadRecords(request);
    }

    /**
     * 重试待上传记录。
     *
     * @param request 重试筛选条件
     * @return 重试汇总结果
     */
    public RetryPendingInvUploadsResult retryPendingUploads(RetryPendingInvUploadsRequest request) {
        // Cmd：委托 facade
        return inventoryOperations.retryPendingUploads(request);
    }

    /**
     * 【3508】查询平台库存。
     *
     * @param request 查询条件
     * @return 平台库存查询结果
     */
    public PlatformInvQueryResult queryPlatformInventory3508(QueryPlatformInvRequest request) {
        // Cmd：委托 facade
        return inventoryOperations.queryPlatformInventory3508(request);
    }

    /**
     * 【3509】查询平台库存变更。
     *
     * @param request 查询条件
     * @return 平台库存变更查询结果
     */
    public PlatformInvQueryResult queryPlatformInvChange3509(QueryPlatformInvRequest request) {
        // Cmd：委托 facade
        return inventoryOperations.queryPlatformInvChange3509(request);
    }

    /**
     * 【3510】查询平台采购。
     *
     * @param request 查询条件
     * @return 平台采购查询结果
     */
    public PlatformInvQueryResult queryPlatformPurchase3510(QueryPlatformInvRequest request) {
        // Cmd：委托 facade
        return inventoryOperations.queryPlatformPurchase3510(request);
    }

    /**
     * 【3511】查询平台销售。
     *
     * @param request 查询条件
     * @return 平台销售查询结果
     */
    public PlatformInvQueryResult queryPlatformSale3511(QueryPlatformInvRequest request) {
        // Cmd：委托 facade
        return inventoryOperations.queryPlatformSale3511(request);
    }

    /**
     * 【3512】查询平台入库追溯。
     *
     * @param request 查询条件
     * @return 平台入库追溯查询结果
     */
    public PlatformInvQueryResult queryPlatformInboundTrace3512(QueryPlatformInvRequest request) {
        // Cmd：委托 facade
        return inventoryOperations.queryPlatformInboundTrace3512(request);
    }

    /**
     * 【3513】查询平台销售追溯。
     *
     * @param request 查询条件
     * @return 平台销售追溯查询结果
     */
    public PlatformInvQueryResult queryPlatformSaleTrace3513(QueryPlatformInvRequest request) {
        // Cmd：委托 facade
        return inventoryOperations.queryPlatformSaleTrace3513(request);
    }

    /**
     * 本地与平台进销存数据比对。
     *
     * @param request 比对条件
     * @return 比对差异结果
     */
    public CompareLocalVsPlatformResult compareLocalVsPlatform(CompareLocalVsPlatformRequest request) {
        // Cmd：委托 facade
        return inventoryOperations.compareLocalVsPlatform(request);
    }
}
