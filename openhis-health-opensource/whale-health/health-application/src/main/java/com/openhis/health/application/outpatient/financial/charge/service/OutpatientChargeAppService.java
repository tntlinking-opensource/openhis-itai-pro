/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.charge.service;

import com.openhis.health.application.outpatient.financial.charge.dto.*;
import com.openhis.health.application.outpatient.financial.charge.mapper.OutpatientChargeMapper;
import com.openhis.health.application.outpatient.financial.charge.request.*;
import com.openhis.health.application.outpatient.financial.charge.response.*;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.application.shared.financial.PaymentChannelOptionProvider;
import com.openhis.health.application.shared.validator.PaymentChannelGate;
import com.openhis.health.application.shared.payment.ThirdPartyPaymentSettleOrchestrator;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.health.application.outpatient.financial.charge.support.ChargeReceiptPrintAssembler;
import com.openhis.health.application.outpatient.financial.charge.support.OutpatientChargeSettlementGate;
import com.openhis.health.application.outpatient.financial.charge.support.OutpatientPaymentDraftLifecycleOrchestrator;
import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.administration.chargeitem.command.BatchChargeItemIdsCommand;
import com.openhis.health.domain.administration.chargeitem.command.SwitchChargeItemAccountCommand;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientChargeQueueEncounterQuery;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.administration.encounter.entity.EncounterDiagnosis;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.application.outpatient.financial.charge.support.OutpatientChargeEncounterQueueLoader;
import com.openhis.health.domain.clinical.condition.queries.model.ConditionBatchItemQueryModel;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.financial.contract.queries.model.ContractDetailQueryModel;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.command.CreatePaymentDraftFromPreSettleCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.PaymentDetailItemCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.PaymentYbSettlementItemCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.SettleOutpatientPaymentCommand;
import com.openhis.health.domain.financial.paymentreconciliation.PaymentYbSettlementStatus;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentRecDetailQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationDomainService;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.medication.medicationdispense.command.PromoteDispenseToPreparationCommand;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseDomainService;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestDomainService;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseDomainService;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestDomainService;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestDomainService;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.infrastructure.integration.insurance.outpatient.orchestration.OutpatientChargeInsuranceCompensationOrchestration;
import com.openhis.health.infrastructure.integration.insurance.outpatient.OutpatientChargeInsuranceExtension;
import com.openhis.health.infrastructure.integration.financial.orchestration.InvoiceAfterPaymentOrchestration;
import com.openhis.health.infrastructure.integration.financial.orchestration.PaymentReconciliationSettleOrchestration;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargeConfirmExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargePreSettleExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargePreSettleListExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargePreSettleBatchExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargePreSettleBatchExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargeConfirmExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargePreSettleGroupExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargePreSettleExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargePreSettleQueryExtensionOut;
import com.openhis.health.infrastructure.integration.workinjury.WorkInjuryOutpatientSettlementExtension;
import com.openhis.health.infrastructure.integration.workinjury.ExtensionIn.WorkInjuryConfirmSettleExtensionIn;
import com.openhis.health.infrastructure.integration.workinjury.ExtensionIn.WorkInjuryPreSettleQueryExtensionIn;
import com.openhis.health.infrastructure.integration.workinjury.ExtensionIn.WorkInjurySettleExtensionIn;
import com.openhis.health.infrastructure.integration.workinjury.ExtensionOut.PrepareWorkInjuryInvokeExtensionOut;
import com.openhis.health.infrastructure.integration.workinjury.ExtensionOut.WorkInjuryConfirmSettleExtensionOut;
import com.openhis.health.infrastructure.integration.workinjury.ExtensionOut.WorkInjuryPreSettleQueryExtensionOut;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.OutpatientChargeQueueTab;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.ChargeTypeCategory;
import com.openhis.health.domain.share.enums.PaymentChannel;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.health.domain.share.enums.YbMedType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.health.domain.share.constants.OutpatientChargeConstants;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Collection;
import java.util.Collections;

/**
 * 门诊收费应用服务（AS-OP-FIN-002）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>Gate：做请求参数与业务前置门禁，失败立即中断，禁止进入领域写操作。</li>
 *   <li>Pre：聚合读服务/读模型查询，完成状态、归属、金额与重复提交校验。</li>
 *   <li>Cmd：调用领域写服务完成转账户、预结算、正式结算与开票草稿创建。</li>
 *   <li>Asm：通过 {@link OutpatientChargeMapper} 统一装配响应 DTO，避免编排层直接拼装输出对象。</li>
 * </ul>
 *
 * <p>本类不承载领域不变量定义，不直接访问 Repository；所有写入路径必须走 DomainService。</p>
 */
@Service
@RequiredArgsConstructor
public class OutpatientChargeAppService {

    private final CurrentPractitionerResolver currentPractitionerResolver;
    private final PageWindowGate pageWindowGate;
    private final PaymentChannelGate paymentChannelGate;
    private final PaymentChannelOptionProvider paymentChannelOptionProvider;
    private final CodeSystemQueryService codeSystemQueryService;
    private final EncounterQueryService encounterQueryService;
    private final EncounterContextReader EncounterContextReader;
    private final AccountQueryService accountQueryService;
    private final ContractQueryService contractQueryService;
    private final ChargeItemDomainService chargeItemDomainService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final OutpatientChargeEncounterQueueLoader outpatientChargeEncounterQueueLoader;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final PaymentReconciliationDomainService paymentReconciliationDomainService;
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final OutpatientChargeMapper outpatientChargeMapper;
    private final MedicationRequestDomainService medicationRequestDomainService;
    private final MedicationDispenseDomainService medicationDispenseDomainService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final DeviceRequestDomainService deviceRequestDomainService;
    private final DeviceDispenseDomainService deviceDispenseDomainService;
    private final DeviceDispenseQueryService deviceDispenseQueryService;
    private final ServiceRequestDomainService serviceRequestDomainService;
    private final OutpatientChargeInsuranceExtension chargeInsuranceExtension;

    private final WorkInjuryOutpatientSettlementExtension workInjurySettleExtension;

    private final PaymentReconciliationSettleOrchestration paymentReconciliationSettleOrchestration;
    private final InvoiceAfterPaymentOrchestration invoiceAfterPaymentOrchestration;

    private final OutpatientChargeInsuranceCompensationOrchestration outpatientChargeInsuranceCompensationOrchestration;

    private final ThirdPartyPaymentSettleOrchestrator thirdPartyPaymentSettleOrchestrator;

    private final ChargeReceiptPrintAssembler chargeReceiptPrintAssembler;
    private final OutpatientChargeSettlementGate outpatientChargeSettlementGate;
    private final OutpatientPaymentDraftLifecycleOrchestrator outpatientPaymentDraftLifecycleOrchestrator;

