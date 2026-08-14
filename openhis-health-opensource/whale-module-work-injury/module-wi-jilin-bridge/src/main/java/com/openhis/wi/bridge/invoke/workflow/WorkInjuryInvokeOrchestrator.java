/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.bridge.invoke.workflow;

import com.alibaba.fastjson2.JSON;
import com.openhis.wi.gateway.scheme.register.model.Wi1101Output;
import com.openhis.wi.gateway.scheme.settle.model.Wi2206Output;
import com.openhis.wi.gateway.scheme.settle.model.Wi2207Output;
import com.openhis.wi.gateway.scheme.register.model.Wi2301Output;
import com.openhis.wi.gateway.scheme.register.model.Wi9102Output;
import com.openhis.wi.gateway.scheme.register.model.Wi9102QualificationRow;
import com.openhis.wi.gateway.scheme.register.parser.Wi1101OutputParser;
import com.openhis.wi.gateway.scheme.register.parser.Wi2301OutputParser;
import com.openhis.wi.gateway.scheme.register.parser.Wi9102OutputParser;
import com.openhis.wi.gateway.scheme.settle.parser.Wi2206OutputParser;
import com.openhis.wi.gateway.scheme.settle.parser.Wi2207OutputParser;
import com.openhis.wi.gateway.scheme.shared.model.WiBusConstants;
import com.openhis.wi.gateway.scheme.shared.model.WiInfnoConstants;
import com.openhis.wi.gateway.scheme.shared.model.WiInvokeSessionStatus;
import com.openhis.wi.gateway.scheme.shared.model.WiNextAction;
import com.openhis.wi.gateway.scheme.shared.model.WiPhase;
import com.openhis.wi.gateway.scheme.shared.model.WiPlatformConstants;
import com.openhis.wi.gateway.scheme.shared.assembler.WiPlatformInputAssembler;
import com.openhis.wi.bridge.invoke.support.WorkInjuryInvokeLogSupport;
import com.openhis.wi.bridge.settle.workflow.WorkInjurySettleStepPlanner;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import com.openhis.wi.domain.share.WiErrorCodes;
import com.openhis.wi.domain.settle.command.WiSettleContext;
import com.openhis.wi.domain.invoke.command.PrepareWorkInjuryInvokeRequest;
import com.openhis.wi.domain.invoke.result.PrepareWorkInjuryInvokeResponse;
import com.openhis.wi.domain.invoke.command.SubmitWorkInjuryInvokeResultRequest;
import com.openhis.wi.domain.invoke.result.SubmitWorkInjuryInvokeResultResponse;
import com.openhis.wi.domain.entity.YbWiEncounterAnchor;
import com.openhis.wi.domain.entity.YbWiInvokeSession;
import com.openhis.wi.domain.loader.WorkInjurySettleContextLoader;
import com.openhis.wi.domain.repository.YbWiEncounterAnchorRepository;
import com.openhis.wi.domain.repository.YbWiInvokeSessionRepository;
import com.openhis.wi.domain.service.YbWiEncounterAnchorDomainService;
import com.openhis.wi.domain.service.YbWiPreSettleDomainService;
import com.openhis.wi.domain.service.YbWiSettlementDomainService;
import com.openhis.chs.gateway.support.ChsCurrentOperatorResolver;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * OpenHIS.Win invoke 多步工作流（prepareInvoke / submitInvokeResult）。
 *
 * <p><b>出站</b>：Win :5000（前端循环，Whale 不直调 DLL）。</p>
 * <p><b>副作用</b>：{@code yb_wi_invoke_session}、{@code yb_invoke_log}、
 * {@code yb_wi_pre_settle}、{@code yb_settlement_record}、{@code yb_settlement_reversal}、
 * {@code yb_wi_encounter_anchor}。</p>
 * <p><b>编排真源</b>：{@link WorkInjurySettleStepPlanner}；报文：{@link WiPlatformInputAssembler}。</p>
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.work-injury.enabled", havingValue = "true")
public class WorkInjuryInvokeOrchestrator {

