/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.chinesemedicine.assembler;

import static com.zxplat.framework.core.application.util.StringUtils.trimToEmpty;

import com.zxplat.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.zxplat.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.zxplat.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.zxplat.domain.medication.medicationrequest.entity.MedicationRequest;
import com.zxplat.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.zxplat.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.zxplat.domain.share.constants.DoctorStationTcmConstants;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.ChargeItemStatus;
import com.zxplat.domain.share.enums.GenerateSource;
import com.zxplat.domain.share.enums.PriceType;
import com.zxplat.domain.share.enums.RequestStatus;
import com.zxplat.domain.share.enums.TherapyType;
import com.zxplat.domain.share.query.ProductDefinitionBatchLookup;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.zxplat.domain.workflow.servicerequest.command.CreateServiceRequestDraftCommand;
import com.zxplat.domain.workflow.servicerequest.command.SignServiceRequestCommand;
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.zxplat.domain.workflow.servicerequest.service.ServiceRequestDomainService;
import com.zxplat.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.zxplat.application.shared.advice.OrderChargeDraftAssembler;
import com.zxplat.application.shared.activitydefinition.ActivityDefinitionPackageChargePersistSupport;
import com.zxplat.application.shared.advice.ServiceAdviceCommandAssembler;
import com.zxplat.application.shared.advice.ServiceAdviceDraftCommandHelper;
import com.zxplat.application.shared.advice.TcmDecoctionBasedOnPrescriptionResolver;
import com.zxplat.application.shared.financial.OutpatientSelfPayFeeNatureAccountResolver;
import com.zxplat.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * IF-DOR-37 代煎费写路径编排：同方代煎 companion {@code ServiceRequest} + {@code PLANNED} 费用。
 *
 * <p>与皮试 companion 同构：用 {@code based_on}（{@code med_medication_request}）关联方内代表药行，
 * <b>禁止</b>把中药处方号写入诊疗请求/代煎费用的 {@code prescription_no}——该列在 SR 上仅服务申请单（{@code AR} 前缀），
 * 真源处方号只在药嘱；读侧/void 经 based_on → MedicationRequest 反查方号。
 * 门诊代煎账单挂个人现金自费户（调用方传入）；SR {@code contract_id} 写自费合同冗余；
 * <b>不</b>开立/改写 {@code Account}。</p>
 */
@Component
@RequiredArgsConstructor
public class TcmDecoctionAssembler {

    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final ChargeItemDomainService chargeItemDomainService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final ActivityDefinitionPackageChargePersistSupport activityDefinitionPackageChargePersistSupport;
    private final MedicationRequestQueryService medicationRequestQueryService;
    private final ServiceRequestDomainService serviceRequestDomainService;
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final TcmDecoctionBasedOnPrescriptionResolver tcmDecoctionBasedOnPrescriptionResolver;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    /** 自费合同只读：写入 SR.contract_id 冗余（不动 Account）。 */
    private final OutpatientSelfPayFeeNatureAccountResolver outpatientSelfPayFeeNatureAccountResolver;

