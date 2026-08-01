/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.registration.service;

import com.openhis.health.application.peis.orchestration.PeisRegistrationOrchestrator;
import com.openhis.health.application.peis.orchestration.result.RegisterPersonalCheckInResult;
import com.openhis.health.application.peis.registration.dto.CustomerRowDto;
import com.openhis.health.application.peis.registration.dto.PersonalRegistrationHistoryRowDto;
import com.openhis.health.application.peis.registration.mapper.PeisRegistrationMapper;
import com.openhis.health.application.peis.registration.request.AdjustServiceRequestsRequest;
import com.openhis.health.application.peis.registration.request.CreateRegistrationRequest;
import com.openhis.health.application.peis.registration.request.GetChargeLaunchContextRequest;
import com.openhis.health.application.peis.registration.request.GetChargeStatusRequest;
import com.openhis.health.application.peis.registration.request.GetCheckOrderDetailRequest;
import com.openhis.health.application.peis.registration.request.PagePersonalRegistrationsRequest;
import com.openhis.health.application.peis.registration.request.SearchCustomerRequest;
import com.openhis.health.application.peis.registration.response.ChargeLaunchContextResponse;
import com.openhis.health.application.peis.registration.response.ChargeStatusResponse;
import com.openhis.health.application.peis.registration.response.CheckOrderDetailResponse;
import com.openhis.health.application.peis.registration.response.CreateRegistrationResponse;
import com.openhis.health.application.peis.registration.response.SearchCustomerResponse;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.patient.queries.PatientPagedQuery;
import com.openhis.health.domain.administration.patient.queries.model.BatchPatientDetailQueryModel;
import com.openhis.health.domain.administration.patient.queries.model.PatientCoreQueryModel;
import com.openhis.health.domain.administration.patient.queries.model.PatientPageItemQueryModel;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.peis.encounterext.queries.model.PeisEncounterExtModel;
import com.openhis.health.domain.peis.encounterext.service.PeisEncounterExtQueryService;
import com.openhis.health.domain.peis.servicerequestext.queries.model.PeisServiceRequestExtModel;
import com.openhis.health.domain.peis.servicerequestext.service.PeisServiceRequestExtQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionDetailQueryModel;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import jakarta.persistence.criteria.Predicate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 个检登记应用服务（AS-PEIS-REG-001）。
 *
 * <p>客户检索、登记、体检单详情、加/减项、收费态与唤起收费上下文。</p>
 */
@Service
@RequiredArgsConstructor
public class PeisPersonalRegistrationAppService {

    private static final String CHARGE_PATH_HINT = "/peis/charge";

    private final PeisRegistrationOrchestrator peisRegistrationOrchestrator;
    private final PeisRegistrationMapper peisRegistrationMapper;
    private final PatientQueryService patientQueryService;
    private final EncounterQueryService encounterQueryService;
    private final PeisEncounterExtQueryService peisEncounterExtQueryService;
    private final PeisServiceRequestExtQueryService peisServiceRequestExtQueryService;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /**
     * 检索体检客户（REG-01）。
     */
    public SearchCustomerResponse searchCustomer(SearchCustomerRequest request) {
        // Gate：身份证或姓名至少一项
        if (request == null
            || (StringUtils.isEmpty(request.getIdCardNo()) && StringUtils.isEmpty(request.getNameKeyword()))) {
            throw new ValidationException("检索客户失败：身份证号或姓名关键字至少填写一项");
        }
        // Pre：分页检索患者
        PatientPagedQuery query = new PatientPagedQuery();
        query.setPageNo(request.getPageNo());
        query.setPageSize(request.getPageSize());
        if (StringUtils.isNotEmpty(request.getIdCardNo())) {
            query.setKeyword(request.getIdCardNo().trim());
        } else {
            query.setKeyword(request.getNameKeyword().trim());
        }
        Page<PatientPageItemQueryModel> page = patientQueryService.searchPatientsPaged(query);
        List<CustomerRowDto> rows = new ArrayList<>();
        for (PatientPageItemQueryModel row : page.getContent()) {
            rows.add(peisRegistrationMapper.toCustomerRowDto(row));
        }
        // Asm
        return peisRegistrationMapper.toSearchCustomerResponse(rows, page.getTotalElements());
    }