    private final YbWiInvokeSessionRepository invokeSessionRepository;
    private final YbWiEncounterAnchorRepository encounterAnchorRepository;
    private final WorkInjurySettleStepPlanner stepPlanner;
    private final WorkInjuryInvokeLogSupport invokeLogSupport;
    private final ChsCurrentOperatorResolver currentOperatorResolver;
    private final YbWiEncounterAnchorDomainService anchorDomainService;
    private final YbWiPreSettleDomainService preSettleDomainService;
    private final YbWiSettlementDomainService settlementDomainService;
    private final ObjectProvider<WorkInjurySettleContextLoader> settleContextOperationsProvider;

    /**
     * 准备当前 step 的 Win 业务体（不下发 Win，由前端 :5000 invoke）。
     *
     * @param request phase/encounter/contract/paymentDraft/context
     * @return sessionId、stepToken、infno、input、nextAction=INVOKE_WIN；链完成时 nextAction=COMPLETED
     */
    @Transactional
    public PrepareWorkInjuryInvokeResponse prepareInvoke(PrepareWorkInjuryInvokeRequest request) {
        WiPhase phase = WiPhase.fromCode(request.getPhase());
        if (phase == null) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "phase 无效");
        }
        if (phase == WiPhase.OP_SETTLE && request.getPaymentDraftId() == null) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "OP_SETTLE 须指定 paymentDraftId");
        }
        Long operatorId = resolveOperatorId(request.getOperatorId());

        YbWiInvokeSession session = loadOrCreateSession(request, phase, operatorId);
        // 续步 prepare 常只带 sessionId；须先解析 contractId 再 load 上下文，避免 loader 拿到 null
        Long contractId = resolveContractIdForPrepare(request, session);
        WiSettleContext ctx = loadContextForPrepare(phase, request, contractId);
        YbWiEncounterAnchor anchor = null;
        if (phase == WiPhase.RECON || phase == WiPhase.RECON_CONFIRM) {
            if (contractId == null) {
                throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, phase.getCode() + " 须指定 contractId");
            }
        } else {
            anchor = anchorDomainService.ensureAnchor(
                    request.getEncounterId(), contractId, ctx);
            if (phase == WiPhase.OP_SETTLE) {
                anchorDomainService.assertQualificationForSettle(anchor);
            }
        }

        int feeLineCount = feeLineCount(ctx);
        int stepIndex = session.getStepIndex();
        String infno = stepPlanner.resolveInfno(phase, stepIndex, anchor, feeLineCount);
        // 链上固定保留 2201 槽位；已登记则本步不调 Win，直接推进，避免「流水号已使用」
        while (WiInfnoConstants.OUTPATIENT_REG.equals(infno) && isPlatformRegistered(anchor)) {
            session.setStepIndex(session.getStepIndex() + 1);
            stepIndex = session.getStepIndex();
            infno = stepPlanner.resolveInfno(phase, stepIndex, anchor, feeLineCount);
        }
        if (!StringUtils.hasText(infno)) {
            return new PrepareWorkInjuryInvokeResponse()
                    .setSessionId(session.getSessionId())
                    .setNextAction(WiNextAction.COMPLETED.getCode());
        }

        String stepToken = UUID.randomUUID().toString();
        session.setPendingStepToken(stepToken);
        session.setStatus(WiInvokeSessionStatus.ACTIVE.getCode());
        invokeSessionRepository.save(session);

        String mdtrtIdFor2207 = resolveMdtrtIdFor2207(session, infno);
        YbSettlementRecord settlementRecord = resolveSettlementRecord(infno, request.getContext());
        int feeUploadBatchIndex =
                stepPlanner.resolveFeeUploadBatchIndex(phase, stepIndex, anchor, feeLineCount);
        Map<String, Object> input = WiPlatformInputAssembler.assemble(
                infno, ctx, anchor, request.getContext(), mdtrtIdFor2207, settlementRecord,
                feeUploadBatchIndex);
        return new PrepareWorkInjuryInvokeResponse()
                .setSessionId(session.getSessionId())
                .setStepToken(stepToken)
                .setInfno(infno)
                .setFixmedinsCode(WiBusConstants.FIXMEDINS_CODE)
                .setOperatorNo(String.valueOf(operatorId))
                .setInput(input)
                .setNextAction(WiNextAction.INVOKE_WIN.getCode());
    }

    /**
     * 单边补落后返回已完成会话（前端跳过 Win 链，直接刷新金额并确认收费）。
     *
     * @param request prepare 请求（须含 encounter/paymentDraft）
     * @param record  已补落的结算回执
     * @return nextAction=COMPLETED
     */
    @Transactional
    public PrepareWorkInjuryInvokeResponse prepareCompletedUnilateralSession(
            PrepareWorkInjuryInvokeRequest request,
            YbSettlementRecord record) {
        Long operatorId = resolveOperatorId(request != null ? request.getOperatorId() : null);
        YbWiInvokeSession session = new YbWiInvokeSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setEncounterId(request.getEncounterId());
        session.setContractId(request.getContractId() != null
                ? request.getContractId()
                : (record != null ? record.getContractId() : null));
        session.setPaymentDraftId(request.getPaymentDraftId());
        session.setPhase(WiPhase.OP_SETTLE.getCode());
        session.setStepIndex(stepPlanner.totalSteps(WiPhase.OP_SETTLE, null, 1));
        session.setStatus(WiInvokeSessionStatus.COMPLETED.getCode());
        session.setPendingStepToken(null);
        session.setTenantId(TenantUtils.getCurrentTenantId());
        session.setCreatedBy(operatorId);
        invokeSessionRepository.save(session);
        PrepareWorkInjuryInvokeResponse response = new PrepareWorkInjuryInvokeResponse()
                .setSessionId(session.getSessionId())
                .setNextAction(WiNextAction.COMPLETED.getCode())
                .setErrMsg("平台已结算，本地已补齐单边，请直接确认收费");
        return response;
    }

    /**
     * 提交 Win/DLL 回写结果；写审计、推进 step_index、执行 infno 副作用。
     *
     * @param request sessionId、stepToken、infcode/output/winRawInput
     * @return nextAction=PREPARE_NEXT / COMPLETED / FAILED；2207 成功时带 settlement 摘要
     */
    @Transactional
    public SubmitWorkInjuryInvokeResultResponse submitInvokeResult(SubmitWorkInjuryInvokeResultRequest request) {
        if (!StringUtils.hasText(request.getSessionId())) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "sessionId 不能为空");
        }
        YbWiInvokeSession session = invokeSessionRepository.findBySessionId(request.getSessionId())
                .orElseThrow(() -> new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "invoke 会话不存在"));
        if (!StringUtils.hasText(request.getStepToken())
                || !request.getStepToken().equals(session.getPendingStepToken())) {
            throw new BusinessException(WiErrorCodes.STEP_TOKEN_MISMATCH, "step_token 不匹配或已失效");
        }

        WiPhase phase = WiPhase.fromCode(session.getPhase());
        YbWiEncounterAnchor anchor = loadAnchor(session.getEncounterId());
        WiSettleContext ctx = loadContextForSubmit(phase, session, request);
        int feeLineCount = feeLineCount(ctx);
        String infno = stepPlanner.resolveInfno(phase, session.getStepIndex(), anchor, feeLineCount);

        boolean success = request.isSuccess()
                || isIdempotent2205EmptyRevoke(infno, request.getErrMsg())
                || isIdempotent2207BillAlreadyUsed(infno, request.getErrMsg())
                || isIdempotent2203AlreadySettled(infno, request.getErrMsg());

        Long invokeLogId = invokeLogSupport.write(
                session.getContractId(),
                session.getEncounterId(),
                session.getPaymentDraftId(),
                infno,
                request.getWinMsgid(),
                request.getInfcode(),
                request.getErrMsg(),
                request.getOutput(),
                success,
                request.getWinRawInput());

        // 2203「已存在结算信息」：平台已结，补本地后结束会话，勿继续 2204
        if (success && isIdempotent2203AlreadySettled(infno, request.getErrMsg())) {
            YbSettlementRecord recovered = settlementDomainService
                    .recoverUnilateralFromActivePreSettle(
                            session.getEncounterId(),
                            session.getPaymentDraftId(),
                            session.getContractId(),
                            TenantUtils.getCurrentTenantId())
                    .orElse(null);
            if (recovered == null) {
                session.setStatus(WiInvokeSessionStatus.FAILED.getCode());
                session.setPendingStepToken(null);
                invokeSessionRepository.save(session);
                return new SubmitWorkInjuryInvokeResultResponse()
                        .setSessionId(session.getSessionId())
                        .setNextAction(WiNextAction.FAILED.getCode())
                        .setErrMsg("平台已结算但本地无预结算快照可补落，请联系医保办");
            }
            if (anchor != null) {
                anchorDomainService.apply2207SetlId(anchor, recovered.getSetlId());
                anchorDomainService.markActiveFeeUpload(anchor, false);
            }
            session.setStatus(WiInvokeSessionStatus.COMPLETED.getCode());
            session.setPendingStepToken(null);
            invokeSessionRepository.save(session);
            invokeLogSupport.linkSettlementRecord(
                    invokeLogId,
                    recovered.getId(),
                    recovered.getPreSettleId(),
                    recovered.getChrgBchno());
            return new SubmitWorkInjuryInvokeResultResponse()
                    .setSessionId(session.getSessionId())
                    .setNextAction(WiNextAction.COMPLETED.getCode())
                    .setSummary("平台已结算，本地已补齐单边")
                    .setSettlementRecordId(recovered.getId())
                    .setSetlId(recovered.getSetlId())
                    .setChrgBchno(recovered.getChrgBchno())
                    .setMedfeeSumamt(recovered.getMedfeeSumamt())
                    .setFundPaySumamt(recovered.getFundPaySumamt())
                    .setPsnCashPay(recovered.getPsnCashPay());
        }

        if (!success) {
            session.setStatus(WiInvokeSessionStatus.FAILED.getCode());
            session.setPendingStepToken(null);
            invokeSessionRepository.save(session);
            return new SubmitWorkInjuryInvokeResultResponse()
                    .setSessionId(session.getSessionId())
                    .setNextAction(WiNextAction.FAILED.getCode())
                    .setErrMsg(request.getErrMsg());
        }

        YbSettlementRecord settlementRecord = applySubmitSideEffect(
                infno, anchor, ctx, session, request, invokeLogId);

        session.setStepIndex(session.getStepIndex() + 1);
        session.setPendingStepToken(null);

        anchor = loadAnchor(session.getEncounterId());
        boolean hasMore = stepPlanner.hasMoreSteps(phase, session.getStepIndex(), anchor, feeLineCount);
        if (hasMore) {
            session.setStatus(WiInvokeSessionStatus.ACTIVE.getCode());
            invokeSessionRepository.save(session);
            return new SubmitWorkInjuryInvokeResultResponse()
                    .setSessionId(session.getSessionId())
                    .setNextAction(WiNextAction.PREPARE_NEXT.getCode());
        }

        session.setStatus(WiInvokeSessionStatus.COMPLETED.getCode());
        invokeSessionRepository.save(session);
        SubmitWorkInjuryInvokeResultResponse response = new SubmitWorkInjuryInvokeResultResponse()
                .setSessionId(session.getSessionId())
                .setNextAction(WiNextAction.COMPLETED.getCode())
                .setSummary("phase " + session.getPhase() + " 完成");
        if (settlementRecord != null) {
            response.setSettlementRecordId(settlementRecord.getId())
                    .setSetlId(settlementRecord.getSetlId())
                    .setChrgBchno(settlementRecord.getChrgBchno())
                    .setMedfeeSumamt(settlementRecord.getMedfeeSumamt())
                    .setFundPaySumamt(settlementRecord.getFundPaySumamt())
                    .setPsnCashPay(settlementRecord.getPsnCashPay());
        }
        return response;
    }

    /**
     * 解析操作员 id：请求未传时使用当前登录用户。
     *
     * @param requestOperatorId prepare 请求中的 operatorId
     * @return 操作员 id
     */
    private Long resolveOperatorId(Long requestOperatorId) {
        if (requestOperatorId != null) {
            return requestOperatorId;
        }
        return currentOperatorResolver.requireOperatorId("请先登录后再操作工伤业务");
    }

    /**
     * prepare 用 contractId：请求优先，否则会话，再否则已有锚点。
     *
     * @param request prepare 请求
     * @param session 当前会话
     * @return contractId；均无时 null
     */
    private Long resolveContractIdForPrepare(
            PrepareWorkInjuryInvokeRequest request, YbWiInvokeSession session) {
        if (request != null && request.getContractId() != null) {
            return request.getContractId();
        }
        if (session != null && session.getContractId() != null) {
            return session.getContractId();
        }
        Long encounterId = request != null ? request.getEncounterId() : null;
        if (encounterId == null && session != null) {
            encounterId = session.getEncounterId();
        }
        if (encounterId == null) {
            return null;
        }
        return encounterAnchorRepository.findByEncounterId(encounterId)
                .map(YbWiEncounterAnchor::getContractId)
                .orElse(null);
    }

    /**
     * 按 sessionId 加载已有会话，否则新建 ACTIVE 会话（stepIndex=0）。
     *
     * @param request    prepare 请求
     * @param phase      业务阶段
     * @param operatorId 操作员 id（写 createdBy）
     * @return 持久化后的会话
     */
    private YbWiInvokeSession loadOrCreateSession(
            PrepareWorkInjuryInvokeRequest request, WiPhase phase, Long operatorId) {
        if (StringUtils.hasText(request.getSessionId())) {
            Optional<YbWiInvokeSession> existing = invokeSessionRepository.findBySessionId(request.getSessionId());
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        YbWiInvokeSession session = new YbWiInvokeSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setEncounterId(request.getEncounterId());
        session.setContractId(request.getContractId());
        session.setPaymentDraftId(request.getPaymentDraftId());
        session.setPhase(phase.getCode());
        session.setStepIndex(0);
        session.setStatus(WiInvokeSessionStatus.ACTIVE.getCode());
        session.setTenantId(TenantUtils.getCurrentTenantId());
        session.setCreatedBy(operatorId);
        return invokeSessionRepository.save(session);
    }

    /**
     * 按就诊 id 加载 anchor（不存在时返回 null，不抛错）。
     *
     * @param encounterId 就诊 id
     * @return 锚点或 null
     */
    private YbWiEncounterAnchor loadAnchor(Long encounterId) {
        if (encounterId == null) {
            return null;
        }
        return encounterAnchorRepository.findByEncounterId(encounterId).orElse(null);
    }

    /**
     * prepare 阶段加载 HIS 上下文：OP_SETTLE 含 chargeLines，其余 phase 仅登记字段。
     *
     * @param phase      业务阶段
     * @param request    prepare 请求
     * @param contractId 已解析的合同 id（续步可能仅来自会话/锚点）
     * @return 组装 input 用上下文；RECON 等无 encounter 时可为 null
     */
    private WiSettleContext loadContextForPrepare(
            WiPhase phase, PrepareWorkInjuryInvokeRequest request, Long contractId) {
        WorkInjurySettleContextLoader loader = settleContextOperationsProvider.getIfAvailable();
        if (phase == WiPhase.OP_SETTLE) {
            if (loader == null) {
                throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "HIS 上下文加载器未装配");
            }
            return loader.loadSettleContext(
                    request.getEncounterId(),
                    contractId,
                    request.getPaymentDraftId(),
                    resolveChargeItemIds(request.getContext()));
        }
        if (loader != null && request.getEncounterId() != null) {
            return loader.loadRegisterContext(request.getEncounterId(), contractId);
        }
        return null;
    }

    /** OP_SETTLE chargeLines 条数；非结算 phase 为 0。 */
    private static int feeLineCount(WiSettleContext ctx) {
        if (ctx == null || ctx.getChargeLines() == null) {
            return 0;
        }
        return ctx.getChargeLines().size();
    }

    private static boolean isPlatformRegistered(YbWiEncounterAnchor anchor) {
        return anchor != null && Boolean.TRUE.equals(anchor.getPlatformRegistered());
    }

    /**
     * 2205 整单撤：平台无未结算处方时返回该文案，视为已干净，按成功推进。
     */
    private static boolean isIdempotent2205EmptyRevoke(String infno, String errMsg) {
        if (!WiInfnoConstants.OUTPATIENT_FEE_REVOKE.equals(infno) || !StringUtils.hasText(errMsg)) {
            return false;
        }
        return errMsg.contains("没有需要删除的未结算的处方");
    }

    /**
     * 2207：平台已用该单据号结算成功，重提报「已使用」——按成功推进并补落本地回执。
     */
    private static boolean isIdempotent2207BillAlreadyUsed(String infno, String errMsg) {
        if (!WiInfnoConstants.OUTPATIENT_SETTLE.equals(infno) || !StringUtils.hasText(errMsg)) {
            return false;
        }
        return errMsg.contains("单据号") && errMsg.contains("已使用");
    }

    /**
     * 2203：平台已有结算信息不可改登记——视为单边信号，补本地后结束链。
     */
    private static boolean isIdempotent2203AlreadySettled(String infno, String errMsg) {
        if (!WiInfnoConstants.OUTPATIENT_VISIT_UPLOAD.equals(infno) || !StringUtils.hasText(errMsg)) {
            return false;
        }
        return errMsg.contains("已存在结算信息");
    }

    /**
     * submit 阶段重载上下文（2206/2207 副作用写预结算/结算时须 ctx）。
     *
     * @param phase   业务阶段
     * @param session invoke 会话
     * @param request submit 请求
     * @return OP_SETTLE 时上下文；其他 phase 为 null
     */
    private WiSettleContext loadContextForSubmit(
            WiPhase phase,
            YbWiInvokeSession session,
            SubmitWorkInjuryInvokeResultRequest request) {
        if (phase != WiPhase.OP_SETTLE) {
            return null;
        }
        WorkInjurySettleContextLoader loader = settleContextOperationsProvider.getIfAvailable();
        if (loader == null) {
            return null;
        }
        if (request.getContext() != null && request.getContext().containsKey("chargeItemIds")) {
            return loader.loadSettleContext(
                    session.getEncounterId(),
                    session.getContractId(),
                    session.getPaymentDraftId(),
                    resolveChargeItemIds(request.getContext()));
        }
        return loader.loadRegisterContext(session.getEncounterId(), session.getContractId());
    }

    /**
     * 从 context.chargeItemIds 解析收费项 id 列表（支持 Number / String）。
     *
     * @param context prepare/submit 扩展上下文
     * @return 非空 id 列表
     */
    private List<Long> resolveChargeItemIds(Map<String, Object> context) {
        if (context == null || !context.containsKey("chargeItemIds")) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "OP_SETTLE 须在 context.chargeItemIds 传入收费项");
        }
        Object raw = context.get("chargeItemIds");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "context.chargeItemIds 无效");
        }
        List<Long> ids = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Number number) {
                ids.add(number.longValue());
            } else if (item != null && StringUtils.hasText(String.valueOf(item))) {
                ids.add(Long.parseLong(String.valueOf(item).trim()));
            }
        }
        if (ids.isEmpty()) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "context.chargeItemIds 无效");
        }
        return ids;
    }

    /**
     * 2207 prepare 时从 ACTIVE 预结算快照取 mdtrt_id。
     *
     * @param session invoke 会话
     * @param infno   当前步 infno
     * @return mdtrt_id；非 2207 时为 null
     */
    private String resolveMdtrtIdFor2207(YbWiInvokeSession session, String infno) {
        if (!WiInfnoConstants.OUTPATIENT_SETTLE.equals(infno)) {
            return null;
        }
        return preSettleDomainService.findActiveMdtrtId(session.getEncounterId(), session.getPaymentDraftId())
                .orElseThrow(() -> new BusinessException(
                        WiErrorCodes.INVALID_INVOKE_SESSION, "2207 须先完成 2206 预结算"));
    }

    /**
     * 按 infno 执行 submit 成功后的 domain 副作用（anchor / pre_settle / settlement）。
     *
     * @param infno        当前步 infno
     * @param anchor       就诊锚点
     * @param ctx          HIS 上下文（2206/2207 用）
     * @param session      invoke 会话
     * @param request      submit 请求
     * @param invokeLogId  刚写入的 yb_invoke_log.id
     * @return 2207/2208 产生的结算回执；其余 infno 为 null
     */
    private YbSettlementRecord applySubmitSideEffect(
            String infno,
            YbWiEncounterAnchor anchor,
            WiSettleContext ctx,
            YbWiInvokeSession session,
            SubmitWorkInjuryInvokeResultRequest request,
            Long invokeLogId) {
        Map<String, Object> output = request.getOutput();
        if (infno == null) {
            return null;
        }
        if (WiInfnoConstants.PERSON_INFO.equals(infno)) {
            Wi1101Output parsed = Wi1101OutputParser.parse(output);
            anchorDomainService.apply1101Submit(anchor, parsed.getPsnNo(), parsed.getInjureAffirmId());
            return null;
        }
        if (WiInfnoConstants.APPROVAL_UPLOAD.equals(infno)) {
            Wi2301Output parsed = Wi2301OutputParser.parse(output);
            anchorDomainService.apply2301Submit(anchor, parsed.getQualificationId());
            return null;
        }
        if (WiInfnoConstants.QUALIFICATION_QUERY.equals(infno)) {
            Wi9102Output parsed = Wi9102OutputParser.parse(output);
            Wi9102QualificationRow preferred = pickPreferredQualificationRow(parsed);
            if (preferred != null && StringUtils.hasText(preferred.getQualificationId())) {
                String approveType = preferred.getApproveType() == null
                        ? null
                        : preferred.getApproveType().trim();
                anchorDomainService.bindQualification(
                        anchor, preferred.getQualificationId().trim(), approveType);
            } else {
                // 无可用类型12：清空旧资格（常为类型10），避免 2201「审批类型与就医类型不符」
                anchorDomainService.clearQualification(anchor);
            }
            return null;
        }
        if (WiInfnoConstants.OUTPATIENT_REG.equals(infno)) {
            anchorDomainService.markPlatformRegistered(anchor);
            return null;
        }
        if (WiInfnoConstants.OUTPATIENT_FEE_UPLOAD.equals(infno)) {
            anchorDomainService.markActiveFeeUpload(anchor, true);
            return null;
        }
        if (WiInfnoConstants.OUTPATIENT_FEE_REVOKE.equals(infno)) {
            anchorDomainService.markActiveFeeUpload(anchor, false);
            return null;
        }
        if (WiInfnoConstants.OUTPATIENT_PRE_SETTLE.equals(infno)) {
            Wi2206Output parsed = Wi2206OutputParser.parse(output);
            preSettleDomainService.saveActiveFrom2206(
                ctx,
                parsed.getMdtrtId(),
                parsed.getMedfeeSumamt(),
                parsed.getHifpPay(),
                parsed.getPsnCashPay(),
                JSON.toJSONString(parsed),
                TenantUtils.getCurrentTenantId());
            return null;
        }
        if (WiInfnoConstants.OUTPATIENT_SETTLE.equals(infno)) {
            Wi2207Output parsed = Wi2207OutputParser.parse(output);
            String mdtrtId = parsed.getMdtrtId();
            String setlId = parsed.getSetlId();
            // 平台已结算但本次失败文案为「单据号已使用」：出参空，从预结算快照回填
            if (!StringUtils.hasText(mdtrtId) || !StringUtils.hasText(setlId)) {
                String fromPre = preSettleDomainService
                        .findActiveMdtrtId(session.getEncounterId(), session.getPaymentDraftId())
                        .orElse(null);
                if (!StringUtils.hasText(mdtrtId)) {
                    mdtrtId = fromPre;
                }
                if (!StringUtils.hasText(setlId)) {
                    setlId = StringUtils.hasText(parsed.getSetlId()) ? parsed.getSetlId() : mdtrtId;
                }
            }
            YbSettlementRecord record = settlementDomainService.saveConfirmedFrom2207(
                    session.getEncounterId(),
                    session.getContractId(),
                    session.getPaymentDraftId(),
                    setlId,
                    mdtrtId,
                    null,
                    null,
                    parsed.getMedfeeSumamt(),
                    parsed.getHifpPay(),
                    parsed.getPsnCashPay(),
                    JSON.toJSONString(parsed),
                    request.getWinMsgid(),
                    TenantUtils.getCurrentTenantId());
            anchorDomainService.apply2207SetlId(anchor, record.getSetlId());
            anchorDomainService.markActiveFeeUpload(anchor, false);
            preSettleDomainService.markSettled(session.getEncounterId(), session.getPaymentDraftId());
            invokeLogSupport.linkSettlementRecord(
                    invokeLogId,
                    record.getId(),
                    record.getPreSettleId(),
                    record.getChrgBchno());
            return record;
        }
        if (WiInfnoConstants.OUTPATIENT_SETTLE_REVOKE.equals(infno)) {
            YbSettlementRecord record = resolveSettlementRecordForSubmit(request);
            if (record == null) {
                throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "2208 缺少 settlementRecordId");
            }
            YbSettlementRecord revoked = settlementDomainService.markRevoked2208(
                    record.getId(), TenantUtils.getCurrentTenantId(), invokeLogId);
            anchorDomainService.clearSetlIdAfter2208(anchor);
            if (revoked.getPreSettleId() != null) {
                preSettleDomainService.markRevokedByPreSettleId(revoked.getPreSettleId());
            }
            invokeLogSupport.linkSettlementRecord(
                    invokeLogId,
                    revoked.getId(),
                    revoked.getPreSettleId(),
                    revoked.getChrgBchno());
            return revoked;
        }
        return null;
    }

    /**
     * 2208 prepare 时从 context 解析待撤销结算回执。
     *
     * @param infno   当前步 infno
     * @param context prepare 扩展上下文
     * @return 结算回执；非 2208 时为 null
     */
    private YbSettlementRecord resolveSettlementRecord(String infno, Map<String, Object> context) {
        if (!WiInfnoConstants.OUTPATIENT_SETTLE_REVOKE.equals(infno)) {
            return null;
        }
        return loadSettlementRecord(contextLong(context, "settlementRecordId"));
    }

    /**
     * 2208 submit 时从 request.context 解析结算回执。
     *
     * @param request submit 请求
     * @return 结算回执；context 无 settlementRecordId 时为 null
     */
    private YbSettlementRecord resolveSettlementRecordForSubmit(SubmitWorkInjuryInvokeResultRequest request) {
        if (request.getContext() != null && request.getContext().containsKey("settlementRecordId")) {
            return loadSettlementRecord(contextLong(request.getContext(), "settlementRecordId"));
        }
        return null;
    }

    /**
     * 按 id 加载租户内结算回执（不存在时抛 WI-006）。
     *
     * @param settlementRecordId yb_settlement_record.id
     * @return 结算回执；id 为 null 时返回 null
     */
    private YbSettlementRecord loadSettlementRecord(Long settlementRecordId) {
        if (settlementRecordId == null) {
            return null;
        }
        return settlementDomainService.findByIdAndTenant(settlementRecordId, TenantUtils.getCurrentTenantId())
                .orElseThrow(() -> new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "结算回执不存在"));
    }

    /**
     * 从 context Map 安全解析 Long 字段（Number / String）。
     *
     * @param context 扩展上下文
     * @param key     键名
     * @return 解析值；缺失或无效时为 null
     */
    private Long contextLong(Map<String, Object> context, String key) {
        if (context == null || !context.containsKey(key)) {
            return null;
        }
        Object raw = context.get(key);
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw != null && StringUtils.hasText(String.valueOf(raw))) {
            return Long.parseLong(String.valueOf(raw).trim());
        }
        return null;
    }

    /**
     * 9102 自动绑定：仅选未过期且审批类型＝院点联网就医类型（12）的行。
     * <p>类型 10 等虽可能未过期，但院点「医疗类别10暂未开通联网结算」；若强行 med_type=12 又会报
     * 「审批类型10跟就医类型12不符」。故不回落到非 12 行。</p>
     *
     * @param parsed 9102 出参
     * @return 待绑定行；无可用类型12时 null
     */
    private Wi9102QualificationRow pickPreferredQualificationRow(Wi9102Output parsed) {
        if (parsed == null || parsed.getRows() == null || parsed.getRows().isEmpty()) {
            return null;
        }
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String settleType = WiPlatformConstants.DEFAULT_MED_TYPE;
        List<Wi9102QualificationRow> rows = parsed.getRows();
        Wi9102QualificationRow best = null;
        String bestEnd = null;
        for (Wi9102QualificationRow row : rows) {
            if (row == null || !StringUtils.hasText(row.getQualificationId())) {
                continue;
            }
            String approveType = row.getApproveType() == null ? "" : String.valueOf(row.getApproveType()).trim();
            if (!settleType.equals(approveType)) {
                continue;
            }
            String end = row.getEndDate() == null ? "" : row.getEndDate().trim();
            if (StringUtils.hasText(end) && end.compareTo(today) < 0) {
                continue;
            }
            if (best == null || end.compareTo(bestEnd == null ? "" : bestEnd) > 0) {
                best = row;
                bestEnd = end;
            }
        }
        return best;
    }
}
