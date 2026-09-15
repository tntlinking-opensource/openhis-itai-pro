/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.settle.service;

import com.openhis.health.application.shared.financial.PaymentChannelDisplayAssembler;
import com.openhis.health.application.shared.inpatient.InpatientAccountBalanceDisplayDtoApplier;
import com.openhis.health.application.shared.inpatient.InpatientAccountBalanceDisplayAssembler;
import com.openhis.health.application.shared.inpatient.InpatientAccountBalanceDisplayResult;
import com.openhis.health.application.shared.inpatient.InpatientInfoQueueDisplayAssembler;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.inpatient.nursestation.ATDManager.support.InpatientDischargeClinicalGate;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.application.shared.validator.PatientEncounterConsistencyGate;
import com.openhis.health.application.shared.financial.PaymentChannelOptionProvider;
import com.openhis.health.application.shared.validator.PaymentChannelGate;
import com.openhis.health.application.shared.payment.ThirdPartyPaymentSettleOrchestrator;
import com.openhis.health.application.shared.payment.ThirdPartyPaymentRefundOrchestrator;
import com.openhis.health.application.outpatient.financial.refund.support.PaymentChannelRefundOrchestrator;
import com.openhis.health.application.inpatient.financial.prepay.mapper.InpatientPrepayMapper;
import com.openhis.health.application.inpatient.financial.settle.dto.InpatientPrepayRefundPlanItemDto;
import com.openhis.health.application.inpatient.financial.settle.dto.InpatientSettleClientRefundItemDto;
import com.openhis.health.application.inpatient.financial.settle.dto.InpatientSettlePaymentDetailDto;
import com.openhis.health.application.inpatient.financial.settle.dto.InpatientSettleQueueItemDto;
import com.openhis.health.application.inpatient.financial.settle.mapper.InpatientSettleMapper;
import com.openhis.health.application.inpatient.financial.settle.mapper.InpatientSettleReadMapper;
import com.openhis.health.application.inpatient.financial.settle.request.ReverseInpatientSettleRequest;
import com.openhis.health.application.inpatient.financial.settle.request.RecallInpatientDischargeRequest;
import com.openhis.health.application.inpatient.financial.settle.request.ConfirmInpatientSettleRequest;
import com.openhis.health.application.inpatient.financial.settle.request.CompleteInpatientDischargePrepayRefundRequest;
import com.openhis.health.application.inpatient.financial.settle.request.LoadInpatientSettlePageInitRequest;
import com.openhis.health.application.inpatient.financial.settle.request.LoadInpatientSettleSummaryRequest;
import com.openhis.health.application.inpatient.financial.settle.request.LoadSettledInpatientPaymentViewRequest;
import com.openhis.health.application.inpatient.financial.settle.request.PageInpatientSettleQueueRequest;
import com.openhis.health.application.inpatient.financial.settle.request.PreSettleInpatientChargesRequest;
import com.openhis.health.application.inpatient.financial.settle.request.QueryInpatientSettleChargesRequest;
import com.openhis.health.application.inpatient.financial.settle.request.AlignInpatientPlatformVisitRequest;
import com.openhis.health.application.inpatient.financial.settle.request.UploadInpatientFeeDetailsBatchRequest;
import com.openhis.health.application.inpatient.financial.settle.response.ReverseInpatientSettleResponse;
import com.openhis.health.application.inpatient.financial.settle.response.RecallInpatientDischargeResponse;
import com.openhis.health.application.inpatient.financial.settle.response.ConfirmInpatientSettleResponse;
import com.openhis.health.application.inpatient.financial.settle.response.LoadInpatientSettlePageInitResponse;
import com.openhis.health.application.inpatient.financial.settle.response.LoadInpatientSettleSummaryResponse;
import com.openhis.health.application.inpatient.financial.settle.response.LoadSettledInpatientPaymentViewResponse;
import com.openhis.health.application.inpatient.financial.settle.response.PageInpatientSettleQueueResponse;
import com.openhis.health.application.inpatient.financial.settle.response.PreSettleInpatientChargesResponse;
import com.openhis.health.application.inpatient.financial.settle.response.QueryInpatientSettleChargesResponse;
import com.openhis.health.application.inpatient.financial.settle.response.AlignInpatientPlatformVisitResponse;
import com.openhis.health.application.inpatient.financial.settle.response.UploadInpatientFeeDetailsBatchResponse;
import com.openhis.health.application.inpatient.financial.settle.support.InpatientDepositSettlementPlanner;
import com.openhis.health.application.inpatient.financial.settle.support.dto.InpatientDepositSettlementPlan;
import com.openhis.health.application.inpatient.financial.settle.support.dto.SettledPaymentBriefDto;
import com.openhis.health.domain.administration.account.command.AdjustAdvanceDepositBalanceCommand;
import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.account.service.AccountDomainService;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.administration.chargeitem.command.BatchChargeItemIdsCommand;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.command.CompleteInpatientFinancialSettlementCommand;
import com.openhis.health.domain.administration.encounter.command.RecallInpatientDischargeCommand;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterDomainService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.application.inpatient.financial.settle.support.InpatientSettleReadLoader;
import com.openhis.health.domain.administration.invoice.command.CancelInvoiceCommand;
import com.openhis.health.domain.administration.invoice.command.CreateDraftInvoiceCommand;
import com.openhis.health.domain.administration.invoice.entity.Invoice;
import com.openhis.health.domain.administration.invoice.service.InvoiceDomainService;
import com.openhis.health.domain.administration.invoice.service.InvoiceQueryService;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.financial.contract.queries.model.ContractDetailQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.command.AbortPreSettlementBeforePaymentCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.CancelInpatientSettleAsRefundCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.CompleteInpatientSettleCancelRefundCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.CreatePaymentDraftFromPreSettleCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.PaymentDetailItemCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.PaymentYbSettlementItemCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.RefundAdvanceDepositCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.SettleInpatientPaymentCommand;
import com.openhis.health.domain.financial.paymentreconciliation.PaymentYbSettlementStatus;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentRecDetailQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationDomainService;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.support.PaymentYbSettlementSupport;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InpatientSettleQueueTab;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.enums.InvoiceStatus;
import com.openhis.health.domain.share.enums.PaymentChannel;
import com.openhis.health.domain.share.enums.PaymentKind;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.health.domain.share.enums.PaymentType;
import com.openhis.health.domain.share.constants.InpatientSettleConstants;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.workflow.servicerequest.command.RejectServiceRequestToDraftCommand;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestDomainService;
import com.openhis.health.application.inpatient.financial.settle.support.InpatientReverseSettlePreOrchestrator;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.health.infrastructure.integration.financial.orchestration.InvoiceAfterPaymentOrchestration;
import com.openhis.health.infrastructure.integration.financial.orchestration.PaymentReconciliationSettleOrchestration;
import com.openhis.health.infrastructure.integration.einvoice.orchestration.BossElectronicInvoiceAfterCommitOrchestration;
import com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionIn.InpatientChargeConfirmExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionIn.InpatientChargeEncounterExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionIn.InpatientChargePreSettleExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionIn.InpatientChargeRevokeFeeDetailExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionIn.InpatientChargeRevokeSettleExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionIn.InpatientChargeUploadFeeBatchExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionIn.InpatientPlatformVisitAlignExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionOut.InpatientChargeConfirmExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionOut.InpatientChargePreSettleExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionOut.InpatientChargeUploadFeeBatchExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionOut.InpatientPlatformVisitAlignExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.inpatient.InpatientChargeInsuranceExtension;
import com.openhis.health.infrastructure.integration.insurance.inventory.InventoryThirdPartyUploadExtension;
import com.openhis.health.infrastructure.integration.insurance.settlement.ExtensionOut.InsuranceSettlementFundLookupExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.settlement.InsuranceSettlementRecordLookupExtension;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterClearBedSummarySalesReverseExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterDischargeSummarySalesExtensionIn;
import com.openhis.health.infrastructure.integration.noop.IntegrationAfterCommitSupport;
import com.openhis.health.infrastructure.integration.payment.dto.ClientRefundResultDto;
import com.openhis.health.infrastructure.integration.payment.dto.ClientScanResultDto;
import com.openhis.health.application.shared.payment.PaymentDraftHalfSuccessAbortGate;
import com.openhis.health.infrastructure.integration.payment.ThirdPartyPaymentExtension;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 住院结算、反结与出院召回应用服务（AS-IP-FIN-003 IF-IP-STL-01～07；AS-IP-FIN-004 IF-IP-RVS-01 / IF-IP-RCL-01）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>Gate/Pre：结算队列、就诊 IMP 门禁、待结算费用与草稿/金额一致性校验。</li>
 *   <li>Cmd：预结算、正式结算、住院反结、已离院召回（内嵌反结）、发票草稿与出院财务全结（顺序与 AS-SDD §3 一致）。</li>
 *   <li>Asm：经 {@link InpatientSettleMapper} 装配队列/摘要/费用/结算/反结/召回响应。</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} →
 * {@code POST /api/app/inpatientSettle/{methodName}}；不另写 RestController。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InpatientSettleAppService {

    /** 写路径操作人门控（B.6.1）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /** 写路径校对人门控（撤销出院嘱，BR-IP-RVS-007）。 */

    private final PageWindowGate pageWindowGate;

    /** 支付渠道入站门控。 */
    private final PaymentChannelGate paymentChannelGate;

    private final ThirdPartyPaymentSettleOrchestrator thirdPartyPaymentSettleOrchestrator;

    /** 原路退扫码段前置（医保出院退预交金）。 */
    private final ThirdPartyPaymentRefundOrchestrator thirdPartyPaymentRefundOrchestrator;

    /** 按原支付渠道判定是否可能须银联 BPC 退货。 */
    private final PaymentChannelRefundOrchestrator paymentChannelRefundOrchestrator;

    /** 第三方收单扩展（判断原预交金是否仍有 BPC SUCCESS 被扫单）。 */
    private final ThirdPartyPaymentExtension thirdPartyPaymentExtension;

    /** 支付草稿作废半成功门禁（重预结算清旧草稿时禁止裸作废银联已扣单）。 */
    private final PaymentDraftHalfSuccessAbortGate paymentDraftHalfSuccessAbortGate;

    private final PaymentChannelOptionProvider paymentChannelOptionProvider;

    /** 患者与就诊一致性门控。 */
    private final PatientEncounterConsistencyGate patientEncounterConsistencyGate;

    /** 出院临床门禁（出院全结 Pre 与 ATD-05 同源，BR-IP-FLW-008 v1.2）。 */
    private final InpatientDischargeClinicalGate inpatientDischargeClinicalGate;

    /** 就诊读侧（队列、门禁与上下文）。 */
    private final EncounterQueryService encounterQueryService;

    /** 住院就诊上下文门面（结算队列分页读模型）。 */
    private final EncounterContextReader EncounterContextReader;

    /** 就诊写侧（出院财务全结）。 */
    private final EncounterDomainService encounterDomainService;

    /** 就诊账户读侧。 */
    private final AccountQueryService accountQueryService;

    /** 就诊账户写侧（出院冲抵占用 / 原路退预交金余额调整）。 */
    private final AccountDomainService accountDomainService;

    /** 出院全结预交金轧差计划器（R1～R7；自费=医保）。 */
    private final InpatientDepositSettlementPlanner inpatientDepositSettlementPlanner;

    /** 收费明细读侧。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 收费明细写侧（预结算/正式结算）。 */
    private final ChargeItemDomainService chargeItemDomainService;

    /** 支付流水读侧。 */
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;

    /** 支付流水写侧。 */
    private final PaymentReconciliationDomainService paymentReconciliationDomainService;

    /** 支付落账 + 第三方医保事后挂接。 */
    private final PaymentReconciliationSettleOrchestration paymentReconciliationSettleOrchestration;

    /** 发票写侧。 */
    private final InvoiceDomainService invoiceDomainService;

    /** 发票读侧。 */
    private final InvoiceQueryService invoiceQueryService;

    /** 合同名称读侧。 */
    private final ContractQueryService contractQueryService;

    /** 产品定义批量加载（费用明细展示名）。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /** 结算读路径（IF-IP-STL-03/04/07）。 */
    private final InpatientSettleReadLoader inpatientSettleReadLoader;

    /** 结算读路径 Request/Response 桥接。 */
    private final InpatientSettleReadMapper inpatientSettleReadMapper;

    /** 操作员展示名。 */
    private final PractitionerQueryService practitionerQueryService;

    /** 住院结算 DTO/Command 映射。 */
    private final InpatientSettleMapper inpatientSettleMapper;

    /** 住院第三方结算扩展（未装 Module 时为 NoOp）。 */
    private final InpatientChargeInsuranceExtension inpatientChargeInsuranceExtension;

    /** 医保结算回执只读（2304 基金分项 / 全自费等，供已结算查阅回填）。 */
    private final InsuranceSettlementRecordLookupExtension insuranceSettlementRecordLookupExtension;

    private final BossElectronicInvoiceAfterCommitOrchestration bossElectronicInvoiceAfterCommitOrchestration;
    private final InvoiceAfterPaymentOrchestration invoiceAfterPaymentOrchestration;

    /** 预交金 Command 复用（出院退预交金结算收口）。 */
    private final InpatientPrepayMapper inpatientPrepayMapper;

    /** 住院队列展示回填。 */
    private final InpatientInfoQueueDisplayAssembler inpatientInfoQueueDisplayAssembler;

    /** 支付渠道展示名。 */
    private final PaymentChannelDisplayAssembler paymentChannelDisplayAssembler;

    /** 出院类诊疗写侧（反结撤销出院嘱 Cmd）。 */
    private final ServiceRequestDomainService serviceRequestDomainService;

    /** 反结 Pre 读侧与校验（module.querysupport，与护士站共用口径）。 */
    private final InpatientReverseSettlePreOrchestrator inpatientReverseSettlePreOrchestrator;

    /** 医保进销存上报（清床汇总冲销 3506；未启用时 no-op）。 */
    private final InventoryThirdPartyUploadExtension inventoryUploadExtension;

    /**
     * IF-IP-STL-01：结算页初始化。
     *
     * @param request 占位请求
     * @return 页签、呈现模式与支付渠道下拉
     * @throws BusinessException 初始化失败时抛出 EX-IP-STL-101
     */
    public LoadInpatientSettlePageInitResponse loadInpatientSettlePageInit(LoadInpatientSettlePageInitRequest request) {
        try {
            // Pre：装配队列页签、费用呈现模式与用户可选支付渠道下拉。
            List<OptionDto> queueTabs = new ArrayList<>();
            queueTabs.add(new OptionDto(1L, InpatientSettleQueueTab.PENDING_SETTLE, "有待结算"));
            queueTabs.add(new OptionDto(2L, InpatientSettleQueueTab.SETTLED, "已结算"));
            List<OptionDto> presentationModes = new ArrayList<>();
            presentationModes.add(new OptionDto(
                1L, InpatientSettleConstants.PRESENTATION_MODE_DETAIL, "明细清单"));
            presentationModes.add(new OptionDto(
                2L, InpatientSettleConstants.PRESENTATION_MODE_SUMMARY, "大类汇总"));
            // Pre：用户可选支付渠道与预交金/门诊收费口径对齐。
            List<OptionDto> paymentChannels = paymentChannelOptionProvider.loadUserSelectableOptions();
            // Asm：Mapper 装配页签、呈现模式与渠道下拉。
            return inpatientSettleMapper.toLoadInpatientSettlePageInitResponse(
                queueTabs, presentationModes, paymentChannels);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_101,
                "初始化失败，请稍后重试");
        }
    }

    /**
     * IF-IP-STL-02：结算患者队列分页查询。
     *
     * @param request 关键字、入院日、页签与分页
     * @return 结算队列卡片分页
     * @throws BusinessException 分页非法或查询失败时抛出 EX-IP-STL-101
     */
    public PageInpatientSettleQueueResponse pageInpatientSettleQueue(PageInpatientSettleQueueRequest request) {
        try {
            // Gate：分页窗口须为正，否则无法安全翻页查询结算队列。
            pageWindowGate.requirePositivePageWindow(
                request,
                InpatientSettleConstants.EX_IP_STL_101,
                "分页参数不合法，请检查页码与每页条数");
            // Pre：装配结算队列分页入参（settleQueue=true）。
            InpatientEncounterPageQuery query = inpatientSettleMapper.toInpatientEncounterPageQuery(request);
            PageQueryBinder.bind(request, query);
            Page<InpatientEncounterContextQueryModel> page =
                EncounterContextReader.pageInpatientSettleQueue(query);
            Set<Long> encounterIds = new LinkedHashSet<>();
            // 遍历分页结果，收集就诊 ID 供后续批量 Pre 加载。
            for (InpatientEncounterContextQueryModel row : page.getContent()) {
                if (row != null && row.getEncounterId() != null) {
                    encounterIds.add(row.getEncounterId());
                }
            }
            // Pre：上下文 patient_id 为空时从就诊主档回填。
            Map<Long, Long> patientIdByEncounterId =
                encounterQueryService.mapPatientIdByEncounterIds(encounterIds);
            for (InpatientEncounterContextQueryModel row : page.getContent()) {
                if (row == null || row.getEncounterId() == null || row.getPatientId() != null) {
                    continue;
                }
                Long patientId = patientIdByEncounterId.get(row.getEncounterId());
                if (patientId != null) {
                    row.setPatientId(patientId);
                }
            }
            // Pre：个人现金户仅供余额；费用性质合同号优先医保账户（与预交金/护士站同口径）。
            Map<Long, Account> accountByEncounterId =
                accountQueryService.mapRegistrationAccountsByEncounterIds(encounterIds);
            Map<Long, String> contractNoByEncounterId =
                accountQueryService.mapPreferredContractNoByEncounterIds(encounterIds);
            Map<Long, BigDecimal> pendingByEncounterId =
                chargeItemQueryService.mapPendingSettlementTotalPriceByEncounterIds(encounterIds);
            Map<Long, BigDecimal> settledByEncounterId =
                chargeItemQueryService.mapSettledTotalPriceByEncounterIds(encounterIds);
            Set<String> contractNos = new HashSet<>();
            for (String contractNo : contractNoByEncounterId.values()) {
                if (StringUtils.isNotBlank(contractNo)) {
                    contractNos.add(contractNo.trim());
                }
            }
            Map<String, String> contractNameByBusNo =
                contractQueryService.mapContractNameByBusNoIn(contractNos);
            Map<String, Long> contractIdByBusNo =
                contractQueryService.mapContractIdByBusNoIn(contractNos);
            Map<Long, InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay> displayOverlay =
                inpatientInfoQueueDisplayAssembler.buildOverlay(page.getContent(), contractNoByEncounterId);
            // Pre：将优先合同展示名回填至队列覆盖层（勿用个人现金户 contract_no 覆盖）。
            for (Map.Entry<Long, InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay> entry
                : displayOverlay.entrySet()) {
                InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay overlay = entry.getValue();
                if (overlay == null) {
                    continue;
                }
                String preferredContractNo = contractNoByEncounterId.get(entry.getKey());
                if (StringUtils.isNotBlank(preferredContractNo)) {
                    String contractDisplay = contractNameByBusNo.get(preferredContractNo.trim());
                    if (StringUtils.isNotBlank(contractDisplay)) {
                        overlay.setContractDisplay(contractDisplay);
                    }
                }
            }
            String tabCode = query.getSettleQueueTab();
            Map<Long, LocalDateTime> lastSettleTimeByEncounterId = new HashMap<>();
            if (InpatientSettleQueueTab.SETTLED.equals(tabCode)) {
                // Pre：已结算页签需展示最近成功住院临床结算时间。
                for (Long encounterId : encounterIds) {
                    List<PaymentReconciliationQueryModel> payments =
                        paymentReconciliationQueryService.findPaymentsByEncounter(encounterId);
                    LocalDateTime maxBillDate = null;
                    // 遍历就诊支付流水，取 INPATIENT_CLINIC + SUCCESS 的最大 bill_date。
                    for (PaymentReconciliationQueryModel payment : payments) {
                        if (payment == null) {
                            continue;
                        }
                        if (payment.getKindEnum() != PaymentKind.INPATIENT_CLINIC) {
                            continue;
                        }
                        if (payment.getStatusEnum() != PaymentStatus.SUCCESS) {
                            continue;
                        }
                        LocalDateTime billDate = payment.getBillDate();
                        if (billDate == null) {
                            continue;
                        }
                        if (maxBillDate == null || billDate.isAfter(maxBillDate)) {
                            maxBillDate = billDate;
                        }
                    }
                    if (maxBillDate != null) {
                        lastSettleTimeByEncounterId.put(encounterId, maxBillDate);
                    }
                }
            }
            Set<Long> reversedPendingResettleEncounterIds =
                paymentReconciliationQueryService.filterReversedPendingResettleEncounterIds(encounterIds);
            // Asm：逐行装配队列卡片（账户余额展示态 + 文案）。
            List<InpatientSettleQueueItemDto> content = new ArrayList<>();
            for (InpatientEncounterContextQueryModel row : page.getContent()) {
                Account account = null;
                InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay rowOverlay = null;
                BigDecimal pendingSettlement = null;
                BigDecimal settledTotal = null;
                LocalDateTime lastSettleTime = null;
                if (row != null && row.getEncounterId() != null) {
                    account = accountByEncounterId.get(row.getEncounterId());
                    rowOverlay = displayOverlay.get(row.getEncounterId());
                    pendingSettlement = pendingByEncounterId.get(row.getEncounterId());
                    settledTotal = settledByEncounterId.get(row.getEncounterId());
                    lastSettleTime = lastSettleTimeByEncounterId.get(row.getEncounterId());
                }
                boolean reversedPendingResettle = false;
                if (row != null
                    && row.getEncounterId() != null
                    && reversedPendingResettleEncounterIds.contains(row.getEncounterId())) {
                    reversedPendingResettle = true;
                }
                BigDecimal pendingTotal = BigDecimal.ZERO;
                if (pendingSettlement != null) {
                    pendingTotal = pendingSettlement;
                }
                BigDecimal settledSum = BigDecimal.ZERO;
                if (settledTotal != null) {
                    settledSum = settledTotal;
                }
                InpatientAccountBalanceDisplayResult accountBalanceDisplay =
                    InpatientAccountBalanceDisplayAssembler.assemble(
                        account,
                        pendingTotal,
                        settledSum,
                        reversedPendingResettle);
                InpatientSettleQueueItemDto item = inpatientSettleMapper.toInpatientSettleQueueItemDto(
                    row, account, rowOverlay, pendingSettlement, lastSettleTime);
                InpatientAccountBalanceDisplayDtoApplier.apply(item, accountBalanceDisplay);
                if (item != null) {
                    // Asm：回填优先费用性质合同主键（医保户优先），供前端展示/跳转。
                    String preferredContractNo = contractNoByEncounterId.get(row.getEncounterId());
                    if (StringUtils.isNotBlank(preferredContractNo)) {
                        Long contractId = contractIdByBusNo.get(preferredContractNo.trim());
                        if (contractId != null) {
                            item.setContractId(contractId);
                        }
                    }
                }
                content.add(item);
            }
            Page<InpatientEncounterContextQueryModel> responsePage = page;
            if (InpatientSettleQueueTab.SETTLED.equals(tabCode)) {
                // Asm：已结算页签按住院号去重；多次结算在 IF-IP-STL-07 历史区查阅。
                content = this.dedupeSettledQueueItemsByEncounterBusNo(content);
                long distinctTotal = EncounterContextReader.countInpatientSettleQueueDistinctBusNo(query);
                responsePage = new PageImpl<>(page.getContent(), page.getPageable(), distinctTotal);
            }
            return inpatientSettleMapper.toPageInpatientSettleQueueResponse(content, responsePage);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_101,
                "队列查询失败，请缩小条件或稍后重试");
        }
    }

    /**
     * IF-IP-STL-03：结算摘要（待结算患者）。
     *
     * @param request 就诊、患者与可选勾选明细
     * @return 待结算合计、选中合计与展示余额
     * @throws BusinessException 就诊/账户/状态非法时抛出 EX-IP-STL-102/103
     */
    public LoadInpatientSettleSummaryResponse loadInpatientSettleSummary(LoadInpatientSettleSummaryRequest request) {
        // Pre：读路径加载待结算摘要（合计、选中合计、展示余额）。
        // Asm：Mapper 转 Response。
        return inpatientSettleReadMapper.toSummaryResponse(
            inpatientSettleReadLoader.loadInpatientSettleSummary(
                inpatientSettleReadMapper.toSummaryQuery(request)));
    }

    /**
     * IF-IP-STL-04：结算专用费用查询。
     *
     * @param request 就诊、呈现模式与业务日区间
     * @return 待结算明细或大类汇总
     * @throws BusinessException 就诊无效或展示数据不完整时抛出 EX-IP-STL-102/104
     */
    public QueryInpatientSettleChargesResponse queryInpatientSettleCharges(QueryInpatientSettleChargesRequest request) {
        // Pre：读路径按呈现模式查询待结算费用明细或大类汇总。
        // Asm：Mapper 转 Response。
        return inpatientSettleReadMapper.toChargesResponse(
            inpatientSettleReadLoader.queryInpatientSettleCharges(
                inpatientSettleReadMapper.toChargesQuery(request)));
    }

    /**
     * IF-IP-STL-05：住院预结算。
     *
     * @param request 就诊、患者与所选待结算明细
     * @return 支付草稿 ID 与应收合计
     * @throws BusinessException 参数/状态/草稿冲突时抛出 EX-IP-STL-001/102/105
     */
    public PreSettleInpatientChargesResponse preSettleInpatientCharges(PreSettleInpatientChargesRequest request) {
        // Gate：就诊、患者与 chargeItemIds 列表（可为空数组）均须齐备。
        if (request == null
            || request.getEncounterId() == null
            || request.getPatientId() == null
            || request.getChargeItemIds() == null) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_001,
                "请选择待结算费用");
        }
        // Gate：写路径须已登录操作员。
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            InpatientSettleConstants.EX_IP_STL_001,
            "请选择待结算费用");
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            request.getEncounterId(),
            EncounterClass.IMP.getCode(),
            InpatientSettleConstants.EX_IP_STL_102,
            "重新选择患者");
        // Pre：终态/取消/完诊就诊不允许再发起住院临床结算预结算。
        if (EncounterStatus.COMPLETED.getCode().equals(encounter.getStatusEnum())
            || EncounterStatus.CANCELLED.getCode().equals(encounter.getStatusEnum())
            || EncounterStatus.DISCHARGED.getCode().equals(encounter.getStatusEnum())) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_102,
                "当前就诊状态不允许结算");
        }
        patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
            request.getPatientId(),
            encounter.getPatientId(),
            InpatientSettleConstants.EX_IP_STL_001,
            "请选择待结算费用");
        Set<Long> allPendingIds = this.loadAllPendingChargeItemIds(request.getEncounterId());
        List<Long> requestedChargeItemIds = request.getChargeItemIds();
        boolean zeroAmountDischargeFull = requestedChargeItemIds.isEmpty();
        if (zeroAmountDischargeFull) {
            // Pre：无费用时仅允许 PENDING 态零元出院全结。
            if (!EncounterStatus.PENDING.getCode().equals(encounter.getStatusEnum())
                || !allPendingIds.isEmpty()) {
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_STL_001,
                    "请选择待结算费用");
            }
        }
        List<ChargeItem> chargeItems = Collections.emptyList();
        BigDecimal receivableTotal = BigDecimal.ZERO;
        if (!zeroAmountDischargeFull) {
            // Pre：加载所选行并校验归属就诊且均为待结算态（BR-IP-STL-002）。
            ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
            loadCmd.setIds(requestedChargeItemIds);
            chargeItems = chargeItemQueryService.loadChargeItemsByIds(loadCmd);
            this.assertChargeItemsForInpatientSettle(
                chargeItems, requestedChargeItemIds, request.getEncounterId());
            receivableTotal = this.sumChargeItemAmount(chargeItems);
            if (receivableTotal.compareTo(BigDecimal.ZERO) <= 0) {
                // Pre：有明细时应收须为正；零元全结走空列表分支。
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_STL_001,
                    "请选择待结算费用");
            }
        }
        Set<Long> selectedIds = new HashSet<>(requestedChargeItemIds);
        String settleMode = this.resolveSettleMode(
            encounter.getStatusEnum(),
            selectedIds,
            allPendingIds,
            request.getSettleMode());
        // Pre：出院全结须先通过 ATD-03/04/05 同源临床门禁（长期/临时/待发退药清零）。
        this.assertDischargeClinicalGateForDischargeFullSettle(request.getEncounterId(), settleMode);
        // Pre：找出同就诊下未完成的 INPATIENT_CLINIC 草稿，后续逐条作废（BR-IP-STL-010）。
        List<PaymentReconciliationQueryModel> encounterPayments =
            paymentReconciliationQueryService.findPaymentsByEncounter(request.getEncounterId());
        List<PaymentReconciliationQueryModel> draftPayments = new ArrayList<>();
        // 遍历同就诊流水，筛出未完成的 INPATIENT_CLINIC 草稿待作废。
        for (PaymentReconciliationQueryModel payment : encounterPayments) {
            if (payment == null) {
                continue;
            }
            if (payment.getKindEnum() != PaymentKind.INPATIENT_CLINIC) {
                continue;
            }
            if (payment.getStatusEnum() != PaymentStatus.DRAFT) {
                continue;
            }
            draftPayments.add(payment);
        }
        try {
            // Cmd：作废旧 INPATIENT_CLINIC 草稿，避免并发预结算残留（BR-IP-STL-010）。
            // Gate：半成功（银联已扣）禁止裸作废，与门诊 BR-OPC-025 同口径。
            for (PaymentReconciliationQueryModel draft : draftPayments) {
                paymentDraftHalfSuccessAbortGate.assertAbortableWithoutUnreimbursedScan(draft.getId());
                AbortPreSettlementBeforePaymentCommand abortCmd = new AbortPreSettlementBeforePaymentCommand();
                abortCmd.setPaymentId(draft.getId());
                paymentReconciliationDomainService.abortPreSettlementBeforePayment(abortCmd);
            }
            BatchChargeItemIdsCommand preSettleCmd =
                inpatientSettleMapper.toBatchChargeItemIdsCommand(requestedChargeItemIds, operatorId);
            if (!zeroAmountDischargeFull) {
                // Cmd：收费项预结算校验与事件（状态仍 PENDING_SETTLEMENT）。
                chargeItemDomainService.preSettleInpatientChargeItems(preSettleCmd);
            }
            Account registrationAccount = accountQueryService.getRegistrationAccount(
                request.getPatientId(),
                request.getEncounterId());
            ContractDetailQueryModel contractFromRequest = null;
            if (request.getContractId() != null) {
                contractFromRequest = contractQueryService.findContractById(request.getContractId());
                // 显式传入合同主键但未命中主数据时，直接阻断预结算，避免生成无法追溯合同号的支付草稿。
                if (contractFromRequest == null) {
                    throw new BusinessException(
                        InpatientSettleConstants.EX_IP_STL_001,
                        "费用性质合同不存在");
                }
            }
            // BR-IP-STL-016：从收费项记账账户聚合 contract_no（非预交金登记户）。
            String contractNo = this.resolvePaymentContractBusNoForPreSettle(
                contractFromRequest,
                chargeItems,
                request.getEncounterId());
            // 占位明细按收费项记账账户汇总（自费/医保分桶），禁止一律挂登记个人现金户。
            List<PaymentDetailItemCommand> draftDetailCommands =
                this.buildInpatientPreSettlePaymentDetailDraftLines(request.getEncounterId(), chargeItems);
            CreatePaymentDraftFromPreSettleCommand paymentDraftCommand =
                inpatientSettleMapper.toCreatePaymentDraftFromPreSettleCommand(
                    request,
                    receivableTotal,
                    Collections.emptyList(),
                    operatorId,
                    this.joinIds(requestedChargeItemIds),
                    contractNo);
            paymentDraftCommand.setPaymentDetails(draftDetailCommands);
            // Cmd：落 INPATIENT_CLINIC 支付草稿（DRAFT）及占位明细，供 IF-IP-STL-06 正式结算消费。
            Long paymentDraftId =
                paymentReconciliationDomainService.createPaymentDraftFromPreSettle(paymentDraftCommand);
            PreSettleInpatientChargesResponse response =
                inpatientSettleMapper.toPreSettleInpatientChargesResponse(paymentDraftId, receivableTotal);
            response.setSettleMode(settleMode);
            // Cmd：医保扩展开启时走第三方预结算；自费/医保均按 R1～R7 回填 P/O/C。
            this.applyInsurancePreSettleIfNeeded(
                request,
                paymentDraftId,
                response,
                chargeItems,
                settleMode,
                registrationAccount);
            if (!Boolean.TRUE.equals(response.getInsurancePreSettled())) {
                this.applySelfPayChannelPayableOnPreSettle(
                    request.getEncounterId(),
                    receivableTotal,
                    registrationAccount,
                    response);
            }
            return response;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_105,
                "重新预结算或作废草稿后再试");
        }
    }

    /**
     * 住院费用明细单批上传（2301，最多 100 行；供前端进度条分批）。
     *
     * @param request 就诊、收费项与读卡字段；可选 mdtrtId 复用上批就诊号
     * @return 本批进度
     */
    public UploadInpatientFeeDetailsBatchResponse uploadInpatientFeeDetailsBatch(
            UploadInpatientFeeDetailsBatchRequest request) {
        if (request == null
            || request.getEncounterId() == null
            || request.getPatientId() == null
            || request.getChargeItemIds() == null
            || request.getChargeItemIds().isEmpty()) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_001,
                "请选择待结算费用");
        }
        if (!inpatientChargeInsuranceExtension.isEnabled()) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_001,
                "当前未启用医保结算扩展");
        }
        if (StringUtils.isBlank(request.getPsnNo())) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_001,
                "医保费用上传须先读卡获取人员编号");
        }
        if (StringUtils.isBlank(request.getSettleMode())) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_001,
                "结算模式不能为空");
        }
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            request.getEncounterId(),
            EncounterClass.IMP.getCode(),
            InpatientSettleConstants.EX_IP_STL_102,
            "重新选择患者");
        patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
            request.getPatientId(),
            encounter.getPatientId(),
            InpatientSettleConstants.EX_IP_STL_001,
            "请选择待结算费用");
        ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
        loadCmd.setIds(request.getChargeItemIds());
        List<ChargeItem> chargeItems = chargeItemQueryService.loadChargeItemsByIds(loadCmd);
        this.assertChargeItemsForInpatientSettle(
            chargeItems, request.getChargeItemIds(), request.getEncounterId());
        PreSettleInpatientChargesRequest contractProbe = new PreSettleInpatientChargesRequest()
            .setEncounterId(request.getEncounterId())
            .setPatientId(request.getPatientId())
            .setChargeItemIds(request.getChargeItemIds())
            .setContractId(request.getContractId());
        Long insuranceContractId =
            this.resolveInsuranceContractId(contractProbe, chargeItems, request.getEncounterId());
        if (insuranceContractId == null
            || !inpatientChargeInsuranceExtension.supportsThirdPartyContract(insuranceContractId)) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_001,
                "当前费用性质不支持医保费用上传");
        }
        InpatientChargeUploadFeeBatchExtensionIn command = new InpatientChargeUploadFeeBatchExtensionIn();
        command.setEncounterId(request.getEncounterId());
        command.setChargeItemIds(request.getChargeItemIds());
        command.setContractId(insuranceContractId);
        command.setPayerCertType(request.getYbMediumType());
        command.setPayerCertNo(request.getYbCardInfo());
        command.setInsuredPersonNo(request.getPsnNo());
        command.setInsuplcAdmdvs(request.getInsuplcAdmdvs());
        command.setInsutype(request.getInsutype());
        command.setSettleMode(request.getSettleMode());
        command.setAcctUsedFlag(request.getAcctUsedFlag());
        command.setPsnSetlway(request.getPsnSetlway());
        command.setPreferredMdtrtId(request.getMdtrtId());
        InpatientChargeUploadFeeBatchExtensionOut ybResult =
            inpatientChargeInsuranceExtension.uploadPendingFeeBatch(command);
        if (ybResult == null) {
            return new UploadInpatientFeeDetailsBatchResponse()
                .setBatchUploaded(0)
                .setRemainingAfter(0)
                .setDone(true);
        }
        return new UploadInpatientFeeDetailsBatchResponse()
            .setBatchUploaded(ybResult.getBatchUploaded())
            .setRemainingAfter(ybResult.getRemainingAfter())
            .setDone(ybResult.isDone())
            .setMdtrtId(ybResult.getMdtrtId());
    }

    /**
     * 诊断本地平台就诊映射与 5201 在院态是否一致。
     */
    public AlignInpatientPlatformVisitResponse diagnoseInpatientPlatformVisit(
            AlignInpatientPlatformVisitRequest request) {
        return this.toAlignResponse(this.invokePlatformVisitAlign(request, false));
    }

    /**
     * 按诊断结果轻量修复平台在院不一致（清多余在院 / 软删死映射）。
     */
    public AlignInpatientPlatformVisitResponse repairInpatientPlatformVisit(
            AlignInpatientPlatformVisitRequest request) {
        return this.toAlignResponse(this.invokePlatformVisitAlign(request, true));
    }

    private InpatientPlatformVisitAlignExtensionOut invokePlatformVisitAlign(
            AlignInpatientPlatformVisitRequest request,
            boolean repair) {
        if (request == null
            || request.getEncounterId() == null
            || request.getPatientId() == null
            || request.getContractId() == null
            || StringUtils.isBlank(request.getPsnNo())) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_001,
                "请选择患者并完成读卡后再对齐平台在院态");
        }
        if (!inpatientChargeInsuranceExtension.isEnabled()) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_001,
                "当前未启用医保结算扩展");
        }
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            request.getEncounterId(),
            EncounterClass.IMP.getCode(),
            InpatientSettleConstants.EX_IP_STL_102,
            "重新选择患者");
        patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
            request.getPatientId(),
            encounter.getPatientId(),
            InpatientSettleConstants.EX_IP_STL_001,
            "患者标识与就诊不一致");
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            InpatientSettleConstants.EX_IP_STL_001,
            "请先登录");
        InpatientPlatformVisitAlignExtensionIn command = new InpatientPlatformVisitAlignExtensionIn();
        command.setEncounterId(request.getEncounterId());
        command.setContractId(request.getContractId());
        command.setInsuredPersonNo(request.getPsnNo().trim());
        command.setInsuplcAdmdvs(request.getInsuplcAdmdvs());
        command.setOperatorId(operatorId);
        if (repair) {
            return inpatientChargeInsuranceExtension.repairPlatformVisit(command);
        }
        return inpatientChargeInsuranceExtension.diagnosePlatformVisit(command);
    }

    private AlignInpatientPlatformVisitResponse toAlignResponse(InpatientPlatformVisitAlignExtensionOut out) {
        AlignInpatientPlatformVisitResponse response = new AlignInpatientPlatformVisitResponse();
        if (out == null) {
            return response.setMismatchCode("OK").setAdvice("无诊断结果");
        }
        return response
            .setLocalMdtrtId(out.getLocalMdtrtId())
            .setPlatformInHospitalMdtrtIds(out.getPlatformInHospitalMdtrtIds())
            .setMismatchCode(out.getMismatchCode())
            .setAdvice(out.getAdvice())
            .setRepaired(out.isRepaired())
            .setRepairSummary(out.getRepairSummary());
    }

    /**
     * IF-IP-STL-06：确认住院结算。
     *
     * @param request 草稿、收费明细与渠道实付明细
     * @return 支付 ID、发票草稿与是否出院全结
     * @throws BusinessException 草稿/金额/全结门禁失败时抛出 EX-IP-STL-106～109
     */
    public ConfirmInpatientSettleResponse confirmInpatientSettle(ConfirmInpatientSettleRequest request) {
        // Gate：草稿、收费项集合与渠道明细均须齐备，否则无法进入正式结算写路径。
        if (request == null
            || request.getEncounterId() == null
            || request.getPatientId() == null
            || request.getPaymentDraftId() == null
            || request.getChargeItemIds() == null
            || request.getPaymentDetails() == null) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_106,
                "重新预结算并核对");
        }
        // Gate：逐条校验用户可选支付渠道 code，防止非法渠道进入结算命令。
        for (InpatientSettlePaymentDetailDto detail : request.getPaymentDetails()) {
            if (detail == null) {
                continue;
            }
            detail.setPayChannelCode(paymentChannelGate.requireUserSelectablePayChannelCode(
                detail.getPayChannelCode(),
                InpatientSettleConstants.EX_IP_STL_106,
                "支付方式不合法"));
        }
        // Gate：写路径须已登录操作员。
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            InpatientSettleConstants.EX_IP_STL_106,
            "重新预结算并核对");
        // Pre：草稿须为 INPATIENT_CLINIC + DRAFT，且就诊锚点与本次提交一致。
        PaymentReconciliationQueryModel payment =
            paymentReconciliationQueryService.mustGetPayment(request.getPaymentDraftId());
        if (payment.getKindEnum() != PaymentKind.INPATIENT_CLINIC
            || payment.getStatusEnum() != PaymentStatus.DRAFT) {
            // Pre：草稿须为住院临床 DRAFT，否则已失效或被其他流程消费。
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_106,
                "支付草稿已失效，请重新预结算");
        }
        if (!request.getEncounterId().equals(payment.getEncounterId())) {
            // Pre：草稿就诊锚点须与本次提交一致，防止跨就诊结算。
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_106,
                "支付草稿与就诊不一致");
        }
        // Pre：草稿从表须非空，否则无法与实收明细交叉校验（BR-IP-STL-005/006）。
        if (paymentReconciliationQueryService.listPaymentRecDetailsByPaymentId(request.getPaymentDraftId()).isEmpty()) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_106,
                "支付草稿明细为空，请重新预结算");
        }
        // Pre：请求 chargeItemIds 须与草稿头绑定集合完全一致（BR-IP-STL-005）。
        if (!this.sameChargeItemSet(payment.getChargeItemIds(), request.getChargeItemIds())) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_106,
                "收费项与草稿不一致");
        }
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            request.getEncounterId(),
            EncounterClass.IMP.getCode(),
            InpatientSettleConstants.EX_IP_STL_106,
            "重新预结算并核对");
        // Pre：拟结项须仍属本就诊且为待结算态，禁止重复结算已结行；零元出院全结允许空列表。
        List<ChargeItem> chargeItems = Collections.emptyList();
        if (!request.getChargeItemIds().isEmpty()) {
            ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
            loadCmd.setIds(request.getChargeItemIds());
            chargeItems = chargeItemQueryService.loadChargeItemsByIds(loadCmd);
            this.assertChargeItemsForInpatientSettle(chargeItems, request.getChargeItemIds(), request.getEncounterId());
        }
        // Pre：与历史成功/退款中流水挂载 chargeItem 求交，命中则拒绝（BR-IP-STL-007）。
        List<PaymentReconciliationQueryModel> overlapping =
            paymentReconciliationQueryService.findPaySuccessOrRefundingOverlappingChargeItems(
                request.getEncounterId(),
                request.getChargeItemIds());
        if (overlapping != null && !overlapping.isEmpty()) {
            // Pre：所选费用已与历史 SUCCESS/REFUNDING 流水绑定，禁止重复结算。
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_106,
                "所选费用已存在成功结算流水");
        }
        InpatientDepositSettlementPlan depositPlan = this.resolveDepositSettlementPlanForConfirm(
            request,
            payment,
            chargeItems);
        BigDecimal expectedChannelPayable = depositPlan.channelPayable();
        BigDecimal submitTotal = this.sumPaymentDetailAmount(request.getPaymentDetails());
        // Pre：渠道实付合计须与轧差计划 C 一致（C=0 时允许空明细）。
        if (!MonetaryAmountSupport.equalsAtSettleScale(expectedChannelPayable, submitTotal)) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_106,
                "渠道实付合计与应收不一致");
        }
        if (expectedChannelPayable.compareTo(BigDecimal.ZERO) > 0 && submitTotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_106,
                "请录入支付明细");
        }
        // Pre：加载就诊下全部待结算行（排除挂号锚点），用于出院全结/中途结算判定。
        Set<Long> allPendingIds = this.loadAllPendingChargeItemIds(request.getEncounterId());
        Set<Long> selectedIds = new HashSet<>(request.getChargeItemIds());
        String insuranceSettleMode = this.resolveInsuranceSettleMode(payment.getId(), request.getYbPreSettleId());
        String settleMode;
        if (insuranceSettleMode != null) {
            settleMode = insuranceSettleMode;
        } else {
            settleMode = this.resolveSettleMode(encounter.getStatusEnum(), selectedIds, allPendingIds, null);
        }
        if (InpatientSettleConstants.SETTLE_MODE_MID.equals(settleMode)) {
            // Gate：正式结算仍不支持中途；医护站 MID 草稿仅供试算，须出院后再预结算。
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_112,
                "本期不支持中途结算，请办理护士出院后全选待结算费用结算");
        }
        boolean dischargeFullSettle =
            InpatientSettleConstants.SETTLE_MODE_DISCHARGE_FULL.equals(settleMode);
        // Pre：出院全结须先通过 ATD-03/04/05 同源临床门禁（长期/临时/待发退药清零）。
        if (dischargeFullSettle) {
            this.assertDischargeClinicalGateForDischargeFullSettle(request.getEncounterId(), settleMode);
        }
        patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
            request.getPatientId(),
            encounter.getPatientId(),
            InpatientSettleConstants.EX_IP_STL_106,
            "患者标识与就诊不一致");
        BigDecimal receivableTotal = this.sumChargeItemAmount(chargeItems);
        // 兼容历史脏草稿：正式结算前若 payment.contract_no 仍为空，从收费项记账账户补写（BR-IP-STL-016）。
        if (StringUtils.isBlank(payment.getContractNo()) && !request.getChargeItemIds().isEmpty()) {
            String contractBusNo = this.resolvePaymentContractBusNoForPreSettle(
                null,
                chargeItems,
                request.getEncounterId());
            paymentReconciliationDomainService.backfillPaymentContractNoIfBlank(
                request.getPaymentDraftId(),
                contractBusNo);
        }
        InpatientChargeConfirmExtensionOut ybConfirmResult = null;
        try {
            // Cmd：医保扩展开启时先 confirm 2304，再推进本地支付 SUCCESS。
            ybConfirmResult = this.runInsuranceConfirmIfNeeded(request);
            SettleInpatientPaymentCommand settleCommand =
                inpatientSettleMapper.toSettleInpatientPaymentCommand(request, operatorId);
            if (ybConfirmResult != null) {
                settleCommand.setYbSettlements(this.buildInpatientYbSettlementItems(ybConfirmResult));
            }
            // Pre：组装结算落账明细：医保基金/个账（非患者自付）+ 冲抵 O + 渠道实付 C；主单金额保持 P。
            List<PaymentDetailItemCommand> settleReceiptDetails = this.buildDischargeSettlePayReceiptDetails(
                request,
                depositPlan,
                ybConfirmResult,
                request.getPaymentDraftId());
            settleCommand.setPayReceiptDetails(settleReceiptDetails);
            if (expectedChannelPayable.compareTo(BigDecimal.ZERO) > 0) {
                // Pre：须对「即将落库」的明细做扫码替换（SCAN_PAY→微信/支付宝/银行卡）；
                // 禁止另拷一份 channelDetails——否则银联成功但落库仍可能残留草稿 CASHIER/SCAN_PAY。
                thirdPartyPaymentSettleOrchestrator.applyScanBeforeSettle(
                    request.getPaymentDraftId(),
                    settleReceiptDetails,
                    request.getClientScanResult(),
                    operatorId,
                    null);
                // Gate：补收行必须已是可展示/可落库渠道（现金/微信/支付宝/银行卡），禁止 CASHIER/SCAN_PAY 进入 SUCCESS。
                this.assertSettleSelfPayChannelsPersistable(settleReceiptDetails);
                this.attachClientScanTradeNoToSelfPayLines(settleReceiptDetails, request.getClientScanResult());
            }
            // Cmd ①：支付草稿推进为 SUCCESS（明细覆盖为冲抵+渠道+医保基金行）。
            Long paymentId = paymentReconciliationSettleOrchestration.settleInpatientPayment(settleCommand);
            BatchChargeItemIdsCommand settleChargeCmd =
                inpatientSettleMapper.toBatchChargeItemIdsCommand(request.getChargeItemIds(), operatorId);
            // Cmd：收费时间与 payment.bill_date 同值写入 ChargeItem.entered_date
            settleChargeCmd.setBillDate(settleCommand.getBillDate());
            if (!request.getChargeItemIds().isEmpty()) {
                // Cmd ②：选中收费项推进为 SETTLED，并落收费人/收费时间（enterer_*）。
                chargeItemDomainService.settleInpatientChargeItems(settleChargeCmd);
            }
            // Cmd ②b：仅冲抵额 O>0 时扣减个人现金户（占用押金，不落 HOSPITAL_DEPOSIT UN_PAY）。
            if (depositPlan.depositOffsetAmount().compareTo(BigDecimal.ZERO) > 0) {
                AdjustAdvanceDepositBalanceCommand deductBalanceCmd =
                    inpatientPrepayMapper.toRefundBalanceAdjustCommand(
                        request.getPatientId(),
                        request.getEncounterId(),
                        depositPlan.depositOffsetAmount(),
                        operatorId);
                accountDomainService.adjustAdvanceDepositBalance(deductBalanceCmd);
            }
            PaymentReconciliationQueryModel settledPayment =
                paymentReconciliationQueryService.mustGetPayment(paymentId);
            // Cmd ③：票面（收费项合计）> 0 时创建发票草稿（BR-IP-STL-008 / BR-INV-008）；真正零元出院全结跳过开票。
            BigDecimal invoiceFaceAmount = MonetaryAmountSupport.scaleSettle(receivableTotal);
            CreateDraftInvoiceCommand invoiceCommand =
                inpatientSettleMapper.toCreateDraftInvoiceCommand(settledPayment, paymentId, invoiceFaceAmount);
            invoiceCommand.setOperatorId(operatorId);
            Long invoiceDraftId = invoiceAfterPaymentOrchestration.createDraftAndScheduleIssueIfPayable(invoiceCommand);
            Long advanceRefundPaymentId = null;
            boolean prepayRefundPending = false;
            List<InpatientPrepayRefundPlanItemDto> pendingPrepayRefundPlan = Collections.emptyList();
            if (dischargeFullSettle) {
                // Cmd ④：冲抵后库内余额盈余一律现金退（不判原收款渠道、不挂 Win 第二阶段）。
                DischargePrepayRefundOutcome refundOutcome =
                    this.refundDischargeAdvanceDepositAsCash(
                        request.getEncounterId(),
                        request.getPatientId(),
                        operatorId,
                        settleCommand.getBillDate());
                advanceRefundPaymentId = refundOutcome.lastRefundPaymentId();
                prepayRefundPending = false;
                pendingPrepayRefundPlan = Collections.emptyList();
                LocalDateTime settlementTime = settleCommand.getBillDate();
                CompleteInpatientFinancialSettlementCommand completeCmd =
                    inpatientSettleMapper.toCompleteInpatientFinancialSettlementCommand(
                        request.getEncounterId(),
                        settlementTime,
                        operatorId);
                // Cmd ⑤：出院全结时就诊 PENDING→COMPLETED（有床）或 DISCHARGED（无床直结）。
                encounterDomainService.completeInpatientFinancialSettlement(completeCmd);
                // Asm：无活动床直结跳过清床，结算成功后异步合并上报 3505（与清床钩子同载荷）
                EncounterDetailQueryModel afterSettle =
                    encounterQueryService.mustGetEncounter(request.getEncounterId());
                if (EncounterStatus.DISCHARGED.getCode().equals(afterSettle.getStatusEnum())) {
                    this.scheduleClearBedSummarySalesUploadAfterCommit(request.getEncounterId());
                }
            }
            String ybSetlId = null;
            if (ybConfirmResult != null) {
                ybSetlId = ybConfirmResult.getSettlementId();
            }
            ConfirmInpatientSettleResponse response = inpatientSettleMapper.toConfirmInpatientSettleResponse(
                paymentId,
                invoiceDraftId,
                dischargeFullSettle,
                advanceRefundPaymentId,
                ybSetlId);
            response.setPrepayRefundPending(prepayRefundPending);
            if (prepayRefundPending) {
                response.setPrepayRefundPlan(pendingPrepayRefundPlan);
                response.setSuccessMessageCode(InpatientSettleConstants.MESSAGE_CODE_SETTLE_SUCCESS);
            }
            return response;
        } catch (BusinessException ex) {
            if (ErrorCodeConstants.ENCOUNTER_INVALID_STATUS.equals(ex.getErrorCode())) {
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_STL_109,
                    "就诊状态已变更，请刷新后重试");
            }
            throw ex;
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_109,
                "支付结算失败，请以流水为准重试");
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_109,
                "支付结算失败，请以流水为准重试");
        }
    }

    /**
     * IF-IP-STL-07：已结算单查阅。
     *
     * @param request 就诊、患者与可选指定结算单
     * @return 结算单快照与费用明细
     * @throws BusinessException 无结算记录时抛出 EX-IP-STL-110
     */
    public LoadSettledInpatientPaymentViewResponse loadSettledInpatientPaymentView(
        LoadSettledInpatientPaymentViewRequest request) {
        // Pre：读路径加载已结算支付单快照与关联费用明细（含历史 NET / reverseEnabled）。
        // Asm：Mapper 转 Response；再按支付单回填医保基金分项（对齐门诊支付记录）。
        LoadSettledInpatientPaymentViewResponse response = inpatientSettleReadMapper.toPaymentViewResponse(
            inpatientSettleReadLoader.loadSettledInpatientPaymentView(
                inpatientSettleReadMapper.toPaymentViewQuery(request)));
        this.fillSettledPaymentInsuranceAmounts(response);
        return response;
    }

    /**
     * 按支付单回填医保 2303/2304 基金分项（基本统筹/个账/现金/全自费等），供已结算明细对齐图一口径。
     *
     * <p>先取已结算预结算快照（含 settleMode / 个账现金），再以正式结算回执覆盖 hifp 等分项。</p>
     *
     * @param response 已结算查阅响应
     */
    private void fillSettledPaymentInsuranceAmounts(LoadSettledInpatientPaymentViewResponse response) {
        if (response == null || response.getPayments() == null || response.getPayments().isEmpty()) {
            return;
        }
        if (!inpatientChargeInsuranceExtension.isEnabled()) {
            return;
        }
        for (SettledPaymentBriefDto brief : response.getPayments()) {
            if (brief == null || brief.getPaymentId() == null) {
                continue;
            }
            InpatientChargePreSettleExtensionOut pre =
                inpatientChargeInsuranceExtension.findSettledPreSettleByPayment(brief.getPaymentId());
            if (pre != null) {
                brief.setInsuranceSettled(Boolean.TRUE);
                brief.setSettleMode(pre.getSettleMode());
                brief.setYbMedfeeSumamt(pre.getMedfeeSumamt());
                brief.setYbFundPaySumamt(pre.getFundPaySumamt());
                brief.setYbHifpPay(pre.getHifpPay());
                brief.setYbOthPay(pre.getOthPay());
                brief.setYbAcctPay(pre.getAcctPay());
                brief.setYbPsnCashPay(pre.getPsnCashPay());
                brief.setYbActPayDedc(pre.getActPayDedc());
                brief.setYbCvlservPay(pre.getCvlservPay());
                brief.setYbFulamtOwnpayAmt(pre.getFulamtOwnpayAmt());
                brief.setYbPreselfpayAmt(pre.getPreselfpayAmt());
                brief.setYbInscpScpAmt(pre.getInscpScpAmt());
                brief.setYbOverlmtSelfpay(pre.getOverlmtSelfpay());
            }
            // Pre：正式结算回执补齐 hifp/全自费等（预结算表无 hifp 列）
            if (!insuranceSettlementRecordLookupExtension.isEnabled()) {
                continue;
            }
            Optional<InsuranceSettlementFundLookupExtensionOut> fundOpt =
                insuranceSettlementRecordLookupExtension.findFundByPaymentId(brief.getPaymentId());
            if (fundOpt.isEmpty()) {
                continue;
            }
            InsuranceSettlementFundLookupExtensionOut fund = fundOpt.get();
            brief.setInsuranceSettled(Boolean.TRUE);
            if (fund.getMedfeeSumamt() != null) {
                brief.setYbMedfeeSumamt(fund.getMedfeeSumamt());
            }
            if (fund.getFundPaySumamt() != null) {
                brief.setYbFundPaySumamt(fund.getFundPaySumamt());
            }
            brief.setYbHifpPay(fund.getHifpPay());
            brief.setYbOthPay(fund.getOthPay());
            if (fund.getAcctPay() != null) {
                brief.setYbAcctPay(fund.getAcctPay());
            }
            if (fund.getPsnCashPay() != null) {
                brief.setYbPsnCashPay(fund.getPsnCashPay());
            }
            brief.setYbActPayDedc(fund.getActPayDedc());
            brief.setYbCvlservPay(fund.getCvlservPay());
            brief.setYbFulamtOwnpayAmt(fund.getFulamtOwnpayAmt());
            brief.setYbPreselfpayAmt(fund.getPreselfpayAmt());
            brief.setYbInscpScpAmt(fund.getInscpScpAmt());
            brief.setYbOverlmtSelfpay(fund.getOverlmtSelfpay());
        }
    }

    /**
     * IF-IP-RVS-01：住院反结（收银台已结算 Tab；未离院）。
     *
     * @param request 正向成功支付单与三主键
     * @return 逆向反结支付单 ID 与成功标识
     * @throws BusinessException EX-IP-RVS-001～005
     */
    public ReverseInpatientSettleResponse reverseInpatientSettle(ReverseInpatientSettleRequest request) {
        // Gate：收银台入口禁止 DISCHARGED 本页反结；已离院须走召回（同页 IF-IP-RCL-01）。
        return this.executeReverseInpatientSettle(request, false);
    }

    /**
     * IF-IP-RCL-01：住院出院召回（收银台已结算历史；仅 DISCHARGED）。
     *
     * <p>同事务：复用反结内核（allowDischarged）+ {@code DISCHARGED→IN_PROGRESS} + INSERT 活动 WARD（不占床）。</p>
     *
     * @param request 就诊/患者及可选支付单
     * @return 召回结果（{@code targetStatusEnum=IN_PROGRESS}、{@code requiresBedAssignment=false}）
     * @throws BusinessException EX-IP-RCL-001～004
     */
    public RecallInpatientDischargeResponse recallInpatientDischarge(RecallInpatientDischargeRequest request) {
        // Gate：三主键/就诊标识必填。
        if (request == null || request.getEncounterId() == null || request.getPatientId() == null) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_RCL_001,
                "就诊标识与患者标识不能为空");
        }
        Long encounterId = request.getEncounterId();
        Long patientId = request.getPatientId();
        // Gate：写路径须已登录操作人。
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            InpatientSettleConstants.EX_IP_RCL_004,
            "出院召回失败，请刷新后重试");
        try {
            // Pre：住院 IMP；仅 DISCHARGED 可召回，COMPLETED 未清床须点反结。
            EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
                encounterId,
                EncounterClass.IMP.getCode(),
                InpatientSettleConstants.EX_IP_RCL_001,
                "当前状态不允许出院召回，请刷新");
            patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
                patientId,
                encounter.getPatientId(),
                InpatientSettleConstants.EX_IP_RCL_001,
                "患者标识与就诊不一致");
            String statusCode = encounter.getStatusEnum();
            if (EncounterStatus.COMPLETED.getCode().equals(statusCode)) {
                // Pre：未清床须本页反结，禁止召回。
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_RCL_001,
                    "未清床患者请直接反结");
            }
            if (!EncounterStatus.DISCHARGED.getCode().equals(statusCode)) {
                // Pre：非已离院终止态不允许召回。
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_RCL_001,
                    "当前状态不允许出院召回，请刷新");
            }
            // Pre：解析末次已完成病区，供 INSERT 活动 WARD。
            Optional<Long> lastWard = encounterQueryService.findLastCompletedWardLocationIdByEncounterId(encounterId);
            if (lastWard.isEmpty()) {
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_RCL_004,
                    "出院召回失败，请刷新后重试");
            }
            Long wardLocationId = lastWard.get();
            // Pre：解析正向 SUCCESS 出院全结单（请求指定或末次）。
            Long paymentId = request.getPaymentId();
            if (paymentId == null) {
                paymentId = inpatientReverseSettlePreOrchestrator
                    .resolveLastForwardSuccessInpatientClinicPaymentId(encounterId);
            }
            if (paymentId == null) {
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_RCL_002,
                    "反结未成功，无法召回");
            }
            ReverseInpatientSettleRequest reverseRequest = new ReverseInpatientSettleRequest();
            reverseRequest.setPaymentId(paymentId);
            reverseRequest.setEncounterId(encounterId);
            reverseRequest.setPatientId(patientId);
            // Cmd：内嵌 FIN-004 反结（允许 DISCHARGED；不触发 COMPLETED→IN_PROGRESS）。
            ReverseInpatientSettleResponse reverseResponse;
            try {
                reverseResponse = this.executeReverseInpatientSettle(reverseRequest, true);
            } catch (BusinessException ex) {
                if (InpatientSettleConstants.EX_IP_RVS_004.equals(ex.getErrorCode())) {
                    throw new BusinessException(
                        InpatientSettleConstants.EX_IP_RCL_003,
                        "未找到可撤回的出院医嘱");
                }
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_RCL_002,
                    "反结未成功，无法召回");
            }
            // Cmd：DISCHARGED→IN_PROGRESS + INSERT 活动 WARD（不占床）。
            RecallInpatientDischargeCommand recallCommand =
                inpatientSettleMapper.toRecallInpatientDischargeCommand(
                    encounterId, operatorId, wardLocationId);
            encounterDomainService.recallInpatientDischarge(recallCommand);
            Long refundPaymentId = null;
            if (reverseResponse != null) {
                refundPaymentId = reverseResponse.getRefundPaymentId();
            }
            // Asm：返回已入科态；不要求二次分床。
            return inpatientSettleMapper.toRecallInpatientDischargeResponse(
                encounterId,
                EncounterStatus.IN_PROGRESS.getCode(),
                false,
                refundPaymentId);
        } catch (BusinessException ex) {
            if (InpatientSettleConstants.EX_IP_RCL_001.equals(ex.getErrorCode())
                || InpatientSettleConstants.EX_IP_RCL_002.equals(ex.getErrorCode())
                || InpatientSettleConstants.EX_IP_RCL_003.equals(ex.getErrorCode())) {
                throw ex;
            }
            if (ErrorCodeConstants.ENCOUNTER_INVALID_STATUS.equals(ex.getErrorCode())) {
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_RCL_001,
                    "当前状态不允许出院召回，请刷新");
            }
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_RCL_004,
                "出院召回失败，请刷新后重试");
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_RCL_004,
                "出院召回失败，请刷新后重试");
        }
    }

    /**
     * 住院反结编排序列内核（IF-IP-RVS-01；召回 IF-IP-RCL-01 复用）。
     *
     * @param request         反结请求
     * @param allowDischarged 为 true 时允许 DISCHARGED（召回内嵌）；为 false 时 DISCHARGED 拒绝
     * @return 反结响应
     * @throws BusinessException EX-IP-RVS-001～005
     */
    private ReverseInpatientSettleResponse executeReverseInpatientSettle(
        ReverseInpatientSettleRequest request,
        boolean allowDischarged) {
        // Gate：反结须已登录操作员与校对人（撤销出院嘱）。
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            InpatientSettleConstants.EX_IP_RVS_001,
            "请先登录");
        Long performerCheckId = currentPractitionerResolver.requirePractitionerId(
            InpatientSettleConstants.EX_IP_RVS_001,
            "请先登录");
        LocalDateTime checkTime = LocalDateTime.now();
        // Pre：加载原正向成功结算单，非 SUCCESS 或非住院结算类型则拒绝。
        PaymentReconciliationQueryModel originalPayment =
            paymentReconciliationQueryService.mustGetPayment(request.getPaymentId());
        if (!PaymentStatus.SUCCESS.equals(originalPayment.getStatusEnum())) {
            // Pre：仅正向 SUCCESS 单可反结，REFUNDED 原单须走历史展示不可写。
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_RVS_001,
                "请刷新并重新选择结算单");
        }
        if (!PaymentKind.INPATIENT_CLINIC.equals(originalPayment.getKindEnum())) {
            // Pre：须为住院临床出院全结单。
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_RVS_001,
                "请刷新并重新选择结算单");
        }
        if (originalPayment.getRelationId() != null) {
            // Pre：逆向单本身不可作为反结原单。
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_RVS_001,
                "请刷新并重新选择结算单");
        }
        if (!request.getEncounterId().equals(originalPayment.getEncounterId())
            || !request.getPatientId().equals(originalPayment.getPatientId())) {
            // Pre：支付单须与请求就诊/患者一致。
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_RVS_001,
                "请刷新并重新选择结算单");
        }
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            request.getEncounterId(),
            EncounterClass.IMP.getCode(),
            InpatientSettleConstants.EX_IP_RVS_001,
            "就诊记录不是住院类别，不能反结");
        // Pre：已离院禁止本页直接反结，须走召回（allowDischarged 时跳过）。
        if (!allowDischarged
            && EncounterStatus.DISCHARGED.getCode().equals(encounter.getStatusEnum())) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_RVS_003,
                "已离院请先召回");
        }
        patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
            request.getPatientId(),
            encounter.getPatientId(),
            InpatientSettleConstants.EX_IP_RVS_001,
            "患者标识与就诊不一致");
        // Pre：一笔结算仅允许一次反结，已存在逆向关联则拒绝。
        if (Boolean.TRUE.equals(
            paymentReconciliationQueryService.hasRefundByRelationId(request.getPaymentId()).getExists())) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_RVS_001,
                "请刷新并重新选择结算单");
        }
        List<Long> paymentBoundChargeItemIds =
            inpatientReverseSettlePreOrchestrator.parseBoundChargeItemIds(originalPayment.getChargeItemIds());
        ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
        loadCmd.setIds(paymentBoundChargeItemIds);
        List<ChargeItem> paymentBoundChargeItems = chargeItemQueryService.loadChargeItemsByIds(loadCmd);
        // Pre：原单绑定费用须均为 SETTLED，否则数据已变化。
        inpatientReverseSettlePreOrchestrator.assertAllChargeItemsSettledForReverse(
            paymentBoundChargeItems, paymentBoundChargeItemIds, request.getEncounterId());
        // Pre：反结仅再生本支付单快照绑定的已结算行，历史 SETTLED 行保持终态供结算历史查阅（BR-IP-RVS-011）。
        List<Long> chargeItemIdsToRevert = paymentBoundChargeItemIds;
        // Pre：本期无中途结算，须存在可退回或已为草稿的医生开立出院嘱（category=34）。
        Long dischargeServiceRequestId =
            inpatientReverseSettlePreOrchestrator.resolveRejectableDischargeServiceRequestId(request.getEncounterId());
        if (dischargeServiceRequestId == null
            && !inpatientReverseSettlePreOrchestrator.hasDoctorDischargeRequestInDraft(request.getEncounterId())) {
            // Pre：既无可退回出院嘱、也无 DRAFT 出院嘱，拒绝反结。
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_RVS_004,
                "未找到可撤回的出院医嘱");
        }
        List<PaymentRecDetailQueryModel> paymentDetails =
            paymentReconciliationQueryService.listPaymentRecDetailsByPaymentId(request.getPaymentId());
        // Cmd：医保正式结算须先平台撤销（2305→2405→2302），再落本地反结，避免平台仍结/仍离院。
        this.runInsuranceRevokeBeforeReverse(
            request.getPaymentId(),
            request.getEncounterId(),
            chargeItemIdsToRevert);
        CancelInpatientSettleAsRefundCommand cancelCommand =
            inpatientSettleMapper.toCancelInpatientSettleAsRefundCommand(
                originalPayment, paymentDetails, operatorId);
        try {
            // Cmd：新建逆向反结支付单（REFUND_PROCESSING）。
            Long refundPaymentId = paymentReconciliationDomainService.cancelInpatientSettleAsRefund(cancelCommand);
            if (!chargeItemIdsToRevert.isEmpty()) {
                BatchChargeItemIdsCommand revertChargeCmd =
                    inpatientSettleMapper.toBatchChargeItemIdsCommand(chargeItemIdsToRevert, operatorId);
                // Cmd：为本支付单快照内源 SETTLED 行批量插入 PENDING_SETTLEMENT 再生行（refund_id 链回源行）。
                chargeItemDomainService.revertInpatientSettledChargeItems(revertChargeCmd);
            }
            // 出院全结反结：有冲抵明细须按 O 回冲个人现金户，避免再结重复计收（DD §3.5）。
            this.creditDepositOffsetOnDischargeReverseIfNeeded(originalPayment, operatorId);
            // 中途结算反结（非 INPATIENT_CLINIC）按本批已结回冲；出院全结不再走「本批合计」回冲。
            this.creditAccountBalanceOnMidSettleReverseIfNeeded(originalPayment, chargeItemIdsToRevert, operatorId);
            // Cmd：仅 COMPLETED 未清床时回退就诊临床态 IN_PROGRESS；DISCHARGED 由召回路径处理。
            if (EncounterStatus.COMPLETED.getCode().equals(encounter.getStatusEnum())) {
                encounterDomainService.revertInpatientFinancialSettlement(request.getEncounterId());
            }
            // Cmd：作废草稿或冲红已开具票据。
            this.voidInvoiceLinkedToPaymentOnCancel(request.getPaymentId(), operatorId);
            if (dischargeServiceRequestId != null) {
                RejectServiceRequestToDraftCommand rejectCommand =
                    inpatientSettleMapper.toRejectDischargeServiceRequestToDraftCommand(
                        dischargeServiceRequestId, operatorId, performerCheckId, checkTime);
                // Cmd：出院类诊疗退回草稿；已为 DRAFT 时幂等跳过。
                serviceRequestDomainService.rejectServiceRequestToDraft(rejectCommand);
            }
            CompleteInpatientSettleCancelRefundCommand completeCommand =
                inpatientSettleMapper.toCompleteInpatientSettleCancelRefundCommand(
                    request.getPaymentId(),
                    refundPaymentId,
                    null);
            // 逆向桥表：拷贝原单 setl 为 REVOKED（与门诊退费同态）
            PaymentReconciliationQueryModel originalForYb =
                paymentReconciliationQueryService.mustGetPayment(request.getPaymentId());
            completeCommand.setYbSettlements(
                PaymentYbSettlementSupport.copyAsRevoked(originalForYb.getYbSettlements()));
            // Cmd：原单与逆向单收口 REFUNDED，发布住院结算取消事件。
            paymentReconciliationDomainService.completeInpatientSettleCancelRefund(completeCommand);
            // Asm：若本就诊已走过清床汇总 3505，事务提交后异步冲销 3506（未清床反结为 no-op）
            this.scheduleClearBedSummarySalesReverseAfterCommit(request.getEncounterId());
            // Asm：返回逆向单 ID 与成功标识。
            return inpatientSettleMapper.toReverseInpatientSettleResponse(refundPaymentId);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_RVS_005,
                "反结失败，请以流水为准后重试");
        }
    }

    /**
     * 无床直结成功后：事务提交再异步触发住院净销售 3505（与清床钩子同 SPI）。
     *
     * <p>扩展未启用时跳过；失败由 CHS 侧记日志，不回滚结算事实。</p>
     *
     * @param encounterId 就诊主键
     */
    private void scheduleClearBedSummarySalesUploadAfterCommit(Long encounterId) {
        if (encounterId == null) {
            return;
        }
        // Gate：CHS 进销存未启用则不上报
        if (!inventoryUploadExtension.isEnabled()) {
            return;
        }
        InventoryUploadAfterDischargeSummarySalesExtensionIn uploadCommand =
            new InventoryUploadAfterDischargeSummarySalesExtensionIn().setEncounterId(encounterId);
        // Asm：afterCommit 后再异步出站，避免读到未提交的结算/离院态
        IntegrationAfterCommitSupport.runAfterCommit(
            () -> inventoryUploadExtension.uploadAfterDischargeSummarySales(uploadCommand));
    }

    /**
     * 反结成功后：事务提交再异步冲销清床（或无床直结）合并净销售 3506。
     *
     * <p>扩展未启用或本就诊无成功清床汇总 3505 时跳过（覆盖未清床反结）；
     * 召回内嵌反结复用本路径，无需单独挂点。</p>
     *
     * @param encounterId 就诊主键
     */
    private void scheduleClearBedSummarySalesReverseAfterCommit(Long encounterId) {
        if (encounterId == null) {
            return;
        }
        // Gate：CHS 进销存未启用则不上报
        if (!inventoryUploadExtension.isEnabled()) {
            return;
        }
        InventoryUploadAfterClearBedSummarySalesReverseExtensionIn reverseCommand =
            new InventoryUploadAfterClearBedSummarySalesReverseExtensionIn().setEncounterId(encounterId);
        // Asm：afterCommit 后再异步出站，避免反结回滚后误冲
        IntegrationAfterCommitSupport.runAfterCommit(
            () -> inventoryUploadExtension.uploadAfterClearBedSummarySalesReverse(reverseCommand));
    }

    /**
     * 校验收费项归属就诊且均为待结算态。
     *
     * @param chargeItems     已加载收费项
     * @param requestedIds    请求 ID 列表
     * @param encounterId     就诊 ID
     * @throws BusinessException 缺失或非待结算/非本就诊
     */
    private void assertChargeItemsForInpatientSettle(
        List<ChargeItem> chargeItems,
        List<Long> requestedIds,
        Long encounterId) {
        Map<Long, ChargeItem> loadedById = new HashMap<>();
        if (chargeItems != null) {
            for (ChargeItem chargeItem : chargeItems) {
                if (chargeItem != null && chargeItem.getId() != null) {
                    loadedById.put(chargeItem.getId(), chargeItem);
                }
            }
        }
        // 逐条校验请求 ID 在库中存在、归属本就诊且为 PENDING_SETTLEMENT。
        for (Long requestedId : requestedIds) {
            ChargeItem chargeItem = loadedById.get(requestedId);
            if (chargeItem == null) {
                // Pre：请求 ID 在库中缺失，禁止部分结算。
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_STL_001,
                    "请选择待结算费用");
            }
            if (!encounterId.equals(chargeItem.getEncounterId())) {
                // Pre：费用须归属当前就诊。
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_STL_001,
                    "所选费用不属于当前就诊");
            }
            if (!ChargeItemStatus.PENDING_SETTLEMENT.getCode().equals(chargeItem.getStatusEnum())) {
                // Pre：仅待结算态可进入预结算/正式结算。
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_STL_001,
                    "所选费用含非待结算项");
            }
        }
    }

    /**
     * 出院全结多退：库内余额盈余一律现金退（不挂原收款、不判渠道、不挂 Win）。
     *
     * @param encounterId        就诊 ID
     * @param patientId          患者 ID
     * @param operatorId         操作员 ID
     * @param settlementBillDate 结算账单时间；可空
     * @return 退款结果（永不挂起）
     */
    private DischargePrepayRefundOutcome refundDischargeAdvanceDepositAsCash(
        Long encounterId,
        Long patientId,
        Long operatorId,
        LocalDateTime settlementBillDate) {
        Account account = accountQueryService.getRegistrationAccount(patientId, encounterId);
        if (account == null || account.getId() == null) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_103,
                "请先完成住院登记");
        }
        BigDecimal refundTotal = InpatientAccountBalanceDisplayAssembler.resolveAccountBalance(account)
            .setScale(InpatientSettleConstants.SETTLE_AMOUNT_COMPARE_SCALE, RoundingMode.HALF_UP);
        if (refundTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return DischargePrepayRefundOutcome.done(null);
        }
        RefundAdvanceDepositCommand refundCommand =
            inpatientSettleMapper.toDischargeDepositRefundCommand(
                encounterId,
                patientId,
                account.getId(),
                refundTotal,
                operatorId);
        Long refundPaymentId = paymentReconciliationDomainService.refundAdvanceDeposit(refundCommand);
        SettleInpatientPaymentCommand refundSettleCommand =
            inpatientPrepayMapper.toSettleInpatientPaymentCommand(refundPaymentId);
        if (settlementBillDate != null) {
            refundSettleCommand.setBillDate(settlementBillDate);
        }
        paymentReconciliationSettleOrchestration.settleInpatientPayment(refundSettleCommand);
        AdjustAdvanceDepositBalanceCommand balanceCmd = inpatientPrepayMapper.toRefundBalanceAdjustCommand(
            patientId,
            encounterId,
            refundTotal,
            operatorId);
        accountDomainService.adjustAdvanceDepositBalance(balanceCmd);
        return DischargePrepayRefundOutcome.done(refundPaymentId);
    }

    /**
     * 医保/自费出院：按原收款渠道 LIFO 原路退预交金（已废除；委托现金退）。
     *
     * @deprecated 多退一律现金；保留签名供第二阶段完结兼容入口转调
     */
    @Deprecated
    private DischargePrepayRefundOutcome refundDischargeAdvanceDepositByOriginalChannels(
        ConfirmInpatientSettleRequest request,
        Long operatorId,
        LocalDateTime settlementBillDate,
        boolean deferMissingScanRefund) {
        if (request == null) {
            return DischargePrepayRefundOutcome.done(null);
        }
        return this.refundDischargeAdvanceDepositAsCash(
            request.getEncounterId(),
            request.getPatientId(),
            operatorId,
            settlementBillDate);
    }

    /**
     * 出院结算第二阶段：携带 Win 退货回传退清预交金并财务完结就诊（自费与医保共用）。
     *
     * @param request 就诊、患者与 Win 退货回传
     * @return 与确认结算同结构的响应（prepayRefundPending=false）
     */
    public ConfirmInpatientSettleResponse completeInpatientDischargePrepayRefund(
        CompleteInpatientDischargePrepayRefundRequest request) {
        if (request == null || request.getEncounterId() == null || request.getPatientId() == null) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_106,
                "就诊或患者标识不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            InpatientSettleConstants.EX_IP_STL_106,
            "重新预结算并核对");
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            request.getEncounterId(),
            EncounterClass.IMP.getCode(),
            InpatientSettleConstants.EX_IP_STL_106,
            "重新预结算并核对");
        patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
            request.getPatientId(),
            encounter.getPatientId(),
            InpatientSettleConstants.EX_IP_STL_106,
            "患者标识与就诊不一致");
        if (!EncounterStatus.PENDING.getCode().equals(encounter.getStatusEnum())) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_106,
                "当前就诊状态不允许完结预交金原路退，请刷新后重试");
        }
        ConfirmInpatientSettleRequest refundRequest = new ConfirmInpatientSettleRequest();
        refundRequest.setEncounterId(request.getEncounterId());
        refundRequest.setPatientId(request.getPatientId());
        refundRequest.setClientRefundResults(request.getClientRefundResults());
        DischargePrepayRefundOutcome refundOutcome = this.refundDischargeAdvanceDepositByOriginalChannels(
            refundRequest,
            operatorId,
            LocalDateTime.now(),
            false);
        if (refundOutcome.pending()) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_106,
                "预交金原路退未完成，请确认银联退货回传后重试");
        }
        CompleteInpatientFinancialSettlementCommand completeCmd =
            inpatientSettleMapper.toCompleteInpatientFinancialSettlementCommand(
                request.getEncounterId(),
                LocalDateTime.now(),
                operatorId);
        encounterDomainService.completeInpatientFinancialSettlement(completeCmd);
        EncounterDetailQueryModel afterSettle =
            encounterQueryService.mustGetEncounter(request.getEncounterId());
        if (EncounterStatus.DISCHARGED.getCode().equals(afterSettle.getStatusEnum())) {
            this.scheduleClearBedSummarySalesUploadAfterCommit(request.getEncounterId());
        }
        ConfirmInpatientSettleResponse response = new ConfirmInpatientSettleResponse();
        response.setAdvanceRefundPaymentId(refundOutcome.lastRefundPaymentId());
        response.setDischargeFullSettle(Boolean.TRUE);
        response.setPrepayRefundPending(Boolean.FALSE);
        response.setSuccessMessageCode(InpatientSettleConstants.MESSAGE_CODE_SETTLE_SUCCESS);
        return response;
    }

    /**
     * 医保出院预交金退款结果（自费原路退挂起复用同一结构）。
     *
     * @param lastRefundPaymentId 最后一笔退款支付单；无退款时 null
     * @param pendingPlan         待 Win 回传后再退的计划；无挂起时为空
     */
    private record DischargePrepayRefundOutcome(
        Long lastRefundPaymentId,
        List<InpatientPrepayRefundPlanItemDto> pendingPlan) {

        static DischargePrepayRefundOutcome done(Long lastRefundPaymentId) {
            return new DischargePrepayRefundOutcome(lastRefundPaymentId, Collections.emptyList());
        }

        static DischargePrepayRefundOutcome pending(
            Long lastRefundPaymentId,
            List<InpatientPrepayRefundPlanItemDto> pendingPlan) {
            return new DischargePrepayRefundOutcome(lastRefundPaymentId, pendingPlan);
        }

        boolean pending() {
            return pendingPlan != null && !pendingPlan.isEmpty();
        }
    }

    /**
     * 按账户余额 LIFO 拆分预交金原路退计划（成功 HOSPITAL_DEPOSIT 收款）。
     *
     * @param encounterId    就诊 ID
     * @param refundBalance  拟退总额（通常为库内余额）
     * @return 退款计划；余额为 0 时空列表
     */
    private List<InpatientPrepayRefundPlanItemDto> buildPrepayRefundPlan(
        Long encounterId,
        BigDecimal refundBalance) {
        BigDecimal remaining = BigDecimal.ZERO;
        if (refundBalance != null) {
            remaining = refundBalance.setScale(
                InpatientSettleConstants.SETTLE_AMOUNT_COMPARE_SCALE, RoundingMode.HALF_UP);
        }
        if (remaining.compareTo(BigDecimal.ZERO) <= 0 || encounterId == null) {
            return Collections.emptyList();
        }
        List<PaymentReconciliationQueryModel> allPayments =
            paymentReconciliationQueryService.findPaymentsByEncounter(encounterId);
        List<PaymentReconciliationQueryModel> collectPays = new ArrayList<>();
        for (PaymentReconciliationQueryModel payment : allPayments) {
            if (payment == null || payment.getId() == null) {
                continue;
            }
            if (!PaymentStatus.SUCCESS.equals(payment.getStatusEnum())) {
                continue;
            }
            if (payment.getKindEnum() == null || !payment.getKindEnum().allowsEmptyChargeItemIds()) {
                continue;
            }
            if (!PaymentType.PAY.getCode().equals(payment.getPaymentEnum())) {
                continue;
            }
            collectPays.add(payment);
        }
        collectPays.sort((left, right) -> {
            LocalDateTime leftBill = left.getBillDate();
            LocalDateTime rightBill = right.getBillDate();
            if (leftBill == null && rightBill == null) {
                return Long.compare(
                    right.getId() == null ? 0L : right.getId(),
                    left.getId() == null ? 0L : left.getId());
            }
            if (leftBill == null) {
                return 1;
            }
            if (rightBill == null) {
                return -1;
            }
            int byBill = rightBill.compareTo(leftBill);
            if (byBill != 0) {
                return byBill;
            }
            return Long.compare(
                right.getId() == null ? 0L : right.getId(),
                left.getId() == null ? 0L : left.getId());
        });
        List<Long> paymentIds = new ArrayList<>();
        for (PaymentReconciliationQueryModel payment : collectPays) {
            paymentIds.add(payment.getId());
        }
        Map<Long, String> firstPayEnumById =
            paymentReconciliationQueryService.mapFirstPayEnumByPaymentIds(paymentIds);
        Map<Long, List<String>> channelCodesById =
            paymentChannelRefundOrchestrator.mapDistinctPayChannelCodesByPaymentIds(paymentIds);
        List<InpatientPrepayRefundPlanItemDto> plan = new ArrayList<>();
        for (PaymentReconciliationQueryModel payment : collectPays) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal available = payment.getDisplayAmount();
            if (available == null) {
                available = payment.getTenderedAmount();
            }
            if (available == null || available.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal slice = available.min(remaining).setScale(
                InpatientSettleConstants.SETTLE_AMOUNT_COMPARE_SCALE, RoundingMode.HALF_UP);
            if (slice.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String payChannel = firstPayEnumById.get(payment.getId());
            if (StringUtils.isBlank(payChannel)) {
                payChannel = PaymentChannel.CASHIER.getCode();
            }
            List<String> channelCodes = channelCodesById.getOrDefault(payment.getId(), Collections.emptyList());
            boolean requiresClientRefund =
                paymentChannelRefundOrchestrator.mayRequireUnionPayBpcRefund(channelCodes);
            InpatientPrepayRefundPlanItemDto item = new InpatientPrepayRefundPlanItemDto();
            item.setOriginalPaymentId(payment.getId());
            item.setPayChannelCode(payChannel);
            item.setAmount(slice);
            item.setRequiresClientRefund(requiresClientRefund);
            plan.add(item);
            remaining = remaining.subtract(slice);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            // 收款流水不足以覆盖余额时，剩余按现金退现兜底。
            InpatientPrepayRefundPlanItemDto fallback = new InpatientPrepayRefundPlanItemDto();
            fallback.setOriginalPaymentId(null);
            fallback.setPayChannelCode(PaymentChannel.CASHIER.getCode());
            fallback.setAmount(remaining);
            fallback.setRequiresClientRefund(Boolean.FALSE);
            plan.add(fallback);
        }
        return plan;
    }

    /**
     * 请求渠道明细 → 领域支付明细命令（扫码挂接用）。
     *
     * @param paymentDetails 确认结算渠道明细
     * @return PaymentDetailItemCommand 列表
     */
    private List<PaymentDetailItemCommand> toPaymentDetailItemCommands(
        List<InpatientSettlePaymentDetailDto> paymentDetails) {
        List<PaymentDetailItemCommand> details = new ArrayList<>();
        if (paymentDetails == null) {
            return details;
        }
        for (InpatientSettlePaymentDetailDto row : paymentDetails) {
            if (row == null || row.getAmount() == null) {
                continue;
            }
            PaymentDetailItemCommand item = new PaymentDetailItemCommand();
            item.setPayEnum(row.getPayChannelCode());
            item.setAmount(row.getAmount());
            details.add(item);
        }
        return details;
    }

    /**
     * 将确认请求中的 Win 退货回传按原支付单索引。
     *
     * @param items 回传列表
     * @return originalPaymentId → ClientRefundResultDto
     */
    private Map<Long, ClientRefundResultDto> indexClientRefundResults(
        List<InpatientSettleClientRefundItemDto> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, ClientRefundResultDto> indexed = new HashMap<>();
        for (InpatientSettleClientRefundItemDto item : items) {
            if (item == null || item.getOriginalPaymentId() == null || item.getClientRefundResult() == null) {
                continue;
            }
            indexed.put(item.getOriginalPaymentId(), item.getClientRefundResult());
        }
        return indexed;
    }

    /**
     * 中途结算反结时回冲账户余额；出院全结（{@link PaymentKind#INPATIENT_CLINIC}）反结不走本方法（冲抵回冲见
     * {@link #creditDepositOffsetOnDischargeReverseIfNeeded}）。
     *
     * @param originalPayment        正向结算支付单
     * @param revertedChargeItemIds  回退为待结算的收费项 ID
     * @param operatorId             操作员 ID
     */
    private void creditAccountBalanceOnMidSettleReverseIfNeeded(
        PaymentReconciliationQueryModel originalPayment,
        List<Long> revertedChargeItemIds,
        Long operatorId) {
        if (originalPayment == null) {
            return;
        }
        if (PaymentKind.INPATIENT_CLINIC.equals(originalPayment.getKindEnum())) {
            // 出院全结反结：余额回冲由冲抵明细 O 驱动，禁止再按本批费用合计回冲。
            return;
        }
        if (revertedChargeItemIds == null || revertedChargeItemIds.isEmpty()) {
            return;
        }
        if (originalPayment.getPatientId() == null || originalPayment.getEncounterId() == null) {
            return;
        }
        ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
        loadCmd.setIds(revertedChargeItemIds);
        List<ChargeItem> revertedItems = chargeItemQueryService.loadChargeItemsByIds(loadCmd);
        BigDecimal revertedTotal = this.sumChargeItemAmount(revertedItems);
        if (revertedTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        AdjustAdvanceDepositBalanceCommand creditCmd = inpatientPrepayMapper.toCollectBalanceAdjustCommand(
            originalPayment.getPatientId(),
            originalPayment.getEncounterId(),
            revertedTotal,
            operatorId);
        accountDomainService.adjustAdvanceDepositBalance(creditCmd);
    }

    /**
     * 出院全结反结：按结算明细冲抵额 O 回冲个人现金户（DD §3.5）。
     *
     * @param originalPayment 原正向出院结算单
     * @param operatorId      操作员
     */
    private void creditDepositOffsetOnDischargeReverseIfNeeded(
        PaymentReconciliationQueryModel originalPayment,
        Long operatorId) {
        if (originalPayment == null || originalPayment.getId() == null) {
            return;
        }
        if (!PaymentKind.INPATIENT_CLINIC.equals(originalPayment.getKindEnum())) {
            return;
        }
        if (originalPayment.getPatientId() == null || originalPayment.getEncounterId() == null) {
            return;
        }
        BigDecimal offsetAmount = this.sumDepositOffsetAmount(originalPayment.getId());
        if (offsetAmount.compareTo(BigDecimal.ZERO) <= 0) {
            // 无冲抵（R4/R5/R6/R7 或历史无冲抵行旧单）：不回冲余额。
            return;
        }
        AdjustAdvanceDepositBalanceCommand creditCmd = inpatientPrepayMapper.toCollectBalanceAdjustCommand(
            originalPayment.getPatientId(),
            originalPayment.getEncounterId(),
            offsetAmount,
            operatorId);
        accountDomainService.adjustAdvanceDepositBalance(creditCmd);
    }

    /**
     * 汇总支付单上预交金冲抵明细金额。
     *
     * @param paymentId 支付主键
     * @return 冲抵合计；无则 0
     */
    private BigDecimal sumDepositOffsetAmount(Long paymentId) {
        BigDecimal sum = BigDecimal.ZERO;
        if (paymentId == null) {
            return sum;
        }
        List<PaymentRecDetailQueryModel> details =
            paymentReconciliationQueryService.listPaymentRecDetailsByPaymentId(paymentId);
        if (details == null || details.isEmpty()) {
            return sum;
        }
        for (PaymentRecDetailQueryModel detail : details) {
            if (detail == null || detail.getAmount() == null) {
                continue;
            }
            if (!PaymentChannel.DEPOSIT_OFFSET.getCode().equals(detail.getPayEnum())) {
                continue;
            }
            sum = sum.add(detail.getAmount());
        }
        return MonetaryAmountSupport.scaleSettle(sum);
    }

    /**
     * 收费项 total_price 求和。
     *
     * @param chargeItems 收费项列表
     * @return 合计金额
     */
    private BigDecimal sumChargeItemAmount(List<ChargeItem> chargeItems) {
        BigDecimal sum = BigDecimal.ZERO;
        if (chargeItems == null) {
            return sum;
        }
        // 累加各收费项 total_price 作为应收基准。
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getTotalPrice() == null) {
                continue;
            }
            sum = sum.add(chargeItem.getTotalPrice());
        }
        return sum;
    }

    /**
     * 支付明细金额求和。
     *
     * @param details 支付明细
     * @return 合计
     */
    private BigDecimal sumPaymentDetailAmount(List<InpatientSettlePaymentDetailDto> details) {
        BigDecimal sum = BigDecimal.ZERO;
        if (details == null) {
            return sum;
        }
        // 累加渠道实付明细 amount 供与应收交叉校验。
        for (InpatientSettlePaymentDetailDto detail : details) {
            if (detail == null || detail.getAmount() == null) {
                continue;
            }
            sum = sum.add(detail.getAmount());
        }
        return sum;
    }

    /**
     * 收费项 ID 列表 → 逗号串。
     *
     * @param ids ID 列表
     * @return 逗号分隔串
     */
    private String joinIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        StringBuilder builder = new StringBuilder();
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(id);
        }
        return builder.toString();
    }

    /**
     * 逗号串 → 收费项 ID 列表。
     *
     * @param raw 持久化串
     * @return ID 列表
     */
    private List<Long> parseIds(String raw) {
        List<Long> ids = new ArrayList<>();
        if (StringUtils.isBlank(raw)) {
            return ids;
        }
        String[] parts = raw.split(",");
        for (String part : parts) {
            if (StringUtils.isBlank(part)) {
                continue;
            }
            ids.add(Long.valueOf(part.trim()));
        }
        return ids;
    }

    /**
     * 草稿绑定收费项与请求集合是否一致。
     *
     * @param draftIds   草稿侧逗号串
     * @param requestIds 请求 ID 列表
     * @return true 表示一致
     */
    private boolean sameChargeItemSet(String draftIds, List<Long> requestIds) {
        List<Long> parsed = this.parseIds(draftIds);
        if (requestIds == null || parsed.size() != requestIds.size()) {
            return false;
        }
        Map<Long, Boolean> requestIdMap = new HashMap<>();
        for (Long requestId : requestIds) {
            requestIdMap.put(requestId, Boolean.TRUE);
        }
        for (Long parsedId : parsed) {
            if (!requestIdMap.containsKey(parsedId)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 加载就诊下全部待结算收费项 ID（排除挂号锚点 service_table）。
     *
     * @param encounterId 就诊 ID
     * @return 待结算 ID 集合；无则空集
     */
    private Set<Long> loadAllPendingChargeItemIds(Long encounterId) {
        ChargeItemQueryCommand pendingQuery = new ChargeItemQueryCommand();
        pendingQuery.setEncounterId(encounterId);
        List<String> pendingStatusIn = new ArrayList<>();
        pendingStatusIn.add(ChargeItemStatus.PENDING_SETTLEMENT.getCode());
        pendingQuery.setStatusEnumIn(pendingStatusIn);
        pendingQuery.setExcludedServiceTable(TableNameConstants.ADM_HEALTHCARE_SERVICE);
        List<ChargeItem> allPending = chargeItemQueryService.listChargeItemsByEncounterId(pendingQuery);
        Set<Long> allPendingIds = new HashSet<>();
        for (ChargeItem pendingItem : allPending) {
            if (pendingItem != null && pendingItem.getId() != null) {
                allPendingIds.add(pendingItem.getId());
            }
        }
        return allPendingIds;
    }

    /**
     * 判定本次结算模式：收费处正式路径仅出院全结；医护站试算可显式传中途（跳过 2402，放宽就诊态）。
     *
     * @param encounterStatus         就诊状态
     * @param selectedChargeItemIds   用户选中 ID
     * @param allPendingChargeItemIds 就诊全部待结算 ID
     * @param requestSettleMode       请求显式指定模式（可 null）
     * @return {@link InpatientSettleConstants#SETTLE_MODE_DISCHARGE_FULL}
     *         或 {@link InpatientSettleConstants#SETTLE_MODE_MID}
     * @throws BusinessException 非 PENDING 却按出院全结、部分选或待出院按全结时
     */
    private String resolveSettleMode(
        String encounterStatus,
        Set<Long> selectedChargeItemIds,
        Set<Long> allPendingChargeItemIds,
        String requestSettleMode) {
        if (InpatientSettleConstants.SETTLE_MODE_MID.equals(requestSettleMode)) {
            // 医护站完整预结算：在院/待出院可试算（跳过 2402；平台 mid_setl_flag 由网关写死 0）。
            return InpatientSettleConstants.SETTLE_MODE_MID;
        }
        if (EncounterStatus.ON_HOLD.getCode().equals(encounterStatus)) {
            // Pre：待出院须先护士办理出院（BR-IP-STL-004）；正式出院全结不可绕过。
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_111,
                "请先办理护士出院后再结算");
        }
        boolean fullSelection = selectedChargeItemIds.equals(allPendingChargeItemIds);
        if (!EncounterStatus.PENDING.getCode().equals(encounterStatus)) {
            // Pre：仅护士已办理出院（PENDING）允许出院全结。
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_108,
                "请先完成护士出院后再结算");
        }
        if (!fullSelection) {
            // Pre：出院全结须全选待结算（无中途子集）。
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_107,
                "请全选待结算费用");
        }
        return InpatientSettleConstants.SETTLE_MODE_DISCHARGE_FULL;
    }

    /**
     * 出院全结 Pre：复用办理出院临床门禁（ATD-05 与 FIN-003 共用 {@link InpatientDischargeClinicalGate}）。
     *
     * @param encounterId 就诊主键
     * @param settleMode    本次结算模式
     * @throws BusinessException EX-IP-ATD-301/302/303/307/308
     */
    private void assertDischargeClinicalGateForDischargeFullSettle(Long encounterId, String settleMode) {
        if (!InpatientSettleConstants.SETTLE_MODE_DISCHARGE_FULL.equals(settleMode)) {
            return;
        }
        inpatientDischargeClinicalGate.assertDischargeClinicalGatePassed(encounterId);
    }

    /**
     * 医保第三方预结算（2303）：草稿主单金额 = 个人现金 P；响应渠道实付 = 轧差计划 C（自费同公式）。
     *
     * @param request             预结算请求（含读卡/合同信息）
     * @param paymentDraftId      本地支付草稿 ID
     * @param response            待回填的预结算响应
     * @param chargeItems         所选收费项
     * @param settleMode          出院全结/中途
     * @param registrationAccount 登记预交金账户（可空）
     */
    private void applyInsurancePreSettleIfNeeded(
        PreSettleInpatientChargesRequest request,
        Long paymentDraftId,
        PreSettleInpatientChargesResponse response,
        List<ChargeItem> chargeItems,
        String settleMode,
        Account registrationAccount) {
        if (!inpatientChargeInsuranceExtension.isEnabled()) {
            // 未启用医保扩展：纯自费口径。
            response.setSettlementComposition("SELF_PAY_ONLY");
            return;
        }
        Long insuranceContractId =
            this.resolveInsuranceContractId(request, chargeItems, request.getEncounterId());
        if (insuranceContractId == null) {
            // 无医保合同：按自费处理。
            response.setSettlementComposition("SELF_PAY_ONLY");
            return;
        }
        if (!inpatientChargeInsuranceExtension.supportsThirdPartyContract(insuranceContractId)) {
            // 合同非第三方医保：按自费处理。
            response.setSettlementComposition("SELF_PAY_ONLY");
            return;
        }
        if (StringUtils.isBlank(request.getPsnNo())) {
            // Gate：医保预结算须先读卡获取人员编号。
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_001,
                "医保预结算须先读卡获取人员编号");
        }
        InpatientChargePreSettleExtensionIn command = new InpatientChargePreSettleExtensionIn();
        command.setEncounterId(request.getEncounterId());
        command.setChargeItemIds(request.getChargeItemIds());
        command.setContractId(insuranceContractId);
        command.setPaymentDraftId(paymentDraftId);
        command.setPayerCertType(request.getYbMediumType());
        command.setPayerCertNo(request.getYbCardInfo());
        command.setInsuredPersonNo(request.getPsnNo());
        command.setInsuplcAdmdvs(request.getInsuplcAdmdvs());
        command.setInsutype(request.getInsutype());
        command.setSettleMode(settleMode);
        command.setAcctUsedFlag(request.getAcctUsedFlag());
        command.setPsnSetlway(request.getPsnSetlway());
        // Cmd：调用医保 2303 预结算，获取基金/个账/现金分摊。
        InpatientChargePreSettleExtensionOut ybResult = inpatientChargeInsuranceExtension.preSettle(command);
        response.setInsurancePreSettled(Boolean.TRUE);
        response.setSettlementComposition("INSURANCE_ONLY");
        response.setYbPreSettleId(ybResult.getPreSettleId());
        response.setYbMedfeeSumamt(ybResult.getMedfeeSumamt());
        response.setYbFundPaySumamt(ybResult.getFundPaySumamt());
        response.setYbAcctPay(ybResult.getAcctPay());
        response.setYbPsnCashPay(ybResult.getPsnCashPay());
        // Asm：2303 setlinfo 分项只读透传（对齐门诊预结算费用明细）。
        response.setYbHifpPay(ybResult.getHifpPay());
        response.setYbOthPay(ybResult.getOthPay());
        response.setYbActPayDedc(ybResult.getActPayDedc());
        response.setYbCvlservPay(ybResult.getCvlservPay());
        response.setYbCvlservFlag(ybResult.getCvlservFlag());
        BigDecimal psnCashPay = BigDecimal.ZERO;
        if (ybResult.getPsnCashPay() != null) {
            psnCashPay = ybResult.getPsnCashPay();
        }
        BigDecimal accountBalance = BigDecimal.ZERO;
        if (registrationAccount != null) {
            accountBalance = InpatientAccountBalanceDisplayAssembler.resolveAccountBalance(registrationAccount);
        }
        InpatientDepositSettlementPlan plan = inpatientDepositSettlementPlanner.resolve(
            psnCashPay,
            accountBalance);
        this.applyDepositSettlementPlanToPreSettleResponse(response, plan, request.getEncounterId());
        List<PaymentDetailItemCommand> draftLines = this.buildInpatientInsuranceCashPayDraftLines(
            request.getEncounterId(),
            chargeItems,
            plan.personalCashPayable());
        // Cmd：草稿主单金额 = P；占位明细挂个人现金应付（确认时再拆冲抵+渠道）。
        paymentReconciliationDomainService.refreshPaymentDraftPreSettleAmounts(
            paymentDraftId,
            plan.personalCashPayable(),
            draftLines);
        // 纯医保预结算：补写医保合同 bus_no，供日结费用性质筛选。
        paymentReconciliationDomainService.backfillPaymentContractNoIfBlank(
            paymentDraftId,
            this.resolveContractBusNoById(insuranceContractId));
    }

    /**
     * 从医保预结算快照取个人现金（院内预交金扣减口径）。
     *
     * @param paymentDraftId 支付草稿 ID
     * @return 2303 psn_cash_pay；无快照时 0
     */
    private BigDecimal resolveInsurancePatientCashPay(Long paymentDraftId) {
        List<InpatientChargePreSettleExtensionOut> activePreSettles =
            inpatientChargeInsuranceExtension.listActivePreSettlesByPaymentDraft(paymentDraftId);
        if (activePreSettles == null || activePreSettles.isEmpty()) {
            return BigDecimal.ZERO;
        }
        InpatientChargePreSettleExtensionOut first = activePreSettles.get(0);
        if (first == null || first.getPsnCashPay() == null) {
            return BigDecimal.ZERO;
        }
        return first.getPsnCashPay();
    }

    /**
     * 按收费项记账账户汇总预结算占位明细（自费/医保分桶，对齐门诊 IF-OPC-06）。
     *
     * @param encounterId 就诊主键
     * @param chargeItems 本次预结算收费项
     * @return 非空占位明细列表
     */
    private List<PaymentDetailItemCommand> buildInpatientPreSettlePaymentDetailDraftLines(
        Long encounterId,
        List<ChargeItem> chargeItems) {
        Long defaultChargeAccountId = null;
        Account defaultChargeAccount = accountQueryService.getDefaultChargeAccount(encounterId);
        if (defaultChargeAccount != null) {
            defaultChargeAccountId = defaultChargeAccount.getId();
        }
        Map<Long, BigDecimal> amountByAccountId = new LinkedHashMap<>();
        for (ChargeItem chargeLine : chargeItems) {
            if (chargeLine == null) {
                continue;
            }
            Long accountId = chargeLine.getAccountId();
            if (accountId == null) {
                accountId = defaultChargeAccountId;
            }
            if (accountId == null) {
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_STL_001,
                    "收费项未绑定结算账户且就诊未开立默认挂账账户，无法生成支付草稿");
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
            Long accountKey = entry.getKey();
            BigDecimal draftLineAmount = entry.getValue();
            PaymentDetailItemCommand draftLine = new PaymentDetailItemCommand();
            draftLine.setAccountId(accountKey);
            Account matchedAccount = accountById.get(accountKey);
            if (matchedAccount != null) {
                draftLine.setAccountCode(matchedAccount.getBusNo());
            }
            draftLine.setPayEnum(PaymentChannel.CASHIER.getCode());
            draftLine.setAmount(draftLineAmount);
            draftLine.setChargeAmount(draftLineAmount);
            draftLine.setResultEnum(PaymentStatus.DRAFT.getCode());
            draftLines.add(draftLine);
        }
        return draftLines;
    }

    /**
     * 医保预结算后占位明细：患者现金自付挂主记账户（来自收费项，非预交金登记户）。
     *
     * @param encounterId   就诊主键
     * @param chargeItems   所选收费项
     * @param cashPayAmount 医保 2303 返回的个人现金自付
     * @return 占位明细
     */
    private List<PaymentDetailItemCommand> buildInpatientInsuranceCashPayDraftLines(
        Long encounterId,
        List<ChargeItem> chargeItems,
        BigDecimal cashPayAmount) {
        List<PaymentDetailItemCommand> baseLines =
            this.buildInpatientPreSettlePaymentDetailDraftLines(encounterId, chargeItems);
        if (baseLines.isEmpty()) {
            return baseLines;
        }
        BigDecimal cashPay = cashPayAmount;
        if (cashPay == null) {
            cashPay = BigDecimal.ZERO;
        }
        Long primaryAccountId = this.resolvePrimaryBillingAccountId(encounterId, chargeItems);
        List<PaymentDetailItemCommand> draftLines = new ArrayList<>();
        boolean cashAssigned = false;
        for (PaymentDetailItemCommand line : baseLines) {
            if (line == null) {
                continue;
            }
            PaymentDetailItemCommand draftLine = new PaymentDetailItemCommand();
            draftLine.setAccountId(line.getAccountId());
            draftLine.setAccountCode(line.getAccountCode());
            draftLine.setPayEnum(PaymentChannel.CASHIER.getCode());
            draftLine.setResultEnum(PaymentStatus.DRAFT.getCode());
            draftLine.setChargeAmount(line.getChargeAmount());
            if (!cashAssigned && primaryAccountId.equals(line.getAccountId())) {
                draftLine.setAmount(cashPay);
                cashAssigned = true;
            } else {
                draftLine.setAmount(BigDecimal.ZERO);
            }
            draftLines.add(draftLine);
        }
        if (!cashAssigned) {
            draftLines.get(0).setAmount(cashPay);
        }
        return draftLines;
    }

    /**
     * 解析支付草稿 contract_no：请求显式合同优先，否则从收费项记账账户聚合（BR-IP-STL-016）。
     *
     * <p>多账户且 contract_no 不一致时返回 null（自付与医保同时付费，见 DM-FIN-002）。</p>
     *
     * @param contractFromRequest 请求显式指定的费用性质合同，可空
     * @param chargeItems         本次预结算/结算收费项
     * @param encounterId         就诊主键
     * @return fin_contract.bus_no，无法唯一确定时 null
     */
    private String resolvePaymentContractBusNoForPreSettle(
        ContractDetailQueryModel contractFromRequest,
        List<ChargeItem> chargeItems,
        Long encounterId) {
        if (contractFromRequest != null && StringUtils.isNotBlank(contractFromRequest.getBusNo())) {
            return contractFromRequest.getBusNo().trim();
        }
        Long defaultChargeAccountId = null;
        Account defaultChargeAccount = accountQueryService.getDefaultChargeAccount(encounterId);
        if (defaultChargeAccount != null) {
            defaultChargeAccountId = defaultChargeAccount.getId();
        }
        LinkedHashSet<Long> accountIds = new LinkedHashSet<>();
        if (defaultChargeAccountId != null) {
            accountIds.add(defaultChargeAccountId);
        }
        Long defaultSelfPayAccountId = accountQueryService.getSelfPayAccount(encounterId);
        if (defaultSelfPayAccountId != null) {
            accountIds.add(defaultSelfPayAccountId);
        }
        Long medicalInsuranceAccountId = accountQueryService.getMedicalInsuranceAccount(encounterId);
        if (medicalInsuranceAccountId != null) {
            accountIds.add(medicalInsuranceAccountId);
        }
        for (ChargeItem item : chargeItems) {
            if (item != null && item.getAccountId() != null) {
                accountIds.add(item.getAccountId());
            }
        }
        Map<Long, Account> accountById = accountQueryService.mapAccountsByIds(accountIds);
        LinkedHashSet<String> contractBusNos = new LinkedHashSet<>();
        for (ChargeItem item : chargeItems) {
            if (item == null) {
                continue;
            }
            Long accountId = item.getAccountId();
            if (accountId == null) {
                accountId = defaultChargeAccountId;
            }
            if (accountId == null) {
                continue;
            }
            Account account = accountById.get(accountId);
            if (account != null && StringUtils.isNotBlank(account.getContractNo())) {
                contractBusNos.add(account.getContractNo().trim());
            }
        }
        if (contractBusNos.size() == 1) {
            return contractBusNos.iterator().next();
        }
        if (contractBusNos.isEmpty()) {
            return null;
        }
        Long primaryAccountId = this.resolvePrimaryBillingAccountId(encounterId, chargeItems);
        if (primaryAccountId != null) {
            Account primaryAccount = accountById.get(primaryAccountId);
            if (primaryAccount != null && StringUtils.isNotBlank(primaryAccount.getContractNo())) {
                return primaryAccount.getContractNo().trim();
            }
        }
        return null;
    }

    /**
     * 解析住院结算主记账账户：优先收费项 accountId，回落医保账户再自费账户。
     *
     * @param encounterId 就诊主键
     * @param chargeItems   待结算收费明细
     * @return 主记账账户 ID
     */
    private Long resolvePrimaryBillingAccountId(Long encounterId, List<ChargeItem> chargeItems) {
        for (ChargeItem item : chargeItems) {
            if (item != null && item.getAccountId() != null) {
                return item.getAccountId();
            }
        }
        Long medicalInsuranceAccountId = accountQueryService.getMedicalInsuranceAccount(encounterId);
        if (medicalInsuranceAccountId != null) {
            return medicalInsuranceAccountId;
        }
        return accountQueryService.getSelfPayAccount(encounterId);
    }

    /**
     * 按合同主键读业务号 busNo；缺失或空白时返回 null。
     *
     * @param contractId 合同主键
     * @return 合同 busNo（trim 后）；不存在时为 null
     */
    private String resolveContractBusNoById(Long contractId) {
        if (contractId == null) {
            return null;
        }
        ContractDetailQueryModel contract = contractQueryService.findContractById(contractId);
        if (contract == null || StringUtils.isBlank(contract.getBusNo())) {
            return null;
        }
        return contract.getBusNo().trim();
    }

    /**
     * 解析本次预结算使用的医保合同 ID（请求优先，否则扫描收费项记账账户）。
     *
     * @param request     预结算请求
     * @param chargeItems 所选收费项
     * @param encounterId 就诊主键
     * @return 医保合同 ID；非医保时为 null
     */
    private Long resolveInsuranceContractId(
        PreSettleInpatientChargesRequest request,
        List<ChargeItem> chargeItems,
        Long encounterId) {
        if (request.getContractId() != null) {
            return request.getContractId();
        }
        LinkedHashSet<Long> accountIds = new LinkedHashSet<>();
        for (ChargeItem item : chargeItems) {
            if (item != null && item.getAccountId() != null) {
                accountIds.add(item.getAccountId());
            }
        }
        Long defaultChargeAccountId = null;
        Account defaultChargeAccount = accountQueryService.getDefaultChargeAccount(encounterId);
        if (defaultChargeAccount != null) {
            defaultChargeAccountId = defaultChargeAccount.getId();
        }
        if (defaultChargeAccountId != null) {
            accountIds.add(defaultChargeAccountId);
        }
        Long defaultSelfPayAccountId = accountQueryService.getSelfPayAccount(encounterId);
        if (defaultSelfPayAccountId != null) {
            accountIds.add(defaultSelfPayAccountId);
        }
        Long medicalInsuranceAccountId = accountQueryService.getMedicalInsuranceAccount(encounterId);
        if (medicalInsuranceAccountId != null) {
            accountIds.add(medicalInsuranceAccountId);
        }
        Map<Long, Account> accountById = accountQueryService.mapAccountsByIds(accountIds);
        for (ChargeItem item : chargeItems) {
            if (item == null) {
                continue;
            }
            Long accountId = item.getAccountId();
            if (accountId == null) {
                accountId = defaultChargeAccountId;
            }
            Account account = accountById.get(accountId);
            if (account == null || StringUtils.isBlank(account.getContractNo())) {
                continue;
            }
            Contract contract = contractQueryService
                .findOptionalContractEntityByBusNo(account.getContractNo().trim())
                .orElse(null);
            if (contract == null) {
                continue;
            }
            if (inpatientChargeInsuranceExtension.supportsThirdPartyContract(contract.getId())) {
                return contract.getId();
            }
        }
        return null;
    }

    /**
     * 正式结算前医保 confirm（2304）：返回医保结算 ID 供本地支付单关联。
     *
     * @param request 确认结算请求
     * @return 医保 setlId；非医保路径为 null
     */
    private InpatientChargeConfirmExtensionOut runInsuranceConfirmIfNeeded(ConfirmInpatientSettleRequest request) {
        if (!inpatientChargeInsuranceExtension.isEnabled()) {
            return null;
        }
        List<InpatientChargePreSettleExtensionOut> active =
            inpatientChargeInsuranceExtension.listActivePreSettlesByPaymentDraft(request.getPaymentDraftId());
        if (active.isEmpty()) {
            return null;
        }
        InpatientChargePreSettleExtensionOut target = active.get(active.size() - 1);
        if (request.getYbPreSettleId() != null) {
            for (InpatientChargePreSettleExtensionOut row : active) {
                if (request.getYbPreSettleId().equals(row.getPreSettleId())) {
                    target = row;
                    break;
                }
            }
        }
        InpatientChargeConfirmExtensionIn command = new InpatientChargeConfirmExtensionIn();
        command.setPaymentDraftId(request.getPaymentDraftId());
        command.setPreSettleId(target.getPreSettleId());
        command.setEncounterId(request.getEncounterId());
        // Cmd：Extension → 2304 正式结算，先于本地 payment 落账
        InpatientChargeConfirmExtensionOut ybResult = inpatientChargeInsuranceExtension.confirmBeforeLocalSettle(command);
        return ybResult;
    }

    /**
     * 反结/召回前撤销平台正式结算：2305 →（出院全结）2405 → 2302。
     *
     * <p>无医保回执时跳过；平台已撤/已在院类文案幂等忽略；其它失败阻断本地反结。
     * 半成功重试时预结算可能已 REVOKED，仍按 paymentId 走 2305 幂等并继续 2405/2302。</p>
     *
     * @param paymentId      原正向支付单 ID
     * @param encounterId    就诊 ID
     * @param chargeItemIds  本单绑定收费项（2302）
     */
    private void runInsuranceRevokeBeforeReverse(
        Long paymentId,
        Long encounterId,
        List<Long> chargeItemIds) {
        if (!inpatientChargeInsuranceExtension.isEnabled() || paymentId == null) {
            return;
        }
        InpatientChargePreSettleExtensionOut settled =
            inpatientChargeInsuranceExtension.findSettledPreSettleByPayment(paymentId);
        PaymentReconciliationQueryModel paymentForYb =
            paymentReconciliationQueryService.mustGetPayment(paymentId);
        boolean hasYbBridge = paymentForYb.getYbSettlements() != null
            && !paymentForYb.getYbSettlements().isEmpty();
        Long contractId = settled == null ? null : settled.getContractId();
        String settleMode = settled == null ? null : settled.getSettleMode();
        String settlementId = settled == null ? null : settled.getSettlementId();
        Long preSettleId = settled == null ? null : settled.getPreSettleId();
        String hint = "；请用医保信息查询(5201)核对在院/结算状态后再反结或召回";
        boolean insuranceRevokeAttempted = false;
        try {
            InpatientChargeRevokeSettleExtensionIn revokeSettle = new InpatientChargeRevokeSettleExtensionIn();
            revokeSettle.setPaymentId(paymentId);
            revokeSettle.setPreSettleId(preSettleId);
            revokeSettle.setSettlementId(settlementId);
            // Cmd：2305 撤销正式结算（本地已 REVOKED 时幂等）
            inpatientChargeInsuranceExtension.revokeSettle(revokeSettle);
            insuranceRevokeAttempted = true;
        } catch (RuntimeException ex) {
            if (this.isInsuranceSettlementMissingMessage(ex)) {
                if (!hasYbBridge && settled == null) {
                    // Pre：无平台结算回执 → 纯自费反结
                    return;
                }
                // Pre：回执已撤或不存在但支付侧曾挂医保 → 继续 2405/2302
                insuranceRevokeAttempted = true;
            } else if (!this.isInsuranceRevokeIdempotentMessage(ex, true)) {
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_RVS_005,
                    "医保结算撤销失败（2305）：" + this.safeExMessage(ex) + hint);
            } else {
                insuranceRevokeAttempted = true;
            }
        }
        if (!insuranceRevokeAttempted && settled == null && !hasYbBridge) {
            return;
        }
        if (contractId == null) {
            contractId = this.resolveMedicalInsuranceContractIdByEncounter(encounterId);
        }
        boolean dischargeFull = settleMode == null
            || InpatientSettleConstants.SETTLE_MODE_DISCHARGE_FULL.equals(settleMode);
        if (dischargeFull && contractId != null && encounterId != null) {
            try {
                InpatientChargeEncounterExtensionIn revokeDischarge = new InpatientChargeEncounterExtensionIn();
                revokeDischarge.setEncounterId(encounterId);
                revokeDischarge.setContractId(contractId);
                // Cmd：2405 撤销平台出院，回到在院
                inpatientChargeInsuranceExtension.revokeDischarge(revokeDischarge);
            } catch (RuntimeException ex) {
                if (!this.isInsuranceRevokeIdempotentMessage(ex, false)) {
                    throw new BusinessException(
                        InpatientSettleConstants.EX_IP_RVS_005,
                        "医保出院撤销失败（2405）：" + this.safeExMessage(ex) + hint);
                }
            }
        }
        if (chargeItemIds == null || chargeItemIds.isEmpty() || contractId == null || encounterId == null) {
            return;
        }
        try {
            InpatientChargeRevokeFeeDetailExtensionIn revokeFees = new InpatientChargeRevokeFeeDetailExtensionIn();
            revokeFees.setEncounterId(encounterId);
            revokeFees.setContractId(contractId);
            revokeFees.setChargeItemIds(chargeItemIds);
            // Cmd：2302 撤销本批费用明细，避免再预结撞旧 feedetl
            inpatientChargeInsuranceExtension.revokeFeeDetails(revokeFees);
        } catch (RuntimeException ex) {
            if (!this.isInsuranceRevokeIdempotentMessage(ex, true)) {
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_RVS_005,
                    "医保费用明细撤销失败（2302）：" + this.safeExMessage(ex) + hint);
            }
        }
    }

    /** 从就诊医保账户解析合同 ID（反结重试预结算已撤时兜底）。 */
    private Long resolveMedicalInsuranceContractIdByEncounter(Long encounterId) {
        if (encounterId == null) {
            return null;
        }
        Long medicalInsuranceAccountId = accountQueryService.getMedicalInsuranceAccount(encounterId);
        if (medicalInsuranceAccountId == null) {
            return null;
        }
        Account account = accountQueryService.mapAccountsByIds(Set.of(medicalInsuranceAccountId))
            .get(medicalInsuranceAccountId);
        if (account == null || StringUtils.isBlank(account.getContractNo())) {
            return null;
        }
        Contract contract = contractQueryService
            .findOptionalContractEntityByBusNo(account.getContractNo().trim())
            .orElse(null);
        if (contract == null) {
            return null;
        }
        if (!inpatientChargeInsuranceExtension.supportsThirdPartyContract(contract.getId())) {
            return null;
        }
        return contract.getId();
    }

    private String safeExMessage(RuntimeException ex) {
        if (ex == null || ex.getMessage() == null || ex.getMessage().isBlank()) {
            return "平台返回失败";
        }
        return ex.getMessage().trim();
    }

    private boolean isInsuranceSettlementMissingMessage(RuntimeException ex) {
        String msg = this.safeExMessage(ex);
        return msg.contains("缺少 settlementRecordId")
            || msg.contains("结算回执不存在")
            || msg.contains("未找到结算")
            || msg.contains("无结算回执")
            || msg.contains("找不到结算")
            || (msg.contains("payment") && msg.contains("结算"));
    }

    /**
     * 平台撤销类接口是否可视为已完成（幂等）。
     *
     * @param ex          平台异常
     * @param settleOrFee true=2305/2302；false=2405
     */
    private boolean isInsuranceRevokeIdempotentMessage(RuntimeException ex, boolean settleOrFee) {
        String msg = this.safeExMessage(ex);
        if (settleOrFee) {
            if (msg.contains("已撤销") || msg.contains("已经撤销") || msg.contains("重复撤销")) {
                return true;
            }
            if (msg.contains("不存在") && (msg.contains("结算") || msg.contains("明细") || msg.contains("费用"))) {
                return true;
            }
            return false;
        }
        if (msg.contains("不是出院") || msg.contains("非出院") || msg.contains("未办理出院")) {
            return true;
        }
        if (msg.contains("入院状态") || msg.contains("已经是在院") || msg.contains("已是在院")) {
            return true;
        }
        return false;
    }

    /**
     * 住院医保 confirm 后组装本地支付单关联的医保结算明细（单条 CONFIRMED）。
     *
     * @param ybResult 医保 Extension confirm 出参
     * @return 结算明细命令列表；无 setlId 时空列表
     */
    private List<PaymentYbSettlementItemCommand> buildInpatientYbSettlementItems(
        InpatientChargeConfirmExtensionOut ybResult) {
        if (ybResult == null || StringUtils.isBlank(ybResult.getSettlementId())) {
            return List.of();
        }
        PaymentYbSettlementItemCommand item = new PaymentYbSettlementItemCommand();
        item.setSetlId(ybResult.getSettlementId().trim());
        if (StringUtils.isNotBlank(ybResult.getChargeBatchNo())) {
            item.setChrgBchno(ybResult.getChargeBatchNo().trim());
        }
        if (StringUtils.isNotBlank(ybResult.getEncounterPlatformId())) {
            item.setMdtrtId(ybResult.getEncounterPlatformId().trim());
        }
        item.setSettlementRecordId(ybResult.getSettlementRecordId());
        item.setStatus(PaymentYbSettlementStatus.CONFIRMED);
        return List.of(item);
    }

    /**
     * 追加一条住院医保/现金/冲抵支付明细（金额≤0 跳过）。
     *
     * @param target      明细列表
     * @param payEnum     支付渠道枚举 code
     * @param amount      金额
     * @param accountId   账户 ID
     * @param accountCode 账户业务号
     * @param setlId      医保结算号；冲抵行可空
     * @param billDate    账单时间
     * @param outcomeEnum 结果枚举
     */
    private void appendInpatientInsurancePaymentLine(
        List<PaymentDetailItemCommand> target,
        String payEnum,
        BigDecimal amount,
        Long accountId,
        String accountCode,
        String setlId,
        LocalDateTime billDate,
        String outcomeEnum) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        PaymentDetailItemCommand line = new PaymentDetailItemCommand();
        line.setPayEnum(payEnum);
        line.setAccountId(accountId);
        line.setAccountCode(accountCode);
        line.setAmount(amount);
        line.setChargeAmount(amount);
        line.setPayTransNo(setlId);
        line.setPayTransDate(billDate);
        line.setResultEnum(outcomeEnum);
        target.add(line);
    }

    /**
     * 从草稿关联的医保预结算快照解析 settleMode（出院全结/中途）。
     *
     * @param paymentDraftId 支付草稿 ID
     * @param ybPreSettleId  请求指定的预结算 ID（可 null）
     * @return settleMode；无医保预结算时为 null
     */
    private String resolveInsuranceSettleMode(Long paymentDraftId, Long ybPreSettleId) {
        if (!inpatientChargeInsuranceExtension.isEnabled() || paymentDraftId == null) {
            return null;
        }
        List<InpatientChargePreSettleExtensionOut> active =
            inpatientChargeInsuranceExtension.listActivePreSettlesByPaymentDraft(paymentDraftId);
        if (active.isEmpty()) {
            return null;
        }
        if (ybPreSettleId != null) {
            for (InpatientChargePreSettleExtensionOut row : active) {
                if (ybPreSettleId.equals(row.getPreSettleId())) {
                    return row.getSettleMode();
                }
            }
        }
        return active.get(active.size() - 1).getSettleMode();
    }

    /**
     * 住院反结时作废/冲红原单关联票据：DRAFT → voidDraftInvoice；ISSUED → writeoff。
     *
     * @param paymentId  被反结的原正向结算支付单 ID
     * @param operatorId 操作员 ID
     */
    private void voidInvoiceLinkedToPaymentOnCancel(Long paymentId, Long operatorId) {
        Invoice invoice = invoiceQueryService.getInvoiceByPaymentId(
            inpatientSettleMapper.toInvoiceQueryByPaymentId(paymentId));
        if (invoice == null || invoice.getId() == null) {
            // 无关联发票则跳过。
            return;
        }
        if (InvoiceStatus.CANCELLED.sameAs(invoice.getStatusEnum())) {
            // 已作废/冲红则幂等跳过。
            return;
        }
        CancelInvoiceCommand cancelCommand = inpatientSettleMapper.toCancelInvoiceCommand(
            invoice.getId(),
            InpatientSettleConstants.INVOICE_CANCEL_REASON_INPATIENT_SETTLE,
            operatorId);
        if (InvoiceStatus.DRAFT.sameAs(invoice.getStatusEnum())) {
            invoiceDomainService.voidDraftInvoice(cancelCommand);
            bossElectronicInvoiceAfterCommitOrchestration.afterInvoiceCancelled(invoice.getId(), cancelCommand.getReason());
            return;
        }
        if (InvoiceStatus.ISSUED.sameAs(invoice.getStatusEnum())) {
            invoiceDomainService.writeoffInvoiceAndCreateRedRecord(cancelCommand);
            bossElectronicInvoiceAfterCommitOrchestration.afterInvoiceCancelled(invoice.getId(), cancelCommand.getReason());
        }
    }

    /**
     * 正式结算：组装出院结算支付明细（医保基金/个账 + 冲抵 O + 渠道 C）。
     *
     * <p>患者自付勾稽 {@code P = O + C}；基金/个账不属于该等式。</p>
     *
     * @param request          确认结算请求
     * @param plan             轧差计划
     * @param ybConfirmResult  医保确认结果；自费时 null
     * @param paymentDraftId   支付草稿 ID
     * @return 落账明细列表（可空：零元且无冲抵）
     */
    private List<PaymentDetailItemCommand> buildDischargeSettlePayReceiptDetails(
        ConfirmInpatientSettleRequest request,
        InpatientDepositSettlementPlan plan,
        InpatientChargeConfirmExtensionOut ybConfirmResult,
        Long paymentDraftId) {
        List<PaymentDetailItemCommand> receiptLines = new ArrayList<>();
        Long billingAccountId = null;
        String billingAccountCode = null;
        List<PaymentRecDetailQueryModel> draftDetails =
            paymentReconciliationQueryService.listPaymentRecDetailsByPaymentId(paymentDraftId);
        if (draftDetails != null) {
            for (PaymentRecDetailQueryModel draftRow : draftDetails) {
                if (draftRow == null || draftRow.getAccountId() == null) {
                    continue;
                }
                billingAccountId = draftRow.getAccountId();
                if (StringUtils.isNotBlank(draftRow.getAccountCode())) {
                    billingAccountCode = draftRow.getAccountCode().trim();
                }
                break;
            }
        }
        Account registrationAccount = accountQueryService.getRegistrationAccount(
            request.getPatientId(),
            request.getEncounterId());
        Long depositAccountId = null;
        String depositAccountCode = null;
        if (registrationAccount != null) {
            depositAccountId = registrationAccount.getId();
            depositAccountCode = registrationAccount.getBusNo();
        }
        if (depositAccountId == null) {
            depositAccountId = billingAccountId;
            depositAccountCode = billingAccountCode;
        }
        String outcomeEnum = PaymentStatus.SUCCESS.getCode();
        LocalDateTime billDate = request.getBillDate();
        if (billDate == null) {
            billDate = LocalDateTime.now();
        }
        if (ybConfirmResult != null) {
            String setlId = ybConfirmResult.getSettlementId();
            this.appendInpatientInsurancePaymentLine(
                receiptLines,
                PaymentChannel.INSURANCE_FUND.getCode(),
                ybConfirmResult.getFundPaySumamt(),
                billingAccountId,
                billingAccountCode,
                setlId,
                billDate,
                outcomeEnum);
            this.appendInpatientInsurancePaymentLine(
                receiptLines,
                PaymentChannel.INSURANCE_ACCT.getCode(),
                ybConfirmResult.getAcctPay(),
                billingAccountId,
                billingAccountCode,
                setlId,
                billDate,
                outcomeEnum);
        }
        if (plan.depositOffsetAmount().compareTo(BigDecimal.ZERO) > 0) {
            if (depositAccountId == null) {
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_STL_103,
                    "请先完成住院登记");
            }
            // 冲抵占用个人现金户；禁止另开 HOSPITAL_DEPOSIT 主单。
            this.appendInpatientInsurancePaymentLine(
                receiptLines,
                PaymentChannel.DEPOSIT_OFFSET.getCode(),
                plan.depositOffsetAmount(),
                depositAccountId,
                depositAccountCode,
                null,
                billDate,
                outcomeEnum);
        }
        if (plan.channelPayable().compareTo(BigDecimal.ZERO) > 0 && request.getPaymentDetails() != null) {
            for (InpatientSettlePaymentDetailDto row : request.getPaymentDetails()) {
                if (row == null || row.getAmount() == null
                    || row.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                PaymentDetailItemCommand line = new PaymentDetailItemCommand();
                line.setPayEnum(row.getPayChannelCode());
                line.setAmount(row.getAmount());
                line.setChargeAmount(row.getAmount());
                line.setAccountId(depositAccountId);
                line.setAccountCode(depositAccountCode);
                line.setResultEnum(outcomeEnum);
                line.setPayTransDate(billDate);
                receiptLines.add(line);
            }
        }
        return receiptLines;
    }

    /**
     * 落库前门禁：结算补收行须为可展示、可落库渠道。
     *
     * <p>草稿占位 {@code CASHIER} 与 UI-only {@code SCAN_PAY} 禁止进入 SUCCESS；
     * 须先经 {@link ThirdPartyPaymentSettleOrchestrator#applyScanBeforeSettle} 将扫码行替换为
     * 微信/支付宝/银行卡，或收银员直接选择现金等用户可选渠道。对齐 **BR-IP-STL-006a**。</p>
     *
     * @param settleReceiptDetails 即将写入的支付明细（已含基金/冲抵/补收行）
     * @throws BusinessException 无补收行、仍为 CASHIER/SCAN_PAY 或渠道不可落库 → EX-IP-STL-106
     */
    private void assertSettleSelfPayChannelsPersistable(List<PaymentDetailItemCommand> settleReceiptDetails) {
        // Gate：C>0 时明细集合不可空，否则补收未录入
        if (settleReceiptDetails == null || settleReceiptDetails.isEmpty()) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_106,
                "结算补收明细为空，请重新选择支付方式");
        }
        boolean hasSelfPayLine = false;
        for (PaymentDetailItemCommand line : settleReceiptDetails) {
            // Pre：跳过空行；无 pay_enum 的行不参与补收校验
            if (line == null || StringUtils.isBlank(line.getPayEnum())) {
                continue;
            }
            String payCode = line.getPayEnum().trim();
            PaymentChannel channel = PaymentChannel.fromCode(payCode);
            // Gate：pay_enum 须能解析为已知 PaymentChannel
            if (channel == null) {
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_STL_106,
                    "支付方式不合法");
            }
            // Pre：基金/个账/冲抵不属于患者补收渠道，跳过
            if (PaymentChannel.INSURANCE_FUND.equals(channel)
                || PaymentChannel.INSURANCE_ACCT.equals(channel)
                || PaymentChannel.DEPOSIT_OFFSET.equals(channel)) {
                continue;
            }
            // Gate：草稿占位不得进入 SUCCESS（历史脏数据根因：占位未替换）
            if (PaymentChannel.CASHIER.equals(channel)) {
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_STL_106,
                    "结算补收渠道未落成，请重新选择支付方式并完成收款");
            }
            // Gate：须为用户可选且可落库（拒绝 SCAN_PAY）
            paymentChannelGate.requirePersistablePayChannelCode(
                payCode,
                InpatientSettleConstants.EX_IP_STL_106,
                "支付方式不合法");
            hasSelfPayLine = true;
        }
        // Gate：至少一行真实补收渠道，避免只有基金行却声称 C>0
        if (!hasSelfPayLine) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_106,
                "结算补收明细为空，请重新选择支付方式");
        }
    }

    /**
     * 扫码成功后把银联交易号写到患者补收行，便于 IF-IP-STL-07 查阅回显。
     *
     * <p>仅填充尚无 {@code payTransNo} 的补收行；基金/冲抵/占位行跳过。</p>
     *
     * @param settleReceiptDetails 即将落库明细
     * @param clientScanResult     Win 被扫回传；无扫码时可空
     */
    private void attachClientScanTradeNoToSelfPayLines(
        List<PaymentDetailItemCommand> settleReceiptDetails,
        ClientScanResultDto clientScanResult) {
        // Pre：无明细或无回传则无需挂交易号
        if (settleReceiptDetails == null || clientScanResult == null) {
            return;
        }
        String tradeNo = clientScanResult.getTradeNo();
        // Pre：回传无交易号则跳过
        if (StringUtils.isBlank(tradeNo)) {
            return;
        }
        String trimmedTradeNo = tradeNo.trim();
        for (PaymentDetailItemCommand line : settleReceiptDetails) {
            // Pre：跳过空行
            if (line == null || StringUtils.isBlank(line.getPayEnum())) {
                continue;
            }
            PaymentChannel channel = PaymentChannel.fromCode(line.getPayEnum().trim());
            // Pre：无法解析渠道码则跳过本行
            if (channel == null) {
                continue;
            }
            // Pre：基金/个账/冲抵/占位不写银联交易号
            if (PaymentChannel.INSURANCE_FUND.equals(channel)
                || PaymentChannel.INSURANCE_ACCT.equals(channel)
                || PaymentChannel.DEPOSIT_OFFSET.equals(channel)
                || PaymentChannel.CASHIER.equals(channel)) {
                continue;
            }
            // Asm：仅补空交易号，避免覆盖医保 setl_id 等已有值（现金直收无 trade_no 时保持空）
            if (StringUtils.isBlank(line.getPayTransNo())) {
                line.setPayTransNo(trimmedTradeNo);
            }
        }
    }

    /**
     * 确认结算时解析预交金轧差计划（自费 P=本批应收；医保 P=草稿个人现金）。
     *
     * @param request     确认请求
     * @param payment     支付草稿
     * @param chargeItems 本批收费项
     * @return 轧差计划
     */
    private InpatientDepositSettlementPlan resolveDepositSettlementPlanForConfirm(
        ConfirmInpatientSettleRequest request,
        PaymentReconciliationQueryModel payment,
        List<ChargeItem> chargeItems) {
        BigDecimal personalCashPayable;
        if (this.isInsuranceSettlePaymentDraft(payment.getId())) {
            personalCashPayable = payment.getDisplayAmount();
            if (personalCashPayable == null) {
                personalCashPayable = this.resolveInsurancePatientCashPay(payment.getId());
            }
        } else {
            personalCashPayable = this.sumChargeItemAmount(chargeItems);
        }
        Account account = accountQueryService.getRegistrationAccount(
            request.getPatientId(),
            request.getEncounterId());
        BigDecimal accountBalance = BigDecimal.ZERO;
        if (account != null) {
            accountBalance = InpatientAccountBalanceDisplayAssembler.resolveAccountBalance(account);
        }
        return inpatientDepositSettlementPlanner.resolve(
            personalCashPayable,
            accountBalance);
    }

    /**
     * 本就诊未退清的成功预交金收款是否含银联类渠道（多退已不依赖；保留供诊断/旧调用）。
     *
     * @param encounterId 就诊 ID
     * @return 含微信/支付宝/银行卡则为 true
     * @deprecated 多退一律现金，轧差不再判渠道
     */
    @Deprecated
    private boolean hasUnionPayDepositChannels(Long encounterId) {
        if (encounterId == null) {
            return false;
        }
        List<PaymentReconciliationQueryModel> collectPays = this.listSuccessfulDepositCollectPayments(encounterId);
        if (collectPays.isEmpty()) {
            return false;
        }
        List<Long> paymentIds = new ArrayList<>();
        for (PaymentReconciliationQueryModel payment : collectPays) {
            paymentIds.add(payment.getId());
        }
        Map<Long, List<String>> channelCodesById =
            paymentChannelRefundOrchestrator.mapDistinctPayChannelCodesByPaymentIds(paymentIds);
        for (List<String> channelCodes : channelCodesById.values()) {
            if (paymentChannelRefundOrchestrator.mayRequireUnionPayBpcRefund(channelCodes)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 列出就诊下成功的预交金收款主单（HOSPITAL_DEPOSIT / PAY）。
     *
     * @param encounterId 就诊 ID
     * @return 收款列表；无则空表
     */
    private List<PaymentReconciliationQueryModel> listSuccessfulDepositCollectPayments(Long encounterId) {
        List<PaymentReconciliationQueryModel> result = new ArrayList<>();
        List<PaymentReconciliationQueryModel> allPayments =
            paymentReconciliationQueryService.findPaymentsByEncounter(encounterId);
        if (allPayments == null) {
            return result;
        }
        for (PaymentReconciliationQueryModel payment : allPayments) {
            if (payment == null || payment.getId() == null) {
                continue;
            }
            if (!PaymentStatus.SUCCESS.equals(payment.getStatusEnum())) {
                continue;
            }
            if (payment.getKindEnum() == null || !payment.getKindEnum().allowsEmptyChargeItemIds()) {
                continue;
            }
            if (!PaymentType.PAY.getCode().equals(payment.getPaymentEnum())) {
                continue;
            }
            result.add(payment);
        }
        return result;
    }

    /**
     * 将轧差计划回填预结算响应（P/O/C/应退/文案；自费与医保共用）。
     *
     * @param response    预结算响应
     * @param plan        轧差计划
     * @param encounterId 就诊 ID（多退现金计划不再依赖原收款拆分）
     */
    private void applyDepositSettlementPlanToPreSettleResponse(
        PreSettleInpatientChargesResponse response,
        InpatientDepositSettlementPlan plan,
        Long encounterId) {
        response.setPrepayNetBalance(plan.depositBalance());
        response.setPersonalCashPayable(plan.personalCashPayable());
        response.setDepositOffsetAmount(plan.depositOffsetAmount());
        response.setDisplayAmount(plan.channelPayable());
        response.setPrepayRefundAmount(plan.prepayRefundAmount());
        response.setPrepayFullRefund(plan.prepayFullRefund());
        response.setDepositSettleRuleCode(plan.ruleCode());
        boolean prepaySufficient = plan.depositBalance().compareTo(plan.personalCashPayable()) >= 0;
        response.setPrepaySufficient(prepaySufficient);
        // Asm：多退展示为单笔现金退；确认落账不再走原路拆分/Win
        if (plan.prepayRefundAmount().compareTo(BigDecimal.ZERO) > 0) {
            InpatientPrepayRefundPlanItemDto cashRefund = new InpatientPrepayRefundPlanItemDto();
            cashRefund.setAmount(plan.prepayRefundAmount());
            cashRefund.setPayChannelCode(PaymentChannel.CASH.getCode());
            cashRefund.setRequiresClientRefund(Boolean.FALSE);
            response.setPrepayRefundPlan(Collections.singletonList(cashRefund));
        } else {
            response.setPrepayRefundPlan(Collections.emptyList());
        }
        response.setPreviewText(this.buildDepositSettlementPreviewText(plan));
    }

    /**
     * 按轧差分支生成预结算预览文案（少补冲抵补收不变；多退改现金退）。
     *
     * @param plan 轧差计划
     * @return 预览文案
     */
    private String buildDepositSettlementPreviewText(InpatientDepositSettlementPlan plan) {
        if (InpatientDepositSettlementPlanner.RULE_R1.equals(plan.ruleCode())) {
            return "预交金冲抵 " + plan.depositOffsetAmount() + " 元，请再收 "
                + plan.channelPayable() + " 元";
        }
        if (InpatientDepositSettlementPlanner.RULE_R2.equals(plan.ruleCode())) {
            return "预交金刚好冲抵，无需收退；冲抵 " + plan.depositOffsetAmount() + " 元";
        }
        if (InpatientDepositSettlementPlanner.RULE_R3.equals(plan.ruleCode())) {
            return "预交金冲抵 " + plan.depositOffsetAmount() + " 元，退现金 "
                + plan.prepayRefundAmount() + " 元";
        }
        if (InpatientDepositSettlementPlanner.RULE_R5.equals(plan.ruleCode())) {
            return "无预交金，请收个人现金 " + plan.channelPayable() + " 元";
        }
        if (InpatientDepositSettlementPlanner.RULE_R6.equals(plan.ruleCode())) {
            return "个人应付为 0；退现金 " + plan.prepayRefundAmount() + " 元";
        }
        return "无需收退";
    }

    /**
     * 是否医保预结算后的住院结算草稿。
     *
     * @param paymentDraftId 支付草稿 ID
     * @return 存在 ACTIVE 医保预结算快照时为 true
     */
    private boolean isInsuranceSettlePaymentDraft(Long paymentDraftId) {
        return inpatientChargeInsuranceExtension.isEnabled()
            && paymentDraftId != null
            && !inpatientChargeInsuranceExtension.listActivePreSettlesByPaymentDraft(paymentDraftId).isEmpty();
    }

    /**
     * 自费预结算：按本批应收与库内预交金回填轧差计划（与医保同一套 R1～R7）。
     *
     * @param encounterId     就诊 ID
     * @param receivableTotal 本批应收合计（个人现金应付 P）
     * @param account         登记账户
     * @param response        预结算响应
     */
    private void applySelfPayChannelPayableOnPreSettle(
        Long encounterId,
        BigDecimal receivableTotal,
        Account account,
        PreSettleInpatientChargesResponse response) {
        BigDecimal accountBalance = BigDecimal.ZERO;
        if (account != null) {
            accountBalance = InpatientAccountBalanceDisplayAssembler.resolveAccountBalance(account);
        }
        InpatientDepositSettlementPlan plan = inpatientDepositSettlementPlanner.resolve(
            receivableTotal,
            accountBalance);
        this.applyDepositSettlementPlanToPreSettleResponse(response, plan, encounterId);
    }

    /**
     * 已结算队列按住院号去重（保留最近结算时间较新的一行）。
     *
     * @param rows 当前页队列行
     * @return 去重后列表（顺序保留首次出现位置）
     */
    private List<InpatientSettleQueueItemDto> dedupeSettledQueueItemsByEncounterBusNo(
        List<InpatientSettleQueueItemDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return rows;
        }
        LinkedHashMap<String, InpatientSettleQueueItemDto> byBusNo = new LinkedHashMap<>();
        for (InpatientSettleQueueItemDto row : rows) {
            if (row == null) {
                continue;
            }
            String key = this.resolveSettledQueueBusNoKey(row);
            InpatientSettleQueueItemDto existing = byBusNo.get(key);
            if (existing == null || this.shouldPreferSettledQueueRow(row, existing)) {
                byBusNo.put(key, row);
            }
        }
        return new ArrayList<>(byBusNo.values());
    }

    /**
     * 已结算队列去重键：优先住院号，缺省时回退就诊主键。
     */
    private String resolveSettledQueueBusNoKey(InpatientSettleQueueItemDto row) {
        if (row == null) {
            return "";
        }
        if (StringUtils.isNotBlank(row.getEncounterBusNo())) {
            return row.getEncounterBusNo().trim();
        }
        if (row.getEncounterId() != null) {
            return "id:" + row.getEncounterId();
        }
        return "";
    }

    /**
     * 同住院号多行时，保留最近结算时间较新者；时间相同则保留就诊 ID 较大者。
     */
    private boolean shouldPreferSettledQueueRow(
        InpatientSettleQueueItemDto candidate,
        InpatientSettleQueueItemDto incumbent) {
        LocalDateTime candidateTime = candidate.getLastSettleTime();
        LocalDateTime incumbentTime = incumbent.getLastSettleTime();
        if (candidateTime != null && incumbentTime != null) {
            int compared = candidateTime.compareTo(incumbentTime);
            if (compared != 0) {
                return compared > 0;
            }
        } else if (candidateTime != null) {
            return true;
        } else if (incumbentTime != null) {
            return false;
        }
        Long candidateId = candidate.getEncounterId();
        Long incumbentId = incumbent.getEncounterId();
        if (candidateId != null && incumbentId != null) {
            return candidateId.compareTo(incumbentId) > 0;
        }
        return candidateId != null;
    }
}
