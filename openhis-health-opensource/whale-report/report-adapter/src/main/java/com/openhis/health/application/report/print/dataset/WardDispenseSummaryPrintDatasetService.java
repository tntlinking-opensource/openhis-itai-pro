/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.report.print.dataset;
import com.openhis.health.application.report.support.ReportDatasetTextSupport;

import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseSlipQueryModel;
import com.openhis.health.domain.medication.medicationdispense.query.MedicationDispenseSlipPageQuery;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.OrganizationType;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 病区发药汇总单打印数据集（对齐 {@code ward_dispense_summary} 元素包 bindPath）。
 */
@Service
@RequiredArgsConstructor
public class WardDispenseSummaryPrintDatasetService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final MedicationRequestQueryService medicationRequestQueryService;
    private final EncounterContextFacade encounterContextFacade;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    private final LocationQueryService locationQueryService;
    private final OrganizationQueryService organizationQueryService;    /**
     * 组装打印数据集 root Map（供 Port / 预览 bindPath 绑定）。
     *
     * @param params 业务参数（如 encounterId、paymentId、dateFrom 等）
     * @return 数据集 root；必填参数缺失时返回空 Map
     */

    public Map<String, Object> build(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return Map.of();
        }
        String summaryNo = extractString(params, "summaryNo", "summary_no");
        if (StringUtils.isBlank(summaryNo)) {
            return Map.of();
        }

        MedicationDispenseSlipPageQuery query = new MedicationDispenseSlipPageQuery();
        query.setSummaryNo(summaryNo.trim());
        query.setInpatientMedicationOnly(true);
        List<MedicationDispenseSlipQueryModel> slipRows =
                medicationDispenseQueryService.listMedicationDispenseSlip(query);
        if (slipRows == null || slipRows.isEmpty()) {
            return Map.of();
        }

        Set<Long> encounterIds = new LinkedHashSet<>();
        Set<Long> locationIds = new LinkedHashSet<>();
        Set<Long> medicationIds = new LinkedHashSet<>();
        for (MedicationDispenseSlipQueryModel row : slipRows) {
            if (row == null) {
                continue;
            }
            if (row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
            if (row.getLocationId() != null) {
                locationIds.add(row.getLocationId());
            }
            if (row.getMedicationId() != null) {
                medicationIds.add(row.getMedicationId());
            }
        }

        Map<Long, InpatientEncounterContextQueryModel> encounterById = new HashMap<>();
        for (Long encounterId : encounterIds) {
            encounterContextFacade.getInpatientContextByEncounterId(encounterId)
                    .ifPresent(ctx -> encounterById.put(encounterId, ctx));
        }

        Map<Long, MedicationRequestListQueryModel> medReqById = loadMedReqByEncounterIds(encounterIds);
        Map<Long, String> pharmacyNameByLocationId = locationQueryService.mapNamesByIds(locationIds);
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId = loadProductByMedicationId(slipRows);

        List<Map<String, Object>> lines = new ArrayList<>();
        LocalDateTime earliestApplyTime = null;
        String primaryPharmacyName = "";
        for (MedicationDispenseSlipQueryModel row : slipRows) {
            if (row == null) {
                continue;
            }
            InpatientEncounterContextQueryModel encounter = encounterById.get(row.getEncounterId());
            MedicationRequestListQueryModel medReq = row.getMedReqId() != null
                    ? medReqById.get(row.getMedReqId())
                    : null;
            ProductDefinitionDisplayDto product = row.getMedicationId() != null
                    ? productByMedicationId.get(row.getMedicationId())
                    : null;
            String pharmacyName = "";
            if (row.getLocationId() != null) {
                pharmacyName = nullToEmpty(pharmacyNameByLocationId.get(row.getLocationId()));
            }
            if (StringUtils.isBlank(primaryPharmacyName) && StringUtils.isNotBlank(pharmacyName)) {
                primaryPharmacyName = pharmacyName;
            }
            LocalDateTime applyCandidate = row.getStatusChangedTime();
            if (row.getPrepareTime() != null) {
                applyCandidate = row.getPrepareTime();
            }
            if (applyCandidate != null
                    && (earliestApplyTime == null || applyCandidate.isBefore(earliestApplyTime))) {
                earliestApplyTime = applyCandidate;
            }

            Map<String, Object> line = new LinkedHashMap<>();
            String bedNo = "";
            String patientName = "";
            String encounterBusNo = "";
            if (encounter != null) {
                bedNo = nullToEmpty(encounter.getBedName());
                patientName = nullToEmpty(encounter.getPatientName());
                encounterBusNo = nullToEmpty(encounter.getEncounterBusNo());
            }
            line.put("bedNo", bedNo);
            line.put("patientName", patientName);
            line.put("encounterBusNo", encounterBusNo);
            String drugName = "";
            if (product != null) {
                drugName = nullToEmpty(product.getProductName());
            }
            line.put("drugName", drugName);
            line.put("quantity", formatQuantity(row.getQuantity()));
            line.put("unitText", nullToEmpty(row.getUnitCode()));
            line.put("orderClass", resolveOrderClass(medReq));
            line.put("pharmacyName", pharmacyName);
            lines.add(line);
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("hospital", Map.of("name", resolveHospitalDisplayName(encounterById.values())));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("summaryNo", summaryNo.trim());
        summary.put("applyTime", ReportDatasetTextSupport.formatDateTimeOrEmpty(TIME_FMT, earliestApplyTime));
        summary.put("pharmacyName", primaryPharmacyName);
        summary.put("patientCount", String.valueOf(encounterIds.size()));
        summary.put("lineCount", String.valueOf(lines.size()));
        summary.put("lines", lines);
        root.put("summary", summary);
        root.put("system", Map.of("printTime", TIME_FMT.format(LocalDateTime.now())));
        return root;
    }

    private Map<Long, MedicationRequestListQueryModel> loadMedReqByEncounterIds(Set<Long> encounterIds) {
        Map<Long, MedicationRequestListQueryModel> medReqById = new HashMap<>();
        for (Long encounterId : encounterIds) {
            List<MedicationRequestListQueryModel> rows =
                    medicationRequestQueryService.listMedicationRequestsByEncounter(encounterId);
            for (MedicationRequestListQueryModel row : rows) {
                if (row != null && row.getId() != null) {
                    medReqById.put(row.getId(), row);
                }
            }
        }
        return medReqById;
    }

    private Map<Long, ProductDefinitionDisplayDto> loadProductByMedicationId(
            List<MedicationDispenseSlipQueryModel> slipRows) {
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        Set<Long> seenMedicationIds = new LinkedHashSet<>();
        for (MedicationDispenseSlipQueryModel row : slipRows) {
            if (row == null || row.getMedicationId() == null || !seenMedicationIds.add(row.getMedicationId())) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
            lookup.setProductDefinitionId(row.getMedicationId());
            lookups.add(lookup);
        }
        Map<String, ProductDefinitionDisplayDto> productByKey =
                productDefinitionBatchLoader.loadAsMap(lookups);
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId = new HashMap<>();
        for (MedicationDispenseSlipQueryModel row : slipRows) {
            if (row == null || row.getMedicationId() == null) {
                continue;
            }
            String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
                    TableNameConstants.MED_MEDICATION_DEFINITION, row.getMedicationId());
            ProductDefinitionDisplayDto product = productByKey.get(compositeKey);
            if (product != null) {
                productByMedicationId.put(row.getMedicationId(), product);
            }
        }
        return productByMedicationId;
    }

    private static String resolveOrderClass(MedicationRequestListQueryModel medReq) {
        if (medReq == null || StringUtils.isBlank(medReq.getTherapyEnum())) {
            return "";
        }
        TherapyType therapyType = EnumUtil.fromCode(TherapyType.class, medReq.getTherapyEnum().trim());
        if (therapyType != null) {
            return therapyType.getDisplay();
        }
        return medReq.getTherapyEnum();
    }

    private String resolveHospitalDisplayName(Iterable<InpatientEncounterContextQueryModel> encounters) {
        for (InpatientEncounterContextQueryModel encounter : encounters) {
            String name = resolveHospitalDisplayName(encounter);
            if (StringUtils.isNotBlank(name)) {
                return name;
            }
        }
        return "";
    }

    private String resolveHospitalDisplayName(InpatientEncounterContextQueryModel encounter) {
        if (encounter == null || encounter.getOrganizationId() == null) {
            return "";
        }
        Long orgId = encounter.getOrganizationId();
        for (int depth = 0; depth < 12; depth++) {
            Organization org = organizationQueryService.loadOrganizationByIdForDisplay(orgId);
            if (org == null) {
                break;
            }
            if (OrganizationType.HOSPITAL.getCode().equals(org.getTypeEnum())
                    && StringUtils.isNotBlank(org.getName())) {
                return org.getName().trim();
            }
            if (org.getParentId() == null) {
                if (StringUtils.isNotBlank(org.getName())) {
                    return org.getName().trim();
                }
                break;
            }
            orgId = org.getParentId();
        }
        return "";
    }

    private static String formatQuantity(BigDecimal quantity) {
        if (quantity == null) {
            return "";
        }
        return quantity.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String nullToEmpty(String value) {
        return ReportDatasetTextSupport.stringOrEmpty(value);
    }

    private static String extractString(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            Object raw = params.get(key);
            if (raw instanceof String text && StringUtils.isNotBlank(text)) {
                return text.trim();
            }
            if (raw != null && StringUtils.isNotBlank(String.valueOf(raw))) {
                return String.valueOf(raw).trim();
            }
        }
        return null;
    }
}
