/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.physicalexam.financial.charge.service;

import com.openhis.health.application.outpatient.financial.charge.support.OutpatientChargeSettlementGate;
import com.openhis.health.application.outpatient.financial.charge.support.OutpatientPaymentDraftLifecycleOrchestrator;
import com.openhis.health.application.physicalexam.financial.charge.dto.PhysicalExamChargeQueueRowDto;
import com.openhis.health.application.physicalexam.financial.charge.mapper.PhysicalExamChargeMapper;
import com.openhis.health.application.physicalexam.financial.charge.request.ConfirmPhysicalExamChargeSettlementRequest;
import com.openhis.health.application.physicalexam.financial.charge.request.LoadPhysicalExamChargeItemsRequest;
import com.openhis.health.application.physicalexam.financial.charge.request.LoadPhysicalExamChargePageInitRequest;
import com.openhis.health.application.physicalexam.financial.charge.request.PagePhysicalExamChargeQueueRequest;
import com.openhis.health.application.physicalexam.financial.charge.request.PreSettlePhysicalExamChargesRequest;
import com.openhis.health.application.physicalexam.financial.charge.request.RefreshPhysicalExamChargeStatusRequest;
import com.openhis.health.application.physicalexam.financial.charge.response.ConfirmPhysicalExamChargeSettlementResponse;
import com.openhis.health.application.physicalexam.financial.charge.response.LoadPhysicalExamChargeItemsResponse;
import com.openhis.health.application.physicalexam.financial.charge.response.LoadPhysicalExamChargePageInitResponse;
import com.openhis.health.application.physicalexam.financial.charge.response.PagePhysicalExamChargeQueueResponse;
import com.openhis.health.application.physicalexam.financial.charge.response.PreSettlePhysicalExamChargesResponse;
import com.openhis.health.application.physicalexam.financial.charge.response.RefreshPhysicalExamChargeStatusResponse;
import com.openhis.health.application.physicalexam.financial.charge.support.PhysicalExamChargeEncounterQueueLoader;
import com.openhis.health.application.physicalexam.financial.charge.support.PhysicalExamPlannedChargeEnsureSupport;
import com.openhis.health.application.shared.financial.PaymentChannelOptionProvider;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.administration.chargeitem.command.BatchChargeItemIdsCommand;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientChargeQueueEncounterQuery;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.command.CreatePaymentDraftFromPreSettleCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.PaymentDetailItemCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.SettleOutpatientPaymentCommand;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentRecDetailQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationDomainService;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.peis.encounterext.queries.model.PeisEncounterExtModel;
import com.openhis.health.domain.peis.encounterext.service.PeisEncounterExtQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.PhysicalExamChargeConstants;
import com.openhis.health.domain.share.constants.PhysicalExamChargeQueueTab;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.PaymentChannel;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.health.infrastructure.integration.financial.orchestration.InvoiceAfterPaymentOrchestration;
import com.openhis.health.infrastructure.integration.financial.orchestration.PaymentReconciliationSettleOrchestration;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 体检收费应用服务（AS-PE-FIN-001）：PE 待收/已收队列、明细、自费预结算与正式结算。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>Gate：仅 {@code Encounter.class=PE}；写路径操作人由会话解析；正式结算防重与草稿一致性。</li>
 *   <li>Pre：队列经 {@link PhysicalExamChargeEncounterQueueLoader} 收敛；明细前懒生成 PLANNED（GAP-PE-CHG-02）。</li>
 *   <li>Cmd：复用门诊自费支付编排（草稿生命周期、落账、开票）；不写医保/诊断门禁/医嘱 sync。</li>
 *   <li>Asm：经 {@link PhysicalExamChargeMapper} 装配响应，禁止 JSON 串出参。</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} → {@code POST /api/app/physicalExamCharge/{methodName}}；不另写 RestController。</p>
 */
@Service
@RequiredArgsConstructor
public class PhysicalExamChargeAppService {