    /**
     * IF-OPC-01：加载收费页初始化数据。
     *
     * @param request 初始化请求（当前仅保留机构入参占位，不参与过滤）
     * @return 初始化响应，包含收费项状态下拉选项
     */
    public LoadChargePageInitResponse loadChargePageInit(LoadChargePageInitRequest request) {
        try {
            // Pre：初始化页签所需状态选项统一走术语服务。
            List<OptionDto> options = codeSystemQueryService.getOptionListByEnum(ChargeItemStatus.class);
            List<OptionDto> paymentChannels = paymentChannelOptionProvider.loadUserSelectableOptions();
            return outpatientChargeMapper.toLoadChargePageInitResponse(options, paymentChannels);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT,
                this.buildMessage(OutpatientChargeConstants.MESSAGE_CODE_INIT_LOAD_FAILED, "初始化数据不可用"));
        }
    }

    /**
     * IF-OPC-02：分页查询门诊收费队列。
     *
     * @param request 队列分页请求（关键字、日期区间、页签与分页参数）
     * @return 分页响应；records 会按页签二次过滤为全部/待收/已结
     * @throws BusinessException 参数缺失或非法时抛出
     */
    public PageOutpatientChargeQueueResponse pageOutpatientChargeQueue(PageOutpatientChargeQueueRequest request) {
        try {
            // Gate：分页参数与页签类型必须合法。
            this.validateQueueRequest(request);
            // Pre：按收费事实分页就诊并回补门诊信息读模型。
            OutpatientChargeQueueEncounterQuery queueEncounterQuery = new OutpatientChargeQueueEncounterQuery();
            PageQueryBinder.bind(request, queueEncounterQuery);
            queueEncounterQuery.setExcludedServiceTable(TableNameConstants.ADM_HEALTHCARE_SERVICE);
            Page<Long> encounterPage =
                this.outpatientChargeEncounterQueueLoader.pageEncounterIdsForOutpatientCashierQueue(queueEncounterQuery);
            List<OutpatientEncounterContextQueryModel> infos =
                EncounterContextReader.listOutpatientContextsByEncounterIdsOrdered(encounterPage.getContent());
            Page<OutpatientEncounterContextQueryModel> pageShell =
                new PageImpl<>(infos, encounterPage.getPageable(), encounterPage.getTotalElements());
            List<ChargeQueueRowDto> records = outpatientChargeMapper.toChargeQueueRowDtoList(infos, null);
            // 队列行挂载与本就诊收银口径一致的收费明细摘要，避免前端二次请求。
            this.attachCashierChargeItemBriefsToQueueRows(records);
            // BR-OPC-012：结合收银明细状态与 BillingStatement，避免「挂号已结、医嘱待收」误入已结算页签。
            this.applyQueueLabelCodes(records);
            this.attachLatestSuccessPaymentIdsToQueueRows(records);
            // 按页签过滤队列记录：全部页签透传，待收/已结按标签精确过滤
            List<ChargeQueueRowDto> filtered = new ArrayList<>();
            for (ChargeQueueRowDto record : records) {
                // ALL 直接透传，PENDING/SETTLED 做标签精确过滤。
                if (OutpatientChargeQueueTab.ALL.equals(request.getQueueTabCode())) {
                    filtered.add(record);
                    continue;
                }
                // 非全部页签时，仅保留与请求页签匹配的记录
                if (request.getQueueTabCode().equals(record.getQueueLabelCode())) {
                    filtered.add(record);
                }
            }
            // Asm：按页签过滤并封装分页响应。
            return this.toQueuePageResponse(pageShell, filtered);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT,
                this.buildMessage(OutpatientChargeConstants.MESSAGE_CODE_QUEUE_QUERY_FAILED, "队列查询失败"));
        }
    }

    /**
     * IF-OPC-03：加载就诊待收费明细。
     *
     * <p>列表不包含 {@code service_table} 为 {@link TableNameConstants#ADM_HEALTHCARE_SERVICE} 的明细（挂号费等），
     * 且仅包含 {@link ChargeItemStatus#PLANNED}（待收费）与 {@link ChargeItemStatus#SETTLED}（已结算）行，不包含草稿；
     * 条件在 JPA 规格/仓储中下推。</p>
     *
     * @param request 明细查询请求（就诊主键 + 是否带价扩展）
     * @return 明细响应，items 为当前就诊的收费项列表
     * @throws BusinessException 就诊参数非法或就诊不存在时抛出
     */
    public LoadPendingChargeItemsResponse loadPendingChargeItems(LoadPendingChargeItemsRequest request) {
        try {
            // Gate：就诊主键须非空且就诊须存在。
            this.validateEncounterId(request.getEncounterId());
            encounterQueryService.mustGetEncounter(request.getEncounterId());
            // Pre：加载就诊下待收费/已结算明细（排除挂号锚点）。
            ChargeItemQueryCommand queryCommand = new ChargeItemQueryCommand();
            queryCommand.setEncounterId(request.getEncounterId());
            this.applyOutpatientCashierChargeItemQueryFilters(queryCommand);
            List<ChargeItem> rows;
            if (Boolean.TRUE.equals(request.getIncludePriceExtension())) {
                rows = chargeItemQueryService.queryCostDetails(queryCommand);
            } else {
                rows = chargeItemQueryService.listChargeItemsByEncounterId(queryCommand);
            }
            LoadPendingChargeItemsResponse response = new LoadPendingChargeItemsResponse();
            response.setEncounterId(request.getEncounterId());
            // Pre：双账户门禁标志供前端禁用转医保/转自费（纯自费挂号不可互转）。
            response.setAccountSwitchEnabled(accountQueryService.hasDualChargeAccounts(request.getEncounterId()));
            // Asm：映射明细行并返回。
            response.setItems(this.toPendingRows(rows, Boolean.TRUE.equals(request.getIncludePriceExtension())));
            // Asm：已结算支付分摊列表 + 当前选中摘要（多笔时可切换）。
            List<ChargeSettlementInsuranceSummaryDto> settlementOptions =
                chargeReceiptPrintAssembler.listSettlementInsuranceSummaries(request.getEncounterId());
            response.setSettlementInsuranceOptions(settlementOptions);
            response.setSettlementInsuranceSummary(
                this.resolveSelectedSettlementInsuranceSummary(settlementOptions, request.getPaymentId()));
            return response;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT,
                this.buildMessage(OutpatientChargeConstants.MESSAGE_CODE_PENDING_ITEMS_LOAD_FAILED, "明细加载失败或就诊无行"));
        }
    }

    /**
     * 从多笔成功支付摘要中解析当前展示项：优先请求指定 paymentId，否则取列表首条（最新）。
     *
     * @param options           已按时间倒序的摘要列表
     * @param requestedPaymentId 前端/队列指定的支付主键；可空
     * @return 选中摘要；无选项时 null
     */
    private ChargeSettlementInsuranceSummaryDto resolveSelectedSettlementInsuranceSummary(
        List<ChargeSettlementInsuranceSummaryDto> options,
        Long requestedPaymentId) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        if (requestedPaymentId != null) {
            for (ChargeSettlementInsuranceSummaryDto option : options) {
                if (option != null && requestedPaymentId.equals(option.getPaymentId())) {
                    return option;
                }
            }
        }
        return options.get(0);
    }

    /**
     * IF-OPC-04：将就诊收费项批量切换到医保账户。
     *
     * @param request 转医保请求
     * @return 切换结果响应
     */
    public SwitchEncounterChargeItemsAccountResponse switchEncounterChargeItemsToInsurance(
        SwitchEncounterChargeItemsToInsuranceRequest request) {
        // Cmd：批量切换就诊收费项至医保账户。
        return this.switchAccount(request.getEncounterId(), true);
    }

    /**
     * IF-OPC-05：将就诊收费项批量切换到自费账户。
     *
     * @param request 转自费请求
     * @return 切换结果响应
     */
    public SwitchEncounterChargeItemsAccountResponse switchEncounterChargeItemsToSelfPay(
        SwitchEncounterChargeItemsToSelfPayRequest request) {
        // Cmd：批量切换就诊收费项至自费账户。
        return this.switchAccount(request.getEncounterId(), false);
    }

    /**
     * IF-OPC-06：门诊预结算。
     *
     * <p>执行顺序：参数门禁 → 就诊与收费项加载及归属校验 → 诊断事实门禁 → 可选合同锚点
     * → 收费项预结算快照落库 → 装配支付草稿命令（含按账户汇总的占位支付明细）
     * → 支付草稿头与从表明细落库 → 预结算预览装配返回。</p>
     *
     * <p>占位支付明细：{@link PaymentReconciliationDomainService#createPaymentDraftFromPreSettle} 要求
     * {@code CreatePaymentDraftFromPreSettleCommand#paymentDetails} 非空，否则从表无行，后续正式结算
     *（IF-OPC-07）在加载草稿明细时将触发 {@code EX-OPC-402}。占位行渠道为收银台口径、结果为草稿态，
     * 正式结算成功时由渠道回执明细整单替换。</p>
     *
     * @param request 预结算请求
     * @return 预结算响应，含支付草稿主键与预览金额
     * @throws BusinessException 任一步前置校验失败时抛出；无法解析结算账户时抛出（语义码 {@code EX-OPC-308}）
     */
    public PreSettleOutpatientChargesResponse preSettleOutpatientCharges(PreSettleOutpatientChargesRequest request) {
        // Gate：预结算请求参数必须完整且合法。
        this.validatePreSettleRequest(request);
        // Pre：预加载就诊详情，确保就诊存在且有效。
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounter(request.getEncounterId());
        // 批量加载收费项明细，用于后续校验与金额汇总
        ChargeItemQueryCommand queryCommand = new ChargeItemQueryCommand();
        queryCommand.setIds(request.getChargeItemIds());
        List<ChargeItem> chargeItems = chargeItemQueryService.loadChargeItemsByIds(queryCommand);
        // 校验收费项全部命中：任一收费项缺失则阻断预结算
        this.assertChargeItemsLoaded(
            request.getChargeItemIds(),
            chargeItems,
            OutpatientChargeConstants.MESSAGE_CODE_PRESETTLE_NO_CHARGE_ITEMS);
        // 校验收费项归属：所有收费项必须属于当前就诊，防止跨就诊错收
        this.assertChargeItemsBelongEncounter(
            chargeItems,
            request.getEncounterId(),
            OutpatientChargeConstants.MESSAGE_CODE_PRESETTLE_DIAGNOSIS_MISMATCH);
        // 预结算前校验就诊下存在就诊诊断行，避免无诊断直接进入收费流程（不再查 cln_condition）。
        if (encounterQueryService.listEncounterDiagnoses(request.getEncounterId()).isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.CONDITION_RULE_CONFLICT,
                this.buildMessage(OutpatientChargeConstants.MESSAGE_CODE_PRESETTLE_DIAGNOSIS_MISSING, "当前就诊缺少诊断信息，不能预结算"));
        }
        // 若收费项绑定了 encounter_diagnosis_ids，则进一步校验就诊诊断主键集合必须可被批量查询命中。
        List<Long> boundEncounterDiagnosisIds = this.extractEncounterDiagnosisIds(chargeItems);
        if (!boundEncounterDiagnosisIds.isEmpty()) {
            List<EncounterDiagnosis> diagnoses =
                encounterQueryService.listEncounterDiagnosisEntitiesByIds(boundEncounterDiagnosisIds);
            if (diagnoses.size() != boundEncounterDiagnosisIds.size()) {
                throw new BusinessException(
                    ErrorCodeConstants.CONDITION_RULE_CONFLICT,
                    this.buildMessage(OutpatientChargeConstants.MESSAGE_CODE_PRESETTLE_DIAGNOSIS_MISMATCH, "收费项关联诊断数据不完整"));
            }
        }
        ContractDetailQueryModel contract = null;
        if (request.getContractId() != null) {
            contract = contractQueryService.findContractById(request.getContractId());
            // 显式传入合同主键但未命中主数据时，直接阻断预结算，避免生成无法追溯合同号的支付草稿。
            if (contract == null) {
                throw new BusinessException(
                    ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                    this.buildMessage(OutpatientChargeConstants.MESSAGE_CODE_PRESETTLE_CONTRACT_NOT_FOUND, "费用性质合同不存在"));
            }
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT,
            "当前会话操作人不能为空");
        BatchChargeItemIdsCommand preSettleCommand =
            outpatientChargeMapper.toBatchChargeItemIdsCommand(request.getChargeItemIds(), operatorId);
        // 预结算前先作废同就诊遗留草稿，避免多次预结算残留 DRAFT 使 BR-OPC-012 误判为待收费。
        this.outpatientPaymentDraftLifecycleOrchestrator.cancelAllDraftsForEncounter(request.getEncounterId(), null);
        // 先落收费项预结算快照，再创建支付草稿，保证“费用事实在前、支付草稿在后”的时序。
        chargeItemDomainService.preSettleChargeItems(preSettleCommand);
        BigDecimal totalAmount = this.sumChargeItemAmount(chargeItems);
        // BR-OPC-003：请求未显式传 contractId 时，从收费项账户聚合 contract_no 写入支付草稿（对齐住院结算写侧）。
        String contractNo = this.resolvePaymentContractBusNoForPreSettle(contract, chargeItems, request.getEncounterId());
        // 先装配支付头命令；明细不入 Mapper，避免与「按收费项事实汇总账户金额」的编排职责混放。
        CreatePaymentDraftFromPreSettleCommand paymentDraftCommand = outpatientChargeMapper.toCreatePaymentDraftFromPreSettleCommand(
            request.getEncounterId(),
            encounter.getPatientId(),
            this.joinIds(request.getChargeItemIds()),
            totalAmount,
            operatorId,
            contractNo);
        // 领域 replaceDetails 对空列表执行「删尽不插」，故此处必须给出至少一行占位明细，否则 IF-OPC-07 读明细为空报 EX-OPC-402。
        paymentDraftCommand.setPaymentDetails(this.buildPreSettlePaymentDetailDraftLines(request.getEncounterId(), chargeItems));
        // 主单从单行级与预结算金额对齐；正式结算时 SettleOutpatientPaymentCommand 携带的回执可覆盖替换明细。
        Long paymentDraftId = paymentReconciliationDomainService.createPaymentDraftFromPreSettle(paymentDraftCommand);
        // 预览文案与 IF-OPC-03 待收费列表同口径：批量加载定价与目录，避免收银端与明细页名称不一致。
        Map<Long, ChargeItemDefinition> definitionMap = this.buildChargeDefinitionMapByDefinitionId(chargeItems);
        Map<String, ProductDefinitionDisplayDto> productMap = this.buildProductDefinitionMapByChargeItems(chargeItems, definitionMap);
        LinkedHashSet<Long> previewAccountIds = new LinkedHashSet<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem.getAccountId() != null) {
                previewAccountIds.add(chargeItem.getAccountId());
            }
        }
        Map<Long, Account> accountByIdForPreview = accountQueryService.mapAccountsByIds(previewAccountIds);
        LinkedHashSet<String> previewContractBusNos = new LinkedHashSet<>();
        for (Account account : accountByIdForPreview.values()) {
            if (account != null && StringUtils.isNotBlank(account.getContractNo())) {
                previewContractBusNos.add(account.getContractNo().trim());
            }
        }
        // 费用性质展示与 IF-OPC-03 待收费列表同口径：按账户 contract_no 批量解析合同名称。
        Map<String, String> contractNameByBusNoForPreview =
            contractQueryService.mapContractNameByBusNoIn(previewContractBusNos);
        List<PreSettleChargeLinePreviewDto> previews = new ArrayList<>();
        for (ChargeItem chargeItem : chargeItems) {
            PreSettleChargeLinePreviewDto preview = new PreSettleChargeLinePreviewDto();
            preview.setChargeItemId(chargeItem.getId());
            preview.setItemLabel(this.resolveOutpatientChargeLineDisplayLabel(chargeItem, definitionMap, productMap));
            preview.setLineAmount(chargeItem.getTotalPrice());
            Account previewAccount = accountByIdForPreview.get(chargeItem.getAccountId());
            if (previewAccount != null && StringUtils.isNotBlank(previewAccount.getContractNo())) {
                String contractBusNo = previewAccount.getContractNo().trim();
                String feeNature = contractNameByBusNoForPreview.get(contractBusNo);
                preview.setFeeNatureDisplay(feeNature);
            }
            previews.add(preview);
        }
        PreSettleOutpatientChargesResponse response =
            outpatientChargeMapper.toPreSettleOutpatientChargesResponse(paymentDraftId, totalAmount, previews);
        this.applyInsurancePreSettleIfNeeded(request, paymentDraftId, response, chargeItems);
        // TODO(ADR-CMP-001 D5/D12)：若 request.gfChargeMode 非空，可选注入 CampusChargeCalcReader#previewSplit，
        // 将公费/自付写入支付明细两行（PUBLIC_EXPENSE_ACCOUNT + PERSONAL_CASH_ACCOUNT）；不选 mode 则跳过。
        // 本迭代仅预留字段与注释，不改草稿金额。
        // Asm：返回预结算响应（草稿 ID、预览金额与医保分组）。
        return response;
    }

    /**
     * IF-OPC-07：门诊正式结算。
     *
     * <p>执行顺序：草稿头加载 → 草稿态门禁 → 草稿从表明细加载与空表门禁 → 收费项集合与草稿绑定比对
     * → 与历史成功/退款中流水挂载收费项求交（BR-OPC-005）→ 收费项加载与未结算门禁 → 草稿明细金额与收费项总额交叉校验
     * → 请求渠道金额与收费项总额对齐 → 支付成功落账 → 收费项结算 → 发票草稿创建 → 响应装配。</p>
     *
     * <p>草稿从表明细须在 IF-OPC-06 预结算阶段随主单写入；若明细为空则业务码 {@code EX-OPC-402}，
     * 提示重新预结算以修复主从不一致。</p>
     *
     * @param request 正式结算请求
     * @return 结算成功响应（支付主键、票据主键）
     * @throws BusinessException 任何校验失败或写入失败时抛出
     */
    public ConfirmOutpatientChargeSettlementResponse confirmOutpatientChargeSettlement(
        ConfirmOutpatientChargeSettlementRequest request) {
        // Gate：正式结算请求参数须完整。
        this.validateConfirmSettleRequest(request);
        // Pre：加载支付草稿并校验草稿态与就诊一致。
        PaymentReconciliationQueryModel payment = paymentReconciliationQueryService.mustGetPayment(request.getPaymentDraftId());
        // Gate：支付草稿就诊锚点必须与本次提交一致，防止跨就诊错结算。
        if (!request.getEncounterId().equals(payment.getEncounterId())) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT,
                this.buildMessage(OutpatientChargeConstants.MESSAGE_CODE_SETTLE_DRAFT_INVALID, "支付草稿与就诊不一致"));
        }
        // 正式结算仅允许消费草稿态支付单，避免重复结算已成功/已取消流水。
        if (!PaymentStatus.DRAFT.equals(payment.getStatusEnum())) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT,
                this.buildMessage(OutpatientChargeConstants.MESSAGE_CODE_SETTLE_DRAFT_INVALID, "支付草稿状态非法，不能正式结算"));
        }
        List<PaymentRecDetailQueryModel> details =
            paymentReconciliationQueryService.listPaymentRecDetailsByPaymentId(request.getPaymentDraftId());
        // 无明细则无法与收费项总额做草稿侧交叉校验，亦不符合支付主从数据完整性；常见成因是预结算未写入占位明细或历史脏草稿。
        if (details.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                this.buildMessage(OutpatientChargeConstants.MESSAGE_CODE_SETTLE_DRAFT_DETAIL_EMPTY, "支付草稿明细为空"));
        }
        // 草稿绑定收费项集合必须与本次提交完全一致，防止“换单结算”。
        if (!this.sameChargeItemSet(payment.getChargeItemIds(), request.getChargeItemIds())) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT,
                this.buildMessage(OutpatientChargeConstants.MESSAGE_CODE_SETTLE_CHARGE_ITEMS_MISMATCH, "收费项与草稿不一致"));
        }
        // 与 BR-OPC-005 一致：仅当历史成功/退款中流水已挂载本次拟收收费项之一时才阻断，支持同就诊增量第二批结算。
        this.assertNotAlreadySettled(request.getEncounterId(), request.getChargeItemIds());
        // 重新加载收费项：校验后与草稿比对确保一致性
        ChargeItemQueryCommand chargeItemQueryCommand = new ChargeItemQueryCommand();
        chargeItemQueryCommand.setIds(request.getChargeItemIds());
        List<ChargeItem> chargeItems = chargeItemQueryService.loadChargeItemsByIds(chargeItemQueryCommand);
        // 二次校验收费项存在性、归属与状态
        this.assertChargeItemsLoaded(
            request.getChargeItemIds(),
            chargeItems,
            OutpatientChargeConstants.MESSAGE_CODE_SETTLE_CHARGE_ITEMS_MISMATCH);
        this.assertChargeItemsBelongEncounter(
            chargeItems,
            request.getEncounterId(),
            OutpatientChargeConstants.MESSAGE_CODE_SETTLE_CHARGE_ITEMS_MISMATCH);
        // 校验收费项未结算：已结算或已退款的收费项不能再次结算
        this.assertNotSettledChargeItems(chargeItems);
        // Gate（GAP-STALE-001）：须 PLANNED 且锚点医嘱/发放仍可收费；须在支付落账前执行。
        this.outpatientChargeSettlementGate.assertChargeItemsSettleable(chargeItems);
        // 计算收费项总金额作为预期金额基准；医保预结算后以草稿 displayAmount 为准。
        BigDecimal expected = this.resolveExpectedPayableAmount(payment, chargeItems);
        // 支付草稿明细金额必须与当前应付口径一致，防止历史脏草稿在正式结算阶段穿透。
        this.assertDraftDetailAmountMatchesExpected(details, expected);
        BigDecimal actual = this.sumSubmitAmount(request);
        if (expected.compareTo(actual) != 0) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT,
                this.buildMessage(OutpatientChargeConstants.MESSAGE_CODE_SETTLE_AMOUNT_MISMATCH, "支付金额与预结算不一致"));
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT,
            "当前会话操作人不能为空");
        // 兼容历史脏草稿：正式结算前若 payment.contract_no 仍为空，从收费项账户补写（IF-OPC-06 写侧口径）。
        if (StringUtils.isBlank(payment.getContractNo())) {
            String contractBusNo =
                this.resolvePaymentContractBusNoForPreSettle(null, chargeItems, request.getEncounterId());
            paymentReconciliationDomainService.backfillPaymentContractNoIfBlank(
                request.getPaymentDraftId(),
                contractBusNo);
        }
        // Gate：扫码渠道致命校验须在 2207 之前，避免平台已扣款后因缺 clientScanResult 本地回滚掉单。
        this.assertScanPayReadyBeforeInsuranceConfirm(request);
        List<OutpatientChargeConfirmExtensionOut> ybConfirmResults = List.of();
        try {
            ybConfirmResults = this.collectThirdPartyConfirmResults(request, chargeItems);
            boolean workInjurySettle = this.isWorkInjuryChargeSettlement(request, chargeItems);
            SettleOutpatientPaymentCommand settleCommand = outpatientChargeMapper.toSettleOutpatientPaymentCommand(request);
            settleCommand.setYbSettlements(this.buildYbSettlementItems(ybConfirmResults));
            this.mergeInsurancePaymentDetailsIntoSettleCommand(
                settleCommand,
                ybConfirmResults,
                details,
                request.getBillDate(),
                request.getOutcomeEnum());
            this.attachDraftAccountsToPayReceiptDetails(settleCommand.getPayReceiptDetails(), details);
            thirdPartyPaymentSettleOrchestrator.applyScanBeforeSettle(
                request.getPaymentDraftId(),
                settleCommand.getPayReceiptDetails(),
                request.getClientScanResult(),
                operatorId,
                null);
            Long paymentId = paymentReconciliationSettleOrchestration.settleOutpatientPayment(settleCommand);
            if (workInjurySettle) {
                this.bindWorkInjuryPaymentAfterLocalSettle(request, paymentId, ybConfirmResults);
            }
            BatchChargeItemIdsCommand batchChargeItemIdsCommand =
                outpatientChargeMapper.toBatchChargeItemIdsCommand(request.getChargeItemIds(), operatorId);
            chargeItemDomainService.settleChargeItems(batchChargeItemIdsCommand);
            // 正式结算后清理同就诊其它草稿支付单，防止队列标签仍命中陈旧 DRAFT。
            this.outpatientPaymentDraftLifecycleOrchestrator.cancelAllDraftsForEncounter(request.getEncounterId(), null);
            // 收费项已结算：按锚点推进关联医嘱/申请及发放草稿，患者方可进入发药/执行后续环节。
            this.syncAdviceStatesAfterChargeSettlement(chargeItems, operatorId);
            // Cmd：票面按收费项合计开票（含医保已付）；现金 displayAmount=0 仍开票（BR-INV-008）
            BigDecimal invoiceFaceAmount = MonetaryAmountSupport.scaleSettle(this.sumChargeItemAmount(chargeItems));
            Long invoiceId = invoiceAfterPaymentOrchestration.createDraftAndScheduleIssueIfPayable(
                outpatientChargeMapper.toCreateDraftInvoiceCommand(payment, paymentId, operatorId, invoiceFaceAmount));
            // Asm：装配正式结算成功响应（支付主键、票据主键）。
            return outpatientChargeMapper.toConfirmOutpatientChargeSettlementResponse(paymentId, invoiceId);
        } catch (RuntimeException ex) {
            // 2207 已成功但本地落账失败：同事务内冲正平台结算，避免「医保已收、HIS 待收费」。
            if (!ybConfirmResults.isEmpty()) {
                try {
                    outpatientChargeInsuranceCompensationOrchestration.compensateSettlementsByPayment(
                        request.getPaymentDraftId());
                } catch (RuntimeException compensationEx) {
                    compensationEx.addSuppressed(ex);
                    throw compensationEx;
                }
            }
            throw ex;
        }
    }

    /**
     * 运维：按支付草稿冲正已落库医保正式结算（2208，失败则 2601）。
     *
     * <p>用于孤儿 2207（平台已扣款、本地回执曾回滚后补回）退个账；正常收费/退费流程勿调用。</p>
     *
     * @param paymentDraftId 支付草稿主键（与 yb_settlement_record.payment_id 一致）
     */
    public void compensateInsuranceSettlementsByPayment(Long paymentDraftId) {
        if (paymentDraftId == null) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                "paymentDraftId 不能为空");
        }
        outpatientChargeInsuranceCompensationOrchestration.compensateSettlementsByPayment(paymentDraftId);
    }

    /**
     * 打印/补打门诊收费小票。
     *
     * @param request 打印请求（encounterId 与 paymentId 至少传一个）
     * @return 小票数据
     */
    public PrintChargeReceiptResponse printChargeReceipt(PrintChargeReceiptRequest request) {
        if (request.getEncounterId() == null && request.getPaymentId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                "encounterId 与 paymentId 至少传一个");
        }
        Long encounterId = request.getEncounterId();
        if (encounterId == null) {
            PaymentReconciliationQueryModel payment =
                paymentReconciliationQueryService.mustGetPayment(request.getPaymentId());
            encounterId = payment.getEncounterId();
        }
        if (encounterId == null) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_NOT_FOUND,
                "未找到就诊信息");
        }
        ChargeReceiptSlipDto slip = new ChargeReceiptSlipDto();
        chargeReceiptPrintAssembler.enrichPrintFields(slip, encounterId, request.getPaymentId());
        return new PrintChargeReceiptResponse().setSlip(slip);
    }

    /**
     * 执行收费项账户切换（医保/自费共用实现）。
     *
     * @param encounterId 就诊主键
     * @param insurance true=转医保；false=转自费
     * @return 切换响应（成功或失败语义码）
     */
    private SwitchEncounterChargeItemsAccountResponse switchAccount(Long encounterId, boolean insurance) {
        // Gate：就诊主键须合法且就诊存在，防止跨就诊误切账户
        this.validateEncounterId(encounterId);
        encounterQueryService.mustGetEncounter(encounterId);
        // Gate：须医保挂号双账户并存；纯自费挂号仅单一账户，禁止互转
        if (!accountQueryService.hasDualChargeAccounts(encounterId)) {
            return outpatientChargeMapper.toSwitchAccountResponse(
                false, OutpatientChargeConstants.MESSAGE_CODE_SWITCH_DUAL_ACCOUNT_REQUIRED);
        }
        // Pre：加载与 IF-OPC-03 同口径的收费明细（PLANNED/SETTLED、排除挂号锚点）
        ChargeItemQueryCommand queryCommand = new ChargeItemQueryCommand();
        queryCommand.setEncounterId(encounterId);
        this.applyOutpatientCashierChargeItemQueryFilters(queryCommand);
        List<ChargeItem> rows = chargeItemQueryService.listChargeItemsByEncounterId(queryCommand);
        List<ChargeItem> switchableRows = this.filterSwitchableChargeItems(rows);
        // 已结算/已退款行不可再切账户，无待切换行则拒绝
        if (switchableRows.isEmpty()) {
            return outpatientChargeMapper.toSwitchAccountResponse(false, OutpatientChargeConstants.MESSAGE_CODE_SWITCH_FAILED);
        }
        Long targetAccountId;
        // Pre：解析目标记账账户（医保挂号双账户场景下取默认医保或自费账户）
        if (insurance) {
            targetAccountId = accountQueryService.getMedicalInsuranceAccount(encounterId);
        } else {
            targetAccountId = accountQueryService.getSelfPayAccount(encounterId);
        }
        if (targetAccountId == null) {
            return outpatientChargeMapper.toSwitchAccountResponse(false, OutpatientChargeConstants.MESSAGE_CODE_SWITCH_ACCOUNT_NOT_FOUND);
        }
        // Pre：仅保留 accountId 与目标不一致的行，已归属目标账户则幂等成功
        List<ChargeItem> rowsToSwitch = this.collectRowsNeedingTargetAccount(switchableRows, targetAccountId);
        if (rowsToSwitch.isEmpty()) {
            return outpatientChargeMapper.toSwitchAccountResponse(true, OutpatientChargeConstants.MESSAGE_CODE_SWITCH_SUCCESS);
        }
        // Cmd：批量更新 adm_charge_item.account_id，使医保账单可改走自费账户结账
        SwitchChargeItemAccountCommand command = new SwitchChargeItemAccountCommand();
        command.setOperatorId(currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT,
            "当前会话操作人不能为空"));
        List<SwitchChargeItemAccountCommand.Item> items = new ArrayList<>();
        for (ChargeItem row : rowsToSwitch) {
            SwitchChargeItemAccountCommand.Item item = new SwitchChargeItemAccountCommand.Item();
            item.setChargeItemId(row.getId());
            item.setAccountId(targetAccountId);
            items.add(item);
        }
        command.setItems(items);
        chargeItemDomainService.switchChargeItemAccount(command);
        // Asm：返回成功语义码，前端刷新明细后费用性质列应展示为自费/医保
        return outpatientChargeMapper.toSwitchAccountResponse(true, OutpatientChargeConstants.MESSAGE_CODE_SWITCH_SUCCESS);
    }

    /**
     * 门诊收银 IF-OPC-03 / 04 / 05 共用：排除挂号锚点表，且仅保留待收费/已结算（不含草稿）。
     *
     * @param cmd 查询命令（调用前须已设置 encounterId）
     */
    private void applyOutpatientCashierChargeItemQueryFilters(ChargeItemQueryCommand cmd) {
        cmd.setExcludedServiceTable(TableNameConstants.ADM_HEALTHCARE_SERVICE);
        cmd.setStatusEnumIn(this.chargeItemQueryService.outpatientCashierBillableStatusCodes());
        cmd.setExcludeSupersededSettledLines(true);
    }

    /**
     * 为队列行写入最近一次成功支付流水（补打收费小票）。
     *
     * @param rows 队列行
     */
    private void attachLatestSuccessPaymentIdsToQueueRows(List<ChargeQueueRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        for (ChargeQueueRowDto row : rows) {
            if (row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
        }
        if (encounterIds.isEmpty()) {
            return;
        }
        Map<Long, Long> paymentByEncounter =
            paymentReconciliationQueryService.findLatestSuccessPaymentIdsByEncounterIds(encounterIds);
        for (ChargeQueueRowDto row : rows) {
            Long paymentId = paymentByEncounter.get(row.getEncounterId());
            if (paymentId != null) {
                row.setPaymentId(paymentId);
            }
        }
    }

    /**
     * 为队列行批量挂载与 IF-OPC-03 同口径的收费明细摘要（单次批量加载，满足 A.9.1）。
     *
     * @param rows 队列行（须已填充 encounterId）
     */
    private void attachCashierChargeItemBriefsToQueueRows(List<ChargeQueueRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        for (ChargeQueueRowDto row : rows) {
            if (row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
        }
        if (encounterIds.isEmpty()) {
            return;
        }
        List<ChargeItem> lines = this.chargeItemQueryService.listOutpatientCashierChargeItemsForEncounters(
            encounterIds,
            TableNameConstants.ADM_HEALTHCARE_SERVICE);
        Map<Long, List<ChargeItem>> byEncounter = new LinkedHashMap<>();
        for (ChargeItem line : lines) {
            Long encounterKey = line.getEncounterId();
            List<ChargeItem> bucket = byEncounter.computeIfAbsent(encounterKey, key -> new ArrayList<>());
            bucket.add(line);
        }
        for (List<ChargeItem> bucket : byEncounter.values()) {
            bucket.sort((left, right) -> {
                if (left.getOccurrenceTime() == null || right.getOccurrenceTime() == null) {
                    return 0;
                }
                return right.getOccurrenceTime().compareTo(left.getOccurrenceTime());
            });
        }
        Map<Long, ChargeItemDefinition> definitionMap = this.buildChargeDefinitionMapByDefinitionId(lines);
        Map<String, ProductDefinitionDisplayDto> productMap = this.buildProductDefinitionMapByChargeItems(lines, definitionMap);
        for (ChargeQueueRowDto row : rows) {
            List<ChargeItem> bucket = byEncounter.get(row.getEncounterId());
            if (bucket == null || bucket.isEmpty()) {
                row.setCashierChargeItems(Collections.emptyList());
                row.setCashierChargeRollupStatusCode(null);
                row.setCashierChargeRollupStatusText(null);
                continue;
            }
            List<ChargeQueueChargeItemBriefDto> briefs = new ArrayList<>(bucket.size());
            for (ChargeItem chargeLine : bucket) {
                ChargeQueueChargeItemBriefDto brief = new ChargeQueueChargeItemBriefDto();
                brief.setChargeItemId(chargeLine.getId());
                brief.setStatusCode(chargeLine.getStatusEnum());
                ChargeItemStatus statusEnum = EnumUtil.fromCode(ChargeItemStatus.class, chargeLine.getStatusEnum());
                if (statusEnum != null) {
                    brief.setStatusText(statusEnum.getDisplay());
                } else {
                    brief.setStatusText(chargeLine.getStatusEnum());
                }
                brief.setTotalPrice(chargeLine.getTotalPrice());
                brief.setItemLabel(this.resolveOutpatientChargeLineDisplayLabel(chargeLine, definitionMap, productMap));
                briefs.add(brief);
            }
            row.setCashierChargeItems(briefs);
            this.applyCashierChargeRollupStatus(row, bucket);
        }
    }

    /**
     * 按本就诊收银明细行的 {@link ChargeItemStatus} 汇总队列展示用收费状态（与明细 brief 同源）。
     *
     * @param row    队列行（写入汇总编码与展示文案）
     * @param bucket 同一就诊下的收费明细集合（可能为空）
     */
    private void applyCashierChargeRollupStatus(ChargeQueueRowDto row, List<ChargeItem> bucket) {
        if (bucket == null || bucket.isEmpty()) {
            row.setCashierChargeRollupStatusCode(null);
            row.setCashierChargeRollupStatusText(null);
            return;
        }
        boolean anyPlanned = false;
        boolean allSettled = true;
        for (ChargeItem line : bucket) {
            ChargeItemStatus st = EnumUtil.fromCode(ChargeItemStatus.class, line.getStatusEnum());
            if (ChargeItemStatus.PLANNED.equals(st)) {
                anyPlanned = true;
            }
            if (!ChargeItemStatus.SETTLED.equals(st)) {
                allSettled = false;
            }
        }
        if (anyPlanned) {
            row.setCashierChargeRollupStatusCode(ChargeItemStatus.PLANNED.getCode());
            row.setCashierChargeRollupStatusText(ChargeItemStatus.PLANNED.getDisplay());
        } else if (allSettled) {
            row.setCashierChargeRollupStatusCode(ChargeItemStatus.SETTLED.getCode());
            row.setCashierChargeRollupStatusText(ChargeItemStatus.SETTLED.getDisplay());
        } else {
            row.setCashierChargeRollupStatusCode(OutpatientChargeConstants.CASHIER_CHARGE_ROLLUP_MIXED_STATUS_CODE);
            row.setCashierChargeRollupStatusText(OutpatientChargeConstants.CASHIER_CHARGE_ROLLUP_MIXED_STATUS_TEXT);
        }
    }

    /**
     * 按 BR-OPC-012 为队列行写入页签标签：存在待收费/混合收银明细，或存在草稿/退款中账单 → 待收费；否则已结算。
     *
     * @param records 已挂载 {@link #attachCashierChargeItemBriefsToQueueRows} 的队列行
     */
    private void applyQueueLabelCodes(List<ChargeQueueRowDto> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        for (ChargeQueueRowDto row : records) {
            if (row.getEncounterId() == null) {
                continue;
            }
            String rollup = row.getCashierChargeRollupStatusCode();
            if (ChargeItemStatus.PLANNED.getCode().equals(rollup)
                || OutpatientChargeConstants.CASHIER_CHARGE_ROLLUP_MIXED_STATUS_CODE.equals(rollup)) {
                row.setQueueLabelCode(OutpatientChargeQueueTab.PENDING);
                continue;
            }
            List<PaymentReconciliationQueryModel> statements =
                paymentReconciliationQueryService.findPaymentsByEncounter(row.getEncounterId());
            if (statements == null || statements.isEmpty()) {
                row.setQueueLabelCode(
                    ChargeItemStatus.SETTLED.getCode().equals(rollup)
                        ? OutpatientChargeQueueTab.SETTLED
                        : OutpatientChargeQueueTab.PENDING);
                continue;
            }
            if (this.hasUnsettledBillingStatement(statements)) {
                row.setQueueLabelCode(OutpatientChargeQueueTab.PENDING);
            } else {
                row.setQueueLabelCode(OutpatientChargeQueueTab.SETTLED);
            }
        }
    }

    /**
     * 将领域收费项映射为应用层待收费 DTO。
     *
     * @param rows 领域收费项列表
     * @return 应用层待收费行列表
     */
    private List<PendingChargeItemRowDto> toPendingRows(List<ChargeItem> rows, boolean includePriceExtension) {
        Map<Long, ChargeItemDefinition> definitionMap = this.buildChargeDefinitionMapByDefinitionId(rows);
        Map<String, ProductDefinitionDisplayDto> productMap = this.buildProductDefinitionMapByChargeItems(rows, definitionMap);
        Map<Long, String> cashierNameByPractitionerId = this.buildCashierDisplayNameByPractitionerId(rows);
        LinkedHashSet<Long> batchAccountIds = new LinkedHashSet<>();
        LinkedHashSet<Long> batchDxIds = new LinkedHashSet<>();
        for (ChargeItem row : rows) {
            if (row.getAccountId() != null) {
                batchAccountIds.add(row.getAccountId());
            }
            collectEncounterDiagnosisIdsFromIds(row.getEncounterDiagnosisIds(), batchDxIds);
        }
        Map<Long, Account> accountByIdForPending = accountQueryService.mapAccountsByIds(batchAccountIds);
        LinkedHashSet<String> contractBusNos = new LinkedHashSet<>();
        for (Account acc : accountByIdForPending.values()) {
            if (acc != null && StringUtils.isNotBlank(acc.getContractNo())) {
                contractBusNos.add(acc.getContractNo().trim());
            }
        }
        Map<String, String> contractNameByBusNo = contractQueryService.mapContractNameByBusNoIn(contractBusNos);
        Map<String, Long> contractIdByBusNo = contractQueryService.mapContractIdByBusNoIn(contractBusNos);
        Map<String, String> contractCategoryByBusNo = new HashMap<>();
        for (Contract contractEntity : contractQueryService.findAllContractEntitiesByBusNoIn(contractBusNos)) {
            if (contractEntity == null || StringUtils.isBlank(contractEntity.getBusNo())
                || StringUtils.isBlank(contractEntity.getCategoryEnum())) {
                continue;
            }
            contractCategoryByBusNo.put(contractEntity.getBusNo().trim(), contractEntity.getCategoryEnum().trim());
        }
        Map<Long, String> medTypeByEncounterDxId = encounterQueryService.mapMedTypeByEncounterDiagnosisIds(batchDxIds);
        List<PendingChargeItemRowDto> items = new ArrayList<>();
        for (ChargeItem row : rows) {
            PendingChargeItemRowDto item = new PendingChargeItemRowDto();
            item.setChargeItemId(row.getId());
            item.setServiceTable(row.getServiceTable());
            item.setServiceId(row.getServiceId());
            ChargeItemDefinition definitionRow = definitionMap.get(row.getDefinitionId());
            ProductDefinitionDisplayDto product = this.resolveProductForChargeItem(row, productMap, definitionRow);
            // 名称口径与 IF-OPC-06 预结算预览一致；此处复用已解析的目录行，避免对同一键二次查 Map。
            item.setItemName(this.resolveOutpatientChargeLineDisplayLabel(row, definitionMap, product));
            item.setBusNo(row.getBusNo());
            item.setUnitPrice(row.getUnitPrice());
            item.setQuantityValue(this.normalizeQuantityValue(row.getQuantityValue()));
            item.setQuantityUnit(this.resolveOutpatientCashierQuantityUnit(row.getQuantityUnit(), product));
            item.setQuantityDisplay(this.buildChargeItemQuantityDisplay(row));
            this.applyPendingChargeCategoryFields(item, product, definitionRow);
            item.setStatusCode(row.getStatusEnum());
            ChargeItemStatus status = EnumUtil.fromCode(ChargeItemStatus.class, row.getStatusEnum());
            if (status != null) {
                item.setStatusText(status.getDisplay());
            } else {
                item.setStatusText(row.getStatusEnum());
            }
            item.setTotalPrice(row.getTotalPrice());
            // 收银展示收款人：仅以 adm_charge_item.performer_id 为口径，不做 updated_by 回落，避免与执行人语义混淆。
            Long cashierPractitionerId = row.getPerformerId();
            if (cashierPractitionerId != null) {
                item.setCashierUserId(cashierPractitionerId);
                item.setCashierName(cashierNameByPractitionerId.get(cashierPractitionerId));
            }
            if (includePriceExtension) {
                item.setDiscountRateDisplay(this.buildDiscountRateDisplay(row.getDiscountAmount(), row.getTotalPrice()));
            }
            item.setAccountId(row.getAccountId());
            Account accBind = accountByIdForPending.get(row.getAccountId());
            if (accBind != null && StringUtils.isNotBlank(accBind.getContractNo())) {
                String contractBusNo = accBind.getContractNo().trim();
                String feeNature = contractNameByBusNo.get(contractBusNo);
                item.setContractName(feeNature);
                item.setFeeNatureDisplay(feeNature);
                item.setContractId(contractIdByBusNo.get(contractBusNo));
                item.setContractCategoryCode(contractCategoryByBusNo.get(contractBusNo));
            }
            item.setMedTypeCode(medTypeByEncounterDxId.get(firstEncounterDiagnosisIdFromIds(row.getEncounterDiagnosisIds())));
            item.setEncounterDiagnosisIds(row.getEncounterDiagnosisIds());
            if (product != null && StringUtils.isNotBlank(product.getYbNo())) {
                item.setYbNo(product.getYbNo());
                item.setMedicalInsuranceCode(product.getYbNo());
            }
            if (product != null) {
                item.setChrgitmLv(product.getChrgitmLv());
                item.setChrgitmLvText(product.getChrgitmLvText());
            }
            items.add(item);
        }
        return items;
    }

    /**
     * 批量解析收款侧展示名：仅收集 {@code adm_charge_item.performer_id}（系统用户主键），
     * 再按医务人员主档解析姓名。
     *
     * @param rows 收费明细
     * @return 执行人用户主键到展示名的映射（未关联医务人员时不放入键）
     */
    private Map<Long, String> buildCashierDisplayNameByPractitionerId(Collection<ChargeItem> rows) {
        LinkedHashSet<Long> practitionerIds = new LinkedHashSet<>();
        for (ChargeItem row : rows) {
            if (row.getPerformerId() != null) {
                practitionerIds.add(row.getPerformerId());
            }
        }
        if (practitionerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> out = new HashMap<>(practitionerIds.size() * 2);
        for (Practitioner practitioner : practitionerQueryService.findAllByIds(practitionerIds)) {
            if (practitioner != null && practitioner.getId() != null && StringUtils.isNotBlank(practitioner.getName())) {
                out.put(practitioner.getId(), practitioner.getName());
            }
        }
        return out;
    }

    /**
     * 将定价定义上的 {@code type_code} 落到待收费行：编码原样透出，展示名仅在能与 {@link ChargeTypeCategory} 对齐时解析。
     */
    private void applyChargeCategoryFields(PendingChargeItemRowDto item, ChargeItemDefinition definition) {
        if (definition == null || StringUtils.isBlank(definition.getTypeCode())) {
            return;
        }
        String code = definition.getTypeCode().trim();
        item.setChargeCategoryCode(code);
        ChargeTypeCategory category = EnumUtil.fromCode(ChargeTypeCategory.class, code);
        if (category != null) {
            item.setChargeCategoryDisplay(category.getDisplay());
        }
    }

    /**
     * 待收费行费用分类：优先目录读模型分类列（锚点同 {@link #resolveCatalogLookupForChargeItem}：明细产品列或定价实例列），
     * 否则回落到收费项目定义的 {@code type_code}。
     */
    private void applyPendingChargeCategoryFields(
        PendingChargeItemRowDto item,
        ProductDefinitionDisplayDto product,
        ChargeItemDefinition definition) {
        if (product != null && StringUtils.isNotBlank(product.getCategoryCode())) {
            String code = product.getCategoryCode().trim();
            item.setChargeCategoryCode(code);
            if (StringUtils.isNotBlank(product.getCategoryCodeDictText())) {
                item.setChargeCategoryDisplay(product.getCategoryCodeDictText());
            } else {
                ChargeTypeCategory category = EnumUtil.fromCode(ChargeTypeCategory.class, code);
                if (category != null) {
                    item.setChargeCategoryDisplay(category.getDisplay());
                }
            }
            return;
        }
        this.applyChargeCategoryFields(item, definition);
    }

    /**
     * 按收费明细解析三大目录锚点并批量加载产品展示 DTO，满足 A.9.1 批量 I/O。
     *
     * <p>锚点优先 {@code adm_charge_item.product_table}/{@code product_id}；创建明细路径历史上未写入该列时，
     * 回落 {@code adm_charge_item_definition.instance_table}/{@code instance_id}（与定价多态实例一致）。</p>
     *
     * @param chargeItems   收费明细列表
     * @param definitionMap 定价定义映射（{@link #buildChargeDefinitionMapByDefinitionId}）；允许 {@code null}
     * @return 复合键（表名 + 分隔符 + 定义主键）到读模型；无锚点时返回空 Map
     */
    private Map<String, ProductDefinitionDisplayDto> buildProductDefinitionMapByChargeItems(
        List<ChargeItem> chargeItems,
        Map<Long, ChargeItemDefinition> definitionMap) {
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (ChargeItem row : chargeItems) {
            ChargeItemDefinition definitionRow = null;
            if (definitionMap != null && row.getDefinitionId() != null) {
                definitionRow = definitionMap.get(row.getDefinitionId());
            }
            ProductDefinitionBatchLookup lookup = this.resolveCatalogLookupForChargeItem(row, definitionRow);
            if (lookup != null) {
                lookups.add(lookup);
            }
        }
        if (lookups.isEmpty()) {
            return Collections.emptyMap();
        }
        return productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
    }

    /**
     * 解析单条收费明细对应的目录批量查询键：优先明细产品锚点，其次定价定义实例锚点。
     *
     * @param row        收费明细
     * @param definition 该行 {@code definition_id} 对应的定价定义；可 {@code null}
     * @return 有效键或 {@code null}
     */
    private ProductDefinitionBatchLookup resolveCatalogLookupForChargeItem(ChargeItem row, ChargeItemDefinition definition) {
        if (row.getProductId() != null && StringUtils.isNotBlank(row.getProductTable())) {
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductDefinitionId(row.getProductId());
            lookup.setProductTableName(row.getProductTable().trim());
            return lookup;
        }
        if (definition != null && definition.getInstanceId() != null && StringUtils.isNotBlank(definition.getInstanceTable())) {
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductDefinitionId(definition.getInstanceId());
            lookup.setProductTableName(definition.getInstanceTable().trim());
            return lookup;
        }
        return null;
    }

    /**
     * 从批量目录映射中解析单条收费明细对应的产品读模型行。
     *
     * @param row        收费明细
     * @param productMap {@link ProductDefinitionDisplayTerminologyAssembler#loadAsMap} 的结果
     * @param definition 该行定价定义，用于产品锚点缺失时的目录键回落
     * @return 命中则非空；锚点缺失或未命中时 {@code null}
     */
    private ProductDefinitionDisplayDto resolveProductForChargeItem(
        ChargeItem row,
        Map<String, ProductDefinitionDisplayDto> productMap,
        ChargeItemDefinition definition) {
        if (productMap == null || productMap.isEmpty()) {
            return null;
        }
        ProductDefinitionBatchLookup anchor = this.resolveCatalogLookupForChargeItem(row, definition);
        if (anchor == null) {
            return null;
        }
        String key = ProductDefinitionKeyUtil.buildCompositeKey(
            anchor.getProductTableName(),
            anchor.getProductDefinitionId());
        if (key == null) {
            return null;
        }
        return productMap.get(key);
    }

    /**
     * 门诊收银相关界面单行项目名称（预结算等仅有目录 Map 时）：先解析目录行再回落定价名。
     *
     * @param row           收费明细
     * @param definitionMap 定价定义映射（{@link #buildChargeDefinitionMapByDefinitionId}）
     * @param productMap    目录读模型映射（{@link #buildProductDefinitionMapByChargeItems}，允许为空 Map）
     * @return 非空展示文案
     */
    private String resolveOutpatientChargeLineDisplayLabel(
        ChargeItem row,
        Map<Long, ChargeItemDefinition> definitionMap,
        Map<String, ProductDefinitionDisplayDto> productMap) {
        ChargeItemDefinition definitionRow = null;
        if (definitionMap != null && row.getDefinitionId() != null) {
            definitionRow = definitionMap.get(row.getDefinitionId());
        }
        ProductDefinitionDisplayDto product = this.resolveProductForChargeItem(row, productMap, definitionRow);
        return this.resolveOutpatientChargeLineDisplayLabel(row, definitionMap, product);
    }

    /**
     * 门诊收银相关界面单行项目名称：优先三大目录读模型 {@code product_name}，其次定价 {@code charge_name}，最后共享占位。
     *
     * @param row           收费明细
     * @param definitionMap 定价定义映射（{@link #buildChargeDefinitionMapByDefinitionId}）
     * @param product       已解析的目录读模型行，允许 {@code null}
     * @return 非空展示文案
     */
    private String resolveOutpatientChargeLineDisplayLabel(
        ChargeItem row,
        Map<Long, ChargeItemDefinition> definitionMap,
        ProductDefinitionDisplayDto product) {
        if (product != null && StringUtils.isNotBlank(product.getProductName())) {
            return product.getProductName();
        }
        return this.resolveChargeItemDisplayName(row, definitionMap);
    }

    /**
     * 规范化数量值：去除尾部多余的零，保持展示简洁（如 1.00 → 1）。
     *
     * @param quantityValue 原始数量值；允许 {@code null}
     * @return 去零后的值；输入为 {@code null} 时返回 {@code null}
     */
    private BigDecimal normalizeQuantityValue(BigDecimal quantityValue) {
        if (quantityValue == null) {
            return null;
        }
        return quantityValue.stripTrailingZeros();
    }

    /**
     * 收集收费明细上的定义主键并一次性加载定价行，避免待收费列表对定义表 N+1 查询。
     *
     * @param chargeItems 收费明细列表
     * @return 定义主键到定价行的映射
     */
    private Map<Long, ChargeItemDefinition> buildChargeDefinitionMapByDefinitionId(List<ChargeItem> chargeItems) {
        LinkedHashSet<Long> definitionIds = new LinkedHashSet<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem.getDefinitionId() != null) {
                definitionIds.add(chargeItem.getDefinitionId());
            }
        }
        return chargeItemDefinitionQueryService.findAllDefinitionsByIdIn(definitionIds);
    }

    /**
     * 解析收费明细在界面上的项目名称：优先取定价定义的 {@code charge_name}。
     *
     * @param row            收费明细
     * @param definitionMap  {@link ChargeItemDefinitionQueryService#findAllDefinitionsByIdIn} 结果
     * @return 非空展示名；定义缺失时回落到共享占位常量
     */
    private String resolveChargeItemDisplayName(ChargeItem row, Map<Long, ChargeItemDefinition> definitionMap) {
        ChargeItemDefinition definition = definitionMap.get(row.getDefinitionId());
        if (definition != null && StringUtils.isNotBlank(definition.getChargeName())) {
            return definition.getChargeName();
        }
        return OutpatientChargeConstants.PENDING_CHARGE_ITEM_DISPLAY_FALLBACK;
    }

    /**
     * 拼接数量与单位，便于收费台与待收费列表同口径展示。
     *
     * @param row 收费明细
     * @return 拼接文案；数量或单位缺失时返回 {@code null}
     */
    private String buildChargeItemQuantityDisplay(ChargeItem row) {
        if (row.getQuantityValue() == null) {
            return null;
        }
        return row.getQuantityValue().stripTrailingZeros().toPlainString();
    }

    /**
     * 门诊收银数量单位展示：按产品读模型各槽单位码精确匹配展示名；未命中时返回持久化 code。
     *
     * @param quantityUnit 持久化单位 code
     * @param product      产品读模型，可空
     * @return 展示用单位；无法解析时回落持久化 code
     */
    private String resolveOutpatientCashierQuantityUnit(String quantityUnit, ProductDefinitionDisplayDto product) {
        if (StringUtils.isBlank(quantityUnit)) {
            return quantityUnit;
        }
        String normalized = quantityUnit.trim();
        if (product != null) {
            List<ProductDefinitionCatalogUnitBandDto> bands =
                ProductDefinitionCatalogUnitBandsAssembler.buildInboundSelectionBands(product);
            for (ProductDefinitionCatalogUnitBandDto band : bands) {
                if (band == null || StringUtils.isBlank(band.getUnitCode())) {
                    continue;
                }
                if (normalized.equalsIgnoreCase(band.getUnitCode().trim())
                    && StringUtils.isNotBlank(band.getUnitName())) {
                    return band.getUnitName();
                }
            }
        }
        return normalized;
    }

    /**
     * 计算折扣率展示文本（如 9.5折、10.0折）。
     *
     * @param discountAmount 折后金额
     * @param totalPrice 原价金额
     * @return 折扣率文本；金额缺失或非法时返回 null
     */
    private String buildDiscountRateDisplay(BigDecimal discountAmount, BigDecimal totalPrice) {
        if (discountAmount == null || totalPrice == null) {
            return null;
        }
        if (totalPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal ratio = discountAmount.divide(totalPrice, 4, RoundingMode.HALF_UP);
        BigDecimal display = ratio.multiply(new BigDecimal("10")).setScale(1, RoundingMode.HALF_UP);
        return display + "折";
    }

    /**
     * 汇总收费项总金额。
     *
     * @param chargeItems 收费项列表
     * @return 金额合计（null 金额按 0 处理）
     */
    private BigDecimal sumChargeItemAmount(List<ChargeItem> chargeItems) {
        BigDecimal total = BigDecimal.ZERO;
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem.getTotalPrice() != null) {
                total = total.add(chargeItem.getTotalPrice());
            }
        }
        return total;
    }

    /**
     * 按收费项账户维度汇总，装配预结算阶段写入支付草稿的占位支付明细。
     *
     * <p>与 {@link com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationDomainService#createPaymentDraftFromPreSettle}
     * 中 {@code replaceDetails} 语义配合：入参非空才会插入 {@code fin_payment_rec_detail} 行。</p>
     *
     * <p>汇总规则：遍历所选收费项，以 {@link ChargeItem#getAccountId()} 为记账账户主键；
     * 若某行未绑定账户，则回落为就诊默认自费账户（{@link AccountQueryService#getSelfPayAccount(Long)}），
     * 与转自费、挂号开户后的个人现金账户口径一致。多账户并存时按账户分多行占位，各行
     * {@code amount} 之和与 {@link #sumChargeItemAmount(List)} 一致，供 IF-OPC-07
     * {@link #assertDraftDetailAmountMatchesExpected} 校验。</p>
     *
     * <p>正式结算请求中的渠道拆分将写入结算命令并由领域服务覆盖草稿明细；占位行的
     * {@code payEnum} 固定为 {@link PaymentChannel#CASHIER}、
     * {@code resultEnum} 为 {@link PaymentStatus#DRAFT} 的持久化码，仅表达「待收银确认」语义。</p>
     *
     * @param encounterId 就诊主键（用于解析默认账户与就诊下账户列表以补全 {@code accountCode}）
     * @param chargeLines 已选收费项列表，须与预结算请求勾选集合一致
     * @return 非空的 {@link PaymentDetailItemCommand} 列表，长度等于不同结算账户的去重个数
     * @throws BusinessException 当收费项未带账户且无法解析默认自费账户时抛出，语义码 {@code EX-OPC-308}
     */
    private List<PaymentDetailItemCommand> buildPreSettlePaymentDetailDraftLines(Long encounterId, List<ChargeItem> chargeLines) {
        // 默认自费账户与收费项上的 accountId 同源：医生站/收费项开立时已锚定，预结算仅做缺失回落。
        Long defaultSelfPayAccountId = accountQueryService.getSelfPayAccount(encounterId);
        Map<Long, BigDecimal> amountByAccountId = new LinkedHashMap<>();
        for (ChargeItem chargeLine : chargeLines) {
            Long accountId = chargeLine.getAccountId();
            if (accountId == null) {
                accountId = defaultSelfPayAccountId;
            }
            // 仍无账户则无法生成符合子表非空约束的明细行，亦无法在后续结算中归集资金池。
            if (accountId == null) {
                throw new BusinessException(
                    ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                    this.buildMessage(
                        OutpatientChargeConstants.MESSAGE_CODE_PRESETTLE_ACCOUNT_MISSING,
                        "收费项未绑定结算账户且就诊未开立默认自费账户，无法生成支付草稿"));
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
        // 一次性拉取就诊下账户主数据，用于回填 accountCode；未命中仅影响展示与对账可读性，不阻断落库。
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
     * 汇总本次提交支付明细金额。
     *
     * @param request 正式结算请求
     * @return 提交金额合计（null 金额按 0 处理）
     */
    private BigDecimal sumSubmitAmount(ConfirmOutpatientChargeSettlementRequest request) {
        BigDecimal total = BigDecimal.ZERO;
        for (int index = 0; index < request.getPaymentDetails().size(); index++) {
            if (request.getPaymentDetails().get(index).getAmount() != null) {
                if (request.getPaymentDetails().get(index).getAmount().compareTo(BigDecimal.ZERO) < 0) {
                    throw new BusinessException(
                        ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                        this.buildMessage(OutpatientChargeConstants.MESSAGE_CODE_SETTLE_AMOUNT_MISMATCH, "支付明细金额不能为负数"));
                }
                total = total.add(request.getPaymentDetails().get(index).getAmount());
            }
        }
        return total;
    }

    /**
     * 校验草稿支付从表明细金额合计与当前收费项事实总额一致。
     *
     * <p>与 IF-OPC-06 写入的占位明细闭环：防止历史草稿金额与当前 {@code ChargeItem} 不一致时穿透到成功态。</p>
     *
     * @param details 草稿态 {@code fin_payment_rec_detail} 读模型行集合，调用方已保证非空
     * @param expected 预期金额，由本次请求勾选收费项的 {@code totalPrice} 汇总
     * @throws BusinessException 任一行金额缺失、为负或与 {@code expected} 不等时抛出
     */
    private void assertDraftDetailAmountMatchesExpected(List<PaymentRecDetailQueryModel> details, BigDecimal expected) {
        BigDecimal draftTotal = BigDecimal.ZERO;
        for (int index = 0; index < details.size(); index++) {
            PaymentRecDetailQueryModel detail = details.get(index);
            if (detail.getAmount() == null) {
                throw new BusinessException(
                    ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT,
                    this.buildMessage(OutpatientChargeConstants.MESSAGE_CODE_SETTLE_DRAFT_DETAIL_EMPTY, "支付草稿明细金额缺失"));
            }
            if (detail.getAmount().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException(
                    ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT,
                    this.buildMessage(OutpatientChargeConstants.MESSAGE_CODE_SETTLE_DRAFT_DETAIL_EMPTY, "支付草稿明细金额非法"));
            }
            draftTotal = draftTotal.add(detail.getAmount());
        }
        if (draftTotal.compareTo(expected) != 0) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT,
                this.buildMessage(OutpatientChargeConstants.MESSAGE_CODE_SETTLE_AMOUNT_MISMATCH, "支付草稿金额与收费项不一致"));
        }
    }

    /**
     * 将草稿支付明细上的账户写回正式结算回执明细，满足 {@code fin_payment_rec_detail.account_id} 与领域自检。
     *
     * <p>IF-OPC-07 请求体通常只带渠道与金额；{@link PaymentReconciliationDomainService#settleOutpatientPayment}
     * 在非空 {@code payReceiptDetails} 时会整表替换从表，必须在应用层沿用 IF-OPC-06 已落库的账户分桶。</p>
     *
     * @param receiptLines Mapper 装配的回执明细（可原地扩展为多行）
     * @param draftDetails 当前支付草稿下的明细读模型，调用方已保证非空
     */
    private void attachDraftAccountsToPayReceiptDetails(
        List<PaymentDetailItemCommand> receiptLines,
        List<PaymentRecDetailQueryModel> draftDetails) {
        if (receiptLines == null || receiptLines.isEmpty()) {
            return;
        }
        if (draftDetails == null || draftDetails.isEmpty()) {
            return;
        }
        boolean anyMissingAccount =
            receiptLines.stream().anyMatch(line -> line != null && line.getAccountId() == null);
        if (!anyMissingAccount) {
            return;
        }
        if (draftDetails.size() == 1) {
            PaymentRecDetailQueryModel draftRow = draftDetails.get(0);
            if (draftRow.getAccountId() == null) {
                throw new BusinessException(
                    ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                    this.buildMessage(
                        OutpatientChargeConstants.MESSAGE_CODE_SETTLE_DRAFT_DETAIL_EMPTY,
                        "支付草稿明细缺少账户标识"));
            }
            for (PaymentDetailItemCommand line : receiptLines) {
                if (line == null) {
                    continue;
                }
                if (line.getAccountId() == null) {
                    line.setAccountId(draftRow.getAccountId());
                    line.setAccountCode(draftRow.getAccountCode());
                }
            }
            return;
        }
        // 多账户草稿且收银端合并为一笔实付：按草稿各行金额拆成多条回执，渠道/结果字段沿用模板行。
        if (receiptLines.size() == 1) {
            PaymentDetailItemCommand template = receiptLines.get(0);
            if (template == null) {
                return;
            }
            BigDecimal draftSum = BigDecimal.ZERO;
            for (PaymentRecDetailQueryModel draftRow : draftDetails) {
                if (draftRow.getAmount() != null) {
                    draftSum = draftSum.add(draftRow.getAmount());
                }
            }
            if (template.getAmount() != null && template.getAmount().compareTo(draftSum) != 0) {
                throw new BusinessException(
                    ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT,
                    this.buildMessage(
                        OutpatientChargeConstants.MESSAGE_CODE_SETTLE_AMOUNT_MISMATCH,
                        "支付明细总金额与草稿账户分桶金额不一致"));
            }
            List<PaymentDetailItemCommand> expanded = new ArrayList<>();
            for (PaymentRecDetailQueryModel draftRow : draftDetails) {
                if (draftRow.getAccountId() == null) {
                    throw new BusinessException(
                        ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                        this.buildMessage(
                            OutpatientChargeConstants.MESSAGE_CODE_SETTLE_DRAFT_DETAIL_EMPTY,
                            "支付草稿明细缺少账户标识"));
                }
                PaymentDetailItemCommand row = new PaymentDetailItemCommand();
                row.setPayEnum(template.getPayEnum());
                row.setPayLevelEnum(template.getPayLevelEnum());
                row.setPayTransDate(template.getPayTransDate());
                row.setResultEnum(template.getResultEnum());
                row.setPayTransNo(template.getPayTransNo());
                row.setPayTransText(template.getPayTransText());
                row.setAccountId(draftRow.getAccountId());
                row.setAccountCode(draftRow.getAccountCode());
                BigDecimal part = BigDecimal.ZERO;
                if (draftRow.getAmount() != null) {
                    part = draftRow.getAmount();
                }
                row.setAmount(part);
                if (draftRow.getChargeAmount() != null) {
                    row.setChargeAmount(draftRow.getChargeAmount());
                } else {
                    row.setChargeAmount(part);
                }
                expanded.add(row);
            }
            receiptLines.clear();
            receiptLines.addAll(expanded);
            return;
        }
        if (receiptLines.size() == draftDetails.size()) {
            for (int index = 0; index < receiptLines.size(); index++) {
                PaymentDetailItemCommand line = receiptLines.get(index);
                PaymentRecDetailQueryModel draftRow = draftDetails.get(index);
                if (line == null || draftRow == null) {
                    continue;
                }
                if (line.getAccountId() != null) {
                    continue;
                }
                if (draftRow.getAccountId() == null) {
                    throw new BusinessException(
                        ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                        this.buildMessage(
                            OutpatientChargeConstants.MESSAGE_CODE_SETTLE_DRAFT_DETAIL_EMPTY,
                            "支付草稿明细缺少账户标识"));
                }
                if (line.getAmount() != null && draftRow.getAmount() != null
                    && line.getAmount().compareTo(draftRow.getAmount()) != 0) {
                    throw new BusinessException(
                        ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT,
                        this.buildMessage(
                            OutpatientChargeConstants.MESSAGE_CODE_SETTLE_AMOUNT_MISMATCH,
                            "支付明细金额序与草稿账户分桶不一致"));
                }
                line.setAccountId(draftRow.getAccountId());
                line.setAccountCode(draftRow.getAccountCode());
            }
            return;
        }
        throw new BusinessException(
            ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
            this.buildMessage(
                OutpatientChargeConstants.MESSAGE_CODE_SETTLE_AMOUNT_MISMATCH,
                "支付明细条数与草稿账户分桶不匹配，无法回填账户"));
    }

    /**
     * 校验收费项归属是否全部匹配当前就诊。
     *
     * @param chargeItems 收费项列表
     * @param encounterId 就诊主键
     * @throws BusinessException 出现跨就诊收费项时抛出
     */
    private void assertChargeItemsBelongEncounter(List<ChargeItem> chargeItems, Long encounterId, String businessCode) {
        for (ChargeItem chargeItem : chargeItems) {
            if (!encounterId.equals(chargeItem.getEncounterId())) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_ITEM_RULE_CONFLICT,
                    this.buildMessage(businessCode, "收费项不属于当前就诊"));
            }
        }
    }

    /**
     * 校验本次拟收收费项未与就诊下已成功或退款处理中的支付流水挂载集合相交（IF-OPC-07 / BR-OPC-005，语义码 {@link OutpatientChargeConstants#MESSAGE_CODE_SETTLE_ALREADY_CHARGED}）。
     *
     * <p>触发时表示 {@code fin_payment_reconciliation} 中该就诊已存在 {@code PaymentStatus#SUCCESS} 或 {@code PaymentStatus#REFUND_PROCESSING}
     * 的流水，且其 {@code charge_item_ids} 与本次请求收费项有交集，属重复结算风险。若认定不应重叠，请核对流水挂载串与收费项主键是否脏数据。</p>
     *
     * @param encounterId   就诊主键
     * @param chargeItemIds 本次正式结算请求携带的收费项主键列表
     * @throws BusinessException 存在与历史成功/退款中流水重叠的收费项时抛出
     */
    private void assertNotAlreadySettled(Long encounterId, List<Long> chargeItemIds) {
        List<PaymentReconciliationQueryModel> rows =
            paymentReconciliationQueryService.findPaySuccessOrRefundingOverlappingChargeItems(encounterId, chargeItemIds);
        if (!rows.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT,
                this.buildMessage(
                    OutpatientChargeConstants.MESSAGE_CODE_SETTLE_ALREADY_CHARGED,
                    "本次收费项已存在于成功或退款处理中的支付流水"));
        }
    }

    /**
     * 校验收费项状态未结算。
     *
     * @param chargeItems 收费项列表
     * @throws BusinessException 任一收费项已结算时抛出
     */
    private void assertNotSettledChargeItems(List<ChargeItem> chargeItems) {
        for (ChargeItem chargeItem : chargeItems) {
            if (ChargeItemStatus.SETTLED.getCode().equals(chargeItem.getStatusEnum())) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_ITEM_INVALID_STATUS,
                    this.buildMessage(OutpatientChargeConstants.MESSAGE_CODE_SETTLE_CHARGE_ITEM_STATUS_INVALID, "收费明细已结算"));
            }
            if (ChargeItemStatus.REFUNDED.getCode().equals(chargeItem.getStatusEnum())) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_ITEM_INVALID_STATUS,
                    this.buildMessage(OutpatientChargeConstants.MESSAGE_CODE_SETTLE_CHARGE_ITEM_STATUS_INVALID, "收费明细已退款"));
            }
        }
    }

    /**
     * 提取收费项绑定的就诊诊断主键集合（解析 {@code encounter_diagnosis_ids} 有序 ids 串）。
     *
     * @param chargeItems 收费项列表
     * @return 去重后的就诊诊断主键列表
     */
    private List<Long> extractEncounterDiagnosisIds(List<ChargeItem> chargeItems) {
        LinkedHashSet<Long> idSet = new LinkedHashSet<>();
        for (ChargeItem chargeItem : chargeItems) {
            collectEncounterDiagnosisIdsFromIds(chargeItem.getEncounterDiagnosisIds(), idSet);
        }
        return new ArrayList<>(idSet);
    }

    /**
     * 从账单绑定 ids 串 收集就诊诊断主键（去重写入 target）。
     */
    private static void collectEncounterDiagnosisIdsFromIds(String idsText, Set<Long> target) {
        if (target == null || StringUtils.isEmpty(idsText)) {
            return;
        }
        String[] parts = idsText.split(",");
        for (String part : parts) {
            if (StringUtils.isEmpty(part)) {
                continue;
            }
            String trimmed = part.trim();
            if (StringUtils.isEmpty(trimmed)) {
                continue;
            }
            try {
                target.add(Long.parseLong(trimmed));
            } catch (NumberFormatException ignored) {
                // 跳过非法段
            }
        }
    }

    /**
     * 取 ids 串中首个合法就诊诊断主键（有序；首位为主诊断）。
     */
    private static Long firstEncounterDiagnosisIdFromIds(String idsText) {
        if (StringUtils.isEmpty(idsText)) {
            return null;
        }
        String[] parts = idsText.split(",");
        for (String part : parts) {
            if (StringUtils.isEmpty(part)) {
                continue;
            }
            String trimmed = part.trim();
            if (StringUtils.isEmpty(trimmed)) {
                continue;
            }
            try {
                return Long.parseLong(trimmed);
            } catch (NumberFormatException ignored) {
                // 继续扫描
            }
        }
        return null;
    }

    /**
     * 组装带业务码前缀的异常消息。
     *
     * @param businessCode 业务码
     * @param message 业务消息
     * @return 组合消息
     */
    private String buildMessage(String businessCode, String message) {
        return businessCode + ": " + message;
    }

    /**
     * 校验收费项是否全部命中。
     *
     * @param requestedIds 请求收费项主键
     * @param loadedChargeItems 实际加载收费项
     * @throws BusinessException 存在缺失收费项时抛出
     */
    private void assertChargeItemsLoaded(List<Long> requestedIds, List<ChargeItem> loadedChargeItems, String businessCode) {
        if (loadedChargeItems == null || loadedChargeItems.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_NOT_FOUND,
                this.buildMessage(businessCode, "收费明细不存在"));
        }
        LinkedHashSet<Long> requestIdSet = new LinkedHashSet<>(requestedIds);
        LinkedHashSet<Long> loadedIdSet = new LinkedHashSet<>();
        for (ChargeItem loadedChargeItem : loadedChargeItems) {
            loadedIdSet.add(loadedChargeItem.getId());
        }
        if (requestIdSet.size() != loadedIdSet.size()) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_NOT_FOUND,
                this.buildMessage(businessCode, "部分收费明细不存在"));
        }
        for (Long requestId : requestIdSet) {
            if (!loadedIdSet.contains(requestId)) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_ITEM_NOT_FOUND,
                    this.buildMessage(businessCode, "部分收费明细不存在"));
            }
        }
    }

    /**
     * 过滤允许切换账户的收费项。
     *
     * @param chargeItems 就诊下收费项列表
     * @return 可切账户收费项列表（非结算/非退款终态）
     */
    private List<ChargeItem> filterSwitchableChargeItems(List<ChargeItem> chargeItems) {
        List<ChargeItem> result = new ArrayList<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (ChargeItemStatus.SETTLED.getCode().equals(chargeItem.getStatusEnum())) {
                continue;
            }
            if (ChargeItemStatus.REFUNDED.getCode().equals(chargeItem.getStatusEnum())) {
                continue;
            }
            result.add(chargeItem);
        }
        return result;
    }

    /**
     * 收集仍需切换至目标账户的收费项（accountId 与目标不一致）。
     *
     * @param chargeItems 可切账户收费项列表
     * @param targetAccountId 目标账户主键
     * @return 待写库切换的行；已全部归属目标账户时为空列表
     */
    private List<ChargeItem> collectRowsNeedingTargetAccount(List<ChargeItem> chargeItems, Long targetAccountId) {
        List<ChargeItem> result = new ArrayList<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (targetAccountId.equals(chargeItem.getAccountId())) {
                continue;
            }
            result.add(chargeItem);
        }
        return result;
    }

    /**
     * 组装队列分页响应。
     *
     * @param page 源分页对象（仅用于保持分页对象依赖契约）
     * @param records 过滤后的记录
     * @return 分页响应
     */
    private PageOutpatientChargeQueueResponse toQueuePageResponse(
        Page<OutpatientEncounterContextQueryModel> page, List<ChargeQueueRowDto> records) {
        // —— B.10.11：使用 PagedResultBuilder 构建分页响应（不分页列表）。——
        return PagedResultBuilder.buildList(new PageOutpatientChargeQueueResponse(), records);
    }

    /**
     * 比较草稿收费项集合与本次提交集合是否完全一致。
     *
     * @param draftIds 草稿侧逗号串
     * @param requestIds 本次提交主键列表
     * @return true=集合一致；false=不一致
     */
    private boolean sameChargeItemSet(String draftIds, List<Long> requestIds) {
        List<Long> parsed = this.parseIds(draftIds);
        if (parsed.size() != requestIds.size()) {
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
     * 判断账单集合中是否存在未完成结算的记录（BR-OPC-012）。
     *
     * <p>草稿支付单仅在其挂载收费项中仍存在 {@link ChargeItemStatus#PLANNED} 时视为待收；
     * 若草稿绑定项均已 {@link ChargeItemStatus#SETTLED}，视为陈旧草稿，不阻断「已结算」标签。</p>
     *
     * @param statements 账单读模型列表
     * @return true=存在有效草稿、退款处理中或状态缺失账单；false=可视为资金侧已结清
     */
    private boolean hasUnsettledBillingStatement(List<PaymentReconciliationQueryModel> statements) {
        if (statements == null || statements.isEmpty()) {
            return false;
        }
        LinkedHashSet<Long> draftBoundChargeItemIds = new LinkedHashSet<>();
        for (PaymentReconciliationQueryModel statement : statements) {
            if (statement == null) {
                continue;
            }
            PaymentStatus status = statement.getStatusEnum();
            if (status == null) {
                // 状态缺失按未结算处理，避免把异常脏数据误判成已结算。
                return true;
            }
            if (PaymentStatus.REFUND_PROCESSING.equals(status)) {
                return true;
            }
            if (PaymentStatus.DRAFT.equals(status)) {
                for (Long chargeItemId : this.parseIds(statement.getChargeItemIds())) {
                    draftBoundChargeItemIds.add(chargeItemId);
                }
            }
        }
        if (draftBoundChargeItemIds.isEmpty()) {
            return false;
        }
        ChargeItemQueryCommand loadBoundItems = new ChargeItemQueryCommand();
        loadBoundItems.setIds(new ArrayList<>(draftBoundChargeItemIds));
        List<ChargeItem> boundChargeItems = chargeItemQueryService.loadChargeItemsByIds(loadBoundItems);
        Map<Long, ChargeItem> boundById = new HashMap<>();
        for (ChargeItem boundChargeItem : boundChargeItems) {
            if (boundChargeItem != null && boundChargeItem.getId() != null) {
                boundById.put(boundChargeItem.getId(), boundChargeItem);
            }
        }
        for (Long chargeItemId : draftBoundChargeItemIds) {
            ChargeItem bound = boundById.get(chargeItemId);
            if (bound == null) {
                // 草稿挂载的收费项已不存在，仍按未结清处理，提示运营核对。
                return true;
            }
            ChargeItemStatus persistedStatus = EnumUtil.fromCode(ChargeItemStatus.class, bound.getStatusEnum());
            if (persistedStatus == null) {
                return true;
            }
            if (ChargeItemStatus.PLANNED.equals(persistedStatus)) {
                return true;
            }
            if (!ChargeItemStatus.SETTLED.equals(persistedStatus)) {
                // 退费中等非终态仍视为该草稿未完结。
                return true;
            }
        }
        return false;
    }

    /**
     * 解析逗号分隔主键串。
     *
     * @param ids 逗号分隔主键串
     * @return 主键列表；空串返回空列表
     */
    private List<Long> parseIds(String ids) {
        List<Long> result = new ArrayList<>();
        if (ids == null || ids.isBlank()) {
            return result;
        }
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>();
        String[] split = ids.split(",");
        for (String value : split) {
            if (!value.isBlank()) {
                try {
                    Long parsedId = Long.parseLong(value.trim());
                    if (!uniqueIds.add(parsedId)) {
                        throw new BusinessException(
                            ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT,
                            this.buildMessage(OutpatientChargeConstants.MESSAGE_CODE_SETTLE_CHARGE_ITEMS_MISMATCH, "支付草稿收费项存在重复主键"));
                    }
                    result.add(parsedId);
                } catch (NumberFormatException ex) {
                    throw new BusinessException(
                        ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT,
                        this.buildMessage(OutpatientChargeConstants.MESSAGE_CODE_SETTLE_CHARGE_ITEMS_MISMATCH, "支付草稿收费项数据格式非法"));
                }
            }
        }
        return result;
    }

    /**
     * 将主键列表拼接为逗号分隔字符串。
     *
     * @param ids 主键列表
     * @return 逗号串
     */
    private String joinIds(List<Long> ids) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                builder.append(",");
            }
            builder.append(ids.get(i));
        }
        return builder.toString();
    }

    /**
     * 校验队列查询入参。
     *
     * @param request 队列请求
     * @throws BusinessException 入参为空、分页非法或页签为空时抛出
     */
    private void validateQueueRequest(PageOutpatientChargeQueueRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请求不能为空");
        }
        pageWindowGate.requirePositivePageWindow(
            request,
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
            "分页参数不合法");
        if (request.getQueueTabCode() == null || request.getQueueTabCode().isBlank()) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "队列页签不能为空");
        }
        if (!OutpatientChargeQueueTab.ALL.equals(request.getQueueTabCode())
            && !OutpatientChargeQueueTab.PENDING.equals(request.getQueueTabCode())
            && !OutpatientChargeQueueTab.SETTLED.equals(request.getQueueTabCode())) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "队列页签值非法");
        }
    }

    /**
     * 校验就诊主键非空。
     *
     * @param encounterId 就诊主键
     * @throws BusinessException 主键为空时抛出
     */
    private void validateEncounterId(Long encounterId) {
        if (encounterId == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "就诊主键不能为空");
        }
    }

    /**
     * 校验预结算入参。
     *
     * @param request 预结算请求
     * @throws BusinessException 请求为空、就诊为空或收费项为空时抛出
     */
    private void validatePreSettleRequest(PreSettleOutpatientChargesRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "预结算请求不能为空");
        }
        this.validateEncounterId(request.getEncounterId());
        if (request.getChargeItemIds() == null || request.getChargeItemIds().isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                this.buildMessage(OutpatientChargeConstants.MESSAGE_CODE_PRESETTLE_NO_CHARGE_ITEMS, "收费项不能为空"));
        }
        if (new LinkedHashSet<>(request.getChargeItemIds()).size() != request.getChargeItemIds().size()) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                this.buildMessage(OutpatientChargeConstants.MESSAGE_CODE_PRESETTLE_NO_CHARGE_ITEMS, "收费项不能重复"));
        }
        this.normalizeChargeItemIds(request.getChargeItemIds());
    }

    /**
     * 校验正式结算入参。
     *
     * @param request 正式结算请求
     * @throws BusinessException 关键参数缺失时抛出
     */
    private void validateConfirmSettleRequest(ConfirmOutpatientChargeSettlementRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT, "结算请求不能为空");
        }
        if (request.getPaymentDraftId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                this.buildMessage(OutpatientChargeConstants.MESSAGE_CODE_SETTLE_DRAFT_INVALID, "支付草稿不能为空"));
        }
        this.validateEncounterId(request.getEncounterId());
        if (request.getChargeItemIds() == null || request.getChargeItemIds().isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                this.buildMessage(OutpatientChargeConstants.MESSAGE_CODE_SETTLE_CHARGE_ITEMS_MISMATCH, "收费项不能为空"));
        }
        if (new LinkedHashSet<>(request.getChargeItemIds()).size() != request.getChargeItemIds().size()) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                this.buildMessage(OutpatientChargeConstants.MESSAGE_CODE_SETTLE_CHARGE_ITEMS_MISMATCH, "收费项不能重复"));
        }
        this.normalizeChargeItemIds(request.getChargeItemIds());
        if (request.getPaymentDetails() == null || request.getPaymentDetails().isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                this.buildMessage(OutpatientChargeConstants.MESSAGE_CODE_SETTLE_DRAFT_DETAIL_EMPTY, "支付明细不能为空"));
        }
        if (request.getBillDate() == null) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                this.buildMessage(OutpatientChargeConstants.MESSAGE_CODE_SETTLE_DRAFT_INVALID, "结算时间不能为空"));
        }
        if (request.getOutcomeEnum() == null || request.getOutcomeEnum().isBlank()) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                this.buildMessage(OutpatientChargeConstants.MESSAGE_CODE_SETTLE_DRAFT_INVALID, "支付结果不能为空"));
        }
        request.setOutcomeEnum(request.getOutcomeEnum().trim());
        this.validatePaymentDetailItems(request);
    }

    /**
     * 校验结算支付明细集合。
     *
     * @param request 正式结算请求
     * @throws BusinessException 支付渠道缺失或金额不合法时抛出
     */
    private void validatePaymentDetailItems(ConfirmOutpatientChargeSettlementRequest request) {
        LinkedHashSet<String> payChannelCodeSet = new LinkedHashSet<>();
        for (int index = 0; index < request.getPaymentDetails().size(); index++) {
            if (request.getPaymentDetails().get(index) == null) {
                throw new BusinessException(
                    ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                    this.buildMessage(OutpatientChargeConstants.MESSAGE_CODE_SETTLE_DRAFT_DETAIL_EMPTY, "支付明细不能为空行"));
            }
            String payChannelCode = paymentChannelGate.requireUserSelectablePayChannelCode(
                request.getPaymentDetails().get(index).getPayChannelCode(),
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                this.buildMessage(OutpatientChargeConstants.MESSAGE_CODE_SETTLE_DRAFT_DETAIL_EMPTY, "支付渠道不合法"));
            request.getPaymentDetails().get(index).setPayChannelCode(payChannelCode);
            if (!payChannelCodeSet.add(payChannelCode)) {
                throw new BusinessException(
                    ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                    this.buildMessage(OutpatientChargeConstants.MESSAGE_CODE_SETTLE_DRAFT_DETAIL_EMPTY, "支付渠道不能重复"));
            }
            if (request.getPaymentDetails().get(index).getAmount() == null) {
                throw new BusinessException(
                    ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                    this.buildMessage(OutpatientChargeConstants.MESSAGE_CODE_SETTLE_DRAFT_DETAIL_EMPTY, "支付金额不能为空"));
            }
            // 允许 0：医保全额报销后 displayAmount=0，仍须带渠道明细落账
            if (request.getPaymentDetails().get(index).getAmount().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException(
                    ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                    this.buildMessage(OutpatientChargeConstants.MESSAGE_CODE_SETTLE_AMOUNT_MISMATCH, "支付金额不能为负数"));
            }
        }
    }

    /**
     * 规范化收费项主键列表：校验非空并按升序排序。
     *
     * @param chargeItemIds 收费项主键列表
     * @throws BusinessException 收费项主键出现空值时抛出
     */
    private void normalizeChargeItemIds(List<Long> chargeItemIds) {
        for (int index = 0; index < chargeItemIds.size(); index++) {
            if (chargeItemIds.get(index) == null) {
                throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "收费项主键不能为空");
            }
        }
        Collections.sort(chargeItemIds);
    }

    /**
     * IF-OPC-07 后置：门诊收费项已结算后，按锚点推进关联医嘱/申请及发放草稿状态。
     *
     * @param settledChargeItems 本次结算涉及的收费明细（须已持久化为已结算）
     * @param operatorId         当前操作人（收银），可空
     */
    private void syncAdviceStatesAfterChargeSettlement(List<ChargeItem> settledChargeItems, Long operatorId) {
        // 空集合快速返回：结算时未勾选任何收费项或入参异常时直接结束。
        if (settledChargeItems == null || settledChargeItems.isEmpty()) {
            return;
        }
        // 聚合容器：去重以保证幂等，LinkedHashSet 保持插入顺序便于调试。
        Set<Long> medReqIds = new LinkedHashSet<>();
        List<Long> medicationDispenseAnchors = new ArrayList<>();
        Set<Long> deviceReqIds = new LinkedHashSet<>();
        List<Long> deviceDispenseAnchors = new ArrayList<>();
        Set<Long> serviceReqIds = new LinkedHashSet<>();

        // 第一遍遍历：按锚点表类型将收费项分发到对应的聚合桶。
        for (ChargeItem ci : settledChargeItems) {
            // 跳过空对象或服务锚点缺失的收费项，避免空指针或无法定位医嘱。
            if (ci == null || ci.getServiceId() == null || StringUtils.isBlank(ci.getServiceTable())) {
                continue;
            }
            String st = ci.getServiceTable();
            Long sid = ci.getServiceId();
            // 药品医嘱：直接收集申请 ID；发放草稿需二次映射到申请 ID 后合并。
            if (TableNameConstants.MED_MEDICATION_REQUEST.equals(st)) {
                medReqIds.add(sid);
            } else if (TableNameConstants.MED_MEDICATION_DISPENSE.equals(st)) {
                // 药品发放草稿：后续需通过查询映射回药品申请主键
                medicationDispenseAnchors.add(sid);
            } else if (TableNameConstants.WKF_DEVICE_REQUEST.equals(st)) {
                deviceReqIds.add(sid);
            } else if (TableNameConstants.WKF_DEVICE_DISPENSE.equals(st)) {
                // 耗材发放草稿：后续需通过查询映射回耗材申请主键
                deviceDispenseAnchors.add(sid);
            } else if (TableNameConstants.WKF_SERVICE_REQUEST.equals(st)) {
                serviceReqIds.add(sid);
            }
        }

        // 发放草稿→申请 ID 映射：确保通过发放结算的收费项也能推进对应的申请状态。
        if (!medicationDispenseAnchors.isEmpty()) {
            Map<Long, Long> map = medicationDispenseQueryService.mapMedicationDispenseIdsToMedReqIds(medicationDispenseAnchors);
            medReqIds.addAll(map.values());
        }
        if (!deviceDispenseAnchors.isEmpty()) {
            Map<Long, Long> map = deviceDispenseQueryService.mapDeviceDispenseIdsToDeviceReqIds(deviceDispenseAnchors);
            deviceReqIds.addAll(map.values());
        }

        // 药品路径：先完成申请状态推进，再批量推进发放草稿至待发药态。
        for (Long medReqId : medReqIds) {
            // 逐条推进药品申请至已完成态，幂等跳过已完成记录
            medicationRequestDomainService.completeMedicationRequestAfterOutpatientCharge(medReqId, operatorId);
        }
        // 批量推进药品发放草稿至待发药态，仅在存在待处理药品申请时执行
        if (!medReqIds.isEmpty()) {
            PromoteDispenseToPreparationCommand promoteMedCmd = new PromoteDispenseToPreparationCommand();
            promoteMedCmd.setMedReqIds(new ArrayList<>(medReqIds));
            promoteMedCmd.setOperatorId(operatorId);
            medicationDispenseDomainService.promoteToPreparation(promoteMedCmd);
        }

        // 耗材路径：申请状态推进与发放草稿推进并行。
        for (Long deviceReqId : deviceReqIds) {
            // 逐条推进耗材申请至已完成态
            deviceRequestDomainService.completeDeviceRequestAfterOutpatientCharge(deviceReqId, operatorId);
        }
        // 批量推进耗材发放草稿，仅在存在待处理耗材申请时执行
        deviceDispenseDomainService.promoteDraftDispensesForDeviceRequests(deviceReqIds, operatorId);

        // 诊疗路径：仅推进申请状态，无发放概念。
        for (Long serviceReqId : serviceReqIds) {
            serviceRequestDomainService.completeServiceRequestAfterOutpatientCharge(serviceReqId, operatorId);
        }
    }

    /**
     * 预结算分流：工伤 invoke / 单账户医保 2206 / 多账户 preSettleBatch；Extension 未启用则纯自费。
     *
     * @param request         预结算请求
     * @param paymentDraftId  支付草稿 id
     * @param response        预结算响应
     * @param chargeItems     待结算 chargeItem
     * @throws BusinessException 医保预结算缺 psnNo
     */
    private void applyInsurancePreSettleIfNeeded(
        PreSettleOutpatientChargesRequest request,
        Long paymentDraftId,
        PreSettleOutpatientChargesResponse response,
        List<ChargeItem> chargeItems) {
        Long workInjuryContractId = this.resolveWorkInjuryContractId(request, chargeItems, request.getEncounterId());
        if (workInjuryContractId != null) {
            this.applyWorkInjuryPreSettle(request, paymentDraftId, response, chargeItems, workInjuryContractId);
            return;
        }
        // Gate：Extension 未启用 → 纯自费口径
        if (!chargeInsuranceExtension.isEnabled()) {
            response.setSettlementComposition("SELF_PAY_ONLY");
            return;
        }
        Long insuranceContractId = this.resolveInsuranceContractId(request, chargeItems, request.getEncounterId());
        if (insuranceContractId == null) {
            response.setSettlementComposition("SELF_PAY_ONLY");
            return;
        }
        // Gate：费用性质非医保契约 → 纯自费
        if (!chargeInsuranceExtension.supportsThirdPartyContract(insuranceContractId)) {
            response.setSettlementComposition("SELF_PAY_ONLY");
            return;
        }
        if (StringUtils.isBlank(request.getPsnNo())) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                this.buildMessage(OutpatientChargeConstants.MESSAGE_CODE_PRESETTLE_CONTRACT_NOT_FOUND, "医保预结算须先读卡获取人员编号"));
        }
        boolean multiAccount = this.hasMultipleSettlementAccounts(request.getEncounterId(), chargeItems);
        boolean multiMedType = this.hasMultipleInsuranceMedTypes(chargeItems);
        // Gate：起付线要求普门/慢病分次收银，禁止一次勾选多种就诊类型合并预结算
        if (multiMedType) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                "普通门诊与慢病等须分次结算（起付线），请每次只勾选同一就诊类型的明细");
        }
        if (!multiAccount) {
            this.applySingleInsurancePreSettle(request, paymentDraftId, response, chargeItems, insuranceContractId);
            return;
        }
        // Cmd：多账户混合 → Extension preSettleBatch（医保组 + 自费 rollup）
        this.applyMixedInsurancePreSettle(request, paymentDraftId, response, chargeItems, insuranceContractId);
    }

    /**
     * 本次勾选是否跨多个医保医疗类别（按收费项绑定诊断的 med_type；缺省视为 11）。
     *
     * @param chargeItems 待结算行
     * @return 去重后 med_type 数 &gt; 1 为 true
     */
    private boolean hasMultipleInsuranceMedTypes(List<ChargeItem> chargeItems) {
        if (chargeItems == null || chargeItems.isEmpty()) {
            return false;
        }
        LinkedHashSet<Long> dxIds = new LinkedHashSet<>();
        for (ChargeItem item : chargeItems) {
            collectEncounterDiagnosisIdsFromIds(item.getEncounterDiagnosisIds(), dxIds);
        }
        Map<Long, String> medTypeByDxId = encounterQueryService.mapMedTypeByEncounterDiagnosisIds(dxIds);
        LinkedHashSet<String> medTypes = new LinkedHashSet<>();
        for (ChargeItem item : chargeItems) {
            Long primaryDxId = this.firstEncounterDiagnosisIdFromIds(item.getEncounterDiagnosisIds());
            String medType = null;
            if (primaryDxId != null) {
                medType = medTypeByDxId.get(primaryDxId);
            }
            if (StringUtils.isBlank(medType)) {
                medType = "11";
            } else {
                medType = medType.trim();
            }
            medTypes.add(medType);
            if (medTypes.size() > 1) {
                return true;
            }
        }
        return false;
    }

    /** 工伤预结算：qualification 校验 + 启动 OP_SETTLE invoke 会话（2201→2206 由前端 Win 循环）。 */
    private void applyWorkInjuryPreSettle(
        PreSettleOutpatientChargesRequest request,
        Long paymentDraftId,
        PreSettleOutpatientChargesResponse response,
        List<ChargeItem> chargeItems,
        Long workInjuryContractId) {
        workInjurySettleExtension.assertQualificationBeforePreSettle(request.getEncounterId());
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT,
            "当前会话操作人不能为空");
        WorkInjurySettleExtensionIn command = new WorkInjurySettleExtensionIn()
            .setEncounterId(request.getEncounterId())
            .setContractId(workInjuryContractId)
            .setPaymentDraftId(paymentDraftId)
            .setOperatorId(operatorId)
            .setChargeItemIds(request.getChargeItemIds());
        PrepareWorkInjuryInvokeExtensionOut invokeStep = workInjurySettleExtension.startSettleSession(command);
        response.setSettlementComposition("WORK_INJURY_INVOKE");
        response.setWorkInjuryInvokeStep(invokeStep);
        paymentReconciliationDomainService.backfillPaymentContractNoIfBlank(
            paymentDraftId,
            this.resolveContractBusNoById(workInjuryContractId));
    }

    /** 单账户预结算：Extension → 2203→2204→2206。 */
    private void applySingleInsurancePreSettle(
        PreSettleOutpatientChargesRequest request,
        Long paymentDraftId,
        PreSettleOutpatientChargesResponse response,
        List<ChargeItem> chargeItems,
        Long insuranceContractId) {
        OutpatientChargePreSettleExtensionIn command = new OutpatientChargePreSettleExtensionIn();
        command.setEncounterId(request.getEncounterId());
        command.setChargeItemIds(request.getChargeItemIds());
        command.setContractId(insuranceContractId);
        command.setPaymentDraftId(paymentDraftId);
        command.setPayerCertType(request.getYbMediumType());
        command.setPayerCertNo(request.getYbCardInfo());
        command.setInsuredPersonNo(request.getPsnNo());
        command.setInsuplcAdmdvs(request.getInsuplcAdmdvs());
        command.setInsutype(request.getInsutype());
        command.setMedType(request.getMedType());
        // Cmd：Extension 单组预结算（2206）
        OutpatientChargePreSettleExtensionOut ybResult = chargeInsuranceExtension.preSettle(command);
        response.setInsurancePreSettled(Boolean.TRUE);
        response.setSettlementComposition("INSURANCE_ONLY");
        response.setYbChrgBchno(ybResult.getChargeBatchNo());
        response.setYbMedfeeSumamt(ybResult.getMedfeeSumamt());
        response.setYbFundPaySumamt(ybResult.getFundPaySumamt());
        response.setYbAcctPay(ybResult.getAcctPay());
        response.setYbPsnCashPay(ybResult.getPsnCashPay());
        if (ybResult.getPsnCashPay() != null) {
            response.setDisplayAmount(ybResult.getPsnCashPay());
        }
        OutpatientChargePreSettleBatchExtensionOut syntheticBatch = new OutpatientChargePreSettleBatchExtensionOut();
        OutpatientChargePreSettleGroupExtensionOut group = new OutpatientChargePreSettleGroupExtensionOut();
        group.setAccountId(this.resolvePrimaryAccountId(request.getEncounterId(), chargeItems));
        group.setPsnCashPay(ybResult.getPsnCashPay());
        syntheticBatch.setGroups(List.of(group));
        List<PaymentDetailItemCommand> draftLines = this.buildMixedPreSettlePaymentDetailDraftLines(
            request.getEncounterId(),
            chargeItems,
            syntheticBatch);
        paymentReconciliationDomainService.refreshPaymentDraftPreSettleAmounts(
            paymentDraftId,
            response.getDisplayAmount(),
            draftLines);
        // 纯医保预结算：补写医保合同 bus_no，供日结费用性质筛选。
        paymentReconciliationDomainService.backfillPaymentContractNoIfBlank(
            paymentDraftId,
            this.resolveContractBusNoById(insuranceContractId));
    }

    /** 混合预结算：Extension preSettleBatch，按 accountId|medType|diseCodg 分组。 */
    private void applyMixedInsurancePreSettle(
        PreSettleOutpatientChargesRequest request,
        Long paymentDraftId,
        PreSettleOutpatientChargesResponse response,
        List<ChargeItem> chargeItems,
        Long insuranceContractId) {
        OutpatientChargePreSettleBatchExtensionIn batchCommand = new OutpatientChargePreSettleBatchExtensionIn();
        batchCommand.setEncounterId(request.getEncounterId());
        batchCommand.setChargeItemIds(request.getChargeItemIds());
        batchCommand.setContractId(insuranceContractId);
        batchCommand.setPaymentDraftId(paymentDraftId);
        batchCommand.setPayerCertType(request.getYbMediumType());
        batchCommand.setPayerCertNo(request.getYbCardInfo());
        batchCommand.setInsuredPersonNo(request.getPsnNo());
        batchCommand.setInsuplcAdmdvs(request.getInsuplcAdmdvs());
        batchCommand.setInsutype(request.getInsutype());

        OutpatientChargePreSettleBatchExtensionOut batchResult = chargeInsuranceExtension.preSettleBatch(batchCommand);
        OpSelfPayRollupDto selfPayRollup = null;
        if (batchResult.getSelfPayRollup() != null) {
            selfPayRollup = new OpSelfPayRollupDto();
            selfPayRollup.setAccountId(batchResult.getSelfPayRollup().getAccountId());
            selfPayRollup.setChargeItemIds(batchResult.getSelfPayRollup().getChargeItemIds());
            selfPayRollup.setTotalAmount(batchResult.getSelfPayRollup().getTotalAmount());
        }
        if (batchResult.getGroups() == null || batchResult.getGroups().isEmpty()) {
            response.setSettlementComposition("SELF_PAY_ONLY");
            response.setSelfPayRollup(selfPayRollup);
            return;
        }

        List<OpInsurancePreSettleGroupDto> groupDtos = new ArrayList<>();
        BigDecimal totalFund = BigDecimal.ZERO;
        BigDecimal totalAcct = BigDecimal.ZERO;
        BigDecimal totalPsnCash = BigDecimal.ZERO;
        BigDecimal totalMedfee = BigDecimal.ZERO;
        List<String> chrgBchnos = new ArrayList<>();
        for (OutpatientChargePreSettleGroupExtensionOut group : batchResult.getGroups()) {
            OpInsurancePreSettleGroupDto dto = this.toInsuranceGroupDto(group, insuranceContractId);
            groupDtos.add(dto);
            if (group.getChargeBatchNo() != null) {
                chrgBchnos.add(group.getChargeBatchNo());
            }
            totalFund = this.addAmount(totalFund, group.getFundPaySumamt());
            totalAcct = this.addAmount(totalAcct, group.getAcctPay());
            totalPsnCash = this.addAmount(totalPsnCash, group.getPsnCashPay());
            totalMedfee = this.addAmount(totalMedfee, group.getMedfeeSumamt());
        }
        response.setInsurancePreSettled(Boolean.TRUE);
        response.setSettlementComposition(this.resolveSettlementComposition(groupDtos, selfPayRollup));
        response.setInsuranceGroups(groupDtos);
        response.setSelfPayRollup(selfPayRollup);
        response.setYbMedfeeSumamt(totalMedfee);
        response.setYbFundPaySumamt(totalFund);
        response.setYbAcctPay(totalAcct);
        response.setYbPsnCashPay(totalPsnCash);
        if (!chrgBchnos.isEmpty()) {
            response.setYbChrgBchno(chrgBchnos.get(chrgBchnos.size() - 1));
        }
        if (batchResult.getTotalPsnCashPay() != null) {
            response.setDisplayAmount(batchResult.getTotalPsnCashPay());
        }
        List<PaymentDetailItemCommand> draftLines = this.buildMixedPreSettlePaymentDetailDraftLines(
            request.getEncounterId(),
            chargeItems,
            batchResult);
        paymentReconciliationDomainService.refreshPaymentDraftPreSettleAmounts(
            paymentDraftId,
            response.getDisplayAmount(),
            draftLines);
        // 纯医保分组且无自费 rollup 时补写 contract_no；自付+医保混合结算保持空（DM-FIN-002）。
        if (selfPayRollup == null) {
            paymentReconciliationDomainService.backfillPaymentContractNoIfBlank(
                paymentDraftId,
                this.resolveContractBusNoById(insuranceContractId));
        }
    }

    /**
     * 本次结算 chargeItem 是否涉及多个 account（混合结算判定）。
     *
     * @param encounterId  就诊 id
     * @param chargeItems  待结算行
     * @return 账户数 &gt; 1 为 true
     */
    private boolean hasMultipleSettlementAccounts(Long encounterId, List<ChargeItem> chargeItems) {
        Long defaultSelfPayAccountId = accountQueryService.getSelfPayAccount(encounterId);
        LinkedHashSet<Long> accountIds = new LinkedHashSet<>();
        for (ChargeItem item : chargeItems) {
            Long accountId = item.getAccountId();
            if (accountId == null) {
                accountId = defaultSelfPayAccountId;
            }
            if (accountId != null) {
                accountIds.add(accountId);
            }
        }
        return accountIds.size() > 1;
    }

    /**
     * 解析主结算 accountId：优先 chargeItem 自带，否则就诊自费账户。
     *
     * @param encounterId  就诊 id
     * @param chargeItems  待结算行
     * @return 主 accountId；可能 null
     */
    private Long resolvePrimaryAccountId(Long encounterId, List<ChargeItem> chargeItems) {
        Long defaultSelfPayAccountId = accountQueryService.getSelfPayAccount(encounterId);
        for (ChargeItem item : chargeItems) {
            if (item.getAccountId() != null) {
                return item.getAccountId();
            }
        }
        return defaultSelfPayAccountId;
    }

    /**
     * 解析支付草稿 contract_no：请求显式合同优先，否则从收费项账户聚合（BR-OPC-003）。
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
        Long defaultSelfPayAccountId = accountQueryService.getSelfPayAccount(encounterId);
        LinkedHashSet<Long> accountIds = new LinkedHashSet<>();
        for (ChargeItem item : chargeItems) {
            if (item.getAccountId() != null) {
                accountIds.add(item.getAccountId());
            }
        }
        if (defaultSelfPayAccountId != null) {
            accountIds.add(defaultSelfPayAccountId);
        }
        Map<Long, Account> accountById = accountQueryService.mapAccountsByIds(accountIds);
        LinkedHashSet<String> contractBusNos = new LinkedHashSet<>();
        for (ChargeItem item : chargeItems) {
            Long accountId = item.getAccountId();
            if (accountId == null) {
                accountId = defaultSelfPayAccountId;
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
        Long primaryAccountId = this.resolvePrimaryAccountId(encounterId, chargeItems);
        if (primaryAccountId != null) {
            Account primaryAccount = accountById.get(primaryAccountId);
            if (primaryAccount != null && StringUtils.isNotBlank(primaryAccount.getContractNo())) {
                return primaryAccount.getContractNo().trim();
            }
        }
        return null;
    }

    /**
     * 合同 id → fin_contract.bus_no（trim）。
     *
     * @param contractId 合同主键
     * @return bus_no；不存在时 null
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
     * 解析医保预结算 contractId：请求显式优先，否则从 chargeItem 账户聚合第三方医保合同。
     *
     * @param request     预结算请求
     * @param chargeItems 待结算行
     * @param encounterId 就诊 id
     * @return 医保 contractId；无则 null
     */
    private Long resolveInsuranceContractId(
        PreSettleOutpatientChargesRequest request,
        List<ChargeItem> chargeItems,
        Long encounterId) {
        if (request.getContractId() != null) {
            return request.getContractId();
        }
        LinkedHashSet<Long> accountIds = new LinkedHashSet<>();
        for (ChargeItem item : chargeItems) {
            if (item.getAccountId() != null) {
                accountIds.add(item.getAccountId());
            }
        }
        Long defaultSelfPayAccountId = accountQueryService.getSelfPayAccount(encounterId);
        if (defaultSelfPayAccountId != null) {
            accountIds.add(defaultSelfPayAccountId);
        }
        Map<Long, Account> accountById = accountQueryService.mapAccountsByIds(accountIds);
        for (ChargeItem item : chargeItems) {
            Long accountId = item.getAccountId();
            if (accountId == null) {
                accountId = defaultSelfPayAccountId;
            }
            Account account = accountById.get(accountId);
            if (account == null || StringUtils.isBlank(account.getContractNo())) {
                continue;
            }
            Contract contract = contractQueryService.findOptionalContractEntityByBusNo(account.getContractNo().trim())
                .orElse(null);
            if (contract == null) {
                continue;
            }
            if (chargeInsuranceExtension.supportsThirdPartyContract(contract.getId())
                && !workInjurySettleExtension.supportsWorkInjuryAccount(contract.getId())) {
                return contract.getId();
            }
        }
        return null;
    }

    /**
     * 解析工伤预结算 contractId：请求或 chargeItem 账户须支持工伤 Extension。
     *
     * @param request     预结算请求
     * @param chargeItems 待结算行
     * @param encounterId 就诊 id
     * @return 工伤 contractId；无则 null
     */
    private Long resolveWorkInjuryContractId(
        PreSettleOutpatientChargesRequest request,
        List<ChargeItem> chargeItems,
        Long encounterId) {
        if (request.getContractId() != null
            && workInjurySettleExtension.supportsWorkInjuryAccount(request.getContractId())) {
            return request.getContractId();
        }
        LinkedHashSet<Long> accountIds = new LinkedHashSet<>();
        for (ChargeItem item : chargeItems) {
            if (item.getAccountId() != null) {
                accountIds.add(item.getAccountId());
            }
        }
        Long defaultSelfPayAccountId = accountQueryService.getSelfPayAccount(encounterId);
        if (defaultSelfPayAccountId != null) {
            accountIds.add(defaultSelfPayAccountId);
        }
        Map<Long, Account> accountById = accountQueryService.mapAccountsByIds(accountIds);
        for (ChargeItem item : chargeItems) {
            Long accountId = item.getAccountId();
            if (accountId == null) {
                accountId = defaultSelfPayAccountId;
            }
            Account account = accountById.get(accountId);
            if (account == null || StringUtils.isBlank(account.getContractNo())) {
                continue;
            }
            Contract contract = contractQueryService.findOptionalContractEntityByBusNo(account.getContractNo().trim())
                .orElse(null);
            if (contract == null) {
                continue;
            }
            if (workInjurySettleExtension.supportsWorkInjuryAccount(contract.getId())) {
                return contract.getId();
            }
        }
        return null;
    }

    /**
     * 正式结算是否为工伤路径（由 chargeItems 账户/请求 contract 判定）。
     *
     * @param request     结算请求
     * @param chargeItems 待结算行
     * @return 工伤结算为 true
     */
    private boolean isWorkInjuryChargeSettlement(
        ConfirmOutpatientChargeSettlementRequest request,
        List<ChargeItem> chargeItems) {
        PreSettleOutpatientChargesRequest synthetic = new PreSettleOutpatientChargesRequest()
            .setEncounterId(request.getEncounterId())
            .setChargeItemIds(request.getChargeItemIds());
        return this.resolveWorkInjuryContractId(synthetic, chargeItems, request.getEncounterId()) != null;
    }

    /**
     * 正式结算前查询工伤 2207 落账回执（不再调平台 2207）；须先完成 invoke 至 2207。
     */
    private List<OutpatientChargeConfirmExtensionOut> collectWorkInjuryConfirmResults(
        ConfirmOutpatientChargeSettlementRequest request,
        List<ChargeItem> chargeItems) {
        String chrgBchno = this.resolveWorkInjuryChrgBchno(request);
        WorkInjuryConfirmSettleExtensionIn command = new WorkInjuryConfirmSettleExtensionIn()
            .setPaymentDraftId(request.getPaymentDraftId())
            .setEncounterId(request.getEncounterId())
            .setChrgBchno(chrgBchno);
        WorkInjuryConfirmSettleExtensionOut wiResult = workInjurySettleExtension.confirmSettle(command);
        Long accountId = wiResult.getAccountId();
        if (accountId == null) {
            accountId = this.resolvePrimaryAccountId(request.getEncounterId(), chargeItems);
        }
        OutpatientChargeConfirmExtensionOut mapped = new OutpatientChargeConfirmExtensionOut()
            .setSettlementId(wiResult.getSetlId())
            .setChargeBatchNo(wiResult.getChrgBchno())
            .setSettlementRecordId(wiResult.getSettlementRecordId())
            .setMdtrtId(wiResult.getMdtrtId())
            .setAccountId(accountId)
            .setMedfeeSumamt(wiResult.getMedfeeSumamt())
            .setFundPaySumamt(wiResult.getFundPaySumamt())
            .setAcctPay(wiResult.getAcctPay())
            .setPsnCashPay(wiResult.getPsnCashPay());
        return List.of(mapped);
    }

    /**
     * 本地结算成功后绑定工伤 paymentId 与 2207 chrg_bchno（Extension confirmSettle）。
     *
     * @param request        结算请求
     * @param paymentId      正式 payment id
     * @param confirmResults 2207 结果
     */
    private void bindWorkInjuryPaymentAfterLocalSettle(
        ConfirmOutpatientChargeSettlementRequest request,
        Long paymentId,
        List<OutpatientChargeConfirmExtensionOut> confirmResults) {
        if (paymentId == null || confirmResults == null || confirmResults.isEmpty()) {
            return;
        }
        String chrgBchno = confirmResults.get(0).getChargeBatchNo();
        workInjurySettleExtension.confirmSettle(new WorkInjuryConfirmSettleExtensionIn()
            .setPaymentDraftId(request.getPaymentDraftId())
            .setPaymentId(paymentId)
            .setEncounterId(request.getEncounterId())
            .setChrgBchno(chrgBchno));
    }

    /**
     * 正式结算前收集第三方确认结果：工伤 2207 或医保 Extension 2207 列表。
     *
     * @param request     结算请求
     * @param chargeItems 待结算行
     * @return 确认结果列表
     */
    private List<OutpatientChargeConfirmExtensionOut> collectThirdPartyConfirmResults(
        ConfirmOutpatientChargeSettlementRequest request,
        List<ChargeItem> chargeItems) {
        if (this.isWorkInjuryChargeSettlement(request, chargeItems)) {
            return this.collectWorkInjuryConfirmResults(request, chargeItems);
        }
        return this.collectInsuranceConfirmResults(request);
    }

    /**
     * 解析工伤结算 chrg_bchno：请求字段优先，否则 ACTIVE 预结算快照。
     *
     * @param request 结算请求
     * @return chrg_bchno；无则 null
     */
    private String resolveWorkInjuryChrgBchno(ConfirmOutpatientChargeSettlementRequest request) {
        if (StringUtils.isNotBlank(request.getYbChrgBchno())) {
            return request.getYbChrgBchno().trim();
        }
        if (request.getYbChrgBchnos() != null && !request.getYbChrgBchnos().isEmpty()) {
            return request.getYbChrgBchnos().get(0).trim();
        }
        return workInjurySettleExtension.findActivePreSettleByPaymentDraft(
                new WorkInjuryPreSettleQueryExtensionIn().setPaymentDraftId(request.getPaymentDraftId()))
            .map(WorkInjuryPreSettleQueryExtensionOut::getChrgBchno)
            .map(String::trim)
            .orElse(null);
    }

    /**
     * 2207 确认结果 → fin_payment_yb_settlement 写入项。
     *
     * @param results 2207 结果列表
     * @return 医保结算项命令
     */
    private List<PaymentYbSettlementItemCommand> buildYbSettlementItems(
        List<OutpatientChargeConfirmExtensionOut> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        List<PaymentYbSettlementItemCommand> items = new ArrayList<>();
        for (OutpatientChargeConfirmExtensionOut row : results) {
            if (row == null || StringUtils.isBlank(row.getSettlementId())) {
                continue;
            }
            PaymentYbSettlementItemCommand item = new PaymentYbSettlementItemCommand();
            item.setSetlId(row.getSettlementId().trim());
            if (StringUtils.isNotBlank(row.getChargeBatchNo())) {
                item.setChrgBchno(row.getChargeBatchNo().trim());
            }
            if (StringUtils.isNotBlank(row.getMdtrtId())) {
                item.setMdtrtId(row.getMdtrtId().trim());
            }
            item.setSettlementRecordId(row.getSettlementRecordId());
            item.setAccountId(row.getAccountId());
            item.setStatus(PaymentYbSettlementStatus.CONFIRMED);
            items.add(item);
        }
        return items;
    }

    /**
     * 正式结算前扫码门禁：含 {@code SCAN_PAY} 时须金额 &gt; 0 且已提交 {@code clientScanResult}。
     *
     * <p>须在调用医保 2207 之前执行，避免平台已扣款后因扫码校验失败本地回滚造成账实不一致。</p>
     *
     * @param request 正式结算请求
     * @throws BusinessException 零现金扫码或缺少扫码回执
     */
    private void assertScanPayReadyBeforeInsuranceConfirm(ConfirmOutpatientChargeSettlementRequest request) {
        if (request.getPaymentDetails() == null || request.getPaymentDetails().isEmpty()) {
            return;
        }
        PaymentDetailSubmitDto scanLine = null;
        for (PaymentDetailSubmitDto detail : request.getPaymentDetails()) {
            if (detail == null) {
                continue;
            }
            if (PaymentChannel.SCAN_PAY.getCode().equals(StringUtils.trimToEmpty(detail.getPayChannelCode()))) {
                scanLine = detail;
                break;
            }
        }
        if (scanLine == null) {
            return;
        }
        if (scanLine.getAmount() == null || scanLine.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                "零现金应付禁止使用扫码支付渠道，请重新预结算后确认收费");
        }
        if (request.getClientScanResult() == null) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                "扫码支付须提交 clientScanResult");
        }
    }

    /**
     * 正式结算前逐组调 Extension 2207；部分失败时触发 {@link OutpatientChargeInsuranceCompensationOrchestration}。
     */
    private List<OutpatientChargeConfirmExtensionOut> collectInsuranceConfirmResults(
        ConfirmOutpatientChargeSettlementRequest request) {
        if (!chargeInsuranceExtension.isEnabled()) {
            return List.of();
        }
        List<String> chrgBchnos = this.resolveYbChrgBchnos(request);
        if (chrgBchnos.isEmpty()) {
            return List.of();
        }
        List<OutpatientChargeConfirmExtensionOut> results = new ArrayList<>();
        int succeededBatches = 0;
        try {
            for (String chrgBchno : chrgBchnos) {
                OutpatientChargeConfirmExtensionIn command = new OutpatientChargeConfirmExtensionIn();
                command.setPaymentDraftId(request.getPaymentDraftId());
                command.setChargeBatchNo(chrgBchno);
                command.setEncounterId(request.getEncounterId());
                // Cmd：每组 chrg_bchno 一次 2207
                OutpatientChargeConfirmExtensionOut ybResult = chargeInsuranceExtension.confirmBeforeLocalSettle(command);
                results.add(ybResult);
                succeededBatches += 1;
            }
        } catch (RuntimeException ex) {
            // 补偿：已成功组 2208/2601 回滚，避免重复收费
            if (succeededBatches > 0) {
                outpatientChargeInsuranceCompensationOrchestration.compensateSettlementsByPayment(request.getPaymentDraftId());
            }
            throw new BusinessException("INS-013", "部分医保结算失败，请勿重复收费，联系医保办处理");
        }
        return results;
    }

    /**
     * 2207 成功后，将基金/个账分项写入 {@code fin_payment_rec_detail}，与前端提交的现金渠道明细合并。
     */
    private void mergeInsurancePaymentDetailsIntoSettleCommand(
        SettleOutpatientPaymentCommand settleCommand,
        List<OutpatientChargeConfirmExtensionOut> ybConfirmResults,
        List<PaymentRecDetailQueryModel> draftDetails,
        java.time.LocalDateTime billDate,
        String outcomeEnum) {
        if (ybConfirmResults == null || ybConfirmResults.isEmpty()) {
            return;
        }
        Map<Long, String> accountCodeById = new HashMap<>();
        for (PaymentRecDetailQueryModel draftRow : draftDetails) {
            if (draftRow.getAccountId() != null && StringUtils.isNotBlank(draftRow.getAccountCode())) {
                accountCodeById.put(draftRow.getAccountId(), draftRow.getAccountCode().trim());
            }
        }
        List<PaymentDetailItemCommand> insuranceLines = new ArrayList<>();
        for (OutpatientChargeConfirmExtensionOut ybResult : ybConfirmResults) {
            if (ybResult == null) {
                continue;
            }
            Long accountId = ybResult.getAccountId();
            String accountCode = accountCodeById.get(accountId);
            String setlId = ybResult.getSettlementId();
            this.appendInsurancePaymentLine(
                insuranceLines,
                PaymentChannel.INSURANCE_FUND.getCode(),
                ybResult.getFundPaySumamt(),
                accountId,
                accountCode,
                setlId,
                billDate,
                outcomeEnum);
            this.appendInsurancePaymentLine(
                insuranceLines,
                PaymentChannel.INSURANCE_ACCT.getCode(),
                ybResult.getAcctPay(),
                accountId,
                accountCode,
                setlId,
                billDate,
                outcomeEnum);
        }
        if (insuranceLines.isEmpty()) {
            return;
        }
        List<PaymentDetailItemCommand> merged = new ArrayList<>(insuranceLines);
        if (settleCommand.getPayReceiptDetails() != null) {
            merged.addAll(settleCommand.getPayReceiptDetails());
        }
        settleCommand.setPayReceiptDetails(merged);
    }

    /**
     * 追加单条医保支付子明细至结算命令（金额≤0 或缺 accountId 时跳过/抛错）。
     *
     * @param target      目标列表
     * @param payEnum     支付渠道
     * @param amount      金额
     * @param accountId   账户 id
     * @param accountCode 账户 busNo
     * @param setlId      setl_id
     * @param billDate    交易时间
     * @param outcomeEnum 结果状态 code
     * @throws BusinessException 缺 accountId
     */
    private void appendInsurancePaymentLine(
        List<PaymentDetailItemCommand> target,
        String payEnum,
        BigDecimal amount,
        Long accountId,
        String accountCode,
        String setlId,
        java.time.LocalDateTime billDate,
        String outcomeEnum) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (accountId == null) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                this.buildMessage(
                    OutpatientChargeConstants.MESSAGE_CODE_SETTLE_DRAFT_DETAIL_EMPTY,
                    "医保结算明细缺少账户标识"));
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
     * 解析正式结算医保 chrg_bchno 列表：请求优先，否则 ACTIVE 预结算快照。
     *
     * @param request 结算请求
     * @return chrg_bchno 列表
     */
    private List<String> resolveYbChrgBchnos(ConfirmOutpatientChargeSettlementRequest request) {
        if (request.getYbChrgBchnos() != null && !request.getYbChrgBchnos().isEmpty()) {
            return request.getYbChrgBchnos();
        }
        if (StringUtils.isNotBlank(request.getYbChrgBchno())) {
            return List.of(request.getYbChrgBchno().trim());
        }
        if (!chargeInsuranceExtension.isEnabled() || request.getPaymentDraftId() == null) {
            return List.of();
        }
        List<OutpatientChargePreSettleQueryExtensionOut> active =
            chargeInsuranceExtension.listActivePreSettlesByPaymentDraft(
                new OutpatientChargePreSettleListExtensionIn().setPaymentDraftId(request.getPaymentDraftId()));
        List<String> chrgBchnos = new ArrayList<>();
        for (OutpatientChargePreSettleQueryExtensionOut row : active) {
            if (StringUtils.isNotBlank(row.getChargeBatchNo())) {
                chrgBchnos.add(row.getChargeBatchNo().trim());
            }
        }
        return chrgBchnos;
    }

    /**
     * 解析正式结算应付：工伤/医保预结算草稿 displayAmount 优先，否则 chargeItem 求和。
     *
     * @param payment     支付草稿读模型
     * @param chargeItems 待结算行
     * @return 应付金额
     */
    private BigDecimal resolveExpectedPayableAmount(
        PaymentReconciliationQueryModel payment,
        List<ChargeItem> chargeItems) {
        if (payment.getDisplayAmount() != null && payment.getId() != null) {
            if (workInjurySettleExtension.findActivePreSettleByPaymentDraft(
                    new WorkInjuryPreSettleQueryExtensionIn().setPaymentDraftId(payment.getId())).isPresent()) {
                return payment.getDisplayAmount();
            }
            if (chargeInsuranceExtension.isEnabled()
                && !chargeInsuranceExtension.listActivePreSettlesByPaymentDraft(
                    new OutpatientChargePreSettleListExtensionIn().setPaymentDraftId(payment.getId())).isEmpty()) {
                return payment.getDisplayAmount();
            }
        }
        return this.sumChargeItemAmount(chargeItems);
    }

    /**
     * 多账户 preSettleBatch 结果 → 按 account 聚合的 CASHIER 草稿支付明细。
     *
     * @param encounterId  就诊 id
     * @param chargeLines  chargeItem 行
     * @param batchResult  Extension batch 结果
     * @return 草稿明细；空则回落单账户 buildPreSettlePaymentDetailDraftLines
     */
    private List<PaymentDetailItemCommand> buildMixedPreSettlePaymentDetailDraftLines(
        Long encounterId,
        List<ChargeItem> chargeLines,
        OutpatientChargePreSettleBatchExtensionOut batchResult) {
        Map<Long, BigDecimal> amountByAccountId = new LinkedHashMap<>();
        if (batchResult.getGroups() != null) {
            for (OutpatientChargePreSettleGroupExtensionOut group : batchResult.getGroups()) {
                if (group.getAccountId() == null) {
                    continue;
                }
                BigDecimal lineAmount;
                if (group.getPsnCashPay() == null) {
                    lineAmount = BigDecimal.ZERO;
                } else {
                    lineAmount = group.getPsnCashPay();
                }
                amountByAccountId.merge(group.getAccountId(), lineAmount, BigDecimal::add);
            }
        }
        if (batchResult.getSelfPayRollup() != null
            && batchResult.getSelfPayRollup().getAccountId() != null
            && batchResult.getSelfPayRollup().getTotalAmount() != null) {
            amountByAccountId.put(
                batchResult.getSelfPayRollup().getAccountId(),
                batchResult.getSelfPayRollup().getTotalAmount());
        }
        if (amountByAccountId.isEmpty()) {
            return this.buildPreSettlePaymentDetailDraftLines(encounterId, chargeLines);
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
     * Extension 预结算分组 → 前端 OpInsurancePreSettleGroupDto。
     *
     * @param group      Extension 分组结果
     * @param contractId 医保合同 id
     * @return 分组 DTO
     */
    private OpInsurancePreSettleGroupDto toInsuranceGroupDto(
        OutpatientChargePreSettleGroupExtensionOut group,
        Long contractId) {
        OpInsurancePreSettleGroupDto dto = new OpInsurancePreSettleGroupDto();
        dto.setGroupIndex(group.getGroupIndex());
        dto.setMedType(group.getMedType());
        if (StringUtils.isNotBlank(group.getMedType())) {
            YbMedType medTypeEnum = EnumUtil.fromCode(YbMedType.class, group.getMedType().trim());
            if (medTypeEnum != null) {
                dto.setMedTypeDisplay(medTypeEnum.getDisplay());
            }
        }
        dto.setDiseCodg(group.getDiseCodg());
        dto.setContractId(contractId);
        dto.setAccountId(group.getAccountId());
        dto.setChargeItemIds(group.getChargeItemIds());
        dto.setChrgBchno(group.getChargeBatchNo());
        dto.setMdtrtId(group.getEncounterPlatformId());
        dto.setPreSettleId(group.getPreSettleId());
        dto.setMedfeeSumamt(group.getMedfeeSumamt());
        dto.setFundPaySumamt(group.getFundPaySumamt());
        dto.setAcctPay(group.getAcctPay());
        dto.setPsnCashPay(group.getPsnCashPay());
        return dto;
    }

    /**
     * 判定预结算结算组成：MIXED / INSURANCE_ONLY / SELF_PAY_ONLY。
     *
     * @param insuranceGroups 医保分组
     * @param selfPayRollup   自费 rollup
     * @return settlementComposition code
     */
    private String resolveSettlementComposition(
        List<OpInsurancePreSettleGroupDto> insuranceGroups,
        OpSelfPayRollupDto selfPayRollup) {
        boolean hasInsurance = insuranceGroups != null && !insuranceGroups.isEmpty();
        boolean hasSelfPay = selfPayRollup != null
            && selfPayRollup.getChargeItemIds() != null
            && !selfPayRollup.getChargeItemIds().isEmpty();
        if (hasInsurance && hasSelfPay) {
            return "MIXED";
        }
        if (hasInsurance) {
            if (insuranceGroups.size() > 1) {
                return "MIXED";
            }
            return "INSURANCE_ONLY";
        }
        return "SELF_PAY_ONLY";
    }

    /**
     * BigDecimal 安全累加（null delta 忽略）。
     *
     * @param base  累加基数
     * @param delta 增量
     * @return 累加结果
     */
    private BigDecimal addAmount(BigDecimal base, BigDecimal delta) {
        if (delta == null) {
            return base;
        }
        if (base == null) {
            return delta;
        }
        return base.add(delta);
    }
}
