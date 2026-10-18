/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.inventory.workflow;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.chs.bridge.inventory.assembler.InventoryDrugTracChunkAssembler;
import com.zxplat.plugin.chs.bridge.inventory.assembler.InventoryPurchaseCompanion3502LineAssembler;
import com.zxplat.plugin.chs.bridge.inventory.assembler.InventorySaleCompanion3502LineAssembler;
import com.zxplat.plugin.chs.bridge.inventory.assembler.InventoryUploadBatchNoAssembler;
import com.zxplat.plugin.chs.bridge.inventory.invoker.Inv3501Invoker;
import com.zxplat.plugin.chs.bridge.inventory.invoker.Inv3502Invoker;
import com.zxplat.plugin.chs.bridge.inventory.invoker.Inv3503Invoker;
import com.zxplat.plugin.chs.bridge.inventory.invoker.Inv3504Invoker;
import com.zxplat.plugin.chs.bridge.inventory.invoker.Inv3505Invoker;
import com.zxplat.plugin.chs.bridge.inventory.invoker.Inv3506Invoker;
import com.zxplat.plugin.chs.bridge.inventory.support.InventoryInboundDrugTracPlatformVerifySupport;
import com.zxplat.plugin.chs.bridge.inventory.support.InventorySalePaired3502ContractSupport;
import com.zxplat.plugin.chs.bridge.inventory.support.InventoryUploadSaleRequiredFieldSupport;
import com.zxplat.plugin.chs.bridge.inventory.support.InventoryUploadTraceLineSupport;
import com.zxplat.plugin.chs.domain.catalog.access.ChsInsuranceContractReader;
import com.zxplat.plugin.chs.domain.contract.ChsContractSnapshot;
import com.zxplat.plugin.chs.domain.share.constants.ChsInventoryConstants;
import com.zxplat.plugin.chs.domain.inventory.config.ChsInventoryProperties;
import com.zxplat.plugin.chs.domain.inventory.command.InvUploadLineCommand;
import com.zxplat.plugin.chs.domain.inventory.command.InvUpload3502Command;
import com.zxplat.plugin.chs.domain.inventory.command.InvUpload3503Command;
import com.zxplat.plugin.chs.domain.inventory.command.InvUpload3504Command;
import com.zxplat.plugin.chs.domain.inventory.command.InvUpload3505Command;
import com.zxplat.plugin.chs.domain.inventory.command.InvUpload3506Command;
import com.zxplat.plugin.chs.domain.inventory.command.InvUpload3501Command;
import com.zxplat.plugin.chs.domain.inventory.result.InvUpload3501Result;
import com.zxplat.plugin.chs.domain.inventory.result.InvUpload3502Result;
import com.zxplat.plugin.chs.domain.inventory.result.InvUpload3503Result;
import com.zxplat.plugin.chs.domain.inventory.result.InvUpload3504Result;
import com.zxplat.plugin.chs.domain.inventory.result.InvUpload3505Result;
import com.zxplat.plugin.chs.domain.inventory.result.InvUpload3506Result;
import com.zxplat.plugin.chs.domain.inventory.entity.YbInvUpload;
import com.zxplat.plugin.chs.domain.inventory.service.YbInvUploadDomainService;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.gateway.config.ContractConfig;
import com.zxplat.plugin.chs.gateway.config.ContractConfigLoader;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.gateway.scheme.inventory.model.Inv3501Input;
import com.zxplat.plugin.chs.gateway.scheme.inventory.model.Inv3502Input;
import com.zxplat.plugin.chs.gateway.scheme.inventory.model.Inv3503Input;
import com.zxplat.plugin.chs.gateway.scheme.inventory.model.Inv3504Input;
import com.zxplat.plugin.chs.gateway.scheme.inventory.model.Inv3505Input;
import com.zxplat.plugin.chs.gateway.scheme.inventory.model.Inv3506Input;
import com.zxplat.plugin.chs.gateway.scheme.inventory.assembler.InvPlatformInputAssembler;
import com.zxplat.plugin.chs.gateway.scheme.inventory.parser.InvPlatformOutputParser;
import com.zxplat.plugin.chs.gateway.session.ChsSignCredentialResolver;
import com.zxplat.plugin.chs.gateway.support.ChsCurrentOperatorResolver;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import com.zxplat.domain.financial.contract.entity.Contract;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 【3501–3506】进销存上报编排器（Extension 专用，非 Facade）。
 *
 * <p>逐行调用对应 Invoker，管理幂等（已成功跳过）、无医保编码跳过、追溯码数量校验、
 * 上报审计落库（{@code yb_inv_upload}）及追溯码子表持久化。</p>
 *
 * <p>3505/3506 解析平台业务结果：有 {@code ret_rslt} 按码判定；省本级空 output 无码时，
 * 在协议成功（{@code infcode=0}）下落 SUCCESS。</p>
 *
 * <p><b>采购伴生</b>：3503/3504 平台 SUCCESS（含幂等）后跟 3502（108 正 / 109 负）；
 * 伴生失败仅落 3502 FAILED，不回写采购侧。</p>
 * <p><b>销售配对</b>：3505 前先试 3502（107 负），再调 3505；3502 失败仍继续 3505（两条互不阻塞）。
 * 3506 SUCCESS 后再 3502（106 正，批次=退药单号）；伴生失败不回写 3506 SUCCESS。</p>
 *
 * <p><b>追溯码子表</b>：平台 SUCCESS 后写 {@code yb_inv_upload_trace}；子表唯一冲突或其它异常
 * <strong>不得</strong>回滚已成功的 {@code yb_inv_upload}（否则退药有码时伴生 106 整笔消失，再发 107→191）。</p>
 * <p><b>入库落码核验</b>：入库类 3502 平台 SUCCESS 且本片有码时，3512 按批次核验；未落全则改记 FAILED
 * （BR-CHS-INV-014），禁止把静默丢码当成成功。</p>
 * <p><b>销售配对契约</b>：3505/3506 跟结算平台（含工伤）；配对 3502 跟码的入库平台（市入市出、省入省出，BR-015）。</p>
 */