    /** 写路径操作人（adm_practitioner.id）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;
    /** 分页窗口门禁。 */
    private final PageWindowGate pageWindowGate;
    /** 支付渠道下拉。 */
    private final PaymentChannelOptionProvider paymentChannelOptionProvider;
    /** 收费项状态等术语下拉。 */
    private final CodeSystemQueryService codeSystemQueryService;
    /** PE 就诊存在性与摘要。 */
    private final EncounterQueryService encounterQueryService;
    /** 队列患者姓名回补。 */
    private final PatientQueryService patientQueryService;
    /** 体检流水号/套餐摘要。 */
    private final PeisEncounterExtQueryService peisEncounterExtQueryService;
    /** 收费项预结算/正式结算写侧。 */
    private final ChargeItemDomainService chargeItemDomainService;
    /** 收费项读侧与 isEncounterPaid。 */
    private final ChargeItemQueryService chargeItemQueryService;
    /** 明细展示定价定义。 */
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    /** 自费账户解析。 */
    private final AccountQueryService accountQueryService;
    /** 支付草稿创建。 */
    private final PaymentReconciliationDomainService paymentReconciliationDomainService;
    /** 支付草稿/成功流水读侧。 */
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    /** PE 收银队列分页。 */
    private final PhysicalExamChargeEncounterQueueLoader physicalExamChargeEncounterQueueLoader;
    /** 懒生成 PLANNED 收费项。 */
    private final PhysicalExamPlannedChargeEnsureSupport physicalExamPlannedChargeEnsureSupport;
    /** 请求/响应与命令装配。 */
    private final PhysicalExamChargeMapper physicalExamChargeMapper;
    /** 支付正式落账编排。 */
    private final PaymentReconciliationSettleOrchestration paymentReconciliationSettleOrchestration;
    /** 实付&gt;0 开票草稿。 */
    private final InvoiceAfterPaymentOrchestration invoiceAfterPaymentOrchestration;
    /** 同就诊 DRAFT 支付作废。 */
    private final OutpatientPaymentDraftLifecycleOrchestrator outpatientPaymentDraftLifecycleOrchestrator;
    /** 正式结算前 PLANNED+锚点可收费 Gate。 */
    private final OutpatientChargeSettlementGate outpatientChargeSettlementGate;

