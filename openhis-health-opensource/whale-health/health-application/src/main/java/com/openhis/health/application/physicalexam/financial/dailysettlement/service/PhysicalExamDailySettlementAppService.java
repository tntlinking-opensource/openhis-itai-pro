/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.physicalexam.financial.dailysettlement.service;

import com.openhis.health.application.physicalexam.financial.dailysettlement.mapper.PhysicalExamDailySettlementMapper;
import com.openhis.health.application.physicalexam.financial.dailysettlement.request.ListPhysicalExamDailySettlementRequest;
import com.openhis.health.application.physicalexam.financial.dailysettlement.response.ListPhysicalExamDailySettlementResponse;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.PhysicalExamChargeConstants;
import com.openhis.health.domain.share.constants.PhysicalExamDailySettlementConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 体检日结清单应用服务。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>Gate：登录、收费业务日闭区间。</li>
 *   <li>Pre：按 {@code entered_date} 取已结账单，再筛 {@code Encounter.class=PE}；
 *       批量补科室、开单人、病人、项目名。</li>
 *   <li>Cmd：无写库（只读清单）。</li>
 *   <li>Asm：经 {@link PhysicalExamDailySettlementMapper} 输出明细与人次、金额合计。</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} →
 * {@code POST /api/app/physicalExamDailySettlement/{methodName}}；不另写 RestController。</p>
 */
@Service
@RequiredArgsConstructor
public class PhysicalExamDailySettlementAppService {

    /** 读路径登录门禁。 */
    private final CurrentOperatorResolver currentOperatorResolver;

    /** 账单读侧（仅本聚合时间窗与就诊 IN）。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** PE 就诊过滤与登记人。 */
    private final EncounterQueryService encounterQueryService;

    /** 科室名称。 */
    private final OrganizationQueryService organizationQueryService;

    /** 开单人/收费人姓名。 */
    private final PractitionerQueryService practitionerQueryService;

    /** 病人姓名。 */
    private final PatientQueryService patientQueryService;

    /** 收费项目定义快照名。 */
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;

    /** 清单装配。 */
    private final PhysicalExamDailySettlementMapper physicalExamDailySettlementMapper;

    /**
     * 查询一日体检收费清单明细与汇总。
     *
     * @param request 收费业务日起止
     * @return 明细、人次与金额合计
     * @throws BusinessException 未登录或日期非法
     */
    public ListPhysicalExamDailySettlementResponse listDailySettlement(
        ListPhysicalExamDailySettlementRequest request) {
        // Gate：须已登录，拒绝匿名拉收费清单
        this.currentOperatorResolver.requireOperatorId(
            PhysicalExamDailySettlementConstants.MESSAGE_CODE_QUERY_FAILED,
            "查询失败，请稍后重试");
        this.validateDateWindow(request);
        String dateFrom = request.getDateFrom().trim();
        String dateTo = request.getDateTo().trim();
        LocalDate from;
        LocalDate to;
        try {
            from = LocalDate.parse(dateFrom, GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
            to = LocalDate.parse(dateTo, GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                PhysicalExamDailySettlementConstants.MESSAGE_CODE_DATE_INVALID + ": 请选择有效的查询日期");
        }
        LocalDateTime enteredFromInclusive = from.atStartOfDay();
        LocalDateTime enteredToExclusive = to.plusDays(1).atStartOfDay();
        List<String> settledCodes = new ArrayList<>();
        settledCodes.add(ChargeItemStatus.SETTLED.getCode());
        try {
            // Pre：先取日窗内已结就诊主键，再筛 PE，避免装全日窗非体检账单
            List<Long> billedEncounterIds = this.chargeItemQueryService.listDistinctEncounterIdsByEnteredDateWindow(
                enteredFromInclusive, enteredToExclusive, settledCodes, null);
            List<Long> peEncounterIds = this.encounterQueryService.filterPeEncounterIds(billedEncounterIds);
            if (peEncounterIds.isEmpty()) {
                return this.physicalExamDailySettlementMapper.toEmptyResponse(dateFrom, dateTo);
            }
            List<ChargeItem> chargeItems = this.chargeItemQueryService.listChargeItemsByEncounterIdsInEnteredDateWindow(
                peEncounterIds, enteredFromInclusive, enteredToExclusive, settledCodes, null, null);
            if (chargeItems.isEmpty()) {
                return this.physicalExamDailySettlementMapper.toEmptyResponse(dateFrom, dateTo);
            }
            List<Encounter> encounters = this.encounterQueryService.listEncounterEntitiesByIds(peEncounterIds);
            Map<Long, Encounter> encounterById = this.indexEncounters(encounters);
            Map<Long, String> departmentNames = this.organizationQueryService.mapNamesByIds(
                this.collectDepartmentIds(chargeItems, encounters));
            Map<Long, String> ordererNames = this.practitionerQueryService.mapDisplayNameByEntererIds(
                this.collectOrdererIds(chargeItems, encounters));
            Map<Long, String> patientNames = this.patientQueryService.findPatientNamesByIds(
                this.collectPatientIds(chargeItems));
            Map<Long, String> itemLabels = this.buildItemLabelMap(chargeItems);
            // Cmd：无写库
            // Asm：按科室/开单人分组排序后输出人次与合计
            return this.physicalExamDailySettlementMapper.toListResponse(
                dateFrom,
                dateTo,
                chargeItems,
                encounterById,
                departmentNames,
                ordererNames,
                patientNames,
                itemLabels);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                PhysicalExamDailySettlementConstants.MESSAGE_CODE_QUERY_FAILED + ": 查询失败，请稍后重试");
        }
    }

