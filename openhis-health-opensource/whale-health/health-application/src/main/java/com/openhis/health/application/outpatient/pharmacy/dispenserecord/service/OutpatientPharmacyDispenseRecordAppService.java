/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.dispenserecord.service;

import com.openhis.health.application.outpatient.pharmacy.dispenserecord.dto.PharmacyDispenseRecordExportRowDto;
import com.openhis.health.application.outpatient.pharmacy.dispenserecord.dto.PharmacyDispenseRecordRowDto;
import com.openhis.health.application.outpatient.pharmacy.dispenserecord.mapper.OutpatientPharmacyDispenseRecordMapper;
import com.openhis.health.application.outpatient.pharmacy.dispenserecord.request.ExportOutpatientPharmacyDispenseRecordsRequest;
import com.openhis.health.application.outpatient.pharmacy.dispenserecord.request.LoadOutpatientPharmacyDispenseRecordPageInitRequest;
import com.openhis.health.application.outpatient.pharmacy.dispenserecord.request.PageOutpatientPharmacyDispenseRecordsRequest;
import com.openhis.health.application.outpatient.pharmacy.dispenserecord.response.ExportOutpatientPharmacyDispenseRecordsResponse;
import com.openhis.health.application.outpatient.pharmacy.dispenserecord.response.LoadOutpatientPharmacyDispenseRecordPageInitResponse;
import com.openhis.health.application.outpatient.pharmacy.dispenserecord.response.PageOutpatientPharmacyDispenseRecordsResponse;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
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
import com.openhis.health.domain.share.constants.OutpatientPharmacyDispenseRecordConstants;
import com.openhis.health.domain.share.constants.PharmacyDispenseRecordConstants;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.share.constants.PharmacyDispenseRecordExportConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.application.shared.pharmacy.PharmacyDispenseRecordChargePricingAssembler;
import com.openhis.health.application.outpatient.pharmacy.dispenserecord.support.PharmacyDispenseRecordLoader;
import com.openhis.health.application.outpatient.pharmacy.dispenserecord.support.PharmacyDispenseRecordReadAssembler;
import com.openhis.health.application.outpatient.pharmacy.dispenserecord.dto.PharmacyDispenseRecordQueryRowDto;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.core.application.util.AnnotatedExcelExporter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 门诊发退药记录应用服务（AS-OP-PHA-004，IF-OPR-01～03）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>Gate：登录、时间窗、分页窗口。</li>
 *   <li>Pre：跨聚合预解析与批量读装配。</li>
 *   <li>Asm：Mapper + ReadAssembler 装配响应。</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} →
 * {@code POST /api/app/outpatientPharmacyDispenseRecord/{methodName}}；不另写 RestController。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutpatientPharmacyDispenseRecordAppService {

    /** 写/读路径登录门禁。 */
    private final CurrentOperatorResolver currentOperatorResolver;

    /** 分页窗口 Gate。 */
    private final PageWindowGate pageWindowGate;

    /** 启用药房读侧。 */
    private final LocationQueryService locationQueryService;

    /** 术语下拉加载。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /** 发药人主档读侧。 */
    private final PractitionerQueryService practitionerQueryService;

    /** 发放事实读侧。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /** 记录筛选跨聚合预解析。 */
    private final PharmacyDispenseRecordLoader pharmacyDispenseRecordLoader;

    /** 药嘱批量读侧。 */
    private final MedicationRequestQueryService medicationRequestQueryService;

    /** 收费明细锚点读价。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 门诊就诊上下文。 */
    private final EncounterContextReader EncounterContextReader;

    /** 开单科室名称。 */
    private final OrganizationQueryService organizationQueryService;

    /** 药品展示批量加载。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /** 平铺行装配。 */
    private final PharmacyDispenseRecordReadAssembler pharmacyDispenseRecordReadAssembler;

    /** 退药行原单 charge 定价补充。 */
    private final PharmacyDispenseRecordChargePricingAssembler pharmacyDispenseRecordChargePricingAssembler;

    /** Request/Response 映射。 */
    private final OutpatientPharmacyDispenseRecordMapper outpatientPharmacyDispenseRecordMapper;

    /**
     * IF-OPR-01：门诊发退药记录页初始化。
     *
     * @param request 占位请求
     * @return 筛选项与默认发药日
     * @throws BusinessException EX-OPR-101 初始化失败或未登录
     */
    public LoadOutpatientPharmacyDispenseRecordPageInitResponse loadOutpatientPharmacyDispenseRecordPageInit(
        LoadOutpatientPharmacyDispenseRecordPageInitRequest request) {
        // Gate：须已登录方可加载报表页元数据
        this.currentOperatorResolver.requireOperatorId(
            OutpatientPharmacyDispenseRecordConstants.EX_OPR_101,
            "页面初始化失败，请稍后重试");
        try {
            // Pre：租户下全部启用药房（非管辖库房收窄）
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
            List<OptionDto> recordTypeOptions = this.outpatientPharmacyDispenseRecordMapper.buildRecordTypeOptions();
            LocalDate today = LocalDate.now();
            String defaultFrom = today.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
            String defaultTo = today.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
            // Asm：装配初始化响应
            return this.outpatientPharmacyDispenseRecordMapper.toLoadPageInitResponse(
                pharmacies,
                categoryOptions,
                dispenserOptions,
                recordTypeOptions,
                defaultFrom,
                defaultTo);
        } catch (RuntimeException ex) {
            log.warn("门诊发退药记录页初始化失败", ex);
            throw new BusinessException(
                OutpatientPharmacyDispenseRecordConstants.EX_OPR_101,
                "页面初始化失败，请稍后重试");
        }
    }

    /**
     * IF-OPR-02：分页查询门诊发退药记录。
     *
     * @param request 筛选与分页参数
     * @return 平铺分页明细
     * @throws BusinessException EX-OPR-102 时间窗非法；EX-OPR-103 查询失败
     */
    public PageOutpatientPharmacyDispenseRecordsResponse pageOutpatientPharmacyDispenseRecords(
        PageOutpatientPharmacyDispenseRecordsRequest request) {
        // Gate：登录与分页窗口、发药时间窗合法
        this.currentOperatorResolver.requireOperatorId(
            OutpatientPharmacyDispenseRecordConstants.EX_OPR_103,
            "查询失败，请稍后重试");
        this.pageWindowGate.requirePositivePageWindow(
            request,
            OutpatientPharmacyDispenseRecordConstants.EX_OPR_103,
            "分页参数无效");
        this.validateDispenseDateWindow(request.getDispenseDateFrom(), request.getDispenseDateTo());
        try {
            // Pre：映射领域查询并预解析筛选
            MedicationDispenseRecordPageQuery query =
                this.outpatientPharmacyDispenseRecordMapper.toMedicationDispenseRecordPageQuery(request);
            this.pharmacyDispenseRecordLoader.enrichOutpatientRecordFilters(
                query,
                request.getMedicationCategoryCodes(),
                request.getDrugKeyword(),
                request.getPatientKeyword());
            // Pre：分页查询发放事实
            Page<MedicationDispenseSlipQueryModel> slipPage =
                this.medicationDispenseQueryService.pageMedicationDispenseRecords(query);
            List<PharmacyDispenseRecordRowDto> rows = this.assembleOutpatientRows(slipPage.getContent());
            // Asm：装配分页响应
            return this.outpatientPharmacyDispenseRecordMapper.toPageResponse(slipPage, rows);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("门诊发退药记录分页查询失败", ex);
            throw new BusinessException(
                OutpatientPharmacyDispenseRecordConstants.EX_OPR_103,
                "查询失败，请稍后重试");
        }
    }

    /**
     * IF-OPR-03：导出门诊发退药记录。
     *
     * @param request 与列表一致的筛选条件
     * @return xlsx 字节流与导出行数
     * @throws BusinessException EX-OPR-102 / EX-OPR-104
     */
    public ExportOutpatientPharmacyDispenseRecordsResponse exportOutpatientPharmacyDispenseRecords(
        ExportOutpatientPharmacyDispenseRecordsRequest request) {
        // Gate：登录与发药时间窗合法（与列表同源）
        this.currentOperatorResolver.requireOperatorId(
            OutpatientPharmacyDispenseRecordConstants.EX_OPR_103,
            "查询失败，请稍后重试");
        this.validateDispenseDateWindow(request.getDispenseDateFrom(), request.getDispenseDateTo());
        try {
            // Pre：映射领域查询并预解析筛选（与列表同源）
            MedicationDispenseRecordPageQuery query =
                this.outpatientPharmacyDispenseRecordMapper.toMedicationDispenseRecordPageQuery(request);
            this.pharmacyDispenseRecordLoader.enrichOutpatientRecordFilters(
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
            List<PharmacyDispenseRecordRowDto> rows = this.assembleOutpatientRows(slipRows);
            List<PharmacyDispenseRecordExportRowDto> exportRows = new ArrayList<>(rows.size());
            for (PharmacyDispenseRecordRowDto row : rows) {
                exportRows.add(this.outpatientPharmacyDispenseRecordMapper.toExportRowDto(row));
            }
            // Asm：生成 xlsx 字节流
            byte[] body = AnnotatedExcelExporter.exportToXlsxBytes(
                PharmacyDispenseRecordExportConstants.OUTPATIENT_SHEET_NAME,
                exportRows,
                PharmacyDispenseRecordExportRowDto.class,
                request.getExportColumns());
            // Asm：装配导出响应（零行仍导出列头）
            return this.outpatientPharmacyDispenseRecordMapper.toExportResponse(body, exportRows.size(), truncated);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("门诊发退药记录导出失败", ex);
            throw new BusinessException(
                OutpatientPharmacyDispenseRecordConstants.EX_OPR_104,
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
        if (StringUtils.isEmpty(dispenseDateFrom) || StringUtils.isEmpty(dispenseDateTo)) {
            throw new BusinessException(
                OutpatientPharmacyDispenseRecordConstants.EX_OPR_102,
                "请选择有效的发药时间范围");
        }
        LocalDate from = LocalDate.parse(dispenseDateFrom.trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
        LocalDate to = LocalDate.parse(dispenseDateTo.trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
        if (from.isAfter(to)) {
            // Gate：结束日早于起始日时拒绝查询，避免空结果误导用户
            throw new BusinessException(
                OutpatientPharmacyDispenseRecordConstants.EX_OPR_102,
                "请选择有效的发药时间范围");
        }
    }

    /**
     * Pre+Asm：批量读侧预热后装配门诊发退药记录平铺行。
     *
     * @param slipRows 发放事实读模型列表
     * @return 应用层列表行；无数据时返回空列表
     */
    private List<PharmacyDispenseRecordRowDto> assembleOutpatientRows(List<MedicationDispenseSlipQueryModel> slipRows) {
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
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId =
            this.loadProductByMedicationId(slipRows);
        Map<Long, OutpatientEncounterContextQueryModel> outpatientContextByEncounterId = new HashMap<>();
        for (OutpatientEncounterContextQueryModel context
            : this.EncounterContextReader.listOutpatientContextsByEncounterIdsOrdered(
                new ArrayList<>(encounterIds))) {
            if (context != null && context.getEncounterId() != null) {
                outpatientContextByEncounterId.put(context.getEncounterId(), context);
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
        List<PharmacyDispenseRecordQueryRowDto> queryRows = this.pharmacyDispenseRecordReadAssembler.assembleOutpatientRows(
            slipRows,
            medicationRequestByMedReqId,
            productByMedicationId,
            outpatientContextByEncounterId,
            chargeUnitPriceByDispenseId,
            chargeTotalPriceByDispenseId,
            orderingDepartmentNameByOrgId,
            dispenserNameByPractitionerId);
        List<PharmacyDispenseRecordRowDto> rows = new ArrayList<>(queryRows.size());
        for (PharmacyDispenseRecordQueryRowDto queryRow : queryRows) {
            rows.add(this.outpatientPharmacyDispenseRecordMapper.toRowDto(queryRow));
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
        Map<String, ProductDefinitionDisplayDto> productByKey = this.productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
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
     * Pre：按就诊分组批量读 charge 锚点并合并单价/金额 Map。
     *
     * @param slipRows       发放事实读模型列表
     * @param unitPriceOut   单价输出 Map（dispenseId → 单价）
     * @param totalPriceOut  金额输出 Map（dispenseId → 金额）
     */
    private void mergeChargePricingByEncounterGroups(
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
            List<ChargeItem> chargeRows = this.chargeItemQueryService.listChargeItemsForMedicationDispensePricing(
                entry.getKey(), entry.getValue());
            this.mergeChargePricingFromChargeItems(chargeRows, unitPriceOut, totalPriceOut);
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
                unitPriceOut.put(dispenseKey, MonetaryAmountSupport.unitPriceFromLineTotal(totalVal, qtyVal));
            }
        }
    }
}
