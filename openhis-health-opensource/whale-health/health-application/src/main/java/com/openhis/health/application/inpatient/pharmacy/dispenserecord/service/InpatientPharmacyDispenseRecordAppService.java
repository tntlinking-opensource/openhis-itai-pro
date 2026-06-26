/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.pharmacy.dispenserecord.service;

import com.openhis.health.application.inpatient.pharmacy.dispenserecord.dto.InpatientPharmacyDispenseRecordExportRowDto;
import com.openhis.health.application.inpatient.pharmacy.dispenserecord.dto.InpatientPharmacyDispenseRecordRowDto;
import com.openhis.health.application.inpatient.pharmacy.dispenserecord.mapper.InpatientPharmacyDispenseRecordMapper;
import com.openhis.health.application.inpatient.pharmacy.dispenserecord.request.ExportInpatientPharmacyDispenseRecordsRequest;
import com.openhis.health.application.inpatient.pharmacy.dispenserecord.request.LoadInpatientPharmacyDispenseRecordPageInitRequest;
import com.openhis.health.application.inpatient.pharmacy.dispenserecord.request.PageInpatientPharmacyDispenseRecordsRequest;
import com.openhis.health.application.inpatient.pharmacy.dispenserecord.response.ExportInpatientPharmacyDispenseRecordsResponse;
import com.openhis.health.application.inpatient.pharmacy.dispenserecord.response.LoadInpatientPharmacyDispenseRecordPageInitResponse;
import com.openhis.health.application.inpatient.pharmacy.dispenserecord.response.PageInpatientPharmacyDispenseRecordsResponse;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.medication.medicationdispense.query.MedicationDispenseRecordPageQuery;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseSlipQueryModel;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InpatientPharmacyDispenseRecordConstants;
import com.openhis.health.domain.share.constants.PharmacyDispenseRecordConstants;
import com.openhis.health.domain.share.constants.PharmacyDispenseRecordExportConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.module.common.assembler.option.TerminologyOptionLoader;
import com.openhis.health.module.common.validator.PageWindowGate;
import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.module.querysupport.pharmacy.InpatientMedicationDispenseChargePricingLoader;
import com.openhis.health.module.querysupport.pharmacy.PharmacyDispenseRecordChargePricingSupport;
import com.openhis.health.module.querysupport.pharmacy.PharmacyDispenseRecordQuerySupport;
import com.openhis.health.module.querysupport.pharmacy.PharmacyDispenseRecordReadAssembler;
import com.openhis.health.module.querysupport.pharmacy.dto.InpatientPharmacyDispenseRecordQueryRowDto;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.core.application.util.AnnotatedExcelExporter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 住院发退药记录应用服务（AS-IP-PHA-004，IF-IPR-01～03）。
 *
 * <p>职责边界：纯读编排；住院待结算 charge 经 {@link InpatientMedicationDispenseChargePricingLoader} 合并。</p>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} →
 * {@code POST /api/app/inpatientPharmacyDispenseRecord/{methodName}}；不另写 RestController。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InpatientPharmacyDispenseRecordAppService {

    private final CurrentOperatorResolver currentOperatorResolver;
    private final PageWindowGate pageWindowGate;
    private final LocationQueryService locationQueryService;
    private final TerminologyOptionLoader terminologyOptionLoader;
    private final PractitionerQueryService practitionerQueryService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final PharmacyDispenseRecordQuerySupport pharmacyDispenseRecordQuerySupport;
    private final MedicationRequestQueryService medicationRequestQueryService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final InpatientMedicationDispenseChargePricingLoader inpatientMedicationDispenseChargePricingLoader;
    private final EncounterContextFacade encounterContextFacade;
    private final OrganizationQueryService organizationQueryService;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    private final PharmacyDispenseRecordReadAssembler pharmacyDispenseRecordReadAssembler;
    private final PharmacyDispenseRecordChargePricingSupport pharmacyDispenseRecordChargePricingSupport;
    private final InpatientPharmacyDispenseRecordMapper inpatientPharmacyDispenseRecordMapper;

    /**
     * IF-IPR-01：住院发退药记录页初始化。
     *
     * @param request 占位请求
     * @return 筛选项与默认发药日
     * @throws BusinessException EX-IPR-101
     */
    public LoadInpatientPharmacyDispenseRecordPageInitResponse loadInpatientPharmacyDispenseRecordPageInit(
        LoadInpatientPharmacyDispenseRecordPageInitRequest request) {
        // Gate：须已登录方可加载报表页元数据
        this.currentOperatorResolver.requireOperatorId(
            InpatientPharmacyDispenseRecordConstants.EX_IPR_101,
            "页面初始化失败，请稍后重试");
        try {
            // Pre：租户下全部启用药房与筛选项下拉
            List<Location> pharmacies = this.locationQueryService.listAllActivePharmacies();
            List<OptionDto> categoryOptions =
                this.terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.MEDICATION_CATEGORY_CODE);
            Page<Practitioner> dispenserPage = this.practitionerQueryService.pageActivePractitionersForPosition(
                null,
                null,
                PageRequest.of(0, PharmacyDispenseRecordConstants.DISPENSER_OPTION_PAGE_SIZE),
                false);
            List<OptionDto> dispenserOptions = new ArrayList<>();
            for (Practitioner practitioner : dispenserPage.getContent()) {
                if (practitioner == null || practitioner.getId() == null) {
                    continue;
                }
                OptionDto option = new OptionDto();
                option.setValue(String.valueOf(practitioner.getId()));
                option.setLabel(practitioner.getName());
                dispenserOptions.add(option);
            }
            List<OptionDto> recordTypeOptions = this.inpatientPharmacyDispenseRecordMapper.buildRecordTypeOptions();
            LocalDate today = LocalDate.now();
            String defaultFrom = today.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
            String defaultTo = today.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
            // Asm：装配初始化响应
            return this.inpatientPharmacyDispenseRecordMapper.toLoadPageInitResponse(
                pharmacies,
                categoryOptions,
                dispenserOptions,
                recordTypeOptions,
                defaultFrom,
                defaultTo);
        } catch (RuntimeException ex) {
            log.warn("住院发退药记录页初始化失败", ex);
            throw new BusinessException(
                InpatientPharmacyDispenseRecordConstants.EX_IPR_101,
                "页面初始化失败，请稍后重试");
        }
    }

    /**
     * IF-IPR-02：分页查询住院发退药记录。
     *
     * @param request 筛选与分页参数
     * @return 平铺分页明细
     * @throws BusinessException EX-IPR-102 / EX-IPR-103
     */
    public PageInpatientPharmacyDispenseRecordsResponse pageInpatientPharmacyDispenseRecords(
        PageInpatientPharmacyDispenseRecordsRequest request) {
        // Gate：登录、分页与时间窗
        this.currentOperatorResolver.requireOperatorId(
            InpatientPharmacyDispenseRecordConstants.EX_IPR_103,
            "查询失败，请稍后重试");
        this.pageWindowGate.requirePositivePageWindow(
            request,
            InpatientPharmacyDispenseRecordConstants.EX_IPR_103,
            "分页参数无效");
        this.validateDispenseDateWindow(request.getDispenseDateFrom(), request.getDispenseDateTo());
        try {
            MedicationDispenseRecordPageQuery query =
                this.inpatientPharmacyDispenseRecordMapper.toMedicationDispenseRecordPageQuery(request);
            this.pharmacyDispenseRecordQuerySupport.enrichInpatientRecordFilters(
                query,
                request.getMedicationCategoryCodes(),
                request.getDrugKeyword(),
                request.getPatientKeyword());
            Page<MedicationDispenseSlipQueryModel> slipPage =
                this.medicationDispenseQueryService.pageMedicationDispenseRecords(query);
            List<InpatientPharmacyDispenseRecordRowDto> rows = this.assembleInpatientRows(slipPage.getContent());
            return this.inpatientPharmacyDispenseRecordMapper.toPageResponse(slipPage, rows);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("住院发退药记录分页查询失败", ex);
            throw new BusinessException(
                InpatientPharmacyDispenseRecordConstants.EX_IPR_103,
                "查询失败，请稍后重试");
        }
    }

    /**
     * IF-IPR-03：导出住院发退药记录。
     *
     * @param request 筛选条件
     * @return xlsx 字节流
     * @throws BusinessException EX-IPR-102 / EX-IPR-104
     */
    public ExportInpatientPharmacyDispenseRecordsResponse exportInpatientPharmacyDispenseRecords(
        ExportInpatientPharmacyDispenseRecordsRequest request) {
        // Gate：登录与时间窗（与列表同源）
        this.currentOperatorResolver.requireOperatorId(
            InpatientPharmacyDispenseRecordConstants.EX_IPR_103,
            "查询失败，请稍后重试");
        this.validateDispenseDateWindow(request.getDispenseDateFrom(), request.getDispenseDateTo());
        try {
            // Pre：映射领域查询并预解析筛选（与列表同源）
            MedicationDispenseRecordPageQuery query =
                this.inpatientPharmacyDispenseRecordMapper.toMedicationDispenseRecordPageQuery(request);
            this.pharmacyDispenseRecordQuerySupport.enrichInpatientRecordFilters(
                query,
                request.getMedicationCategoryCodes(),
                request.getDrugKeyword(),
                request.getPatientKeyword());
            // Pre：统计命中总数以判定是否触达导出行上限
            query.setPageSize(1);
            Page<MedicationDispenseSlipQueryModel> totalProbe =
                this.medicationDispenseQueryService.pageMedicationDispenseRecords(query);
            boolean truncated =
                totalProbe.getTotalElements() > PharmacyDispenseRecordExportConstants.EXPORT_MAX_ROWS;
            query.setPageSize(PharmacyDispenseRecordExportConstants.EXPORT_MAX_ROWS);
            // Pre：全量列表读（行数上限 EXPORT_MAX_ROWS，由领域 list 下推 LIMIT）
            List<MedicationDispenseSlipQueryModel> slipRows =
                this.medicationDispenseQueryService.listMedicationDispenseRecords(query);
            List<InpatientPharmacyDispenseRecordRowDto> rows = this.assembleInpatientRows(slipRows);
            List<InpatientPharmacyDispenseRecordExportRowDto> exportRows = new ArrayList<>(rows.size());
            for (InpatientPharmacyDispenseRecordRowDto row : rows) {
                exportRows.add(this.inpatientPharmacyDispenseRecordMapper.toExportRowDto(row));
            }
            // Asm：生成 xlsx 字节流
            byte[] body = AnnotatedExcelExporter.exportToXlsxBytes(
                PharmacyDispenseRecordExportConstants.INPATIENT_SHEET_NAME,
                exportRows,
                InpatientPharmacyDispenseRecordExportRowDto.class);
            return this.inpatientPharmacyDispenseRecordMapper.toExportResponse(body, exportRows.size(), truncated);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("住院发退药记录导出失败", ex);
            throw new BusinessException(
                InpatientPharmacyDispenseRecordConstants.EX_IPR_104,
                "导出失败，请稍后重试");
        }
    }

    /**
     * Gate：发药日闭区间合法且起止不颠倒。
     *
     * @param dispenseDateFrom 发药日起（yyyy-MM-dd）
     * @param dispenseDateTo   发药日止（yyyy-MM-dd）
     */
    private void validateDispenseDateWindow(String dispenseDateFrom, String dispenseDateTo) {
        if (StringUtils.isBlank(dispenseDateFrom) || StringUtils.isBlank(dispenseDateTo)) {
            throw new BusinessException(
                InpatientPharmacyDispenseRecordConstants.EX_IPR_102,
                "请选择有效的发药时间范围");
        }
        LocalDate from = LocalDate.parse(dispenseDateFrom.trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
        LocalDate to = LocalDate.parse(dispenseDateTo.trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
        if (from.isAfter(to)) {
            throw new BusinessException(
                InpatientPharmacyDispenseRecordConstants.EX_IPR_102,
                "请选择有效的发药时间范围");
        }
    }

    /**
     * Pre+Asm：批量读侧预热后装配住院发退药记录平铺行（含待结算 charge 合并）。
     *
     * @param slipRows 发放事实读模型列表
     * @return 应用层列表行；无数据时返回空列表
     */
    private List<InpatientPharmacyDispenseRecordRowDto> assembleInpatientRows(
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
            : this.encounterContextFacade.listInpatientContextsByEncounterIdsOrdered(
                new ArrayList<>(encounterIds))) {
            if (context != null && context.getEncounterId() != null) {
                inpatientContextByEncounterId.put(context.getEncounterId(), context);
            }
        }
        Map<Long, BigDecimal> chargeUnitPriceByDispenseId = new HashMap<>();
        Map<Long, BigDecimal> chargeTotalPriceByDispenseId = new HashMap<>();
        this.mergeInpatientChargePricingByEncounterGroups(
            slipRows, chargeUnitPriceByDispenseId, chargeTotalPriceByDispenseId);
        this.pharmacyDispenseRecordChargePricingSupport.supplementRefundPricingFromOriginMedicationRequests(
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
        List<InpatientPharmacyDispenseRecordQueryRowDto> queryRows =
            this.pharmacyDispenseRecordReadAssembler.assembleInpatientRows(
                slipRows,
                medicationRequestByMedReqId,
                productByMedicationId,
                inpatientContextByEncounterId,
                chargeUnitPriceByDispenseId,
                chargeTotalPriceByDispenseId,
                orderingDepartmentNameByOrgId,
                dispenserNameByPractitionerId);
        List<InpatientPharmacyDispenseRecordRowDto> rows = new ArrayList<>(queryRows.size());
        for (InpatientPharmacyDispenseRecordQueryRowDto queryRow : queryRows) {
            rows.add(this.inpatientPharmacyDispenseRecordMapper.toRowDto(queryRow));
        }
        return rows;
    }

    /**
     * Pre：按发放行批量加载药品展示 DTO。
     *
     * @param slipRows 发放事实读模型列表
     * @return 药品定义主键 → 展示 DTO
     */
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
        Map<String, ProductDefinitionDisplayDto> productByKey = this.productDefinitionBatchLoader.loadAsMap(lookups);
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId = new HashMap<>();
        for (MedicationDispenseSlipQueryModel slip : slipRows) {
            if (slip == null || slip.getMedicationId() == null) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.MED_MEDICATION_DEFINITION, slip.getMedicationId());
            ProductDefinitionDisplayDto product = productByKey.get(key);
            if (product != null) {
                productByMedicationId.put(slip.getMedicationId(), product);
            }
        }
        return productByMedicationId;
    }

    /**
     * Pre：按就诊分组读 charge 锚点并合并住院待结算定价。
     *
     * @param slipRows       发放事实读模型列表
     * @param unitPriceOut   单价输出 Map
     * @param totalPriceOut  金额输出 Map
     */
    private void mergeInpatientChargePricingByEncounterGroups(
        List<MedicationDispenseSlipQueryModel> slipRows,
        Map<Long, BigDecimal> unitPriceOut,
        Map<Long, BigDecimal> totalPriceOut) {
        Map<Long, List<Long>> dispenseIdsByEncounterId = new HashMap<>();
        for (MedicationDispenseSlipQueryModel slip : slipRows) {
            if (slip == null || slip.getDispenseId() == null || slip.getEncounterId() == null) {
                continue;
            }
            dispenseIdsByEncounterId
                .computeIfAbsent(slip.getEncounterId(), ignored -> new ArrayList<>())
                .add(slip.getDispenseId());
        }
        for (Map.Entry<Long, List<Long>> entry : dispenseIdsByEncounterId.entrySet()) {
            Long encounterId = entry.getKey();
            List<Long> dispenseIds = entry.getValue();
            List<ChargeItem> chargeRows =
                this.chargeItemQueryService.listChargeItemsForMedicationDispensePricing(encounterId, dispenseIds);
            this.mergeChargePricingFromChargeItems(chargeRows, unitPriceOut, totalPriceOut);
            // 补充在院待结算 charge（与 slip 读侧同口径）
            this.inpatientMedicationDispenseChargePricingLoader.mergePendingSettlementPricingMaps(
                encounterId, dispenseIds, unitPriceOut, totalPriceOut);
        }
    }

    /**
     * 将 charge 行聚合为发放维度的单价与金额（同 dispense 多行 charge 累加）。
     *
     * @param chargeRows     charge 明细列表
     * @param unitPriceOut   单价输出 Map
     * @param totalPriceOut  金额输出 Map
     */
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
                unitPriceOut.put(dispenseKey, totalVal.divide(qtyVal, 4, RoundingMode.HALF_UP));
            }
        }
    }
}
