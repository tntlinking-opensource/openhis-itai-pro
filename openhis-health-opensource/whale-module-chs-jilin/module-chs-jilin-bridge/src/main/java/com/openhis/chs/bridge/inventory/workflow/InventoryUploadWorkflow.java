/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inventory.workflow;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.bridge.inventory.invoker.Inv3501Invoker;
import com.openhis.chs.bridge.inventory.invoker.Inv3502Invoker;
import com.openhis.chs.bridge.inventory.invoker.Inv3503Invoker;
import com.openhis.chs.bridge.inventory.invoker.Inv3504Invoker;
import com.openhis.chs.bridge.inventory.invoker.Inv3505Invoker;
import com.openhis.chs.bridge.inventory.invoker.Inv3506Invoker;
import com.openhis.chs.bridge.inventory.support.InvPlatformSupport;
import com.openhis.chs.bridge.inventory.support.InvPlatformSupport.InvContractContext;
import com.openhis.chs.domain.inventory.access.InventoryUploadAccess;
import com.openhis.chs.domain.share.constants.ChsInventoryConstants;
import com.openhis.chs.domain.inventory.config.ChsInventoryProperties;
import com.openhis.chs.domain.inventory.command.InvUploadLineCommand;
import com.openhis.chs.domain.inventory.command.InvUpload3502Command;
import com.openhis.chs.domain.inventory.command.InvUpload3503Command;
import com.openhis.chs.domain.inventory.command.InvUpload3504Command;
import com.openhis.chs.domain.inventory.command.InvUpload3505Command;
import com.openhis.chs.domain.inventory.command.InvUpload3506Command;
import com.openhis.chs.domain.inventory.command.InvUpload3501Command;
import com.openhis.chs.domain.inventory.result.InvUpload3501Result;
import com.openhis.chs.domain.inventory.result.InvUpload3502Result;
import com.openhis.chs.domain.inventory.result.InvUpload3503Result;
import com.openhis.chs.domain.inventory.result.InvUpload3504Result;
import com.openhis.chs.domain.inventory.result.InvUpload3505Result;
import com.openhis.chs.domain.inventory.result.InvUpload3506Result;
import com.openhis.chs.domain.inventory.entity.YbInvUpload;
import com.openhis.chs.domain.inventory.service.YbInvUploadDomainService;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.inventory.model.Inv3501Input;
import com.openhis.chs.gateway.scheme.inventory.model.Inv3502Input;
import com.openhis.chs.gateway.scheme.inventory.model.Inv3503Input;
import com.openhis.chs.gateway.scheme.inventory.model.Inv3504Input;
import com.openhis.chs.gateway.scheme.inventory.model.Inv3505Input;
import com.openhis.chs.gateway.scheme.inventory.model.Inv3506Input;
import com.openhis.chs.gateway.scheme.inventory.assembler.InvPlatformInputAssembler;
import com.openhis.chs.gateway.scheme.inventory.parser.InvPlatformOutputParser;
import com.openhis.chs.gateway.support.ChsCurrentOperatorResolver;
import com.openhis.chs.gateway.support.ChsGatewayOutputSupport;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 【3501–3506】进销存上报 bridge 编排实现（P14）。
 *
 * <p><b>职责：</b>校验命令 → 调用 Inv350x invoker 出站 → upsert {@code yb_inv_upload} 及追溯表。</p>
 * <p><b>实现端口：</b>{@link com.openhis.chs.domain.inventory.access.InventoryUploadAccess}</p>
 * <p><b>调用方：</b>{@code InsuranceInventoryAppService}</p>
 * <p><b>架构边界：</b>bridge 层组装 gateway 出站上下文；application 只依赖 domain 端口。</p>
 * <p><b>迁移阶段：</b>P14</p>
 */
@Component
@RequiredArgsConstructor
public class InventoryUploadWorkflow implements InventoryUploadAccess {

