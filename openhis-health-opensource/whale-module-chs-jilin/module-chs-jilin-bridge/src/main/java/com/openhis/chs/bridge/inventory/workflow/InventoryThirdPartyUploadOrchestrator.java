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
import com.openhis.chs.bridge.inventory.support.InventoryUploadTraceLineSupport;
import com.openhis.chs.domain.catalog.access.ChsInsuranceContractReader;
import com.openhis.chs.domain.contract.ChsContractSnapshot;
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
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.gateway.config.ContractConfig;
import com.openhis.chs.gateway.config.ContractConfigLoader;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.inventory.model.Inv3501Input;
import com.openhis.chs.gateway.scheme.inventory.model.Inv3502Input;
import com.openhis.chs.gateway.scheme.inventory.model.Inv3503Input;
import com.openhis.chs.gateway.scheme.inventory.model.Inv3504Input;
import com.openhis.chs.gateway.scheme.inventory.model.Inv3505Input;
import com.openhis.chs.gateway.scheme.inventory.model.Inv3506Input;
import com.openhis.chs.gateway.scheme.inventory.assembler.InvPlatformInputAssembler;
import com.openhis.chs.gateway.session.ChsSignCredentialResolver;
import com.openhis.chs.gateway.support.ChsCurrentOperatorResolver;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 【3501–3506】进销存上报编排器（Extension 专用，非 Facade）。
 *
 * <p>逐行调用对应 Invoker，管理幂等（已成功跳过）、无医保编码跳过、追溯码数量校验、
 * 上报审计落库（{@code yb_inv_upload}）及追溯码子表持久化。</p>
 *
 * <p>3505/3506 额外解析平台 output.result.retRslt 业务结果码。</p>
 */
@Component
@RequiredArgsConstructor
public class InventoryThirdPartyUploadOrchestrator {

        private final Inv3501Invoker inv3501Invoker;
    private final Inv3502Invoker inv3502Invoker;
    private final Inv3503Invoker inv3503Invoker;
    private final Inv3504Invoker inv3504Invoker;
    private final Inv3505Invoker inv3505Invoker;
    private final Inv3506Invoker inv3506Invoker;
    private final ChsInsuranceContractReader contractReader;
    private final ChsCurrentOperatorResolver currentOperatorResolver;
    private final ChsSignCredentialResolver signCredentialResolver;
    private final YbInvUploadDomainService uploadDomainService;
    private final ChsInventoryProperties inventoryProperties;
    private final InventoryUploadTraceLineSupport uploadTraceLineSupport;

    /**
     * 解析平台响应 output 为 JSON 对象。
     *
     * @param body 平台调用结果
     * @return output JSON，为空时返回空对象
     */
    private static JSONObject parseOutputObject(ResultBody body) {
        if (body == null || isEmpty(body.getOutput())) {
            return new JSONObject();
        }
        return JSON.parseObject(body.getOutput());
    }

    /**
     * 【3505】商品销售上报：逐行调用平台接口。
     *
     * @param request 契约、来源单据（就诊）、销售行
     */
    public void uploadSale3505(InvUpload3505Command request) {
        // Pre：契约、租户、操作人
        ContractContext ctx = resolveContract(request.getContractBusNo(), request.getContractId());
        Long tenantId = TenantUtils.getCurrentTenantId();
        Long operatorId = currentOperatorResolver.requireOperatorId("请先登录");
        if (request.getLines() == null) {
            return;
        }
        // Cmd：组装 3505 上报数据、追溯校验、平台 invoke
        for (InvUploadLineCommand line : request.getLines()) {
            processSaleLine3505(request, line, ctx, tenantId, operatorId);
        }
    }

    /**
     * 【3506】商品销售退货上报：逐行调用平台接口。
     *
     * @param request 契约、来源单据、退货行
     * @return 各行 uploadId、uploadStatus 及平台 retRslt/msgRslt
     */
    public void uploadSaleReturn3506(InvUpload3506Command request) {
        // Pre：契约、租户、操作人
        ContractContext ctx = resolveContract(request.getContractBusNo(), request.getContractId());
        Long tenantId = TenantUtils.getCurrentTenantId();
        Long operatorId = currentOperatorResolver.requireOperatorId("请先登录");
        if (request.getLines() == null) {
            return;
        }
        // Cmd：组装 3506 上报
        for (InvUploadLineCommand line : request.getLines()) {
            processReturnLine3506(request, line, ctx, tenantId, operatorId);
        }
    }