    /**
     * Gate：收费业务日起止必填且止日不早于起日。
     *
     * @param request 查询请求
     */
    private void validateDateWindow(ListPhysicalExamDailySettlementRequest request) {
        if (request == null
            || StringUtils.isEmpty(request.getDateFrom())
            || StringUtils.isEmpty(request.getDateTo())) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                PhysicalExamDailySettlementConstants.MESSAGE_CODE_DATE_INVALID + ": 请选择有效的查询日期");
        }
        LocalDate from;
        LocalDate to;
        try {
            from = LocalDate.parse(request.getDateFrom().trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
            to = LocalDate.parse(request.getDateTo().trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                PhysicalExamDailySettlementConstants.MESSAGE_CODE_DATE_INVALID + ": 请选择有效的查询日期");
        }
        if (to.isBefore(from)) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                PhysicalExamDailySettlementConstants.MESSAGE_CODE_DATE_INVALID + ": 请选择有效的查询日期");
        }
    }

    /**
     * 就诊实体按主键索引。
     *
     * @param encounters 就诊列表
     * @return 主键 → 实体
     */
    private Map<Long, Encounter> indexEncounters(List<Encounter> encounters) {
        Map<Long, Encounter> encounterById = new HashMap<>();
        if (encounters == null) {
            return encounterById;
        }
        for (Encounter encounter : encounters) {
            if (encounter != null && encounter.getId() != null) {
                encounterById.put(encounter.getId(), encounter);
            }
        }
        return encounterById;
    }

    /**
     * 收集科室主键：账单开立科室优先，缺省回落就诊机构。
     *
     * @param chargeItems 收费行
     * @param encounters  就诊
     * @return 去重科室主键
     */
    private List<Long> collectDepartmentIds(List<ChargeItem> chargeItems, List<Encounter> encounters) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (chargeItems != null) {
            for (ChargeItem chargeItem : chargeItems) {
                if (chargeItem != null && chargeItem.getRequestingOrgId() != null) {
                    ids.add(chargeItem.getRequestingOrgId());
                }
            }
        }
        if (encounters != null) {
            for (Encounter encounter : encounters) {
                if (encounter != null && encounter.getOrganizationId() != null) {
                    ids.add(encounter.getOrganizationId());
                }
            }
        }
        return new ArrayList<>(ids);
    }

    /**
     * 收集开单人主键：就诊登记人优先，缺省回落账单收费人。
     *
     * @param chargeItems 收费行
     * @param encounters  就诊
     * @return 去重人员主键
     */
    private List<Long> collectOrdererIds(List<ChargeItem> chargeItems, List<Encounter> encounters) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (encounters != null) {
            for (Encounter encounter : encounters) {
                if (encounter != null && encounter.getRegistrarId() != null) {
                    ids.add(encounter.getRegistrarId());
                }
            }
        }
        if (chargeItems != null) {
            for (ChargeItem chargeItem : chargeItems) {
                if (chargeItem != null && chargeItem.getEntererId() != null) {
                    ids.add(chargeItem.getEntererId());
                }
            }
        }
        return new ArrayList<>(ids);
    }

    /**
     * 收集患者主键。
     *
     * @param chargeItems 收费行
     * @return 去重患者主键
     */
    private List<Long> collectPatientIds(List<ChargeItem> chargeItems) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (chargeItems == null) {
            return new ArrayList<>();
        }
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem != null && chargeItem.getPatientId() != null) {
                ids.add(chargeItem.getPatientId());
            }
        }
        return new ArrayList<>(ids);
    }

    /**
     * 按收费定义快照装配项目名。
     *
     * @param rows 收费行
     * @return 收费项主键 → 项目名
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
        Map<Long, ChargeItemDefinition> definitionMap =
            this.chargeItemDefinitionQueryService.findAllDefinitionsByIdIn(definitionIds);
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
}