@Slf4j
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
    private final InventoryInboundDrugTracPlatformVerifySupport inboundDrugTracPlatformVerifySupport;
    private final InventorySalePaired3502ContractSupport salePaired3502ContractSupport;

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
        // Pre：契约、租户、操作人（异步路径命令已带 operatorId，对齐发票快照）
        ContractContext ctx = resolveContract(request.getContractBusNo(), request.getContractId());
        Long tenantId = TenantUtils.getCurrentTenantId();
        Long operatorId = resolveOperatorId(request.getOperatorId());
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
        Long operatorId = resolveOperatorId(request.getOperatorId());
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
        Long operatorId = resolveOperatorId(request.getOperatorId());
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
        Long operatorId = resolveOperatorId(request.getOperatorId());
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
        Long operatorId = resolveOperatorId(request.getOperatorId());
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
        Long operatorId = resolveOperatorId(request.getOperatorId());
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
     * 处理单行 3505 商品销售上报：校验通过后先试 3502(107)，再调 3505；3502 成败不影响 3505。
     *
     * @param request    3505 上报命令
     * @param line       销售行
     * @param ctx        契约上下文
     * @param tenantId   租户 ID
     * @param operatorId 操作人 ID
     * @return 单行 3505 上报结果（不含前置 3502 状态）
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
        String sourceType = this.resolveSaleSourceType(request.getSourceType());

        // Pre：未对码不调平台，仅记 SKIPPED（不落 request_json）
        if (inventoryProperties.isSkipWithoutYbNo()
            && (ChsInventoryConstants.TRACE_POLICY_NO_YB_NO.equals(line.getTracePolicy())
                || isEmpty(line.getMedListCodg()))) {
            YbInvUpload skipped = uploadDomainService.upsertUploadRecord(
                null, ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_SALE, line, sourceType, request.getSourceId(),
                ChsInventoryConstants.UPLOAD_STATUS_SKIPPED_NO_YB_NO,
                null, null, null, null, operatorId, tenantId, false);
            return result.setUploadId(skipped.getId()).setUploadStatus(skipped.getUploadStatus());
        }

        Optional<YbInvUpload> existing = uploadDomainService.findExistingLine(
            tenantId, ChsInfNo.INVENTORY_SALE, line.getFixmedinsBchno(), line.getSourceLineId());
        // 幂等：3505 已成功则不再调 3502 / 3505
        if (existing.isPresent()
            && ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(existing.get().getUploadStatus())) {
            YbInvUpload row = existing.get();
            return result.setUploadId(row.getId()).setUploadStatus(row.getUploadStatus())
                .setPlatformRetRslt(row.getPlatformRetRslt()).setPlatformMsg(row.getPlatformMsg());
        }

        String traceFailureMessage = uploadTraceLineSupport.resolveUploadTraceFailureMessage(line, true, null, null);
        if (isNotEmpty(traceFailureMessage)) {
            // 出站前失败也落草稿，运维台可回显编辑后补传（此时尚未调 3502）
            String draftRequestJson = tryAssembleRequestJson(() -> InvPlatformInputAssembler.assemble3505(line));
            YbInvUpload failed = uploadDomainService.upsertUploadRecord(
                existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_SALE, line, sourceType, request.getSourceId(),
                ChsInventoryConstants.UPLOAD_STATUS_FAILED,
                ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED, traceFailureMessage, draftRequestJson, null, operatorId, tenantId, true);
            return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
                .setPlatformRetRslt(ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED).setPlatformMsg(failed.getPlatformMsg());
        }
        // 规范必填项：缺则记失败（主数据应齐全）；尚未调 3502
        String requiredFailureMessage = InventoryUploadSaleRequiredFieldSupport.resolveMissingRequiredMessage(line, true);
        if (isNotEmpty(requiredFailureMessage)) {
            String draftRequestJson = tryAssembleRequestJson(() -> InvPlatformInputAssembler.assemble3505(line));
            YbInvUpload failed = uploadDomainService.upsertUploadRecord(
                existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_SALE, line, sourceType, request.getSourceId(),
                ChsInventoryConstants.UPLOAD_STATUS_FAILED,
                ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED, requiredFailureMessage, draftRequestJson, null, operatorId, tenantId, true);
            return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
                .setPlatformRetRslt(ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED).setPlatformMsg(failed.getPlatformMsg());
        }

        // Cmd：先试 3502 其他出库(107)；失败仅落 3502 审计，不阻塞后续 3505
        uploadPrecedingSaleOutbound3502(request, line, ctx, tenantId, operatorId, sourceType);

        // Cmd：无论前置 3502 成败，均出站 3505
        Inv3505Input input = InvPlatformInputAssembler.assemble3505(line);
        PlatformUploadOutcome outcome = invokeSalePlatform(
            ctx.busNo(), ChsInfNo.INVENTORY_SALE, input, operatorId, request.getOperatorName());
        if (outcome.invokeOk()) {
            YbInvUpload saved = uploadDomainService.upsertUploadRecord(
                existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_SALE, line, sourceType, request.getSourceId(),
                outcome.uploadStatus(), outcome.retRslt(), outcome.msgRslt(), outcome.requestJson(), outcome.responseJson(),
                operatorId, tenantId, ChsInventoryConstants.UPLOAD_STATUS_FAILED.equals(outcome.uploadStatus()));
            // 子表失败不回滚 3505 SUCCESS
            persistTraceRowsSafely(saved, line, tenantId);
            return result.setUploadId(saved.getId()).setUploadStatus(saved.getUploadStatus())
                .setPlatformRetRslt(outcome.retRslt()).setPlatformMsg(outcome.msgRslt());
        }
        // 3505 失败：不回写前置 3502 SUCCESS
        YbInvUpload failed = uploadDomainService.upsertUploadRecord(
            existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_SALE, line, sourceType, request.getSourceId(),
            ChsInventoryConstants.UPLOAD_STATUS_FAILED,
            outcome.retRslt(), outcome.errorMessage(), outcome.requestJson(), null, operatorId, tenantId, true);
        return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
            .setPlatformRetRslt(outcome.retRslt()).setPlatformMsg(outcome.errorMessage());
    }

    /**
     * 处理单行 3506 商品销售退货上报：SUCCESS（含幂等）后再伴生 3502(106，批次=退药单号)。
     *
     * @param request    3506 上报命令
     * @param line       退货行
     * @param ctx        契约上下文
     * @param tenantId   租户 ID
     * @param operatorId 操作人 ID
     * @return 单行 3506 上报结果（不含伴生 3502 状态）
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
        String sourceType = this.resolveSaleSourceType(request.getSourceType());

        // Pre：未对码不调平台，仅记 SKIPPED（不落 request_json）
        if (inventoryProperties.isSkipWithoutYbNo()
            && (ChsInventoryConstants.TRACE_POLICY_NO_YB_NO.equals(line.getTracePolicy())
                || isEmpty(line.getMedListCodg()))) {
            YbInvUpload skipped = uploadDomainService.upsertUploadRecord(
                null, ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_SALE_RETURN, line, sourceType, request.getSourceId(),
                ChsInventoryConstants.UPLOAD_STATUS_SKIPPED_NO_YB_NO,
                null, null, null, null, operatorId, tenantId, false);
            return result.setUploadId(skipped.getId()).setUploadStatus(skipped.getUploadStatus());
        }

        Optional<YbInvUpload> existing = uploadDomainService.findExistingLine(
            tenantId, ChsInfNo.INVENTORY_SALE_RETURN, line.getFixmedinsBchno(), line.getSourceLineId());
        // 幂等：3506 已成功仍尝试伴生 3502（3502 自身再幂等）
        if (existing.isPresent()
            && ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(existing.get().getUploadStatus())) {
            YbInvUpload row = existing.get();
            // 幂等短路时仍尝试标删配对 3505（兼容上次冲销成功但未标删）
            this.markClearBedSummarySaleDeletedIfNeeded(tenantId, sourceType, row, line);
            followUpCompanion3502AfterSaleReturn(request, line, ctx, tenantId, operatorId, sourceType);
            return result.setUploadId(row.getId()).setUploadStatus(row.getUploadStatus())
                .setPlatformRetRslt(row.getPlatformRetRslt()).setPlatformMsg(row.getPlatformMsg());
        }

        String traceFailureMessage = uploadTraceLineSupport.resolveUploadTraceFailureMessage(line, true, null, null);
        if (isNotEmpty(traceFailureMessage)) {
            // 出站前失败也落草稿，运维台可回显编辑后补传
            String draftRequestJson = tryAssembleRequestJson(() -> InvPlatformInputAssembler.assemble3506(line));
            YbInvUpload failed = uploadDomainService.upsertUploadRecord(
                existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_SALE_RETURN, line, sourceType, request.getSourceId(),
                ChsInventoryConstants.UPLOAD_STATUS_FAILED,
                ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED, traceFailureMessage, draftRequestJson, null, operatorId, tenantId, true);
            return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
                .setPlatformRetRslt(ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED).setPlatformMsg(failed.getPlatformMsg());
        }
        String requiredFailureMessage = InventoryUploadSaleRequiredFieldSupport.resolveMissingRequiredMessage(line, false);
        if (isNotEmpty(requiredFailureMessage)) {
            String draftRequestJson = tryAssembleRequestJson(() -> InvPlatformInputAssembler.assemble3506(line));
            YbInvUpload failed = uploadDomainService.upsertUploadRecord(
                existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_SALE_RETURN, line, sourceType, request.getSourceId(),
                ChsInventoryConstants.UPLOAD_STATUS_FAILED,
                ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED, requiredFailureMessage, draftRequestJson, null, operatorId, tenantId, true);
            return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
                .setPlatformRetRslt(ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED).setPlatformMsg(failed.getPlatformMsg());
        }

        Inv3506Input input = InvPlatformInputAssembler.assemble3506(line);
        PlatformUploadOutcome outcome = invokeSalePlatform(
            ctx.busNo(), ChsInfNo.INVENTORY_SALE_RETURN, input, operatorId, request.getOperatorName());
        if (outcome.invokeOk()) {
            YbInvUpload saved = uploadDomainService.upsertUploadRecord(
                existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_SALE_RETURN, line, sourceType, request.getSourceId(),
                outcome.uploadStatus(), outcome.retRslt(), outcome.msgRslt(), outcome.requestJson(), outcome.responseJson(),
                operatorId, tenantId, ChsInventoryConstants.UPLOAD_STATUS_FAILED.equals(outcome.uploadStatus()));
            // 子表失败不回滚 3506 SUCCESS，否则后续伴生 106 可能整笔消失
            persistTraceRowsSafely(saved, line, tenantId);
            // 清床汇总冲销成功：配对 3505 标 DELETED，便于再清床重报净量
            this.markClearBedSummarySaleDeletedIfNeeded(tenantId, sourceType, saved, line);
            result.setUploadId(saved.getId()).setUploadStatus(saved.getUploadStatus())
                .setPlatformRetRslt(outcome.retRslt()).setPlatformMsg(outcome.msgRslt());
            // Cmd：仅业务 SUCCESS 后伴生 3502；失败不回写 3506
            if (ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(saved.getUploadStatus())) {
                followUpCompanion3502AfterSaleReturn(request, line, ctx, tenantId, operatorId, sourceType);
            }
            return result;
        }
        YbInvUpload failed = uploadDomainService.upsertUploadRecord(
            existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_SALE_RETURN, line, sourceType, request.getSourceId(),
            ChsInventoryConstants.UPLOAD_STATUS_FAILED,
            outcome.retRslt(), outcome.errorMessage(), outcome.requestJson(), null, operatorId, tenantId, true);
        return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
            .setPlatformRetRslt(outcome.retRslt()).setPlatformMsg(outcome.errorMessage());
    }

    /**
     * 清床汇总 3506 成功时，将配对 SUCCESS 3505 标为 DELETED。
     *
     * @param tenantId   租户
     * @param sourceType 来源类型
     * @param saved      已落库 3506
     * @param line       退货行
     */
    private void markClearBedSummarySaleDeletedIfNeeded(
        Long tenantId,
        String sourceType,
        YbInvUpload saved,
        InvUploadLineCommand line
    ) {
        if (saved == null || line == null) {
            return;
        }
        if (!ChsInventoryConstants.SOURCE_TYPE_CLEAR_BED_SUMMARY.equals(sourceType)) {
            return;
        }
        if (!ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(saved.getUploadStatus())) {
            return;
        }
        // 配对 3505：用原销售批次（fixmedinsBchno）；originalFixmedinsBchno 是退药单号，仅给伴生 3502
        if (isEmpty(line.getFixmedinsBchno())) {
            return;
        }
        uploadDomainService.markClearBedSummarySaleDeletedAfterReverse(
            tenantId, line.getFixmedinsBchno());
    }

    /**
     * 解析 3505/3506 本地来源类型；空则回落发药核发。
     *
     * @param sourceType 命令携带的来源类型
     * @return 非空来源类型
     */
    private String resolveSaleSourceType(String sourceType) {
        if (isNotEmpty(sourceType)) {
            return sourceType.trim();
        }
        return ChsInventoryConstants.SOURCE_TYPE_DISPENSE;
    }

    /**
     * 处理单行 3503 商品采购入库上报；平台 SUCCESS（含幂等）后伴生 3502（108）。
     *
     * @param request    3503 上报命令
     * @param line       采购行
     * @param ctx        契约上下文
     * @param tenantId   租户 ID
     * @param operatorId 操作人 ID
     * @return 单行 3503 上报结果（不含伴生 3502 状态）
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

        // Pre：未对码不调平台，仅记 SKIPPED（不落 request_json）
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
        // 幂等：3503 已成功仍尝试伴生 3502（3502 自身再幂等，便于补传失败伴生）
        if (existing.isPresent()
            && ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(existing.get().getUploadStatus())) {
            YbInvUpload row = existing.get();
            followUpCompanion3502AfterPurchase(
                request.getContractBusNo(),
                request.getContractId(),
                request.getSourceId(),
                request.getOperatorName(),
                InventoryPurchaseCompanion3502LineAssembler.fromPurchaseInbound(line),
                ctx,
                tenantId,
                operatorId);
            return result.setUploadId(row.getId()).setUploadStatus(row.getUploadStatus())
                .setPlatformMsg(row.getPlatformMsg());
        }

        String traceFailureMessage = uploadTraceLineSupport.resolveUploadTraceFailureMessage(line, false, null, null);
        if (isNotEmpty(traceFailureMessage)) {
            String draftRequestJson = tryAssembleRequestJson(() -> InvPlatformInputAssembler.assemble3503(line));
            YbInvUpload failed = uploadDomainService.upsertUploadRecord(
                existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_PURCHASE, line, ChsInventoryConstants.SOURCE_TYPE_INBOUND,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_FAILED,
                ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED, traceFailureMessage, draftRequestJson, null, operatorId, tenantId, true);
            return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
                .setPlatformMsg(failed.getPlatformMsg());
        }

        Inv3503Input input = InvPlatformInputAssembler.assemble3503(line);
        String requestJson = JSON.toJSONString(input);
        try {
            ResultBody body = inv3503Invoker.invoke(
                ctx.busNo(), input, buildInvokeContext(operatorId, request.getOperatorName()));
            YbInvUpload saved = uploadDomainService.upsertUploadRecord(
                existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_PURCHASE, line, ChsInventoryConstants.SOURCE_TYPE_INBOUND,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_SUCCESS,
                ChsInventoryConstants.PLATFORM_RET_RSLT_SUCCESS, null, requestJson, JSON.toJSONString(body), operatorId, tenantId, false);
            result.setUploadId(saved.getId()).setUploadStatus(saved.getUploadStatus());
        } catch (RuntimeException ex) {
            YbInvUpload failed = uploadDomainService.upsertUploadRecord(
                existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_PURCHASE, line, ChsInventoryConstants.SOURCE_TYPE_INBOUND,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_FAILED,
                ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED, ex.getMessage(), requestJson, null, operatorId, tenantId, true);
            return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
                .setPlatformMsg(ex.getMessage());
        }
        // Cmd：3503 已 SUCCESS——伴生须在 try 外，避免 3502 异常把 3503 回写成 FAILED
        followUpCompanion3502AfterPurchase(
            request.getContractBusNo(),
            request.getContractId(),
            request.getSourceId(),
            request.getOperatorName(),
            InventoryPurchaseCompanion3502LineAssembler.fromPurchaseInbound(line),
            ctx,
            tenantId,
            operatorId);
        return result;
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

        // Pre：未对码不调平台，仅记 SKIPPED（不落 request_json）
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

        String traceFailureMessage = uploadTraceLineSupport.resolveUploadTraceFailureMessage(line, false, null, null);
        if (isNotEmpty(traceFailureMessage)) {
            String draftRequestJson = tryAssembleRequestJson(() -> InvPlatformInputAssembler.assemble3501(line));
            YbInvUpload failed = uploadDomainService.upsertUploadRecord(
                existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_STOCKTAKE, line, sourceType,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_FAILED,
                ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED, traceFailureMessage, draftRequestJson, null, operatorId, tenantId, true);
            return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
                .setPlatformMsg(failed.getPlatformMsg());
        }

        Inv3501Input input = InvPlatformInputAssembler.assemble3501(line);
        String requestJson = JSON.toJSONString(input);
        try {
            ResultBody body = inv3501Invoker.invoke(
                ctx.busNo(), input, buildInvokeContext(operatorId, request.getOperatorName()));
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
     * <p>追溯码超过 {@link ChsInventoryConstants#DRUGTRACINFO_MAX_PER_REQUEST} 时先拆批出站，
     * 每片批次号追加 {@code -C01}/{@code -C02}… 以独立幂等。</p>
     *
     * @param request    3502 上报命令
     * @param line       库存变更行
     * @param ctx        契约上下文
     * @param tenantId   租户 ID
     * @param operatorId 操作人 ID
     * @param sourceType 来源类型
     * @return 单行上报结果（多片时返回末片；任一片失败则状态为 FAILED）
     */
    private InvUpload3502Result processInvChangeLine3502(
        InvUpload3502Command request,
        InvUploadLineCommand line,
        ContractContext ctx,
        Long tenantId,
        Long operatorId,
        String sourceType
    ) {
        // 本系统 drugtracinfo ≤100（平台书面 500 但会静默丢码）：超限先拆批，每片独立幂等键（bchno-C01…）
        List<InvUploadLineCommand> chunks = InventoryDrugTracChunkAssembler.splitForPlatformLimit(
            line, ChsInventoryConstants.DRUGTRACINFO_MAX_PER_REQUEST);
        // 多片：逐片出站；任一片非 SUCCESS 则汇总结果不得冒充全部成功
        if (chunks.size() > 1) {
            InvUpload3502Result last = null;
            boolean allSuccess = true;
            // 逐片出站：每片用独立 -C 后缀批次号
            for (InvUploadLineCommand chunk : chunks) {
                last = processInvChangeLine3502Single(
                    request, chunk, ctx, tenantId, operatorId, sourceType);
                if (last == null
                    || !ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(last.getUploadStatus())) {
                    allSuccess = false;
                }
            }
            // 拆批结果异常为空：记 FAILED 便于运维按原单号排查
            if (last == null) {
                return new InvUpload3502Result()
                    .setSourceLineId(line.getSourceLineId())
                    .setFixmedinsBchno(line.getFixmedinsBchno())
                    .setInvChgType(line.getInvChgType())
                    .setUploadStatus(ChsInventoryConstants.UPLOAD_STATUS_FAILED)
                    .setPlatformMsg("追溯码拆批后无有效上报片");
            }
            // 末片 SUCCESS 但前序有失败：降级 FAILED，提示按 -C 后缀补传
            if (!allSuccess
                && ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(last.getUploadStatus())) {
                last.setUploadStatus(ChsInventoryConstants.UPLOAD_STATUS_FAILED);
                last.setPlatformMsg("追溯码拆批后仍有失败片，请按 -C 后缀批次补传");
            }
            return last;
        }
        // 单片：走原幂等路径
        return processInvChangeLine3502Single(request, line, ctx, tenantId, operatorId, sourceType);
    }

    /**
     * 单片 3502 出站（不含追溯码再拆批）。
     *
     * @param request    3502 命令
     * @param line       已是单片行
     * @param ctx        契约上下文
     * @param tenantId   租户
     * @param operatorId 操作人
     * @param sourceType 来源类型
     * @return 单片结果
     */
    private InvUpload3502Result processInvChangeLine3502Single(
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

        // Pre：未对码不调平台，仅记 SKIPPED（不落 request_json）
        if (inventoryProperties.isSkipWithoutYbNo() && isEmpty(line.getMedListCodg())) {
            YbInvUpload skipped = uploadDomainService.upsertUploadRecord(
                null, ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_CHANGE, line, sourceType,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_SKIPPED_NO_YB_NO,
                null, null, null, null, operatorId, tenantId, false);
            return result.setUploadId(skipped.getId()).setUploadStatus(skipped.getUploadStatus());
        }

        Optional<YbInvUpload> existing = uploadDomainService.findExistingLine(
            tenantId, ChsInfNo.INVENTORY_CHANGE, line.getFixmedinsBchno(), line.getSourceLineId());
        // Gate：同批次+来源行已 SUCCESS → 幂等短路，禁止重复出站
        if (existing.isPresent()
            && ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(existing.get().getUploadStatus())) {
            YbInvUpload row = existing.get();
            return result.setUploadId(row.getId()).setUploadStatus(row.getUploadStatus())
                .setPlatformMsg(row.getPlatformMsg());
        }

        String traceFailureMessage = uploadTraceLineSupport.resolveUploadTraceFailureMessage(line, false, null, null);
        if (isNotEmpty(traceFailureMessage)) {
            String draftRequestJson = tryAssembleRequestJson(() -> InvPlatformInputAssembler.assemble3502(line));
            YbInvUpload failed = uploadDomainService.upsertUploadRecord(
                existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_CHANGE, line, sourceType,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_FAILED,
                ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED, traceFailureMessage, draftRequestJson, null, operatorId, tenantId, true);
            return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
                .setPlatformMsg(failed.getPlatformMsg());
        }

        // Pre：组装放 try 内——符号校验失败记 FAILED，不向上抛（避免打坏 3503/3504 伴生调用方）
        String requestJson = null;
        try {
            Inv3502Input input = InvPlatformInputAssembler.assemble3502(line);
            requestJson = JSON.toJSONString(input);
            ResultBody body = inv3502Invoker.invoke(
                ctx.busNo(), input, buildInvokeContext(operatorId, request.getOperatorName()));
            YbInvUpload saved = uploadDomainService.upsertUploadRecord(
                existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_CHANGE, line, sourceType,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_SUCCESS,
                ChsInventoryConstants.PLATFORM_RET_RSLT_SUCCESS, null, requestJson, JSON.toJSONString(body),
                operatorId, tenantId, false);
            // Gate：入库有码须 3512 确认落全，否则改 FAILED（防平台静默丢码→虚空入库→后续 191）
            String incompleteMsg = inboundDrugTracPlatformVerifySupport.findIncompleteInboundTraceMessage(
                ctx.busNo(), line, operatorId, request.getOperatorName());
            if (isNotEmpty(incompleteMsg)) {
                YbInvUpload failed = uploadDomainService.upsertUploadRecord(
                    saved, ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_CHANGE, line, sourceType,
                    request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_FAILED,
                    ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED, incompleteMsg, requestJson,
                    JSON.toJSONString(body), operatorId, tenantId, true);
                return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
                    .setPlatformMsg(incompleteMsg);
            }
            // 子表失败不把平台已成功的 3502 打成 FAILED（退药伴生 106 有码时历史整笔回滚根因）
            persistTraceRowsSafely(saved, line, tenantId);
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
     * 平台 SUCCESS 后写追溯码子表；异常只打日志，不回滚主表审计。
     *
     * @param saved    已落库 SUCCESS 流水
     * @param line     上报行
     * @param tenantId 租户
     */
    private void persistTraceRowsSafely(YbInvUpload saved, InvUploadLineCommand line, Long tenantId) {
        // 无主表或无行命令：跳过子表，避免 NPE
        if (saved == null || line == null) {
            return;
        }
        try {
            uploadDomainService.saveTraceRows(saved, line, tenantId);
        } catch (RuntimeException ex) {
            // Gate：子表 uk 冲突等不得否定平台已成功的主表行
            log.warn(
                "yb_inv_upload_trace 写入失败（主表仍 SUCCESS）uploadId={} infno={} bchno={} msg={}",
                saved.getId(),
                saved.getInfno(),
                saved.getFixmedinsBchno(),
                ex.getMessage());
        }
    }

    /**
     * 处理单行 3504 商品采购退货上报；平台 SUCCESS（含幂等）后伴生 3502（109，批次=退供单号）。
     *
     * @param request    3504 上报命令（{@code inboundBusNo} 实际为退供单号）
     * @param line       采购退货行
     * @param ctx        契约上下文
     * @param tenantId   租户 ID
     * @param operatorId 操作人 ID
     * @return 单行 3504 上报结果（不含伴生 3502 状态）
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

        // Pre：未对码不调平台，仅记 SKIPPED（不落 request_json）
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
        // 幂等：3504 已成功仍尝试伴生 3502（批次=退供单号）
        if (existing.isPresent()
            && ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(existing.get().getUploadStatus())) {
            YbInvUpload row = existing.get();
            followUpCompanion3502AfterPurchaseReturn(request, line, ctx, tenantId, operatorId);
            return result.setUploadId(row.getId()).setUploadStatus(row.getUploadStatus())
                .setPlatformMsg(row.getPlatformMsg());
        }

        String traceFailureMessage = uploadTraceLineSupport.resolveUploadTraceFailureMessage(line, false, null, null);
        if (isNotEmpty(traceFailureMessage)) {
            String draftRequestJson = tryAssembleRequestJson(() -> InvPlatformInputAssembler.assemble3504(line));
            YbInvUpload failed = uploadDomainService.upsertUploadRecord(
                existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_PURCHASE_RETURN, line, ChsInventoryConstants.SOURCE_TYPE_INBOUND,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_FAILED,
                ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED, traceFailureMessage, draftRequestJson, null, operatorId, tenantId, true);
            return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
                .setPlatformMsg(failed.getPlatformMsg());
        }

        Inv3504Input input = InvPlatformInputAssembler.assemble3504(line);
        String requestJson = JSON.toJSONString(input);
        try {
            ResultBody body = inv3504Invoker.invoke(
                ctx.busNo(), input, buildInvokeContext(operatorId, request.getOperatorName()));
            YbInvUpload saved = uploadDomainService.upsertUploadRecord(
                existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_PURCHASE_RETURN, line, ChsInventoryConstants.SOURCE_TYPE_INBOUND,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_SUCCESS,
                ChsInventoryConstants.PLATFORM_RET_RSLT_SUCCESS, null, requestJson, JSON.toJSONString(body), operatorId, tenantId, false);
            result.setUploadId(saved.getId()).setUploadStatus(saved.getUploadStatus());
        } catch (RuntimeException ex) {
            YbInvUpload failed = uploadDomainService.upsertUploadRecord(
                existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_PURCHASE_RETURN, line, ChsInventoryConstants.SOURCE_TYPE_INBOUND,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_FAILED,
                ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED, ex.getMessage(), requestJson, null, operatorId, tenantId, true);
            return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
                .setPlatformMsg(ex.getMessage());
        }
        // Cmd：3504 已 SUCCESS——伴生须在 try 外，避免 3502 异常把 3504 回写成 FAILED
        followUpCompanion3502AfterPurchaseReturn(request, line, ctx, tenantId, operatorId);
        return result;
    }

    /**
     * 3504 成功后组装并以退供单号为批次出站伴生 3502。
     *
     * @param request    3504 命令（inboundBusNo=退供单号）
     * @param line       3504 源行
     * @param ctx        契约上下文
     * @param tenantId   租户
     * @param operatorId 操作人
     */
    private void followUpCompanion3502AfterPurchaseReturn(
        InvUpload3504Command request,
        InvUploadLineCommand line,
        ContractContext ctx,
        Long tenantId,
        Long operatorId
    ) {
        // 伴生 3502 批次取退供单号，不用 3504 行上的原进货批次
        String returnBchno = InventoryUploadBatchNoAssembler.buildFixmedinsBchno(request.getInboundBusNo());
        followUpCompanion3502AfterPurchase(
            request.getContractBusNo(),
            request.getContractId(),
            request.getSourceId(),
            request.getOperatorName(),
            InventoryPurchaseCompanion3502LineAssembler.fromPurchaseReturn(line, returnBchno),
            ctx,
            tenantId,
            operatorId);
    }

    /**
     * 采购/退供平台成功后出站伴生 3502；失败仅落 3502 审计，不抛回调用方。
     *
     * @param contractBusNo 契约编号
     * @param contractId    契约主键
     * @param sourceId      来源单据主键
     * @param operatorName  操作人姓名
     * @param line3502      已组装的伴生 3502 行
     * @param ctx           契约上下文
     * @param tenantId      租户
     * @param operatorId    操作人主键
     */
    private void followUpCompanion3502AfterPurchase(
        String contractBusNo,
        Long contractId,
        Long sourceId,
        String operatorName,
        InvUploadLineCommand line3502,
        ContractContext ctx,
        Long tenantId,
        Long operatorId
    ) {
        runPaired3502(
            contractBusNo,
            contractId,
            sourceId,
            operatorName,
            line3502,
            ctx,
            tenantId,
            operatorId,
            ChsInventoryConstants.SOURCE_TYPE_INBOUND);
    }

    /**
     * 3505 出站前：先试上报 3502 其他出库(107)；成败由调用方忽略，仅作库存配对尽力而为。
     *
     * @param request    3505 命令
     * @param line       销售行
     * @param ctx        契约上下文
     * @param tenantId   租户
     * @param operatorId 操作人
     * @param sourceType 与 3505 相同的 source_type
     * @return 3502 SUCCESS 时 true；组装失败或平台失败时 false（调用方仍继续 3505）
     */
    private boolean uploadPrecedingSaleOutbound3502(
        InvUpload3505Command request,
        InvUploadLineCommand line,
        ContractContext ctx,
        Long tenantId,
        Long operatorId,
        String sourceType
    ) {
        InvUploadLineCommand line3502 = InventorySaleCompanion3502LineAssembler.fromSaleOutbound(line);
        // Gate：销售配对 3502 跟入库平台，不跟结算/工伤契约（BR-CHS-INV-015）
        ContractContext stockCtx = resolveSalePaired3502Context(tenantId, line3502);
        return runPaired3502(
            stockCtx.busNo(),
            stockCtx.contract().id(),
            request.getSourceId(),
            request.getOperatorName(),
            line3502,
            stockCtx,
            tenantId,
            operatorId,
            sourceType);
    }

    /**
     * 3506 SUCCESS 后伴生 3502 其他入库(106，批次=退药单号)；失败不回写 3506。
     *
     * @param request    3506 命令
     * @param line       退药行
     * @param ctx        结算契约上下文（3506 用；本方法改走入库平台）
     * @param tenantId   租户
     * @param operatorId 操作人
     * @param sourceType 与 3506 相同的 source_type
     */
    private void followUpCompanion3502AfterSaleReturn(
        InvUpload3506Command request,
        InvUploadLineCommand line,
        ContractContext ctx,
        Long tenantId,
        Long operatorId,
        String sourceType
    ) {
        InvUploadLineCommand line3502 = InventorySaleCompanion3502LineAssembler.fromSaleReturnInbound(line);
        // Gate：退药入库 3502 亦跟码的入库平台，不跟工伤结算契约
        ContractContext stockCtx = resolveSalePaired3502Context(tenantId, line3502);
        runPaired3502(
            stockCtx.busNo(),
            stockCtx.contract().id(),
            request.getSourceId(),
            request.getOperatorName(),
            line3502,
            stockCtx,
            tenantId,
            operatorId,
            sourceType);
    }

    /**
     * 销售配对 3502：按码的入库平台解析契约上下文。
     *
     * @param tenantId 租户
     * @param line3502 配对行
     * @return 入库/市优先契约上下文
     */
    private ContractContext resolveSalePaired3502Context(Long tenantId, InvUploadLineCommand line3502) {
        Contract stock = salePaired3502ContractSupport.resolveForSalePaired3502(tenantId, line3502);
        return resolveContract(stock.getBusNo(), stock.getId());
    }

    /**
     * 配对/前置 3502 出站：行空返回 false；异常吞掉返回 false；SUCCESS 返回 true。
     *
     * <p>供采购伴生、销售前置出库、退药后置入库共用，保证 3502 失败不冒泡打坏 3503–3506。</p>
     *
     * @param contractBusNo 契约编号
     * @param contractId    契约主键
     * @param sourceId      来源单据主键
     * @param operatorName  操作人姓名
     * @param line3502      已组装 3502 行
     * @param ctx           契约上下文
     * @param tenantId      租户
     * @param operatorId    操作人主键
     * @param sourceType    yb_inv_upload.source_type
     * @return 平台 SUCCESS 时 true
     */
    private boolean runPaired3502(
        String contractBusNo,
        Long contractId,
        Long sourceId,
        String operatorName,
        InvUploadLineCommand line3502,
        ContractContext ctx,
        Long tenantId,
        Long operatorId,
        String sourceType
    ) {
        // 无数量/无批次/无退药单号：伴生跳过；须打日志便于排查「有码退药无 106」
        if (line3502 == null) {
            log.warn(
                "配对/伴生 3502 组装为空已跳过 sourceId={} sourceType={} contractBusNo={}",
                sourceId,
                sourceType,
                contractBusNo);
            return false;
        }
        // 经办人回落：行未填时用异步捕获的操作人姓名
        if (isEmpty(line3502.getInvChgOpterName()) && isNotEmpty(operatorName)) {
            line3502.setInvChgOpterName(operatorName);
        }
        InvUpload3502Command companion = new InvUpload3502Command()
            .setContractBusNo(contractBusNo)
            .setContractId(contractId)
            .setSourceId(sourceId)
            .setSourceType(sourceType)
            .setOperatorId(operatorId)
            .setOperatorName(operatorName)
            .setLines(List.of(line3502));
        try {
            InvUpload3502Result r3502 = processInvChangeLine3502(
                companion, line3502, ctx, tenantId, operatorId, sourceType);
            return r3502 != null
                && ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(r3502.getUploadStatus());
        } catch (RuntimeException ex) {
            // 3502 失败已尽量落 FAILED；此处仅防未捕获异常冒泡打坏 3503–3506
            log.warn(
                "配对/伴生 3502 未捕获异常已吞掉 sourceId={} bchno={} msg={}",
                sourceId,
                line3502.getFixmedinsBchno(),
                ex.getMessage());
            return false;
        }
    }

    /**
     * 调用 3505/3506 销售类平台接口并解析业务结果码。
     *
     * @param contractBusNo 契约业务编号
     * @param infno         接口编号
     * @param input         平台入参
     * @param operatorId    已解析操作人主键（可来自异步命令）
     * @param operatorName  操作人姓名；可空
     * @return 平台上报结果
     */
    private PlatformUploadOutcome invokeSalePlatform(
        String contractBusNo,
        String infno,
        Object input,
        Long operatorId,
        String operatorName
    ) {
        String requestJson = JSON.toJSONString(input);
        try {
            ResultBody body;
            InvokeContext invokeContext = buildInvokeContext(operatorId, operatorName);
            // 3505 / 3506 分 Invoker
            if (ChsInfNo.INVENTORY_SALE.equals(infno)) {
                body = inv3505Invoker.invoke(contractBusNo, (Inv3505Input) input, invokeContext);
            } else {
                body = inv3506Invoker.invoke(contractBusNo, (Inv3506Input) input, invokeContext);
            }
            JSONObject output = parseOutputObject(body);
            // 省本级可能 output={} 无 ret_rslt；协议成功则落 SUCCESS
            InvPlatformOutputParser.SaleUploadBizResult biz =
                InvPlatformOutputParser.resolveSaleUploadBizResult(output);
            return PlatformUploadOutcome.success(
                biz.uploadStatus(), biz.retRslt(), biz.msgRslt(), requestJson, JSON.toJSONString(body));
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
     * 解析操作人：命令已带则用命令（异步/发票同构）；否则回落当前登录态（运维台同步）。
     *
     * @param preferredOperatorId 命令上的操作人主键；可空
     * @return 非空操作人主键
     */
    private Long resolveOperatorId(Long preferredOperatorId) {
        // 异步路径：调用线程已写入
        if (preferredOperatorId != null) {
            return preferredOperatorId;
        }
        // 同步运维台：仍从 SecurityContext 取
        return currentOperatorResolver.requireOperatorId("请先登录");
    }

    /**
     * 构建平台调用上下文（操作人 ID 与姓名）。
     *
     * @param operatorId   已解析操作人主键；可空则再 require
     * @param operatorName 操作人姓名；可空则签到解析回落 admin/登录名
     * @return 调用上下文
     */
    private InvokeContext buildInvokeContext(Long operatorId, String operatorName) {
        Long resolvedOperatorId = resolveOperatorId(operatorId);
        InvokeContext.InvokeContextBuilder partialBuilder = InvokeContext.builder()
            .operatorId(String.valueOf(resolvedOperatorId));
        // 异步路径带入姓名，避免异步线程无登录名时误用默认 admin
        if (isNotEmpty(operatorName)) {
            partialBuilder.operatorName(operatorName.trim());
        }
        InvokeContext partial = partialBuilder.build();
        return InvokeContext.builder()
            .operatorId(signCredentialResolver.resolveOperatorId(partial))
            .operatorName(signCredentialResolver.resolveOperatorName(partial))
            .build();
    }

    /**
     * 出站前校验失败时尽量组装平台 input 草稿 JSON，供运维台回显编辑。
     *
     * @param assembler 组装器（如 assemble3505）
     * @return 草稿 JSON；组装异常时 null
     */
    private static String tryAssembleRequestJson(java.util.function.Supplier<Object> assembler) {
        if (assembler == null) {
            return null;
        }
        try {
            Object input = assembler.get();
            if (input == null) {
                return null;
            }
            return JSON.toJSONString(input);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** 契约上下文（契约快照、业务编号、契约配置）。 */
    private record ContractContext(ChsContractSnapshot contract, String busNo, ContractConfig config) {
    }
}
