package com.openhis.health.application.outpatient.doctorstation.refundapplication.service;

import com.openhis.health.module.common.validator.CurrentPractitionerResolver;
import com.openhis.health.domain.share.constants.DoctorStationRefundApplicationConstants;
import com.openhis.health.application.outpatient.doctorstation.refundapplication.dto.OutpatientRefundApplicationPaymentSummaryDto;
import com.openhis.health.application.outpatient.doctorstation.refundapplication.dto.RefundableChargeLineItemDto;
import com.openhis.health.application.outpatient.doctorstation.refundapplication.dto.RefundApplicationLineItemDto;
import com.openhis.health.application.outpatient.doctorstation.refundapplication.mapper.DoctorStationRefundApplicationMapper;
import com.openhis.health.application.outpatient.doctorstation.refundapplication.request.ListRefundableChargeLinesForEncounterRequest;
import com.openhis.health.application.outpatient.doctorstation.refundapplication.request.SubmitOutpatientRefundApplicationRequest;
import com.openhis.health.application.outpatient.doctorstation.refundapplication.request.VerifyConsumableExecutionBeforeRefundRequest;
import com.openhis.health.application.outpatient.doctorstation.refundapplication.response.ListRefundableChargeLinesForEncounterResponse;
import com.openhis.health.application.outpatient.doctorstation.refundapplication.response.SubmitOutpatientRefundApplicationResponse;
import com.openhis.health.application.outpatient.doctorstation.refundapplication.response.VerifyConsumableExecutionBeforeRefundResponse;
import com.openhis.health.domain.administration.chargeitem.command.CreateChargeItemsLinkingRegeneratedAnchorsCommand;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.OutpatientChargeConstants;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.ChargeItemIdsCollectionQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseDomainService;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.command.CloneMedicationRequestForPreservedChargeRegenerationCommand;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestDomainService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.utils.GatewayIdParseUtil;
import com.openhis.health.domain.workflow.devicedispense.queries.model.DeviceDispenseListModel;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseDomainService;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.openhis.health.domain.workflow.devicerequest.command.CloneDeviceRequestForPreservedChargeRegenerationCommand;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestDomainService;
import com.openhis.health.domain.workflow.servicerequest.command.CloneServiceRequestForPreservedChargeRegenerationCommand;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestQueryModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestDomainService;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 门诊医生站「申请退费」应用服务编排（IF-DRA-01～03）。
 * <p>职责边界：</p>
 * <ul>
 *   <li>{@link EncounterQueryService}：就诊存在性门禁（Pre）。</li>
 *   <li>{@link PaymentReconciliationQueryService}：支付流水只读与挂载收费项快照解析（Pre）。</li>
 *   <li>{@link ChargeItemQueryService}：收费明细只读装载（Pre）。</li>
 *   <li>{@link DeviceDispenseQueryService}：耗材发放只读快照（IF-DRA-02）。</li>
 *   <li>{@link MedicationDispenseQueryService}：药品发放只读快照（IF-DRA-03）。</li>
 *   <li>{@link ServiceRequestQueryService}：诊疗申请只读强校验（IF-DRA-03）。</li>
 *   <li>{@link ChargeItemDomainService}：保留链新行落库与拟退行过程态迁移（Cmd）。</li>
 *   <li>{@link MedicationRequestDomainService} / {@link MedicationDispenseDomainService}：拟退药品分支（Cmd）。</li>
 *   <li>{@link DeviceRequestDomainService} / {@link DeviceDispenseDomainService}：拟退耗材分支（Cmd）。</li>
 *   <li>{@link ServiceRequestDomainService}：拟退诊疗分支（Cmd）。</li>
 *   <li>{@link DoctorStationRefundApplicationMapper}：Request/Response 与领域命令字段级装配（Asm）。</li>
 *   <li>{@link CurrentOperatorResolver}：写路径操作人主键门控（B.6.1）。</li>
 * </ul>
 * <p>接入说明：由 {@code AppServiceControllerRegistrar} 动态注册为
 * {@code POST /api/app/doctorStationRefundApplication/{methodName}}；不另写 {@code RestController}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorStationRefundApplicationAppService {

    /** 就诊只读查询（写前门禁）。 */
    private final EncounterQueryService encounterQueryService;
    /** 支付流水只读查询（可退候选解析）。 */
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    /** 收费明细只读查询（候选行与提交前事实装载）。 */
    private final ChargeItemQueryService chargeItemQueryService;
    /** 收费定价定义只读查询（可退列表项目名称回落）。 */
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    /** 产品定义读模型查询（可退列表项目名称优先口径）。 */
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    /** 耗材发放只读查询（IF-DRA-02 / IF-DRA-03 发放态判断）。 */
    private final DeviceDispenseQueryService deviceDispenseQueryService;
    /** 药品发放只读查询（IF-DRA-03 发放态判断）。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    /** 诊疗申请只读查询（IF-DRA-03 写前强校验）。 */
    private final ServiceRequestQueryService serviceRequestQueryService;
    /** 收费明细写侧（保留链与拟退过程态）。 */
    private final ChargeItemDomainService chargeItemDomainService;
    /** 药品申请写侧（退药/重开/保留链克隆）。 */
    private final MedicationRequestDomainService medicationRequestDomainService;
    /** 药品发放写侧（作废/待退药）。 */
    private final MedicationDispenseDomainService medicationDispenseDomainService;
    /** 耗材申请写侧（退耗/保留链克隆）。 */
    private final DeviceRequestDomainService deviceRequestDomainService;
    /** 耗材发放写侧（待退/作废未发）。 */
    private final DeviceDispenseDomainService deviceDispenseDomainService;
    /** 诊疗申请写侧（取消/停止）。 */
    private final ServiceRequestDomainService serviceRequestDomainService;
    /** 申请退费装配器。 */
    private final DoctorStationRefundApplicationMapper doctorStationRefundApplicationMapper;
    /** 写路径操作人主键门控（B.6.1）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /**
     * IF-DRA-01：按就诊加载可申请退费账单行。
     *
     * <p>不包含 {@code service_table} 为 {@link TableNameConstants#ADM_HEALTHCARE_SERVICE} 的明细（挂号费等），
     * 挂号费退费走 {@code AS-OP-FIN-001} 退号链路，与门诊收银 IF-OPC-03 排除口径一致。</p>
     *
     * @param request 就诊键
     * @return 可退候选行（按支付分组排序）
     * @throws BusinessException 就诊不存在或参数非法
     */
    public ListRefundableChargeLinesForEncounterResponse listRefundableChargeLinesForEncounter(
        ListRefundableChargeLinesForEncounterRequest request) {
        // Gate：就诊键必须显式传入，否则无法定义医生站退费上下文。
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        Long encounterId = request.getEncounterId();
        // Pre：就诊必须存在，否则前端无法继续退费申请（EX-DRA-101）。
        this.encounterQueryService.mustGetEncounter(encounterId);
        // Pre：列出就诊下全部支付流水，再在内存筛选成功与部分退语义（BR-DRA-001）。
        List<PaymentReconciliationQueryModel> payments = this.paymentReconciliationQueryService.findPaymentsByEncounter(encounterId);
        List<PaymentReconciliationQueryModel> eligiblePayments = new ArrayList<>();
        for (PaymentReconciliationQueryModel payment : payments) {
            // Pre：仅成功与部分退款态支付可继续解析挂载收费项（BR-DRA-001）。
            if (payment.getStatusEnum() == null) {
                continue;
            }
            if (PaymentStatus.SUCCESS.equals(payment.getStatusEnum())
                || PaymentStatus.PARTIAL_REFUNDED.equals(payment.getStatusEnum())) {
                eligiblePayments.add(payment);
            }
        }
        // Pre：无命中支付时直接返回空表，避免无意义批量查询（AS 主路径分支）。
        if (eligiblePayments.isEmpty()) {
            return this.doctorStationRefundApplicationMapper.toListRefundableChargeLinesForEncounterResponse(Collections.emptyList());
        }
        List<Long> paymentIds = new ArrayList<>();
        for (PaymentReconciliationQueryModel payment : eligiblePayments) {
            paymentIds.add(payment.getId());
        }
        // Pre：批量解析各支付挂载的收费项主键串，得到候选 id 全集。
        List<ChargeItemIdsCollectionQueryModel> idCollections =
            this.paymentReconciliationQueryService.getChargeItemIdsByPayments(paymentIds);
        Set<Long> allChargeItemIds = new LinkedHashSet<>();
        Map<Long, PaymentReconciliationQueryModel> paymentById = new HashMap<>();
        for (PaymentReconciliationQueryModel payment : eligiblePayments) {
            paymentById.put(payment.getId(), payment);
        }
        for (ChargeItemIdsCollectionQueryModel collection : idCollections) {
            if (collection.getChargeItemIds() == null) {
                continue;
            }
            for (Long chargeItemId : collection.getChargeItemIds()) {
                if (chargeItemId != null) {
                    allChargeItemIds.add(chargeItemId);
                }
            }
        }
        // Pre：无挂载收费项时返回空表。
        if (allChargeItemIds.isEmpty()) {
            return this.doctorStationRefundApplicationMapper.toListRefundableChargeLinesForEncounterResponse(Collections.emptyList());
        }
        ChargeItemQueryCommand chargeQuery = new ChargeItemQueryCommand();
        chargeQuery.setIds(new ArrayList<>(allChargeItemIds));
        // Pre：批量加载收费明细实体，避免在后续装配中逐条查询（A.9.1）。
        List<ChargeItem> loaded = this.chargeItemQueryService.loadChargeItemsByIds(chargeQuery);
        List<ChargeItem> settledForEncounter = new ArrayList<>();
        for (ChargeItem row : loaded) {
            // Pre：仅保留同就诊且已结算行，作为可申请退费事实（BR-DRA-008 读侧口径）。
            if (!encounterId.equals(row.getEncounterId())) {
                continue;
            }
            if (!ChargeItemStatus.SETTLED.getCode().equals(row.getStatusEnum())) {
                continue;
            }
            // Pre：挂号费锚点不在医生站申请退费范围，须走退号（与 IF-OPC-03 排除 adm_healthcare_service 一致）。
            if (TableNameConstants.ADM_HEALTHCARE_SERVICE.equals(row.getServiceTable())) {
                continue;
            }
            settledForEncounter.add(row);
        }
        // Pre：排除半退保留链已再生替代的源 SETTLED 行，避免可退列表同药双行（BR-DRA-020 / BR-REF-020）。
        settledForEncounter = this.chargeItemQueryService.filterOutSupersededSettledLines(encounterId, settledForEncounter);
        // Pre：批量解析项目名称（与门诊收费 IF-OPC-03 同口径：目录 product_name 优先，其次定价 charge_name）。
        Map<Long, ChargeItemDefinition> definitionMap = this.buildChargeDefinitionMapByDefinitionId(settledForEncounter);
        Map<String, ProductDefinitionDisplayDto> productMap =
            this.buildProductDefinitionMapByChargeItems(settledForEncounter, definitionMap);
        // Asm：将每条候选行装配为对外 DTO（枚举展示由 Mapper 内 EnumUtil 完成，BR-DRA-002）。
        List<RefundableChargeLineItemDto> lines = new ArrayList<>();
        for (ChargeItem row : settledForEncounter) {
            PaymentReconciliationQueryModel payment = this.resolvePaymentForChargeItem(row.getId(), idCollections, paymentById);
            if (payment == null) {
                continue;
            }
            String itemDisplayName = this.resolveRefundableChargeLineItemDisplayName(row, definitionMap, productMap);
            lines.add(this.doctorStationRefundApplicationMapper.toRefundableChargeLineItemDto(row, payment, itemDisplayName));
        }
        // Asm：按支付与收费行主键排序后封装响应（BR-DRA-002）。
        return this.doctorStationRefundApplicationMapper.toListRefundableChargeLinesForEncounterResponse(lines);
    }

    /**
     * IF-DRA-02：申请前校验耗材执行。
     *
     * @param request 就诊键与勾选收费项
     * @return 是否允许继续申请
     * @throws BusinessException 就诊或收费项非法
     */
    public VerifyConsumableExecutionBeforeRefundResponse verifyConsumableExecutionBeforeRefund(
        VerifyConsumableExecutionBeforeRefundRequest request) {
        // Gate：就诊与勾选集合必须齐全，否则门禁无法定义批次范围。
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        GatewayIdParseUtil.requireNonEmptyLongIds(request.getChargeItemIds(), "耗材执行校验：收费项标识列表不能为空");
        Long encounterId = request.getEncounterId();
        // Pre：就诊必须存在，避免伪造就诊键绕过展示范围。
        this.encounterQueryService.mustGetEncounter(encounterId);
        ChargeItemQueryCommand cmd = new ChargeItemQueryCommand();
        cmd.setIds(request.getChargeItemIds());
        // Pre：批量加载勾选收费行，用于后续就诊一致性与耗材路径筛选。
        List<ChargeItem> rows = this.chargeItemQueryService.loadChargeItemsByIds(cmd);
        Map<Long, ChargeItem> rowById = new HashMap<>();
        for (ChargeItem row : rows) {
            rowById.put(row.getId(), row);
        }
        for (Long id : request.getChargeItemIds()) {
            // Pre：每条勾选 id 必须命中实体且属于当前就诊，防止跨就诊恶意勾选。
            ChargeItem row = rowById.get(id);
            if (row == null) {
                throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_NOT_FOUND, "收费明细不存在");
            }
            if (!encounterId.equals(row.getEncounterId())) {
                throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_RULE_CONFLICT, "收费明细不属于当前就诊");
            }
            // Pre：挂号费不可走耗材执行门禁与申请退费，须退号办理。
            if (TableNameConstants.ADM_HEALTHCARE_SERVICE.equals(row.getServiceTable())) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_ITEM_RULE_CONFLICT,
                    "挂号费请通过退号办理，不可申请退费"
                );
            }
        }
        List<Long> deviceRequestIds = new ArrayList<>();
        for (Long id : request.getChargeItemIds()) {
            ChargeItem row = rowById.get(id);
            if (row == null) {
                continue;
            }
            // Pre：耗材路径含请求锚点与发放锚点（与医嘱列表收费汇总口径一致）。
            Long deviceRequestId = this.resolveDeviceRequestId(row);
            if (deviceRequestId != null) {
                deviceRequestIds.add(deviceRequestId);
            }
        }
        // Pre：无耗材路径时直接返回允许，避免无意义发放查询。
        if (deviceRequestIds.isEmpty()) {
            return this.doctorStationRefundApplicationMapper.toVerifyConsumableExecutionBeforeRefundResponse(true, null);
        }
        Set<Long> uniqueDeviceReqIds = new LinkedHashSet<>(deviceRequestIds);
        List<Long> requestIdList = new ArrayList<>(uniqueDeviceReqIds);
        // Pre：批量加载耗材发放快照，用于判断是否已执行（EX-DRA-201）。
        List<DeviceDispenseListModel> dispenses = this.deviceDispenseQueryService.selectByRequestIdList(requestIdList);
        for (DeviceDispenseListModel dispense : dispenses) {
            // Pre：任一发放为已完成或部分完成，则整批拒绝退费申请（EX-DRA-201）。
            if (DispenseStatus.COMPLETED.getCode().equals(dispense.getStatusEnum())
                || DispenseStatus.PART_COMPLETED.getCode().equals(dispense.getStatusEnum())) {
                throw new BusinessException(
                    ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT,
                    "存在已执行的耗材发放，请先取消执行后再申请退费"
                );
            }
        }
        // Asm：全部检查通过，返回允许语义。
        return this.doctorStationRefundApplicationMapper.toVerifyConsumableExecutionBeforeRefundResponse(true, null);
    }

    /**
     * IF-DRA-03 Gate：提交请求体、行锚点与当前操作人会话校验。
     *
     * @param request 提交体
     * @return 操作人主键（写库命令 {@code operatorId}）
     */
    private Long validateSubmitOutpatientRefundApplicationRequestAndGetOperatorId(SubmitOutpatientRefundApplicationRequest request) {
        if (request == null || request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "退费申请行不能为空");
        }
        for (RefundApplicationLineItemDto line : request.getLines()) {
            if (line.getPaymentId() == null || line.getChargeItemId() == null || line.getRefundForCurrentBill() == null) {
                throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "退费申请行字段不完整");
            }
        }
        if (request.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        return currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT, "当前会话操作人不能为空");
    }

    /**
     * IF-DRA-03：提交门诊退费申请。
     *
     * @param request 提交体（就诊 + 行级拟退/保留标记）
     * @return 受理结果与拟退行数
     * @throws BusinessException 支付挂载不一致、状态不允许或药/耗/诊分支无法落库
     */
    public SubmitOutpatientRefundApplicationResponse submitOutpatientRefundApplication(SubmitOutpatientRefundApplicationRequest request) {
        Long operatorId = this.validateSubmitOutpatientRefundApplicationRequestAndGetOperatorId(request);
        Long encounterId = request.getEncounterId();
        // Pre：就诊必须存在，作为全链路上下文锚点。
        this.encounterQueryService.mustGetEncounter(encounterId);
        List<Long> allLineChargeIds = new ArrayList<>();
        for (RefundApplicationLineItemDto line : request.getLines()) {
            allLineChargeIds.add(line.getChargeItemId());
        }
        ChargeItemQueryCommand loadAllCmd = new ChargeItemQueryCommand();
        loadAllCmd.setIds(allLineChargeIds);
        // Pre：批量加载提交涉及的全部收费行，避免循环查询（A.9.1）。
        List<ChargeItem> allRows = this.chargeItemQueryService.loadChargeItemsByIds(loadAllCmd);
        Map<Long, ChargeItem> chargeById = new HashMap<>();
        for (ChargeItem row : allRows) {
            chargeById.put(row.getId(), row);
        }
        for (Long id : allLineChargeIds) {
            // Pre：每条提交行必须能命中收费实体，否则属于伪造主键。
            ChargeItem row = chargeById.get(id);
            if (row == null) {
                throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_NOT_FOUND, "收费明细不存在");
            }
            // Pre：收费行必须属于当前就诊，防止跨就诊提交。
            if (!encounterId.equals(row.getEncounterId())) {
                throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_RULE_CONFLICT, "收费明细不属于当前就诊");
            }
            // Pre：挂号费不可提交医生站退费申请，须退号（AS-OP-DOR-004 §1.3 与 IF-OPC-03 排除口径一致）。
            if (TableNameConstants.ADM_HEALTHCARE_SERVICE.equals(row.getServiceTable())) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_ITEM_RULE_CONFLICT,
                    "挂号费请通过退号办理，不可申请退费"
                );
            }
        }
        for (RefundApplicationLineItemDto line : request.getLines()) {
            // Pre：逐行校验支付锚点与收费行挂载关系（BR-DRA-009）。
            PaymentReconciliationQueryModel payment = this.paymentReconciliationQueryService.mustGetPayment(line.getPaymentId());
            if (!encounterId.equals(payment.getEncounterId())) {
                throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT, "支付流水不属于当前就诊");
            }
            List<ChargeItemIdsCollectionQueryModel> onePay =
                this.paymentReconciliationQueryService.getChargeItemIdsByPayments(Collections.singletonList(line.getPaymentId()));
            Set<Long> snap = new HashSet<>();
            if (!onePay.isEmpty() && onePay.get(0).getChargeItemIds() != null) {
                for (Long cid : onePay.get(0).getChargeItemIds()) {
                    if (cid != null) {
                        snap.add(cid);
                    }
                }
            }
            if (!snap.contains(line.getChargeItemId())) {
                throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT, "收费明细不属于所选支付流水");
            }
        }
        List<ChargeItem> submitScopeRows = new ArrayList<>(allRows);
        submitScopeRows = this.chargeItemQueryService.filterOutSupersededSettledLines(encounterId, submitScopeRows);
        Set<Long> visibleSubmitIds = new HashSet<>();
        for (ChargeItem visibleRow : submitScopeRows) {
            visibleSubmitIds.add(visibleRow.getId());
        }
        for (RefundApplicationLineItemDto line : request.getLines()) {
            ChargeItem row = chargeById.get(line.getChargeItemId());
            // Pre：已被半退再生替代或已进入退费过程态的行不得再次申请（BR-DRA-020）。
            if (!visibleSubmitIds.contains(line.getChargeItemId())) {
                throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_RULE_CONFLICT, "收费明细已不可申请退费，请刷新后重试");
            }
            if (ChargeItemStatus.REFUNDING.getCode().equals(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_RULE_CONFLICT, "收费明细已在退费中");
            }
            if (ChargeItemStatus.REFUNDED.getCode().equals(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_RULE_CONFLICT, "收费明细已退费完成");
            }
            // Pre：拟退行必须为已结算态，才能进入退款中迁移（BR-DRA-008）。
            if (Boolean.TRUE.equals(line.getRefundForCurrentBill())) {
                if (!ChargeItemStatus.SETTLED.getCode().equals(row.getStatusEnum())) {
                    throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_RULE_CONFLICT, "拟退收费明细必须为已结算状态");
                }
            }
        }
        // Cmd：先处理保留行账单重生成（BR-DRA-004），再进入拟退药/耗/诊编排，避免新行锚点与旧事实冲突。
        this.regeneratePreservedChargeLines(operatorId, request, chargeById);
        List<Long> refundingIds = new ArrayList<>();
        for (RefundApplicationLineItemDto line : request.getLines()) {
            // Pre：构造拟退集合 R：仅本单退费为 true 的行进入第二段编排（BR-DRA-010）。
            if (Boolean.TRUE.equals(line.getRefundForCurrentBill())) {
                refundingIds.add(line.getChargeItemId());
            }
        }
        int refundingCount = refundingIds.size();
        // Pre：若不存在拟退行，则无需二次装载与药耗诊写库，仅返回成功语义。
        List<OutpatientRefundApplicationPaymentSummaryDto> paymentSummaries =
            this.buildPaymentSummariesForSubmitResponse(encounterId, request, refundingIds);
        if (refundingIds.isEmpty()) {
            log.info("submitOutpatientRefundApplication encounterId={} operatorId={} refundingCount=0", encounterId, operatorId);
            return this.doctorStationRefundApplicationMapper.toSubmitOutpatientRefundApplicationResponse(true, 0, paymentSummaries);
        }
        ChargeItemQueryCommand reloadCmd = new ChargeItemQueryCommand();
        reloadCmd.setIds(refundingIds);
        // Pre：对拟退集合二次装载，降低并发下状态漂移风险（AS-SDD §3.5 步骤 9）。
        List<ChargeItem> refundRows = this.chargeItemQueryService.loadChargeItemsByIds(reloadCmd);
        Map<Long, ChargeItem> refundById = new HashMap<>();
        for (ChargeItem row : refundRows) {
            refundById.put(row.getId(), row);
        }
        List<Long> medReqIds = new ArrayList<>();
        List<ChargeItem> medCharges = new ArrayList<>();
        List<ChargeItem> devCharges = new ArrayList<>();
        List<ChargeItem> svcCharges = new ArrayList<>();
        for (Long id : refundingIds) {
            ChargeItem row = refundById.get(id);
            if (row == null) {
                throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_NOT_FOUND, "拟退收费明细不存在");
            }
            if (!ChargeItemStatus.SETTLED.getCode().equals(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_RULE_CONFLICT, "拟退收费明细状态已变化，请刷新后重试");
            }
            // Pre：按药/耗/诊分流（兼容 service 锚发放表与 dispense_* 三锚点，与 IF-OPC-07 后置口径一致）。
            if (this.isMedicationAnchoredChargeItem(row)) {
                Long medicationRequestId = this.resolveMedicationRequestId(row);
                if (medicationRequestId == null) {
                    throw new BusinessException(
                        ErrorCodeConstants.CHARGE_ITEM_RULE_CONFLICT,
                        "拟退药品收费明细无法解析药品申请锚点"
                    );
                }
                medReqIds.add(medicationRequestId);
                medCharges.add(row);
            } else if (this.isDeviceAnchoredChargeItem(row)) {
                Long deviceRequestId = this.resolveDeviceRequestId(row);
                if (deviceRequestId == null) {
                    throw new BusinessException(
                        ErrorCodeConstants.CHARGE_ITEM_RULE_CONFLICT,
                        "拟退耗材收费明细无法解析耗材申请锚点"
                    );
                }
                devCharges.add(row);
            } else if (TableNameConstants.WKF_SERVICE_REQUEST.equals(row.getServiceTable())) {
                svcCharges.add(row);
            }
        }
        Set<Long> uniqueMedReq = new LinkedHashSet<>(medReqIds);
        List<Long> medReqIdList = new ArrayList<>(uniqueMedReq);
        // Pre：批量拉取药品发放事实，供后续互斥分支判断（IF-DRA-03 步骤 11）。
        List<MedicationDispense> medDispenses = Collections.emptyList();
        if (!medReqIdList.isEmpty()) {
            medDispenses = this.medicationDispenseQueryService.listMedicationDispensesByMedReqIds(medReqIdList);
        }
        Map<Long, List<MedicationDispense>> medDispenseByReq = new LinkedHashMap<>();
        for (MedicationDispense dispense : medDispenses) {
            List<MedicationDispense> bucket = medDispenseByReq.computeIfAbsent(dispense.getMedReqId(), k -> new ArrayList<>());
            bucket.add(dispense);
        }
        for (Long serviceRequestId : this.collectDistinctServiceIds(svcCharges)) {
            // Pre：诊疗写库前强校验服务申请可读（BR-DRA-007）。
            this.serviceRequestQueryService.mustGetServiceRequest(serviceRequestId);
        }
        for (ChargeItem medCharge : medCharges) {
            // Cmd：逐条执行药品退费互斥分支（BR-DRA-005）。
            MedicationDispense dispense = this.pickMedicationDispenseForChargeItem(medCharge, medDispenseByReq);
            this.executeMedicationRefundBranch(medCharge, dispense, operatorId);
        }
        for (ChargeItem devCharge : devCharges) {
            // Cmd：逐条执行耗材退费分支（BR-DRA-006）。
            this.executeDeviceRefundBranch(devCharge, operatorId);
        }
        for (ChargeItem svcCharge : svcCharges) {
            // Cmd：逐条执行诊疗取消/停止分支（BR-DRA-007）。
            this.executeServiceRefundBranch(svcCharge, operatorId);
        }
        // Cmd：拟退行统一进入退款中过程态并发布申请事件（BR-DRA-008）。
        this.chargeItemDomainService.requestOutpatientBillRefund(
            this.doctorStationRefundApplicationMapper.toBatchChargeItemIdsCommand(refundingIds, operatorId)
        );
        // NFR：日志不落患者隐私，仅记录就诊、操作人与拟退规模（AS-SDD §6.4）。
        log.info("submitOutpatientRefundApplication encounterId={} operatorId={} refundingCount={}", encounterId, operatorId, refundingCount);
        // Asm：返回受理成功语义与按支付分组的执行衔接摘要（BR-DRA-020 / EX-DRA-410）。
        return this.doctorStationRefundApplicationMapper.toSubmitOutpatientRefundApplicationResponse(
            true, refundingCount, paymentSummaries);
    }

    /**
     * IF-DRA-03：按支付主单组装执行侧衔接摘要（半退保留再生行主键供收费窗 pre+settle）。
     *
     * @param encounterId   就诊标识
     * @param request       提交体
     * @param refundingIds  拟退收费明细主键
     * @return 按支付分组摘要列表
     */
    private List<OutpatientRefundApplicationPaymentSummaryDto> buildPaymentSummariesForSubmitResponse(
        Long encounterId,
        SubmitOutpatientRefundApplicationRequest request,
        List<Long> refundingIds
    ) {
        Map<Long, LinkedHashSet<Long>> refundingByPayment = new LinkedHashMap<>();
        Map<Long, LinkedHashSet<Long>> preservedSourceByPayment = new LinkedHashMap<>();
        for (RefundApplicationLineItemDto line : request.getLines()) {
            if (line.getPaymentId() == null) {
                continue;
            }
            if (Boolean.TRUE.equals(line.getRefundForCurrentBill())) {
                LinkedHashSet<Long> bucket = refundingByPayment.computeIfAbsent(line.getPaymentId(), k -> new LinkedHashSet<>());
                bucket.add(line.getChargeItemId());
            } else {
                LinkedHashSet<Long> bucket = preservedSourceByPayment.computeIfAbsent(line.getPaymentId(), k -> new LinkedHashSet<>());
                bucket.add(line.getChargeItemId());
            }
        }
        LinkedHashSet<Long> allPreservedSources = new LinkedHashSet<>();
        for (LinkedHashSet<Long> sources : preservedSourceByPayment.values()) {
            allPreservedSources.addAll(sources);
        }
        Map<Long, Long> sourceToNewPlanned =
            this.chargeItemQueryService.mapPreservedNewChargeItemIdBySourceId(encounterId, allPreservedSources);
        LinkedHashSet<Long> paymentIds = new LinkedHashSet<>();
        paymentIds.addAll(refundingByPayment.keySet());
        paymentIds.addAll(preservedSourceByPayment.keySet());
        List<OutpatientRefundApplicationPaymentSummaryDto> summaries = new ArrayList<>();
        for (Long paymentId : paymentIds) {
            OutpatientRefundApplicationPaymentSummaryDto summary = new OutpatientRefundApplicationPaymentSummaryDto();
            summary.setPaymentId(paymentId);
            LinkedHashSet<Long> refundingOnPayment = refundingByPayment.get(paymentId);
            if (refundingOnPayment == null) {
                summary.setRefundingChargeItemIds(Collections.emptyList());
            } else {
                summary.setRefundingChargeItemIds(new ArrayList<>(refundingOnPayment));
            }
            LinkedHashSet<Long> preservedSources = preservedSourceByPayment.get(paymentId);
            List<Long> preservedNewIds = new ArrayList<>();
            if (preservedSources != null) {
                for (Long sourceId : preservedSources) {
                    Long newId = sourceToNewPlanned.get(sourceId);
                    if (newId != null) {
                        preservedNewIds.add(newId);
                    }
                }
            }
            summary.setPreservedNewChargeItemIds(preservedNewIds);
            boolean hasRefunding = refundingOnPayment != null && !refundingOnPayment.isEmpty();
            boolean hasPreserved = !preservedNewIds.isEmpty();
            boolean halfRefund = hasRefunding && hasPreserved;
            summary.setHalfRefundOnPayment(halfRefund);
            summaries.add(summary);
        }
        return summaries;
    }

    /**
     * 解析某收费项所属支付流水读模型（IF-DRA-01 列表装配）。
     *
     * @param chargeItemId   收费明细主键
     * @param idCollections  支付解析结果集合
     * @param paymentById    支付主键索引
     * @return 命中支付；未命中返回 null
     */
    private PaymentReconciliationQueryModel resolvePaymentForChargeItem(
        Long chargeItemId,
        List<ChargeItemIdsCollectionQueryModel> idCollections,
        Map<Long, PaymentReconciliationQueryModel> paymentById
    ) {
        for (ChargeItemIdsCollectionQueryModel collection : idCollections) {
            if (collection.getChargeItemIds() == null) {
                continue;
            }
            for (Long id : collection.getChargeItemIds()) {
                if (chargeItemId.equals(id)) {
                    return paymentById.get(collection.getPaymentId());
                }
            }
        }
        return null;
    }

    /**
     * 半退保留链：克隆上游并落库新收费行（IF-DRA-03 步骤 6～7）。
     *
     * @param operatorId 操作人主键
     * @param request    提交体
     * @param chargeById 已装载收费行索引
     */
    private void regeneratePreservedChargeLines(
        Long operatorId,
        SubmitOutpatientRefundApplicationRequest request,
        Map<Long, ChargeItem> chargeById
    ) {
        List<ChargeItem> preserved = new ArrayList<>();
        for (RefundApplicationLineItemDto line : request.getLines()) {
            // Pre：仅保留（本单不退）行进入再生链，避免拟退行被重复克隆（BR-DRA-004/010）。
            if (Boolean.TRUE.equals(line.getRefundForCurrentBill())) {
                continue;
            }
            ChargeItem src = chargeById.get(line.getChargeItemId());
            if (src == null) {
                continue;
            }
            // Pre：挂号费不走半退保留链（须退号）；与 IF-DRA-01 列表排除口径一致。
            if (TableNameConstants.ADM_HEALTHCARE_SERVICE.equals(src.getServiceTable())) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_ITEM_RULE_CONFLICT,
                    "挂号费请通过退号办理，不可申请退费"
                );
            }
            preserved.add(src);
        }
        // Pre：无保留行则跳过写库点 1，直接进入拟退集合编排。
        if (preserved.isEmpty()) {
            return;
        }
        List<CreateChargeItemsLinkingRegeneratedAnchorsCommand.Item> anchors = new ArrayList<>();
        for (ChargeItem src : preserved) {
            // Cmd：按业务来源表名分别克隆药/耗/诊上游根（AS-SDD §3.5 步骤 7）。
            Long newServiceId = this.cloneUpstreamForPreservedLine(operatorId, src);
            CreateChargeItemsLinkingRegeneratedAnchorsCommand.Item anchor = new CreateChargeItemsLinkingRegeneratedAnchorsCommand.Item();
            anchor.setSourceChargeItemId(src.getId());
            anchor.setNewServiceTable(this.resolvePreservedRegenerationRequestServiceTable(src));
            anchor.setNewServiceId(newServiceId);
            anchor.setNewDispenseTable(null);
            anchor.setNewDispenseId(null);
            anchors.add(anchor);
        }
        CreateChargeItemsLinkingRegeneratedAnchorsCommand cmd =
            this.doctorStationRefundApplicationMapper.toCreateChargeItemsLinkingRegeneratedAnchorsCommand(operatorId, anchors);
        // Cmd：批量落库新账单行并链回源行（DM-ADM-011 §6.1.9a）。
        this.chargeItemDomainService.createChargeItemsLinkingRegeneratedAnchors(cmd);
    }

    /**
     * 克隆保留链上游业务根并返回新 serviceId（IF-DRA-03 步骤 7）。
     *
     * @param operatorId 操作人主键
     * @param src        源收费行
     * @return 新上游主键
     */
    private Long cloneUpstreamForPreservedLine(Long operatorId, ChargeItem src) {
        // Pre：药品路径（含发放表锚点）走药品申请聚合克隆（DM-MED-003 §6.1.16）。
        if (this.isMedicationAnchoredChargeItem(src)) {
            Long medicationRequestId = this.resolveMedicationRequestId(src);
            if (medicationRequestId == null) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_ITEM_RULE_CONFLICT,
                    "保留链药品收费明细无法解析药品申请锚点"
                );
            }
            CloneMedicationRequestForPreservedChargeRegenerationCommand command = new CloneMedicationRequestForPreservedChargeRegenerationCommand();
            command.setSourceMedicationRequestId(medicationRequestId);
            command.setOperatorId(operatorId);
            return this.medicationRequestDomainService.cloneMedicationRequestForPreservedChargeRegeneration(command);
        }
        // Pre：耗材路径（含发放表锚点）走耗材请求聚合克隆（DM-WKF-003 §6.1.11）。
        if (this.isDeviceAnchoredChargeItem(src)) {
            Long deviceRequestId = this.resolveDeviceRequestId(src);
            if (deviceRequestId == null) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_ITEM_RULE_CONFLICT,
                    "保留链耗材收费明细无法解析耗材申请锚点"
                );
            }
            CloneDeviceRequestForPreservedChargeRegenerationCommand command = new CloneDeviceRequestForPreservedChargeRegenerationCommand();
            command.setSourceDeviceRequestId(deviceRequestId);
            command.setOperatorId(operatorId);
            return this.deviceRequestDomainService.cloneDeviceRequestForPreservedChargeRegeneration(command);
        }
        // Pre：诊疗路径走服务申请聚合克隆（DM-WKF-004 §6.1.17）。
        if (TableNameConstants.WKF_SERVICE_REQUEST.equals(src.getServiceTable())) {
            CloneServiceRequestForPreservedChargeRegenerationCommand command = new CloneServiceRequestForPreservedChargeRegenerationCommand();
            command.setSourceServiceRequestId(src.getServiceId());
            command.setOperatorId(operatorId);
            return this.serviceRequestDomainService.cloneServiceRequestForPreservedChargeRegeneration(command);
        }
        throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_RULE_CONFLICT, "保留链暂不支持该业务来源表类型");
    }

    /**
     * 半退保留链落库时新行 {@code service_table}：统一锚医嘱请求表，不复用发放表物理名。
     *
     * @param src 源已结算收费行
     * @return 再生后 service_table
     */
    private String resolvePreservedRegenerationRequestServiceTable(ChargeItem src) {
        if (this.isMedicationAnchoredChargeItem(src)) {
            return TableNameConstants.MED_MEDICATION_REQUEST;
        }
        if (this.isDeviceAnchoredChargeItem(src)) {
            return TableNameConstants.WKF_DEVICE_REQUEST;
        }
        if (TableNameConstants.WKF_SERVICE_REQUEST.equals(src.getServiceTable())) {
            return TableNameConstants.WKF_SERVICE_REQUEST;
        }
        throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_RULE_CONFLICT, "保留链暂不支持该业务来源表类型");
    }

    /**
     * 判断收费行是否锚定药品业务（医嘱请求或发放表两种持久化口径）。
     *
     * @param row 收费明细
     * @return 是否药品路径
     */
    private boolean isMedicationAnchoredChargeItem(ChargeItem row) {
        if (TableNameConstants.MED_MEDICATION_REQUEST.equals(row.getServiceTable())) {
            return true;
        }
        if (TableNameConstants.MED_MEDICATION_DISPENSE.equals(row.getServiceTable())) {
            return true;
        }
        if (TableNameConstants.MED_MEDICATION_DISPENSE.equals(row.getDispenseTable()) && row.getDispenseId() != null) {
            return true;
        }
        return false;
    }

    /**
     * 判断收费行是否锚定耗材业务（请求或发放表两种持久化口径）。
     *
     * @param row 收费明细
     * @return 是否耗材路径
     */
    private boolean isDeviceAnchoredChargeItem(ChargeItem row) {
        if (TableNameConstants.WKF_DEVICE_REQUEST.equals(row.getServiceTable())) {
            return true;
        }
        if (TableNameConstants.WKF_DEVICE_DISPENSE.equals(row.getServiceTable())) {
            return true;
        }
        if (TableNameConstants.WKF_DEVICE_DISPENSE.equals(row.getDispenseTable()) && row.getDispenseId() != null) {
            return true;
        }
        return false;
    }

    /**
     * 解析药品申请主键：兼容 {@code service_table} 锚医嘱或锚发放，以及 {@code dispense_table+dispense_id} 三锚点。
     *
     * @param row 收费明细
     * @return 药品申请主键；非药品路径返回 null
     */
    private Long resolveMedicationRequestId(ChargeItem row) {
        if (TableNameConstants.MED_MEDICATION_REQUEST.equals(row.getServiceTable())) {
            return row.getServiceId();
        }
        Long dispenseId = null;
        if (TableNameConstants.MED_MEDICATION_DISPENSE.equals(row.getServiceTable())) {
            dispenseId = row.getServiceId();
        } else if (TableNameConstants.MED_MEDICATION_DISPENSE.equals(row.getDispenseTable())) {
            dispenseId = row.getDispenseId();
        }
        if (dispenseId == null) {
            return null;
        }
        Map<Long, Long> mapped =
            this.medicationDispenseQueryService.mapMedicationDispenseIdsToMedReqIds(Collections.singletonList(dispenseId));
        return mapped.get(dispenseId);
    }

    /**
     * 解析耗材申请主键：兼容 {@code service_table} 锚请求或锚发放，以及 {@code dispense_table+dispense_id} 三锚点。
     *
     * @param row 收费明细
     * @return 耗材申请主键；非耗材路径返回 null
     */
    private Long resolveDeviceRequestId(ChargeItem row) {
        if (TableNameConstants.WKF_DEVICE_REQUEST.equals(row.getServiceTable())) {
            return row.getServiceId();
        }
        Long dispenseId = null;
        if (TableNameConstants.WKF_DEVICE_DISPENSE.equals(row.getServiceTable())) {
            dispenseId = row.getServiceId();
        } else if (TableNameConstants.WKF_DEVICE_DISPENSE.equals(row.getDispenseTable())) {
            dispenseId = row.getDispenseId();
        }
        if (dispenseId == null) {
            return null;
        }
        Map<Long, Long> mapped =
            this.deviceDispenseQueryService.mapDeviceDispenseIdsToDeviceReqIds(Collections.singletonList(dispenseId));
        return mapped.get(dispenseId);
    }

    /**
     * 为收费行挑选用于退费编排的药品发放单（若存在多条，优先匹配收费行 dispense 锚点）。
     *
     * @param chargeItem        收费行
     * @param medDispenseByReq  药品申请主键索引
     * @return 命中发放；可能为 null
     */
    private MedicationDispense pickMedicationDispenseForChargeItem(
        ChargeItem chargeItem,
        Map<Long, List<MedicationDispense>> medDispenseByReq
    ) {
        List<MedicationDispense> bucket = medDispenseByReq.get(chargeItem.getServiceId());
        if (bucket == null || bucket.isEmpty()) {
            return null;
        }
        Long wantedDispenseId = chargeItem.getDispenseId();
        if (wantedDispenseId != null) {
            for (MedicationDispense dispense : bucket) {
                if (wantedDispenseId.equals(dispense.getId())) {
                    return dispense;
                }
            }
        }
        return bucket.get(0);
    }

    /**
     * 药品拟退互斥分支（IF-DRA-03 步骤 12～15）。
     *
     * @param chargeItem 拟退收费行
     * @param dispense   可能为空的发放事实
     * @param operatorId 操作人主键
     */
    private void executeMedicationRefundBranch(ChargeItem chargeItem, MedicationDispense dispense, Long operatorId) {
        boolean wrote = false;
        if (dispense != null && this.isMedicationDispenseCompletedOrPartCompleted(dispense)) {
            // Cmd：已发/部分发路径（IF-DRA-03 步骤 12）：退药申请单 + 待退药发放。
            this.createRefundMedicationOrderAndPendingDispense(chargeItem, dispense, operatorId);
            wrote = true;
        }
        if (!wrote && dispense != null && this.isMedicationDispenseCancellableQuietly(dispense)) {
            // Cmd：未发可作废路径：作废原发放单（分支 13）。
            this.medicationDispenseDomainService.cancelMedicationDispense(
                this.doctorStationRefundApplicationMapper.toCancelMedicationDispenseCommand(
                    dispense.getId(),
                    DoctorStationRefundApplicationConstants.MEDICATION_DISPENSE_NOT_PERFORMED_REASON_OUTPATIENT_REFUND_APP,
                    operatorId
                )
            );
            wrote = true;
        }
        if (!wrote) {
            // Cmd：医嘱重开路径（分支 14）。
            try {
                this.medicationRequestDomainService.reopenMedicationOrder(
                    this.doctorStationRefundApplicationMapper.toReopenMedicationOrderCommand(chargeItem.getServiceId(), operatorId)
                );
                wrote = true;
            } catch (BusinessException ex) {
                log.debug(
                    "reopenMedicationOrder skipped medReqId={} chargeItemId={}",
                    chargeItem.getServiceId(),
                    chargeItem.getId(),
                    ex
                );
                wrote = false;
            }
        }
        if (!wrote && dispense != null) {
            // Cmd：待退药窗口（IF-DRA-03 步骤 15）：先建退药申请再建待退发放。
            this.createRefundMedicationOrderAndPendingDispense(chargeItem, dispense, operatorId);
            wrote = true;
        }
        if (!wrote) {
            // Gate：若药嘱侧无法落入任一分支，则视为重复/非法申请（EX-DRA-301）。
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_RULE_CONFLICT, "退药申请无法落地，请刷新后重试");
        }
    }

    /**
     * 创建退药申请单并基于原发放单生成待退药发放行（AS-OP-PHA-002 队列入口）。
     *
     * @param chargeItem 拟退收费行
     * @param dispense   原发放事实
     * @param operatorId 操作人主键
     */
    private void createRefundMedicationOrderAndPendingDispense(
        ChargeItem chargeItem,
        MedicationDispense dispense,
        Long operatorId
    ) {
        Long refundRequestId = this.medicationRequestDomainService.createRefundMedicationOrder(
            this.doctorStationRefundApplicationMapper.toCreateRefundMedicationOrderCommand(chargeItem, operatorId)
        );
        this.medicationDispenseDomainService.createPendingRefundDispense(
            this.doctorStationRefundApplicationMapper.toCreatePendingRefundDispenseCommand(
                dispense, refundRequestId, operatorId
            )
        );
    }

    /**
     * 判断药品发放是否处于已完成或部分完成态（code 口径）。
     *
     * @param dispense 发放实体
     * @return 是否命中已完成/部分完成
     */
    private boolean isMedicationDispenseCompletedOrPartCompleted(MedicationDispense dispense) {
        if (dispense.getStatusEnum() == null) {
            return false;
        }
        if (DispenseStatus.COMPLETED.equals(dispense.getStatusEnum())) {
            return true;
        }
        if (DispenseStatus.PART_COMPLETED.equals(dispense.getStatusEnum())) {
            return true;
        }
        return false;
    }

    /**
     * 静默判断药品发放是否允许作废（不向外抛出业务异常）。
     *
     * @param dispense 发放实体
     * @return 是否允许作废
     */
    private boolean isMedicationDispenseCancellableQuietly(MedicationDispense dispense) {
        try {
            dispense.ensureCancellable();
            return true;
        } catch (BusinessException ex) {
            return false;
        }
    }

    /**
     * 耗材拟退分支（IF-DRA-03 步骤 16～17）。
     *
     * @param chargeItem 拟退收费行
     * @param operatorId 操作人主键
     */
    private void executeDeviceRefundBranch(ChargeItem chargeItem, Long operatorId) {
        // Cmd：先创建退耗材负向请求（写库点 3a）。
        this.deviceRequestDomainService.createDeviceOrderRefund(
            this.doctorStationRefundApplicationMapper.toCreateDeviceOrderRefundCommand(chargeItem, operatorId)
        );
        List<DeviceDispenseListModel> models =
            this.deviceDispenseQueryService.selectByRequestIdList(Collections.singletonList(chargeItem.getServiceId()));
        DeviceDispenseListModel picked = this.pickLatestDeviceDispense(models);
        if (picked == null) {
            return;
        }
        boolean executed = DispenseStatus.COMPLETED.getCode().equals(picked.getStatusEnum())
            || DispenseStatus.PART_COMPLETED.getCode().equals(picked.getStatusEnum());
        BigDecimal refundQty = chargeItem.getQuantityValue();
        if (refundQty == null) {
            refundQty = BigDecimal.ZERO;
        }
        if (executed) {
            // Cmd：已执行/部分执行：创建待退发放（写库点 3b 分支 A）。
            this.deviceDispenseDomainService.createPendingRefundDeviceDispense(
                this.doctorStationRefundApplicationMapper.toCreatePendingRefundDeviceDispenseCommand(picked.getId(), refundQty, operatorId)
            );
            return;
        }
        // Cmd：未发路径：作废未发耗材发放（写库点 3b 分支 B）。
        this.deviceDispenseDomainService.cancelUndispensedDeviceDispense(
            this.doctorStationRefundApplicationMapper.toCancelUndispensedDeviceDispenseCommand(
                picked.getId(),
                DoctorStationRefundApplicationConstants.DEVICE_DISPENSE_NOT_PERFORMED_REASON_OUTPATIENT_REFUND_APP,
                operatorId
            )
        );
    }

    /**
     * 在列表模型中挑选用于编排的耗材发放行（优先最新主键）。
     *
     * @param models 发放列表模型
     * @return 选中模型；空表返回 null
     */
    private DeviceDispenseListModel pickLatestDeviceDispense(List<DeviceDispenseListModel> models) {
        if (models == null || models.isEmpty()) {
            return null;
        }
        DeviceDispenseListModel best = models.get(0);
        for (DeviceDispenseListModel model : models) {
            if (model.getId() == null) {
                continue;
            }
            if (best.getId() == null) {
                best = model;
                continue;
            }
            if (model.getId() > best.getId()) {
                best = model;
            }
        }
        return best;
    }

    /**
     * 诊疗拟退分支（IF-DRA-03 步骤 18）。
     *
     * @param chargeItem 拟退收费行
     * @param operatorId 操作人主键
     */
    private void executeServiceRefundBranch(ChargeItem chargeItem, Long operatorId) {
        ServiceRequestQueryModel sr = this.serviceRequestQueryService.mustGetServiceRequest(chargeItem.getServiceId());
        // Cmd：已完成诊疗走取消申请；未完成走停止申请（BR-DRA-007）。
        if (RequestStatus.COMPLETED.getCode().equals(sr.getStatusEnum())) {
            this.serviceRequestDomainService.cancelServiceRequest(
                this.doctorStationRefundApplicationMapper.toCancelServiceRequestCommand(sr.getId(), operatorId)
            );
            return;
        }
        this.serviceRequestDomainService.stopServiceRequest(
            this.doctorStationRefundApplicationMapper.toStopServiceRequestCommand(sr.getId(), operatorId)
        );
    }

    /**
     * 收集诊疗收费行对应的去重服务申请主键集合。
     *
     * @param svcCharges 诊疗收费行列表
     * @return 去重后的 serviceId 列表
     */
    private List<Long> collectDistinctServiceIds(List<ChargeItem> svcCharges) {
        Set<Long> ids = new LinkedHashSet<>();
        for (ChargeItem row : svcCharges) {
            ids.add(row.getServiceId());
        }
        return new ArrayList<>(ids);
    }

    /**
     * 收集收费明细上的定义主键并一次性加载定价行（IF-DRA-01 项目名称批量解析）。
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
        return this.chargeItemDefinitionQueryService.findAllDefinitionsByIdIn(definitionIds);
    }

    /**
     * 按收费明细批量加载产品定义读模型（IF-DRA-01 项目名称优先口径）。
     *
     * @param chargeItems   收费明细列表
     * @param definitionMap 定价定义映射
     * @return 复合键到读模型；无锚点时返回空 Map
     */
    private Map<String, ProductDefinitionDisplayDto> buildProductDefinitionMapByChargeItems(
        List<ChargeItem> chargeItems,
        Map<Long, ChargeItemDefinition> definitionMap
    ) {
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
        return this.productDefinitionBatchLoader.loadAsMap(lookups);
    }

    /**
     * 解析单条收费明细对应的目录批量查询键：优先明细产品锚点，其次定价定义实例锚点。
     *
     * @param row        收费明细
     * @param definition 该行定价定义；可 {@code null}
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
     * @param productMap 批量目录查询结果
     * @param definition 定价定义，用于产品锚点缺失时的目录键回落
     * @return 命中则非空；未命中时 {@code null}
     */
    private ProductDefinitionDisplayDto resolveProductForChargeItem(
        ChargeItem row,
        Map<String, ProductDefinitionDisplayDto> productMap,
        ChargeItemDefinition definition
    ) {
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
     * IF-DRA-01：可退账单行项目名称摘要（与门诊收费待收费列表同口径）。
     *
     * @param row           收费明细
     * @param definitionMap 定价定义映射
     * @param productMap    目录读模型映射
     * @return 非空展示文案
     */
    private String resolveRefundableChargeLineItemDisplayName(
        ChargeItem row,
        Map<Long, ChargeItemDefinition> definitionMap,
        Map<String, ProductDefinitionDisplayDto> productMap
    ) {
        ChargeItemDefinition definitionRow = null;
        if (definitionMap != null && row.getDefinitionId() != null) {
            definitionRow = definitionMap.get(row.getDefinitionId());
        }
        ProductDefinitionDisplayDto product = this.resolveProductForChargeItem(row, productMap, definitionRow);
        if (product != null && StringUtils.isNotBlank(product.getProductName())) {
            return product.getProductName();
        }
        if (definitionRow != null && StringUtils.isNotBlank(definitionRow.getChargeName())) {
            return definitionRow.getChargeName();
        }
        return OutpatientChargeConstants.PENDING_CHARGE_ITEM_DISPLAY_FALLBACK;
    }
}