    /**
     * 【3503】商品采购入库上报：逐行调用平台接口。
     *
     * @param request 契约、来源单据、采购行
     * @return 各行 uploadId、uploadStatus
     */
    public void uploadPurchase3503(InvUpload3503Command request) {
        // Pre：契约、租户、操作人
        ContractContext ctx = resolveContract(request.getContractBusNo(), request.getContractId());
        Long tenantId = TenantUtils.getCurrentTenantId();
        Long operatorId = currentOperatorResolver.requireOperatorId("请先登录");
        if (request.getLines() == null) {
            return;
        }
        // Cmd：组装 3503 上报
        for (InvUploadLineCommand line : request.getLines()) {
            processPurchaseLine3503(request, line, ctx, tenantId, operatorId);
        }
    }

    /**
     * 【3504】商品采购退货上报：逐行调用平台接口。
     *
     * @param request 契约、来源单据、采购退货行
     * @return 各行 uploadId、uploadStatus
     */
    public void uploadPurchaseReturn3504(InvUpload3504Command request) {
        // Pre：契约、租户、操作人
        ContractContext ctx = resolveContract(request.getContractBusNo(), request.getContractId());
        Long tenantId = TenantUtils.getCurrentTenantId();
        Long operatorId = currentOperatorResolver.requireOperatorId("请先登录");
        if (request.getLines() == null) {
            return;
        }
        // Cmd：组装 3504 上报
        for (InvUploadLineCommand line : request.getLines()) {
            processPurchaseReturnLine3504(request, line, ctx, tenantId, operatorId);
        }
    }

    /**
     * 【3501】商品盘存上报：逐行调用平台接口。
     *
     * @param request 契约、来源单据、盘存行
     * @return 各行 uploadId、uploadStatus
     */
    public void uploadStocktake3501(InvUpload3501Command request) {
        // Pre：契约、租户、操作人
        ContractContext ctx = resolveContract(request.getContractBusNo(), request.getContractId());
        Long tenantId = TenantUtils.getCurrentTenantId();
        Long operatorId = currentOperatorResolver.requireOperatorId("请先登录");
        if (request.getLines() == null) {
            return;
        }
        // Cmd：组装 3501 盘存上报，sourceType=STOCKTAKE
        for (InvUploadLineCommand line : request.getLines()) {
            processStocktakeLine3501(
                request, line, ctx, tenantId, operatorId, ChsInventoryConstants.SOURCE_TYPE_STOCKTAKE);
        }
    }

    /**
     * 【3502】商品库存变更上报：逐行调用平台接口并保存追溯码。
     *
     * @param request 契约、来源单据、sourceType（缺省 MANUAL）
     * @return 各行 uploadId、uploadStatus、invChgType
     */
    public void uploadInvChange3502(InvUpload3502Command request) {
        // Pre：契约、租户、操作人，sourceType 缺省为 MANUAL
        ContractContext ctx = resolveContract(request.getContractBusNo(), request.getContractId());
        Long tenantId = TenantUtils.getCurrentTenantId();
        Long operatorId = currentOperatorResolver.requireOperatorId("请先登录");
        String sourceType;
        if (isNotEmpty(request.getSourceType())) {
            sourceType = request.getSourceType().trim();
        } else {
            sourceType = ChsInventoryConstants.SOURCE_TYPE_MANUAL;
        }
        if (request.getLines() == null) {
            return;
        }
        // Cmd：组装 3502 库存变更上报
        for (InvUploadLineCommand line : request.getLines()) {
            processInvChangeLine3502(request, line, ctx, tenantId, operatorId, sourceType);
        }
    }

