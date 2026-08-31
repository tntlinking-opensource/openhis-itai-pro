/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.encountermanage.service;

import com.openhis.health.application.shared.inpatient.AdmissionPrimaryDiagnosisAssembler;
import com.openhis.health.application.shared.inpatient.BusinessDischargeTimeResolver;
import com.openhis.health.application.shared.inpatient.InpatientAccountBalanceDisplayAssembler;
import com.openhis.health.application.shared.inpatient.InpatientAccountBalanceDisplayDtoApplier;
import com.openhis.health.application.shared.inpatient.InpatientAccountBalanceDisplayResult;
import com.openhis.health.application.shared.inpatient.InpatientInfoQueueDisplayAssembler;
import com.openhis.health.application.shared.financial.EncounterInsutypeDisplayAssembler;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.application.inpatient.doctorstation.encountermanage.dto.DoctorStationPatientQueueItemDto;
import com.openhis.health.application.inpatient.doctorstation.encountermanage.dto.DoctorStationQueueDisplayEnrichment;
import com.openhis.health.application.inpatient.doctorstation.encountermanage.mapper.InpatientDoctorStationEncounterManageMapper;
import com.openhis.health.application.inpatient.doctorstation.encountermanage.request.PagePatientQueueRequest;
import com.openhis.health.application.inpatient.doctorstation.encountermanage.response.PagePatientQueueResponse;
import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.patient.queries.model.PatientDetailQueryModel;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InpatientDoctorStationConstants;
import com.openhis.health.application.inpatient.nursestation.ATDManager.support.InpatientPatientQueueNursingLevelEnrichAssembler;
import com.openhis.health.application.inpatient.nursestation.ATDManager.support.dto.InpatientPatientQueueNursingLevelDisplayRow;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院医生站就诊管理应用编排。
 *
 * <p>在院队列：按当前科室分页展示医生站在院患者，回填账户余额与入院主诊断等展示字段。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InpatientDoctorStationEncounterManageAppService {

    /** 分页窗口门控：页码/页大小须为正（EX-IP-DOR-101）。 */
    private final PageWindowGate pageWindowGate;
    /** 会话机构解析（B.6.1 读路径）。 */
    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;
    /** 就诊读侧：在院队列分页与优先级批量回填。 */
    private final EncounterQueryService encounterQueryService;
    private final EncounterContextReader EncounterContextReader;
    /** 就诊账户读侧：预交金账户批量加载。 */
    private final AccountQueryService accountQueryService;
    /** 收费明细读侧：待结算合计批量加载。 */
    private final ChargeItemQueryService chargeItemQueryService;
    /** 患者读侧：检索码（拼音/五笔）批量解析。 */
    private final PatientQueryService patientQueryService;
    /** 库位读侧：病房/床位 bus_no 前缀匹配。 */
    private final LocationQueryService locationQueryService;
    /** 字段映射：读模型 → 医生站患者卡片 DTO。 */
    private final InpatientDoctorStationEncounterManageMapper inpatientDoctorStationEncounterManageMapper;
    /** 队列展示覆盖层：读模型缺失列的主档/位置回落。 */
    private final InpatientInfoQueueDisplayAssembler inpatientInfoQueueDisplayAssembler;
    /** 入院主诊断展示：diagnosisJson 解析与摘要拼接。 */
    private final AdmissionPrimaryDiagnosisAssembler admissionPrimaryDiagnosisAssembler;
    /** 支付读侧：反结待再结展示模式判定。 */
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    /** 患者队列护理级别只读 enrich（BR-IP-DOR-009）。 */
    private final InpatientPatientQueueNursingLevelEnrichAssembler inpatientPatientQueueNursingLevelEnrichAssembler;
    /** 险种展示：账户/读卡缓存/预结算回落。 */
    private final EncounterInsutypeDisplayAssembler encounterInsutypeDisplayAssembler;
    /** 业务出院时间：出院医嘱计划时间优先（粘性条/费用清单继承 endTime）。 */
    private final BusinessDischargeTimeResolver businessDischargeTimeResolver;

    /**
     * 分页查询住院医生站在院患者队列（IF-IP-DOR-01）。
     *
     * @param request 关键字与分页
     * @return 富字段患者卡片分页
     * @throws BusinessException 分页非法、未选科室或查询失败
     */
    public PagePatientQueueResponse pagePatientQueue(PagePatientQueueRequest request) {
        try {
            // Gate：分页窗口合法且须已选当前科室
            pageWindowGate.requirePositivePageWindow(
                request,
                InpatientDoctorStationConstants.EX_IP_DOR_101,
                "分页参数不合法，请检查页码与每页条数");
            Long organizationId = managedOrganizationIdsResolver.resolveOrganizationUnitIdForQuery();
            if (organizationId == null) {
                throw new BusinessException(
                    ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                    "请先选择科室");
            }
            InpatientEncounterPageQuery pageQuery =
                inpatientDoctorStationEncounterManageMapper.toInpatientEncounterPageQuery(request);
            PageQueryBinder.bind(request, pageQuery);
            // Pre：读侧分页拉取在院队列并批量预取账户/费用/护理级别等 enrich 数据
            Page<InpatientEncounterContextQueryModel> page =
                EncounterContextReader.pageInpatientDoctorStationQueue(pageQuery);
            Set<Long> pageEncounterIds = new LinkedHashSet<>();
            for (InpatientEncounterContextQueryModel row : page.getContent()) {
                if (row != null && row.getEncounterId() != null) {
                    pageEncounterIds.add(row.getEncounterId());
                }
            }
            Map<Long, Account> accountByEncounterId =
                accountQueryService.mapRegistrationAccountsByEncounterIds(pageEncounterIds);
            // Pre：费用性质展示优先医保账户 contract_no，否则登记个人现金户（与门诊医生站同口径）
            Map<Long, String> contractNoByEncounterId =
                accountQueryService.mapPreferredContractNoByEncounterIds(pageEncounterIds);
            Map<Long, String> priorityEnumByEncounterId =
                encounterQueryService.mapPriorityEnumByEncounterIds(pageEncounterIds);
            Map<Long, InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay> displayOverlay =
                inpatientInfoQueueDisplayAssembler.buildOverlay(page.getContent(), contractNoByEncounterId);
            Map<Long, DoctorStationQueueDisplayEnrichment> enrichmentByEncounterId =
                this.buildDisplayEnrichment(page.getContent(), pageEncounterIds);
            Map<Long, BigDecimal> pendingByEncounterId =
                chargeItemQueryService.mapPendingSettlementTotalPriceByEncounterIds(pageEncounterIds);
            Map<Long, BigDecimal> settledByEncounterId =
                chargeItemQueryService.mapSettledTotalPriceByEncounterIds(pageEncounterIds);
            Set<Long> reversedPendingResettleEncounterIds =
                paymentReconciliationQueryService.filterReversedPendingResettleEncounterIds(pageEncounterIds);
            // Pre：当前页批量解析护理级别展示（BR-IP-DOR-009）
            Map<Long, InpatientPatientQueueNursingLevelDisplayRow> nursingLevelByEncounterId =
                inpatientPatientQueueNursingLevelEnrichAssembler.enrichByEncounterIds(pageEncounterIds, LocalDate.now());
            // Pre：本页业务出院时间（出院医嘱计划时间优先，覆盖脏 end_time）
            Map<Long, LocalDateTime> endTimeByEncounter = new HashMap<>();
            for (InpatientEncounterContextQueryModel row : page.getContent()) {
                if (row != null && row.getEncounterId() != null) {
                    endTimeByEncounter.put(row.getEncounterId(), row.getEndTime());
                }
            }
            Map<Long, LocalDateTime> businessEndByEncounter =
                businessDischargeTimeResolver.mapByEncounterIds(pageEncounterIds, endTimeByEncounter);
            List<DoctorStationPatientQueueItemDto> content = new ArrayList<>();
            for (InpatientEncounterContextQueryModel row : page.getContent()) {
                // 跳过空行，避免装配脏分页结果。
                if (row == null) {
                    continue;
                }
                Account account = null;
                InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay rowOverlay = null;
                DoctorStationQueueDisplayEnrichment enrichment = null;
                String priorityEnum = null;
                if (row.getEncounterId() != null) {
                    account = accountByEncounterId.get(row.getEncounterId());
                    rowOverlay = displayOverlay.get(row.getEncounterId());
                    enrichment = enrichmentByEncounterId.get(row.getEncounterId());
                    priorityEnum = priorityEnumByEncounterId.get(row.getEncounterId());
                }
                BigDecimal pending = BigDecimal.ZERO;
                BigDecimal settled = BigDecimal.ZERO;
                boolean reversedPendingResettle = false;
                if (row.getEncounterId() != null) {
                    BigDecimal pendingTotal = pendingByEncounterId.get(row.getEncounterId());
                    if (pendingTotal != null) {
                        pending = pendingTotal;
                    }
                    BigDecimal settledTotal = settledByEncounterId.get(row.getEncounterId());
                    if (settledTotal != null) {
                        settled = settledTotal;
                    }
                    if (reversedPendingResettleEncounterIds.contains(row.getEncounterId())) {
                        reversedPendingResettle = true;
                    }
                }
                InpatientAccountBalanceDisplayResult accountBalanceDisplay =
                    InpatientAccountBalanceDisplayAssembler.assemble(
                        account, pending, settled, reversedPendingResettle);
                List<String> diagnosisLines =
                    admissionPrimaryDiagnosisAssembler.resolveDiagnosisDisplayLines(row.getDiagnosisJson());
                String diagnosisSummary =
                    admissionPrimaryDiagnosisAssembler.joinDiagnosisDisplayLines(diagnosisLines);
                DoctorStationPatientQueueItemDto item =
                    inpatientDoctorStationEncounterManageMapper.toDoctorStationPatientQueueItemDto(
                        row,
                        rowOverlay,
                        priorityEnum,
                        diagnosisLines,
                        diagnosisSummary,
                        enrichment);
                InpatientAccountBalanceDisplayDtoApplier.apply(item, accountBalanceDisplay);
                this.applyNursingLevelDisplay(item, nursingLevelByEncounterId.get(row.getEncounterId()));
                // Asm：队列 endTime 展示业务出院时间（粘性条/打印/费用止日同源）
                if (row.getEncounterId() != null && businessEndByEncounter.containsKey(row.getEncounterId())) {
                    item.setEndTime(businessEndByEncounter.get(row.getEncounterId()));
                }
                content.add(item);
            }
            // Asm：封装分页响应
            return inpatientDoctorStationEncounterManageMapper.toPagePatientQueueResponse(content, page);
        } catch (BusinessException ex) {
            // 业务异常原样上抛，保留 EX-IP-DOR-* 与 ErrorCode 口径。
            throw ex;
        } catch (RuntimeException ex) {
            // 非预期读失败统一包装为 EX-IP-DOR-101，避免栈信息直出。
            log.warn("pagePatientQueue failed", ex);
            throw new BusinessException(
                InpatientDoctorStationConstants.EX_IP_DOR_101,
                "加载失败，请稍后重试");
        }
    }

    /**
     * 回填护理级别展示字段至医生站患者列表行（BR-IP-DOR-009）。
     *
     * @param item    列表行 DTO
     * @param nursing 护理级别 enrich 行；无护理时为 null
     */
    private void applyNursingLevelDisplay(
        DoctorStationPatientQueueItemDto item,
        InpatientPatientQueueNursingLevelDisplayRow nursing) {
        if (item == null || nursing == null) {
            return;
        }
        item.setNursingLevelServiceRequestId(nursing.getServiceRequestId());
        item.setNursingLevelActivityId(nursing.getActivityId());
        item.setNursingLevelName(nursing.getActivityName());
        item.setNursingLevelStatusEnum(nursing.getStatusEnum());
    }

    /**
     * 判断展示余额是否低于预警阈值（复用护士站 500 元口径）。
     *
     * @param availableBalance 可用余额
     * @return true 表示需低余额预警
     */
    private boolean isLowBalanceWarning(BigDecimal availableBalance) {
        return InpatientAccountBalanceDisplayAssembler.isLowBalanceWarning(availableBalance);
    }

    /**
     * 为当前页读模型行构建展示增补（病房名、检索码、险种）；不拉护士站任务计数。
     *
     * @param rows         读模型行
     * @param encounterIds 当前页就诊主键（批量取医保账户）
     * @return encounterId → 增补字段
     */
    private Map<Long, DoctorStationQueueDisplayEnrichment> buildDisplayEnrichment(
        List<InpatientEncounterContextQueryModel> rows,
        Set<Long> encounterIds) {
        Map<Long, DoctorStationQueueDisplayEnrichment> result = new HashMap<>();
        if (rows == null || rows.isEmpty()) {
            return result;
        }
        Set<Long> patientIds = new LinkedHashSet<>();
        Set<Long> wardIds = new LinkedHashSet<>();
        for (InpatientEncounterContextQueryModel row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getPatientId() != null) {
                patientIds.add(row.getPatientId());
            }
            if (row.getWardId() != null) {
                wardIds.add(row.getWardId());
            }
        }
        Map<Long, String> pinyinByPatientId = new HashMap<>();
        Map<Long, String> wubiByPatientId = new HashMap<>();
        for (Long patientId : patientIds) {
            PatientDetailQueryModel detail = patientQueryService.loadPatientDetail(patientId);
            // 患者扩展档缺失时跳过，检索码保持 null。
            if (detail == null || detail.getPatient() == null) {
                continue;
            }
            pinyinByPatientId.put(patientId, detail.getPatient().getPyStr());
            wubiByPatientId.put(patientId, detail.getPatient().getWbStr());
        }
        Map<Long, Map<String, String>> houseNameByBusNoByWardId = new HashMap<>();
        for (Long wardId : wardIds) {
            Map<String, String> houseIndex = new HashMap<>();
            List<Location> houses = locationQueryService.listHousesUnderWard(wardId);
            for (Location house : houses) {
                // 仅索引有业务号的病房，供床位 bus_no 前缀匹配。
                if (house != null && StringUtils.isNotEmpty(house.getBusNo())) {
                    houseIndex.put(house.getBusNo().trim(), house.getName());
                }
            }
            houseNameByBusNoByWardId.put(wardId, houseIndex);
        }
        // Pre：医保账户险种批量回填（账户空则读卡缓存/预结算回落）。
        Map<Long, String> insutypeByEncounterId =
            encounterInsutypeDisplayAssembler.mapInsutypeByEncounterIds(encounterIds);
        for (InpatientEncounterContextQueryModel row : rows) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            DoctorStationQueueDisplayEnrichment enrichment = new DoctorStationQueueDisplayEnrichment();
            Map<String, String> houseNameByBusNo = null;
            if (row.getWardId() != null) {
                houseNameByBusNo = houseNameByBusNoByWardId.get(row.getWardId());
            }
            enrichment.setHouseName(this.resolveHouseName(row.getBedId(), houseNameByBusNo));
            if (row.getPatientId() != null) {
                enrichment.setPinyinCode(pinyinByPatientId.get(row.getPatientId()));
                enrichment.setWubiCode(wubiByPatientId.get(row.getPatientId()));
            }
            enrichment.setInsutype(insutypeByEncounterId.get(row.getEncounterId()));
            result.put(row.getEncounterId(), enrichment);
        }
        return result;
    }

    /**
     * 由床位 bus_no 前缀匹配病房名称。
     *
     * @param bedId            床位主键
     * @param houseNameByBusNo 病房 bus_no 索引
     * @return 病房名或 null
     */
    private String resolveHouseName(Long bedId, Map<String, String> houseNameByBusNo) {
        if (bedId == null || houseNameByBusNo == null || houseNameByBusNo.isEmpty()) {
            return null;
        }
        Location bed = locationQueryService.findOptionalById(bedId).orElse(null);
        if (bed == null || StringUtils.isEmpty(bed.getBusNo())) {
            return null;
        }
        String bedBusNo = bed.getBusNo().trim();
        String matchedName = null;
        int matchedPrefixLen = 0;
        for (Map.Entry<String, String> entry : houseNameByBusNo.entrySet()) {
            String houseBusNo = entry.getKey();
            if (StringUtils.isEmpty(houseBusNo)) {
                continue;
            }
            String prefix = houseBusNo.trim();
            if (bedBusNo.startsWith(prefix) && prefix.length() > matchedPrefixLen) {
                matchedPrefixLen = prefix.length();
                matchedName = entry.getValue();
            }
        }
        return matchedName;
    }
}
