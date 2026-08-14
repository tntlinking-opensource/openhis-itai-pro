/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.medicationdispense.inpatient.support;

import com.openhis.health.application.outpatient.pharmacy.dispenserecord.dto.PharmacyDispenseRecordQueryRowDto;
import com.openhis.health.application.outpatient.pharmacy.dispenserecord.support.PharmacyDispenseRecordChargePricingAssembler;
import com.openhis.health.application.outpatient.pharmacy.dispenserecord.support.PharmacyDispenseRecordLoader;
import com.openhis.health.application.outpatient.pharmacy.dispenserecord.support.PharmacyDispenseRecordReadAssembler;
import com.openhis.health.application.report.medicationdispense.inpatient.dto.InpatientMedicationDispenseDetailRowDto;
import com.openhis.health.application.report.medicationdispense.inpatient.dto.InpatientMedicationDispenseSummaryRowDto;
import com.openhis.health.application.report.medicationdispense.support.MedicationDispenseReportPricingEnricher;
import com.openhis.health.application.shared.advice.OrderCatalogDisplayAssembler;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.application.shared.report.PatientSettlementContextRow;
import com.openhis.health.application.shared.report.ReportPatientSettlementContextAssembler;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseReportSummaryRow;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseSlipQueryModel;
import com.openhis.health.domain.medication.medicationdispense.query.MedicationDispenseRecordPageQuery;
import com.openhis.health.domain.medication.medicationdispense.query.MedicationDispenseReportSummaryQuery;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.DeptIssueReportConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InpatientMedicationDispenseReportConstants;
import com.openhis.health.domain.share.constants.MedicationDispenseReportConstants;
import com.openhis.health.domain.share.constants.PharmacyDispenseRecordConstants;
import com.openhis.health.domain.share.constants.PharmacyDispenseRecordExportConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.DeptIssueReportLineQueryModel;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.DeptIssueReportSummaryRow;
import com.openhis.health.domain.workflow.supplyrequest.query.DeptIssueReportPageQuery;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 住院发药报表跨聚合预解析与单源明细装配（AS-RPT-004）。
 *
 * <p>来源仅允许患者发药或科室领用；禁止 ALL 两路并集。仅只读编排；禁止 import 本 Feature Request/Response。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientMedicationDispenseReportQuerySupport {

    /** 发退药记录跨聚合筛选预解析。 */
    private final PharmacyDispenseRecordLoader pharmacyDispenseRecordLoader;

    /** 患者侧发放事实读侧。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /** 科室领用/退领读侧。 */
    private final SupplyRequestQueryService supplyRequestQueryService;

    /** 库位读侧（药房/科室药房）。 */
    private final LocationQueryService locationQueryService;

    /** 药品定义读侧（类型映射）。 */
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;

    /** 药嘱批量读侧（退药价原 service_id 锚点）。 */
    private final MedicationRequestQueryService medicationRequestQueryService;

    /** 收费明细历史价。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 退药原单价补充。 */
    private final PharmacyDispenseRecordChargePricingAssembler pharmacyDispenseRecordChargePricingAssembler;

    /** 住院就诊上下文。 */
    private final EncounterContextReader encounterContextReader;

    /** 科室组织名称批量映射。 */
    private final OrganizationQueryService organizationQueryService;

    /** 发药人姓名批量映射。 */
    private final PractitionerQueryService practitionerQueryService;

    /** 药品定义展示（名称/规格/厂家）。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /** 患者侧平铺行装配。 */
    private final PharmacyDispenseRecordReadAssembler pharmacyDispenseRecordReadAssembler;

    /** 进价/售价 enrich（BR-PRICE-SRC-001）。 */
    private final MedicationDispenseReportPricingEnricher medicationDispenseReportPricingEnricher;

    /** 费用性质 + medType 报表上下文。 */
    private final ReportPatientSettlementContextAssembler reportPatientSettlementContextAssembler;

    /** 药品类型术语码→展示名。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /**
     * 解析来源筛选项；空默认患者发药；ALL / 未知码拒绝（禁止并集混查）。
     *
     * @param sourceCode 请求来源码
     * @return {@link MedicationDispenseReportConstants#SOURCE_PATIENT_DISPENSE} 或
     *         {@link MedicationDispenseReportConstants#SOURCE_DEPT_ISSUE}
     * @throws BusinessException 传入 ALL 或非单源合法码时拒绝
     */
    public String normalizeSourceCode(String sourceCode) {
        String normalized = StringUtils.trimToNull(sourceCode);
        // Gate：未传默认患者发药（产品已拆开患者/领用，页面必选其一）
        if (normalized == null) {
            return MedicationDispenseReportConstants.SOURCE_PATIENT_DISPENSE;
        }
        // Gate：遗留 ALL 明确拒绝，禁止两路并集全窗物化
        if (MedicationDispenseReportConstants.SOURCE_ALL.equals(normalized)) {
            throw new BusinessException(
                InpatientMedicationDispenseReportConstants.EX_IMD_103,
                "禁止全部来源混查");
        }
        // Gate：仅患者或领用单源可走领域真分页
        if (MedicationDispenseReportConstants.SOURCE_PATIENT_DISPENSE.equals(normalized)
            || MedicationDispenseReportConstants.SOURCE_DEPT_ISSUE.equals(normalized)) {
            return normalized;
        }
        throw new BusinessException(
            InpatientMedicationDispenseReportConstants.EX_IMD_103,
            "禁止全部来源混查");
    }

    /**
     * 解析汇总维度；空则按药房。
     *
     * @param summaryDimension 请求维度
     * @return 规范化维度码
     */
    public String normalizeSummaryDimension(String summaryDimension) {
        String normalized = StringUtils.trimToNull(summaryDimension);
        if (normalized == null) {
            return MedicationDispenseReportConstants.SUMMARY_DIMENSION_PHARMACY;
        }
        return normalized;
    }

    /**
     * 日期字符串 → 日初。
     *
     * @param dateText yyyy-MM-dd
     * @return 日初时刻
     */
    public LocalDateTime toDayStart(String dateText) {
        LocalDate date = LocalDate.parse(dateText.trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
        return date.atStartOfDay();
    }

    /**
     * 日期字符串 → 日末。
     *
     * @param dateText yyyy-MM-dd
     * @return 日末时刻
     */
    public LocalDateTime toDayEnd(String dateText) {
        LocalDate date = LocalDate.parse(dateText.trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
        return date.atTime(LocalTime.of(23, 59, 59));
    }

    /**
     * 是否查询患者侧发放（仅 {@code PATIENT_DISPENSE} 为真；禁止未知码误开两路）。
     *
     * @param sourceCode 规范化来源
     * @return true 表示纳入患者侧
     */
    public boolean includePatientDispense(String sourceCode) {
        return MedicationDispenseReportConstants.SOURCE_PATIENT_DISPENSE.equals(sourceCode);
    }

    /**
     * 是否查询科室领用侧（仅 {@code DEPT_ISSUE} 为真；禁止未知码误开两路）。
     *
     * @param sourceCode 规范化来源
     * @return true 表示纳入领用侧
     */
    public boolean includeDeptIssue(String sourceCode) {
        return MedicationDispenseReportConstants.SOURCE_DEPT_ISSUE.equals(sourceCode);
    }

    /**
     * 构建患者侧发退药记录分页 Query 并预解析筛选。
     *
     * @param dispenseDateFrom        日起
     * @param dispenseDateTo          日止
     * @param recordTypeCode          记录类型
     * @param pharmacyLocationId      药房
     * @param dispenserPractitionerId 发药人
     * @param medicationCategoryCodes 类型多选
     * @param drugKeyword             药品关键字
     * @param patientKeyword          患者关键字
     * @param feeNatureContractNo     费用性质 bus_no
     * @param requestingOrgId         开立科室（药嘱 org_id）；空则不筛
     * @param pageNo                  页码
     * @param pageSize                页大小
     * @return 领域查询入参
     */
    public MedicationDispenseRecordPageQuery buildPatientRecordQuery(
        String dispenseDateFrom,
        String dispenseDateTo,
        String recordTypeCode,
        Long pharmacyLocationId,
        Long dispenserPractitionerId,
        List<String> medicationCategoryCodes,
        String drugKeyword,
        String patientKeyword,
        String feeNatureContractNo,
        Long requestingOrgId,
        Integer pageNo,
        Integer pageSize) {
        MedicationDispenseRecordPageQuery query = new MedicationDispenseRecordPageQuery();
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        query.setStartDate(this.toDayStart(dispenseDateFrom));
        query.setEndDate(this.toDayEnd(dispenseDateTo));
        String normalizedRecordType = StringUtils.trimToNull(recordTypeCode);
        if (normalizedRecordType == null) {
            normalizedRecordType = PharmacyDispenseRecordConstants.RECORD_TYPE_ALL;
        }
        query.setRecordTypeCode(normalizedRecordType);
        query.setLocationId(pharmacyLocationId);
        query.setPractitionerId(dispenserPractitionerId);
        this.pharmacyDispenseRecordLoader.enrichInpatientRecordFilters(
            query, medicationCategoryCodes, drugKeyword, patientKeyword);
        this.reportPatientSettlementContextAssembler.applyFeeNatureFilterToMedicationDispenseRecordQuery(
            query, feeNatureContractNo);
        // Pre：开立科室 → 药嘱 org_id 预解析 medReqId（B.6.4）
        if (requestingOrgId != null) {
            query.setResolvedMedReqIdsForOrderingDepartment(
                this.medicationRequestQueryService.listMedicationRequestIdsByOrgId(requestingOrgId));
        }
        return query;
    }

    /**
     * 构建患者侧汇总 Query（含 CATEGORY 维类型映射）。
     *
     * @param recordQuery        已预解析的记录筛选
     * @param summaryDimension   汇总维度
     * @return 汇总查询入参
     */
    public MedicationDispenseReportSummaryQuery buildPatientSummaryQuery(
        MedicationDispenseRecordPageQuery recordQuery,
        String summaryDimension) {
        MedicationDispenseReportSummaryQuery summaryQuery = new MedicationDispenseReportSummaryQuery();
        summaryQuery.setPageNo(recordQuery.getPageNo());
        summaryQuery.setPageSize(recordQuery.getPageSize());
        summaryQuery.setStartDate(recordQuery.getStartDate());
        summaryQuery.setEndDate(recordQuery.getEndDate());
        summaryQuery.setRecordTypeCode(recordQuery.getRecordTypeCode());
        summaryQuery.setInpatientMedicationOnly(recordQuery.getInpatientMedicationOnly());
        summaryQuery.setInpatientMedicationOnly(recordQuery.getInpatientMedicationOnly());
        summaryQuery.setLocationId(recordQuery.getLocationId());
        summaryQuery.setPractitionerId(recordQuery.getPractitionerId());
        summaryQuery.setResolvedEncounterIdsForScene(recordQuery.getResolvedEncounterIdsForScene());
        summaryQuery.setResolvedMedicationIdsForDrugKeyword(recordQuery.getResolvedMedicationIdsForDrugKeyword());
        summaryQuery.setResolvedEncounterIdsForPatientKeyword(recordQuery.getResolvedEncounterIdsForPatientKeyword());
        summaryQuery.setResolvedMedicationIdsForCategory(recordQuery.getResolvedMedicationIdsForCategory());
        summaryQuery.setResolvedMedReqIdsForOrderingDepartment(
            recordQuery.getResolvedMedReqIdsForOrderingDepartment());
        summaryQuery.setSummaryDimension(summaryDimension);
        // CATEGORY 维：先抽样发放行收集品规再批量读类型，避免空映射整桶落入空类型
        if (MedicationDispenseReportConstants.SUMMARY_DIMENSION_CATEGORY.equals(summaryDimension)) {
            summaryQuery.setCategoryCodeByMedicationId(this.resolveCategoryCodeByMedicationId(recordQuery));
        }
        return summaryQuery;
    }

    /**
     * 构建科室药品领用报表 Query。
     *
     * @param dispenseDateFrom        日起
     * @param dispenseDateTo          日止
     * @param pharmacyLocationId      单库位；空则收窄全部启用科室药房
     * @param medicationCategoryCodes 类型
     * @param drugKeyword             药品关键字
     * @param consumingOrganizationId 领用科室；空则不筛
     * @param summaryDimension        汇总维；明细分页可空
     * @param pageNo                  页码
     * @param pageSize                页大小
     * @return 领用查询入参
     */
    public DeptIssueReportPageQuery buildDeptIssueQuery(
        String dispenseDateFrom,
        String dispenseDateTo,
        Long pharmacyLocationId,
        List<String> medicationCategoryCodes,
        String drugKeyword,
        Long consumingOrganizationId,
        String summaryDimension,
        Integer pageNo,
        Integer pageSize) {
        DeptIssueReportPageQuery query = new DeptIssueReportPageQuery();
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        query.setItemTable(TableNameConstants.MED_MEDICATION_DEFINITION);
        query.setBusinessTimeFrom(this.toDayStart(dispenseDateFrom));
        query.setBusinessTimeTo(this.toDayEnd(dispenseDateTo));
        query.setSummaryDimension(summaryDimension);
        if (pharmacyLocationId != null) {
            query.setPurposeLocationId(pharmacyLocationId);
        } else {
            // 报表式：收窄全部启用科室药房，避免误入非科室库位领用
            List<Location> departmentPharmacies = this.locationQueryService.listAllActiveDepartmentPharmacies();
            List<Long> locationIds = new ArrayList<>();
            Map<Long, Long> orgByLocation = new HashMap<>();
            for (Location location : departmentPharmacies) {
                if (location == null || location.getId() == null) {
                    continue;
                }
                // 领用科室筛：仅保留归属该科室的药房（历史空 consuming_org 回落）
                if (consumingOrganizationId != null
                    && !consumingOrganizationId.equals(location.getOrganizationId())) {
                    continue;
                }
                locationIds.add(location.getId());
                orgByLocation.put(location.getId(), location.getOrganizationId());
            }
            query.setResolvedPurposeLocationIds(locationIds);
            query.setOrganizationIdByPurposeLocationId(orgByLocation);
        }
        if (pharmacyLocationId != null) {
            Location single = null;
            List<Location> pharmacies = this.locationQueryService.listAllActiveDepartmentPharmacies();
            for (Location location : pharmacies) {
                if (location != null && pharmacyLocationId.equals(location.getId())) {
                    single = location;
                    break;
                }
            }
            if (single == null) {
                List<Location> allPharmacies = this.locationQueryService.listAllActivePharmacies();
                for (Location location : allPharmacies) {
                    if (location != null && pharmacyLocationId.equals(location.getId())) {
                        single = location;
                        break;
                    }
                }
            }
            if (single != null && single.getOrganizationId() != null) {
                Map<Long, Long> orgByLocation = new HashMap<>();
                orgByLocation.put(single.getId(), single.getOrganizationId());
                query.setOrganizationIdByPurposeLocationId(orgByLocation);
            }
        }
        // 领用科室：落库 consuming_organization_id 精确筛
        if (consumingOrganizationId != null) {
            query.setConsumingOrganizationId(consumingOrganizationId);
        }
        query.setResolvedItemIds(this.resolveMedicationItemIds(medicationCategoryCodes, drugKeyword));
        return query;
    }

    /**
     * 患者侧净汇总。
     *
     * @param query 汇总入参
     * @return 领域汇总行
     */
    public List<MedicationDispenseReportSummaryRow> summarizePatient(MedicationDispenseReportSummaryQuery query) {
        return this.medicationDispenseQueryService.summarizeMedicationDispenseReport(query);
    }

    /**
     * 科室侧净汇总。
     *
     * @param query 领用查询
     * @return 领域汇总行
     */
    public List<DeptIssueReportSummaryRow> summarizeDept(DeptIssueReportPageQuery query) {
        return this.supplyRequestQueryService.summarizeDeptIssueReport(query);
    }

    /**
     * 合并两路汇总行（同维度键净数量相加）。
     *
     * @param patientRows 患者侧
     * @param deptRows    领用侧
     * @param dimension   维度码
     * @return 应用层汇总行
     */
    public List<InpatientMedicationDispenseSummaryRowDto> mergeSummaryRows(
        List<MedicationDispenseReportSummaryRow> patientRows,
        List<DeptIssueReportSummaryRow> deptRows,
        String dimension) {
        Map<String, InpatientMedicationDispenseSummaryRowDto> bucket = new LinkedHashMap<>();
        if (patientRows != null) {
            for (MedicationDispenseReportSummaryRow row : patientRows) {
                if (row == null || StringUtils.isEmpty(row.getDimensionKey())) {
                    continue;
                }
                InpatientMedicationDispenseSummaryRowDto dto = bucket.get(row.getDimensionKey());
                if (dto == null) {
                    dto = new InpatientMedicationDispenseSummaryRowDto();
                    dto.setDimensionKey(row.getDimensionKey());
                    dto.setSummaryDimension(dimension);
                    dto.setLocationId(row.getLocationId());
                    dto.setMedicationId(row.getMedicationId());
                    dto.setCategoryCode(row.getCategoryCode());
                    dto.setNetQuantity(BigDecimal.ZERO);
                    dto.setLineCount(0L);
                    bucket.put(row.getDimensionKey(), dto);
                }
                if (row.getNetQuantity() != null) {
                    dto.setNetQuantity(dto.getNetQuantity().add(row.getNetQuantity()));
                }
                dto.setLineCount(dto.getLineCount() + row.getLineCount());
            }
        }
        if (deptRows != null) {
            for (DeptIssueReportSummaryRow row : deptRows) {
                if (row == null || StringUtils.isEmpty(row.getDimensionKey())) {
                    continue;
                }
                InpatientMedicationDispenseSummaryRowDto dto = bucket.get(row.getDimensionKey());
                if (dto == null) {
                    dto = new InpatientMedicationDispenseSummaryRowDto();
                    dto.setDimensionKey(row.getDimensionKey());
                    dto.setSummaryDimension(dimension);
                    dto.setLocationId(row.getPurposeLocationId());
                    dto.setMedicationId(row.getItemId());
                    dto.setOrganizationId(row.getOrganizationId());
                    dto.setNetQuantity(BigDecimal.ZERO);
                    dto.setLineCount(0L);
                    bucket.put(row.getDimensionKey(), dto);
                }
                if (dto.getOrganizationId() == null) {
                    dto.setOrganizationId(row.getOrganizationId());
                }
                if (dto.getLocationId() == null) {
                    dto.setLocationId(row.getPurposeLocationId());
                }
                if (dto.getMedicationId() == null) {
                    dto.setMedicationId(row.getItemId());
                }
                if (row.getNetQuantity() != null) {
                    dto.setNetQuantity(dto.getNetQuantity().add(row.getNetQuantity()));
                }
                dto.setLineCount(dto.getLineCount() + row.getLineCount());
            }
        }
        List<InpatientMedicationDispenseSummaryRowDto> result = new ArrayList<>(bucket.values());
        this.enrichSummaryDimensionNames(result, dimension);
        return result;
    }

    /**
     * 患者侧明细分页。
     *
     * @param query 记录查询
     * @return 分页壳
     */
    public Page<MedicationDispenseSlipQueryModel> pagePatientSlips(MedicationDispenseRecordPageQuery query) {
        return this.medicationDispenseQueryService.pageMedicationDispenseRecords(query);
    }

    /**
     * 患者侧明细列表（导出）。
     *
     * @param query 记录查询
     * @return 发放行
     */
    public List<MedicationDispenseSlipQueryModel> listPatientSlips(MedicationDispenseRecordPageQuery query) {
        return this.medicationDispenseQueryService.listMedicationDispenseRecords(query);
    }

    /**
     * 科室侧明细分页。
     *
     * @param query 领用查询
     * @return 分页壳
     */
    public Page<DeptIssueReportLineQueryModel> pageDeptLines(DeptIssueReportPageQuery query) {
        return this.supplyRequestQueryService.pageDeptIssueReportLines(query);
    }

    /**
     * 科室侧明细列表。
     *
     * @param query 领用查询
     * @return 明细行
     */
    public List<DeptIssueReportLineQueryModel> listDeptLines(DeptIssueReportPageQuery query) {
        return this.supplyRequestQueryService.listDeptIssueReportLines(query);
    }

    /**
     * 装配患者侧明细行（含 ChargeItem 价）。
     *
     * @param slipRows 发放事实
     * @return 报表明细行
     */
    public List<InpatientMedicationDispenseDetailRowDto> assemblePatientDetailRows(
        List<MedicationDispenseSlipQueryModel> slipRows) {
        if (slipRows == null || slipRows.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> medReqIds = new LinkedHashSet<>();
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        LinkedHashSet<Long> dispenserIds = new LinkedHashSet<>();
        for (MedicationDispenseSlipQueryModel slip : slipRows) {
            if (slip == null) {
                continue;
            }
            if (slip.getMedReqId() != null) {
                medReqIds.add(slip.getMedReqId());
            }
            if (slip.getEncounterId() != null) {
                encounterIds.add(slip.getEncounterId());
            }
            if (slip.getDispenserId() != null) {
                dispenserIds.add(slip.getDispenserId());
            }
        }
        Map<Long, MedicationRequestListQueryModel> medicationRequestByMedReqId = new HashMap<>();
        for (MedicationRequestListQueryModel req
            : this.medicationRequestQueryService.listMedicationRequestListModelsByIds(medReqIds)) {
            if (req != null && req.getId() != null) {
                medicationRequestByMedReqId.put(req.getId(), req);
            }
        }
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId = this.loadProductByMedicationId(slipRows);
        Map<Long, InpatientEncounterContextQueryModel> inpatientContextByEncounterId = new HashMap<>();
        for (InpatientEncounterContextQueryModel context
            : this.encounterContextReader.listInpatientContextsByEncounterIdsOrdered(
                new ArrayList<>(encounterIds))) {
            if (context != null && context.getEncounterId() != null) {
                inpatientContextByEncounterId.put(context.getEncounterId(), context);
            }
        }
        Map<Long, BigDecimal> chargeUnitPriceByDispenseId = new HashMap<>();
        Map<Long, BigDecimal> chargeTotalPriceByDispenseId = new HashMap<>();
        this.mergeChargePricingByEncounterGroups(slipRows, chargeUnitPriceByDispenseId, chargeTotalPriceByDispenseId);
        this.pharmacyDispenseRecordChargePricingAssembler.supplementRefundPricingFromOriginMedicationRequests(
            slipRows,
            medicationRequestByMedReqId,
            chargeUnitPriceByDispenseId,
            chargeTotalPriceByDispenseId);
        LinkedHashSet<Long> orgIds = new LinkedHashSet<>();
        for (MedicationRequestListQueryModel req : medicationRequestByMedReqId.values()) {
            if (req != null && req.getOrgId() != null) {
                orgIds.add(req.getOrgId());
            }
        }
        Map<Long, String> orderingDepartmentNameByOrgId = this.organizationQueryService.mapNamesByIds(orgIds);
        List<Practitioner> dispensers = this.practitionerQueryService.findAllByIds(dispenserIds);
        Map<Long, String> dispenserNameByPractitionerId =
            this.pharmacyDispenseRecordReadAssembler.indexDispenserNames(dispensers);
        List<PharmacyDispenseRecordQueryRowDto> queryRows = this.pharmacyDispenseRecordReadAssembler.assembleInpatientRows(
            slipRows,
            medicationRequestByMedReqId,
            productByMedicationId,
            inpatientContextByEncounterId,
            chargeUnitPriceByDispenseId,
            chargeTotalPriceByDispenseId,
            orderingDepartmentNameByOrgId,
            dispenserNameByPractitionerId);
        List<InpatientMedicationDispenseDetailRowDto> rows = new ArrayList<>(queryRows.size());
        for (PharmacyDispenseRecordQueryRowDto queryRow : queryRows) {
            rows.add(this.toPatientDetailRow(queryRow));
        }
        this.medicationDispenseReportPricingEnricher.enrichPatientDetailPurchaseSale(
            rows, slipRows, productByMedicationId, true);
        this.enrichPatientDetailRowsWithSettlementContext(rows);
        return rows;
    }

    /**
     * 汇总金额专用：患者侧仅 map 聚合字段 + ChargeItem 进售价，跳过药嘱/品名/住院上下文/settlement。
     *
     * @param slipRows 发放事实
     * @return 仅含定价与分桶键字段的明细行
     */
    public List<InpatientMedicationDispenseDetailRowDto> assemblePatientDetailRowsForSummaryPricing(
        List<MedicationDispenseSlipQueryModel> slipRows) {
        if (slipRows == null || slipRows.isEmpty()) {
            return List.of();
        }
        // Pre：跨就诊一次批量拉 ChargeItem（含待结算），售价/进价同批复用
        List<ChargeItem> chargeRows = this.loadChargeItemsForDispensePricing(slipRows, true);
        Map<Long, BigDecimal> chargeUnitPriceByDispenseId = new HashMap<>();
        Map<Long, BigDecimal> chargeTotalPriceByDispenseId = new HashMap<>();
        this.mergeChargePricingFromChargeItems(
            chargeRows, chargeUnitPriceByDispenseId, chargeTotalPriceByDispenseId);
        List<InpatientMedicationDispenseDetailRowDto> rows = new ArrayList<>(slipRows.size());
        for (MedicationDispenseSlipQueryModel slip : slipRows) {
            if (slip == null) {
                continue;
            }
            InpatientMedicationDispenseDetailRowDto row = new InpatientMedicationDispenseDetailRowDto();
            row.setSourceCode(MedicationDispenseReportConstants.SOURCE_PATIENT_DISPENSE);
            row.setDispenseId(slip.getDispenseId());
            row.setEncounterId(slip.getEncounterId());
            row.setPatientId(slip.getPatientId());
            row.setMedicationId(slip.getMedicationId());
            row.setPharmacyLocationId(slip.getLocationId());
            BigDecimal quantity = slip.getDispenseQuantity();
            if (quantity == null) {
                quantity = slip.getQuantity();
            }
            if (quantity == null) {
                quantity = BigDecimal.ZERO;
            }
            // 已退药冲减：数量取负（与明细展示口径对齐，供进价汇总）
            if (DispenseStatus.REFUNDED.equals(slip.getStatusEnum()) && quantity.signum() > 0) {
                quantity = quantity.negate();
            }
            row.setQuantity(quantity);
            if (slip.getDispenseId() != null) {
                row.setUnitPrice(chargeUnitPriceByDispenseId.get(slip.getDispenseId()));
                row.setTotalAmount(chargeTotalPriceByDispenseId.get(slip.getDispenseId()));
            }
            rows.add(row);
        }
        // Asm：进价/售价别名写回；住院账单缺失（含正向）回落批号价，与日结轧差对齐
        this.medicationDispenseReportPricingEnricher.enrichPatientDetailPurchaseSaleFromChargeItems(
            rows,
            chargeRows,
            slipRows,
            this.loadProductByMedicationId(slipRows),
            true);
        return rows;
    }

    /**
     * 患者侧明细行回填费用性质与 medType。
     *
     * <p>费用性质：优先发放锚点收费行 {@code account_id}；账单缺失时回落就诊优先合同。
     * 医疗类别仍取就诊主诊。</p>
     *
     * @param rows 已装配明细行
     */
    private void enrichPatientDetailRowsWithSettlementContext(
        List<InpatientMedicationDispenseDetailRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        LinkedHashSet<Long> dispenseIds = new LinkedHashSet<>();
        for (InpatientMedicationDispenseDetailRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
            if (row.getDispenseId() != null) {
                dispenseIds.add(row.getDispenseId());
            }
        }
        Map<Long, PatientSettlementContextRow> contextByEncounterId =
            this.reportPatientSettlementContextAssembler.mapContextsByEncounterIds(encounterIds);
        List<ChargeItem> chargeItems =
            this.chargeItemQueryService.listChargeItemsForMedicationDispensePricingByDispenseIds(
                dispenseIds, true);
        Map<Long, PatientSettlementContextRow> feeNatureByDispenseId =
            this.reportPatientSettlementContextAssembler.mapFeeNatureContextsByDispenseIdFromChargeItems(
                chargeItems);
        for (InpatientMedicationDispenseDetailRowDto row : rows) {
            if (row == null) {
                continue;
            }
            PatientSettlementContextRow encounterContext = null;
            if (row.getEncounterId() != null) {
                encounterContext = contextByEncounterId.get(row.getEncounterId());
                if (encounterContext != null) {
                    row.setMedType(encounterContext.getMedType());
                    row.setMedTypeDisplay(encounterContext.getMedTypeDisplay());
                }
            }
            // Asm：费用性质优先账单账户
            boolean feeNatureFilled = false;
            if (row.getDispenseId() != null) {
                PatientSettlementContextRow feeNature = feeNatureByDispenseId.get(row.getDispenseId());
                if (feeNature != null && StringUtils.isNotEmpty(feeNature.getFeeNatureContractNo())) {
                    row.setFeeNatureContractNo(feeNature.getFeeNatureContractNo());
                    row.setFeeNatureDisplay(feeNature.getFeeNatureDisplay());
                    feeNatureFilled = true;
                }
            }
            // Asm：退药删账后无挂账账户 → 回落就诊优先合同（如自费）
            if (!feeNatureFilled
                && encounterContext != null
                && StringUtils.isNotEmpty(encounterContext.getFeeNatureContractNo())) {
                row.setFeeNatureContractNo(encounterContext.getFeeNatureContractNo());
                row.setFeeNatureDisplay(encounterContext.getFeeNatureDisplay());
            }
        }
    }

    /**
     * 装配科室领用明细行（落库价；退领数量/金额为负）。
     *
     * @param lines 领用行
     * @return 报表明细行
     */
    public List<InpatientMedicationDispenseDetailRowDto> assembleDeptDetailRows(
        List<DeptIssueReportLineQueryModel> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> locationIds = new LinkedHashSet<>();
        LinkedHashSet<Long> itemIds = new LinkedHashSet<>();
        LinkedHashSet<Long> consumingOrgIds = new LinkedHashSet<>();
        for (DeptIssueReportLineQueryModel line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getPurposeLocationId() != null) {
                locationIds.add(line.getPurposeLocationId());
            }
            if (line.getItemId() != null) {
                itemIds.add(line.getItemId());
            }
            // 落库领用科室：须纳入名称批量查询，避免只查药房上级科室导致手选科室无名
            if (line.getConsumingOrganizationId() != null) {
                consumingOrgIds.add(line.getConsumingOrganizationId());
            }
        }
        Map<Long, String> locationNameById = this.locationQueryService.mapNamesByIds(locationIds);
        Map<Long, Long> orgByLocation = new HashMap<>();
        List<Location> locations = this.locationQueryService.listAllActiveDepartmentPharmacies();
        for (Location location : locations) {
            if (location != null && location.getId() != null && locationIds.contains(location.getId())) {
                orgByLocation.put(location.getId(), location.getOrganizationId());
            }
        }
        LinkedHashSet<Long> orgIds = new LinkedHashSet<>(orgByLocation.values());
        orgIds.addAll(consumingOrgIds);
        Map<Long, String> orgNameById = this.organizationQueryService.mapNamesByIds(orgIds);
        Map<Long, ProductDefinitionDisplayDto> productById = this.loadProductByItemIds(itemIds);
        List<InpatientMedicationDispenseDetailRowDto> rows = new ArrayList<>(lines.size());
        for (DeptIssueReportLineQueryModel line : lines) {
            if (line == null) {
                continue;
            }
            rows.add(this.toDeptDetailRow(line, locationNameById, orgByLocation, orgNameById, productById));
        }
        return rows;
    }

    /**
     * 汇总金额专用：科室领用仅 map 聚合字段 + 进售价，跳过库位名/品名等展示。
     *
     * @param lines 领用行
     * @return 仅含定价与分桶键字段的明细行
     */
    public List<InpatientMedicationDispenseDetailRowDto> assembleDeptDetailRowsForSummaryPricing(
        List<DeptIssueReportLineQueryModel> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<InpatientMedicationDispenseDetailRowDto> rows = new ArrayList<>(lines.size());
        for (DeptIssueReportLineQueryModel line : lines) {
            if (line == null) {
                continue;
            }
            InpatientMedicationDispenseDetailRowDto row = new InpatientMedicationDispenseDetailRowDto();
            row.setSourceCode(MedicationDispenseReportConstants.SOURCE_DEPT_ISSUE);
            row.setSupplyRequestId(line.getSupplyRequestId());
            row.setPharmacyLocationId(line.getPurposeLocationId());
            // 领用科室：优先落库 consumingOrganizationId；缺失时由 aggregate 经 orgByLocation 回落
            row.setDepartmentId(line.getConsumingOrganizationId());
            row.setMedicationId(line.getItemId());
            BigDecimal quantity = this.medicationDispenseReportPricingEnricher.resolveDeptSignedQuantity(line);
            BigDecimal signedPurchaseTotal =
                this.medicationDispenseReportPricingEnricher.resolveDeptSignedPurchaseTotal(line);
            row.setQuantity(quantity);
            // Asm：进价/售价写回，不查产品展示名
            this.medicationDispenseReportPricingEnricher.enrichDeptDetailPurchaseSale(
                row, line, quantity, signedPurchaseTotal);
            rows.add(row);
        }
        return rows;
    }

    /**
     * 按明细行 enrich 汇总行进价/售价净额（BR-PRICE-SRC-001）。
     *
     * @param summaryRows              已合并汇总行
     * @param patientRecordQuery       患者侧筛选；不查患者时为 null
     * @param deptIssueQuery           领用侧筛选；不查领用时为 null
     * @param queryPatient             是否纳入患者侧
     * @param queryDept                是否纳入领用侧
     * @param summaryDimension         汇总维度
     * @param categoryCodeByMedicationId CATEGORY 维品规→类型；可空
     */
    public void enrichSummaryPurchaseSaleAmounts(
        List<InpatientMedicationDispenseSummaryRowDto> summaryRows,
        MedicationDispenseRecordPageQuery patientRecordQuery,
        DeptIssueReportPageQuery deptIssueQuery,
        boolean queryPatient,
        boolean queryDept,
        String summaryDimension,
        Map<Long, String> categoryCodeByMedicationId) {
        if (summaryRows == null || summaryRows.isEmpty()) {
            return;
        }
        List<InpatientMedicationDispenseDetailRowDto> detailRows = new ArrayList<>();
        if (queryPatient && patientRecordQuery != null) {
            List<MedicationDispenseSlipQueryModel> slips =
                this.listPatientSlips(patientRecordQuery);
            // Pre：汇总定价仅装最小字段 + ChargeItem 进售价，跳过展示 enrich
            detailRows.addAll(this.assemblePatientDetailRowsForSummaryPricing(slips));
        }
        if (queryDept && deptIssueQuery != null) {
            List<DeptIssueReportLineQueryModel> deptLines = this.listDeptLines(deptIssueQuery);
            detailRows.addAll(this.assembleDeptDetailRowsForSummaryPricing(deptLines));
        }
        Map<Long, Long> orgByLocation = new HashMap<>();
        if (deptIssueQuery != null && deptIssueQuery.getOrganizationIdByPurposeLocationId() != null) {
            orgByLocation.putAll(deptIssueQuery.getOrganizationIdByPurposeLocationId());
        }
        this.medicationDispenseReportPricingEnricher.aggregateSummaryPurchaseSaleAmounts(
            summaryRows,
            detailRows,
            summaryDimension,
            categoryCodeByMedicationId,
            orgByLocation);
    }

    /**
     * 按药品类型/关键字解析领用侧 item_id 预筛；无筛选时返回 null（不收窄）。
     *
     * @param medicationCategoryCodes 类型
     * @param drugKeyword             关键字
     * @return 预解析 id；无筛选 null；有筛选无命中空列表
     */
    private List<Long> resolveMedicationItemIds(List<String> medicationCategoryCodes, String drugKeyword) {
        boolean hasCategory = medicationCategoryCodes != null && !medicationCategoryCodes.isEmpty();
        boolean hasKeyword = StringUtils.isNotEmpty(drugKeyword);
        // 无类型且无关键字：不写 resolvedItemIds，领用侧不过滤品规
        if (!hasCategory && !hasKeyword) {
            return null;
        }
        LinkedHashSet<Long> union = new LinkedHashSet<>();
        boolean initialized = false;
        // 类型多选 → medication_definition.id IN
        if (hasCategory) {
            List<Long> byCategory =
                this.medicationDefinitionQueryService.listIdsByCategoryCodesIn(medicationCategoryCodes);
            union.addAll(byCategory);
            initialized = true;
        }
        // 药品关键字 → catalog 预解析 id；与类型同时存在时取交集
        if (hasKeyword) {
            MedicationDispenseRecordPageQuery probe = new MedicationDispenseRecordPageQuery();
            this.pharmacyDispenseRecordLoader.enrichInpatientRecordFilters(probe, null, drugKeyword, null);
            List<Long> byKeyword = probe.getResolvedMedicationIdsForDrugKeyword();
            // 仅关键字：直接并入
            if (!initialized) {
                if (byKeyword != null) {
                    union.addAll(byKeyword);
                }
            } else {
                // 类型∩关键字：无命中则空列表拒查
                LinkedHashSet<Long> keywordSet = new LinkedHashSet<>();
                if (byKeyword != null) {
                    keywordSet.addAll(byKeyword);
                }
                union.retainAll(keywordSet);
            }
        }
        return new ArrayList<>(union);
    }

    /**
     * CATEGORY 维：解析品规→类型映射。
     *
     * <p>优先复用记录筛选已预解析的 medicationId（空列表表示筛无命中，禁止再全窗抽样）；
     * 仅完全无预解析时才抽样发放行。</p>
     *
     * @param recordQuery 同筛选记录查询
     * @return 品规→类型
     */
    private Map<Long, String> resolveCategoryCodeByMedicationId(MedicationDispenseRecordPageQuery recordQuery) {
        LinkedHashSet<Long> medicationIds = new LinkedHashSet<>();
        List<Long> categoryResolved = recordQuery.getResolvedMedicationIdsForCategory();
        List<Long> keywordResolved = recordQuery.getResolvedMedicationIdsForDrugKeyword();
        // Pre：已有类型或关键字预解析则直接用，空列表=无命中不再扫发放窗
        if (categoryResolved != null) {
            medicationIds.addAll(categoryResolved);
        } else if (keywordResolved != null) {
            medicationIds.addAll(keywordResolved);
        } else {
            MedicationDispenseRecordPageQuery probe = new MedicationDispenseRecordPageQuery();
            probe.setPageNo(1);
            probe.setPageSize(PharmacyDispenseRecordExportConstants.EXPORT_MAX_ROWS);
            probe.setStartDate(recordQuery.getStartDate());
            probe.setEndDate(recordQuery.getEndDate());
            probe.setRecordTypeCode(recordQuery.getRecordTypeCode());
            probe.setOutpatientMedicationOnly(recordQuery.getOutpatientMedicationOnly());
            probe.setInpatientMedicationOnly(recordQuery.getInpatientMedicationOnly());
            probe.setLocationId(recordQuery.getLocationId());
            probe.setPractitionerId(recordQuery.getPractitionerId());
            probe.setResolvedEncounterIdsForScene(recordQuery.getResolvedEncounterIdsForScene());
            probe.setResolvedMedicationIdsForDrugKeyword(recordQuery.getResolvedMedicationIdsForDrugKeyword());
            probe.setResolvedEncounterIdsForPatientKeyword(recordQuery.getResolvedEncounterIdsForPatientKeyword());
            probe.setResolvedMedicationIdsForCategory(recordQuery.getResolvedMedicationIdsForCategory());
            List<MedicationDispenseSlipQueryModel> slips =
                this.medicationDispenseQueryService.listMedicationDispenseRecords(probe);
            for (MedicationDispenseSlipQueryModel slip : slips) {
                if (slip != null && slip.getMedicationId() != null) {
                    medicationIds.add(slip.getMedicationId());
                }
            }
        }
        Map<Long, String> categoryById = new HashMap<>();
        if (medicationIds.isEmpty()) {
            return categoryById;
        }
        // 批量读定义表 category_code；缺失映射落入空类型桶由领域处理
        for (MedicationDefinition definition : this.medicationDefinitionQueryService.findAllByIds(medicationIds)) {
            if (definition != null && definition.getId() != null) {
                categoryById.put(definition.getId(), definition.getCategoryCode());
            }
        }
        return categoryById;
    }

    /**
     * 回填汇总行维度展示名。
     *
     * @param rows      汇总行
     * @param dimension 维度
     */
    private void enrichSummaryDimensionNames(
        List<InpatientMedicationDispenseSummaryRowDto> rows,
        String dimension) {
        // 空结果无需 enrich
        if (rows == null || rows.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> locationIds = new LinkedHashSet<>();
        LinkedHashSet<Long> medicationIds = new LinkedHashSet<>();
        LinkedHashSet<Long> orgIds = new LinkedHashSet<>();
        // 收集维度主键批量读名称
        for (InpatientMedicationDispenseSummaryRowDto row : rows) {
            if (row.getLocationId() != null) {
                locationIds.add(row.getLocationId());
            }
            if (row.getMedicationId() != null) {
                medicationIds.add(row.getMedicationId());
            }
            if (row.getOrganizationId() != null) {
                orgIds.add(row.getOrganizationId());
            }
        }
        Map<Long, String> locationNameById = this.locationQueryService.mapNamesByIds(locationIds);
        Map<Long, String> orgNameById = this.organizationQueryService.mapNamesByIds(orgIds);
        Map<Long, ProductDefinitionDisplayDto> productById = this.loadProductByItemIds(medicationIds);
        // 类型维：术语码→展示名（禁止把 categoryCode 原样当 dimensionName）
        Map<String, String> categoryLabelByCode = Map.of();
        if (MedicationDispenseReportConstants.SUMMARY_DIMENSION_CATEGORY.equals(dimension)) {
            categoryLabelByCode = this.terminologyOptionLoader.buildLabelMap(
                TerminologyCodeSystemNameConstants.MEDICATION_CATEGORY_CODE);
        }
        for (InpatientMedicationDispenseSummaryRowDto row : rows) {
            // 品规维：展示药品通用名
            if (MedicationDispenseReportConstants.SUMMARY_DIMENSION_ITEM.equals(dimension)) {
                ProductDefinitionDisplayDto product = productById.get(row.getMedicationId());
                if (product != null) {
                    row.setDimensionName(product.getProductName());
                }
            } else if (MedicationDispenseReportConstants.SUMMARY_DIMENSION_CATEGORY.equals(dimension)) {
                String categoryCode = row.getCategoryCode();
                String categoryLabel =
                    OrderCatalogDisplayAssembler.resolveCategoryDisplay(categoryLabelByCode, categoryCode);
                if (StringUtils.isNotEmpty(categoryLabel)) {
                    row.setDimensionName(categoryLabel);
                } else {
                    // 未命中术语时回落 code，避免空维度
                    row.setDimensionName(categoryCode);
                }
            } else if (MedicationDispenseReportConstants.SUMMARY_DIMENSION_DEPARTMENT.equals(dimension)
                || DeptIssueReportConstants.SUMMARY_DIMENSION_DEPARTMENT.equals(dimension)) {
                // 科室维：组织名称
                row.setDimensionName(orgNameById.get(row.getOrganizationId()));
            } else {
                // 默认药房维
                row.setDimensionName(locationNameById.get(row.getLocationId()));
            }
        }
    }

    private InpatientMedicationDispenseDetailRowDto toPatientDetailRow(PharmacyDispenseRecordQueryRowDto source) {
        InpatientMedicationDispenseDetailRowDto row = new InpatientMedicationDispenseDetailRowDto();
        row.setSourceCode(MedicationDispenseReportConstants.SOURCE_PATIENT_DISPENSE);
        row.setSourceText(MedicationDispenseReportConstants.SOURCE_PATIENT_DISPENSE_LABEL);
        row.setBusinessTime(source.getDispenseTime());
        row.setDispenseId(source.getDispenseId());
        row.setBusNo(source.getSummaryNo());
        row.setRecordTypeCode(source.getRecordTypeCode());
        row.setRecordTypeText(source.getRecordTypeText());
        row.setPharmacyLocationId(source.getPharmacyLocationId());
        row.setPharmacyLocationName(source.getPharmacyLocationName());
        row.setDispenserPractitionerId(source.getDispenserPractitionerId());
        row.setDispenserName(source.getDispenserName());
        row.setPatientId(source.getPatientId());
        row.setPatientName(source.getPatientName());
        row.setEncounterId(source.getEncounterId());
        row.setInpatientNo(source.getInpatientNo());
        row.setWardName(source.getWardName());
        row.setBedNo(source.getBedName());
        row.setPrescriptionNo(source.getPrescriptionNo());
        row.setMedicationId(source.getMedicationId());
        row.setMedicationCode(source.getMedicationCode());
        row.setMedicationName(source.getMedicationName());
        row.setMedicationSpec(source.getMedicationSpec());
        row.setQuantity(source.getQuantity());
        row.setUnitCode(source.getUnitCode());
        row.setUnitText(source.getUnitText());
        row.setUnitPrice(source.getUnitPrice());
        row.setTotalAmount(source.getTotalAmount());
        row.setLotNumber(source.getLotNumber());
        row.setTraceNo(source.getTraceNo());
        row.setDepartmentId(source.getOrderingDepartmentId());
        row.setDepartmentName(source.getOrderingDepartmentName());
        row.setUsageText(source.getUsageText());
        row.setManufacturerText(source.getManufacturerText());
        row.setMedReqId(source.getMedReqId());
        return row;
    }

    private InpatientMedicationDispenseDetailRowDto toDeptDetailRow(
        DeptIssueReportLineQueryModel line,
        Map<Long, String> locationNameById,
        Map<Long, Long> orgByLocation,
        Map<Long, String> orgNameById,
        Map<Long, ProductDefinitionDisplayDto> productById) {
        InpatientMedicationDispenseDetailRowDto row = new InpatientMedicationDispenseDetailRowDto();
        row.setSourceCode(MedicationDispenseReportConstants.SOURCE_DEPT_ISSUE);
        row.setSourceText(MedicationDispenseReportConstants.SOURCE_DEPT_ISSUE_LABEL);
        if (line.getBusinessTime() != null) {
            row.setBusinessTime(line.getBusinessTime().format(GeneralSharedConstants.DATE_TIME_FORMATTER));
        }
        row.setSupplyRequestId(line.getSupplyRequestId());
        row.setBusNo(line.getBusNo());
        row.setRecordTypeCode(line.getTypeEnum());
        if (SupplyType.DEPT_ISSUE_RETURN.getCode().equals(line.getTypeEnum())) {
            row.setRecordTypeText("科室退领");
        } else {
            row.setRecordTypeText("科室领用");
        }
        row.setPharmacyLocationId(line.getPurposeLocationId());
        row.setPharmacyLocationName(locationNameById.get(line.getPurposeLocationId()));
        // 领用科室：优先落库 consumingOrganizationId，历史单回落药房上级科室
        Long orgId = line.getConsumingOrganizationId();
        if (orgId == null) {
            orgId = orgByLocation.get(line.getPurposeLocationId());
        }
        row.setDepartmentId(orgId);
        row.setDepartmentName(orgNameById.get(orgId));
        row.setMedicationId(line.getItemId());
        ProductDefinitionDisplayDto product = productById.get(line.getItemId());
        if (product != null) {
            row.setMedicationCode(product.getProductBusNo());
            row.setMedicationName(product.getProductName());
            row.setMedicationSpec(product.getTotalVolume());
            row.setManufacturerText(product.getManufacturer());
            row.setUnitText(product.getSplitUnitCodeDictText());
        }
        row.setUnitCode(line.getUnitCode());
        BigDecimal quantity = this.medicationDispenseReportPricingEnricher.resolveDeptSignedQuantity(line);
        BigDecimal signedPurchaseTotal =
            this.medicationDispenseReportPricingEnricher.resolveDeptSignedPurchaseTotal(line);
        row.setQuantity(quantity);
        row.setLotNumber(line.getLotNumber());
        this.medicationDispenseReportPricingEnricher.enrichDeptDetailPurchaseSale(
            row, line, quantity, signedPurchaseTotal);
        return row;
    }

    private Map<Long, ProductDefinitionDisplayDto> loadProductByMedicationId(
        List<MedicationDispenseSlipQueryModel> slipRows) {
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (MedicationDispenseSlipQueryModel slip : slipRows) {
            if (slip == null || slip.getMedicationId() == null) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
            lookup.setProductDefinitionId(slip.getMedicationId());
            lookups.add(lookup);
        }
        return this.indexProductByDefinitionId(lookups);
    }

    private Map<Long, ProductDefinitionDisplayDto> loadProductByItemIds(LinkedHashSet<Long> itemIds) {
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (Long itemId : itemIds) {
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
            lookup.setProductDefinitionId(itemId);
            lookups.add(lookup);
        }
        return this.indexProductByDefinitionId(lookups);
    }

    private Map<Long, ProductDefinitionDisplayDto> indexProductByDefinitionId(
        List<ProductDefinitionBatchLookup> lookups) {
        Map<String, ProductDefinitionDisplayDto> productByKey =
            this.productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
        Map<Long, ProductDefinitionDisplayDto> productById = new HashMap<>();
        for (ProductDefinitionBatchLookup lookup : lookups) {
            if (lookup == null || lookup.getProductDefinitionId() == null) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.MED_MEDICATION_DEFINITION, lookup.getProductDefinitionId());
            ProductDefinitionDisplayDto product = productByKey.get(key);
            if (product != null) {
                productById.put(lookup.getProductDefinitionId(), product);
            }
        }
        return productById;
    }

    /**
     * 按发放主键批量加载发药定价 ChargeItem（明细/汇总共用；住院含待结算态）。
     *
     * @param slipRows                 发放事实
     * @param includePendingSettlement 是否含住院待结算态
     * @return 收费明细；无发放主键时为空表
     */
    private List<ChargeItem> loadChargeItemsForDispensePricing(
        List<MedicationDispenseSlipQueryModel> slipRows,
        boolean includePendingSettlement) {
        LinkedHashSet<Long> dispenseIds = new LinkedHashSet<>();
        for (MedicationDispenseSlipQueryModel slip : slipRows) {
            if (slip != null && slip.getDispenseId() != null) {
                dispenseIds.add(slip.getDispenseId());
            }
        }
        if (dispenseIds.isEmpty()) {
            return List.of();
        }
        return this.chargeItemQueryService.listChargeItemsForMedicationDispensePricingByDispenseIds(
            dispenseIds, includePendingSettlement);
    }

    /**
     * 批量回填售价单价/总额（含待结算；跨就诊一次 IN，替代按就诊 + pending 双循环）。
     *
     * @param slipRows      发放事实
     * @param unitPriceOut  dispenseId → 售价单价
     * @param totalPriceOut dispenseId → 售价总额
     */
    private void mergeChargePricingByEncounterGroups(
        List<MedicationDispenseSlipQueryModel> slipRows,
        Map<Long, BigDecimal> unitPriceOut,
        Map<Long, BigDecimal> totalPriceOut) {
        List<ChargeItem> chargeRows = this.loadChargeItemsForDispensePricing(slipRows, true);
        this.mergeChargePricingFromChargeItems(chargeRows, unitPriceOut, totalPriceOut);
    }

    private void mergeChargePricingFromChargeItems(
        List<ChargeItem> chargeRows,
        Map<Long, BigDecimal> unitPriceOut,
        Map<Long, BigDecimal> totalPriceOut) {
        Map<Long, BigDecimal> totalByDispense = new HashMap<>();
        Map<Long, BigDecimal> qtyByDispense = new HashMap<>();
        for (ChargeItem chargeRow : chargeRows) {
            if (chargeRow == null || chargeRow.getDispenseId() == null) {
                continue;
            }
            Long dispenseKey = chargeRow.getDispenseId();
            if (chargeRow.getTotalPrice() != null) {
                BigDecimal existingTotal = totalByDispense.get(dispenseKey);
                if (existingTotal == null) {
                    totalByDispense.put(dispenseKey, chargeRow.getTotalPrice());
                } else {
                    totalByDispense.put(dispenseKey, existingTotal.add(chargeRow.getTotalPrice()));
                }
            }
            if (chargeRow.getQuantityValue() != null) {
                BigDecimal existingQty = qtyByDispense.get(dispenseKey);
                if (existingQty == null) {
                    qtyByDispense.put(dispenseKey, chargeRow.getQuantityValue());
                } else {
                    qtyByDispense.put(dispenseKey, existingQty.add(chargeRow.getQuantityValue()));
                }
            }
        }
        for (Map.Entry<Long, BigDecimal> entry : totalByDispense.entrySet()) {
            Long dispenseKey = entry.getKey();
            BigDecimal totalVal = entry.getValue();
            totalPriceOut.put(dispenseKey, totalVal);
            BigDecimal qtyVal = qtyByDispense.get(dispenseKey);
            if (qtyVal != null && qtyVal.compareTo(BigDecimal.ZERO) > 0) {
                unitPriceOut.put(dispenseKey, MonetaryAmountSupport.unitPriceFromLineTotal(totalVal, qtyVal));
            }
        }
    }
}