    /**
     * 处理单行 3505 商品销售上报。
     *
     * @param request    3505 上报命令
     * @param line       销售行
     * @param ctx        契约上下文
     * @param tenantId   租户 ID
     * @param operatorId 操作人 ID
     * @return 单行上报结果
     */
    private InvUpload3505Result processSaleLine3505(
        InvUpload3505Command request,
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

        String traceFailureMessage = uploadTraceLineSupport.resolveUploadTraceFailureMessage(line, true);
        if (isNotEmpty(traceFailureMessage)) {
            YbInvUpload failed = uploadDomainService.upsertUploadRecord(
                existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_SALE, line, ChsInventoryConstants.SOURCE_TYPE_DISPENSE, request.getSourceId(),
                ChsInventoryConstants.UPLOAD_STATUS_FAILED,
                ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED, traceFailureMessage, null, null, operatorId, tenantId, true);
            return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
                .setPlatformRetRslt(ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED).setPlatformMsg(failed.getPlatformMsg());
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

    /**
     * 处理单行 3506 商品销售退货上报。
     *
     * @param request    3506 上报命令
     * @param line       退货行
     * @param ctx        契约上下文
     * @param tenantId   租户 ID
     * @param operatorId 操作人 ID
     * @return 单行上报结果
     */
    private InvUpload3506Result processReturnLine3506(
        InvUpload3506Command request,
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

        String traceFailureMessage = uploadTraceLineSupport.resolveUploadTraceFailureMessage(line, true);
        if (isNotEmpty(traceFailureMessage)) {
            YbInvUpload failed = uploadDomainService.upsertUploadRecord(
                existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_SALE_RETURN, line, ChsInventoryConstants.SOURCE_TYPE_DISPENSE, request.getSourceId(),
                ChsInventoryConstants.UPLOAD_STATUS_FAILED,
                ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED, traceFailureMessage, null, null, operatorId, tenantId, true);
            return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
                .setPlatformRetRslt(ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED).setPlatformMsg(failed.getPlatformMsg());
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

    /**
     * 处理单行 3503 商品采购入库上报。
     *
     * @param request    3503 上报命令
     * @param line       采购行
     * @param ctx        契约上下文
     * @param tenantId   租户 ID
     * @param operatorId 操作人 ID
     * @return 单行上报结果
     */
    private InvUpload3503Result processPurchaseLine3503(
        InvUpload3503Command request,
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

        String traceFailureMessage = uploadTraceLineSupport.resolveUploadTraceFailureMessage(line, false);
        if (isNotEmpty(traceFailureMessage)) {
            YbInvUpload failed = uploadDomainService.upsertUploadRecord(
                existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_PURCHASE, line, ChsInventoryConstants.SOURCE_TYPE_INBOUND,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_FAILED,
                ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED, traceFailureMessage, null, null, operatorId, tenantId, true);
            return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
                .setPlatformMsg(failed.getPlatformMsg());
        }

        Inv3503Input input = InvPlatformInputAssembler.assemble3503(line);
        String requestJson = JSON.toJSONString(input);
        try {
            ResultBody body = inv3503Invoker.invoke(ctx.busNo(), input, buildInvokeContext());
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

    /**
     * 处理单行 3501 商品盘存上报。
     *
     * @param request    3501 上报命令
     * @param line       盘存行
     * @param ctx        契约上下文
     * @param tenantId   租户 ID
     * @param operatorId 操作人 ID
     * @param sourceType 来源类型
     * @return 单行上报结果
     */
    private InvUpload3501Result processStocktakeLine3501(
        InvUpload3501Command request,
        InvUploadLineCommand line,
        ContractContext ctx,
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

        String traceFailureMessage = uploadTraceLineSupport.resolveUploadTraceFailureMessage(line, false);
        if (isNotEmpty(traceFailureMessage)) {
            YbInvUpload failed = uploadDomainService.upsertUploadRecord(
                existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_STOCKTAKE, line, sourceType,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_FAILED,
                ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED, traceFailureMessage, null, null, operatorId, tenantId, true);
            return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
                .setPlatformMsg(failed.getPlatformMsg());
        }

        Inv3501Input input = InvPlatformInputAssembler.assemble3501(line);
        String requestJson = JSON.toJSONString(input);
        try {
            ResultBody body = inv3501Invoker.invoke(ctx.busNo(), input, buildInvokeContext());
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

    /**
     * 处理单行 3502 商品库存变更上报。
     *
     * @param request    3502 上报命令
     * @param line       库存变更行
     * @param ctx        契约上下文
     * @param tenantId   租户 ID
     * @param operatorId 操作人 ID
     * @param sourceType 来源类型
     * @return 单行上报结果
     */
    private InvUpload3502Result processInvChangeLine3502(
        InvUpload3502Command request,
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

        String traceFailureMessage = uploadTraceLineSupport.resolveUploadTraceFailureMessage(line, false);
        if (isNotEmpty(traceFailureMessage)) {
            YbInvUpload failed = uploadDomainService.upsertUploadRecord(
                existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_CHANGE, line, sourceType,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_FAILED,
                ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED, traceFailureMessage, null, null, operatorId, tenantId, true);
            return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
                .setPlatformMsg(failed.getPlatformMsg());
        }

        Inv3502Input input = InvPlatformInputAssembler.assemble3502(line);
        String requestJson = JSON.toJSONString(input);
        try {
            ResultBody body = inv3502Invoker.invoke(ctx.busNo(), input, buildInvokeContext());
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

    /**
     * 处理单行 3504 商品采购退货上报。
     *
     * @param request    3504 上报命令
     * @param line       采购退货行
     * @param ctx        契约上下文
     * @param tenantId   租户 ID
     * @param operatorId 操作人 ID
     * @return 单行上报结果
     */
    private InvUpload3504Result processPurchaseReturnLine3504(
        InvUpload3504Command request,
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

        String traceFailureMessage = uploadTraceLineSupport.resolveUploadTraceFailureMessage(line, false);
        if (isNotEmpty(traceFailureMessage)) {
            YbInvUpload failed = uploadDomainService.upsertUploadRecord(
                existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_PURCHASE_RETURN, line, ChsInventoryConstants.SOURCE_TYPE_INBOUND,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_FAILED,
                ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED, traceFailureMessage, null, null, operatorId, tenantId, true);
            return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
                .setPlatformMsg(failed.getPlatformMsg());
        }

        Inv3504Input input = InvPlatformInputAssembler.assemble3504(line);
        String requestJson = JSON.toJSONString(input);
        try {
            ResultBody body = inv3504Invoker.invoke(ctx.busNo(), input, buildInvokeContext());
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

    /**
     * 调用 3505/3506 销售类平台接口并解析业务结果码。
     *
     * @param contractBusNo 契约业务编号
     * @param infno         接口编号
     * @param input         平台入参
     * @return 平台上报结果
     */
    private PlatformUploadOutcome invokeSalePlatform(String contractBusNo, String infno, Object input) {
        String requestJson = JSON.toJSONString(input);
        try {
            ResultBody body;
            if (ChsInfNo.INVENTORY_SALE.equals(infno)) {
                body = inv3505Invoker.invoke(contractBusNo, (Inv3505Input) input, buildInvokeContext());
            } else {
                body = inv3506Invoker.invoke(contractBusNo, (Inv3506Input) input, buildInvokeContext());
            }
            JSONObject output = parseOutputObject(body);
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

    /** 平台上报调用结果（状态、业务码、报文及错误信息）。 */
    private record PlatformUploadOutcome(
        boolean invokeOk,
        String uploadStatus,
        String retRslt,
        String msgRslt,
        String requestJson,
        String responseJson,
        String errorMessage
    ) {
        /**
         * 构建成功的平台上报结果。
         *
         * @param uploadStatus  上报状态
         * @param retRslt       平台业务结果码
         * @param msgRslt       平台业务消息
         * @param requestJson   请求报文
         * @param responseJson  响应报文
         * @return 成功结果
         */
        static PlatformUploadOutcome success(
            String uploadStatus,
            String retRslt,
            String msgRslt,
            String requestJson,
            String responseJson
        ) {
            return new PlatformUploadOutcome(true, uploadStatus, retRslt, msgRslt, requestJson, responseJson, null);
        }

        /**
         * 构建失败的平台上报结果。
         *
         * @param errorMessage 错误消息
         * @param requestJson  请求报文
         * @return 失败结果
         */
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

    /**
     * 判断接口是否需要解析平台 retRslt 业务结果码。
     *
     * @param infno 接口编号
     * @return 3505/3506 时返回 true
     */
    private static boolean requiresPlatformRetRslt(String infno) {
        return ChsInfNo.INVENTORY_SALE.equals(infno) || ChsInfNo.INVENTORY_SALE_RETURN.equals(infno);
    }

    /**
     * 从平台结果 JSON 读取字段（兼容驼峰与下划线命名）。
     *
     * @param platformResult 平台结果 JSON
     * @param camelKey       驼峰字段名
     * @param snakeKey       下划线字段名
     * @return 字段值
     */
    private static String readPlatformField(JSONObject platformResult, String camelKey, String snakeKey) {
        if (platformResult == null) {
            return null;
        }
        String value = platformResult.getString(camelKey);
        if (isEmpty(value)) {
            value = platformResult.getString(snakeKey);
        }
        return value;
    }

    /**
     * 解析契约上下文（契约快照、业务编号及配置）。
     *
     * @param contractBusNo 契约业务编号
     * @param contractId    契约 ID
     * @return 契约上下文
     */
    private ContractContext resolveContract(String contractBusNo, Long contractId) {
        ChsContractSnapshot contract = contractReader.requireContract(contractBusNo, contractId);
        String busNo = contractReader.requireContractBusNo(contract);
        ContractConfig config = ContractConfigLoader.getByBusNo(busNo);
        return new ContractContext(contract, busNo, config);
    }

    /**
     * 构建平台调用上下文（操作人 ID 与姓名）。
     *
     * @return 调用上下文
     */
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

    /** 契约上下文（契约快照、业务编号、契约配置）。 */
    private record ContractContext(ChsContractSnapshot contract, String busNo, ContractConfig config) {
    }
}