    /**
     * IF-PE-CHG-01：加载体检收费页初始化数据。
     *
     * @param request 初始化请求
     * @return 初始化响应
     */
    public LoadPhysicalExamChargePageInitResponse loadPageInit(LoadPhysicalExamChargePageInitRequest request) {
        try {
            // Pre：下拉选项
            return physicalExamChargeMapper.toLoadPageInitResponse(
                codeSystemQueryService.getOptionListByEnum(ChargeItemStatus.class),
                paymentChannelOptionProvider.loadUserSelectableOptions());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT,
                PhysicalExamChargeConstants.MESSAGE_CODE_INIT_LOAD_FAILED + ": 收费页初始化失败");
        }
    }

    /**
     * IF-PE-CHG-02：分页查询待收费队列。
     *
     * @param request 分页请求
     * @return 分页响应
     */
    public PagePhysicalExamChargeQueueResponse pageWaitingQueue(PagePhysicalExamChargeQueueRequest request) {
        return this.pageQueueByTab(request, PhysicalExamChargeQueueTab.WAITING);
    }

    /**
     * IF-PE-CHG-03：分页查询已收费队列。
     *
     * @param request 分页请求
     * @return 分页响应
     */
    public PagePhysicalExamChargeQueueResponse pageCompleteQueue(PagePhysicalExamChargeQueueRequest request) {
        return this.pageQueueByTab(request, PhysicalExamChargeQueueTab.COMPLETE);
    }

    /**
     * IF-PE-CHG-04：加载体检收费明细。
     *
     * @param request 明细请求
     * @return 明细响应
     */
    public LoadPhysicalExamChargeItemsResponse loadChargeItems(LoadPhysicalExamChargeItemsRequest request) {
        try {
            // Gate：就诊主键必填
            if (request == null || request.getEncounterId() == null) {
                throw new BusinessException(
                    ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                    PhysicalExamChargeConstants.MESSAGE_CODE_ENCOUNTER_INVALID + ": 就诊标识不能为空");
            }
            EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
                request.getEncounterId(),
                EncounterClass.PE.getCode(),
                ErrorCodeConstants.ENCOUNTER_INVALID_STATUS,
                PhysicalExamChargeConstants.MESSAGE_CODE_ENCOUNTER_INVALID + ": 非体检就诊或记录不存在");
            if (request.getPatientId() != null && !request.getPatientId().equals(encounter.getPatientId())) {
                throw new BusinessException(
                    ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT,
                    PhysicalExamChargeConstants.MESSAGE_CODE_ENCOUNTER_INVALID + ": 患者与就诊不一致");
            }
            Long operatorId = currentPractitionerResolver.requireOperatorId(
                ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT,
                "当前会话操作人不能为空");
            // Pre：懒生成 PLANNED 收费项
            physicalExamPlannedChargeEnsureSupport.ensureForEncounter(request.getEncounterId(), operatorId);
            List<ChargeItem> rows = this.listBillableChargeItems(request.getEncounterId());
            Map<Long, String> itemLabels = this.buildItemLabelMap(rows);
            LoadPhysicalExamChargeItemsResponse response = new LoadPhysicalExamChargeItemsResponse();
            response.setEncounterId(request.getEncounterId());
            response.setPatientId(encounter.getPatientId());
            PeisEncounterExtModel ext = peisEncounterExtQueryService.findOptionalByEncounterId(request.getEncounterId()).orElse(null);
            if (ext != null) {
                response.setBusNo(ext.getBusNo());
                response.setPackageName(this.resolvePackageName(ext));
            }
            response.setItems(physicalExamChargeMapper.toChargeItemRows(rows, itemLabels));
            BigDecimal plannedTotal = BigDecimal.ZERO;
            BigDecimal settledTotal = BigDecimal.ZERO;
            for (ChargeItem row : rows) {
                if (row == null || row.getTotalPrice() == null) {
                    continue;
                }
                if (ChargeItemStatus.PLANNED.getCode().equals(row.getStatusEnum())) {
                    plannedTotal = plannedTotal.add(row.getTotalPrice());
                }
                if (ChargeItemStatus.SETTLED.getCode().equals(row.getStatusEnum())) {
                    settledTotal = settledTotal.add(row.getTotalPrice());
                }
            }
            response.setPlannedTotalAmount(MonetaryAmountSupport.scaleSettle(plannedTotal));
            response.setSettledTotalAmount(MonetaryAmountSupport.scaleSettle(settledTotal));
            return response;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT,
                PhysicalExamChargeConstants.MESSAGE_CODE_ENCOUNTER_INVALID + ": 明细加载失败");
        }
    }

    /**
     * IF-PE-CHG-05：体检预结算（自费主路径）。
     *
     * @param request 预结算请求
     * @return 预结算响应
     */
    public PreSettlePhysicalExamChargesResponse preSettleCharges(PreSettlePhysicalExamChargesRequest request) {
        // Gate：参数完整
        if (request == null || request.getEncounterId() == null
            || request.getChargeItemIds() == null || request.getChargeItemIds().isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                PhysicalExamChargeConstants.MESSAGE_CODE_PRESETTLE_NO_CHARGE_ITEMS + ": 预结算失败（账户/项目/合同）");
        }
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            request.getEncounterId(),
            EncounterClass.PE.getCode(),
            ErrorCodeConstants.ENCOUNTER_INVALID_STATUS,
            PhysicalExamChargeConstants.MESSAGE_CODE_ENCOUNTER_INVALID + ": 非体检就诊");
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT,
            "当前会话操作人不能为空");
        ChargeItemQueryCommand queryCommand = new ChargeItemQueryCommand();
        queryCommand.setIds(request.getChargeItemIds());
        List<ChargeItem> chargeItems = chargeItemQueryService.loadChargeItemsByIds(queryCommand);
        this.assertChargeItemsLoaded(request.getChargeItemIds(), chargeItems);
        this.assertChargeItemsBelongEncounter(chargeItems, request.getEncounterId());
        BatchChargeItemIdsCommand preSettleCommand =
            physicalExamChargeMapper.toBatchChargeItemIdsCommand(request.getChargeItemIds(), operatorId);
        // Cmd：作废遗留草稿 → 预结算 → 创建支付草稿
        outpatientPaymentDraftLifecycleOrchestrator.cancelAllDraftsForEncounter(request.getEncounterId(), null);
        chargeItemDomainService.preSettleChargeItems(preSettleCommand);
        BigDecimal totalAmount = MonetaryAmountSupport.sumThenSettle(this.sumChargeItemAmount(chargeItems));
        String contractNo = this.resolveContractNoFromChargeItems(chargeItems, request.getEncounterId());
        CreatePaymentDraftFromPreSettleCommand paymentDraftCommand = physicalExamChargeMapper.toCreatePaymentDraftFromPreSettleCommand(
            request.getEncounterId(),
            encounter.getPatientId(),
            this.joinIds(request.getChargeItemIds()),
            totalAmount,
            operatorId,
            contractNo);
        paymentDraftCommand.setPaymentDetails(this.buildPreSettlePaymentDetailDraftLines(request.getEncounterId(), chargeItems));
        Long paymentDraftId = paymentReconciliationDomainService.createPaymentDraftFromPreSettle(paymentDraftCommand);
        Map<Long, String> itemLabels = this.buildItemLabelMap(chargeItems);
        // Asm：返回草稿与预览
        return physicalExamChargeMapper.toPreSettleResponse(
            paymentDraftId,
            totalAmount,
            physicalExamChargeMapper.toPreSettlePreviews(chargeItems, itemLabels));
    }

    /**
     * IF-PE-CHG-06：体检正式结算（自费主路径，跳过医保/syncAdvice）。
     *
     * @param request 正式结算请求
     * @return 正式结算响应
     */
    public ConfirmPhysicalExamChargeSettlementResponse confirmChargeSettlement(
        ConfirmPhysicalExamChargeSettlementRequest request) {
        // Gate：参数完整
        if (request == null || request.getPaymentDraftId() == null || request.getEncounterId() == null
            || request.getChargeItemIds() == null || request.getChargeItemIds().isEmpty()
            || request.getPaymentDetails() == null || request.getPaymentDetails().isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                PhysicalExamChargeConstants.MESSAGE_CODE_SETTLE_FAILED + ": 收费失败（请勿重复提交）");
        }
        PaymentReconciliationQueryModel payment = paymentReconciliationQueryService.mustGetPayment(request.getPaymentDraftId());
        if (!request.getEncounterId().equals(payment.getEncounterId())) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT,
                PhysicalExamChargeConstants.MESSAGE_CODE_SETTLE_DRAFT_INVALID + ": 支付草稿与就诊不一致");
        }
        if (!PaymentStatus.DRAFT.equals(payment.getStatusEnum())) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT,
                PhysicalExamChargeConstants.MESSAGE_CODE_SETTLE_DRAFT_INVALID + ": 支付草稿状态非法");
        }
        List<PaymentRecDetailQueryModel> details =
            paymentReconciliationQueryService.listPaymentRecDetailsByPaymentId(request.getPaymentDraftId());
        if (details.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                PhysicalExamChargeConstants.MESSAGE_CODE_SETTLE_DRAFT_DETAIL_EMPTY + ": 支付草稿明细为空");
        }
        if (!this.sameChargeItemSet(payment.getChargeItemIds(), request.getChargeItemIds())) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT,
                PhysicalExamChargeConstants.MESSAGE_CODE_SETTLE_CHARGE_ITEMS_MISMATCH + ": 收费项与草稿不一致");
        }
        this.assertNotAlreadySettled(request.getEncounterId(), request.getChargeItemIds());
        ChargeItemQueryCommand chargeItemQueryCommand = new ChargeItemQueryCommand();
        chargeItemQueryCommand.setIds(request.getChargeItemIds());
        List<ChargeItem> chargeItems = chargeItemQueryService.loadChargeItemsByIds(chargeItemQueryCommand);
        this.assertChargeItemsLoaded(request.getChargeItemIds(), chargeItems);
        this.assertChargeItemsBelongEncounter(chargeItems, request.getEncounterId());
        this.assertNotSettledChargeItems(chargeItems);
        // Gate：PE 明细 wkf_service_request 锚点可收费
        outpatientChargeSettlementGate.assertChargeItemsSettleable(chargeItems);
        BigDecimal expected = MonetaryAmountSupport.sumThenSettle(this.sumChargeItemAmount(chargeItems));
        this.assertDraftDetailAmountMatchesExpected(details, expected);
        BigDecimal actual = this.sumSubmitAmount(request);
        if (expected.compareTo(actual) != 0) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT,
                PhysicalExamChargeConstants.MESSAGE_CODE_SETTLE_AMOUNT_MISMATCH + ": 支付金额与预结算不一致");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT,
            "当前会话操作人不能为空");
        SettleOutpatientPaymentCommand settleCommand = physicalExamChargeMapper.toSettleOutpatientPaymentCommand(request);
        this.attachDraftAccountsToPayReceiptDetails(settleCommand.getPayReceiptDetails(), details);
        Long paymentId = paymentReconciliationSettleOrchestration.settleOutpatientPayment(settleCommand);
        BatchChargeItemIdsCommand batchCommand =
            physicalExamChargeMapper.toBatchChargeItemIdsCommand(request.getChargeItemIds(), operatorId);
        chargeItemDomainService.settleChargeItems(batchCommand);
        outpatientPaymentDraftLifecycleOrchestrator.cancelAllDraftsForEncounter(request.getEncounterId(), null);
        // Cmd：票面按收费项合计开票（含医保已付）；现金 displayAmount=0 仍开票（BR-INV-008）
        BigDecimal invoiceFaceAmount = MonetaryAmountSupport.sumThenSettle(this.sumChargeItemAmount(chargeItems));
        Long invoiceId = invoiceAfterPaymentOrchestration.createDraftAndScheduleIssueIfPayable(
            physicalExamChargeMapper.toCreateDraftInvoiceCommand(payment, paymentId, operatorId, invoiceFaceAmount));
        return physicalExamChargeMapper.toConfirmSettlementResponse(paymentId, invoiceId);
    }

    /**
     * IF-PE-CHG-07：刷新单就诊收费态摘要。
     *
     * @param request 刷新请求
     * @return 收费态响应
     */
    public RefreshPhysicalExamChargeStatusResponse refreshChargeStatus(RefreshPhysicalExamChargeStatusRequest request) {
        RefreshPhysicalExamChargeStatusResponse response = new RefreshPhysicalExamChargeStatusResponse();
        response.setPaid(Boolean.FALSE);
        response.setUnpaid(Boolean.FALSE);
        response.setUnknown(Boolean.FALSE);
        if (request == null || request.getEncounterId() == null) {
            response.setUnknown(Boolean.TRUE);
            return response;
        }
        try {
            encounterQueryService.mustGetEncounterOfClass(
                request.getEncounterId(),
                EncounterClass.PE.getCode(),
                ErrorCodeConstants.ENCOUNTER_INVALID_STATUS,
                PhysicalExamChargeConstants.MESSAGE_CODE_ENCOUNTER_INVALID);
            boolean paid = chargeItemQueryService.isEncounterPaid(request.getEncounterId());
            response.setPaid(paid);
            response.setUnpaid(!paid);
        } catch (RuntimeException ex) {
            response.setUnknown(Boolean.TRUE);
            response.setPaid(Boolean.FALSE);
            response.setUnpaid(Boolean.FALSE);
        }
        return response;
    }

    /**
     * 按页签分页查询体检收费队列。
     *
     * @param request 分页请求
     * @param queueTab  页签编码
     * @return 分页响应
     */
    private PagePhysicalExamChargeQueueResponse pageQueueByTab(
        PagePhysicalExamChargeQueueRequest request,
        String queueTab) {
        try {
            pageWindowGate.requirePositivePageWindow(
                request,
                ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                "分页参数非法");
            OutpatientChargeQueueEncounterQuery queueQuery = new OutpatientChargeQueueEncounterQuery();
            PageQueryBinder.bind(request, queueQuery);
            queueQuery.setExcludedServiceTable(TableNameConstants.ADM_HEALTHCARE_SERVICE);
            Page<Long> encounterPage = physicalExamChargeEncounterQueueLoader.pageEncounterIdsForPhysicalExamCashierQueue(queueQuery);
            List<Long> encounterIds = encounterPage.getContent();
            Map<Long, Encounter> encounterById = new HashMap<>();
            List<Long> patientIds = new ArrayList<>();
            for (Encounter encounter : encounterQueryService.listEncounterEntitiesByIds(encounterIds)) {
                if (encounter != null && encounter.getId() != null) {
                    encounterById.put(encounter.getId(), encounter);
                    if (encounter.getPatientId() != null) {
                        patientIds.add(encounter.getPatientId());
                    }
                }
            }
            Map<Long, String> patientNames = patientQueryService.findPatientNamesByIds(patientIds);
            Map<Long, PeisEncounterExtModel> extByEncounter = peisEncounterExtQueryService.mapByEncounterIds(encounterIds);
            LinkedHashSet<Long> encounterIdSet = new LinkedHashSet<>(encounterIds);
            List<ChargeItem> chargeLines = chargeItemQueryService.listOutpatientCashierChargeItemsForEncounters(
                encounterIdSet,
                TableNameConstants.ADM_HEALTHCARE_SERVICE);
            Map<Long, List<ChargeItem>> chargeItemsByEncounter = new LinkedHashMap<>();
            for (ChargeItem line : chargeLines) {
                if (line == null || line.getEncounterId() == null) {
                    continue;
                }
                List<ChargeItem> bucket = chargeItemsByEncounter.computeIfAbsent(line.getEncounterId(), key -> new ArrayList<>());
                bucket.add(line);
            }
            List<PhysicalExamChargeQueueRowDto> rows = physicalExamChargeMapper.toQueueRows(
                encounterIds,
                encounterById,
                patientNames,
                extByEncounter,
                chargeItemsByEncounter);
            this.applyDraftPaymentQueueLabels(rows);
            List<PhysicalExamChargeQueueRowDto> filtered = new ArrayList<>();
            for (PhysicalExamChargeQueueRowDto row : rows) {
                if (row == null) {
                    continue;
                }
                if (queueTab.equals(row.getQueueLabelCode())) {
                    filtered.add(row);
                }
            }
            return physicalExamChargeMapper.toPageQueueResponse(encounterPage, filtered);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT,
                PhysicalExamChargeConstants.MESSAGE_CODE_QUEUE_QUERY_FAILED + ": 查询体检收费队列失败");
        }
    }

    /**
     * 存在草稿/退款中账单时将队列标签打为待收费。
     *
     * @param rows 队列行
     */
    private void applyDraftPaymentQueueLabels(List<PhysicalExamChargeQueueRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (PhysicalExamChargeQueueRowDto row : rows) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            if (PhysicalExamChargeQueueTab.WAITING.equals(row.getQueueLabelCode())) {
                continue;
            }
            List<PaymentReconciliationQueryModel> statements =
                paymentReconciliationQueryService.findPaymentsByEncounter(row.getEncounterId());
            if (statements == null || statements.isEmpty()) {
                continue;
            }
            if (this.hasUnsettledBillingStatement(statements)) {
                row.setQueueLabelCode(PhysicalExamChargeQueueTab.WAITING);
            }
        }
    }

    /**
     * 判断是否存在未结清账单（草稿或退款中）。
     *
     * @param statements 支付流水列表
     * @return 存在未结清时 true
     */
    private boolean hasUnsettledBillingStatement(List<PaymentReconciliationQueryModel> statements) {
        for (PaymentReconciliationQueryModel statement : statements) {
            if (statement == null) {
                continue;
            }
            if (PaymentStatus.DRAFT.equals(statement.getStatusEnum())) {
                return true;
            }
            if (PaymentStatus.REFUND_PROCESSING.equals(statement.getStatusEnum())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 列出就诊下 PLANNED/SETTLED 明细（排除挂号锚点）。
     *
     * @param encounterId 就诊主键
     * @return 收费明细
     */
    private List<ChargeItem> listBillableChargeItems(Long encounterId) {
        ChargeItemQueryCommand cmd = new ChargeItemQueryCommand();
        cmd.setEncounterId(encounterId);
        cmd.setExcludedServiceTable(TableNameConstants.ADM_HEALTHCARE_SERVICE);
        cmd.setStatusEnumIn(chargeItemQueryService.outpatientCashierBillableStatusCodes());
        cmd.setExcludeSupersededSettledLines(true);
        return chargeItemQueryService.listChargeItemsByEncounterId(cmd);
    }

    /**
     * 批量解析收费项展示名。
     *
     * @param rows 收费明细
     * @return chargeItemId → 展示名
     */
    private Map<Long, String> buildItemLabelMap(List<ChargeItem> rows) {
        Map<Long, String> labels = new HashMap<>();
        if (rows == null || rows.isEmpty()) {
            return labels;
        }
        LinkedHashSet<Long> definitionIds = new LinkedHashSet<>();
        for (ChargeItem row : rows) {
            if (row != null && row.getDefinitionId() != null) {
                definitionIds.add(row.getDefinitionId());
            }
        }
        Map<Long, ChargeItemDefinition> definitionMap = chargeItemDefinitionQueryService.findAllDefinitionsByIdIn(definitionIds);
        for (ChargeItem row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            String label = PhysicalExamChargeConstants.CHARGE_ITEM_DISPLAY_FALLBACK;
            ChargeItemDefinition definition = definitionMap.get(row.getDefinitionId());
            if (definition != null && StringUtils.isNotEmpty(definition.getChargeName())) {
                label = definition.getChargeName();
            }
            labels.put(row.getId(), label);
        }
        return labels;
    }

    /**
     * 解析套餐展示名。
     *
     * @param ext 体检扩展
     * @return 套餐名
     */
    private String resolvePackageName(PeisEncounterExtModel ext) {
        if (ext == null) {
            return null;
        }
        if (StringUtils.isNotEmpty(ext.getPackageSnapshotJson())) {
            String marker = "\"name\":\"";
            int start = ext.getPackageSnapshotJson().indexOf(marker);
            if (start >= 0) {
                start += marker.length();
                StringBuilder builder = new StringBuilder();
                for (int index = start; index < ext.getPackageSnapshotJson().length(); index++) {
                    char ch = ext.getPackageSnapshotJson().charAt(index);
                    if (ch == '"') {
                        break;
                    }
                    builder.append(ch);
                }
                if (builder.length() > 0) {
                    return builder.toString();
                }
            }
        }
        if (ext.getPackageActivityId() != null) {
            return "套餐#" + ext.getPackageActivityId();
        }
        return null;
    }

    /**
     * 汇总收费项金额（6 位计算刻度）。
     *
     * @param chargeItems 收费项
     * @return 合计
     */
    private List<BigDecimal> sumChargeItemAmount(List<ChargeItem> chargeItems) {
        List<BigDecimal> parts = new ArrayList<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem != null && chargeItem.getTotalPrice() != null) {
                parts.add(chargeItem.getTotalPrice());
            }
        }
        return parts;
    }

    /**
     * 拼接收费项 id 为持久化串。
     *
     * @param chargeItemIds 主键列表
     * @return 逗号分隔串
     */
    private String joinIds(List<Long> chargeItemIds) {
        StringBuilder builder = new StringBuilder();
        if (chargeItemIds == null) {
            return builder.toString();
        }
        boolean first = true;
        for (Long chargeItemId : chargeItemIds) {
            if (chargeItemId == null) {
                continue;
            }
            if (!first) {
                builder.append(',');
            }
            builder.append(chargeItemId);
            first = false;
        }
        return builder.toString();
    }

    /**
     * 校验收费项全部加载命中。
     *
     * @param requestedIds 请求 id
     * @param chargeItems  加载结果
     */
    private void assertChargeItemsLoaded(List<Long> requestedIds, List<ChargeItem> chargeItems) {
        if (requestedIds == null || chargeItems == null || requestedIds.size() != chargeItems.size()) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_NOT_FOUND,
                PhysicalExamChargeConstants.MESSAGE_CODE_PRESETTLE_NO_CHARGE_ITEMS + ": 收费项不存在");
        }
    }

    /**
     * 校验收费项归属就诊。
     *
     * @param chargeItems 收费项
     * @param encounterId 就诊主键
     */
    private void assertChargeItemsBelongEncounter(List<ChargeItem> chargeItems, Long encounterId) {
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || !encounterId.equals(chargeItem.getEncounterId())) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_ITEM_RULE_CONFLICT,
                    PhysicalExamChargeConstants.MESSAGE_CODE_PRESETTLE_FAILED + ": 收费项不属于当前就诊");
            }
        }
    }

    /**
     * 校验收费项未结算。
     *
     * @param chargeItems 收费项
     */
    private void assertNotSettledChargeItems(List<ChargeItem> chargeItems) {
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null) {
                continue;
            }
            if (ChargeItemStatus.SETTLED.getCode().equals(chargeItem.getStatusEnum())) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_ITEM_INVALID_STATUS,
                    PhysicalExamChargeConstants.MESSAGE_CODE_SETTLE_CHARGE_ITEM_STATUS_INVALID + ": 收费明细已结算");
            }
        }
    }

    /**
     * 防重：已成功流水不得再次包含本次收费项。
     *
     * @param encounterId   就诊主键
     * @param chargeItemIds 收费项主键
     */
    private void assertNotAlreadySettled(Long encounterId, List<Long> chargeItemIds) {
        List<PaymentReconciliationQueryModel> rows =
            paymentReconciliationQueryService.findPaySuccessOrRefundingOverlappingChargeItems(encounterId, chargeItemIds);
        if (!rows.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT,
                PhysicalExamChargeConstants.MESSAGE_CODE_SETTLE_ALREADY_CHARGED + ": 请勿重复提交");
        }
    }

    /**
     * 比较草稿绑定收费项与请求集合是否一致。
     *
     * @param persistedIds 持久化 id 串
     * @param requestIds   请求 id 列表
     * @return 一致时 true
     */
    private boolean sameChargeItemSet(String persistedIds, List<Long> requestIds) {
        LinkedHashSet<Long> left = this.parseChargeItemIds(persistedIds);
        LinkedHashSet<Long> right = new LinkedHashSet<>();
        if (requestIds != null) {
            for (Long id : requestIds) {
                if (id != null) {
                    right.add(id);
                }
            }
        }
        return left.equals(right);
    }

    /**
     * 解析 charge_item_ids 持久化串。
     *
     * @param persistedIds 逗号分隔串
     * @return id 集合
     */
    private LinkedHashSet<Long> parseChargeItemIds(String persistedIds) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (StringUtils.isEmpty(persistedIds)) {
            return ids;
        }
        String[] parts = persistedIds.split(",");
        for (String part : parts) {
            if (StringUtils.isEmpty(part)) {
                continue;
            }
            ids.add(Long.valueOf(part.trim()));
        }
        return ids;
    }

    /**
     * 汇总提交支付明细金额。
     *
     * @param request 正式结算请求
     * @return 合计（2 位）
     */
    private BigDecimal sumSubmitAmount(ConfirmPhysicalExamChargeSettlementRequest request) {
        BigDecimal total = BigDecimal.ZERO;
        for (int index = 0; index < request.getPaymentDetails().size(); index++) {
            if (request.getPaymentDetails().get(index) == null) {
                continue;
            }
            BigDecimal amount = request.getPaymentDetails().get(index).getAmount();
            if (amount != null) {
                total = total.add(amount);
            }
        }
        return MonetaryAmountSupport.scaleSettle(total);
    }

    /**
     * 校验草稿明细合计与预期一致。
     *
     * @param details  草稿明细
     * @param expected 预期金额
     */
    private void assertDraftDetailAmountMatchesExpected(List<PaymentRecDetailQueryModel> details, BigDecimal expected) {
        BigDecimal draftTotal = BigDecimal.ZERO;
        for (PaymentRecDetailQueryModel detail : details) {
            if (detail.getAmount() == null) {
                throw new BusinessException(
                    ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT,
                    PhysicalExamChargeConstants.MESSAGE_CODE_SETTLE_DRAFT_DETAIL_EMPTY + ": 支付草稿明细金额缺失");
            }
            draftTotal = draftTotal.add(detail.getAmount());
        }
        if (MonetaryAmountSupport.scaleSettle(draftTotal).compareTo(expected) != 0) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT,
                PhysicalExamChargeConstants.MESSAGE_CODE_SETTLE_AMOUNT_MISMATCH + ": 支付草稿金额与收费项不一致");
        }
    }

    /**
     * 从收费项账户解析合同号。
     *
     * @param chargeItems 收费项
     * @param encounterId 就诊主键
     * @return 合同业务号；可空
     */
    private String resolveContractNoFromChargeItems(List<ChargeItem> chargeItems, Long encounterId) {
        LinkedHashSet<Long> accountIds = new LinkedHashSet<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem != null && chargeItem.getAccountId() != null) {
                accountIds.add(chargeItem.getAccountId());
            }
        }
        Map<Long, Account> accountById = accountQueryService.mapAccountsByIds(accountIds);
        for (Account account : accountById.values()) {
            if (account != null && StringUtils.isNotEmpty(account.getContractNo())) {
                return account.getContractNo().trim();
            }
        }
        List<Account> encounterAccounts = accountQueryService.getAccountListByEncounter(encounterId);
        for (Account account : encounterAccounts) {
            if (account != null && StringUtils.isNotEmpty(account.getContractNo())) {
                return account.getContractNo().trim();
            }
        }
        return null;
    }

    /**
     * 装配预结算占位支付明细。
     *
     * @param encounterId 就诊主键
     * @param chargeLines 收费项
     * @return 占位明细
     */
    private List<PaymentDetailItemCommand> buildPreSettlePaymentDetailDraftLines(Long encounterId, List<ChargeItem> chargeLines) {
        Long defaultSelfPayAccountId = accountQueryService.getSelfPayAccount(encounterId);
        Map<Long, BigDecimal> amountByAccountId = new LinkedHashMap<>();
        for (ChargeItem chargeLine : chargeLines) {
            Long accountId = chargeLine.getAccountId();
            if (accountId == null) {
                accountId = defaultSelfPayAccountId;
            }
            if (accountId == null) {
                throw new BusinessException(
                    ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                    PhysicalExamChargeConstants.MESSAGE_CODE_PRESETTLE_ACCOUNT_MISSING + ": 无法生成支付草稿");
            }
            BigDecimal linePrice = chargeLine.getTotalPrice();
            if (linePrice == null) {
                linePrice = BigDecimal.ZERO;
            }
            BigDecimal prior = amountByAccountId.get(accountId);
            if (prior == null) {
                amountByAccountId.put(accountId, linePrice);
            } else {
                amountByAccountId.put(accountId, prior.add(linePrice));
            }
        }
        List<Account> encounterAccounts = accountQueryService.getAccountListByEncounter(encounterId);
        Map<Long, Account> accountById = new HashMap<>();
        for (Account accountRow : encounterAccounts) {
            accountById.put(accountRow.getId(), accountRow);
        }
        List<PaymentDetailItemCommand> draftLines = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : amountByAccountId.entrySet()) {
            PaymentDetailItemCommand draftLine = new PaymentDetailItemCommand();
            draftLine.setAccountId(entry.getKey());
            Account matchedAccount = accountById.get(entry.getKey());
            if (matchedAccount != null) {
                draftLine.setAccountCode(matchedAccount.getBusNo());
            }
            draftLine.setPayEnum(PaymentChannel.CASHIER.getCode());
            draftLine.setAmount(entry.getValue());
            draftLine.setChargeAmount(entry.getValue());
            draftLine.setResultEnum(PaymentStatus.DRAFT.getCode());
            draftLines.add(draftLine);
        }
        return draftLines;
    }

    /**
     * 将草稿账户写回正式结算回执明细。
     *
     * @param receiptLines 回执明细
     * @param draftDetails 草稿明细
     */
    private void attachDraftAccountsToPayReceiptDetails(
        List<PaymentDetailItemCommand> receiptLines,
        List<PaymentRecDetailQueryModel> draftDetails) {
        if (receiptLines == null || receiptLines.isEmpty() || draftDetails == null || draftDetails.isEmpty()) {
            return;
        }
        if (draftDetails.size() == 1) {
            PaymentRecDetailQueryModel draftRow = draftDetails.get(0);
            for (PaymentDetailItemCommand line : receiptLines) {
                if (line == null || line.getAccountId() != null) {
                    continue;
                }
                line.setAccountId(draftRow.getAccountId());
                line.setAccountCode(draftRow.getAccountCode());
            }
        }
    }
}