        private final Inv3501Invoker inv3501Invoker;
    private final Inv3502Invoker inv3502Invoker;
    private final Inv3503Invoker inv3503Invoker;
    private final Inv3504Invoker inv3504Invoker;
    private final Inv3505Invoker inv3505Invoker;
    private final Inv3506Invoker inv3506Invoker;    private final InvPlatformSupport invPlatformSupport;
    private final ChsCurrentOperatorResolver currentOperatorResolver;
    private final YbInvUploadDomainService uploadDomainService;
    private final ChsInventoryProperties inventoryProperties;

    public List<InvUpload3505Result> uploadSale3505(InvUpload3505Command request) {
        InvContractContext ctx = invPlatformSupport.resolveContract(request.getContractBusNo(), request.getContractId());
        Long tenantId = TenantUtils.getCurrentTenantId();
        Long operatorId = currentOperatorResolver.requireOperatorId("请先登录");
        List<InvUpload3505Result> results = new ArrayList<>();
        if (request.getLines() == null) {
            return results;
        }
        for (InvUploadLineCommand line : request.getLines()) {
            results.add(processSaleLine3505(request, line, ctx, tenantId, operatorId));
        }
        return results;
    }

    /**
     * 【3506】商品销售退货上报：逐行调用平台接口。
     *
     * @param request 契约、来源单据、退货行
     * @return 各行 uploadId、uploadStatus 及平台 retRslt/msgRslt
     */
    public List<InvUpload3506Result> uploadSaleReturn3506(InvUpload3506Command request) {
        InvContractContext ctx = invPlatformSupport.resolveContract(request.getContractBusNo(), request.getContractId());
        Long tenantId = TenantUtils.getCurrentTenantId();
        Long operatorId = currentOperatorResolver.requireOperatorId("请先登录");
        List<InvUpload3506Result> results = new ArrayList<>();
        if (request.getLines() == null) {
            return results;
        }
        for (InvUploadLineCommand line : request.getLines()) {
            results.add(processReturnLine3506(request, line, ctx, tenantId, operatorId));
        }
        return results;
    }

    /**
     * 【3503】商品采购入库上报：逐行调用平台接口。
     *
     * @param request 契约、来源单据、采购行
     * @return 各行 uploadId、uploadStatus
     */
    public List<InvUpload3503Result> uploadPurchase3503(InvUpload3503Command request) {
        InvContractContext ctx = invPlatformSupport.resolveContract(request.getContractBusNo(), request.getContractId());
        Long tenantId = TenantUtils.getCurrentTenantId();
        Long operatorId = currentOperatorResolver.requireOperatorId("请先登录");
        List<InvUpload3503Result> results = new ArrayList<>();
        if (request.getLines() == null) {
            return results;
        }
        for (InvUploadLineCommand line : request.getLines()) {
            results.add(processPurchaseLine3503(request, line, ctx, tenantId, operatorId));
        }
        return results;
    }

    /**
     * 【3504】商品采购退货上报：逐行调用平台接口。
     *
     * @param request 契约、来源单据、采购退货行
     * @return 各行 uploadId、uploadStatus
     */
    public List<InvUpload3504Result> uploadPurchaseReturn3504(InvUpload3504Command request) {
        InvContractContext ctx = invPlatformSupport.resolveContract(request.getContractBusNo(), request.getContractId());
        Long tenantId = TenantUtils.getCurrentTenantId();
        Long operatorId = currentOperatorResolver.requireOperatorId("请先登录");
        List<InvUpload3504Result> results = new ArrayList<>();
        if (request.getLines() == null) {
            return results;
        }
        for (InvUploadLineCommand line : request.getLines()) {
            results.add(processPurchaseReturnLine3504(request, line, ctx, tenantId, operatorId));
        }
        return results;
    }

