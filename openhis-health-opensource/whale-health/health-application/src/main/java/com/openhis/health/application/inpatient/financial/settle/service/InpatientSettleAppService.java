package com.openhis.health.application.inpatient.financial.settle.service;

import com.openhis.health.application.common.assembler.financial.PaymentChannelDisplayAssembler;
import com.openhis.health.application.common.assembler.inpatient.InpatientDisplayAccountBalanceAssembler;
import com.openhis.health.application.common.assembler.inpatient.InpatientInfoQueueDisplayAssembler;
import com.openhis.health.application.common.pagination.PageQueryBinder;
import com.openhis.health.application.common.validator.CurrentOperatorResolver;
import com.openhis.health.application.common.validator.PageWindowGate;
import com.openhis.health.application.common.validator.PatientEncounterConsistencyGate;
import com.openhis.health.application.common.validator.PaymentChannelGate;
import com.openhis.health.application.inpatient.financial.prepay.mapper.InpatientPrepayMapper;
import com.openhis.health.application.inpatient.financial.settle.dto.InpatientSettleCategorySummaryDto;
import com.openhis.health.application.inpatient.financial.settle.dto.InpatientSettleChargeLineDto;
import com.openhis.health.application.inpatient.financial.settle.dto.InpatientSettlePaymentDetailDto;
import com.openhis.health.application.inpatient.financial.settle.dto.InpatientSettleQueueItemDto;
import com.openhis.health.application.inpatient.financial.settle.dto.SettledPaymentBriefDto;
import com.openhis.health.application.inpatient.financial.settle.mapper.InpatientSettleMapper;
import com.openhis.health.application.inpatient.financial.settle.request.ConfirmInpatientSettleRequest;
import com.openhis.health.application.inpatient.financial.settle.request.LoadInpatientSettlePageInitRequest;
import com.openhis.health.application.inpatient.financial.settle.request.LoadInpatientSettleSummaryRequest;
import com.openhis.health.application.inpatient.financial.settle.request.LoadSettledInpatientPaymentViewRequest;
import com.openhis.health.application.inpatient.financial.settle.request.PageInpatientSettleQueueRequest;
import com.openhis.health.application.inpatient.financial.settle.request.PreSettleInpatientChargesRequest;
import com.openhis.health.application.inpatient.financial.settle.request.QueryInpatientSettleChargesRequest;
import com.openhis.health.application.inpatient.financial.settle.response.ConfirmInpatientSettleResponse;
import com.openhis.health.application.inpatient.financial.settle.response.LoadInpatientSettlePageInitResponse;
import com.openhis.health.application.inpatient.financial.settle.response.LoadInpatientSettleSummaryResponse;
import com.openhis.health.application.inpatient.financial.settle.response.LoadSettledInpatientPaymentViewResponse;
import com.openhis.health.application.inpatient.financial.settle.response.PageInpatientSettleQueueResponse;
import com.openhis.health.application.inpatient.financial.settle.response.PreSettleInpatientChargesResponse;
import com.openhis.health.application.inpatient.financial.settle.response.QueryInpatientSettleChargesResponse;
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
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterDomainService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.application.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.domain.administration.invoice.command.CreateDraftInvoiceCommand;
import com.openhis.health.domain.administration.invoice.service.InvoiceDomainService;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.command.AbortPreSettlementBeforePaymentCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.CreatePaymentDraftFromPreSettleCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.PaymentDetailItemCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.RefundAdvanceDepositCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.SettleInpatientPaymentCommand;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationDomainService;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InpatientSettleConstants;
import com.openhis.health.domain.share.constants.InpatientSettleQueueTab;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.enums.PaymentChannel;
import com.openhis.health.domain.share.enums.PaymentKind;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院结算应用服务（AS-IP-FIN-003，IF-IP-STL-01～07）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>Gate/Pre：结算队列、就诊 IMP 门禁、待结算费用与草稿/金额一致性校验。</li>
 *   <li>Cmd：预结算、正式结算、发票草稿与出院财务全结（顺序与 AS-SDD §3 一致）。</li>
 *   <li>Asm：经 {@link InpatientSettleMapper} 装配队列/摘要/费用/结算响应。</li>
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
    private final CurrentOperatorResolver currentOperatorResolver;

    /** 分页窗口入站门控（B.10.11）。 */
    private final PageWindowGate pageWindowGate;

    /** 支付渠道入站门控。 */
    private final PaymentChannelGate paymentChannelGate;

    /** 患者与就诊一致性门控。 */
    private final PatientEncounterConsistencyGate patientEncounterConsistencyGate;

    /** 就诊读侧（队列、门禁与上下文）。 */
    private final EncounterQueryService encounterQueryService;
    private final EncounterContextFacade encounterContextFacade;

    /** 就诊写侧（出院财务全结）。 */
    private final EncounterDomainService encounterDomainService;

    /** 就诊账户读侧。 */
    private final AccountQueryService accountQueryService;

    /** 就诊账户写侧（出院多退少补退预交金）。 */
    private final AccountDomainService accountDomainService;

    /** 收费明细读侧。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 收费明细写侧（预结算/正式结算）。 */
    private final ChargeItemDomainService chargeItemDomainService;

    /** 支付流水读侧。 */
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;

    /** 支付流水写侧。 */
    private final PaymentReconciliationDomainService paymentReconciliationDomainService;

    /** 发票写侧。 */
    private final InvoiceDomainService invoiceDomainService;

    /** 合同名称读侧。 */
    private final ContractQueryService contractQueryService;

    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;

    /** 操作员展示名。 */
    private final PractitionerQueryService practitionerQueryService;

    /** 住院结算 DTO/Command 映射。 */
    private final InpatientSettleMapper inpatientSettleMapper;

    /** 预交金 Command 复用（出院退预交金结算收口）。 */
    private final InpatientPrepayMapper inpatientPrepayMapper;

    /** 住院队列展示回填。 */
    private final InpatientInfoQueueDisplayAssembler inpatientInfoQueueDisplayAssembler;

    /** 支付渠道展示名。 */
    private final PaymentChannelDisplayAssembler paymentChannelDisplayAssembler;

    /**
     * IF-IP-STL-01：结算页初始化。
     *
     * @param request 占位请求
     * @return 页签、呈现模式与支付渠道下拉
     * @throws BusinessException 初始化失败时抛出 EX-IP-STL-101
     */
    public LoadInpatientSettlePageInitResponse loadInpatientSettlePageInit(LoadInpatientSettlePageInitRequest request) {
        try {
            // Pre：装配队列页签与费用呈现模式选项，供前端 Tab 切换。
            List<OptionDto> queueTabs = new ArrayList<>();
            queueTabs.add(new OptionDto(1L, InpatientSettleQueueTab.PENDING_SETTLE, "有待结算"));
            queueTabs.add(new OptionDto(2L, InpatientSettleQueueTab.SETTLED, "已结算"));
            List<OptionDto> presentationModes = new ArrayList<>();
            presentationModes.add(new OptionDto(
                1L, InpatientSettleConstants.PRESENTATION_MODE_DETAIL, "明细清单"));
            presentationModes.add(new OptionDto(
                2L, InpatientSettleConstants.PRESENTATION_MODE_SUMMARY, "大类汇总"));
            // Pre：用户可选支付渠道与预交金/门诊收费口径对齐。
            List<OptionDto> paymentChannels = new ArrayList<>();
            long channelSort = 1L;
            for (PaymentChannel channel : PaymentChannel.values()) {
                if (!channel.isUserSelectable()) {
                    continue;
                }
                paymentChannels.add(new OptionDto(channelSort, channel.getCode(), channel.getDisplay()));
                channelSort = channelSort + 1L;
            }
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
                encounterContextFacade.pageInpatientSettleQueue(query);
            Set<Long> encounterIds = new LinkedHashSet<>();
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
            // Pre：批量加载登记账户、待结算合计、合同展示名与队列展示覆盖层。
            Map<Long, Account> accountByEncounterId =
                accountQueryService.mapRegistrationAccountsByEncounterIds(encounterIds);
            Map<Long, String> contractNoByEncounterId = new HashMap<>();
            for (Map.Entry<Long, Account> entry : accountByEncounterId.entrySet()) {
                if (entry.getValue() != null && entry.getValue().getContractNo() != null) {
                    contractNoByEncounterId.put(entry.getKey(), entry.getValue().getContractNo());
                }
            }
            Map<Long, BigDecimal> pendingByEncounterId =
                chargeItemQueryService.mapPendingSettlementTotalPriceByEncounterIds(encounterIds);
            Map<Long, BigDecimal> chargedOccupancyByEncounterId =
                chargeItemQueryService.mapInpatientChargedOccupancyTotalPriceByEncounterIds(encounterIds);
            Set<String> contractNos = new HashSet<>();
            for (String contractNo : contractNoByEncounterId.values()) {
                if (StringUtils.isNotBlank(contractNo)) {
                    contractNos.add(contractNo.trim());
                }
            }
            Map<String, String> contractNameByBusNo =
                contractQueryService.mapContractNameByBusNoIn(contractNos);
            Map<Long, InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay> displayOverlay =
                inpatientInfoQueueDisplayAssembler.buildOverlay(page.getContent(), contractNoByEncounterId);
            for (Map.Entry<Long, InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay> entry
                : displayOverlay.entrySet()) {
                InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay overlay = entry.getValue();
                if (overlay == null) {
                    continue;
                }
                Account account = accountByEncounterId.get(entry.getKey());
                if (account != null && StringUtils.isNotBlank(account.getContractNo())) {
                    String contractDisplay = contractNameByBusNo.get(account.getContractNo().trim());
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
            // Asm：逐行装配队列卡片（含展示余额 = 预交金净值 − 待结算/已结算占用）。
            List<InpatientSettleQueueItemDto> content = new ArrayList<>();
            for (InpatientEncounterContextQueryModel row : page.getContent()) {
                Account account = null;
                InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay rowOverlay = null;
                BigDecimal pendingSettlement = null;
                BigDecimal chargedOccupancy = null;
                LocalDateTime lastSettleTime = null;
                if (row != null && row.getEncounterId() != null) {
                    account = accountByEncounterId.get(row.getEncounterId());
                    rowOverlay = displayOverlay.get(row.getEncounterId());
                    pendingSettlement = pendingByEncounterId.get(row.getEncounterId());
                    chargedOccupancy = chargedOccupancyByEncounterId.get(row.getEncounterId());
                    lastSettleTime = lastSettleTimeByEncounterId.get(row.getEncounterId());
                }
                BigDecimal displayBalance = InpatientDisplayAccountBalanceAssembler.computeDisplayAccountBalance(
                    account, chargedOccupancy);
                content.add(inpatientSettleMapper.toInpatientSettleQueueItemDto(
                    row, account, rowOverlay, pendingSettlement, lastSettleTime, displayBalance));
            }
            return inpatientSettleMapper.toPageInpatientSettleQueueResponse(content, page);
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
        if (request == null || request.getEncounterId() == null || request.getPatientId() == null) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_102,
                "刷新并重新选择患者");
        }
        // Pre：须为住院类就诊。
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            request.getEncounterId(),
            EncounterClass.IMP.getCode(),
            InpatientSettleConstants.EX_IP_STL_102,
            "刷新并重新选择患者");
        // Pre：已财务结算完成的患者应走已结算查阅，本 IF 拒绝。
        if (EncounterStatus.COMPLETED.getCode().equals(encounter.getStatusEnum())) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_102,
                "该患者已结算，请切换至已结算页签查阅");
        }
        patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
            request.getPatientId(),
            encounter.getPatientId(),
            InpatientSettleConstants.EX_IP_STL_102,
            "患者标识与就诊不一致，请刷新并重新选择");
        InpatientEncounterContextQueryModel inpatientContext =
            encounterContextFacade.mustGetInpatientContextByEncounterId(request.getEncounterId());
        Account account = accountQueryService.getRegistrationAccount(
            request.getPatientId(),
            request.getEncounterId());
        if (account == null || account.getId() == null) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_103,
                "请先完成住院登记");
        }
        BigDecimal pendingTotal =
            chargeItemQueryService.sumPendingSettlementTotalPriceByEncounterId(request.getEncounterId());
        BigDecimal selectedTotal = pendingTotal;
        // Pre：前端传入勾选 ID 时按选中行求和，否则默认全选语义。
        if (request.getSelectedChargeItemIds() != null && !request.getSelectedChargeItemIds().isEmpty()) {
            ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
            loadCmd.setIds(request.getSelectedChargeItemIds());
            List<ChargeItem> selectedItems = chargeItemQueryService.loadChargeItemsByIds(loadCmd);
            selectedTotal = this.sumChargeItemAmount(selectedItems);
        }
        BigDecimal prepayNet = InpatientDisplayAccountBalanceAssembler.resolvePersonalCashPrepayNet(account);
        BigDecimal settledTotal =
            chargeItemQueryService.sumSettledTotalPriceByEncounterId(request.getEncounterId());
        // Pre：差额提示 = 预交金净值 − 已结算占用 − 本次选中待结（BR-IP-STL-013，含中途已结部分）。
        BigDecimal balanceHint = prepayNet.subtract(settledTotal).subtract(selectedTotal)
            .setScale(InpatientSettleConstants.SETTLE_AMOUNT_COMPARE_SCALE, RoundingMode.HALF_UP);
        BigDecimal chargedOccupancy =
            chargeItemQueryService.sumInpatientChargedOccupancyTotalPriceByEncounterId(request.getEncounterId());
        BigDecimal displayBalance = InpatientDisplayAccountBalanceAssembler.computeDisplayAccountBalance(
            account, chargedOccupancy);
        return inpatientSettleMapper.toLoadInpatientSettleSummaryResponse(
            inpatientContext,
            account,
            pendingTotal,
            selectedTotal,
            prepayNet,
            balanceHint,
            displayBalance);
    }

    /**
     * IF-IP-STL-04：结算专用费用查询。
     *
     * @param request 就诊、呈现模式与业务日区间
     * @return 待结算明细或大类汇总
     * @throws BusinessException 就诊无效或展示数据不完整时抛出 EX-IP-STL-102/104
     */
    public QueryInpatientSettleChargesResponse queryInpatientSettleCharges(QueryInpatientSettleChargesRequest request) {
        if (request == null
            || request.getEncounterId() == null
            || request.getPatientId() == null
            || StringUtils.isBlank(request.getPresentationMode())) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_102,
                "重新选择患者");
        }
        // Gate：呈现模式须为 DETAIL 或 SUMMARY，否则无法决定返回明细还是大类汇总。
        String mode = request.getPresentationMode().trim();
        if (!InpatientSettleConstants.PRESENTATION_MODE_DETAIL.equals(mode)
            && !InpatientSettleConstants.PRESENTATION_MODE_SUMMARY.equals(mode)) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_102,
                "费用呈现模式不合法");
        }
        // Pre：住院就诊与请求患者须一致，防止跨患者查费。
        EncounterDetailQueryModel encounterForGate = encounterQueryService.mustGetEncounterOfClass(
            request.getEncounterId(),
            EncounterClass.IMP.getCode(),
            InpatientSettleConstants.EX_IP_STL_102,
            "重新选择患者");
        patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
            request.getPatientId(),
            encounterForGate.getPatientId(),
            InpatientSettleConstants.EX_IP_STL_102,
            "患者标识与就诊不一致");
        // Pre：入院日用于默认 chargeDateFrom；待结算行排除挂号锚点（BR-IP-STL-012）。
        LocalDate admissionDate = null;
        if (encounterForGate.getStartTime() != null) {
            admissionDate = encounterForGate.getStartTime().toLocalDate();
        }
        ChargeItemQueryCommand queryCommand =
            inpatientSettleMapper.toChargeItemQueryCommand(request, admissionDate);
        List<ChargeItem> chargeItems =
            chargeItemQueryService.listChargeItemsByEncounterId(queryCommand);
        Map<String, ProductDefinitionDisplayDto> productByKey =
            this.buildProductMapByChargeItems(chargeItems);
        // Pre：大类汇总模式要求每条产品均有 categoryCode，否则无法按类目聚合。
        if (InpatientSettleConstants.PRESENTATION_MODE_SUMMARY.equals(mode)) {
            for (ChargeItem chargeItem : chargeItems) {
                if (chargeItem == null || chargeItem.getProductTable() == null || chargeItem.getProductId() == null) {
                    continue;
                }
                String key = ProductDefinitionKeyUtil.buildCompositeKey(
                    chargeItem.getProductTable(), chargeItem.getProductId());
                ProductDefinitionDisplayDto product = productByKey.get(key);
                if (product == null || StringUtils.isBlank(product.getCategoryCode())) {
                    throw new BusinessException(
                        InpatientSettleConstants.EX_IP_STL_104,
                        "联系管理员维护产品类目");
                }
            }
        }
        List<InpatientSettleChargeLineDto> lines = inpatientSettleMapper.toInpatientSettleChargeLineDtoList(
            chargeItems, productByKey, true);
        BigDecimal pendingTotal = this.sumChargeItemAmount(chargeItems);
        List<InpatientSettleCategorySummaryDto> categories = null;
        List<InpatientSettleChargeLineDto> responseLines = lines;
        // Asm：SUMMARY 仅返回类目汇总；DETAIL 返回可勾选明细行。
        if (InpatientSettleConstants.PRESENTATION_MODE_SUMMARY.equals(mode)) {
            categories = inpatientSettleMapper.toInpatientSettleCategorySummaryDtoList(lines);
            responseLines = null;
        }
        return inpatientSettleMapper.toQueryInpatientSettleChargesResponse(
            request.getEncounterId(),
            mode,
            responseLines,
            categories,
            pendingTotal,
            true);
    }

    /**
     * IF-IP-STL-05：住院预结算。
     *
     * @param request 就诊、患者与所选待结算明细
     * @return 支付草稿 ID 与应收合计
     * @throws BusinessException 参数/状态/草稿冲突时抛出 EX-IP-STL-001/102/105
     */
    public PreSettleInpatientChargesResponse preSettleInpatientCharges(PreSettleInpatientChargesRequest request) {
        // Gate：就诊、患者与所选收费明细均须非空，否则无法形成有效预结算草稿。
        if (request == null
            || request.getEncounterId() == null
            || request.getPatientId() == null
            || request.getChargeItemIds() == null
            || request.getChargeItemIds().isEmpty()) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_001,
                "请选择待结算费用");
        }
        Long operatorId = currentOperatorResolver.requireOperatorId(
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
        // Pre：加载所选行并校验归属就诊且均为待结算态（BR-IP-STL-002）。
        ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
        loadCmd.setIds(request.getChargeItemIds());
        List<ChargeItem> chargeItems = chargeItemQueryService.loadChargeItemsByIds(loadCmd);
        this.assertChargeItemsForInpatientSettle(chargeItems, request.getChargeItemIds(), request.getEncounterId());
        BigDecimal receivableTotal = this.sumChargeItemAmount(chargeItems);
        if (receivableTotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_001,
                "请选择待结算费用");
        }
        // Pre：找出同就诊下未完成的 INPATIENT_CLINIC 草稿，后续逐条作废（BR-IP-STL-010）。
        List<PaymentReconciliationQueryModel> encounterPayments =
            paymentReconciliationQueryService.findPaymentsByEncounter(request.getEncounterId());
        List<PaymentReconciliationQueryModel> draftPayments = new ArrayList<>();
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
            for (PaymentReconciliationQueryModel draft : draftPayments) {
                AbortPreSettlementBeforePaymentCommand abortCmd = new AbortPreSettlementBeforePaymentCommand();
                abortCmd.setPaymentId(draft.getId());
                paymentReconciliationDomainService.abortPreSettlementBeforePayment(abortCmd);
            }
            BatchChargeItemIdsCommand preSettleCmd =
                inpatientSettleMapper.toBatchChargeItemIdsCommand(request.getChargeItemIds(), operatorId);
            // Cmd：收费项预结算校验与事件（状态仍 PENDING_SETTLEMENT）。
            chargeItemDomainService.preSettleInpatientChargeItems(preSettleCmd);
            Account account = accountQueryService.getRegistrationAccount(
                request.getPatientId(),
                request.getEncounterId());
            String contractNo = null;
            if (account != null) {
                contractNo = account.getContractNo();
            }
            List<PaymentDetailItemCommand> draftDetailCommands = new ArrayList<>();
            PaymentDetailItemCommand placeholder = new PaymentDetailItemCommand();
            placeholder.setPayEnum(PaymentChannel.CASHIER.getCode());
            placeholder.setAmount(receivableTotal);
            placeholder.setChargeAmount(receivableTotal);
            placeholder.setResultEnum(PaymentStatus.DRAFT.getCode());
            if (account != null) {
                placeholder.setAccountId(account.getId());
            }
            draftDetailCommands.add(placeholder);
            CreatePaymentDraftFromPreSettleCommand paymentDraftCommand =
                inpatientSettleMapper.toCreatePaymentDraftFromPreSettleCommand(
                    request,
                    receivableTotal,
                    Collections.emptyList(),
                    operatorId,
                    this.joinIds(request.getChargeItemIds()),
                    contractNo);
            paymentDraftCommand.setPaymentDetails(draftDetailCommands);
            // Cmd：落 INPATIENT_CLINIC 支付草稿（DRAFT）及占位明细，供 IF-IP-STL-06 正式结算消费。
            Long paymentDraftId =
                paymentReconciliationDomainService.createPaymentDraftFromPreSettle(paymentDraftCommand);
            return inpatientSettleMapper.toPreSettleInpatientChargesResponse(paymentDraftId, receivableTotal);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_105,
                "重新预结算或作废草稿后再试");
        }
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
            || request.getChargeItemIds().isEmpty()
            || request.getPaymentDetails() == null
            || request.getPaymentDetails().isEmpty()) {
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
        Long operatorId = currentOperatorResolver.requireOperatorId(
            InpatientSettleConstants.EX_IP_STL_106,
            "重新预结算并核对");
        // Pre：草稿须为 INPATIENT_CLINIC + DRAFT，且就诊锚点与本次提交一致。
        PaymentReconciliationQueryModel payment =
            paymentReconciliationQueryService.mustGetPayment(request.getPaymentDraftId());
        if (payment.getKindEnum() != PaymentKind.INPATIENT_CLINIC
            || payment.getStatusEnum() != PaymentStatus.DRAFT) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_106,
                "支付草稿已失效，请重新预结算");
        }
        if (!request.getEncounterId().equals(payment.getEncounterId())) {
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
        // Pre：拟结项须仍属本就诊且为待结算态，禁止重复结算已结行。
        ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
        loadCmd.setIds(request.getChargeItemIds());
        List<ChargeItem> chargeItems = chargeItemQueryService.loadChargeItemsByIds(loadCmd);
        this.assertChargeItemsForInpatientSettle(chargeItems, request.getChargeItemIds(), request.getEncounterId());
        // Pre：与历史成功/退款中流水挂载 chargeItem 求交，命中则拒绝（BR-IP-STL-007）。
        List<PaymentReconciliationQueryModel> overlapping =
            paymentReconciliationQueryService.findPaySuccessOrRefundingOverlappingChargeItems(
                request.getEncounterId(),
                request.getChargeItemIds());
        if (overlapping != null && !overlapping.isEmpty()) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_106,
                "所选费用已存在成功结算流水");
        }
        BigDecimal expectedTotal = this.sumChargeItemAmount(chargeItems);
        BigDecimal submitTotal = this.sumPaymentDetailAmount(request.getPaymentDetails());
        // Pre：渠道实付合计须与选中费用 total_price 之和一致（BR-IP-STL-006）。
        if (expectedTotal.setScale(InpatientSettleConstants.SETTLE_AMOUNT_COMPARE_SCALE, RoundingMode.HALF_UP)
            .compareTo(submitTotal.setScale(InpatientSettleConstants.SETTLE_AMOUNT_COMPARE_SCALE, RoundingMode.HALF_UP))
            != 0) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_106,
                "渠道实付合计与应收不一致");
        }
        // Pre：加载就诊下全部待结算行（排除挂号锚点），用于出院全结/中途结算判定。
        ChargeItemQueryCommand pendingQuery = new ChargeItemQueryCommand();
        pendingQuery.setEncounterId(request.getEncounterId());
        List<String> pendingStatusIn = new ArrayList<>();
        pendingStatusIn.add(ChargeItemStatus.PENDING_SETTLEMENT.getCode());
        pendingQuery.setStatusEnumIn(pendingStatusIn);
        pendingQuery.setExcludedServiceTable(TableNameConstants.ADM_HEALTHCARE_SERVICE);
        List<ChargeItem> allPending =
            chargeItemQueryService.listChargeItemsByEncounterId(pendingQuery);
        Set<Long> allPendingIds = new HashSet<>();
        for (ChargeItem pendingItem : allPending) {
            if (pendingItem != null && pendingItem.getId() != null) {
                allPendingIds.add(pendingItem.getId());
            }
        }
        Set<Long> selectedIds = new HashSet<>(request.getChargeItemIds());
        boolean dischargeFullSettle = false;
        // Pre：PENDING 态须全选待结算才允许出院全结；非 PENDING 态全选则拒绝（BR-IP-STL-003/007/008）。
        if (EncounterStatus.PENDING.getCode().equals(encounter.getStatusEnum())) {
            if (selectedIds.size() == allPendingIds.size() && selectedIds.containsAll(allPendingIds)) {
                dischargeFullSettle = true;
            } else {
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_STL_107,
                    "请全选待结算费用或改为中途结算");
            }
        } else {
            if (selectedIds.size() == allPendingIds.size() && selectedIds.containsAll(allPendingIds)) {
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_STL_108,
                    "请先完成护士出院或改为中途结算");
            }
        }
        patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
            request.getPatientId(),
            encounter.getPatientId(),
            InpatientSettleConstants.EX_IP_STL_106,
            "患者标识与就诊不一致");
        // Pre：本批结算推进为 SETTLED 前的已结算占用，供出院多退计算（避免同事务内 SUM 未 flush 漏计本批）。
        BigDecimal settledBeforeCurrentBatch =
            chargeItemQueryService.sumSettledTotalPriceByEncounterId(request.getEncounterId());
        try {
            SettleInpatientPaymentCommand settleCommand =
                inpatientSettleMapper.toSettleInpatientPaymentCommand(request, operatorId);
            // Cmd ①：支付草稿推进为 SUCCESS。
            Long paymentId = paymentReconciliationDomainService.settleInpatientPayment(settleCommand);
            BatchChargeItemIdsCommand settleChargeCmd =
                inpatientSettleMapper.toBatchChargeItemIdsCommand(request.getChargeItemIds(), operatorId);
            // Cmd ②：选中收费项推进为 SETTLED。
            chargeItemDomainService.settleInpatientChargeItems(settleChargeCmd);
            PaymentReconciliationQueryModel settledPayment =
                paymentReconciliationQueryService.mustGetPayment(paymentId);
            CreateDraftInvoiceCommand invoiceCommand =
                inpatientSettleMapper.toCreateDraftInvoiceCommand(settledPayment, paymentId);
            invoiceCommand.setOperatorId(operatorId);
            // Cmd ③：创建发票草稿（BR-IP-STL-008）。
            Long invoiceDraftId = invoiceDomainService.createDraftInvoiceAfterPayment(invoiceCommand);
            Long advanceRefundPaymentId = null;
            if (dischargeFullSettle) {
                // Cmd ④：出院全结多退——仅退「预交金净值 − 已结算占用」盈余，非全额预交金（BR-IP-STL-014）。
                advanceRefundPaymentId = this.refundDischargeAdvanceDepositSurplus(
                    request.getEncounterId(),
                    request.getPatientId(),
                    operatorId,
                    settledBeforeCurrentBatch,
                    expectedTotal);
                LocalDateTime settlementTime = settleCommand.getBillDate();
                CompleteInpatientFinancialSettlementCommand completeCmd =
                    inpatientSettleMapper.toCompleteInpatientFinancialSettlementCommand(
                        request.getEncounterId(),
                        settlementTime,
                        operatorId);
                // Cmd ⑤：出院全结时就诊 PENDING→COMPLETED（条件执行）。
                encounterDomainService.completeInpatientFinancialSettlement(completeCmd);
            }
            return inpatientSettleMapper.toConfirmInpatientSettleResponse(
                paymentId, invoiceDraftId, dischargeFullSettle, advanceRefundPaymentId);
        } catch (BusinessException ex) {
            throw ex;
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
        if (request == null || request.getEncounterId() == null || request.getPatientId() == null) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_102,
                "重新选择");
        }
        // Pre：须为住院类就诊且已进入 COMPLETED（已结算队列入口，BR-IP-STL-009）。
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            request.getEncounterId(),
            EncounterClass.IMP.getCode(),
            InpatientSettleConstants.EX_IP_STL_102,
            "重新选择");
        if (!EncounterStatus.COMPLETED.getCode().equals(encounter.getStatusEnum())) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_102,
                "请从已结算页签选择患者");
        }
        patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
            request.getPatientId(),
            encounter.getPatientId(),
            InpatientSettleConstants.EX_IP_STL_102,
            "患者标识与就诊不一致");
        List<PaymentReconciliationQueryModel> allPayments =
            paymentReconciliationQueryService.findPaymentsByEncounter(request.getEncounterId());
        // Pre：筛选 INPATIENT_CLINIC + SUCCESS 流水，按 bill_date 降序供切换历史结算单。
        List<PaymentReconciliationQueryModel> successPayments = new ArrayList<>();
        for (PaymentReconciliationQueryModel payment : allPayments) {
            if (payment == null) {
                continue;
            }
            if (payment.getKindEnum() != PaymentKind.INPATIENT_CLINIC) {
                continue;
            }
            if (payment.getStatusEnum() != PaymentStatus.SUCCESS) {
                continue;
            }
            successPayments.add(payment);
        }
        if (successPayments.isEmpty()) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_110,
                "无历史结算单");
        }
        successPayments.sort(
            Comparator.comparing(
                PaymentReconciliationQueryModel::getBillDate,
                Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(
                    PaymentReconciliationQueryModel::getId,
                    Comparator.nullsLast(Comparator.reverseOrder())));
        LinkedHashSet<Long> paymentIds = new LinkedHashSet<>();
        for (PaymentReconciliationQueryModel payment : successPayments) {
            if (payment.getId() != null) {
                paymentIds.add(payment.getId());
            }
        }
        Map<Long, String> payMethodTextByPaymentId =
            paymentReconciliationQueryService.mapFirstPayEnumByPaymentIds(paymentIds);
        Map<Long, String> payMethodDisplayByPaymentId = new HashMap<>();
        for (Map.Entry<Long, String> entry : payMethodTextByPaymentId.entrySet()) {
            String display = paymentChannelDisplayAssembler.resolvePayChannelDisplay(entry.getValue(), null);
            payMethodDisplayByPaymentId.put(entry.getKey(), display);
        }
        List<SettledPaymentBriefDto> paymentBriefs =
            inpatientSettleMapper.toSettledPaymentBriefDtoList(successPayments, payMethodDisplayByPaymentId);
        Long activePaymentId = request.getPaymentId();
        if (activePaymentId == null) {
            activePaymentId = successPayments.get(0).getId();
        }
        PaymentReconciliationQueryModel activePayment =
            paymentReconciliationQueryService.mustGetPayment(activePaymentId);
        List<Long> chargeItemIdList = this.parseIds(activePayment.getChargeItemIds());
        ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
        loadCmd.setIds(chargeItemIdList);
        List<ChargeItem> chargeItems = chargeItemQueryService.loadChargeItemsByIds(loadCmd);
        Map<String, ProductDefinitionDisplayDto> productByKey =
            this.buildProductMapByChargeItems(chargeItems);
        String mode = request.getPresentationMode();
        if (StringUtils.isBlank(mode)) {
            mode = InpatientSettleConstants.PRESENTATION_MODE_DETAIL;
        }
        List<InpatientSettleChargeLineDto> lines = inpatientSettleMapper.toInpatientSettleChargeLineDtoList(
            chargeItems, productByKey, false);
        List<InpatientSettleCategorySummaryDto> categories = null;
        List<InpatientSettleChargeLineDto> responseLines = lines;
        if (InpatientSettleConstants.PRESENTATION_MODE_SUMMARY.equals(mode.trim())) {
            categories = inpatientSettleMapper.toInpatientSettleCategorySummaryDtoList(lines);
            responseLines = null;
        }
        Map<Long, String> operatorNameById = practitionerQueryService.mapDisplayNameByEntererIds(
            Collections.singleton(activePayment.getEntererId()));
        String operatorName = null;
        if (activePayment.getEntererId() != null && operatorNameById != null) {
            operatorName = operatorNameById.get(activePayment.getEntererId());
        }
        String payMethodText = payMethodDisplayByPaymentId.get(activePaymentId);
        return inpatientSettleMapper.toLoadSettledInpatientPaymentViewResponse(
            request.getEncounterId(),
            paymentBriefs,
            activePaymentId,
            activePayment.getBillDate(),
            operatorName,
            activePayment.getDisplayAmount(),
            payMethodText,
            responseLines,
            categories);
    }

    /**
     * 批量加载收费行关联的产品读模型。
     *
     * @param chargeItems 收费明细列表
     * @return 复合键 → 产品展示 DTO
     */
    private Map<String, ProductDefinitionDisplayDto> buildProductMapByChargeItems(List<ChargeItem> chargeItems) {
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        if (chargeItems != null) {
            for (ChargeItem chargeItem : chargeItems) {
                if (chargeItem == null
                    || chargeItem.getProductTable() == null
                    || chargeItem.getProductId() == null) {
                    continue;
                }
                ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
                lookup.setProductTableName(chargeItem.getProductTable());
                lookup.setProductDefinitionId(chargeItem.getProductId());
                lookups.add(lookup);
            }
        }
        if (lookups.isEmpty()) {
            return new HashMap<>();
        }
        return productDefinitionBatchLoader.loadAsMap(lookups);
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
        for (Long requestedId : requestedIds) {
            ChargeItem chargeItem = loadedById.get(requestedId);
            if (chargeItem == null) {
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_STL_001,
                    "请选择待结算费用");
            }
            if (!encounterId.equals(chargeItem.getEncounterId())) {
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_STL_001,
                    "所选费用不属于当前就诊");
            }
            if (!ChargeItemStatus.PENDING_SETTLEMENT.getCode().equals(chargeItem.getStatusEnum())) {
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_STL_001,
                    "所选费用含非待结算项");
            }
        }
    }

    /**
     * 出院全结多退：预交金净值大于已结算占用时，落退预交金流水并扣减账户余额。
     *
     * <p>退费额 = 预交金净值 −（结算前已结占用 + 本批刚结占用）；只退盈余，不退全额预交金。
     * 例：预交 1000、本批费用 50 → 退 950，账户净值 50，展示余额 50−50=0。</p>
     *
     * @param encounterId                 就诊 ID
     * @param patientId                   患者 ID
     * @param operatorId                  操作员 ID
     * @param settledBeforeCurrentBatch   本批 {@code settleInpatientChargeItems} 执行前已结算占用合计
     * @param currentBatchSettledTotal    本批刚推进为 {@code SETTLED} 的费用合计
     * @return 退预交金支付单 ID；无需退费时 null
     */
    private Long refundDischargeAdvanceDepositSurplus(
        Long encounterId,
        Long patientId,
        Long operatorId,
        BigDecimal settledBeforeCurrentBatch,
        BigDecimal currentBatchSettledTotal) {
        Account account = accountQueryService.getRegistrationAccount(patientId, encounterId);
        if (account == null || account.getId() == null) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_103,
                "请先完成住院登记");
        }
        BigDecimal prepayNet = InpatientDisplayAccountBalanceAssembler.resolvePersonalCashPrepayNet(account);
        BigDecimal settledOccupancy = settledBeforeCurrentBatch;
        if (settledOccupancy == null) {
            settledOccupancy = BigDecimal.ZERO;
        }
        if (currentBatchSettledTotal != null) {
            settledOccupancy = settledOccupancy.add(currentBatchSettledTotal);
        }
        // 多退少补之「多退」：仅退还预交金净值超出全部已结算占用的部分。
        BigDecimal refundAmount = prepayNet.subtract(settledOccupancy)
            .setScale(InpatientSettleConstants.SETTLE_AMOUNT_COMPARE_SCALE, RoundingMode.HALF_UP);
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        RefundAdvanceDepositCommand refundCommand = inpatientSettleMapper.toDischargeDepositRefundCommand(
            encounterId,
            patientId,
            account.getId(),
            refundAmount,
            operatorId);
        // Cmd：落退预交金 DRAFT（HOSPITAL_DEPOSIT / UN_PAY），与预交金工作台口径一致。
        Long refundPaymentId = paymentReconciliationDomainService.refundAdvanceDeposit(refundCommand);
        SettleInpatientPaymentCommand refundSettleCommand =
            inpatientPrepayMapper.toSettleInpatientPaymentCommand(refundPaymentId);
        paymentReconciliationDomainService.settleInpatientPayment(refundSettleCommand);
        AdjustAdvanceDepositBalanceCommand balanceCmd = inpatientPrepayMapper.toRefundBalanceAdjustCommand(
            patientId,
            encounterId,
            refundAmount,
            operatorId);
        accountDomainService.adjustAdvanceDepositBalance(balanceCmd);
        return refundPaymentId;
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
}