    /**
     * 创建个检登记（REG-02）。
     */
    public CreateRegistrationResponse createRegistration(CreateRegistrationRequest request) {
        // Gate
        if (request == null || request.getPatientId() == null || request.getPackageActivityId() == null
            || request.getOrgId() == null) {
            throw new ValidationException("创建登记失败：患者、套餐与机构不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PEIS_REG_INVALID_ARGUMENT, "请先登录后再登记");
        // Cmd
        RegisterPersonalCheckInResult result = peisRegistrationOrchestrator.registerPersonalCheckIn(
            peisRegistrationMapper.toRegisterCommand(request, operatorId));
        // Asm
        return peisRegistrationMapper.toCreateRegistrationResponse(result);
    }

    /**
     * 体检单详情（REG-03）。
     */
    public CheckOrderDetailResponse getCheckOrderDetail(GetCheckOrderDetailRequest request) {
        // Gate
        if (request == null || request.getEncounterId() == null) {
            throw new ValidationException("查询体检单失败：就诊标识不能为空");
        }
        return loadCheckOrderDetail(request.getEncounterId());
    }

    /**
     * 项目加/减项（REG-04）；调整后回读详情。
     */
    public CheckOrderDetailResponse adjustServiceRequests(AdjustServiceRequestsRequest request) {
        // Gate
        if (request == null || request.getEncounterId() == null) {
            throw new ValidationException("项目调整失败：就诊标识不能为空");
        }
        boolean hasAdd = request.getAddActivityIds() != null && !request.getAddActivityIds().isEmpty();
        boolean hasRemove =
            request.getRemoveServiceRequestIds() != null && !request.getRemoveServiceRequestIds().isEmpty();
        if (!hasAdd && !hasRemove) {
            throw new ValidationException("项目调整失败：加项或减项至少一项");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PEIS_REG_INVALID_ARGUMENT, "请先登录后再调整项目");
        // Cmd
        peisRegistrationOrchestrator.adjustServiceRequests(
            peisRegistrationMapper.toAdjustCommand(request, operatorId));
        // Asm：回读详情
        return loadCheckOrderDetail(request.getEncounterId());
    }

    /**
     * 读取收费态（REG-08）。
     */
    public ChargeStatusResponse getChargeStatus(GetChargeStatusRequest request) {
        // Gate
        if (request == null || request.getEncounterId() == null) {
            throw new ValidationException("读取收费态失败：就诊标识不能为空");
        }
        ChargeStatusResponse response = new ChargeStatusResponse();
        response.setPaid(false);
        response.setUnknown(false);
        try {
            ChargeItemQueryCommand cmd = new ChargeItemQueryCommand();
            cmd.setEncounterId(request.getEncounterId());
            List<ChargeItem> items = chargeItemQueryService.listChargeItemsByEncounterId(cmd);
            String settled = ChargeItemStatus.SETTLED.getCode();
            for (ChargeItem item : items) {
                if (item != null && settled.equals(item.getStatusEnum())) {
                    response.setPaid(true);
                    break;
                }
            }
        } catch (RuntimeException ex) {
            response.setUnknown(true);
            response.setPaid(false);
        }
        return response;
    }

    /**
     * 唤起收费上下文（REG-09）。
     */
    public ChargeLaunchContextResponse getChargeLaunchContext(GetChargeLaunchContextRequest request) {
        // Gate
        if (request == null || request.getEncounterId() == null) {
            throw new ValidationException("唤起收费失败：就诊标识不能为空");
        }
        // Pre：确认体检就诊与扩展存在
        peisEncounterExtQueryService.getByEncounterId(request.getEncounterId());
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            request.getEncounterId(),
            EncounterClass.PE.getCode(),
            ErrorCodeConstants.PEIS_REG_INVALID_ARGUMENT,
            "非体检就诊不可唤起收费");
        // Asm
        ChargeLaunchContextResponse response = new ChargeLaunchContextResponse();
        response.setEncounterId(encounter.getId());
        response.setPatientId(encounter.getPatientId());
        response.setPathHint(CHARGE_PATH_HINT);
        return response;
    }

    /**
     * 个检登记历史分页（对齐门诊挂号底部历史）。
     */
    public PagedResult<PersonalRegistrationHistoryRowDto> pagePersonalRegistrations(
            PagePersonalRegistrationsRequest request) {
        // Gate：业务日必填
        if (request == null || request.getDateFrom() == null || request.getDateTo() == null) {
            throw new ValidationException("查询登记历史失败：请选择日期范围");
        }
        LocalDate dateFrom = request.getDateFrom();
        LocalDate dateTo = request.getDateTo();
        if (dateTo.isBefore(dateFrom)) {
            throw new ValidationException("查询登记历史失败：结束日期不能早于开始日期");
        }

        LocalDateTime rangeStart = dateFrom.atStartOfDay();
        LocalDateTime rangeEnd = dateTo.atTime(LocalTime.MAX);
        String keyword = request.getKeyword() == null ? "" : request.getKeyword().trim();
        Long filterPatientId = request.getPatientId();

        // Pre：关键字命中患者姓名/证件 → 患者主键集合（与流水号 OR）
        Set<Long> keywordPatientIds = null;
        if (StringUtils.isNotEmpty(keyword) && filterPatientId == null) {
            keywordPatientIds = resolvePatientIdsByKeyword(keyword);
        }

        Set<Long> finalKeywordPatientIds = keywordPatientIds;
        Specification<Encounter> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get(FieldNameConstants.CLASS_ENUM), EncounterClass.PE.getCode()));
            predicates.add(cb.equal(root.get(FieldNameConstants.IS_DELETED), Boolean.FALSE));
            predicates.add(cb.notEqual(
                root.get(FieldNameConstants.STATUS_ENUM), EncounterStatus.ENTERED_IN_ERROR.getCode()));
            predicates.add(cb.greaterThanOrEqualTo(root.get(FieldNameConstants.START_TIME), rangeStart));
            predicates.add(cb.lessThanOrEqualTo(root.get(FieldNameConstants.START_TIME), rangeEnd));
            if (filterPatientId != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.PATIENT_ID), filterPatientId));
            }
            if (StringUtils.isNotEmpty(keyword)) {
                List<Predicate> keywordOr = new ArrayList<>();
                keywordOr.add(cb.like(root.get(FieldNameConstants.BUS_NO), "%" + keyword + "%"));
                if (finalKeywordPatientIds != null && !finalKeywordPatientIds.isEmpty()) {
                    keywordOr.add(root.get(FieldNameConstants.PATIENT_ID).in(finalKeywordPatientIds));
                }
                predicates.add(cb.or(keywordOr.toArray(new Predicate[0])));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        PageRequest pageable = PageRequest.of(
            request.springPageIndex(),
            request.getBoundedPageSize(),
            Sort.by(Sort.Direction.DESC, FieldNameConstants.START_TIME));
        Page<Encounter> page = encounterQueryService.pageEncounterEntities(spec, pageable);

        List<Encounter> content = page.getContent();
        List<Long> encounterIds = new ArrayList<>();
        List<Long> patientIds = new ArrayList<>();
        for (Encounter row : content) {
            if (row.getId() != null) {
                encounterIds.add(row.getId());
            }
            if (row.getPatientId() != null) {
                patientIds.add(row.getPatientId());
            }
        }

        Map<Long, PeisEncounterExtModel> extByEncounter =
            peisEncounterExtQueryService.mapByEncounterIds(encounterIds);
        Map<Long, BatchPatientDetailQueryModel> patientById =
            patientQueryService.batchLoadPatientDetails(patientIds);
        Map<Long, Boolean> paidByEncounter = resolvePaidByEncounterIds(encounterIds);

        List<PersonalRegistrationHistoryRowDto> rows = new ArrayList<>();
        for (Encounter encounter : content) {
            PersonalRegistrationHistoryRowDto dto = new PersonalRegistrationHistoryRowDto();
            dto.setEncounterId(encounter.getId());
            dto.setBusNo(encounter.getBusNo());
            dto.setPatientId(encounter.getPatientId());
            dto.setRegisteredAt(encounter.getStartTime());
            dto.setStatusEnum(encounter.getStatusEnum());

            BatchPatientDetailQueryModel patientBatch = patientById.get(encounter.getPatientId());
            if (patientBatch != null
                && patientBatch.getPatientDetail() != null
                && patientBatch.getPatientDetail().getPatient() != null) {
                PatientCoreQueryModel core = patientBatch.getPatientDetail().getPatient();
                dto.setPatientName(core.getName());
                dto.setGenderCode(core.getGenderEnum());
                dto.setPhone(core.getPhone());
            }

            PeisEncounterExtModel ext = extByEncounter.get(encounter.getId());
            if (ext != null) {
                dto.setPackageActivityId(ext.getPackageActivityId());
                dto.setPackageName(extractPackageName(ext.getPackageSnapshotJson()));
                if (StringUtils.isEmpty(dto.getBusNo())) {
                    dto.setBusNo(ext.getBusNo());
                }
            }

            Boolean paid = paidByEncounter.get(encounter.getId());
            if (paid == null) {
                dto.setPaid(Boolean.FALSE);
                dto.setChargeUnknown(Boolean.TRUE);
            } else {
                dto.setPaid(paid);
                dto.setChargeUnknown(Boolean.FALSE);
            }
            rows.add(dto);
        }

        return PagedResult.of(rows, page.getTotalElements(), page.getNumber() + 1, page.getSize());
    }

    private Set<Long> resolvePatientIdsByKeyword(String keyword) {
        PatientPagedQuery query = new PatientPagedQuery();
        query.setKeyword(keyword);
        query.setPageNo(1);
        query.setPageSize(100);
        Page<PatientPageItemQueryModel> page = patientQueryService.searchPatientsPaged(query);
        Set<Long> ids = new HashSet<>();
        for (PatientPageItemQueryModel row : page.getContent()) {
            if (row != null && row.getId() != null) {
                ids.add(row.getId());
            }
        }
        return ids;
    }

    private Map<Long, Boolean> resolvePaidByEncounterIds(List<Long> encounterIds) {
        Map<Long, Boolean> paidMap = new HashMap<>();
        if (encounterIds == null || encounterIds.isEmpty()) {
            return paidMap;
        }
        try {
            ChargeItemQueryCommand cmd = new ChargeItemQueryCommand();
            cmd.setEncounterIds(encounterIds);
            List<ChargeItem> items = chargeItemQueryService.listChargeItemsByEncounterIds(cmd);
            String settled = ChargeItemStatus.SETTLED.getCode();
            for (Long id : encounterIds) {
                paidMap.put(id, Boolean.FALSE);
            }
            for (ChargeItem item : items) {
                if (item == null || item.getEncounterId() == null) {
                    continue;
                }
                if (settled.equals(item.getStatusEnum())) {
                    paidMap.put(item.getEncounterId(), Boolean.TRUE);
                }
            }
        } catch (RuntimeException ex) {
            for (Long id : encounterIds) {
                paidMap.put(id, null);
            }
        }
        return paidMap;
    }

    private static String extractPackageName(String snapshotJson) {
        if (StringUtils.isEmpty(snapshotJson)) {
            return null;
        }
        String marker = "\"name\":\"";
        int start = snapshotJson.indexOf(marker);
        if (start < 0) {
            return null;
        }
        start += marker.length();
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < snapshotJson.length(); i++) {
            char ch = snapshotJson.charAt(i);
            if (ch == '\\' && i + 1 < snapshotJson.length()) {
                sb.append(snapshotJson.charAt(i + 1));
                i++;
                continue;
            }
            if (ch == '"') {
                break;
            }
            sb.append(ch);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private CheckOrderDetailResponse loadCheckOrderDetail(Long encounterId) {
        // Pre
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            encounterId,
            EncounterClass.PE.getCode(),
            ErrorCodeConstants.PEIS_REG_INVALID_ARGUMENT,
            "非体检就诊");
        PeisEncounterExtModel ext = peisEncounterExtQueryService.getByEncounterId(encounterId);
        List<PeisServiceRequestExtModel> lines =
            peisServiceRequestExtQueryService.listByEncounterId(encounterId);
        Map<Long, String> displayByActivityId = loadActivityDisplays(lines);
        // Asm
        return peisRegistrationMapper.toCheckOrderDetailResponse(
            ext, encounter.getPatientId(), lines, displayByActivityId);
    }

    private Map<Long, String> loadActivityDisplays(List<PeisServiceRequestExtModel> lines) {
        Map<Long, String> result = new HashMap<>();
        if (lines == null || lines.isEmpty()) {
            return result;
        }
        Set<Long> activityIds = new HashSet<>();
        for (PeisServiceRequestExtModel line : lines) {
            if (line != null && line.getActivityId() != null) {
                activityIds.add(line.getActivityId());
            }
        }
        for (Long activityId : activityIds) {
            ActivityDefinitionDetailQueryModel detail =
                activityDefinitionQueryService.getServiceCatalog(activityId);
            if (detail != null && detail.getBaseInfo() != null) {
                result.put(activityId, detail.getBaseInfo().getName());
            }
        }
        return result;
    }
}