    /**
     * 【3501】商品盘存上报：逐行调用平台接口。
     *
     * @param request 契约、来源单据、盘存行
     * @return 各行 uploadId、uploadStatus
     */
    public List<InvUpload3501Result> uploadStocktake3501(InvUpload3501Command request) {
        InvContractContext ctx = invPlatformSupport.resolveContract(request.getContractBusNo(), request.getContractId());
        Long tenantId = TenantUtils.getCurrentTenantId();
        Long operatorId = currentOperatorResolver.requireOperatorId("请先登录");
        String sourceType = isNotEmpty(request.getSourceType())
            ? request.getSourceType().trim() : ChsInventoryConstants.SOURCE_TYPE_STOCKTAKE;
        List<InvUpload3501Result> results = new ArrayList<>();
        if (request.getLines() == null) {
            return results;
        }
        for (InvUploadLineCommand line : request.getLines()) {
            results.add(processStocktakeLine3501(request, line, ctx, tenantId, operatorId, sourceType));
        }
        return results;
    }

    /**
     * 【3502】商品库存变更上报：逐行调用平台接口并保存追溯码。
     *
     * @param request 契约、来源单据、sourceType（缺省 MANUAL）
     * @return 各行 uploadId、uploadStatus、invChgType
     */
    public List<InvUpload3502Result> uploadInvChange3502(InvUpload3502Command request) {
        InvContractContext ctx = invPlatformSupport.resolveContract(request.getContractBusNo(), request.getContractId());
        Long tenantId = TenantUtils.getCurrentTenantId();
        Long operatorId = currentOperatorResolver.requireOperatorId("请先登录");
        String sourceType;
        if (isNotEmpty(request.getSourceType())) {
            sourceType = request.getSourceType().trim();
        } else {
            sourceType = ChsInventoryConstants.SOURCE_TYPE_MANUAL;
        }
        List<InvUpload3502Result> results = new ArrayList<>();
        if (request.getLines() == null) {
            return results;
        }
        for (InvUploadLineCommand line : request.getLines()) {
            results.add(processInvChangeLine3502(request, line, ctx, tenantId, operatorId, sourceType));
        }
        return results;
    }

    private InvUpload3505Result processSaleLine3505(
        InvUpload3505Command request,
        InvUploadLineCommand line,
        InvContractContext ctx,
        Long tenantId,
        Long operatorId
    ) {
        InvUpload3505Result result = new InvUpload3505Result();
        result.setSourceLineId(line.getSourceLineId());
        result.setFixmedinsBchno(line.getFixmedinsBchno());

        if (inventoryProperties.isSkipWithoutYbNo()
            && (ChsInventoryConstants.TRACE_POLICY_NO_YB_NO.equals(line.getTracePolicy())
                || isEmpty(line.getMedListCodg()))) {
            YbInvUpload skipped = uploadDomainService.upsertUploadRecord(
                null, ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_SALE, line, ChsInventoryConstants.SOURCE_TYPE_DISPENSE, request.getSourceId(),
                ChsInventoryConstants.UPLOAD_STATUS_SKIPPED_NO_YB_NO,
                null, null, null, null, operatorId, tenantId, false);
            return result.setUploadId(skipped.getId()).setUploadStatus(skipped.getUploadStatus());
        }

        Optional<YbInvUpload> existing = uploadDomainService.findExistingLine(
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
                YbInvUpload failed = uploadDomainService.upsertUploadRecord(
                    existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_SALE, line, ChsInventoryConstants.SOURCE_TYPE_DISPENSE, request.getSourceId(),
                    ChsInventoryConstants.UPLOAD_STATUS_FAILED,
                    ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED, "trace code count mismatch", null, null, operatorId, tenantId, true);
                return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
                    .setPlatformRetRslt(ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED).setPlatformMsg(failed.getPlatformMsg());
            }
        }