    /**
     * 按付数刷新同处方号下代煎费（先 void 旧项，再签发 ServiceRequest 并升 PLANNED 费用）。
     *
     * <p>BR-TCM-RX-009：方头代煎=是时，数量取中药付数；费用锚定 {@code wkf_service_request}。
     * {@code ChargeItem.account_id} 须为就诊个人现金自费户；禁止改写 Account 主数据。</p>
     *
     * @param encounterId               就诊主键
     * @param patientId                 患者主键
     * @param accountId                 个人现金自费户主键（仅写账单挂账）
     * @param orgId                     机构主键
     * @param operatorId                操作人（开嘱/签发医师）
     * @param prescriptionNo            中药处方号
     * @param doseQuantity              付数
     * @param anchorRow                 方内代表药行（诊断、长临、执行科室透传）
     * @param anchorMedicationRequestId 方内代表药行主键（{@code based_on} 锚点）
     * @throws BusinessException 租户未配置代煎诊疗项目、未维护零售价或自费合同时
     */
    public void replacePlannedDecoctionCharge(
        Long encounterId,
        Long patientId,
        Long accountId,
        Long orgId,
        Long operatorId,
        String prescriptionNo,
        BigDecimal doseQuantity,
        MedicationRequestListQueryModel anchorRow,
        Long anchorMedicationRequestId
    ) {
        // Gate：方级 companion 必须 based_on 锚定代表药行，禁止用处方号串代关联
        if (anchorMedicationRequestId == null) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "签发代煎费时代表药行主键不能为空");
        }
        // Pre：同方重复签发前先清理旧代煎 SR 与费用，避免双挂
        voidDecoctionChargeByPrescriptionNo(encounterId, prescriptionNo, operatorId);
        // Pre：解析租户代煎诊疗项目与零售价
        Long activityId = requireDecoctionActivityId();
        ProductDefinitionDisplayDto activityProductRow = loadDecoctionActivityProductRow(activityId);
        ChargeItemDefinition saleLine = requireDecoctionSaleLine(activityId);
        BigDecimal quantity = doseQuantity;
        String therapyEnum = resolveTherapyEnum(anchorRow);
        String unitCode = ServiceAdviceDraftCommandHelper.requireUnitCode(null, null, activityProductRow);
        // Pre：自费合同写入 SR 冗余；账单挂账以入参个人现金户为准
        Long selfPayContractId = outpatientSelfPayFeeNatureAccountResolver.resolveSelfPayContractId();
        // Cmd：落库代煎 ServiceRequest 草稿并立即签发至 ACTIVE（prescription_no 留空）
        CreateServiceRequestDraftCommand createCmd = buildDecoctionServiceRequestDraftCommand(
            encounterId, patientId, operatorId, orgId, activityId,
            anchorRow, anchorMedicationRequestId, quantity, unitCode, therapyEnum, activityProductRow,
            selfPayContractId);
        Long serviceRequestId = serviceRequestDomainService.createServiceRequestDraft(createCmd);
        serviceRequestDomainService.signServiceRequest(buildSignServiceRequestCommand(serviceRequestId, operatorId));
        // Cmd：按 SR 锚点挂费用草稿并升为 PLANNED（account_id=个人现金自费户；不写处方号快照）
        CreateChargeItemCommand.Item draftItem = OrderChargeDraftAssembler.toCreateChargeItemDraftItemFromOrder(
            encounterId, patientId, accountId, orgId,
            serviceRequestId, TableNameConstants.WKF_SERVICE_REQUEST,
            TableNameConstants.WKF_ACTIVITY_DEFINITION, activityId,
            saleLine, quantity, unitCode, activityProductRow);
        CreateChargeItemCommand chargeCmd = new CreateChargeItemCommand();
        chargeCmd.setOperatorId(operatorId);
        chargeCmd.setItem(draftItem);
        Long parentChargeId = chargeItemDomainService.createDraftChargeItemFromOrder(chargeCmd);
        // Cmd：诊疗账单落库后通用补写；代煎无子项则立即跳过
        activityDefinitionPackageChargePersistSupport.persistPackageChildChargeItemsAfterParent(
            parentChargeId, activityId, operatorId);
        chargeItemDomainService.planDraftChargeItemsAfterAdviceSigned(
            encounterId, TableNameConstants.WKF_SERVICE_REQUEST, serviceRequestId);
    }

    /**
     * 补充代煎费前门控：同方已存在有效代煎 companion，或代煎费已进入收费/结算时拒绝重复补充。
     *
     * <p>已收费药材处方仍可补充；若代煎费本身已收费，须走收银退费。
     * 取消后残留的未计费 DRAFT/PLANNED（无有效 SR）不拦截，由 {@link #replacePlannedDecoctionCharge} 内 void 扫尾。</p>
     *
     * @param encounterId    就诊主键
     * @param prescriptionNo 中药处方号
     * @throws BusinessException 已存在代煎费时
     */
    public void assertDecoctionChargeSupplementable(Long encounterId, String prescriptionNo) {
        if (encounterId == null || StringUtils.isEmpty(prescriptionNo)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "补充代煎费时就诊或处方号不能为空");
        }
        Long activityId = requireDecoctionActivityId();
        List<ServiceRequestListModel> decoctionRows =
            findDecoctionServiceRequests(encounterId, prescriptionNo, activityId);
        // Gate：仍存在有效代煎 companion SR 则拒绝重复补充
        if (!decoctionRows.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT,
                "当前处方已有代煎费，请勿重复补充；已收费代煎请走退费流程");
        }
        // Pre：无有效 SR 时仅拦截已进入收费/结算的费用；取消后残留的 DRAFT/PLANNED 由后续 void 清理，不得误拦再补充
        List<ChargeItem> items = chargeItemQueryService.listChargeItemsByEncounterAndProduct(
            encounterId, TableNameConstants.WKF_ACTIVITY_DEFINITION, activityId);
        String trimmedPrescriptionNo = prescriptionNo.trim();
        Map<Long, String> prescriptionByServiceRequestId =
            this.mapServiceRequestPrescriptionNos(items);
        for (ChargeItem chargeItem : items) {
            if (chargeItem == null || Boolean.TRUE.equals(chargeItem.getIsDeleted())) {
                continue;
            }
            // Pre：仅经 SR.based_on→药嘱反查方号
            if (!trimmedPrescriptionNo.equals(
                this.resolveChargeLinkedPrescriptionNo(chargeItem, prescriptionByServiceRequestId))) {
                continue;
            }
            String status = StringUtils.trimToEmpty(chargeItem.getStatusEnum());
            if (ChargeItemStatus.PENDING_SETTLEMENT.getCode().equals(status)
                || ChargeItemStatus.SETTLED.getCode().equals(status)
                || ChargeItemStatus.REFUNDING.getCode().equals(status)) {
                throw new BusinessException(
                    ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT,
                    "当前处方已有代煎费，请勿重复补充；已收费代煎请走退费流程");
            }
        }
    }

    /**
     * 取消代煎费前门控：同方代煎费用已进入待结算/已结算/退费中则拒绝（须走退费，不可直接取消勾选）。
     *
     * <p>与 {@link #assertDecoctionChargeSupplementable} 同口径：收费行不写中药方号，经 SR.`based_on`→药嘱反查方号。
     * 申请退费后 companion 可能已为 {@code STOPPED}（不再算「有效 SR」），仍须按收费态拦截，避免误清 {@code suffering_flag}。</p>
     *
     * @param encounterId    就诊主键
     * @param prescriptionNo 中药处方号（药嘱方头号，非收费行 prescription_no）
     * @throws BusinessException 代煎费已结算或不可取消时
     */
    public void assertDecoctionChargeVoidable(Long encounterId, String prescriptionNo) {
        if (encounterId == null || StringUtils.isEmpty(prescriptionNo)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "取消代煎费时就诊或处方号不能为空");
        }
        Long activityId;
        try {
            activityId = requireDecoctionActivityId();
        } catch (RuntimeException ex) {
            // 租户未配置代煎项目时静默放行，与 void 路径一致
            return;
        }
        // Pre：按代煎产品拉本就诊收费行，经 based_on 反查方号后判收费态（不依赖 live companion SR）
        List<ChargeItem> items = chargeItemQueryService.listChargeItemsByEncounterAndProduct(
            encounterId, TableNameConstants.WKF_ACTIVITY_DEFINITION, activityId);
        String trimmedPrescriptionNo = prescriptionNo.trim();
        Map<Long, String> prescriptionByServiceRequestId =
            this.mapServiceRequestPrescriptionNos(items);
        for (ChargeItem chargeItem : items) {
            if (chargeItem == null || Boolean.TRUE.equals(chargeItem.getIsDeleted())) {
                continue;
            }
            // Pre：仅本方代煎行（反查 CP，不读收费行 prescription_no）
            if (!trimmedPrescriptionNo.equals(
                this.resolveChargeLinkedPrescriptionNo(chargeItem, prescriptionByServiceRequestId))) {
                continue;
            }
            String status = StringUtils.trimToEmpty(chargeItem.getStatusEnum());
            // Gate：待结/已结/退费中禁止「取消代煎」清勾选，须走收银退费或撤销退费申请
            if (ChargeItemStatus.PENDING_SETTLEMENT.getCode().equals(status)
                || ChargeItemStatus.SETTLED.getCode().equals(status)
                || ChargeItemStatus.REFUNDING.getCode().equals(status)) {
                throw new BusinessException(
                    ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT,
                    "代煎费已进入收费/结算流程，不能直接取消，请走退费流程");
            }
        }
    }

    /**
     * 移除同方未计费代煎费及对应 ServiceRequest。
     *
     * <p>签退/取消代煎时对称清理：已签发 SR 先回退 PLANNED→DRAFT 并作废草稿费用，再签退并软删；
     * 最后扫尾仍锚定同方 SR 的孤儿未计费代煎费，保证取消后可再次补充。</p>
     *
     * @param encounterId    就诊主键
     * @param prescriptionNo 中药处方号（经药嘱 id 匹配 based_on）
     * @param operatorId     操作人；为 {@code null} 时跳过 SR 写侧清理
     */
    public void voidDecoctionChargeByPrescriptionNo(Long encounterId, String prescriptionNo, Long operatorId) {
        // Gate：就诊或处方号缺失时不做 void，避免误删他方费用
        if (encounterId == null || StringUtils.isEmpty(prescriptionNo)) {
            return;
        }
        Long activityId;
        try {
            activityId = requireDecoctionActivityId();
        } catch (RuntimeException ex) {
            // 租户未配置代煎项目时静默跳过，与签发前 Gate 分工
            return;
        }
        // Cmd：逐条清理同方代煎 SR 及其费用（仅 based_on 命中）
        for (ServiceRequestListModel row : findDecoctionServiceRequests(encounterId, prescriptionNo, activityId)) {
            if (row == null || row.getId() == null || operatorId == null) {
                // 无操作人时不改写 SR，防止审计链断裂
                continue;
            }
            String status = row.getStatusEnum();
            if (RequestStatus.DRAFT.getCode().equals(status)) {
                // 草稿态：直接作废锚定 SR 的未计费费用
                chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(
                    ServiceAdviceCommandAssembler.toVoidDraftChargeAnchor(
                        encounterId, TableNameConstants.WKF_SERVICE_REQUEST, row.getId(), operatorId));
            } else {
                // 已签发：PLANNED→DRAFT 后必须作废草稿费用，再签退并软删 SR（否则再补充会被费用门控误拦）
                chargeItemDomainService.rollbackPlannedChargeItemsAfterAdviceSignedOff(
                    encounterId, TableNameConstants.WKF_SERVICE_REQUEST, row.getId());
                chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(
                    ServiceAdviceCommandAssembler.toVoidDraftChargeAnchor(
                        encounterId, TableNameConstants.WKF_SERVICE_REQUEST, row.getId(), operatorId));
                if (RequestStatus.ACTIVE.getCode().equals(status)) {
                    serviceRequestDomainService.withdrawSignedServiceRequest(
                        ServiceAdviceCommandAssembler.toWithdrawServiceRequestCommand(row.getId(), operatorId));
                }
            }
            serviceRequestDomainService.removeServiceRequestDraft(
                ServiceAdviceCommandAssembler.toRemoveServiceRequestDraftCommand(row.getId(), operatorId));
        }
        // Cmd：清理已软删 SR 锚定的孤儿未计费代煎费
        voidResidualUnbilledDecoctionCharges(encounterId, prescriptionNo, activityId, operatorId);
    }

    /**
     * 从方内代表药行解析长临属性；缺省时回落临嘱，与门诊诊疗 companion 口径一致。
     *
     * @param anchorRow 方内代表药行，可空
     * @return 写入代煎 SR 的 {@code therapy_enum} code
     */
    private String resolveTherapyEnum(MedicationRequestListQueryModel anchorRow) {
        String therapyEnum = TherapyType.TEMPORARY.getCode();
        if (anchorRow == null) {
            return therapyEnum;
        }
        String resolved = StringUtils.firstNonBlank(anchorRow.getTherapyEnum(), TherapyType.TEMPORARY.getCode());
        if (resolved != null) {
            therapyEnum = resolved;
        }
        return therapyEnum;
    }

    /**
     * 装配代煎 companion {@code ServiceRequest} 草稿命令。
     *
     * <p>{@code prescription_no} 故意不写：诊疗请求该列仅申请单（AR）使用；代煎靠 based_on 关联药嘱。</p>
     *
     * @param encounterId               就诊主键
     * @param patientId                 患者主键
     * @param operatorId                操作人
     * @param orgId                     机构主键
     * @param activityId                代煎诊疗项目主键
     * @param anchorRow                 方内代表药行
     * @param anchorMedicationRequestId 代表药行主键（必填 based_on）
     * @param quantity                  付数
     * @param unitCode                  单位 code
     * @param therapyEnum               长临 code
     * @param activityProductRow        诊疗产品读模型
     * @param selfPayContractId         自费费用性质合同主键（写入 SR 冗余，不动 Account）
     * @return 领域创建命令
     */
    private CreateServiceRequestDraftCommand buildDecoctionServiceRequestDraftCommand(
        Long encounterId,
        Long patientId,
        Long operatorId,
        Long orgId,
        Long activityId,
        MedicationRequestListQueryModel anchorRow,
        Long anchorMedicationRequestId,
        BigDecimal quantity,
        String unitCode,
        String therapyEnum,
        ProductDefinitionDisplayDto activityProductRow,
        Long selfPayContractId
    ) {
        CreateServiceRequestDraftCommand cmd = new CreateServiceRequestDraftCommand();
        cmd.setOperatorId(operatorId);
        CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand item =
            new CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand();
        item.setEncounterId(encounterId);
        item.setPatientId(patientId);
        item.setActivityId(activityId);
        item.setQuantity(quantity);
        item.setUnitCode(unitCode);
        item.setExecutionCount(ServiceAdviceDraftCommandHelper.requireExecutionCount(1, null, therapyEnum));
        item.setCategoryEnum(StringUtils.firstNonBlankOrEmpty(activityProductRow.getCategoryCode(), "1"));
        item.setTherapyEnum(therapyEnum);
        item.setOrgId(orgId);
        item.setRequesterId(operatorId);
        item.setGenerateSourceEnum(GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        // SR 冗余自费 contract_id（账单挂账以入参个人现金户为准，不动 Account）
        item.setContractId(selfPayContractId);
        if (anchorRow != null) {
            // 诊断与执行科室与代表药行对齐，便于护士站/收银展示
            item.setEncounterDiagnosisIds(anchorRow.getEncounterDiagnosisIds());
            if (anchorRow.getOrgId() != null) {
                item.setOrgId(anchorRow.getOrgId());
            }
        }
        // 方级 companion 唯一关联：based_on → 代表药行（不写 prescription_no）
        item.setBasedOnTable(TableNameConstants.MED_MEDICATION_REQUEST);
        item.setBasedOnId(anchorMedicationRequestId);
        cmd.setItem(item);
        return cmd;
    }

    /**
     * 装配代煎 ServiceRequest 签发命令。
     *
     * @param serviceRequestId 代煎 SR 主键
     * @param operatorId       签发人
     * @return 领域签发命令
     */
    private SignServiceRequestCommand buildSignServiceRequestCommand(Long serviceRequestId, Long operatorId) {
        SignServiceRequestCommand cmd = new SignServiceRequestCommand();
        cmd.setOperatorId(operatorId);
        SignServiceRequestCommand.SignServiceRequestItemCommand item =
            new SignServiceRequestCommand.SignServiceRequestItemCommand();
        item.setId(serviceRequestId);
        item.setSignerId(operatorId);
        item.setSignTime(LocalDateTime.now());
        cmd.setItem(item);
        return cmd;
    }

    /**
     * 按就诊、方号与代煎 activityId 筛选 companion ServiceRequest 行。
     *
     * <p>仅 {@code based_on} → 同方药嘱 id；不读 SR.prescription_no。</p>
     *
     * @param encounterId    就诊主键
     * @param prescriptionNo 中药处方号（用于解析同方药嘱 id）
     * @param activityId     代煎诊疗项目主键
     * @return 命中行列表；无匹配时为空列表
     */
    private List<ServiceRequestListModel> findDecoctionServiceRequests(
        Long encounterId,
        String prescriptionNo,
        Long activityId
    ) {
        Set<Long> medicationRequestIds =
            this.collectMedicationRequestIdsForPrescription(encounterId, prescriptionNo);
        if (medicationRequestIds.isEmpty()) {
            return List.of();
        }
        List<ServiceRequestListModel> rows = serviceRequestQueryService.listServiceRequestsByGenerateSource(
            encounterId, GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        List<ServiceRequestListModel> matched = new ArrayList<>();
        for (ServiceRequestListModel row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (!activityId.equals(row.getActivityId())) {
                // 非代煎 activity 的 SR 不参与本处方 void
                continue;
            }
            // Gate：仅 DRAFT/ACTIVE 视为有效代煎 companion；STOPPED/COMPLETED/CANCELLED 不挡再补充
            if (!this.isLiveDecoctionCompanionStatus(row.getStatusEnum())) {
                continue;
            }
            // Pre：仅 based_on 命中同方药嘱
            if (!TableNameConstants.MED_MEDICATION_REQUEST.equals(trimToEmpty(row.getBasedOnTable()))) {
                continue;
            }
            if (row.getBasedOnId() == null || !medicationRequestIds.contains(row.getBasedOnId())) {
                continue;
            }
            matched.add(row);
        }
        return matched;
    }

    /**
     * 是否为仍占用「方头代煎」语义的 companion 请求态（可 void / 可拦补充）。
     *
     * @param statusEnum 服务申请状态 code
     * @return {@code DRAFT}/{@code ACTIVE} 时 {@code true}
     */
    private boolean isLiveDecoctionCompanionStatus(String statusEnum) {
        String status = StringUtils.trimToEmpty(statusEnum);
        if (RequestStatus.DRAFT.getCode().equals(status)) {
            return true;
        }
        if (RequestStatus.ACTIVE.getCode().equals(status)) {
            return true;
        }
        return false;
    }

    /**
     * 解析就诊下同中药处方号可匹配代煎 based_on 的药嘱主键集合。
     *
     * <p>含该方正向药嘱，以及 {@code refund_medicine_id} 指向这些正向单的负向退药嘱（退代煎绑退药嘱时仍可按原方 CP 命中）。</p>
     *
     * @param encounterId    就诊主键
     * @param prescriptionNo 中药处方号
     * @return 药嘱主键集合；无命中时为空集
     */
    private Set<Long> collectMedicationRequestIdsForPrescription(Long encounterId, String prescriptionNo) {
        Set<Long> ids = new LinkedHashSet<>();
        if (encounterId == null || StringUtils.isEmpty(prescriptionNo)) {
            return ids;
        }
        List<MedicationRequestListQueryModel> rows =
            this.medicationRequestQueryService.listMedicationRequestListModelsByPrescriptionNos(
                List.of(prescriptionNo.trim()));
        for (MedicationRequestListQueryModel row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            // Pre：同方号可能跨就诊，须限制在当前就诊
            if (!encounterId.equals(row.getEncounterId())) {
                continue;
            }
            ids.add(row.getId());
        }
        if (ids.isEmpty()) {
            return ids;
        }
        // Pre：退代煎 may based_on 负向退药嘱，一并纳入匹配集
        List<MedicationRequest> refundRows =
            this.medicationRequestQueryService.listByRefundMedicineIdIn(ids);
        for (MedicationRequest refundRow : refundRows) {
            if (refundRow == null || refundRow.getId() == null) {
                continue;
            }
            if (Boolean.TRUE.equals(refundRow.getIsDeleted())) {
                continue;
            }
            if (!encounterId.equals(refundRow.getEncounterId())) {
                continue;
            }
            ids.add(refundRow.getId());
        }
        return ids;
    }

    /**
     * 清理同方仍残留的未计费代煎费（锚定同方代煎 SR 的孤儿 DRAFT/PLANNED）。
     *
     * @param encounterId    就诊主键
     * @param prescriptionNo 处方号
     * @param activityId     代煎诊疗项目主键
     * @param operatorId     操作人；为空时跳过（与 SR 写侧一致）
     */
    private void voidResidualUnbilledDecoctionCharges(
        Long encounterId,
        String prescriptionNo,
        Long activityId,
        Long operatorId
    ) {
        // Gate：无操作人时不改写费用，避免审计链断裂
        if (operatorId == null) {
            return;
        }
        List<ChargeItem> items = chargeItemQueryService.listChargeItemsByEncounterAndProduct(
            encounterId, TableNameConstants.WKF_ACTIVITY_DEFINITION, activityId);
        String trimmedPrescriptionNo = prescriptionNo.trim();
        Map<Long, String> prescriptionByServiceRequestId =
            this.mapServiceRequestPrescriptionNos(items);
        Set<Long> serviceRequestIds = new LinkedHashSet<>();
        for (ChargeItem chargeItem : items) {
            if (chargeItem == null || Boolean.TRUE.equals(chargeItem.getIsDeleted())) {
                continue;
            }
            // Pre：仅 SR.based_on→药嘱可反查到方号的费用行
            if (!trimmedPrescriptionNo.equals(
                this.resolveChargeLinkedPrescriptionNo(chargeItem, prescriptionByServiceRequestId))) {
                continue;
            }
            String status = StringUtils.trimToEmpty(chargeItem.getStatusEnum());
            // 仅扫未计费态；已收费/结算由门控拒绝，须走退费
            if (!ChargeItemStatus.DRAFT.getCode().equals(status)
                && !ChargeItemStatus.PLANNED.getCode().equals(status)) {
                continue;
            }
            if (!TableNameConstants.WKF_SERVICE_REQUEST.equals(trimToEmpty(chargeItem.getServiceTable()))) {
                continue;
            }
            if (chargeItem.getServiceId() == null) {
                continue;
            }
            serviceRequestIds.add(chargeItem.getServiceId());
        }
        for (Long serviceId : serviceRequestIds) {
            // Cmd：孤儿 PLANNED 先回退再作废草稿
            chargeItemDomainService.rollbackPlannedChargeItemsAfterAdviceSignedOff(
                encounterId, TableNameConstants.WKF_SERVICE_REQUEST, serviceId);
            chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(
                ServiceAdviceCommandAssembler.toVoidDraftChargeAnchor(
                    encounterId, TableNameConstants.WKF_SERVICE_REQUEST, serviceId, operatorId));
        }
    }

    /**
     * 批量反查代煎收费行锚定的中药方头处方号（委托共享解析器：based_on→药嘱，负向回原方 CP）。
     *
     * @param items 代煎产品收费行
     * @return serviceRequestId → 中药处方号
     */
    private Map<Long, String> mapServiceRequestPrescriptionNos(List<ChargeItem> items) {
        Set<Long> serviceRequestIds = new LinkedHashSet<>();
        if (items != null) {
            for (ChargeItem chargeItem : items) {
                if (chargeItem == null || chargeItem.getServiceId() == null) {
                    continue;
                }
                if (!TableNameConstants.WKF_SERVICE_REQUEST.equals(trimToEmpty(chargeItem.getServiceTable()))) {
                    continue;
                }
                serviceRequestIds.add(chargeItem.getServiceId());
            }
        }
        return this.tcmDecoctionBasedOnPrescriptionResolver.mapServiceRequestIdsToHerbalPrescriptionNos(
            serviceRequestIds);
    }

    /**
     * 解析收费行关联中药处方号：仅 SR.based_on→药嘱；无法反查则空串。
     *
     * @param chargeItem                     收费行
     * @param prescriptionByServiceRequestId SR→方号字典（已按 based_on 解析）
     * @return 处方号；无法解析时空串
     */
    private String resolveChargeLinkedPrescriptionNo(
        ChargeItem chargeItem,
        Map<Long, String> prescriptionByServiceRequestId
    ) {
        if (chargeItem == null) {
            return "";
        }
        if (!TableNameConstants.WKF_SERVICE_REQUEST.equals(trimToEmpty(chargeItem.getServiceTable()))
            || chargeItem.getServiceId() == null) {
            return "";
        }
        String fromRequest = prescriptionByServiceRequestId.get(chargeItem.getServiceId());
        if (StringUtils.isEmpty(fromRequest)) {
            return "";
        }
        return fromRequest.trim();
    }

    /**
     * 解析租户代煎诊疗项目主键（名称精确匹配 {@link DoctorStationTcmConstants#DECOCTION_ACTIVITY_NAME}）。
     *
     * @return 代煎 activity 主键
     * @throws BusinessException 未配置或名称不唯一时
     */
    private Long requireDecoctionActivityId() {
        try {
            return activityDefinitionQueryService.requireIdByExactName(
                DoctorStationTcmConstants.DECOCTION_ACTIVITY_NAME);
        } catch (RuntimeException ex) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                "代煎诊疗项目未配置，请联系管理员维护");
        }
    }

    /**
     * 加载代煎诊疗产品读模型，供 SR 与挂费单位/类目解析。
     *
     * @param activityId 代煎 activity 主键
     * @return 产品读模型
     * @throws BusinessException 读模型未命中时
     */
    private ProductDefinitionDisplayDto loadDecoctionActivityProductRow(Long activityId) {
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        lookup.setProductDefinitionId(activityId);
        Map<String, ProductDefinitionDisplayDto> map = productDefinitionDisplayTerminologyAssembler.loadAsMap(List.of(lookup));
        String key = ProductDefinitionKeyUtil.buildCompositeKey(
            TableNameConstants.WKF_ACTIVITY_DEFINITION, activityId);
        ProductDefinitionDisplayDto row = map.get(key);
        if (row == null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                "未找到代煎诊疗活动定义，请先在基础数据维护「代煎中药」项目。");
        }
        return row;
    }

    /**
     * 解析代煎诊疗项目零售价（SALE）定价行。
     *
     * @param activityId 代煎 activity 主键
     * @return 零售价定价行
     * @throws BusinessException 未配置零售价时
     */
    private ChargeItemDefinition requireDecoctionSaleLine(Long activityId) {
        ChargeItemDefinitionQueryCommand priceQuery = new ChargeItemDefinitionQueryCommand();
        priceQuery.setInstanceTable(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        priceQuery.setInstanceIds(List.of(activityId));
        List<ChargeItemDefinition> priceLines = chargeItemDefinitionQueryService.findPriceLinesByProjects(priceQuery);
        for (ChargeItemDefinition line : priceLines) {
            if (line != null && PriceType.SALE.getCode().equals(line.getPriceTypeCode())) {
                return line;
            }
        }
        throw new BusinessException(
            ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
            "代煎诊疗项目未配置零售价，请联系管理员维护");
    }
}
