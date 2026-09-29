/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.caserecord.support;

import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.openhis.health.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDiagnosisQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterLocationQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterParticipantQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterContextQueryService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.administration.patient.valueobject.Address;
import com.openhis.health.domain.administration.patient.valueobject.ContactPerson;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.ClinicalDiagnosisPresentationQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionBatchItemQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.clinical.procedure.entity.Procedure;
import com.openhis.health.domain.clinical.procedure.queries.model.ProcedurePerformerQueryModel;
import com.openhis.health.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.openhis.health.domain.clinical.procedure.service.ProcedureQueryService;
import com.openhis.health.domain.clinical.proceduredefinition.queries.model.ProcedureDefinitionSearchItemQueryModel;
import com.openhis.health.domain.clinical.proceduredefinition.service.ProcedureDefinitionQueryService;
import com.openhis.health.domain.custom.encounterhistory.queries.model.EncounterHistoryMilestoneQueryModel;
import com.openhis.health.domain.custom.encounterhistory.service.EncounterHistoryQueryService;
import com.openhis.health.domain.mrms.caserecord.resolver.AdmissionConditionResolver;
import com.openhis.health.domain.mrms.caserecord.resolver.HomepageSelfpayAmtResolver;
import com.openhis.health.domain.mrms.caserecord.vo.DiagnosisLineSnapshot;
import com.openhis.health.domain.mrms.caserecord.vo.EncounterSnapshot;
import com.openhis.health.domain.mrms.caserecord.vo.FeeSummarySnapshot;
import com.openhis.health.domain.mrms.caserecord.vo.NursingCareDaysSnapshot;
import com.openhis.health.domain.mrms.caserecord.vo.PatientSnapshot;
import com.openhis.health.domain.mrms.caserecord.vo.ProcedureLineSnapshot;
import com.openhis.health.domain.mrms.caserecord.vo.TransferLineSnapshot;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.share.constants.ClinicalProcedureConstants;
import com.openhis.health.domain.share.constants.CustomConstants;
import com.openhis.health.domain.share.constants.DoctorStationDiagnosisConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.MrmsConstants;
import com.openhis.health.domain.share.constants.ServiceRequestCategoryConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.EncounterParticipantRole;
import com.openhis.health.domain.share.enums.IptPatientDiseaseDiagType;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.utils.AdministrativeGenderOutboundUtil;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.health.domain.workflow.servicerequest.queries.model.NursingLevelOrderLineQueryModel;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.openhis.whale.core.application.util.StringUtils.firstNonBlank;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;
import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 病案首页自动取值只读查询服务（DM-MRMS-001 §4）。
 *
 * <p>跨聚合读：注入各 bounded context 的 {@code *QueryService} 组装快照，不写库（B.6.4）。</p>
 * <p>写侧合并由 {@link CaseRecordHomepageDomainService#mergeAutoFillFromHis} 负责。</p>
 */
@Service
@RequiredArgsConstructor
public class CaseRecordAutoFillSupport {

    private final PatientQueryService patientQueryService;
    private final EncounterQueryService encounterQueryService;
    private final EncounterContextQueryService encounterContextQueryService;
    private final AccountQueryService accountQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final LocationQueryService locationQueryService;
    private final OrganizationQueryService organizationQueryService;
    private final EncounterHistoryQueryService encounterHistoryQueryService;
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;
    private final ProcedureQueryService procedureQueryService;
    private final ProcedureDefinitionQueryService procedureDefinitionQueryService;
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final DeviceDefinitionQueryService deviceDefinitionQueryService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final Optional<AdmissionConditionResolver> admissionConditionResolver;

    private final Optional<HomepageSelfpayAmtResolver> homepageSelfpayAmtResolver;

    /**
     * 加载患者基础信息快照（adm_patient + 地址/联系人）。
     *
     * @param patientId 患者主键
     * @return 快照；不存在或 id 为空时返回 null
     */
    public PatientSnapshot loadPatient(Long patientId) {
        if (patientId == null) {
            return null;
        }
        // 跨聚合读：adm_patient
        Patient patient = patientQueryService.findOptionalPatientEntityById(patientId).orElse(null);
        if (patient == null) {
            return null;
        }
        PatientSnapshot snap = new PatientSnapshot();
        snap.setPatientId(patient.getId());
        snap.setName(patient.getName());
        snap.setGenderCode(AdministrativeGenderOutboundUtil.toHomepageGenderCode(patient.getGenderEnum()));
        snap.setBirthDate(patient.getBirthDate());
        snap.setCertno(patient.getIdCard());
        snap.setPhone(patient.getPhone());
        snap.setCurrAddr(this.formatPatientAddress(patient.getAddress()));
        // 国籍/民族列命名易混：country_code→ntly，nationality_code→naty
        snap.setNationalityCode(trimToNull(patient.getCountryCode()));
        snap.setNatyCode(trimToNull(patient.getNationalityCode()));
        snap.setMaritalStatusCode(trimToNull(patient.getMaritalStatusEnum()));
        snap.setPrfsCode(trimToNull(patient.getPrfsEnum()));
        snap.setWorkCompany(trimToNull(patient.getWorkCompany()));
        snap.setNativePlace(trimToNull(patient.getNativePlace()));
        ContactPerson contact = patient.getLinkName();
        // 联系人：姓名/电话/关系
        if (contact != null) {
            snap.setContactName(trimToNull(contact.getName()));
            snap.setContactPhone(trimToNull(contact.getTelcom()));
            if (contact.getRelationCode() != null) {
                snap.setContactRelationCode(String.valueOf(contact.getRelationCode()));
            }
        }
        return snap;
    }

    /**
     * 加载就诊快照：出入院时间、科室、病区床位、入院途径、参与者。
     *
     * <p>出院时间优先取出院医嘱计划时间（{@code occurrence_start_time}），
     * 再回落 {@code Encounter.endTime}（护士办理出院写入；清床/结算不得覆盖）。</p>
     *
     * @param encounterId 就诊主键
     * @return 快照；不存在时返回 null
     */
    public EncounterSnapshot loadEncounter(Long encounterId) {
        if (encounterId == null) {
            return null;
        }
        // 跨聚合读：adm_encounter + 参与者 + 位置轨迹
        Encounter encounter = encounterQueryService.findOptionalEncounterEntityById(encounterId).orElse(null);
        if (encounter == null) {
            return null;
        }
        EncounterSnapshot snap = new EncounterSnapshot();
        snap.setEncounterId(encounter.getId());
        snap.setPatientId(encounter.getPatientId());
        snap.setIptNo(encounter.getBusNo());
        snap.setAdmTime(encounter.getStartTime());
        // 业务出院时间：出院医嘱计划时间优先，避免历史脏 end_time（清床/结算误写）污染首页
        snap.setDscgTime(this.resolveBusinessDischargeTime(encounterId, encounter.getEndTime()));
        // 入院途径：in_way 与 4401 adm_way 同码（V28）；禁止拷贝入院类型 admit_source（如 6=常规）
        snap.setAdmWayCode(MrmsConstants.mapInWayCodeToAdmWayCode(encounter.getInWayCode()));
        // 付费方式：入院登记已开户；无医保户→全自费，有户按 insutype 映射国标 1～9
        snap.setPayWayCode(this.resolvePayWayCode(encounterId));
        applyDepartments(snap, encounter);
        applyWardAndBed(snap, encounterId);
        applyParticipants(snap, encounterQueryService.listDisplayEncounterParticipants(encounterId));
        return snap;
    }

    /**
     * 解析首页付费方式：优先就诊医保账户 {@code insutype}，无医保户则全自费。
     *
     * <p>调用关系：{@link #loadEncounter}；映射见 {@link MrmsConstants#resolvePayWayCode}。
     * 有医保户但险种尚未回写时返回 null，打开页保留空选、待读卡或手工。</p>
     *
     * @param encounterId 就诊主键
     * @return 国标付费方式码；无法判定时 null
     */
    private String resolvePayWayCode(Long encounterId) {
        // 跨聚合读：adm_account 医保户
        Account miAccount = accountQueryService.getYbAccount(encounterId);
        if (miAccount == null) {
            return MrmsConstants.resolvePayWayCode(false, null);
        }
        return MrmsConstants.resolvePayWayCode(true, miAccount.getInsutype());
    }

    /**
     * 解析业务出院时间：有效出院类医嘱的计划时间优先，否则回落 encounter.endTime。
     *
     * <p>跳过已取消出院嘱；多条有效嘱时取扫描到的最后一条（通常为最近一次）。</p>
     *
     * @param encounterId      就诊主键
     * @param encounterEndTime 就诊结束时间（办理出院写入的计划时间；可空）
     * @return 业务出院时间；皆空则 null
     */
    private LocalDateTime resolveBusinessDischargeTime(Long encounterId, LocalDateTime encounterEndTime) {
        if (encounterId == null) {
            return encounterEndTime;
        }
        Map<Long, LocalDateTime> planned =
                serviceRequestQueryService.mapDischargePlannedTimeByEncounterIds(List.of(encounterId));
        LocalDateTime fromAdvice = planned.get(encounterId);
        if (fromAdvice != null) {
            return fromAdvice;
        }
        return encounterEndTime;
    }

    /**
     * 按护理级别医嘱累计特级/一/二/三级护理天数。
     *
     * <p>起点：校对日（含）；止点：停嘱日或出院日/今日（不含止点日，记进不计出）；至少 1 天。</p>
     *
     * @param encounterId 就诊主键
     * @param dscgTime    出院时间；空则止于今日
     * @return 天数快照；无护理医嘱时各字段为 null
     */
    public NursingCareDaysSnapshot sumNursingCareDays(Long encounterId, LocalDateTime dscgTime) {
        NursingCareDaysSnapshot snap = new NursingCareDaysSnapshot();
        if (encounterId == null) {
            return snap;
        }
        List<NursingLevelOrderLineQueryModel> lines =
                serviceRequestQueryService.listNursingLevelOrderLinesForCaseRecord(encounterId);
        if (lines.isEmpty()) {
            return snap;
        }
        Set<Long> activityIds = new HashSet<>();
        for (NursingLevelOrderLineQueryModel line : lines) {
            if (line.getActivityId() != null) {
                activityIds.add(line.getActivityId());
            }
        }
        Map<Long, String> activityNameById = new HashMap<>();
        if (!activityIds.isEmpty()) {
            for (ActivityDefinition def : activityDefinitionQueryService.findAllByIds(activityIds)) {
                if (def != null && def.getId() != null) {
                    activityNameById.put(def.getId(), def.getName());
                }
            }
        }
        LocalDate endExclusive = LocalDate.now().plusDays(1);
        // 已出院：止于出院日（不含出院当日，与滚费记进不计出对齐）
        if (dscgTime != null) {
            endExclusive = dscgTime.toLocalDate();
        }
        int spga = 0;
        int lv1 = 0;
        int scd = 0;
        int lv3 = 0;
        for (NursingLevelOrderLineQueryModel line : lines) {
            LocalDate start = line.getCheckTime().toLocalDate();
            LocalDate stopExclusive = endExclusive;
            // 已停嘱：止于停嘱日
            if (line.getStopTime() != null) {
                stopExclusive = line.getStopTime().toLocalDate();
            }
            int days = this.countInclusiveExclusiveDays(start, stopExclusive);
            if (days <= 0) {
                continue;
            }
            String level = this.resolveNursingLevelBucket(activityNameById.get(line.getActivityId()));
            // 按级别桶累加
            if ("spga".equals(level)) {
                spga += days;
            } else if ("lv1".equals(level)) {
                lv1 += days;
            } else if ("scd".equals(level)) {
                scd += days;
            } else if ("lv3".equals(level)) {
                lv3 += days;
            }
        }
        if (spga > 0) {
            snap.setSpgaNurscareDays(spga);
        }
        if (lv1 > 0) {
            snap.setLv1NurscareDays(lv1);
        }
        if (scd > 0) {
            snap.setScdNurscareDays(scd);
        }
        if (lv3 > 0) {
            snap.setLv3NurscareDays(lv3);
        }
        return snap;
    }

    /**
     * 按就诊收费明细汇总医疗费总额与 4401 费用分项（供费用快照重算写库）。
     *
     * <p>口径：待结算+已结算占用（与住院费用占用合计一致）；分项按目录
     * {@code mr_settlement_type_code} 映射，未维护归入 {@code oth_fee}。
     * 调用方：{@code CaseRecordHomepageDomainService}「重新统计」/ 空快照补种。</p>
     *
     * @param encounterId 就诊主键
     * @return 费用快照；无费用时总额为 0、分项空
     */
    public FeeSummarySnapshot sumFeeSummary(Long encounterId) {
        FeeSummarySnapshot snap = new FeeSummarySnapshot();
        snap.setMedfeeSumamt(BigDecimal.ZERO);
        snap.setFeeItems(new LinkedHashMap<>());
        if (encounterId == null) {
            return snap;
        }
        BigDecimal total = chargeItemQueryService.sumInpatientChargedOccupancyTotalPriceByEncounterId(encounterId);
        snap.setMedfeeSumamt(MonetaryAmountSupport.scaleCalc(total));
        // 预置 1～28 分项为 0，保证首页费用区完整回填
        Map<String, BigDecimal> feeItems = new LinkedHashMap<>();
        for (int i = 1; i < MrmsConstants.MR_SETTLEMENT_TO_4401_FEE_KEY.length; i++) {
            String feeKey = MrmsConstants.MR_SETTLEMENT_TO_4401_FEE_KEY[i];
            if (feeKey != null) {
                feeItems.put(feeKey, MonetaryAmountSupport.scaleCalc(BigDecimal.ZERO));
            }
        }
        snap.setFeeItems(feeItems);
        ChargeItemQueryCommand cmd = new ChargeItemQueryCommand();
        cmd.setEncounterId(encounterId);
        cmd.setStatusEnumIn(List.of(
                ChargeItemStatus.PENDING_SETTLEMENT.getCode(),
                ChargeItemStatus.SETTLED.getCode()));
        cmd.setExcludeSupersededSettledLines(Boolean.TRUE);
        List<ChargeItem> rows = chargeItemQueryService.listChargeItemsByEncounterId(cmd);
        if (rows == null || rows.isEmpty()) {
            return snap;
        }
        Map<String, Map<Long, String>> mrCodeByTableAndProductId = this.loadMrSettlementTypeByProduct(rows);
        for (ChargeItem row : rows) {
            if (row == null || row.getTotalPrice() == null) {
                continue;
            }
            String mrCode = this.resolveMrSettlementTypeCode(row, mrCodeByTableAndProductId);
            String feeKey = MrmsConstants.to4401FeeExtensionKey(mrCode);
            BigDecimal previous = feeItems.get(feeKey);
            if (previous == null) {
                previous = BigDecimal.ZERO;
            }
            feeItems.put(feeKey, MonetaryAmountSupport.scaleCalc(previous.add(row.getTotalPrice())));
        }
        snap.setFeeItems(feeItems);
        homepageSelfpayAmtResolver
                .map(resolver -> resolver.resolveByEncounterId(encounterId, snap.getMedfeeSumamt()))
                .ifPresent(snap::setSelfpayAmt);
        return snap;
    }

    /**
     * 批量预载各产品表的病案结算类型码。
     *
     * @param rows 收费明细
     * @return productTable → (productId → mrSettlementTypeCode)
     */
    private Map<String, Map<Long, String>> loadMrSettlementTypeByProduct(List<ChargeItem> rows) {
        Set<Long> medIds = new LinkedHashSet<>();
        Set<Long> actIds = new LinkedHashSet<>();
        Set<Long> deviceIds = new LinkedHashSet<>();
        for (ChargeItem row : rows) {
            if (row == null || row.getProductId() == null) {
                continue;
            }
            String table = trimToNull(row.getProductTable());
            if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(table)) {
                medIds.add(row.getProductId());
            } else if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(table)) {
                actIds.add(row.getProductId());
            } else if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(table)) {
                deviceIds.add(row.getProductId());
            }
        }
        Map<String, Map<Long, String>> result = new HashMap<>();
        if (!medIds.isEmpty()) {
            Map<Long, String> medMap = new HashMap<>();
            for (MedicationDefinition def : medicationDefinitionQueryService.findAllByIds(medIds)) {
                if (def != null && def.getId() != null) {
                    medMap.put(def.getId(), trimToNull(def.getMrSettlementTypeCode()));
                }
            }
            result.put(TableNameConstants.MED_MEDICATION_DEFINITION, medMap);
        }
        if (!actIds.isEmpty()) {
            Map<Long, String> actMap = new HashMap<>();
            for (ActivityDefinition def : activityDefinitionQueryService.findAllByIds(actIds)) {
                if (def != null && def.getId() != null) {
                    actMap.put(def.getId(), trimToNull(def.getMrSettlementTypeCode()));
                }
            }
            result.put(TableNameConstants.WKF_ACTIVITY_DEFINITION, actMap);
        }
        if (!deviceIds.isEmpty()) {
            Map<Long, String> deviceMap = new HashMap<>();
            for (DeviceDefinition def : deviceDefinitionQueryService.getAllDeviceDefinitions(deviceIds)) {
                if (def != null && def.getId() != null) {
                    deviceMap.put(def.getId(), trimToNull(def.getMrSettlementTypeCode()));
                }
            }
            result.put(TableNameConstants.ADM_DEVICE_DEFINITION, deviceMap);
        }
        return result;
    }

    /**
     * 解析单行收费明细的病案结算类型码。
     *
     * @param row                       收费行
     * @param mrCodeByTableAndProductId 预载映射
     * @return 术语码；无则 null
     */
    private String resolveMrSettlementTypeCode(
            ChargeItem row, Map<String, Map<Long, String>> mrCodeByTableAndProductId) {
        String table = trimToNull(row.getProductTable());
        if (table == null || row.getProductId() == null) {
            return null;
        }
        Map<Long, String> byId = mrCodeByTableAndProductId.get(table);
        if (byId == null) {
            return null;
        }
        return byId.get(row.getProductId());
    }

    /**
     * 写入就诊快照入院/出院科别：入院取就诊机构；出院优先末次完成病区机构，
     * 其次住院上下文当前科室，最后回落就诊机构（无转科时与入院同）。
     *
     * @param snap      待填充就诊快照
     * @param encounter 就诊实体
     */
    private void applyDepartments(EncounterSnapshot snap, Encounter encounter) {
        // 入院科别：就诊所属机构
        if (encounter.getOrganizationId() != null) {
            applyOrganization(
                    snap,
                    organizationQueryService.loadOrganizationByIdForDisplay(encounter.getOrganizationId()),
                    true);
        }
        // 出院科别①：末次已完成病区所属机构（转科/清床后最准确）
        encounterQueryService.findLastCompletedWardLocationIdByEncounterId(encounter.getId())
                .flatMap(locationQueryService::findOptionalById)
                .ifPresent(ward -> applyOrganization(
                        snap,
                        organizationQueryService.loadOrganizationByIdForDisplay(ward.getOrganizationId()),
                        false));
        // 出院科别②：尚无完成病区轨迹时用住院上下文当前科室（与出院病区同源）
        if (this.isBlankDscgCaty(snap)) {
            Optional<InpatientEncounterContextQueryModel> ctxOpt =
                    encounterContextQueryService.getInpatientContextByEncounterId(encounter.getId());
            if (ctxOpt.isPresent()) {
                InpatientEncounterContextQueryModel ctx = ctxOpt.get();
                if (ctx.getOrganizationId() != null) {
                    applyOrganization(
                            snap,
                            organizationQueryService.loadOrganizationByIdForDisplay(ctx.getOrganizationId()),
                            false);
                } else if (trimToNull(ctx.getOrganizationName()) != null) {
                    snap.setDscgCatyName(trimToNull(ctx.getOrganizationName()));
                }
            }
        }
        // 出院科别③：无转科时与入院同为就诊机构（出院时间已有、病区仍在活动态时常见）
        if (this.isBlankDscgCaty(snap) && encounter.getOrganizationId() != null) {
            applyOrganization(
                    snap,
                    organizationQueryService.loadOrganizationByIdForDisplay(encounter.getOrganizationId()),
                    false);
        }
    }

    /**
     * 出院科别 code/name 是否均未写入。
     *
     * @param snap 就诊快照
     * @return true 表示出院科别仍空
     */
    private boolean isBlankDscgCaty(EncounterSnapshot snap) {
        return trimToNull(snap.getDscgCatyCode()) == null && trimToNull(snap.getDscgCatyName()) == null;
    }

    /**
     * 将机构<strong>医保编码</strong>/名称写入快照入院或出院科别字段。
     *
     * <p>4401/4101A {@code adm_caty}/{@code dscg_caty} 须为科室维护的 {@code yb_no}
     *（如 {@code A50.13}，长度 1–10），禁止写入院内 {@code bus_no}（如 {@code OR0007.OR0022}）。</p>
     *
     * @param snap       就诊快照
     * @param organization 组织实体
     * @param admission  true 写 admCaty*；false 写 dscgCaty*
     */
    private void applyOrganization(EncounterSnapshot snap, Organization organization, boolean admission) {
        if (organization == null) {
            return;
        }
        // 科别出站码 = 科室医保编码；无医保编码则不写 code，避免误用 bus_no 被平台拒收
        String code = trimToNull(organization.getYbNo());
        String name = trimToNull(organization.getYbName());
        if (name == null) {
            name = trimToNull(organization.getName());
        }
        // 入院科别写入 adm_* 字段
        if (admission) {
            if (code != null) {
                snap.setAdmCatyCode(code);
            }
            if (name != null) {
                snap.setAdmCatyName(name);
            }
            return;
        }
        // 出院科别写入 dscg_* 字段
        if (code != null) {
            snap.setDscgCatyCode(code);
        }
        if (name != null) {
            snap.setDscgCatyName(name);
        }
    }

    /**
     * 按参与者角色映射首页人员字段（主治/主任/住院医师/责任护士）。
     *
     * @param snap         就诊快照
     * @param participants 展示参与者（活动优先，无则末次 COMPLETED）
     */
    private void applyParticipants(EncounterSnapshot snap, List<EncounterParticipantQueryModel> participants) {
        if (participants == null || participants.isEmpty()) {
            return;
        }
        Set<Long> practitionerIds = new HashSet<>();
        for (EncounterParticipantQueryModel participant : participants) {
            if (participant.getPractitionerId() != null) {
                practitionerIds.add(participant.getPractitionerId());
            }
        }
        Map<Long, String> nameById = new HashMap<>();
        if (!practitionerIds.isEmpty()) {
            for (Practitioner practitioner : practitionerQueryService.findAllByIds(practitionerIds)) {
                if (practitioner.getId() != null && isNotEmpty(practitioner.getName())) {
                    nameById.put(practitioner.getId(), practitioner.getName());
                }
            }
        }
        for (EncounterParticipantQueryModel participant : participants) {
            if (participant.getPractitionerId() == null) {
                continue;
            }
            String roleCode = participant.getRoleCode();
            Long practitionerId = participant.getPractitionerId();
            String name = nameById.get(practitionerId);
            // 按参与者角色映射首页人员字段
            if (EncounterParticipantRole.ATTENDING_DOCTOR.getCode().equals(roleCode)) {
                snap.setAtddrPractitionerId(practitionerId);
                snap.setAtddrName(name);
            } else if (EncounterParticipantRole.CHIEF_DOCTOR.getCode().equals(roleCode)) {
                snap.setChfdrName(name);
            } else if (EncounterParticipantRole.ADMITTER.getCode().equals(roleCode)) {
                snap.setIptDrName(name);
            } else if (EncounterParticipantRole.PRIMARY_NURSE.getCode().equals(roleCode)) {
                snap.setRespNursName(name);
            }
        }
    }

    /**
     * 解析离院方式字典码（custom encounter history）。
     *
     * @param encounterId 就诊主键
     * @return 离院方式 code，无法解析时 null
     */
    public String resolveDischargeWayCode(Long encounterId) {
        if (encounterId == null) {
            return null;
        }
        var way = encounterHistoryQueryService.resolveDischargeWay(encounterId);
        if (way == null) {
            return null;
        }
        return way.getOutWayCode();
    }

    /**
     * 列出就诊转科/入科里程碑，用于首页转科摘要。
     *
     * @param encounterId 就诊主键
     * @return 转科行列表，无数据时 empty
     */
    public List<TransferLineSnapshot> listTransfers(Long encounterId) {
        if (encounterId == null) {
            return List.of();
        }
        List<EncounterHistoryMilestoneQueryModel> milestones =
                encounterHistoryQueryService.listMilestonesByEncounter(encounterId);
        if (milestones.isEmpty()) {
            return List.of();
        }
        List<EncounterHistoryMilestoneQueryModel> transferMilestones = new ArrayList<>();
        Set<Long> orgIds = new LinkedHashSet<>();
        for (EncounterHistoryMilestoneQueryModel milestone : milestones) {
            // 仅转科/入科里程碑参与转科摘要
            if (!isTransferMilestone(milestone.getName())) {
                continue;
            }
            transferMilestones.add(milestone);
            if (milestone.getOriginalOrganizationId() != null) {
                orgIds.add(milestone.getOriginalOrganizationId());
            }
            if (milestone.getTargetOrganizationId() != null) {
                orgIds.add(milestone.getTargetOrganizationId());
            }
        }
        if (transferMilestones.isEmpty()) {
            return List.of();
        }
        Map<Long, String> orgNames = organizationQueryService.findOrganizationNamesByIds(orgIds);
        List<TransferLineSnapshot> lines = new ArrayList<>();
        for (EncounterHistoryMilestoneQueryModel milestone : transferMilestones) {
            TransferLineSnapshot line = new TransferLineSnapshot();
            line.setFromDeptName(resolveOrgName(orgNames, milestone.getOriginalOrganizationId()));
            line.setToDeptName(resolveOrgName(orgNames, milestone.getTargetOrganizationId()));
            lines.add(line);
        }
        return lines;
    }

    /**
     * 里程碑名称是否为转科或入科（参与转科摘要）。
     *
     * @param name 里程碑 name
     * @return 转科/入科为 true
     */
    private boolean isTransferMilestone(String name) {
        if (name == null) {
            return false;
        }
        return CustomConstants.EncounterHistoryMilestoneName.TRANSFER.equals(name)
                || CustomConstants.EncounterHistoryMilestoneName.ADMISSION_TO_DEPT.equals(name);
    }

    /**
     * 从预加载 org 名称 Map 解析科室展示名。
     *
     * @param orgNames       orgId → 名称
     * @param organizationId 组织 id
     * @return trim 后名称；缺失时 null
     */
    private String resolveOrgName(Map<Long, String> orgNames, Long organizationId) {
        if (organizationId == null || orgNames == null) {
            return null;
        }
        String name = orgNames.get(organizationId);
        if (name == null || name.isBlank()) {
            return null;
        }
        return name.trim();
    }

    /**
     * 列出出院诊断快照行（含主/其他，由 merge 侧按类型拆分）。
     *
     * @param encounterId 就诊主键
     * @return 诊断快照列表
     */
    public List<DiagnosisLineSnapshot> listDischargeDiagnoses(Long encounterId) {
        return listDiagnosesByScope(encounterId, DiagnosisScope.DISCHARGE);
    }

    /**
     * 列出入院诊断快照行。
     *
     * @param encounterId 就诊主键
     * @return 诊断快照列表
     */
    public List<DiagnosisLineSnapshot> listAdmissionDiagnoses(Long encounterId) {
        return listDiagnosesByScope(encounterId, DiagnosisScope.ADMISSION);
    }

    /**
     * 列出就诊已完成手术/操作快照（国临 PD 术式行；纯加收不占行）。
     *
     * <p>对齐 DM-MRMS-001 §7 BR-MRMS-HP-PR-002～006；手术时间优先 procedure_time。</p>
     *
     * @param encounterId 就诊主键
     * @return 手术快照列表
     */
    public List<ProcedureLineSnapshot> listProcedures(Long encounterId) {
        if (encounterId == null) {
            return Collections.emptyList();
        }
        List<ProcedureRecordQueryModel> completedRows =
                procedureQueryService.listNetRemainingCompletedProcedureRecords(encounterId, null, null);
        // 无已完成手术记录时返回空列表
        if (completedRows.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> serviceRequestIds = new ArrayList<>();
        List<Long> procedureIds = new ArrayList<>();
        for (ProcedureRecordQueryModel row : completedRows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            procedureIds.add(row.getId());
            if (TableNameConstants.WKF_SERVICE_REQUEST.equals(row.getRequestTable()) && row.getRequestId() != null) {
                serviceRequestIds.add(row.getRequestId());
            }
        }
        Map<Long, Procedure> procedureById = indexProcedures(procedureQueryService.listByIds(procedureIds));
        Map<Long, ServiceRequestListModel> serviceRequestById =
                serviceRequestQueryService.mapListModelsByIds(serviceRequestIds);
        Map<Long, List<ProcedurePerformerQueryModel>> performersByProcedureId =
                procedureQueryService.mapPerformersByProcedureIds(procedureIds);

        // 收集 AD → 国临 PD；人员 id → 姓名
        Set<Long> activityIds = new LinkedHashSet<>();
        Set<Long> practitionerIds = new LinkedHashSet<>();
        for (ServiceRequestListModel sr : serviceRequestById.values()) {
            if (sr == null) {
                continue;
            }
            if (sr.getActivityId() != null) {
                activityIds.add(sr.getActivityId());
            }
            addPractitionerId(practitionerIds, sr.getSurgeonId());
            addPractitionerId(practitionerIds, sr.getFirstAssistantId());
            addPractitionerId(practitionerIds, sr.getSecondAssistantId());
            addPractitionerId(practitionerIds, sr.getNurseId());
            addPractitionerId(practitionerIds, sr.getAnesthetistId());
        }
        for (List<ProcedurePerformerQueryModel> performers : performersByProcedureId.values()) {
            if (performers == null) {
                continue;
            }
            for (ProcedurePerformerQueryModel p : performers) {
                if (p != null) {
                    addPractitionerId(practitionerIds, p.getPractitionerId());
                }
            }
        }
        Map<Long, ProcedureDefinitionSearchItemQueryModel> pdByActivityId =
                procedureDefinitionQueryService.mapActiveClinicalByActivityDefinitionIds(activityIds);
        Map<Long, String> practitionerNameById = mapPractitionerNames(practitionerIds);

        List<ProcedureLineSnapshot> result = new ArrayList<>();
        Set<Long> seenServiceRequestIds = new LinkedHashSet<>();
        for (ProcedureRecordQueryModel row : completedRows) {
            // 仅 service_request 来源且按 requestId 去重
            if (row == null || !TableNameConstants.WKF_SERVICE_REQUEST.equals(row.getRequestTable())) {
                continue;
            }
            Long requestId = row.getRequestId();
            if (requestId == null || !seenServiceRequestIds.add(requestId)) {
                continue;
            }
            ServiceRequestListModel serviceRequest = serviceRequestById.get(requestId);
            // 非手术类医嘱跳过
            if (serviceRequest == null
                    || !ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROCEDURE.equals(serviceRequest.getCategoryEnum())) {
                continue;
            }
            // 无国临 PD（纯加收）不占首页手术行
            ProcedureDefinitionSearchItemQueryModel pd = pdByActivityId.get(serviceRequest.getActivityId());
            if (pd == null) {
                continue;
            }
            Procedure procedure = procedureById.get(row.getId());
            List<ProcedurePerformerQueryModel> performers = performersByProcedureId.get(row.getId());
            ProcedureLineSnapshot snap = toProcedureSnapshot(
                    row, serviceRequest, procedure, pd, performers, practitionerNameById);
            if (snap != null) {
                result.add(snap);
            }
        }
        // 按业务手术时间排序后处理主手术缺省
        result.sort(Comparator
                .comparing(ProcedureLineSnapshot::getOprnTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ProcedureLineSnapshot::getOprnDate, Comparator.nullsLast(Comparator.naturalOrder())));
        boolean anyMainFromSr = false;
        for (ProcedureLineSnapshot snap : result) {
            if (Boolean.TRUE.equals(snap.getMain())) {
                anyMainFromSr = true;
                break;
            }
        }
        // SR 未标主手术时：序首条为主
        if (!anyMainFromSr && !result.isEmpty()) {
            result.get(0).setMain(Boolean.TRUE);
            for (int i = 1; i < result.size(); i++) {
                result.get(i).setMain(Boolean.FALSE);
            }
        }
        return result;
    }

    /**
     * 收集人员主键（空跳过）。
     *
     * @param target 目标集合
     * @param id     人员主键
     */
    private void addPractitionerId(Set<Long> target, Long id) {
        if (id != null) {
            target.add(id);
        }
    }

    /**
     * 批量解析医务人员姓名。
     *
     * @param practitionerIds 人员主键集合
     * @return id → 姓名
     */
    private Map<Long, String> mapPractitionerNames(Set<Long> practitionerIds) {
        Map<Long, String> names = new HashMap<>();
        if (practitionerIds == null || practitionerIds.isEmpty()) {
            return names;
        }
        for (Practitioner practitioner : practitionerQueryService.findAllByIds(practitionerIds)) {
            if (practitioner == null || practitioner.getId() == null) {
                continue;
            }
            names.put(practitioner.getId(), trimToNull(practitioner.getName()));
        }
        return names;
    }

    /**
     * 按出院/入院范围加载诊断快照行（含 condition 回落合成与 EMR 入院病情）。
     *
     * @param encounterId 就诊主键
     * @param scope       诊断范围（出院/入院）
     * @return 诊断行快照
     */
    private List<DiagnosisLineSnapshot> listDiagnosesByScope(Long encounterId, DiagnosisScope scope) {
        if (encounterId == null) {
            return Collections.emptyList();
        }
        List<EncounterDiagnosisQueryModel> associations = resolveActiveAssociations(encounterId);
        if (associations.isEmpty()) {
            return Collections.emptyList();
        }
        sortAssociations(associations);
        boolean hasTypedDischarge = hasTypedDischarge(associations);
        List<EncounterDiagnosisQueryModel> scoped = new ArrayList<>();
        for (EncounterDiagnosisQueryModel row : associations) {
            // 按出院/入院范围过滤诊断关联
            if (matchesScope(row.getDiagTypeCode(), scope, hasTypedDischarge)) {
                scoped.add(row);
            }
        }
        if (scoped.isEmpty()) {
            return Collections.emptyList();
        }
        DiagnosisContext ctx = loadDiagnosisContext(scoped);
        List<DiagnosisLineSnapshot> result = new ArrayList<>();
        for (EncounterDiagnosisQueryModel row : scoped) {
            DiagnosisLineSnapshot snap = toDiagnosisSnapshot(row, ctx);
            if (snap != null) {
                result.add(snap);
            }
        }
        applyAdmissionConditions(encounterId, result);
        return result;
    }

    /**
     * 加载就诊有效诊断关联。
     *
     * @param encounterId 就诊主键
     * @return 诊断关联列表
     */
    private List<EncounterDiagnosisQueryModel> resolveActiveAssociations(Long encounterId) {
        return new ArrayList<>(encounterQueryService.listEncounterDiagnoses(encounterId));
    }

    /**
     * 诊断关联按 rank、diagnosisId 稳定排序。
     *
     * @param associations 待排序关联（原地排序）
     */
    private void sortAssociations(List<EncounterDiagnosisQueryModel> associations) {
        associations.sort(Comparator
                .comparing(EncounterDiagnosisQueryModel::getRank, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(EncounterDiagnosisQueryModel::getDiagnosisId, Comparator.nullsLast(Comparator.naturalOrder())));
    }

    /**
     * 关联列表中是否存在显式出院类型诊断（影响无类型码行的默认归属）。
     *
     * @param associations 诊断关联
     * @return 含出院类型为 true
     */
    private boolean hasTypedDischarge(List<EncounterDiagnosisQueryModel> associations) {
        for (EncounterDiagnosisQueryModel row : associations) {
            IptPatientDiseaseDiagType type = parseDiagType(row.getDiagTypeCode());
            if (type != null && isDischargeDiagType(type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 诊断类型码是否匹配出院/入院范围（无类型码时出院范围默认归入）。
     *
     * @param diagTypeCode        诊断类型 code
     * @param scope               目标范围
     * @param hasTypedDischarge   是否存在显式出院诊断
     * @return 匹配为 true
     */
    private boolean matchesScope(String diagTypeCode, DiagnosisScope scope, boolean hasTypedDischarge) {
        IptPatientDiseaseDiagType type = parseDiagType(diagTypeCode);
        if (scope == DiagnosisScope.DISCHARGE) {
            // 无类型码且无显式出院诊断时，未分型关联默认归入出院
            if (type == null) {
                return !hasTypedDischarge;
            }
            return isDischargeDiagType(type);
        }
        // 入院范围：无类型码不匹配
        if (type == null) {
            return false;
        }
        return isAdmissionDiagType(type);
    }

    /**
     * 解析住院诊断类型枚举 code。
     *
     * @param diagTypeCode 类型 code 字符串
     * @return 枚举；空或非法时 null
     */
    private IptPatientDiseaseDiagType parseDiagType(String diagTypeCode) {
        String code = trimToNull(diagTypeCode);
        if (code == null) {
            return null;
        }
        return EnumUtil.fromCode(IptPatientDiseaseDiagType.class, code);
    }

    /**
     * 是否为出院侧诊断类型（含中医出院类）。
     *
     * @param type 诊断类型枚举
     * @return 出院类型为 true
     */
    private boolean isDischargeDiagType(IptPatientDiseaseDiagType type) {
        return type == IptPatientDiseaseDiagType.DISCHARGE
                || type == IptPatientDiseaseDiagType.TCM_DISCHARGE_NAME
                || type == IptPatientDiseaseDiagType.TCM_DISCHARGE_MAIN_DISEASE
                || type == IptPatientDiseaseDiagType.TCM_DISCHARGE_MAIN_PATTERN;
    }

    /**
     * 是否为入院侧诊断类型（入院/初步）。
     *
     * @param type 诊断类型枚举
     * @return 入院类型为 true
     */
    private boolean isAdmissionDiagType(IptPatientDiseaseDiagType type) {
        return type == IptPatientDiseaseDiagType.ADMISSION
                || type == IptPatientDiseaseDiagType.ADMISSION_PRELIMINARY;
    }

    /**
     * 批量加载 conditionDefinition 上下文（诊断编码/名称装配用，不经 Condition）。
     *
     * @param scoped 范围内诊断关联
     * @return definition 索引上下文
     */
    private DiagnosisContext loadDiagnosisContext(List<EncounterDiagnosisQueryModel> scoped) {
        Set<Long> defIdSet = new LinkedHashSet<>();
        for (EncounterDiagnosisQueryModel row : scoped) {
            if (row.getConditionDefinitionId() != null) {
                defIdSet.add(row.getConditionDefinitionId());
            }
        }
        Map<Long, ConditionDefinitionBatchItemQueryModel> defById = new HashMap<>();
        if (!defIdSet.isEmpty()) {
            for (ConditionDefinitionBatchItemQueryModel def
                    : conditionDefinitionQueryService.getConditionDefinitionListByIds(new ArrayList<>(defIdSet))) {
                defById.put(def.getId(), def);
            }
        }
        return new DiagnosisContext(defById);
    }

    /**
     * 单条诊断关联 → 首页诊断行快照（编码/名称/主诊断标记）。
     *
     * @param row 诊断关联
     * @param ctx condition/definition 上下文
     * @return 诊断行；编码名称均缺失时 null
     */
    private DiagnosisLineSnapshot toDiagnosisSnapshot(EncounterDiagnosisQueryModel row, DiagnosisContext ctx) {
        ConditionDefinitionBatchItemQueryModel definition = null;
        if (row.getConditionDefinitionId() != null) {
            definition = ctx.defById.get(row.getConditionDefinitionId());
        }
        // 目录定义缺失时无法组装编码/名称
        if (definition == null) {
            String fallbackName = trimToNull(row.getDescription());
            if (fallbackName == null) {
                return null;
            }
            DiagnosisLineSnapshot snap = new DiagnosisLineSnapshot();
            snap.setDiagName(fallbackName);
            snap.setMain(Boolean.TRUE.equals(row.getPrimaryFlag()));
            return snap;
        }
        String diagCode = null;
        String diagName = null;
        if (definition != null) {
            ClinicalDiagnosisPresentationQueryModel presentation =
                    conditionDefinitionQueryService.resolveClinicalPresentationFromDefinition(definition);
            if (presentation != null) {
                diagCode = trimToNull(presentation.getClinicCode());
                diagName = trimToNull(presentation.getClinicName());
                boolean isTcm = DoctorStationDiagnosisConstants.DIAGNOSIS_KIND_TAG_TCM.equals(
                        presentation.getDiagnosisKind());
                // 中医：主列落目录码/名（兼容仅填了 yb 的旧口径）
                if (isTcm) {
                    if (diagCode == null) {
                        diagCode = trimToNull(presentation.getYbCode());
                    }
                    if (diagName == null) {
                        diagName = trimToNull(presentation.getYbName());
                    }
                } else if (diagCode == null) {
                    // 西医医保无国临绑码：首页仅一码字段，仍落目录码，避免入院诊断被刷成空行
                    diagCode = trimToNull(presentation.getYbCode());
                    if (diagName == null) {
                        diagName = trimToNull(presentation.getYbName());
                    }
                }
            }
            if (diagCode == null) {
                diagCode = trimToNull(definition.getConditionCode());
            }
            if (diagName == null) {
                diagName = trimToNull(definition.getName());
            }
        }
        if (diagName == null) {
            diagName = trimToNull(row.getDescription());
        }
        // 编码与名称均为空时丢弃该行
        if (diagCode == null && diagName == null) {
            return null;
        }
        DiagnosisLineSnapshot snap = new DiagnosisLineSnapshot();
        snap.setDiagCode(diagCode);
        snap.setDiagName(diagName);
        snap.setMain(Boolean.TRUE.equals(row.getPrimaryFlag()));
        snap.setAdmCondCode(null);
        return snap;
    }

    /**
     * 从 EMR 扩展解析入院病情 adm_cond 并按诊断编码回填（不覆盖已有值）。
     *
     * @param encounterId 就诊主键
     * @param snapshots   待回填诊断行
     */
    private void applyAdmissionConditions(Long encounterId, List<DiagnosisLineSnapshot> snapshots) {
        if (encounterId == null || snapshots == null || snapshots.isEmpty()) {
            return;
        }
        Map<String, String> admCondByDiagCode = admissionConditionResolver
                .map(r -> r.resolveByEncounterId(encounterId)).orElse(Map.of());
        // EMR 未提供入院病情扩展时跳过
        if (admCondByDiagCode.isEmpty()) {
            return;
        }
        for (DiagnosisLineSnapshot snap : snapshots) {
            // 已有 adm_cond 的行不覆盖
            if (trimToNull(snap.getAdmCondCode()) != null) {
                continue;
            }
            String key = normalizeDiagCodeKey(snap.getDiagCode());
            if (key == null) {
                continue;
            }
            String admCond = admCondByDiagCode.get(key);
            if (admCond != null) {
                snap.setAdmCondCode(admCond);
            }
        }
    }

    /**
     * Procedure 实体列表 → id 索引 Map。
     *
     * @param procedures 手术实体列表
     * @return procedureId → 实体
     */
    private Map<Long, Procedure> indexProcedures(List<Procedure> procedures) {
        Map<Long, Procedure> map = new HashMap<>();
        if (procedures == null) {
            return map;
        }
        for (Procedure procedure : procedures) {
            if (procedure != null && procedure.getId() != null) {
                map.put(procedure.getId(), procedure);
            }
        }
        return map;
    }

    /**
     * 由 serviceRequest 引用的 activityId 批量加载 ActivityDefinition。
     *
     * @param serviceRequestById 诊疗请求索引
     * @return activityId → 活动定义
     */
    private Map<Long, ActivityDefinition> loadActivities(Map<Long, ServiceRequestListModel> serviceRequestById) {
        Set<Long> activityIds = new LinkedHashSet<>();
        for (ServiceRequestListModel row : serviceRequestById.values()) {
            if (row != null && row.getActivityId() != null) {
                activityIds.add(row.getActivityId());
            }
        }
        if (activityIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ActivityDefinition> map = new HashMap<>();
        for (ActivityDefinition activity : activityDefinitionQueryService.findAllByIds(activityIds)) {
            if (activity != null && activity.getId() != null) {
                map.put(activity.getId(), activity);
            }
        }
        return map;
    }

    /**
     * 已完成手术记录 → 首页手术行快照（国临 PD + performer + SR 横表）。
     *
     * @param row                  手术记录读模型
     * @param serviceRequest       关联申请
     * @param procedure            手术实体（可空）
     * @param pd                   国临手术定义（必有）
     * @param performers           执行人列表（可空）
     * @param practitionerNameById 人员姓名索引
     * @return 手术行；编码名称均空时 null
     */
    private ProcedureLineSnapshot toProcedureSnapshot(
            ProcedureRecordQueryModel row,
            ServiceRequestListModel serviceRequest,
            Procedure procedure,
            ProcedureDefinitionSearchItemQueryModel pd,
            List<ProcedurePerformerQueryModel> performers,
            Map<Long, String> practitionerNameById) {
        String oprnCode = trimToNull(pd.getProcedureCode());
        String oprnName = trimToNull(pd.getName());
        // 编码与名称均为空时丢弃该行
        if (oprnCode == null && oprnName == null) {
            return null;
        }
        ProcedureLineSnapshot snap = new ProcedureLineSnapshot();
        snap.setOprnCode(oprnCode);
        snap.setOprnName(oprnName);

        // 手术时间：Procedure.procedure_time → SR.procedure_time；禁止优先 occurrence_time
        LocalDateTime oprnTime = null;
        if (procedure != null) {
            oprnTime = procedure.getProcedureTime();
        }
        if (oprnTime == null && serviceRequest != null) {
            oprnTime = serviceRequest.getProcedureTime();
        }
        snap.setOprnTime(oprnTime);
        if (oprnTime != null) {
            snap.setOprnDate(oprnTime.toLocalDate());
        }

        if (serviceRequest != null) {
            snap.setOprnLevelCode(trimToNull(serviceRequest.getProcedureLevelCode()));
            snap.setAnstWayCode(trimToNull(serviceRequest.getAnesthesiaMethodCode()));
            if (Boolean.TRUE.equals(serviceRequest.getMainProcedureFlag())) {
                snap.setMain(Boolean.TRUE);
            } else if (Boolean.FALSE.equals(serviceRequest.getMainProcedureFlag())) {
                snap.setMain(Boolean.FALSE);
            } else {
                snap.setMain(Boolean.FALSE);
            }
            String incisionFromSr = trimToNull(serviceRequest.getIncisionTypeCode());
            snap.setOprnIncisionCode(incisionFromSr);
        } else {
            snap.setMain(Boolean.FALSE);
        }
        // 切口：执行可覆盖申请预填
        if (procedure != null && trimToNull(procedure.getIncisionTypeCode()) != null) {
            snap.setOprnIncisionCode(trimToNull(procedure.getIncisionTypeCode()));
        }
        if (procedure != null) {
            snap.setAnstStartTime(procedure.getAnesthesiaStartTime());
            snap.setAnstEndTime(procedure.getAnesthesiaEndTime());
            snap.setSpecimen(trimToNull(procedure.getSpecimen()));
            snap.setNote(trimToNull(procedure.getNote()));
            snap.setSendLabFlag(procedure.getSendLabFlag());
        }

        // 人员：performer 优先，缺则回落 SR 预填 id
        Long surgeonId = null;
        Long firstAssistantId = null;
        Long secondAssistantId = null;
        Long nurseId = null;
        Long anesthetistId = null;
        if (serviceRequest != null) {
            surgeonId = serviceRequest.getSurgeonId();
            firstAssistantId = serviceRequest.getFirstAssistantId();
            secondAssistantId = serviceRequest.getSecondAssistantId();
            nurseId = serviceRequest.getNurseId();
            anesthetistId = serviceRequest.getAnesthetistId();
        }
        snap.setOperDrName(resolveRoleName(
                performers,
                ClinicalProcedureConstants.PROCEDURE_PERFORMER_FUNCTION_SURGEON,
                surgeonId,
                practitionerNameById));
        snap.setAsit1Name(resolveRoleName(
                performers,
                ClinicalProcedureConstants.PROCEDURE_PERFORMER_FUNCTION_FIRST_ASSISTANT,
                firstAssistantId,
                practitionerNameById));
        snap.setAsit2Name(resolveRoleName(
                performers,
                ClinicalProcedureConstants.PROCEDURE_PERFORMER_FUNCTION_SECOND_ASSISTANT,
                secondAssistantId,
                practitionerNameById));
        snap.setNurseName(resolveRoleName(
                performers,
                ClinicalProcedureConstants.PROCEDURE_PERFORMER_FUNCTION_NURSE,
                nurseId,
                practitionerNameById));
        snap.setAnstDrName(resolveRoleName(
                performers,
                ClinicalProcedureConstants.PROCEDURE_PERFORMER_FUNCTION_ANESTHETIST,
                anesthetistId,
                practitionerNameById));
        return snap;
    }

    /**
     * 按角色解析人员姓名：performer 同码优先，否则 SR 预填 id。
     *
     * @param performers           执行人列表
     * @param functionCode         角色码
     * @param fallbackPractitionerId SR 预填人员
     * @param practitionerNameById 姓名索引
     * @return 姓名；无则 null
     */
    private String resolveRoleName(
            List<ProcedurePerformerQueryModel> performers,
            String functionCode,
            Long fallbackPractitionerId,
            Map<Long, String> practitionerNameById) {
        if (performers != null) {
            for (ProcedurePerformerQueryModel p : performers) {
                if (p == null) {
                    continue;
                }
                if (functionCode != null && functionCode.equals(p.getFunctionCode())) {
                    String name = practitionerNameById.get(p.getPractitionerId());
                    if (name != null) {
                        return name;
                    }
                }
            }
        }
        if (fallbackPractitionerId == null) {
            return null;
        }
        return practitionerNameById.get(fallbackPractitionerId);
    }

    /**
     * 诊断编码归一化为大写 Map 键（EMR 入院病情匹配用）。
     *
     * @param diagCode 原始诊断编码
     * @return 大写 trim 键；空时 null
     */
    private String normalizeDiagCodeKey(String diagCode) {
        String code = trimToNull(diagCode);
        if (code == null) {
            return null;
        }
        return code.toUpperCase(Locale.ROOT);
    }

    /**
     * 填充入院/出院病区床位：轨迹最早 WARD/BED + 当前活动或末次完成。
     * 床位格写入「病房-床位」（HOUSE 父级名 + 床名），与病案首页「病房」栏口径一致。
     *
     * @param snap        就诊快照
     * @param encounterId 就诊主键
     */
    private void applyWardAndBed(EncounterSnapshot snap, Long encounterId) {
        List<EncounterLocationQueryModel> locations = encounterQueryService.listEncounterLocations(encounterId);
        Long earliestWardId = this.findEarliestLocationId(locations, LocationForm.WARD.getCode());
        Long earliestBedId = this.findEarliestLocationId(locations, LocationForm.BED.getCode());
        if (earliestWardId != null) {
            snap.setAdmWardName(this.resolveLocationDisplayName(earliestWardId));
        }
        if (earliestBedId != null) {
            snap.setAdmBedName(this.resolveHouseBedDisplay(earliestBedId));
        }
        // 出院侧：优先当前活动上下文；无则末次完成 WARD/BED
        Optional<InpatientEncounterContextQueryModel> ctxOpt =
                encounterContextQueryService.getInpatientContextByEncounterId(encounterId);
        if (ctxOpt.isPresent()) {
            InpatientEncounterContextQueryModel ctx = ctxOpt.get();
            String wardName = trimToNull(ctx.getWardName());
            // 床位：优先按 bedId 拼「病房-床位」；无 id 时从上下文展示文案剥未占床包装
            String bedName = this.resolveHouseBedDisplay(ctx.getBedId());
            if (bedName == null) {
                bedName = this.stripUnoccupiedLastBedDisplay(ctx.getBedName());
            }
            if (wardName != null) {
                snap.setDscgWardName(wardName);
            }
            if (bedName != null) {
                snap.setDscgBedName(bedName);
            }
        }
        // 上下文无病区时回落末次完成 WARD
        if (snap.getDscgWardName() == null) {
            encounterQueryService.findLastCompletedWardLocationIdByEncounterId(encounterId)
                    .ifPresent(wardId -> snap.setDscgWardName(this.resolveLocationDisplayName(wardId)));
        }
        // 上下文无床位时回落末次完成 BED（同样拼病房-床位）
        if (snap.getDscgBedName() == null) {
            Map<Long, Long> bedMap =
                    encounterQueryService.mapLastCompletedBedLocationIdByEncounterIds(List.of(encounterId));
            Long bedId = bedMap.get(encounterId);
            if (bedId != null) {
                snap.setDscgBedName(this.resolveHouseBedDisplay(bedId));
            }
        }
        // 入院病区空时用当前/出院病区兜底（未分床前仅有当前）
        if (snap.getAdmWardName() == null && snap.getDscgWardName() != null) {
            snap.setAdmWardName(snap.getDscgWardName());
        }
        if (snap.getAdmBedName() == null && snap.getDscgBedName() != null) {
            snap.setAdmBedName(snap.getDscgBedName());
        }
    }

    /**
     * 位置展示名：名称优先，空则业务号（床位 name 偶发空白时仍能带出床号）。
     *
     * @param locationId 位置主键
     * @return 展示名；无实体或皆空时 null
     */
    private String resolveLocationDisplayName(Long locationId) {
        if (locationId == null) {
            return null;
        }
        Optional<Location> locOpt = locationQueryService.findOptionalById(locationId);
        if (locOpt.isEmpty()) {
            return null;
        }
        Location location = locOpt.get();
        String name = trimToNull(location.getName());
        if (name != null) {
            return name;
        }
        return trimToNull(location.getBusNo());
    }

    /**
     * 病案首页床位格：父级为 HOUSE 时拼「病房-床位」，否则仅床名。
     * 床名已含「-」时原样返回，避免重复拼接。
     *
     * @param bedLocationId 床位位置主键
     * @return 展示文案；无床位实体时 null
     */
    private String resolveHouseBedDisplay(Long bedLocationId) {
        if (bedLocationId == null) {
            return null;
        }
        Optional<Location> bedOpt = locationQueryService.findOptionalById(bedLocationId);
        if (bedOpt.isEmpty()) {
            return null;
        }
        Location bed = bedOpt.get();
        String bedName = trimToNull(bed.getName());
        if (bedName == null) {
            bedName = trimToNull(bed.getBusNo());
        }
        if (bedName == null) {
            return null;
        }
        // 已是「房-床」形态则不再拼父级
        if (bedName.contains(GeneralSharedConstants.DASH)) {
            return bedName;
        }
        if (bed.getParentId() == null) {
            return bedName;
        }
        Optional<Location> parentOpt = locationQueryService.findOptionalById(bed.getParentId());
        if (parentOpt.isEmpty()) {
            return bedName;
        }
        Location parent = parentOpt.get();
        // 仅 HOUSE 计入病房名，避免把病区拼进床位格
        if (!LocationForm.HOUSE.getCode().equals(parent.getFormEnum())) {
            return bedName;
        }
        String houseName = trimToNull(parent.getName());
        if (houseName == null) {
            houseName = trimToNull(parent.getBusNo());
        }
        if (houseName == null) {
            return bedName;
        }
        return houseName + GeneralSharedConstants.DASH + bedName;
    }

    /**
     * 去掉护士站「原{房}-{床}（未占床）」包装，首页床位格只留中间段。
     *
     * @param bedDisplay 上下文床位展示文案
     * @return 中间段或原 trim；空则 null
     */
    private String stripUnoccupiedLastBedDisplay(String bedDisplay) {
        String raw = trimToNull(bedDisplay);
        if (raw == null) {
            return null;
        }
        String prefix = InpatientNurseStationConstants.UNOCCUPIED_LAST_BED_DISPLAY_PREFIX;
        String suffix = InpatientNurseStationConstants.UNOCCUPIED_LAST_BED_DISPLAY_SUFFIX;
        if (raw.startsWith(prefix) && raw.endsWith(suffix) && raw.length() > prefix.length() + suffix.length()) {
            return trimToNull(raw.substring(prefix.length(), raw.length() - suffix.length()));
        }
        return raw;
    }

    /**
     * 在位置轨迹中取指定形态最早 start_time 的 locationId。
     *
     * @param locations 轨迹
     * @param formCode  WARD/BED
     * @return locationId；无则 null
     */
    private Long findEarliestLocationId(List<EncounterLocationQueryModel> locations, String formCode) {
        if (locations == null || locations.isEmpty() || formCode == null) {
            return null;
        }
        EncounterLocationQueryModel best = null;
        for (EncounterLocationQueryModel row : locations) {
            if (row == null || row.getLocationId() == null) {
                continue;
            }
            if (!formCode.equals(row.getFormEnum())) {
                continue;
            }
            if (best == null) {
                best = row;
                continue;
            }
            LocalDateTime bestStart = best.getStartTime();
            LocalDateTime rowStart = row.getStartTime();
            // 无开始时间的行靠后
            if (bestStart == null && rowStart != null) {
                best = row;
                continue;
            }
            if (bestStart != null && rowStart != null && rowStart.isBefore(bestStart)) {
                best = row;
            }
        }
        if (best == null) {
            return null;
        }
        return best.getLocationId();
    }

    /**
     * 拼接患者地址全文（省市区+街道/text）。
     *
     * @param address 地址值对象
     * @return 非空全文；全空时 null
     */
    private String formatPatientAddress(Address address) {
        if (address == null) {
            return null;
        }
        String text = trimToNull(address.getText());
        if (text != null) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        this.appendAddressPart(sb, address.getProvince());
        this.appendAddressPart(sb, address.getCity());
        this.appendAddressPart(sb, address.getDistrict());
        this.appendAddressPart(sb, address.getStreet());
        if (sb.length() == 0) {
            return null;
        }
        return sb.toString();
    }

    /**
     * 追加地址片段（跳过空）。
     *
     * @param sb   缓冲
     * @param part 片段
     */
    private void appendAddressPart(StringBuilder sb, String part) {
        String value = trimToNull(part);
        if (value == null) {
            return;
        }
        sb.append(value);
    }

    /**
     * 日历天：含 start、不含 endExclusive；同日至少 1 天。
     *
     * @param start         含
     * @param endExclusive  不含
     * @return 天数
     */
    private int countInclusiveExclusiveDays(LocalDate start, LocalDate endExclusive) {
        if (start == null || endExclusive == null) {
            return 0;
        }
        // 止点不晚于起点：按 1 天计（当日校对当日停）
        if (!endExclusive.isAfter(start)) {
            return 1;
        }
        long days = ChronoUnit.DAYS.between(start, endExclusive);
        if (days < 1) {
            return 1;
        }
        return (int) days;
    }

    /**
     * 活动定义名称 → 护理级别桶（spga/lv1/scd/lv3）。
     *
     * @param activityName 活动名称
     * @return 桶码；无法识别 null
     */
    private String resolveNursingLevelBucket(String activityName) {
        String name = trimToNull(activityName);
        if (name == null) {
            return null;
        }
        String normalized = name.replace("Ⅰ", "I").replace("Ⅱ", "II").replace("Ⅲ", "III");
        // 特级优先
        if (normalized.contains("特")) {
            return "spga";
        }
        if (normalized.contains("III") || normalized.contains("Ⅲ") || normalized.contains("三级")
                || normalized.contains("3级") || normalized.contains("Ⅲ级")) {
            return "lv3";
        }
        if (normalized.contains("II") || normalized.contains("Ⅱ") || normalized.contains("二级")
                || normalized.contains("2级")) {
            return "scd";
        }
        if (normalized.contains("I") || normalized.contains("一级") || normalized.contains("1级")) {
            return "lv1";
        }
        return null;
    }

    /** 诊断回填范围：出院诊断 / 入院诊断。 */
    private enum DiagnosisScope {
        /** 出院诊断。 */
        DISCHARGE,
        /** 入院诊断。 */
        ADMISSION
    }

    /** 诊断批量读模型上下文（definition 索引，不经 Condition）。 */
    private static final class DiagnosisContext {
        /** definitionId → 批量 definition 行。 */
        private final Map<Long, ConditionDefinitionBatchItemQueryModel> defById;

        /**
         * @param defById definition 索引
         */
        private DiagnosisContext(Map<Long, ConditionDefinitionBatchItemQueryModel> defById) {
            this.defById = defById;
        }
    }
}