        Inv3505Input input = InvPlatformInputAssembler.assemble3505(line);
        PlatformUploadOutcome outcome = invokeSalePlatform(ctx.busNo(), ChsInfNo.INVENTORY_SALE, input);
        if (outcome.invokeOk()) {
            YbInvUpload saved = uploadDomainService.upsertUploadRecord(
                existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_SALE, line, ChsInventoryConstants.SOURCE_TYPE_DISPENSE, request.getSourceId(),
                outcome.uploadStatus(), outcome.retRslt(), outcome.msgRslt(), outcome.requestJson(), outcome.responseJson(),
                operatorId, tenantId, ChsInventoryConstants.UPLOAD_STATUS_FAILED.equals(outcome.uploadStatus()));
            uploadDomainService.saveTraceRows(saved, line, tenantId);
            return result.setUploadId(saved.getId()).setUploadStatus(saved.getUploadStatus())
                .setPlatformRetRslt(outcome.retRslt()).setPlatformMsg(outcome.msgRslt());
        }
        YbInvUpload failed = uploadDomainService.upsertUploadRecord(
            existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_SALE, line, ChsInventoryConstants.SOURCE_TYPE_DISPENSE, request.getSourceId(),
            ChsInventoryConstants.UPLOAD_STATUS_FAILED,
            outcome.retRslt(), outcome.errorMessage(), outcome.requestJson(), null, operatorId, tenantId, true);
        return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
            .setPlatformRetRslt(outcome.retRslt()).setPlatformMsg(outcome.errorMessage());
    }

    private InvUpload3506Result processReturnLine3506(
        InvUpload3506Command request,
        InvUploadLineCommand line,
        InvContractContext ctx,
        Long tenantId,
        Long operatorId
    ) {
        InvUpload3506Result result = new InvUpload3506Result();
        result.setSourceLineId(line.getSourceLineId());
        result.setFixmedinsBchno(line.getFixmedinsBchno());

        if (inventoryProperties.isSkipWithoutYbNo()
            && (ChsInventoryConstants.TRACE_POLICY_NO_YB_NO.equals(line.getTracePolicy())
                || isEmpty(line.getMedListCodg()))) {
            YbInvUpload skipped = uploadDomainService.upsertUploadRecord(
                null, ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_SALE_RETURN, line, ChsInventoryConstants.SOURCE_TYPE_DISPENSE, request.getSourceId(),
                ChsInventoryConstants.UPLOAD_STATUS_SKIPPED_NO_YB_NO,
                null, null, null, null, operatorId, tenantId, false);
            return result.setUploadId(skipped.getId()).setUploadStatus(skipped.getUploadStatus());
        }

        Optional<YbInvUpload> existing = uploadDomainService.findExistingLine(
            tenantId, ChsInfNo.INVENTORY_SALE_RETURN, line.getFixmedinsBchno(), line.getSourceLineId());
        if (existing.isPresent()
            && ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(existing.get().getUploadStatus())) {
            YbInvUpload row = existing.get();
            return result.setUploadId(row.getId()).setUploadStatus(row.getUploadStatus())
                .setPlatformRetRslt(row.getPlatformRetRslt()).setPlatformMsg(row.getPlatformMsg());
        }

        Inv3506Input input = InvPlatformInputAssembler.assemble3506(line);
        PlatformUploadOutcome outcome = invokeSalePlatform(ctx.busNo(), ChsInfNo.INVENTORY_SALE_RETURN, input);
        if (outcome.invokeOk()) {
            YbInvUpload saved = uploadDomainService.upsertUploadRecord(
                existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_SALE_RETURN, line, ChsInventoryConstants.SOURCE_TYPE_DISPENSE, request.getSourceId(),
                outcome.uploadStatus(), outcome.retRslt(), outcome.msgRslt(), outcome.requestJson(), outcome.responseJson(),
                operatorId, tenantId, ChsInventoryConstants.UPLOAD_STATUS_FAILED.equals(outcome.uploadStatus()));
            uploadDomainService.saveTraceRows(saved, line, tenantId);
            return result.setUploadId(saved.getId()).setUploadStatus(saved.getUploadStatus())
                .setPlatformRetRslt(outcome.retRslt()).setPlatformMsg(outcome.msgRslt());
        }
        YbInvUpload failed = uploadDomainService.upsertUploadRecord(
            existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_SALE_RETURN, line, ChsInventoryConstants.SOURCE_TYPE_DISPENSE, request.getSourceId(),
            ChsInventoryConstants.UPLOAD_STATUS_FAILED,
            outcome.retRslt(), outcome.errorMessage(), outcome.requestJson(), null, operatorId, tenantId, true);
        return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
            .setPlatformRetRslt(outcome.retRslt()).setPlatformMsg(outcome.errorMessage());
    }

    private InvUpload3503Result processPurchaseLine3503(
        InvUpload3503Command request,
        InvUploadLineCommand line,
        InvContractContext ctx,
        Long tenantId,
        Long operatorId
    ) {
        InvUpload3503Result result = new InvUpload3503Result();
        result.setSourceLineId(line.getSourceLineId());
        result.setFixmedinsBchno(line.getFixmedinsBchno());

        if (inventoryProperties.isSkipWithoutYbNo()
            && (ChsInventoryConstants.TRACE_POLICY_NO_YB_NO.equals(line.getTracePolicy())
                || isEmpty(line.getMedListCodg()))) {
            YbInvUpload skipped = uploadDomainService.upsertUploadRecord(
                null, ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_PURCHASE, line, ChsInventoryConstants.SOURCE_TYPE_INBOUND,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_SKIPPED_NO_YB_NO,
                null, null, null, null, operatorId, tenantId, false);
            return result.setUploadId(skipped.getId()).setUploadStatus(skipped.getUploadStatus());
        }

        Optional<YbInvUpload> existing = uploadDomainService.findExistingLine(
            tenantId, ChsInfNo.INVENTORY_PURCHASE, line.getFixmedinsBchno(), line.getSourceLineId());
        if (existing.isPresent()
            && ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(existing.get().getUploadStatus())) {
            YbInvUpload row = existing.get();
            return result.setUploadId(row.getId()).setUploadStatus(row.getUploadStatus())
                .setPlatformMsg(row.getPlatformMsg());
        }

        Inv3503Input input = InvPlatformInputAssembler.assemble3503(line);
        String requestJson = JSON.toJSONString(input);
        try {
            ResultBody body = inv3503Invoker.invoke(ctx.busNo(), input, invPlatformSupport.buildInvokeContext());
            YbInvUpload saved = uploadDomainService.upsertUploadRecord(
                existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_PURCHASE, line, ChsInventoryConstants.SOURCE_TYPE_INBOUND,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_SUCCESS,
                ChsInventoryConstants.PLATFORM_RET_RSLT_SUCCESS, null, requestJson, JSON.toJSONString(body), operatorId, tenantId, false);
            return result.setUploadId(saved.getId()).setUploadStatus(saved.getUploadStatus());
        } catch (RuntimeException ex) {
            YbInvUpload failed = uploadDomainService.upsertUploadRecord(
                existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_PURCHASE, line, ChsInventoryConstants.SOURCE_TYPE_INBOUND,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_FAILED,
                ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED, ex.getMessage(), requestJson, null, operatorId, tenantId, true);
            return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
                .setPlatformMsg(ex.getMessage());
        }
    }

    private InvUpload3501Result processStocktakeLine3501(
        InvUpload3501Command request,
        InvUploadLineCommand line,
        InvContractContext ctx,
        Long tenantId,
        Long operatorId,
        String sourceType
    ) {
        InvUpload3501Result result = new InvUpload3501Result();
        result.setSourceLineId(line.getSourceLineId());
        result.setFixmedinsBchno(line.getFixmedinsBchno());

        if (inventoryProperties.isSkipWithoutYbNo() && isEmpty(line.getMedListCodg())) {
            YbInvUpload skipped = uploadDomainService.upsertUploadRecord(
                null, ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_STOCKTAKE, line, sourceType,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_SKIPPED_NO_YB_NO,
                null, null, null, null, operatorId, tenantId, false);
            return result.setUploadId(skipped.getId()).setUploadStatus(skipped.getUploadStatus());
        }

        Optional<YbInvUpload> existing = uploadDomainService.findExistingLine(
            tenantId, ChsInfNo.INVENTORY_STOCKTAKE, line.getFixmedinsBchno(), line.getSourceLineId());
        if (existing.isPresent()
            && ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(existing.get().getUploadStatus())) {
            YbInvUpload row = existing.get();
            return result.setUploadId(row.getId()).setUploadStatus(row.getUploadStatus())
                .setPlatformMsg(row.getPlatformMsg());
        }

        Inv3501Input input = InvPlatformInputAssembler.assemble3501(line);
        String requestJson = JSON.toJSONString(input);
        try {
            ResultBody body = inv3501Invoker.invoke(ctx.busNo(), input, invPlatformSupport.buildInvokeContext());
            YbInvUpload saved = uploadDomainService.upsertUploadRecord(
                existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_STOCKTAKE, line, sourceType,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_SUCCESS,
                ChsInventoryConstants.PLATFORM_RET_RSLT_SUCCESS, null, requestJson, JSON.toJSONString(body),
                operatorId, tenantId, false);
            return result.setUploadId(saved.getId()).setUploadStatus(saved.getUploadStatus());
        } catch (RuntimeException ex) {
            YbInvUpload failed = uploadDomainService.upsertUploadRecord(
                existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_STOCKTAKE, line, sourceType,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_FAILED,
                ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED, ex.getMessage(), requestJson, null, operatorId, tenantId, true);
            return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
                .setPlatformMsg(ex.getMessage());
        }
    }

    private InvUpload3502Result processInvChangeLine3502(
        InvUpload3502Command request,
        InvUploadLineCommand line,
        InvContractContext ctx,
        Long tenantId,
        Long operatorId,
        String sourceType
    ) {
        InvUpload3502Result result = new InvUpload3502Result();
        result.setSourceLineId(line.getSourceLineId());
        result.setFixmedinsBchno(line.getFixmedinsBchno());
        result.setInvChgType(line.getInvChgType());

        if (inventoryProperties.isSkipWithoutYbNo() && isEmpty(line.getMedListCodg())) {
            YbInvUpload skipped = uploadDomainService.upsertUploadRecord(
                null, ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_CHANGE, line, sourceType,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_SKIPPED_NO_YB_NO,
                null, null, null, null, operatorId, tenantId, false);
            return result.setUploadId(skipped.getId()).setUploadStatus(skipped.getUploadStatus());
        }

        Optional<YbInvUpload> existing = uploadDomainService.findExistingLine(
            tenantId, ChsInfNo.INVENTORY_CHANGE, line.getFixmedinsBchno(), line.getSourceLineId());
        if (existing.isPresent()
            && ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(existing.get().getUploadStatus())) {
            YbInvUpload row = existing.get();
            return result.setUploadId(row.getId()).setUploadStatus(row.getUploadStatus())
                .setPlatformMsg(row.getPlatformMsg());
        }

        Inv3502Input input = InvPlatformInputAssembler.assemble3502(line);
        String requestJson = JSON.toJSONString(input);
        try {
            ResultBody body = inv3502Invoker.invoke(ctx.busNo(), input, invPlatformSupport.buildInvokeContext());
            YbInvUpload saved = uploadDomainService.upsertUploadRecord(
                existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_CHANGE, line, sourceType,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_SUCCESS,
                ChsInventoryConstants.PLATFORM_RET_RSLT_SUCCESS, null, requestJson, JSON.toJSONString(body),
                operatorId, tenantId, false);
            uploadDomainService.saveTraceRows(saved, line, tenantId);
            return result.setUploadId(saved.getId()).setUploadStatus(saved.getUploadStatus());
        } catch (RuntimeException ex) {
            YbInvUpload failed = uploadDomainService.upsertUploadRecord(
                existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_CHANGE, line, sourceType,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_FAILED,
                ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED, ex.getMessage(), requestJson, null,
                operatorId, tenantId, true);
            return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
                .setPlatformMsg(ex.getMessage());
        }
    }

    private InvUpload3504Result processPurchaseReturnLine3504(
        InvUpload3504Command request,
        InvUploadLineCommand line,
        InvContractContext ctx,
        Long tenantId,
        Long operatorId
    ) {
        InvUpload3504Result result = new InvUpload3504Result();
        result.setSourceLineId(line.getSourceLineId());
        result.setFixmedinsBchno(line.getFixmedinsBchno());

        if (inventoryProperties.isSkipWithoutYbNo()
            && (ChsInventoryConstants.TRACE_POLICY_NO_YB_NO.equals(line.getTracePolicy())
                || isEmpty(line.getMedListCodg()))) {
            YbInvUpload skipped = uploadDomainService.upsertUploadRecord(
                null, ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_PURCHASE_RETURN, line, ChsInventoryConstants.SOURCE_TYPE_INBOUND,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_SKIPPED_NO_YB_NO,
                null, null, null, null, operatorId, tenantId, false);
            return result.setUploadId(skipped.getId()).setUploadStatus(skipped.getUploadStatus());
        }

        Optional<YbInvUpload> existing = uploadDomainService.findExistingLine(
            tenantId, ChsInfNo.INVENTORY_PURCHASE_RETURN, line.getFixmedinsBchno(), line.getSourceLineId());
        if (existing.isPresent()
            && ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(existing.get().getUploadStatus())) {
            YbInvUpload row = existing.get();
            return result.setUploadId(row.getId()).setUploadStatus(row.getUploadStatus())
                .setPlatformMsg(row.getPlatformMsg());
        }

        Inv3504Input input = InvPlatformInputAssembler.assemble3504(line);
        String requestJson = JSON.toJSONString(input);
        try {
            ResultBody body = inv3504Invoker.invoke(ctx.busNo(), input, invPlatformSupport.buildInvokeContext());
            YbInvUpload saved = uploadDomainService.upsertUploadRecord(
                existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_PURCHASE_RETURN, line, ChsInventoryConstants.SOURCE_TYPE_INBOUND,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_SUCCESS,
                ChsInventoryConstants.PLATFORM_RET_RSLT_SUCCESS, null, requestJson, JSON.toJSONString(body), operatorId, tenantId, false);
            return result.setUploadId(saved.getId()).setUploadStatus(saved.getUploadStatus());
        } catch (RuntimeException ex) {
            YbInvUpload failed = uploadDomainService.upsertUploadRecord(
                existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_PURCHASE_RETURN, line, ChsInventoryConstants.SOURCE_TYPE_INBOUND,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_FAILED,
                ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED, ex.getMessage(), requestJson, null, operatorId, tenantId, true);
            return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
                .setPlatformMsg(ex.getMessage());
        }
    }

    private PlatformUploadOutcome invokeSalePlatform(String contractBusNo, String infno, Object input) {
        String requestJson = JSON.toJSONString(input);
        try {
            ResultBody body;
            if (ChsInfNo.INVENTORY_SALE.equals(infno)) {
                body = inv3505Invoker.invoke(
                    contractBusNo, (Inv3505Input) input, invPlatformSupport.buildInvokeContext());
            } else {
                body = inv3506Invoker.invoke(
                    contractBusNo, (Inv3506Input) input, invPlatformSupport.buildInvokeContext());
            }
            JSONObject output = ChsGatewayOutputSupport.parseOutputObject(body);
            JSONObject platformResult = output.getJSONObject("result");
            if (platformResult == null) {
                platformResult = output;
            }
            String retRslt = InvPlatformOutputParser.readSaleUploadField(platformResult, "retRslt", "ret_rslt");
            String msgRslt = InvPlatformOutputParser.readSaleUploadField(platformResult, "msgRslt", "msg_rslt");
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

    private boolean traceCountMatchesQuantity(InvUploadLineCommand line) {
        if (line.getSelRetnCnt() == null) {
            return false;
        }
        int expected = line.getSelRetnCnt().intValue();
        int actual = 0;
        if (line.getDrugTracCodgs() != null) {
            for (String code : line.getDrugTracCodgs()) {
                if (isNotEmpty(code)) {
                    actual++;
                }
            }
        }
        return actual == expected;
    }
}
