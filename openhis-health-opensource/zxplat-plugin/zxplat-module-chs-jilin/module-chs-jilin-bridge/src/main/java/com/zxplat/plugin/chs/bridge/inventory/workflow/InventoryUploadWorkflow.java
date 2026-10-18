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
import com.zxplat.plugin.chs.bridge.inventory.support.InvPlatformSupport;
import com.zxplat.plugin.chs.bridge.inventory.support.InvPlatformSupport.InvContractContext;
import com.zxplat.plugin.chs.domain.inventory.access.InventoryUploadAccess;
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
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.gateway.scheme.inventory.model.Inv3501Input;
import com.zxplat.plugin.chs.gateway.scheme.inventory.model.Inv3502Input;
import com.zxplat.plugin.chs.gateway.scheme.inventory.model.Inv3503Input;
import com.zxplat.plugin.chs.gateway.scheme.inventory.model.Inv3504Input;
import com.zxplat.plugin.chs.gateway.scheme.inventory.model.Inv3505Input;
import com.zxplat.plugin.chs.gateway.scheme.inventory.model.Inv3506Input;
import com.zxplat.plugin.chs.gateway.scheme.inventory.assembler.InvPlatformInputAssembler;
import com.zxplat.plugin.chs.gateway.scheme.inventory.parser.InvPlatformOutputParser;
import com.zxplat.plugin.chs.gateway.support.ChsCurrentOperatorResolver;
import com.zxplat.plugin.chs.gateway.support.ChsGatewayOutputSupport;
import com.zxplat.domain.financial.contract.entity.Contract;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 【3501–3506】进销存上报 bridge 编排实现（P14）。
 *
 * <p><b>职责：</b>校验命令 → 调用 Inv350x invoker 出站 → upsert {@code yb_inv_upload} 及追溯表。</p>
 * <p><b>实现端口：</b>{@link com.zxplat.plugin.chs.domain.inventory.access.InventoryUploadAccess}</p>
 * <p><b>调用方：</b>{@code InsuranceInventoryAppService}</p>
 * <p><b>架构边界：</b>bridge 层组装 gateway 出站上下文；application 只依赖 domain 端口。</p>
 * <p><b>采购伴生：</b>3503/3504 SUCCESS 后跟 3502（108/109；退供批次=退供单号）。</p>
 * <p><b>销售配对：</b>3505 前先试 3502(107)，再调 3505（3502 失败仍继续）；
 * 3506 SUCCESS 后再 3502(106，批次=退药单号)。</p>
 * <p><b>入库落码核验：</b>入库类 3502 SUCCESS 且有码时 3512 核验（BR-CHS-INV-014）。</p>
 * <p><b>销售配对契约：</b>3505/3506 跟结算平台；配对 3502 跟入库平台（BR-CHS-INV-015）。</p>
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
    private final Inv3506Invoker inv3506Invoker;
    private final InvPlatformSupport invPlatformSupport;
    private final ChsCurrentOperatorResolver currentOperatorResolver;
    private final YbInvUploadDomainService uploadDomainService;
    private final ChsInventoryProperties inventoryProperties;
    private final InventoryInboundDrugTracPlatformVerifySupport inboundDrugTracPlatformVerifySupport;
    private final InventorySalePaired3502ContractSupport salePaired3502ContractSupport;

    /**
     * 【3505】商品销售上报：逐行调用平台接口。
     *
     * @param request 契约、来源单据、销售行
     * @return 各行 uploadId、uploadStatus 及平台回执摘要
     */
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
        // 运维可带 sourceType；空则回落盘点
        String sourceType = ChsInventoryConstants.SOURCE_TYPE_STOCKTAKE;
        if (isNotEmpty(request.getSourceType())) {
            sourceType = request.getSourceType().trim();
        }
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

    /**
     * 处理单行 3505：校验通过后先试 3502(107)，再调 3505；3502 成败不影响 3505。
     *
     * @param request    3505 命令
     * @param line       销售行
     * @param ctx        契约上下文
     * @param tenantId   租户
     * @param operatorId 操作人
     * @return 3505 单行结果
     */
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

        // Pre：未对码不调平台，仅记 SKIPPED（不落 request_json）
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
        // 幂等：3505 已成功则不再调 3502 / 3505
        if (existing.isPresent()
            && ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(existing.get().getUploadStatus())) {
            YbInvUpload row = existing.get();
            return result.setUploadId(row.getId()).setUploadStatus(row.getUploadStatus())
                .setPlatformRetRslt(row.getPlatformRetRslt()).setPlatformMsg(row.getPlatformMsg());
        }

        // Gate：追溯码数量校验未通过则记 3505 FAILED，尚未调 3502
        if (inventoryProperties.isEnforceTraceCountEqualsQty()
            && ChsInventoryConstants.TRACE_POLICY_TRACE_REQUIRED.equals(line.getTracePolicy())) {
            if (!traceCountMatchesQuantity(line)) {
                String draftRequestJson = tryAssembleRequestJson3505(line);
                YbInvUpload failed = uploadDomainService.upsertUploadRecord(
                    existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_SALE, line, ChsInventoryConstants.SOURCE_TYPE_DISPENSE, request.getSourceId(),
                    ChsInventoryConstants.UPLOAD_STATUS_FAILED,
                    ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED, "trace code count mismatch", draftRequestJson, null, operatorId, tenantId, true);
                return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
                    .setPlatformRetRslt(ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED).setPlatformMsg(failed.getPlatformMsg());
            }
        }
        // Gate：规范必填项缺则 FAILED，尚未调 3502（与 Orchestrator 同序，避免组合路径误出站）
        String requiredFailureMessage = InventoryUploadSaleRequiredFieldSupport.resolveMissingRequiredMessage(line, true);
        if (isNotEmpty(requiredFailureMessage)) {
            String draftRequestJson = tryAssembleRequestJson3505(line);
            YbInvUpload failed = uploadDomainService.upsertUploadRecord(
                existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_SALE, line,
                ChsInventoryConstants.SOURCE_TYPE_DISPENSE, request.getSourceId(),
                ChsInventoryConstants.UPLOAD_STATUS_FAILED,
                ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED, requiredFailureMessage, draftRequestJson, null,
                operatorId, tenantId, true);
            return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
                .setPlatformRetRslt(ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED)
                .setPlatformMsg(failed.getPlatformMsg());
        }

        // Cmd：先试销售配对 3502（跟入库平台，BR-015）；失败仅落 3502 审计，不阻塞 3505
        runSalePaired3502(
            request.getSourceId(),
            request.getOperatorName(),
            InventorySaleCompanion3502LineAssembler.fromSaleOutbound(line),
            tenantId,
            operatorId,
            ChsInventoryConstants.SOURCE_TYPE_DISPENSE);

        // Cmd：无论前置 3502 成败，均出站 3505
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
        // 3505 失败不回写前置 3502
        YbInvUpload failed = uploadDomainService.upsertUploadRecord(
            existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_SALE, line, ChsInventoryConstants.SOURCE_TYPE_DISPENSE, request.getSourceId(),
            ChsInventoryConstants.UPLOAD_STATUS_FAILED,
            outcome.retRslt(), outcome.errorMessage(), outcome.requestJson(), null, operatorId, tenantId, true);
        return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
            .setPlatformRetRslt(outcome.retRslt()).setPlatformMsg(outcome.errorMessage());
    }

    /**
     * 处理单行 3506：SUCCESS（含幂等）后再伴生 3502(106，批次=退药单号)。
     *
     * @param request    3506 命令
     * @param line       退货行
     * @param ctx        契约上下文
     * @param tenantId   租户
     * @param operatorId 操作人
     * @return 3506 单行结果
     */
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

        // Pre：未对码不调平台，仅记 SKIPPED（不落 request_json）
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
        // 幂等：3506 已成功仍尝试伴生 3502
        if (existing.isPresent()
            && ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(existing.get().getUploadStatus())) {
            YbInvUpload row = existing.get();
            runSalePaired3502(
                request.getSourceId(),
                request.getOperatorName(),
                InventorySaleCompanion3502LineAssembler.fromSaleReturnInbound(line),
                tenantId,
                operatorId,
                ChsInventoryConstants.SOURCE_TYPE_DISPENSE);
            return result.setUploadId(row.getId()).setUploadStatus(row.getUploadStatus())
                .setPlatformRetRslt(row.getPlatformRetRslt()).setPlatformMsg(row.getPlatformMsg());
        }

        // Gate：3506 必填项；失败不调平台也不伴生 3502
        String requiredFailureMessage3506 =
            InventoryUploadSaleRequiredFieldSupport.resolveMissingRequiredMessage(line, false);
        if (isNotEmpty(requiredFailureMessage3506)) {
            String draftRequestJson = tryAssembleRequestJson(() -> InvPlatformInputAssembler.assemble3506(line));
            YbInvUpload failed = uploadDomainService.upsertUploadRecord(
                existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_SALE_RETURN, line,
                ChsInventoryConstants.SOURCE_TYPE_DISPENSE, request.getSourceId(),
                ChsInventoryConstants.UPLOAD_STATUS_FAILED,
                ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED, requiredFailureMessage3506, draftRequestJson, null,
                operatorId, tenantId, true);
            return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
                .setPlatformRetRslt(ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED)
                .setPlatformMsg(failed.getPlatformMsg());
        }

        Inv3506Input input = InvPlatformInputAssembler.assemble3506(line);
        PlatformUploadOutcome outcome = invokeSalePlatform(ctx.busNo(), ChsInfNo.INVENTORY_SALE_RETURN, input);
        if (outcome.invokeOk()) {
            YbInvUpload saved = uploadDomainService.upsertUploadRecord(
                existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_SALE_RETURN, line, ChsInventoryConstants.SOURCE_TYPE_DISPENSE, request.getSourceId(),
                outcome.uploadStatus(), outcome.retRslt(), outcome.msgRslt(), outcome.requestJson(), outcome.responseJson(),
                operatorId, tenantId, ChsInventoryConstants.UPLOAD_STATUS_FAILED.equals(outcome.uploadStatus()));
            uploadDomainService.saveTraceRows(saved, line, tenantId);
            result.setUploadId(saved.getId()).setUploadStatus(saved.getUploadStatus())
                .setPlatformRetRslt(outcome.retRslt()).setPlatformMsg(outcome.msgRslt());
            // 仅业务 SUCCESS 后伴生 3502；失败不回写 3506
            if (ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(saved.getUploadStatus())) {
                runSalePaired3502(
                    request.getSourceId(),
                    request.getOperatorName(),
                    InventorySaleCompanion3502LineAssembler.fromSaleReturnInbound(line),
                    tenantId,
                    operatorId,
                    ChsInventoryConstants.SOURCE_TYPE_DISPENSE);
            }
            return result;
        }
        YbInvUpload failed = uploadDomainService.upsertUploadRecord(
            existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_SALE_RETURN, line, ChsInventoryConstants.SOURCE_TYPE_DISPENSE, request.getSourceId(),
            ChsInventoryConstants.UPLOAD_STATUS_FAILED,
            outcome.retRslt(), outcome.errorMessage(), outcome.requestJson(), null, operatorId, tenantId, true);
        return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
            .setPlatformRetRslt(outcome.retRslt()).setPlatformMsg(outcome.errorMessage());
    }

    /**
     * 处理单行 3503 采购上报；SUCCESS（含幂等）后伴生 3502（108）。
     *
     * @param request    3503 命令
     * @param line       采购行
     * @param ctx        契约上下文
     * @param tenantId   租户
     * @param operatorId 操作人
     * @return 3503 单行结果
     */
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
        // 幂等：3503 已成功仍尝试伴生 3502
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

        Inv3503Input input = InvPlatformInputAssembler.assemble3503(line);
        String requestJson = JSON.toJSONString(input);
        try {
            ResultBody body = inv3503Invoker.invoke(ctx.busNo(), input, invPlatformSupport.buildInvokeContext());
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
        // 3503 已 SUCCESS：伴生在 try 外，避免 3502 异常回写 3503
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
     * 处理单行 3501 商品盘存上报（运维/初始化路径）。
     *
     * @param request    3501 命令
     * @param line       盘存行
     * @param ctx        契约上下文
     * @param tenantId   租户
     * @param operatorId 操作人
     * @param sourceType 审计来源类型
     * @return 3501 单行结果
     */
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

    /**
     * 处理单行 3502 库存变更；组装/符号校验放 try 内，失败记 FAILED 不向上抛。
     *
     * @param request    3502 命令
     * @param line       变更行
     * @param ctx        契约上下文
     * @param tenantId   租户
     * @param operatorId 操作人
     * @param sourceType 审计来源类型
     * @return 3502 单行结果
     */
    private InvUpload3502Result processInvChangeLine3502(
        InvUpload3502Command request,
        InvUploadLineCommand line,
        InvContractContext ctx,
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
     * @param sourceType 审计来源类型
     * @return 单片结果
     */
    private InvUpload3502Result processInvChangeLine3502Single(
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

        // 组装放 try 内，符号校验失败记 FAILED 不向上抛
        String requestJson = null;
        try {
            Inv3502Input input = InvPlatformInputAssembler.assemble3502(line);
            requestJson = JSON.toJSONString(input);
            ResultBody body = inv3502Invoker.invoke(ctx.busNo(), input, invPlatformSupport.buildInvokeContext());
            YbInvUpload saved = uploadDomainService.upsertUploadRecord(
                existing.orElse(null), ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_CHANGE, line, sourceType,
                request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_SUCCESS,
                ChsInventoryConstants.PLATFORM_RET_RSLT_SUCCESS, null, requestJson, JSON.toJSONString(body),
                operatorId, tenantId, false);
            // Gate：入库有码须 3512 确认落全，否则改 FAILED（BR-CHS-INV-014）
            String incompleteMsg = inboundDrugTracPlatformVerifySupport.findIncompleteInboundTraceMessage(
                ctx.busNo(), line, operatorId, null);
            if (isNotEmpty(incompleteMsg)) {
                YbInvUpload failed = uploadDomainService.upsertUploadRecord(
                    saved, ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_CHANGE, line, sourceType,
                    request.getSourceId(), ChsInventoryConstants.UPLOAD_STATUS_FAILED,
                    ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED, incompleteMsg, requestJson,
                    JSON.toJSONString(body), operatorId, tenantId, true);
                return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
                    .setPlatformMsg(incompleteMsg);
            }
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
     * 处理单行 3504 退供上报；SUCCESS（含幂等）后伴生 3502（109，批次=退供单号）。
     *
     * @param request    3504 命令（inboundBusNo=退供单号）
     * @param line       退供行
     * @param ctx        契约上下文
     * @param tenantId   租户
     * @param operatorId 操作人
     * @return 3504 单行结果
     */
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

        Inv3504Input input = InvPlatformInputAssembler.assemble3504(line);
        String requestJson = JSON.toJSONString(input);
        try {
            ResultBody body = inv3504Invoker.invoke(ctx.busNo(), input, invPlatformSupport.buildInvokeContext());
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
        // 3504 已 SUCCESS：伴生在 try 外，避免 3502 异常回写 3504
        followUpCompanion3502AfterPurchaseReturn(request, line, ctx, tenantId, operatorId);
        return result;
    }

    /**
     * 3504 成功后以退供单号为批次出站伴生 3502。
     *
     * @param request    3504 命令
     * @param line       3504 源行
     * @param ctx        契约上下文
     * @param tenantId   租户
     * @param operatorId 操作人
     */
    private void followUpCompanion3502AfterPurchaseReturn(
        InvUpload3504Command request,
        InvUploadLineCommand line,
        InvContractContext ctx,
        Long tenantId,
        Long operatorId
    ) {
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
     * 采购伴生 3502（sourceType=INBOUND）；行空跳过；失败仅落 3502 审计、不抛。
     *
     * @param contractBusNo 契约编号
     * @param contractId    契约主键
     * @param sourceId      来源单据
     * @param operatorName  操作人姓名
     * @param line3502      伴生行
     * @param ctx           契约上下文
     * @param tenantId      租户
     * @param operatorId    操作人
     */
    private void followUpCompanion3502AfterPurchase(
        String contractBusNo,
        Long contractId,
        Long sourceId,
        String operatorName,
        InvUploadLineCommand line3502,
        InvContractContext ctx,
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
     * 销售配对 3502：按码的入库平台出站（市入市出、省入省出；无码市优先；BR-CHS-INV-015）。
     *
     * @param sourceId     来源单据
     * @param operatorName 操作人姓名
     * @param line3502     已组装 3502 行
     * @param tenantId     租户
     * @param operatorId   操作人
     * @param sourceType   审计来源
     * @return 是否 SUCCESS
     */
    private boolean runSalePaired3502(
        Long sourceId,
        String operatorName,
        InvUploadLineCommand line3502,
        Long tenantId,
        Long operatorId,
        String sourceType
    ) {
        // Gate：不跟 3505/3506 结算契约（工伤结算时 3502 不得打到工伤平台）
        Contract stock = salePaired3502ContractSupport.resolveForSalePaired3502(tenantId, line3502);
        InvContractContext stockCtx = invPlatformSupport.resolveContract(stock.getBusNo(), stock.getId());
        return runPaired3502(
            stock.getBusNo(),
            stock.getId(),
            sourceId,
            operatorName,
            line3502,
            stockCtx,
            tenantId,
            operatorId,
            sourceType);
    }

    /**
     * 配对/前置 3502 出站：SUCCESS 返回 true；行空或异常返回 false（不冒泡）。
     *
     * @param contractBusNo 契约编号
     * @param contractId    契约主键
     * @param sourceId      来源单据
     * @param operatorName  操作人姓名
     * @param line3502      3502 行
     * @param ctx           契约上下文
     * @param tenantId      租户
     * @param operatorId    操作人
     * @param sourceType    审计来源类型
     * @return 是否 SUCCESS
     */
    private boolean runPaired3502(
        String contractBusNo,
        Long contractId,
        Long sourceId,
        String operatorName,
        InvUploadLineCommand line3502,
        InvContractContext ctx,
        Long tenantId,
        Long operatorId,
        String sourceType
    ) {
        if (line3502 == null) {
            return false;
        }
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
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /**
     * 调用 3505/3506 销售类平台接口并解析业务结果码。
     *
     * @param contractBusNo 契约业务编号
     * @param infno         接口编号（销售或销售退货）
     * @param input         平台入参
     * @return 平台上报结果
     */
    private PlatformUploadOutcome invokeSalePlatform(String contractBusNo, String infno, Object input) {
        String requestJson = JSON.toJSONString(input);
        try {
            ResultBody body;
            // 3505 / 3506 分 Invoker
            if (ChsInfNo.INVENTORY_SALE.equals(infno)) {
                body = inv3505Invoker.invoke(
                    contractBusNo, (Inv3505Input) input, invPlatformSupport.buildInvokeContext());
            } else {
                body = inv3506Invoker.invoke(
                    contractBusNo, (Inv3506Input) input, invPlatformSupport.buildInvokeContext());
            }
            JSONObject output = ChsGatewayOutputSupport.parseOutputObject(body);
            // 省本级可能 output={} 无 ret_rslt；协议成功则落 SUCCESS
            InvPlatformOutputParser.SaleUploadBizResult biz =
                InvPlatformOutputParser.resolveSaleUploadBizResult(output);
            return PlatformUploadOutcome.success(
                biz.uploadStatus(), biz.retRslt(), biz.msgRslt(), requestJson, JSON.toJSONString(body));
        } catch (RuntimeException ex) {
            return PlatformUploadOutcome.failure(ex.getMessage(), requestJson);
        }
    }

    /**
     * 出站前失败时尽量落平台 input 草稿报文。
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

    /**
     * 出站前失败时尽量落 3505 草稿报文。
     *
     * @param line 上报行
     * @return 草稿 JSON；组装失败时 null
     */
    private static String tryAssembleRequestJson3505(InvUploadLineCommand line) {
        return tryAssembleRequestJson(() -> InvPlatformInputAssembler.assemble3505(line));
    }

    /**
     * 销售类平台调用结果（协议成功/失败与业务 uploadStatus）。
     *
     * @param invokeOk     是否完成平台往返且未抛异常
     * @param uploadStatus 本地上报状态
     * @param retRslt      平台业务结果码
     * @param msgRslt      平台业务消息
     * @param requestJson  请求报文
     * @param responseJson 响应报文
     * @param errorMessage 失败时的错误文案
     */
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
         * 构建协议成功结果。
         *
         * @param uploadStatus 本地状态
         * @param retRslt      平台结果码
         * @param msgRslt      平台消息
         * @param requestJson  请求 JSON
         * @param responseJson 响应 JSON
         * @return 成功 outcome
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
         * 构建调用失败结果。
         *
         * @param errorMessage 错误文案
         * @param requestJson  已序列化请求（可空）
         * @return 失败 outcome
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
     * 校验追溯码条数是否与销售数量一致（配置 enforce 时使用）。
     *
     * @param line 销售行
     * @return 数量一致时 true
     */
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
