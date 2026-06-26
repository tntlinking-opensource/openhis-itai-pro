/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.inventory;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.api.ChsInfNo;
import com.openhis.chs.api.InsuranceGatewayClient;
import com.openhis.chs.api.InvokeContext;
import com.openhis.chs.api.ResultBody;
import com.openhis.chs.application.config.ContractConfig;
import com.openhis.chs.application.config.ContractConfigLoader;
import com.openhis.chs.application.gateway.session.ChsSignCredentialResolver;
import com.openhis.chs.application.gateway.support.ChsCurrentOperatorResolver;
import com.openhis.chs.application.inventory.assembler.ChsInventoryPlatformAssembler;
import com.openhis.chs.domain.inventory.config.ChsInventoryProperties;
import com.openhis.chs.application.outpatient.support.ChsPlatformResultSupport;
import com.openhis.chs.domain.catalog.port.ChsContractSnapshot;
import com.openhis.chs.domain.catalog.port.ChsInsuranceContractPort;
import com.openhis.chs.domain.ChsErrorCodes;
import com.openhis.chs.domain.inventory.ChsInventoryConstants;
import com.openhis.chs.domain.inventory.dto.InvUploadLineCommand;
import com.openhis.chs.domain.inventory.dto.request.CompareLocalVsPlatformRequest;
import com.openhis.chs.domain.inventory.dto.request.ListLocalInvUploadRecordsRequest;
import com.openhis.chs.domain.inventory.dto.request.QueryPlatformInvRequest;
import com.openhis.chs.domain.inventory.dto.request.RetryPendingInvUploadsRequest;
import com.openhis.chs.domain.inventory.dto.request.UploadPurchase3503Request;
import com.openhis.chs.domain.inventory.dto.request.UploadPurchaseReturn3504Request;
import com.openhis.chs.domain.inventory.dto.request.UploadSale3505Request;
import com.openhis.chs.domain.inventory.dto.request.UploadSaleReturn3506Request;
import com.openhis.chs.domain.inventory.dto.InitializeInventoryPairCommand;
import com.openhis.chs.domain.inventory.dto.request.InitializeInventoryRequest;
import com.openhis.chs.domain.inventory.dto.request.UploadStocktake3501Request;
import com.openhis.chs.domain.inventory.dto.request.UploadInvChange3502Request;
import com.openhis.chs.domain.inventory.dto.request.DeleteInvData3507Request;
import com.openhis.chs.domain.inventory.dto.response.InvDelete3507Result;
import com.openhis.chs.domain.inventory.dto.response.InitializeInventoryLineResult;
import com.openhis.chs.domain.inventory.dto.response.InitializeInventoryResult;
import com.openhis.chs.domain.inventory.dto.response.InvUpload3501Result;
import com.openhis.chs.domain.inventory.dto.response.InvUpload3502Result;
import com.openhis.chs.domain.inventory.dto.response.InvUpload3503Result;
import com.openhis.chs.domain.inventory.dto.response.InvUpload3504Result;
import com.openhis.chs.domain.inventory.dto.response.InvUpload3505Result;
import com.openhis.chs.domain.inventory.dto.response.InvUpload3506Result;
import com.openhis.chs.domain.inventory.dto.response.CompareLocalVsPlatformResult;
import com.openhis.chs.domain.inventory.dto.response.InvCompareRowDto;
import com.openhis.chs.domain.inventory.dto.response.LocalInvUploadDto;
import com.openhis.chs.domain.inventory.dto.response.LocalInvUploadTraceCompareDto;
import com.openhis.chs.domain.inventory.dto.response.PlatformInvQueryResult;
import com.openhis.chs.domain.inventory.dto.response.RetryPendingInvUploadsResult;
import com.openhis.chs.domain.inventory.entity.YbInvUpload;
import com.openhis.chs.domain.inventory.entity.YbInvUploadTrace;
import com.openhis.chs.domain.inventory.facade.ChsInventoryOperations;
import com.openhis.chs.domain.inventory.repository.YbInvUploadLookup;
import com.openhis.chs.domain.inventory.repository.YbInvUploadRepository;
import com.openhis.chs.domain.inventory.repository.YbInvUploadTraceLookup;
import com.openhis.chs.domain.inventory.repository.InvUploadTraceCompareRow;
import com.openhis.chs.domain.inventory.repository.YbInvUploadTraceRepository;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 吉林进销存：3501–3507 上报、3508–3513 平台查询、本地与平台差异比对及失败重试。 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class ChsInventoryAppService implements ChsInventoryOperations {

    private final InsuranceGatewayClient gatewayClient;
    private final ChsInsuranceContractPort contractPort;
    private final ChsCurrentOperatorResolver currentOperatorResolver;
    private final ChsSignCredentialResolver signCredentialResolver;
    private final YbInvUploadRepository uploadRepository;
    private final YbInvUploadLookup uploadLookup;
    private final YbInvUploadTraceRepository uploadTraceRepository;
    private final YbInvUploadTraceLookup uploadTraceLookup;
    private final ChsInventoryProperties inventoryProperties;

    /**
     * 【3505】商品销售上报；逐行调平台并落库 yb_inv_upload 及追溯码。
     *
     * @param request 契约、来源单据与销售行（含追溯策略与追溯码）
     * @return 逐行 uploadId、uploadStatus 及平台 retRslt/msgRslt
     */
    @Override
    public List<InvUpload3505Result> uploadSale3505(UploadSale3505Request request) {
        // Pre：契约、租户、操作人
        ContractContext ctx = resolveContract(request.getContractBusNo(), request.getContractId());
        Long tenantId = TenantUtils.getCurrentTenantId();
        Long operatorId = currentOperatorResolver.requireOperatorId("请先登录");
        List<InvUpload3505Result> results = new ArrayList<>();
        // Gate：无行则直接返回空结果
        if (request.getLines() == null) {
            return results;
        }
        // Cmd：逐行 3505 上报（幂等、追溯校验、平台 invoke）
        for (InvUploadLineCommand line : request.getLines()) {
            results.add(processSaleLine3505(request, line, ctx, tenantId, operatorId));
        }
        // Asm：汇总逐行结果
        return results;
    }

    /**
     * 【3506】商品销售退货上报；逐行调平台并落库。
     *
     * @param request 契约、来源单据与退货行
     * @return 逐行 uploadId、uploadStatus 及平台 retRslt/msgRslt
     */
    @Override
    public List<InvUpload3506Result> uploadSaleReturn3506(UploadSaleReturn3506Request request) {
        // Pre：契约、租户、操作人
        ContractContext ctx = resolveContract(request.getContractBusNo(), request.getContractId());
        Long tenantId = TenantUtils.getCurrentTenantId();
        Long operatorId = currentOperatorResolver.requireOperatorId("请先登录");
        List<InvUpload3506Result> results = new ArrayList<>();
        // Gate：无行则直接返回空结果
        if (request.getLines() == null) {
            return results;
        }
        // Cmd：逐行 3506 上报
        for (InvUploadLineCommand line : request.getLines()) {
            results.add(processReturnLine3506(request, line, ctx, tenantId, operatorId));
        }
        // Asm：汇总逐行结果
        return results;
    }

    /**
     * 【3503】商品采购入库上报；逐行调平台并落库。
     *
     * @param request 契约、来源单据与采购行
     * @return 逐行 uploadId、uploadStatus
     */
    @Override
    public List<InvUpload3503Result> uploadPurchase3503(UploadPurchase3503Request request) {
        // Pre：契约、租户、操作人
        ContractContext ctx = resolveContract(request.getContractBusNo(), request.getContractId());
        Long tenantId = TenantUtils.getCurrentTenantId();
        Long operatorId = currentOperatorResolver.requireOperatorId("请先登录");
        List<InvUpload3503Result> results = new ArrayList<>();
        // Gate：无行则直接返回空结果
        if (request.getLines() == null) {
            return results;
        }
        // Cmd：逐行 3503 上报
        for (InvUploadLineCommand line : request.getLines()) {
            results.add(processPurchaseLine3503(request, line, ctx, tenantId, operatorId));
        }
        // Asm：汇总逐行结果
        return results;
    }

    /**
     * 【3504】商品采购退货上报；逐行调平台并落库。
     *
     * @param request 契约、来源单据与采购退货行
     * @return 逐行 uploadId、uploadStatus
     */
    @Override
    public List<InvUpload3504Result> uploadPurchaseReturn3504(UploadPurchaseReturn3504Request request) {
        // Pre：契约、租户、操作人
        ContractContext ctx = resolveContract(request.getContractBusNo(), request.getContractId());
        Long tenantId = TenantUtils.getCurrentTenantId();
        Long operatorId = currentOperatorResolver.requireOperatorId("请先登录");
        List<InvUpload3504Result> results = new ArrayList<>();
        // Gate：无行则直接返回空结果
        if (request.getLines() == null) {
            return results;
        }
        // Cmd：逐行 3504 上报
        for (InvUploadLineCommand line : request.getLines()) {
            results.add(processPurchaseReturnLine3504(request, line, ctx, tenantId, operatorId));
        }
        // Asm：汇总逐行结果
        return results;
    }

    /**
     * 【3501】商品盘存上报；逐行调平台并落库。
     *
     * @param request 契约、来源库位与盘存行
     * @return 逐行 uploadId、uploadStatus
     */
    @Override
    public List<InvUpload3501Result> uploadStocktake3501(UploadStocktake3501Request request) {
        // Pre：契约、租户、操作人
        ContractContext ctx = resolveContract(request.getContractBusNo(), request.getContractId());
        Long tenantId = TenantUtils.getCurrentTenantId();
        Long operatorId = currentOperatorResolver.requireOperatorId("请先登录");
        List<InvUpload3501Result> results = new ArrayList<>();
        // Gate：无行则直接返回空结果
        if (request.getLines() == null) {
            return results;
        }
        // Cmd：逐行 3501 盘存上报（sourceType=STOCKTAKE）
        for (InvUploadLineCommand line : request.getLines()) {
            results.add(processStocktakeLine3501(
                request, line, ctx, tenantId, operatorId, ChsInventoryConstants.SOURCE_TYPE_STOCKTAKE));
        }
        // Asm：汇总逐行结果
        return results;
    }

    /**
     * 【3502】商品库存变更上报；逐行调平台、落库并保存追溯码。
     *
     * @param request 契约、来源单据、sourceType（缺省 MANUAL）与变更行
     * @return 逐行 uploadId、uploadStatus、invChgType
     */
    @Override
    public List<InvUpload3502Result> uploadInvChange3502(UploadInvChange3502Request request) {
        // Pre：契约、租户、操作人；sourceType 缺省回落 MANUAL
        ContractContext ctx = resolveContract(request.getContractBusNo(), request.getContractId());
        Long tenantId = TenantUtils.getCurrentTenantId();
        Long operatorId = currentOperatorResolver.requireOperatorId("请先登录");
        String sourceType;
        if (StringUtils.isNotBlank(request.getSourceType())) {
            sourceType = request.getSourceType().trim();
        } else {
            sourceType = ChsInventoryConstants.SOURCE_TYPE_MANUAL;
        }
        List<InvUpload3502Result> results = new ArrayList<>();
        // Gate：无行则直接返回空结果
        if (request.getLines() == null) {
            return results;
        }
        // Cmd：逐行 3502 库存变更上报
        for (InvUploadLineCommand line : request.getLines()) {
            results.add(processInvChangeLine3502(request, line, ctx, tenantId, operatorId, sourceType));
        }
        // Asm：汇总逐行结果
        return results;
    }

    /**
     * 批初始化进销存：每对先 3501 盘存再 3502 其他入库；3501 失败则跳过同对 3502。
     *
     * @param request 库位 locationId 与 3501/3502 配对行
     * @return 尝试/成功/失败/无医保码跳过计数及逐对明细
     * @throws BusinessException locationId 为空或 request 为空时
     */
    @Override
    public InitializeInventoryResult initializeInventory(InitializeInventoryRequest request) {
        // Gate：参数与 locationId 必填
        assertInitializeInventoryRequest(request);
        // Pre：契约、租户、操作人
        ContractContext ctx = resolveContract(request.getContractBusNo(), request.getContractId());
        Long tenantId = TenantUtils.getCurrentTenantId();
        Long operatorId = currentOperatorResolver.requireOperatorId("请先登录");
        InitializeInventoryResult result = new InitializeInventoryResult();
        // Gate：无配对行则直接返回
        if (request.getPairs() == null || request.getPairs().isEmpty()) {
            return result;
        }
        UploadStocktake3501Request batch3501 = new UploadStocktake3501Request();
        batch3501.setContractBusNo(ctx.busNo());
        batch3501.setContractId(ctx.contract().id());
        batch3501.setSourceId(request.getLocationId());
        batch3501.setRemark(request.getRemark());

        // Cmd：逐对 3501 → 3502 串行上报
        for (InitializeInventoryPairCommand pair : request.getPairs()) {
            // 业务：配对不完整则跳过
            if (pair == null || pair.getStocktake3501() == null || pair.getInvChange3502() == null) {
                continue;
            }
            result.setAttemptedPairs(result.getAttemptedPairs() + 1);
            InitializeInventoryLineResult lineResult = new InitializeInventoryLineResult();
            InvUploadLineCommand line3501 = pair.getStocktake3501();
            lineResult.setInventoryItemId(line3501.getSourceLineId());
            lineResult.setFixmedinsBchno(line3501.getFixmedinsBchno());
            lineResult.setMedListCodg(line3501.getMedListCodg());

            InvUpload3501Result r3501 = processStocktakeLine3501(
                batch3501, line3501, ctx, tenantId, operatorId, ChsInventoryConstants.SOURCE_TYPE_INIT);
            lineResult.setUpload3501Id(r3501.getUploadId());
            lineResult.setUpload3501Status(r3501.getUploadStatus());
            // 业务：无医保码跳过，不计入失败
            if (ChsInventoryConstants.UPLOAD_STATUS_SKIPPED_NO_YB_NO.equals(r3501.getUploadStatus())) {
                result.setSkippedNoYbNo(result.getSkippedNoYbNo() + 1);
                lineResult.setPlatformMsg(r3501.getPlatformMsg());
                result.getLines().add(lineResult);
                continue;
            }
            // 业务：3501 失败则本对终止，不继续 3502
            if (!ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(r3501.getUploadStatus())) {
                result.setFailedPairs(result.getFailedPairs() + 1);
                lineResult.setPlatformMsg(r3501.getPlatformMsg());
                result.getLines().add(lineResult);
                continue;
            }

            UploadInvChange3502Request batch3502 = new UploadInvChange3502Request();
            batch3502.setContractBusNo(ctx.busNo());
            batch3502.setContractId(ctx.contract().id());
            batch3502.setSourceId(request.getLocationId());
            batch3502.setSourceType(ChsInventoryConstants.SOURCE_TYPE_INIT);
            InvUpload3502Result r3502 = processInvChangeLine3502(
                batch3502, pair.getInvChange3502(), ctx, tenantId, operatorId, ChsInventoryConstants.SOURCE_TYPE_INIT);
            lineResult.setUpload3502Id(r3502.getUploadId());
            lineResult.setUpload3502Status(r3502.getUploadStatus());
            if (ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(r3502.getUploadStatus())) {
                result.setSucceededPairs(result.getSucceededPairs() + 1);
            } else {
                // 业务：3502 失败计入 failedPairs
                result.setFailedPairs(result.getFailedPairs() + 1);
                lineResult.setPlatformMsg(r3502.getPlatformMsg());
            }
            result.getLines().add(lineResult);
        }
        // Asm：返回初始化汇总
        return result;
    }

    /**
     * 【3507】商品信息删除；调平台成功后本地同批次成功记录标记 DELETED。
     *
     * @param request 契约、fixmedins_bchno、inv_data_type 及来源单据
     * @return uploadId、uploadStatus；失败时含 platformMsg
     * @throws BusinessException 必填参数缺失时
     */
    @Override
    public InvDelete3507Result deleteInvData3507(DeleteInvData3507Request request) {
        // Gate：fixmedins_bchno、inv_data_type 必填
        assertDeleteInvDataRequest(request);
        // Pre：契约、租户、操作人
        ContractContext ctx = resolveContract(request.getContractBusNo(), request.getContractId());
        Long tenantId = TenantUtils.getCurrentTenantId();
        Long operatorId = currentOperatorResolver.requireOperatorId("请先登录");
        InvDelete3507Result result = new InvDelete3507Result();
        result.setFixmedinsBchno(request.getFixmedinsBchno().trim());
        result.setInvDataType(request.getInvDataType().trim());

        InvUploadLineCommand line = new InvUploadLineCommand();
        line.setFixmedinsBchno(request.getFixmedinsBchno().trim());
        line.setSourceLineId(0L);

        JSONObject input = ChsInventoryPlatformAssembler.assemble3507(
            request.getFixmedinsBchno().trim(), request.getInvDataType().trim());
        String requestJson = JSON.toJSONString(input);
        // Cmd：调平台 3507 删除
        try {
            ResultBody body = gatewayClient.invoke(
                ctx.busNo(), ChsInfNo.INVENTORY_DELETE, input, buildInvokeContext());
            ChsPlatformResultSupport.requireSuccess(body, ChsInfNo.INVENTORY_DELETE);
            YbInvUpload saved = upsertUploadRecord(
                null, ctx, ChsInfNo.INVENTORY_DELETE, line, ChsInventoryConstants.SOURCE_TYPE_MANUAL,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_SUCCESS,
                ChsInventoryConstants.PLATFORM_RET_RSLT_SUCCESS, null, requestJson, JSON.toJSONString(body),
                operatorId, tenantId, false);
            // 业务：平台删除成功后，本地同批次 SUCCESS 记录改 DELETED
            markDeletedByBatchNo(tenantId, request.getFixmedinsBchno().trim(), request.getInvDataType().trim());
            // Asm：返回成功结果
            return result.setUploadId(saved.getId()).setUploadStatus(saved.getUploadStatus());
        } catch (RuntimeException ex) {
            YbInvUpload failed = upsertUploadRecord(
                null, ctx, ChsInfNo.INVENTORY_DELETE, line, ChsInventoryConstants.SOURCE_TYPE_MANUAL,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_FAILED,
                ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED, ex.getMessage(), requestJson, null,
                operatorId, tenantId, true);
            // Asm：返回失败结果及平台消息
            return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
                .setPlatformMsg(ex.getMessage());
        }
    }

    /**
     * 【3508】平台库存信息查询。
     *
     * @param request 契约、日期范围及 medins_list_codg/fixmedins_bchno 等筛选
     * @return 平台返回行列表
     */
    @Override
    public PlatformInvQueryResult queryPlatformInventory3508(QueryPlatformInvRequest request) {
        // Cmd：委托通用平台查询（3508）
        return queryPlatformInv(ChsInfNo.INVENTORY_STOCK_QUERY, request);
    }

    /**
     * 【3509】平台库存变更信息查询。
     *
     * @param request 契约、日期范围及筛选条件
     * @return 平台返回行列表
     */
    @Override
    public PlatformInvQueryResult queryPlatformInvChange3509(QueryPlatformInvRequest request) {
        // Cmd：委托通用平台查询（3509）
        return queryPlatformInv(ChsInfNo.INVENTORY_CHANGE_QUERY, request);
    }

    /**
     * 【3510】平台商品采购信息查询。
     *
     * @param request 契约、日期范围及筛选条件
     * @return 平台返回行列表
     */
    @Override
    public PlatformInvQueryResult queryPlatformPurchase3510(QueryPlatformInvRequest request) {
        // Cmd：委托通用平台查询（3510）
        return queryPlatformInv(ChsInfNo.INVENTORY_PURCHASE_QUERY, request);
    }

    /**
     * 【3511】平台商品销售信息查询。
     *
     * @param request 契约、日期范围及筛选条件
     * @return 平台返回行列表
     */
    @Override
    public PlatformInvQueryResult queryPlatformSale3511(QueryPlatformInvRequest request) {
        // Cmd：委托通用平台查询（3511）
        return queryPlatformInv(ChsInfNo.INVENTORY_SALE_QUERY, request);
    }

    /**
     * 【3512】平台入库追溯信息查询。
     *
     * @param request 契约及 med_list_codg、drug_trac_codg 等至少一项主键
     * @return 平台返回行列表
     */
    @Override
    public PlatformInvQueryResult queryPlatformInboundTrace3512(QueryPlatformInvRequest request) {
        // Cmd：委托通用平台查询（3512）
        return queryPlatformInv(ChsInfNo.INVENTORY_INBOUND_TRACE_QUERY, request);
    }

    /**
     * 【3513】平台销售追溯信息查询。
     *
     * @param request 契约及 med_list_codg、drug_trac_codg 等至少一项主键
     * @return 平台返回行列表
     */
    @Override
    public PlatformInvQueryResult queryPlatformSaleTrace3513(QueryPlatformInvRequest request) {
        // Cmd：委托通用平台查询（3513）
        return queryPlatformInv(ChsInfNo.INVENTORY_SALE_TRACE_QUERY, request);
    }

    /**
     * 本地 yb_inv_upload 与平台 3508–3513 查询结果差异比对。
     *
     * @param request compareQueryInfno（3508–3513）及可选筛选（契约、批次、日期等）
     * @return 匹配/仅本地/仅平台计数及逐行 diffType
     * @throws BusinessException compareQueryInfno 非法或为空时
     */
    @Override
    public CompareLocalVsPlatformResult compareLocalVsPlatform(CompareLocalVsPlatformRequest request) {
        // Pre：归一化请求并校验 compareQueryInfno
        CompareLocalVsPlatformRequest safeRequest = normalizeCompareRequest(request);
        String compareQueryInfno = requireCompareQueryInfno(safeRequest);
        // 业务：3512/3513 走追溯码维度比对
        if (ChsInfNo.INVENTORY_INBOUND_TRACE_QUERY.equals(compareQueryInfno)
            || ChsInfNo.INVENTORY_SALE_TRACE_QUERY.equals(compareQueryInfno)) {
            return compareLocalVsPlatformByTrace(compareQueryInfno, safeRequest);
        }
        // Pre：映射本地 infno、拉取平台与本地数据
        String localUploadInfno = mapPlatformQueryToLocalUploadInfno(compareQueryInfno);
        PlatformInvQueryResult platformResult = queryPlatformByCompareInfno(compareQueryInfno, safeRequest);
        List<Map<String, Object>> platformRows;
        if (platformResult.getRows() != null) {
            platformRows = platformResult.getRows();
        } else {
            platformRows = List.of();
        }
        List<LocalInvUploadDto> localRows = filterLocalRowsForCompare(
            listLocalUploadRecords(new ListLocalInvUploadRecordsRequest().setInfno(localUploadInfno)),
            safeRequest);
        Map<String, LocalInvUploadDto> localByKey = indexLocalRowsByCompareKey(localRows);
        Map<String, Map<String, Object>> platformByKey = indexPlatformRowsByCompareKey(platformRows);
        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(localByKey.keySet());
        allKeys.addAll(platformByKey.keySet());
        // Cmd：按比对键合并两侧行并统计 diffType
        CompareLocalVsPlatformResult result = new CompareLocalVsPlatformResult();
        result.setCompareQueryInfno(compareQueryInfno);
        result.setLocalUploadInfno(localUploadInfno);
        result.setPlatformRowCount(platformRows.size());
        result.setLocalRowCount(localRows.size());
        for (String key : allKeys) {
            LocalInvUploadDto localRow = localByKey.get(key);
            Map<String, Object> platformRow = platformByKey.get(key);
            InvCompareRowDto rowDto = buildCompareRow(key, localRow, platformRow);
            result.getRows().add(rowDto);
            if (ChsInventoryConstants.COMPARE_DIFF_MATCH.equals(rowDto.getDiffType())) {
                result.setMatchCount(result.getMatchCount() + 1);
            } else if (ChsInventoryConstants.COMPARE_DIFF_LOCAL_ONLY.equals(rowDto.getDiffType())) {
                result.setLocalOnlyCount(result.getLocalOnlyCount() + 1);
            } else if (ChsInventoryConstants.COMPARE_DIFF_PLATFORM_ONLY.equals(rowDto.getDiffType())) {
                result.setPlatformOnlyCount(result.getPlatformOnlyCount() + 1);
            }
        }
        // Asm：返回比对汇总
        return result;
    }

    private CompareLocalVsPlatformResult compareLocalVsPlatformByTrace(
        String compareQueryInfno,
        CompareLocalVsPlatformRequest request
    ) {
        List<String> localInfnoList = resolveTraceLocalInfnoList(compareQueryInfno);
        PlatformInvQueryResult platformResult = queryPlatformByCompareInfno(compareQueryInfno, request);
        List<Map<String, Object>> platformRows;
        if (platformResult.getRows() != null) {
            platformRows = platformResult.getRows();
        } else {
            platformRows = List.of();
        }
        List<LocalInvUploadTraceCompareDto> localRows = filterLocalTraceRowsForCompare(
            listLocalTraceRowsForCompare(localInfnoList),
            request);
        Map<String, LocalInvUploadTraceCompareDto> localByKey = indexLocalTraceRowsByCompareKey(localRows);
        Map<String, Map<String, Object>> platformByKey = indexPlatformTraceRowsByCompareKey(platformRows);
        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(localByKey.keySet());
        allKeys.addAll(platformByKey.keySet());
        CompareLocalVsPlatformResult result = new CompareLocalVsPlatformResult();
        result.setCompareQueryInfno(compareQueryInfno);
        result.setLocalUploadInfno(mapPlatformQueryToLocalUploadInfno(compareQueryInfno));
        result.setPlatformRowCount(platformRows.size());
        result.setLocalRowCount(localRows.size());
        for (String key : allKeys) {
            LocalInvUploadTraceCompareDto localRow = localByKey.get(key);
            Map<String, Object> platformRow = platformByKey.get(key);
            InvCompareRowDto rowDto = buildTraceCompareRow(key, localRow, platformRow);
            result.getRows().add(rowDto);
            if (ChsInventoryConstants.COMPARE_DIFF_MATCH.equals(rowDto.getDiffType())) {
                result.setMatchCount(result.getMatchCount() + 1);
            } else if (ChsInventoryConstants.COMPARE_DIFF_LOCAL_ONLY.equals(rowDto.getDiffType())) {
                result.setLocalOnlyCount(result.getLocalOnlyCount() + 1);
            } else if (ChsInventoryConstants.COMPARE_DIFF_PLATFORM_ONLY.equals(rowDto.getDiffType())) {
                result.setPlatformOnlyCount(result.getPlatformOnlyCount() + 1);
            }
        }
        return result;
    }

    private List<LocalInvUploadTraceCompareDto> listLocalTraceRowsForCompare(List<String> infnoList) {
        if (infnoList == null || infnoList.isEmpty()) {
            return List.of();
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        PageRequest page = PageRequest.of(0, ChsInventoryConstants.LOCAL_UPLOAD_LIST_DEFAULT_LIMIT);
        List<InvUploadTraceCompareRow> pairs = uploadTraceLookup.findTraceRowsForCompare(tenantId, infnoList, page);
        List<LocalInvUploadTraceCompareDto> rows = new ArrayList<>(pairs.size());
        for (InvUploadTraceCompareRow pair : pairs) {
            if (pair == null) {
                continue;
            }
            LocalInvUploadTraceCompareDto dto = new LocalInvUploadTraceCompareDto();
            dto.setTraceId(pair.getTraceId());
            dto.setDrugTracCodg(pair.getDrugTracCodg());
            dto.setUploadId(pair.getUploadId());
            dto.setContractBusNo(pair.getContractBusNo());
            dto.setInfno(pair.getInfno());
            dto.setFixmedinsBchno(pair.getFixmedinsBchno());
            dto.setMedListCodg(pair.getMedListCodg());
            dto.setUploadStatus(pair.getUploadStatus());
            if (pair.getCreatedAt() != null) {
                dto.setCreatedAt(pair.getCreatedAt().toString());
            }
            rows.add(dto);
        }
        return rows;
    }

    private static List<String> resolveTraceLocalInfnoList(String compareQueryInfno) {
        if (ChsInfNo.INVENTORY_INBOUND_TRACE_QUERY.equals(compareQueryInfno)) {
            return List.of(ChsInfNo.INVENTORY_CHANGE, ChsInfNo.INVENTORY_PURCHASE);
        }
        if (ChsInfNo.INVENTORY_SALE_TRACE_QUERY.equals(compareQueryInfno)) {
            return List.of(ChsInfNo.INVENTORY_SALE, ChsInfNo.INVENTORY_SALE_RETURN);
        }
        return List.of();
    }

    private List<LocalInvUploadTraceCompareDto> filterLocalTraceRowsForCompare(
        List<LocalInvUploadTraceCompareDto> rows,
        CompareLocalVsPlatformRequest request
    ) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        CompareFilterCriteria criteria = extractCompareFilterCriteria(normalizeCompareRequest(request));
        List<LocalInvUploadTraceCompareDto> filtered = new ArrayList<>();
        for (LocalInvUploadTraceCompareDto row : rows) {
            if (row == null) {
                continue;
            }
            if (criteria.contractBusNo() != null && !criteria.contractBusNo().equals(trimToNull(row.getContractBusNo()))) {
                continue;
            }
            if (criteria.fixmedinsBchno() != null && !criteria.fixmedinsBchno().equals(trimToNull(row.getFixmedinsBchno()))) {
                continue;
            }
            if (criteria.medListCodg() != null && !criteria.medListCodg().equals(trimToNull(row.getMedListCodg()))) {
                continue;
            }
            if (criteria.drugTracCodg() != null && !criteria.drugTracCodg().equals(trimToNull(row.getDrugTracCodg()))) {
                continue;
            }
            if (!withinCompareDateRange(row.getCreatedAt(), criteria.beginDate(), criteria.endDate())) {
                continue;
            }
            filtered.add(row);
        }
        return filtered;
    }

    private static Map<String, LocalInvUploadTraceCompareDto> indexLocalTraceRowsByCompareKey(
        List<LocalInvUploadTraceCompareDto> rows
    ) {
        Map<String, LocalInvUploadTraceCompareDto> indexed = new LinkedHashMap<>();
        for (LocalInvUploadTraceCompareDto row : rows) {
            String key = buildTraceCompareKey(row.getDrugTracCodg());
            if (key == null) {
                continue;
            }
            indexed.putIfAbsent(key, row);
        }
        return indexed;
    }

    private static Map<String, Map<String, Object>> indexPlatformTraceRowsByCompareKey(List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> indexed = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            String key = buildTraceCompareKey(
                readCompareRowField(row, "drugTracCodg", "drug_trac_codg"));
            if (key == null) {
                continue;
            }
            indexed.putIfAbsent(key, row);
        }
        return indexed;
    }

    private static InvCompareRowDto buildTraceCompareRow(
        String key,
        LocalInvUploadTraceCompareDto localRow,
        Map<String, Object> platformRow
    ) {
        InvCompareRowDto rowDto = new InvCompareRowDto();
        rowDto.setCompareKey(key);
        rowDto.setDrugTracCodg(key);
        if (localRow != null && platformRow != null) {
            rowDto.setDiffType(ChsInventoryConstants.COMPARE_DIFF_MATCH);
            rowDto.setLocalUploadId(localRow.getUploadId());
            rowDto.setLocalUploadStatus(localRow.getUploadStatus());
            rowDto.setLocalCreatedAt(localRow.getCreatedAt());
            rowDto.setFixmedinsBchno(localRow.getFixmedinsBchno());
            rowDto.setMedListCodg(localRow.getMedListCodg());
            rowDto.setPlatformRow(new HashMap<>(platformRow));
        } else if (localRow != null) {
            rowDto.setDiffType(ChsInventoryConstants.COMPARE_DIFF_LOCAL_ONLY);
            rowDto.setLocalUploadId(localRow.getUploadId());
            rowDto.setLocalUploadStatus(localRow.getUploadStatus());
            rowDto.setLocalCreatedAt(localRow.getCreatedAt());
            rowDto.setFixmedinsBchno(localRow.getFixmedinsBchno());
            rowDto.setMedListCodg(localRow.getMedListCodg());
        } else {
            rowDto.setDiffType(ChsInventoryConstants.COMPARE_DIFF_PLATFORM_ONLY);
            rowDto.setPlatformRow(new HashMap<>(platformRow));
            rowDto.setFixmedinsBchno(readCompareRowField(platformRow, "fixmedinsBchno", "fixmedins_bchno"));
            rowDto.setMedListCodg(readCompareRowField(platformRow, "medListCodg", "med_list_codg"));
        }
        return rowDto;
    }

    private static String buildTraceCompareKey(String drugTracCodg) {
        return trimToNull(drugTracCodg);
    }

    /**
     * 查询本地 yb_inv_upload 上报记录（分页或按来源单据）。
     *
     * @param request 可选 infno、sourceType+sourceId 筛选；null 时按租户默认分页
     * @return 本地上报 DTO 列表
     */
    @Override
    public List<LocalInvUploadDto> listLocalUploadRecords(ListLocalInvUploadRecordsRequest request) {
        // Pre：租户与归一化请求
        Long tenantId = TenantUtils.getCurrentTenantId();
        ListLocalInvUploadRecordsRequest safeRequest = normalizeListLocalUploadRecordsRequest(request);
        List<YbInvUpload> rows;
        // Cmd：按 sourceType+sourceId 或 infno 或默认分页查库
        if (StringUtils.isNotBlank(safeRequest.getSourceType()) && safeRequest.getSourceId() != null) {
            rows = uploadRepository.findAllByTenantIdAndSourceTypeAndSourceIdOrderByIdAsc(
                tenantId, safeRequest.getSourceType().trim(), safeRequest.getSourceId());
        } else {
            PageRequest page = PageRequest.of(0, ChsInventoryConstants.LOCAL_UPLOAD_LIST_DEFAULT_LIMIT);
            if (StringUtils.isNotBlank(safeRequest.getInfno())) {
                rows = uploadRepository.findAllByTenantIdAndInfnoOrderByIdDesc(
                    tenantId, safeRequest.getInfno().trim(), page);
            } else {
                rows = uploadRepository.findAllByTenantIdOrderByIdDesc(tenantId, page);
            }
        }
        List<LocalInvUploadDto> dtos = new ArrayList<>(rows.size());
        for (YbInvUpload row : rows) {
            dtos.add(toLocalDto(row));
        }
        // Asm：实体转 DTO
        return dtos;
    }

    /**
     * 重试失败/待上报的 yb_inv_upload 记录（Worker 或手动触发）。
     *
     * @param request 可选 batchSize；null 时使用配置 retryWorkerBatchSize
     * @return attempted/succeeded/failed/skipped 计数
     */
    @Override
    public RetryPendingInvUploadsResult retryPendingUploads(RetryPendingInvUploadsRequest request) {
        RetryPendingInvUploadsResult result = new RetryPendingInvUploadsResult();
        // Gate：上报开关关闭则直接返回
        if (!inventoryProperties.isUploadEnabled()) {
            return result;
        }
        // Pre：租户、批次大小
        Long tenantId = TenantUtils.getCurrentTenantId();
        int batchSize = inventoryProperties.getRetryWorkerBatchSize();
        if (request != null && request.getBatchSize() != null && request.getBatchSize() > 0) {
            batchSize = request.getBatchSize();
        }
        List<YbInvUpload> rows = uploadLookup.findRetryable(
            tenantId,
            ChsInventoryConstants.RETRYABLE_UPLOAD_STATUSES,
            inventoryProperties.getMaxRetry(),
            LocalDateTime.now(),
            PageRequest.of(0, batchSize));
        // Cmd：逐条重放 requestJson 调平台
        for (YbInvUpload row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            // 业务：无 requestJson 无法重试
            if (StringUtils.isBlank(row.getRequestJson())) {
                result.setSkipped(result.getSkipped() + 1);
                continue;
            }
            result.setAttempted(result.getAttempted() + 1);
            row.setRetryCount(safeRetryCount(row.getRetryCount()) + 1);
            boolean success = retryStoredUpload(row);
            if (success) {
                result.setSucceeded(result.getSucceeded() + 1);
            } else {
                result.setFailed(result.getFailed() + 1);
            }
        }
        // Asm：返回重试汇总
        return result;
    }

    private InvUpload3505Result processSaleLine3505(
        UploadSale3505Request request,
        InvUploadLineCommand line,
        ContractContext ctx,
        Long tenantId,
        Long operatorId
    ) {
        InvUpload3505Result result = new InvUpload3505Result();
        result.setSourceLineId(line.getSourceLineId());
        result.setFixmedinsBchno(line.getFixmedinsBchno());

        if (inventoryProperties.isSkipWithoutYbNo()
            && (ChsInventoryConstants.TRACE_POLICY_NO_YB_NO.equals(line.getTracePolicy())
                || StringUtils.isBlank(line.getMedListCodg()))) {
            YbInvUpload skipped = upsertUploadRecord(
                null, ctx, ChsInfNo.INVENTORY_SALE, line, ChsInventoryConstants.SOURCE_TYPE_DISPENSE, request.getSourceId(),
                ChsInventoryConstants.UPLOAD_STATUS_SKIPPED_NO_YB_NO,
                null, null, null, null, operatorId, tenantId, false);
            return result.setUploadId(skipped.getId()).setUploadStatus(skipped.getUploadStatus());
        }

        Optional<YbInvUpload> existing = uploadRepository.findFirstByTenantIdAndInfnoAndFixmedinsBchnoAndSourceLineId(
            tenantId, ChsInfNo.INVENTORY_SALE, line.getFixmedinsBchno(), line.getSourceLineId());
        if (existing.isPresent()
            && ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(existing.get().getUploadStatus())) {
            YbInvUpload row = existing.get();
            return result.setUploadId(row.getId()).setUploadStatus(row.getUploadStatus())
                .setPlatformRetRslt(row.getPlatformRetRslt()).setPlatformMsg(row.getPlatformMsg());
        }

        if (inventoryProperties.isEnforceTraceCountEqualsQty()
            && ChsInventoryConstants.TRACE_POLICY_TRACE_REQUIRED.equals(line.getTracePolicy())) {
            if (!traceCountMatchesQuantity(line)) {
                YbInvUpload failed = upsertUploadRecord(
                    existing.orElse(null), ctx, ChsInfNo.INVENTORY_SALE, line, ChsInventoryConstants.SOURCE_TYPE_DISPENSE, request.getSourceId(),
                    ChsInventoryConstants.UPLOAD_STATUS_FAILED,
                    ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED, "追溯码条数与销售数量不一致", null, null, operatorId, tenantId, true);
                return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
                    .setPlatformRetRslt(ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED).setPlatformMsg(failed.getPlatformMsg());
            }
        }

        JSONObject input = ChsInventoryPlatformAssembler.assemble3505(line);
        PlatformUploadOutcome outcome = invokeSalePlatform(ctx.busNo(), ChsInfNo.INVENTORY_SALE, input);
        if (outcome.invokeOk()) {
            YbInvUpload saved = upsertUploadRecord(
                existing.orElse(null), ctx, ChsInfNo.INVENTORY_SALE, line, ChsInventoryConstants.SOURCE_TYPE_DISPENSE, request.getSourceId(),
                outcome.uploadStatus(), outcome.retRslt(), outcome.msgRslt(), outcome.requestJson(), outcome.responseJson(),
                operatorId, tenantId, ChsInventoryConstants.UPLOAD_STATUS_FAILED.equals(outcome.uploadStatus()));
            saveTraceRows(saved, line, tenantId);
            return result.setUploadId(saved.getId()).setUploadStatus(saved.getUploadStatus())
                .setPlatformRetRslt(outcome.retRslt()).setPlatformMsg(outcome.msgRslt());
        }
        YbInvUpload failed = upsertUploadRecord(
            existing.orElse(null), ctx, ChsInfNo.INVENTORY_SALE, line, ChsInventoryConstants.SOURCE_TYPE_DISPENSE, request.getSourceId(),
            ChsInventoryConstants.UPLOAD_STATUS_FAILED,
            outcome.retRslt(), outcome.errorMessage(), outcome.requestJson(), null, operatorId, tenantId, true);
        return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
            .setPlatformRetRslt(outcome.retRslt()).setPlatformMsg(outcome.errorMessage());
    }

    private InvUpload3506Result processReturnLine3506(
        UploadSaleReturn3506Request request,
        InvUploadLineCommand line,
        ContractContext ctx,
        Long tenantId,
        Long operatorId
    ) {
        InvUpload3506Result result = new InvUpload3506Result();
        result.setSourceLineId(line.getSourceLineId());
        result.setFixmedinsBchno(line.getFixmedinsBchno());

        if (inventoryProperties.isSkipWithoutYbNo()
            && (ChsInventoryConstants.TRACE_POLICY_NO_YB_NO.equals(line.getTracePolicy())
                || StringUtils.isBlank(line.getMedListCodg()))) {
            YbInvUpload skipped = upsertUploadRecord(
                null, ctx, ChsInfNo.INVENTORY_SALE_RETURN, line, ChsInventoryConstants.SOURCE_TYPE_DISPENSE, request.getSourceId(),
                ChsInventoryConstants.UPLOAD_STATUS_SKIPPED_NO_YB_NO,
                null, null, null, null, operatorId, tenantId, false);
            return result.setUploadId(skipped.getId()).setUploadStatus(skipped.getUploadStatus());
        }

        Optional<YbInvUpload> existing = uploadRepository.findFirstByTenantIdAndInfnoAndFixmedinsBchnoAndSourceLineId(
            tenantId, ChsInfNo.INVENTORY_SALE_RETURN, line.getFixmedinsBchno(), line.getSourceLineId());
        if (existing.isPresent()
            && ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(existing.get().getUploadStatus())) {
            YbInvUpload row = existing.get();
            return result.setUploadId(row.getId()).setUploadStatus(row.getUploadStatus())
                .setPlatformRetRslt(row.getPlatformRetRslt()).setPlatformMsg(row.getPlatformMsg());
        }

        JSONObject input = ChsInventoryPlatformAssembler.assemble3506(line);
        PlatformUploadOutcome outcome = invokeSalePlatform(ctx.busNo(), ChsInfNo.INVENTORY_SALE_RETURN, input);
        if (outcome.invokeOk()) {
            YbInvUpload saved = upsertUploadRecord(
                existing.orElse(null), ctx, ChsInfNo.INVENTORY_SALE_RETURN, line, ChsInventoryConstants.SOURCE_TYPE_DISPENSE, request.getSourceId(),
                outcome.uploadStatus(), outcome.retRslt(), outcome.msgRslt(), outcome.requestJson(), outcome.responseJson(),
                operatorId, tenantId, ChsInventoryConstants.UPLOAD_STATUS_FAILED.equals(outcome.uploadStatus()));
            saveTraceRows(saved, line, tenantId);
            return result.setUploadId(saved.getId()).setUploadStatus(saved.getUploadStatus())
                .setPlatformRetRslt(outcome.retRslt()).setPlatformMsg(outcome.msgRslt());
        }
        YbInvUpload failed = upsertUploadRecord(
            existing.orElse(null), ctx, ChsInfNo.INVENTORY_SALE_RETURN, line, ChsInventoryConstants.SOURCE_TYPE_DISPENSE, request.getSourceId(),
            ChsInventoryConstants.UPLOAD_STATUS_FAILED,
            outcome.retRslt(), outcome.errorMessage(), outcome.requestJson(), null, operatorId, tenantId, true);
        return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
            .setPlatformRetRslt(outcome.retRslt()).setPlatformMsg(outcome.errorMessage());
    }

    private InvUpload3503Result processPurchaseLine3503(
        UploadPurchase3503Request request,
        InvUploadLineCommand line,
        ContractContext ctx,
        Long tenantId,
        Long operatorId
    ) {
        InvUpload3503Result result = new InvUpload3503Result();
        result.setSourceLineId(line.getSourceLineId());
        result.setFixmedinsBchno(line.getFixmedinsBchno());

        if (inventoryProperties.isSkipWithoutYbNo()
            && (ChsInventoryConstants.TRACE_POLICY_NO_YB_NO.equals(line.getTracePolicy())
                || StringUtils.isBlank(line.getMedListCodg()))) {
            YbInvUpload skipped = upsertUploadRecord(
                null, ctx, ChsInfNo.INVENTORY_PURCHASE, line, ChsInventoryConstants.SOURCE_TYPE_INBOUND,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_SKIPPED_NO_YB_NO,
                null, null, null, null, operatorId, tenantId, false);
            return result.setUploadId(skipped.getId()).setUploadStatus(skipped.getUploadStatus());
        }

        Optional<YbInvUpload> existing = uploadRepository.findFirstByTenantIdAndInfnoAndFixmedinsBchnoAndSourceLineId(
            tenantId, ChsInfNo.INVENTORY_PURCHASE, line.getFixmedinsBchno(), line.getSourceLineId());
        if (existing.isPresent()
            && ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(existing.get().getUploadStatus())) {
            YbInvUpload row = existing.get();
            return result.setUploadId(row.getId()).setUploadStatus(row.getUploadStatus())
                .setPlatformMsg(row.getPlatformMsg());
        }

        JSONObject input = ChsInventoryPlatformAssembler.assemble3503(line);
        String requestJson = JSON.toJSONString(input);
        try {
            ResultBody body = gatewayClient.invoke(
                ctx.busNo(), ChsInfNo.INVENTORY_PURCHASE, input, buildInvokeContext());
            ChsPlatformResultSupport.requireSuccess(body, ChsInfNo.INVENTORY_PURCHASE);
            YbInvUpload saved = upsertUploadRecord(
                existing.orElse(null), ctx, ChsInfNo.INVENTORY_PURCHASE, line, ChsInventoryConstants.SOURCE_TYPE_INBOUND,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_SUCCESS,
                ChsInventoryConstants.PLATFORM_RET_RSLT_SUCCESS, null, requestJson, JSON.toJSONString(body), operatorId, tenantId, false);
            return result.setUploadId(saved.getId()).setUploadStatus(saved.getUploadStatus());
        } catch (RuntimeException ex) {
            YbInvUpload failed = upsertUploadRecord(
                existing.orElse(null), ctx, ChsInfNo.INVENTORY_PURCHASE, line, ChsInventoryConstants.SOURCE_TYPE_INBOUND,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_FAILED,
                ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED, ex.getMessage(), requestJson, null, operatorId, tenantId, true);
            return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
                .setPlatformMsg(ex.getMessage());
        }
    }

    private InvUpload3501Result processStocktakeLine3501(
        UploadStocktake3501Request request,
        InvUploadLineCommand line,
        ContractContext ctx,
        Long tenantId,
        Long operatorId,
        String sourceType
    ) {
        InvUpload3501Result result = new InvUpload3501Result();
        result.setSourceLineId(line.getSourceLineId());
        result.setFixmedinsBchno(line.getFixmedinsBchno());

        if (inventoryProperties.isSkipWithoutYbNo() && StringUtils.isBlank(line.getMedListCodg())) {
            YbInvUpload skipped = upsertUploadRecord(
                null, ctx, ChsInfNo.INVENTORY_STOCKTAKE, line, sourceType,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_SKIPPED_NO_YB_NO,
                null, null, null, null, operatorId, tenantId, false);
            return result.setUploadId(skipped.getId()).setUploadStatus(skipped.getUploadStatus());
        }

        Optional<YbInvUpload> existing = uploadRepository.findFirstByTenantIdAndInfnoAndFixmedinsBchnoAndSourceLineId(
            tenantId, ChsInfNo.INVENTORY_STOCKTAKE, line.getFixmedinsBchno(), line.getSourceLineId());
        if (existing.isPresent()
            && ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(existing.get().getUploadStatus())) {
            YbInvUpload row = existing.get();
            return result.setUploadId(row.getId()).setUploadStatus(row.getUploadStatus())
                .setPlatformMsg(row.getPlatformMsg());
        }

        JSONObject input = ChsInventoryPlatformAssembler.assemble3501(line);
        String requestJson = JSON.toJSONString(input);
        try {
            ResultBody body = gatewayClient.invoke(
                ctx.busNo(), ChsInfNo.INVENTORY_STOCKTAKE, input, buildInvokeContext());
            ChsPlatformResultSupport.requireSuccess(body, ChsInfNo.INVENTORY_STOCKTAKE);
            YbInvUpload saved = upsertUploadRecord(
                existing.orElse(null), ctx, ChsInfNo.INVENTORY_STOCKTAKE, line, sourceType,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_SUCCESS,
                ChsInventoryConstants.PLATFORM_RET_RSLT_SUCCESS, null, requestJson, JSON.toJSONString(body),
                operatorId, tenantId, false);
            return result.setUploadId(saved.getId()).setUploadStatus(saved.getUploadStatus());
        } catch (RuntimeException ex) {
            YbInvUpload failed = upsertUploadRecord(
                existing.orElse(null), ctx, ChsInfNo.INVENTORY_STOCKTAKE, line, sourceType,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_FAILED,
                ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED, ex.getMessage(), requestJson, null, operatorId, tenantId, true);
            return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
                .setPlatformMsg(ex.getMessage());
        }
    }

    private InvUpload3502Result processInvChangeLine3502(
        UploadInvChange3502Request request,
        InvUploadLineCommand line,
        ContractContext ctx,
        Long tenantId,
        Long operatorId,
        String sourceType
    ) {
        InvUpload3502Result result = new InvUpload3502Result();
        result.setSourceLineId(line.getSourceLineId());
        result.setFixmedinsBchno(line.getFixmedinsBchno());
        result.setInvChgType(line.getInvChgType());

        if (inventoryProperties.isSkipWithoutYbNo() && StringUtils.isBlank(line.getMedListCodg())) {
            YbInvUpload skipped = upsertUploadRecord(
                null, ctx, ChsInfNo.INVENTORY_CHANGE, line, sourceType,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_SKIPPED_NO_YB_NO,
                null, null, null, null, operatorId, tenantId, false);
            return result.setUploadId(skipped.getId()).setUploadStatus(skipped.getUploadStatus());
        }

        Optional<YbInvUpload> existing = uploadRepository.findFirstByTenantIdAndInfnoAndFixmedinsBchnoAndSourceLineId(
            tenantId, ChsInfNo.INVENTORY_CHANGE, line.getFixmedinsBchno(), line.getSourceLineId());
        if (existing.isPresent()
            && ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(existing.get().getUploadStatus())) {
            YbInvUpload row = existing.get();
            return result.setUploadId(row.getId()).setUploadStatus(row.getUploadStatus())
                .setPlatformMsg(row.getPlatformMsg());
        }

        JSONObject input = ChsInventoryPlatformAssembler.assemble3502(line);
        String requestJson = JSON.toJSONString(input);
        try {
            ResultBody body = gatewayClient.invoke(
                ctx.busNo(), ChsInfNo.INVENTORY_CHANGE, input, buildInvokeContext());
            ChsPlatformResultSupport.requireSuccess(body, ChsInfNo.INVENTORY_CHANGE);
            YbInvUpload saved = upsertUploadRecord(
                existing.orElse(null), ctx, ChsInfNo.INVENTORY_CHANGE, line, sourceType,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_SUCCESS,
                ChsInventoryConstants.PLATFORM_RET_RSLT_SUCCESS, null, requestJson, JSON.toJSONString(body),
                operatorId, tenantId, false);
            saveTraceRows(saved, line, tenantId);
            return result.setUploadId(saved.getId()).setUploadStatus(saved.getUploadStatus());
        } catch (RuntimeException ex) {
            YbInvUpload failed = upsertUploadRecord(
                existing.orElse(null), ctx, ChsInfNo.INVENTORY_CHANGE, line, sourceType,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_FAILED,
                ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED, ex.getMessage(), requestJson, null,
                operatorId, tenantId, true);
            return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
                .setPlatformMsg(ex.getMessage());
        }
    }

    private InvUpload3504Result processPurchaseReturnLine3504(
        UploadPurchaseReturn3504Request request,
        InvUploadLineCommand line,
        ContractContext ctx,
        Long tenantId,
        Long operatorId
    ) {
        InvUpload3504Result result = new InvUpload3504Result();
        result.setSourceLineId(line.getSourceLineId());
        result.setFixmedinsBchno(line.getFixmedinsBchno());

        if (inventoryProperties.isSkipWithoutYbNo()
            && (ChsInventoryConstants.TRACE_POLICY_NO_YB_NO.equals(line.getTracePolicy())
                || StringUtils.isBlank(line.getMedListCodg()))) {
            YbInvUpload skipped = upsertUploadRecord(
                null, ctx, ChsInfNo.INVENTORY_PURCHASE_RETURN, line, ChsInventoryConstants.SOURCE_TYPE_INBOUND,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_SKIPPED_NO_YB_NO,
                null, null, null, null, operatorId, tenantId, false);
            return result.setUploadId(skipped.getId()).setUploadStatus(skipped.getUploadStatus());
        }

        Optional<YbInvUpload> existing = uploadRepository.findFirstByTenantIdAndInfnoAndFixmedinsBchnoAndSourceLineId(
            tenantId, ChsInfNo.INVENTORY_PURCHASE_RETURN, line.getFixmedinsBchno(), line.getSourceLineId());
        if (existing.isPresent()
            && ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(existing.get().getUploadStatus())) {
            YbInvUpload row = existing.get();
            return result.setUploadId(row.getId()).setUploadStatus(row.getUploadStatus())
                .setPlatformMsg(row.getPlatformMsg());
        }

        JSONObject input = ChsInventoryPlatformAssembler.assemble3504(line);
        String requestJson = JSON.toJSONString(input);
        try {
            ResultBody body = gatewayClient.invoke(
                ctx.busNo(), ChsInfNo.INVENTORY_PURCHASE_RETURN, input, buildInvokeContext());
            ChsPlatformResultSupport.requireSuccess(body, ChsInfNo.INVENTORY_PURCHASE_RETURN);
            YbInvUpload saved = upsertUploadRecord(
                existing.orElse(null), ctx, ChsInfNo.INVENTORY_PURCHASE_RETURN, line, ChsInventoryConstants.SOURCE_TYPE_INBOUND,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_SUCCESS,
                ChsInventoryConstants.PLATFORM_RET_RSLT_SUCCESS, null, requestJson, JSON.toJSONString(body), operatorId, tenantId, false);
            return result.setUploadId(saved.getId()).setUploadStatus(saved.getUploadStatus());
        } catch (RuntimeException ex) {
            YbInvUpload failed = upsertUploadRecord(
                existing.orElse(null), ctx, ChsInfNo.INVENTORY_PURCHASE_RETURN, line, ChsInventoryConstants.SOURCE_TYPE_INBOUND,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_FAILED,
                ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED, ex.getMessage(), requestJson, null, operatorId, tenantId, true);
            return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
                .setPlatformMsg(ex.getMessage());
        }
    }

    private static void assertDeleteInvDataRequest(DeleteInvData3507Request request) {
        if (request == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "删除参数不能为空");
        }
        if (StringUtils.isBlank(request.getFixmedinsBchno())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "fixmedins_bchno 不能为空");
        }
        if (StringUtils.isBlank(request.getInvDataType())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "inv_data_type 不能为空");
        }
    }

    private static void assertInitializeInventoryRequest(InitializeInventoryRequest request) {
        if (request == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "初始化参数不能为空");
        }
        if (request.getLocationId() == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "locationId 不能为空");
        }
    }

    private void markDeletedByBatchNo(Long tenantId, String fixmedinsBchno, String invDataType) {
        List<String> infnos = resolveInfnoByInvDataType(invDataType);
        if (infnos.isEmpty()) {
            return;
        }
        PageRequest page = PageRequest.of(0, ChsInventoryConstants.LOCAL_UPLOAD_LIST_DEFAULT_LIMIT);
        List<YbInvUpload> rows = uploadRepository.findAllByTenantIdOrderByIdDesc(tenantId, page);
        for (YbInvUpload row : rows) {
            if (row == null || !infnos.contains(row.getInfno())) {
                continue;
            }
            if (!fixmedinsBchno.equals(row.getFixmedinsBchno())) {
                continue;
            }
            if (!ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(row.getUploadStatus())) {
                continue;
            }
            row.setUploadStatus(ChsInventoryConstants.UPLOAD_STATUS_DELETED);
            row.setUpdatedAt(LocalDateTime.now());
            uploadRepository.save(row);
        }
    }

    private static List<String> resolveInfnoByInvDataType(String invDataType) {
        if (ChsInventoryConstants.INV_DATA_TYPE_STOCKTAKE.equals(invDataType)) {
            return List.of(ChsInfNo.INVENTORY_STOCKTAKE);
        }
        if (ChsInventoryConstants.INV_DATA_TYPE_INV_CHANGE.equals(invDataType)) {
            return List.of(ChsInfNo.INVENTORY_CHANGE);
        }
        if (ChsInventoryConstants.INV_DATA_TYPE_PURCHASE.equals(invDataType)) {
            return List.of(ChsInfNo.INVENTORY_PURCHASE, ChsInfNo.INVENTORY_PURCHASE_RETURN);
        }
        if (ChsInventoryConstants.INV_DATA_TYPE_SALE.equals(invDataType)) {
            return List.of(ChsInfNo.INVENTORY_SALE, ChsInfNo.INVENTORY_SALE_RETURN);
        }
        return List.of();
    }

    private PlatformInvQueryResult queryPlatformInv(String infno, QueryPlatformInvRequest request) {
        assertPlatformQueryRequest(infno, request);
        ContractContext ctx = resolveContract(request.getContractBusNo(), request.getContractId());
        String fixmedinsCode = ctx.config().getFixmedinsCode();
        if (StringUtils.isBlank(fixmedinsCode)) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "契约未配置 fixmedins_code");
        }
        JSONObject input = ChsInventoryPlatformAssembler.assemblePlatformQuery(request, fixmedinsCode);
        ResultBody body = gatewayClient.invoke(ctx.busNo(), infno, input, buildInvokeContext());
        ChsPlatformResultSupport.requireSuccess(body, infno);
        JSONObject output = ChsPlatformResultSupport.parseOutputObject(body);
        PlatformInvQueryResult result = new PlatformInvQueryResult();
        result.setInfno(infno);
        result.setRows(parsePlatformQueryRows(output));
        return result;
    }

    private static void assertPlatformQueryRequest(String infno, QueryPlatformInvRequest request) {
        if (request == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "查询参数不能为空");
        }
        if (ChsInfNo.INVENTORY_INBOUND_TRACE_QUERY.equals(infno)
            || ChsInfNo.INVENTORY_SALE_TRACE_QUERY.equals(infno)) {
            boolean hasKey = StringUtils.isNotBlank(request.getMedListCodg())
                || StringUtils.isNotBlank(request.getMedinsListCodg())
                || StringUtils.isNotBlank(request.getFixmedinsBchno())
                || StringUtils.isNotBlank(request.getDrugTracCodg())
                || StringUtils.isNotBlank(request.getCertno());
            if (!hasKey) {
                throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID,
                    "3512/3513 至少填写 med_list_codg、medins_list_codg、fixmedins_bchno、drug_trac_codg 或 certno 之一");
            }
            return;
        }
        if (StringUtils.isBlank(request.getBegndate()) || StringUtils.isBlank(request.getEnddate())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "begndate/enddate 不能为空");
        }
        if (StringUtils.isBlank(request.getMedinsListCodg()) && StringUtils.isBlank(request.getFixmedinsBchno())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID,
                "medins_list_codg 与 fixmedins_bchno 至少填写一项");
        }
    }

    private PlatformUploadOutcome invokeSalePlatform(String contractBusNo, String infno, JSONObject input) {
        String requestJson = JSON.toJSONString(input);
        try {
            ResultBody body = gatewayClient.invoke(contractBusNo, infno, input, buildInvokeContext());
            ChsPlatformResultSupport.requireSuccess(body, infno);
            JSONObject output = ChsPlatformResultSupport.parseOutputObject(body);
            JSONObject platformResult = output.getJSONObject("result");
            if (platformResult == null) {
                platformResult = output;
            }
            String retRslt = readPlatformField(platformResult, "retRslt", "ret_rslt");
            String msgRslt = readPlatformField(platformResult, "msgRslt", "msg_rslt");
            String uploadStatus;
            if (ChsInventoryConstants.PLATFORM_RET_RSLT_SUCCESS.equals(retRslt)) {
                uploadStatus = ChsInventoryConstants.UPLOAD_STATUS_SUCCESS;
            } else {
                uploadStatus = ChsInventoryConstants.UPLOAD_STATUS_FAILED;
            }
            return PlatformUploadOutcome.success(uploadStatus, retRslt, msgRslt, requestJson, JSON.toJSONString(body));
        } catch (RuntimeException ex) {
            return PlatformUploadOutcome.failure(ex.getMessage(), requestJson);
        }
    }

    private record PlatformUploadOutcome(
        boolean invokeOk,
        String uploadStatus,
        String retRslt,
        String msgRslt,
        String requestJson,
        String responseJson,
        String errorMessage
    ) {
        static PlatformUploadOutcome success(
            String uploadStatus,
            String retRslt,
            String msgRslt,
            String requestJson,
            String responseJson
        ) {
            return new PlatformUploadOutcome(true, uploadStatus, retRslt, msgRslt, requestJson, responseJson, null);
        }

        static PlatformUploadOutcome failure(String errorMessage, String requestJson) {
            return new PlatformUploadOutcome(
                false,
                ChsInventoryConstants.UPLOAD_STATUS_FAILED,
                ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED,
                errorMessage,
                requestJson,
                null,
                errorMessage
            );
        }
    }

    private List<Map<String, Object>> parsePlatformQueryRows(JSONObject output) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (output == null || output.isEmpty()) {
            return rows;
        }
        for (String key : output.keySet()) {
            Object value = output.get(key);
            if (value instanceof JSONArray array) {
                appendJsonArrayRows(rows, array);
                if (!rows.isEmpty()) {
                    return rows;
                }
            }
        }
        if (output.containsKey("medListCodg") || output.containsKey("med_list_codg")
            || output.containsKey("fixmedinsBchno") || output.containsKey("fixmedins_bchno")) {
            rows.add(new HashMap<>(output));
        }
        return rows;
    }

    private static void appendJsonArrayRows(List<Map<String, Object>> rows, JSONArray array) {
        if (array == null || array.isEmpty()) {
            return;
        }
        for (int i = 0; i < array.size(); i++) {
            Object element = array.get(i);
            if (element instanceof JSONObject object) {
                rows.add(new HashMap<>(object));
            }
        }
    }

    private boolean retryStoredUpload(YbInvUpload row) {
        if (row == null || StringUtils.isBlank(row.getInfno()) || StringUtils.isBlank(row.getRequestJson())) {
            return false;
        }
        ContractContext ctx = resolveContract(row.getContractBusNo(), row.getContractId());
        JSONObject input = JSON.parseObject(row.getRequestJson());
        try {
            ResultBody body = gatewayClient.invoke(
                ctx.busNo(), row.getInfno(), input, buildInvokeContext());
            ChsPlatformResultSupport.requireSuccess(body, row.getInfno());
            JSONObject output = ChsPlatformResultSupport.parseOutputObject(body);
            JSONObject platformResult = output.getJSONObject("result");
            if (platformResult == null) {
                platformResult = output;
            }
            String retRslt = readPlatformField(platformResult, "retRslt", "ret_rslt");
            String msgRslt = readPlatformField(platformResult, "msgRslt", "msg_rslt");
            String uploadStatus = ChsInventoryConstants.UPLOAD_STATUS_SUCCESS;
            if (requiresPlatformRetRslt(row.getInfno())) {
                if (!ChsInventoryConstants.PLATFORM_RET_RSLT_SUCCESS.equals(retRslt)) {
                    uploadStatus = ChsInventoryConstants.UPLOAD_STATUS_FAILED;
                }
            } else {
                retRslt = ChsInventoryConstants.PLATFORM_RET_RSLT_SUCCESS;
            }
            row.setUploadStatus(uploadStatus);
            row.setPlatformRetRslt(retRslt);
            row.setPlatformMsg(msgRslt);
            row.setResponseJson(JSON.toJSONString(body));
            row.setUpdatedAt(LocalDateTime.now());
            if (ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(uploadStatus)) {
                row.setNextRetryAt(null);
            } else {
                applyNextRetrySchedule(row);
            }
            uploadRepository.save(row);
            rebuildTraceRowsFromStoredRequest(row);
            return ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(uploadStatus);
        } catch (RuntimeException ex) {
            row.setUploadStatus(ChsInventoryConstants.UPLOAD_STATUS_FAILED);
            row.setPlatformRetRslt(ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED);
            row.setPlatformMsg(ex.getMessage());
            row.setUpdatedAt(LocalDateTime.now());
            applyNextRetrySchedule(row);
            uploadRepository.save(row);
            return false;
        }
    }

    private boolean traceCountMatchesQuantity(InvUploadLineCommand line) {
        if (line.getSelRetnCnt() == null) {
            return false;
        }
        int expected = line.getSelRetnCnt().intValue();
        int actual = 0;
        if (line.getDrugTracCodgs() != null) {
            for (String code : line.getDrugTracCodgs()) {
                if (StringUtils.isNotBlank(code)) {
                    actual++;
                }
            }
        }
        return actual == expected;
    }

    private YbInvUpload upsertUploadRecord(
        YbInvUpload existing,
        ContractContext ctx,
        String infno,
        InvUploadLineCommand line,
        String sourceType,
        Long sourceId,
        String uploadStatus,
        String retRslt,
        String msgRslt,
        String requestJson,
        String responseJson,
        Long operatorId,
        Long tenantId,
        boolean scheduleRetry
    ) {
        YbInvUpload row = existing;
        if (row == null) {
            row = new YbInvUpload();
            row.setRetryCount(0);
            row.setCreatedAt(LocalDateTime.now());
            row.setTenantId(tenantId);
        }
        row.setContractId(ctx.contract().id());
        row.setContractBusNo(ctx.busNo());
        row.setInfno(infno);
        row.setFixmedinsBchno(line.getFixmedinsBchno());
        row.setMedListCodg(line.getMedListCodg());
        row.setSourceType(sourceType);
        row.setSourceId(sourceId);
        row.setSourceLineId(line.getSourceLineId());
        row.setUploadStatus(uploadStatus);
        row.setPlatformRetRslt(retRslt);
        row.setPlatformMsg(msgRslt);
        if (requestJson != null) {
            row.setRequestJson(requestJson);
        }
        row.setResponseJson(responseJson);
        row.setOperatorId(operatorId);
        row.setUpdatedAt(LocalDateTime.now());
        if (ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(uploadStatus)) {
            row.setNextRetryAt(null);
        } else if (scheduleRetry) {
            applyNextRetrySchedule(row);
        }
        return uploadRepository.saveAndFlush(row);
    }

    private void applyNextRetrySchedule(YbInvUpload row) {
        int count = safeRetryCount(row.getRetryCount());
        if (count >= inventoryProperties.getMaxRetry()) {
            row.setNextRetryAt(null);
            return;
        }
        List<Integer> backoffs = inventoryProperties.getRetryBackoffSeconds();
        if (backoffs == null || backoffs.isEmpty()) {
            row.setNextRetryAt(LocalDateTime.now().plusSeconds(60));
            return;
        }
        int index = Math.min(count, backoffs.size() - 1);
        int seconds = backoffs.get(index);
        if (seconds <= 0) {
            seconds = 60;
        }
        row.setNextRetryAt(LocalDateTime.now().plusSeconds(seconds));
    }

    private static int safeRetryCount(Integer retryCount) {
        if (retryCount == null || retryCount < 0) {
            return 0;
        }
        return retryCount;
    }

    private static boolean requiresPlatformRetRslt(String infno) {
        return ChsInfNo.INVENTORY_SALE.equals(infno) || ChsInfNo.INVENTORY_SALE_RETURN.equals(infno);
    }

    private static String readPlatformField(JSONObject platformResult, String camelKey, String snakeKey) {
        if (platformResult == null) {
            return null;
        }
        String value = platformResult.getString(camelKey);
        if (StringUtils.isBlank(value)) {
            value = platformResult.getString(snakeKey);
        }
        return value;
    }

    private void saveTraceRows(YbInvUpload upload, InvUploadLineCommand line, Long tenantId) {
        if (!ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(upload.getUploadStatus())) {
            return;
        }
        if (line.getDrugTracCodgs() == null || line.getDrugTracCodgs().isEmpty()) {
            return;
        }
        for (String code : line.getDrugTracCodgs()) {
            if (StringUtils.isBlank(code)) {
                continue;
            }
            saveTraceRow(upload.getId(), code.trim(), line.getTrdnFlag(), tenantId);
        }
    }

    private void rebuildTraceRowsFromStoredRequest(YbInvUpload row) {
        if (row == null || row.getId() == null) {
            return;
        }
        if (!ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(row.getUploadStatus())) {
            return;
        }
        if (!requiresPlatformRetRslt(row.getInfno())) {
            return;
        }
        if (!uploadTraceRepository.findAllByUploadIdOrderByIdAsc(row.getId()).isEmpty()) {
            return;
        }
        if (StringUtils.isBlank(row.getRequestJson())) {
            return;
        }
        JSONObject input;
        try {
            input = JSON.parseObject(row.getRequestJson());
        } catch (RuntimeException ex) {
            return;
        }
        JSONObject selinfo = input.getJSONObject("selinfo");
        if (selinfo == null) {
            return;
        }
        JSONArray drugtracinfo = selinfo.getJSONArray("drugtracinfo");
        if (drugtracinfo == null || drugtracinfo.isEmpty()) {
            return;
        }
        String trdnFlag = readPlatformField(selinfo, "trdnFlag", "trdn_flag");
        Long tenantId;
        if (row.getTenantId() != null) {
            tenantId = row.getTenantId();
        } else {
            tenantId = TenantUtils.getCurrentTenantId();
        }
        for (int i = 0; i < drugtracinfo.size(); i++) {
            Object element = drugtracinfo.get(i);
            if (!(element instanceof JSONObject traceObject)) {
                continue;
            }
            String code = readPlatformField(traceObject, "drugTracCodg", "drug_trac_codg");
            if (StringUtils.isBlank(code)) {
                continue;
            }
            saveTraceRow(row.getId(), code.trim(), trdnFlag, tenantId);
        }
    }

    private void saveTraceRow(Long uploadId, String drugTracCodg, String trdnFlag, Long tenantId) {
        YbInvUploadTrace trace = new YbInvUploadTrace();
        trace.setUploadId(uploadId);
        trace.setDrugTracCodg(drugTracCodg);
        trace.setTrdnFlag(trdnFlag);
        trace.setTenantId(tenantId);
        trace.setCreatedAt(LocalDateTime.now());
        trace.setUpdatedAt(LocalDateTime.now());
        uploadTraceRepository.save(trace);
    }

    private LocalInvUploadDto toLocalDto(YbInvUpload row) {
        LocalInvUploadDto dto = new LocalInvUploadDto();
        dto.setId(row.getId());
        dto.setContractBusNo(row.getContractBusNo());
        dto.setInfno(row.getInfno());
        dto.setFixmedinsBchno(row.getFixmedinsBchno());
        dto.setMedListCodg(row.getMedListCodg());
        dto.setSourceType(row.getSourceType());
        dto.setSourceId(row.getSourceId());
        dto.setSourceLineId(row.getSourceLineId());
        dto.setUploadStatus(row.getUploadStatus());
        dto.setPlatformRetRslt(row.getPlatformRetRslt());
        dto.setPlatformMsg(row.getPlatformMsg());
        if (row.getCreatedAt() != null) {
            dto.setCreatedAt(row.getCreatedAt().toString());
        }
        return dto;
    }

    private static CompareLocalVsPlatformRequest normalizeCompareRequest(CompareLocalVsPlatformRequest request) {
        if (request != null) {
            return request;
        }
        return new CompareLocalVsPlatformRequest();
    }

    private static ListLocalInvUploadRecordsRequest normalizeListLocalUploadRecordsRequest(
        ListLocalInvUploadRecordsRequest request
    ) {
        if (request != null) {
            return request;
        }
        return new ListLocalInvUploadRecordsRequest();
    }

    private record CompareFilterCriteria(
        String contractBusNo,
        String fixmedinsBchno,
        String medListCodg,
        String drugTracCodg,
        LocalDate beginDate,
        LocalDate endDate
    ) {
    }

    private static CompareFilterCriteria extractCompareFilterCriteria(CompareLocalVsPlatformRequest request) {
        return new CompareFilterCriteria(
            trimToNull(request.getContractBusNo()),
            trimToNull(request.getFixmedinsBchno()),
            trimToNull(request.getMedListCodg()),
            trimToNull(request.getDrugTracCodg()),
            parseCompareDate(request.getBegndate()),
            parseCompareDate(request.getEnddate())
        );
    }

    private static String requireCompareQueryInfno(CompareLocalVsPlatformRequest request) {
        if (request == null || StringUtils.isBlank(request.getCompareQueryInfno())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "compareQueryInfno 不能为空（3508–3513）");
        }
        String infno = request.getCompareQueryInfno().trim();
        if (!ChsInfNo.INVENTORY_STOCK_QUERY.equals(infno)
            && !ChsInfNo.INVENTORY_CHANGE_QUERY.equals(infno)
            && !ChsInfNo.INVENTORY_PURCHASE_QUERY.equals(infno)
            && !ChsInfNo.INVENTORY_SALE_QUERY.equals(infno)
            && !ChsInfNo.INVENTORY_INBOUND_TRACE_QUERY.equals(infno)
            && !ChsInfNo.INVENTORY_SALE_TRACE_QUERY.equals(infno)) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "compareQueryInfno 仅支持 3508–3513");
        }
        return infno;
    }

    private static String mapPlatformQueryToLocalUploadInfno(String compareQueryInfno) {
        if (ChsInfNo.INVENTORY_STOCK_QUERY.equals(compareQueryInfno)) {
            return ChsInfNo.INVENTORY_STOCKTAKE;
        }
        if (ChsInfNo.INVENTORY_CHANGE_QUERY.equals(compareQueryInfno)) {
            return ChsInfNo.INVENTORY_CHANGE;
        }
        if (ChsInfNo.INVENTORY_PURCHASE_QUERY.equals(compareQueryInfno)) {
            return ChsInfNo.INVENTORY_PURCHASE;
        }
        if (ChsInfNo.INVENTORY_SALE_QUERY.equals(compareQueryInfno)) {
            return ChsInfNo.INVENTORY_SALE;
        }
        if (ChsInfNo.INVENTORY_INBOUND_TRACE_QUERY.equals(compareQueryInfno)) {
            return ChsInfNo.INVENTORY_CHANGE + "," + ChsInfNo.INVENTORY_PURCHASE;
        }
        if (ChsInfNo.INVENTORY_SALE_TRACE_QUERY.equals(compareQueryInfno)) {
            return ChsInfNo.INVENTORY_SALE + "," + ChsInfNo.INVENTORY_SALE_RETURN;
        }
        throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "无法映射 compareQueryInfno: " + compareQueryInfno);
    }

    private PlatformInvQueryResult queryPlatformByCompareInfno(
        String compareQueryInfno,
        CompareLocalVsPlatformRequest request
    ) {
        if (ChsInfNo.INVENTORY_STOCK_QUERY.equals(compareQueryInfno)) {
            return queryPlatformInventory3508(request);
        }
        if (ChsInfNo.INVENTORY_CHANGE_QUERY.equals(compareQueryInfno)) {
            return queryPlatformInvChange3509(request);
        }
        if (ChsInfNo.INVENTORY_PURCHASE_QUERY.equals(compareQueryInfno)) {
            return queryPlatformPurchase3510(request);
        }
        if (ChsInfNo.INVENTORY_INBOUND_TRACE_QUERY.equals(compareQueryInfno)) {
            return queryPlatformInboundTrace3512(request);
        }
        if (ChsInfNo.INVENTORY_SALE_TRACE_QUERY.equals(compareQueryInfno)) {
            return queryPlatformSaleTrace3513(request);
        }
        return queryPlatformSale3511(request);
    }

    private List<LocalInvUploadDto> filterLocalRowsForCompare(
        List<LocalInvUploadDto> rows,
        CompareLocalVsPlatformRequest request
    ) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        CompareFilterCriteria criteria = extractCompareFilterCriteria(normalizeCompareRequest(request));
        List<LocalInvUploadDto> filtered = new ArrayList<>();
        for (LocalInvUploadDto row : rows) {
            if (row == null) {
                continue;
            }
            if (criteria.contractBusNo() != null && !criteria.contractBusNo().equals(trimToNull(row.getContractBusNo()))) {
                continue;
            }
            if (criteria.fixmedinsBchno() != null && !criteria.fixmedinsBchno().equals(trimToNull(row.getFixmedinsBchno()))) {
                continue;
            }
            if (criteria.medListCodg() != null && !criteria.medListCodg().equals(trimToNull(row.getMedListCodg()))) {
                continue;
            }
            if (!withinCompareDateRange(row.getCreatedAt(), criteria.beginDate(), criteria.endDate())) {
                continue;
            }
            filtered.add(row);
        }
        return filtered;
    }

    private static LocalDate parseCompareDate(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 10) {
            trimmed = trimmed.substring(0, 10);
        }
        return LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private static boolean withinCompareDateRange(String createdAt, LocalDate beginDate, LocalDate endDate) {
        if (beginDate == null && endDate == null) {
            return true;
        }
        if (StringUtils.isBlank(createdAt)) {
            return false;
        }
        LocalDate createdDate;
        try {
            if (createdAt.length() >= 10) {
                createdDate = LocalDate.parse(createdAt.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);
            } else {
                createdDate = LocalDate.parse(createdAt.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
            }
        } catch (RuntimeException ex) {
            return false;
        }
        if (beginDate != null && createdDate.isBefore(beginDate)) {
            return false;
        }
        if (endDate != null && createdDate.isAfter(endDate)) {
            return false;
        }
        return true;
    }

    private static Map<String, LocalInvUploadDto> indexLocalRowsByCompareKey(List<LocalInvUploadDto> rows) {
        Map<String, LocalInvUploadDto> indexed = new LinkedHashMap<>();
        for (LocalInvUploadDto row : rows) {
            String key = buildCompareKey(row.getFixmedinsBchno(), row.getMedListCodg());
            if (key == null) {
                continue;
            }
            indexed.putIfAbsent(key, row);
        }
        return indexed;
    }

    private static Map<String, Map<String, Object>> indexPlatformRowsByCompareKey(List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> indexed = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            String key = buildCompareKey(
                readCompareRowField(row, "fixmedinsBchno", "fixmedins_bchno"),
                readCompareRowField(row, "medListCodg", "med_list_codg"));
            if (key == null) {
                continue;
            }
            indexed.putIfAbsent(key, row);
        }
        return indexed;
    }

    private static InvCompareRowDto buildCompareRow(
        String key,
        LocalInvUploadDto localRow,
        Map<String, Object> platformRow
    ) {
        InvCompareRowDto rowDto = new InvCompareRowDto();
        rowDto.setCompareKey(key);
        String[] parts = key.split("\\|", 2);
        if (parts.length > 0) {
            rowDto.setFixmedinsBchno(parts[0]);
        } else {
            rowDto.setFixmedinsBchno(null);
        }
        if (parts.length > 1) {
            rowDto.setMedListCodg(parts[1]);
        } else {
            rowDto.setMedListCodg(null);
        }
        if (localRow != null && platformRow != null) {
            rowDto.setDiffType(ChsInventoryConstants.COMPARE_DIFF_MATCH);
            rowDto.setLocalUploadId(localRow.getId());
            rowDto.setLocalUploadStatus(localRow.getUploadStatus());
            rowDto.setLocalCreatedAt(localRow.getCreatedAt());
            rowDto.setPlatformRow(new HashMap<>(platformRow));
        } else if (localRow != null) {
            rowDto.setDiffType(ChsInventoryConstants.COMPARE_DIFF_LOCAL_ONLY);
            rowDto.setLocalUploadId(localRow.getId());
            rowDto.setLocalUploadStatus(localRow.getUploadStatus());
            rowDto.setLocalCreatedAt(localRow.getCreatedAt());
        } else {
            rowDto.setDiffType(ChsInventoryConstants.COMPARE_DIFF_PLATFORM_ONLY);
            rowDto.setPlatformRow(new HashMap<>(platformRow));
            rowDto.setFixmedinsBchno(readCompareRowField(platformRow, "fixmedinsBchno", "fixmedins_bchno"));
            rowDto.setMedListCodg(readCompareRowField(platformRow, "medListCodg", "med_list_codg"));
        }
        return rowDto;
    }

    private static String buildCompareKey(String fixmedinsBchno, String medListCodg) {
        String bchno = trimToNull(fixmedinsBchno);
        if (bchno == null) {
            return null;
        }
        String med = trimToNull(medListCodg);
        if (med == null) {
            return bchno + "|";
        }
        return bchno + "|" + med;
    }

    private static String readCompareRowField(Map<String, Object> row, String camelKey, String snakeKey) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        Object camelValue = row.get(camelKey);
        if (camelValue != null) {
            return trimToNull(String.valueOf(camelValue));
        }
        Object snakeValue = row.get(snakeKey);
        if (snakeValue != null) {
            return trimToNull(String.valueOf(snakeValue));
        }
        return null;
    }

    private ContractContext resolveContract(String contractBusNo, Long contractId) {
        ChsContractSnapshot contract = contractPort.requireContract(contractBusNo, contractId);
        String busNo = contractPort.requireContractBusNo(contract);
        ContractConfig config = ContractConfigLoader.getByBusNo(busNo);
        return new ContractContext(contract, busNo, config);
    }

    private InvokeContext buildInvokeContext() {
        Long operatorId = currentOperatorResolver.requireOperatorId("请先登录");
        InvokeContext partial = InvokeContext.builder()
            .operatorId(String.valueOf(operatorId))
            .build();
        return InvokeContext.builder()
            .operatorId(signCredentialResolver.resolveOperatorId(partial))
            .operatorName(signCredentialResolver.resolveOperatorName(partial))
            .build();
    }

    private record ContractContext(ChsContractSnapshot contract, String busNo, ContractConfig config) {
    }
}
