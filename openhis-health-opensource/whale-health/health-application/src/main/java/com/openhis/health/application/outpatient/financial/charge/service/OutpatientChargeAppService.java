package com.openhis.health.application.outpatient.financial.charge.service;

import com.openhis.health.application.outpatient.financial.charge.dto.*;
import com.openhis.health.application.outpatient.financial.charge.mapper.OutpatientChargeMapper;
import com.openhis.health.application.outpatient.financial.charge.request.*;
import com.openhis.health.application.outpatient.financial.charge.response.*;
import com.openhis.health.module.common.assembler.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.module.common.dto.product.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.module.common.pagination.PageQueryBinder;
import com.openhis.health.module.common.pagination.PagedResultBuilder;
import com.openhis.health.module.common.validator.CurrentPractitionerResolver;
import com.openhis.health.module.common.validator.PageWindowGate;
import com.openhis.health.module.common.assembler.financial.PaymentChannelOptionProvider;
import com.openhis.health.module.common.validator.PaymentChannelGate;
import com.openhis.health.module.common.support.ThirdPartyPaymentSettleSupport;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.health.application.outpatient.financial.charge.support.ChargeReceiptPrintAssembler;
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
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.module.querysupport.financial.OutpatientChargeEncounterQueueLoader;
import com.openhis.health.domain.administration.invoice.service.InvoiceDomainService;
import com.openhis.health.domain.clinical.condition.service.ConditionQueryService;
import com.openhis.health.domain.clinical.condition.queries.model.ConditionBatchItemQueryModel;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.financial.contract.queries.model.ContractDetailQueryModel;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.command.AbortPreSettlementBeforePaymentCommand;
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
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.integration.support.insurance.outpatient.OutpatientInsuranceSettlementCompensation;
import com.openhis.health.integration.insurance.outpatient.OutpatientChargeInsuranceExtension;
import com.openhis.health.integration.support.einvoice.BossElectronicInvoiceIntegrationSupport;
import com.openhis.health.integration.insurance.outpatient.command.OutpatientChargeConfirmCommand;
import com.openhis.health.integration.insurance.outpatient.command.OutpatientChargePreSettleBatchCommand;
import com.openhis.health.integration.insurance.outpatient.command.OutpatientChargePreSettleCommand;
import com.openhis.health.integration.insurance.outpatient.result.OutpatientChargeConfirmResult;
import com.openhis.health.integration.insurance.outpatient.result.OutpatientChargePreSettleBatchResult;
import com.openhis.health.integration.insurance.outpatient.result.OutpatientChargePreSettleGroupResult;
import com.openhis.health.integration.insurance.outpatient.result.OutpatientChargePreSettleResult;
import com.openhis.health.integration.insurance.outpatient.result.OutpatientChargePreSettleSnapshot;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.OutpatientChargeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.OutpatientChargeQueueTab;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.ChargeTypeCategory;
import com.openhis.health.domain.share.enums.PaymentChannel;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.health.domain.share.enums.YbMedType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final EncounterContextFacade encounterContextFacade;
    private final AccountQueryService accountQueryService;
    private final ContractQueryService contractQueryService;
    private final ChargeItemDomainService chargeItemDomainService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final OutpatientChargeEncounterQueueLoader outpatientChargeEncounterQueueLoader;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final PaymentReconciliationDomainService paymentReconciliationDomainService;
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    private final InvoiceDomainService invoiceDomainService;
    private final ConditionQueryService conditionQueryService;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    private final OutpatientChargeMapper outpatientChargeMapper;
    private final MedicationRequestDomainService medicationRequestDomainService;
    private final MedicationDispenseDomainService medicationDispenseDomainService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final DeviceRequestDomainService deviceRequestDomainService;
    private final DeviceDispenseDomainService deviceDispenseDomainService;
    private final DeviceDispenseQueryService deviceDispenseQueryService;
    private final ServiceRequestDomainService serviceRequestDomainService;
    private final OutpatientChargeInsuranceExtension chargeInsuranceExtension;

    private final BossElectronicInvoiceIntegrationSupport bossElectronicInvoiceIntegrationSupport;

    private final OutpatientInsuranceSettlementCompensation outpatientInsuranceSettlementCompensation;

    private final ThirdPartyPaymentSettleSupport thirdPartyPaymentSettleSupport;

    private final ChargeReceiptPrintAssembler chargeReceiptPrintAssembler;

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
                encounterContextFacade.listOutpatientContextsByEncounterIdsOrdered(encounterPage.getContent());
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
            // Asm：映射明细行并返回。
            response.setItems(this.toPendingRows(rows, Boolean.TRUE.equals(request.getIncludePriceExtension())));
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
        // 预结算前校验就诊下存在诊断事实，避免无诊断直接进入收费流程。
        if (conditionQueryService.getEncounterDiagnosis(request.getEncounterId()).isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.CONDITION_RULE_CONFLICT,
                this.buildMessage(OutpatientChargeConstants.MESSAGE_CODE_PRESETTLE_DIAGNOSIS_MISSING, "当前就诊缺少诊断信息，不能预结算"));
        }
        // 若收费项绑定了 condition_id，则进一步校验诊断主键集合必须可被批量查询命中。
        List<Long> conditionIds = this.extractConditionIds(chargeItems);
        if (!conditionIds.isEmpty()) {
            List<ConditionBatchItemQueryModel> conditions = conditionQueryService.getConditionListById(conditionIds);
            if (conditions.size() != conditionIds.size()) {
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
        this.cancelStaleOutpatientPaymentDrafts(request.getEncounterId(), null);
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
        List<OutpatientChargeConfirmResult> ybConfirmResults = this.collectInsuranceConfirmResults(request);
        SettleOutpatientPaymentCommand settleCommand = outpatientChargeMapper.toSettleOutpatientPaymentCommand(request);
        settleCommand.setYbSettlements(this.buildYbSettlementItems(ybConfirmResults));
        this.mergeInsurancePaymentDetailsIntoSettleCommand(
            settleCommand,
            ybConfirmResults,
            details,
            request.getBillDate(),
            request.getOutcomeEnum());
        this.attachDraftAccountsToPayReceiptDetails(settleCommand.getPayReceiptDetails(), details);
        thirdPartyPaymentSettleSupport.applyScanBeforeSettle(
            request.getPaymentDraftId(),
            settleCommand.getPayReceiptDetails(),
            request.getClientScanResult(),
            operatorId,
            null);
        Long paymentId = paymentReconciliationDomainService.settleOutpatientPayment(settleCommand);
        BatchChargeItemIdsCommand batchChargeItemIdsCommand =
            outpatientChargeMapper.toBatchChargeItemIdsCommand(request.getChargeItemIds(), operatorId);
        chargeItemDomainService.settleChargeItems(batchChargeItemIdsCommand);
        // 正式结算后清理同就诊其它草稿支付单，防止队列标签仍命中陈旧 DRAFT。
        this.cancelStaleOutpatientPaymentDrafts(request.getEncounterId(), null);
        // 收费项已结算：按锚点推进关联医嘱/申请及发放草稿，患者方可进入发药/执行后续环节。
        this.syncAdviceStatesAfterChargeSettlement(chargeItems, operatorId);
        Long invoiceId = invoiceDomainService.createDraftInvoiceAfterPayment(
            outpatientChargeMapper.toCreateDraftInvoiceCommand(payment, paymentId, operatorId));
        bossElectronicInvoiceIntegrationSupport.afterDraftCreated(invoiceId);
        // Asm：装配正式结算成功响应（支付主键、票据主键）。
        return outpatientChargeMapper.toConfirmOutpatientChargeSettlementResponse(paymentId, invoiceId);
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
        this.validateEncounterId(encounterId);
        encounterQueryService.mustGetEncounter(encounterId);
        ChargeItemQueryCommand queryCommand = new ChargeItemQueryCommand();
        queryCommand.setEncounterId(encounterId);
        this.applyOutpatientCashierChargeItemQueryFilters(queryCommand);
        List<ChargeItem> rows = chargeItemQueryService.listChargeItemsByEncounterId(queryCommand);
        List<ChargeItem> switchableRows = this.filterSwitchableChargeItems(rows);
        // 全部明细都已结算/已退款时不允许再切账户，避免篡改历史记账事实。
        if (switchableRows.isEmpty()) {
            return outpatientChargeMapper.toSwitchAccountResponse(false, OutpatientChargeConstants.MESSAGE_CODE_SWITCH_FAILED);
        }
        Long targetAccountId;
        if (insurance) {
            targetAccountId = accountQueryService.getMedicalInsuranceAccount(encounterId);
        } else {
            targetAccountId = accountQueryService.getSelfPayAccount(encounterId);
        }
        if (targetAccountId == null) {
            return outpatientChargeMapper.toSwitchAccountResponse(false, OutpatientChargeConstants.MESSAGE_CODE_SWITCH_ACCOUNT_NOT_FOUND);
        }
        // 如果待切换行已全部指向目标账户，则视为幂等成功，避免重复写库和无意义事件。
        if (this.allRowsAlreadyInTargetAccount(switchableRows, targetAccountId)) {
            return outpatientChargeMapper.toSwitchAccountResponse(true, OutpatientChargeConstants.MESSAGE_CODE_SWITCH_SUCCESS);
        }
        SwitchChargeItemAccountCommand command = new SwitchChargeItemAccountCommand();
        command.setOperatorId(currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT,
            "当前会话操作人不能为空"));
        List<SwitchChargeItemAccountCommand.Item> items = new ArrayList<>();
        for (ChargeItem row : switchableRows) {
            SwitchChargeItemAccountCommand.Item item = new SwitchChargeItemAccountCommand.Item();
            item.setChargeItemId(row.getId());
            item.setAccountId(targetAccountId);
            items.add(item);
        }
        command.setItems(items);
        chargeItemDomainService.switchChargeItemAccount(command);
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
            if (row.getEncounterDiagnosisId() != null) {
                batchDxIds.add(row.getEncounterDiagnosisId());
            }
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
            }
            item.setMedTypeCode(medTypeByEncounterDxId.get(row.getEncounterDiagnosisId()));
            if (product != null && StringUtils.isNotBlank(product.getYbNo())) {
                item.setYbNo(product.getYbNo());
                item.setMedicalInsuranceCode(product.getYbNo());
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
        return productDefinitionBatchLoader.loadAsMap(lookups);
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
     * @param productMap {@link ProductDefinitionBatchLoader#loadAsMap} 的结果
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
     * 提取收费项关联诊断主键集合。
     *
     * @param chargeItems 收费项列表
     * @return 去重后的诊断主键列表
     */
    private List<Long> extractConditionIds(List<ChargeItem> chargeItems) {
        LinkedHashSet<Long> idSet = new LinkedHashSet<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem.getConditionId() != null) {
                idSet.add(chargeItem.getConditionId());
            }
        }
        return new ArrayList<>(idSet);
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
     * 判断待切换收费项是否已经全部归属到目标账户。
     *
     * @param chargeItems 可切换收费项列表
     * @param targetAccountId 目标账户主键
     * @return true=全部已归属目标账户；false=仍存在需切换行
     */
    private boolean allRowsAlreadyInTargetAccount(List<ChargeItem> chargeItems, Long targetAccountId) {
        for (ChargeItem chargeItem : chargeItems) {
            if (!targetAccountId.equals(chargeItem.getAccountId())) {
                return false;
            }
        }
        return true;
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
     * 作废同就诊下仍处于草稿态的支付流水，避免 BR-OPC-012 队列标签被陈旧预结算单误判。
     *
     * @param encounterId         就诊主键
     * @param retainDraftPaymentId 保留不作废的草稿主键；null 表示同就诊草稿全部作废
     */
    private void cancelStaleOutpatientPaymentDrafts(Long encounterId, Long retainDraftPaymentId) {
        if (encounterId == null) {
            return;
        }
        List<PaymentReconciliationQueryModel> encounterPayments =
            paymentReconciliationQueryService.findPaymentsByEncounter(encounterId);
        if (encounterPayments == null || encounterPayments.isEmpty()) {
            return;
        }
        for (PaymentReconciliationQueryModel payment : encounterPayments) {
            if (payment == null || payment.getId() == null) {
                continue;
            }
            if (!PaymentStatus.DRAFT.equals(payment.getStatusEnum())) {
                continue;
            }
            if (retainDraftPaymentId != null && retainDraftPaymentId.equals(payment.getId())) {
                continue;
            }
            AbortPreSettlementBeforePaymentCommand abortCommand = new AbortPreSettlementBeforePaymentCommand();
            abortCommand.setPaymentId(payment.getId());
            paymentReconciliationDomainService.abortPreSettlementBeforePayment(abortCommand);
        }
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
            if (request.getPaymentDetails().get(index).getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(
                    ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                    this.buildMessage(OutpatientChargeConstants.MESSAGE_CODE_SETTLE_AMOUNT_MISMATCH, "支付金额必须大于0"));
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

    private void applyInsurancePreSettleIfNeeded(
        PreSettleOutpatientChargesRequest request,
        Long paymentDraftId,
        PreSettleOutpatientChargesResponse response,
        List<ChargeItem> chargeItems) {
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
        if (!multiAccount) {
            this.applySingleInsurancePreSettle(request, paymentDraftId, response, chargeItems, insuranceContractId);
            return;
        }
        // Cmd：多账户/慢病混合 → Extension preSettleBatch（多组 2206）
        this.applyMixedInsurancePreSettle(request, paymentDraftId, response, chargeItems, insuranceContractId);
    }

    /** 单账户预结算：Extension → 2203→2204→2206。 */
    private void applySingleInsurancePreSettle(
        PreSettleOutpatientChargesRequest request,
        Long paymentDraftId,
        PreSettleOutpatientChargesResponse response,
        List<ChargeItem> chargeItems,
        Long insuranceContractId) {
        OutpatientChargePreSettleCommand command = new OutpatientChargePreSettleCommand();
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
        OutpatientChargePreSettleResult ybResult = chargeInsuranceExtension.preSettle(command);
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
        OutpatientChargePreSettleBatchResult syntheticBatch = new OutpatientChargePreSettleBatchResult();
        OutpatientChargePreSettleGroupResult group = new OutpatientChargePreSettleGroupResult();
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
        OutpatientChargePreSettleBatchCommand batchCommand = new OutpatientChargePreSettleBatchCommand();
        batchCommand.setEncounterId(request.getEncounterId());
        batchCommand.setChargeItemIds(request.getChargeItemIds());
        batchCommand.setContractId(insuranceContractId);
        batchCommand.setPaymentDraftId(paymentDraftId);
        batchCommand.setPayerCertType(request.getYbMediumType());
        batchCommand.setPayerCertNo(request.getYbCardInfo());
        batchCommand.setInsuredPersonNo(request.getPsnNo());
        batchCommand.setInsuplcAdmdvs(request.getInsuplcAdmdvs());
        batchCommand.setInsutype(request.getInsutype());

        OutpatientChargePreSettleBatchResult batchResult = chargeInsuranceExtension.preSettleBatch(batchCommand);
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
        for (OutpatientChargePreSettleGroupResult group : batchResult.getGroups()) {
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
            if (chargeInsuranceExtension.supportsThirdPartyContract(contract.getId())) {
                return contract.getId();
            }
        }
        return null;
    }

    private List<PaymentYbSettlementItemCommand> buildYbSettlementItems(
        List<OutpatientChargeConfirmResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        List<PaymentYbSettlementItemCommand> items = new ArrayList<>();
        for (OutpatientChargeConfirmResult row : results) {
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
     * 正式结算前逐组调 Extension 2207；部分失败时触发 {@link OutpatientInsuranceSettlementCompensation}。
     */
    private List<OutpatientChargeConfirmResult> collectInsuranceConfirmResults(
        ConfirmOutpatientChargeSettlementRequest request) {
        if (!chargeInsuranceExtension.isEnabled()) {
            return List.of();
        }
        List<String> chrgBchnos = this.resolveYbChrgBchnos(request);
        if (chrgBchnos.isEmpty()) {
            return List.of();
        }
        List<OutpatientChargeConfirmResult> results = new ArrayList<>();
        int succeededBatches = 0;
        try {
            for (String chrgBchno : chrgBchnos) {
                OutpatientChargeConfirmCommand command = new OutpatientChargeConfirmCommand();
                command.setPaymentDraftId(request.getPaymentDraftId());
                command.setChargeBatchNo(chrgBchno);
                command.setEncounterId(request.getEncounterId());
                // Cmd：每组 chrg_bchno 一次 2207
                OutpatientChargeConfirmResult ybResult = chargeInsuranceExtension.confirmBeforeLocalSettle(command);
                results.add(ybResult);
                succeededBatches += 1;
            }
        } catch (RuntimeException ex) {
            // 补偿：已成功组 2208/2601 回滚，避免重复收费
            if (succeededBatches > 0) {
                outpatientInsuranceSettlementCompensation.compensateSettlementsByPayment(request.getPaymentDraftId());
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
        List<OutpatientChargeConfirmResult> ybConfirmResults,
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
        for (OutpatientChargeConfirmResult ybResult : ybConfirmResults) {
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
        List<OutpatientChargePreSettleSnapshot> active =
            chargeInsuranceExtension.listActivePreSettlesByPaymentDraft(request.getPaymentDraftId());
        List<String> chrgBchnos = new ArrayList<>();
        for (OutpatientChargePreSettleSnapshot row : active) {
            if (StringUtils.isNotBlank(row.getChargeBatchNo())) {
                chrgBchnos.add(row.getChargeBatchNo().trim());
            }
        }
        return chrgBchnos;
    }

    private BigDecimal resolveExpectedPayableAmount(
        PaymentReconciliationQueryModel payment,
        List<ChargeItem> chargeItems) {
        if (payment.getDisplayAmount() != null
            && chargeInsuranceExtension.isEnabled()
            && payment.getId() != null
            && !chargeInsuranceExtension.listActivePreSettlesByPaymentDraft(payment.getId()).isEmpty()) {
            return payment.getDisplayAmount();
        }
        return this.sumChargeItemAmount(chargeItems);
    }

    private List<PaymentDetailItemCommand> buildMixedPreSettlePaymentDetailDraftLines(
        Long encounterId,
        List<ChargeItem> chargeLines,
        OutpatientChargePreSettleBatchResult batchResult) {
        Map<Long, BigDecimal> amountByAccountId = new LinkedHashMap<>();
        if (batchResult.getGroups() != null) {
            for (OutpatientChargePreSettleGroupResult group : batchResult.getGroups()) {
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

    private OpInsurancePreSettleGroupDto toInsuranceGroupDto(
        OutpatientChargePreSettleGroupResult group,
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
