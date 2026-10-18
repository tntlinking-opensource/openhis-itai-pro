/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.pharmacy.summarydispense.assembler;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.application.shared.product.MedicationPackageSplitQuantityDisplayAssembler;
import com.zxplat.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.zxplat.application.inpatient.pharmacy.summarydispense.dto.InpatientPharmacySummaryDispenseItemDto;
import com.zxplat.application.inpatient.pharmacy.summarydispense.dto.InpatientPharmacySummarySlipLineDto;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.medication.medicationdispense.queries.model.MedicationDispenseSummaryItemQueryModel;
import com.zxplat.domain.medication.medicationdispense.queries.model.SummaryDispenseGateQueryModel;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.DispenseStatus;
import com.zxplat.domain.share.enums.SupplyStatus;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.domain.workflow.supplyrequest.queries.model.MedicationSummarySlipLineQueryModel;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 住院药房汇总发药详情装配（IF-IPS-02 Asm）。
 *
 * <p>合并 Supply 行禁止输出批号；发放行须含批号/扣库字段与患者锚点展示。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientPharmacySummaryDispenseAssembler {

    /**
     * 域合并 Supply 行 → 展示 DTO（禁止输出 lotNumber）。
     *
     * <p>行序：按药品目录拼音 {@code pyStr} 首字母序（与护士站 IF-IP-SUM-03 / BR-IP-SUM-020 同源）。</p>
     *
     * @param supplyLines          {@link com.zxplat.domain.workflow.supplyrequest.service.SupplyRequestQueryService#listMedicationSummarySlipLinesByBusNo} 结果
     * @param productByKey         药品目录读模型索引
     * @param purposeLocationName  汇总单头发放药房展示名
     * @param traceBySupplyRequestId  供应行 id → 追溯码（已核发读 SupplyDelivery）
     * @return 已按拼音排序的合并明细行列表
     */
    public List<InpatientPharmacySummarySlipLineDto> buildMergedLineDtos(
        List<MedicationSummarySlipLineQueryModel> supplyLines,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        String purposeLocationName,
        Map<Long, String> traceBySupplyRequestId) {
        List<InpatientPharmacySummarySlipLineDto> lines = new ArrayList<>();
        if (supplyLines == null) {
            return lines;
        }
        Map<Long, String> pySortKeyByItemId = new HashMap<>();
        for (MedicationSummarySlipLineQueryModel row : supplyLines) {
            if (row == null) {
                continue;
            }
            InpatientPharmacySummarySlipLineDto dto = new InpatientPharmacySummarySlipLineDto();
            dto.setSupplyRequestId(row.getSupplyRequestId());
            dto.setItemId(row.getItemId());
            dto.setItemQuantity(row.getItemQuantity());
            dto.setUnitCode(row.getUnitCode());
            dto.setUnitPrice(row.getUnitPrice());
            dto.setTotalPrice(row.getTotalPrice());
            dto.setPurposeLocationName(trimToNull(purposeLocationName));
            if (traceBySupplyRequestId != null && row.getSupplyRequestId() != null) {
                dto.setTraceNo(trimToNull(traceBySupplyRequestId.get(row.getSupplyRequestId())));
            }
            String productKey = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.MED_MEDICATION_DEFINITION, row.getItemId());
            ProductDefinitionDisplayDto product = productByKey.get(productKey);
            if (product != null) {
                dto.setMedicationName(StringUtils.firstNonBlank(product.getProductName(), product.getMerchandiseName()));
                dto.setSpecificationText(this.resolveSpecificationText(product));
                dto.setUnitCodeText(ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                    null, row.getUnitCode(), product));
                dto.setManufacturerName(trimToNull(product.getManufacturer()));
                // 输液拆零；非输液整包装凑整（BR-IP-SUM-015）；金额仍用 totalPrice 只读累加
                dto.setDisplayQuantityText(MedicationPackageSplitQuantityDisplayAssembler.buildSummaryQuantityText(
                    row.getItemQuantity(), product, dto.getUnitCodeText()));
                // 包装/拆零：汇总待扫换算（与患者发药同口径）
                dto.setPackageUnitCode(product.getPackageUnitCode());
                dto.setPackageUnitRatio(product.getPackageUnitRatio());
                dto.setSplitUnitCode(product.getSplitUnitCode());
                // 药典贴码单位/比例：主档无快照时回落；有主档时由 enricher 覆盖
                if (StringUtils.isNotEmpty(product.getTraceUnitCode())) {
                    dto.setTraceUnitCode(product.getTraceUnitCode());
                    dto.setTraceUnitRatio(product.getTraceUnitRatio());
                    dto.setTraceUnitCodeText(product.getTraceUnitCodeDictText());
                }
                pySortKeyByItemId.put(row.getItemId(), this.resolveMedicationPySortKey(product));
            } else {
                pySortKeyByItemId.put(row.getItemId(), "");
            }
            lines.add(dto);
        }
        // 拼音首字母序；缺拼音排后；同拼音按药品名、itemId 稳定
        lines.sort(Comparator
            .comparing((InpatientPharmacySummarySlipLineDto line) ->
                    this.resolveLinePySortKey(line, pySortKeyByItemId),
                Comparator.nullsLast(String::compareToIgnoreCase))
            .thenComparing(InpatientPharmacySummarySlipLineDto::getMedicationName,
                Comparator.nullsLast(String::compareToIgnoreCase))
            .thenComparing(InpatientPharmacySummarySlipLineDto::getItemId,
                Comparator.nullsLast(Long::compareTo)));
        return lines;
    }

    /**
     * 药品拼音排序键：目录 {@code pyStr} 大写；空则空串。
     *
     * @param product 药品目录投影
     * @return 非 null 排序键
     */
    private String resolveMedicationPySortKey(ProductDefinitionDisplayDto product) {
        if (product == null || StringUtils.isEmpty(product.getPyStr())) {
            return "";
        }
        return product.getPyStr().trim().toUpperCase();
    }

    /**
     * 明细行拼音排序键（无 pyStr 时用「~」前缀药名，保证有拼音的行排前）。
     *
     * @param line               明细行
     * @param pySortKeyByItemId  itemId → pyStr 键
     * @return 排序键
     */
    private String resolveLinePySortKey(
        InpatientPharmacySummarySlipLineDto line,
        Map<Long, String> pySortKeyByItemId) {
        if (line == null) {
            return "~";
        }
        String pyKey = null;
        if (line.getItemId() != null && pySortKeyByItemId != null) {
            pyKey = pySortKeyByItemId.get(line.getItemId());
        }
        if (StringUtils.isNotEmpty(pyKey)) {
            return pyKey;
        }
        String medicationName = line.getMedicationName();
        if (StringUtils.isEmpty(medicationName)) {
            return "~";
        }
        return "~" + medicationName.trim();
    }

    /**
     * 域发放行 → 展示 DTO（含批号与患者/床号；汇总路径追溯码不落发放行）。
     *
     * @param dispenseRows             汇总单号下发放行 QueryModel
     * @param productByKey             药品目录读模型索引
     * @param patientNameById          患者 id → 姓名
     * @param bedDisplayByEncounterId  就诊 id → 床号展示
     * @param locationNameById         库位 id → 名称
     * @return 发放行明细列表
     */
    public List<InpatientPharmacySummaryDispenseItemDto> buildDispenseItemDtos(
        List<MedicationDispenseSummaryItemQueryModel> dispenseRows,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<Long, String> patientNameById,
        Map<Long, String> bedDisplayByEncounterId,
        Map<Long, String> locationNameById) {
        List<InpatientPharmacySummaryDispenseItemDto> items = new ArrayList<>();
        if (dispenseRows == null) {
            return items;
        }
        for (MedicationDispenseSummaryItemQueryModel row : dispenseRows) {
            if (row == null) {
                continue;
            }
            InpatientPharmacySummaryDispenseItemDto dto = new InpatientPharmacySummaryDispenseItemDto();
            dto.setDispenseId(row.getId());
            if (row.getStatusEnum() != null) {
                dto.setStatusEnum(row.getStatusEnum().getCode());
                dto.setStatusEnumText(row.getStatusEnum().getDisplay());
            }
            dto.setMedicationId(row.getMedicationId());
            dto.setLocationId(row.getLocationId());
            dto.setLotNumber(row.getLotNumber());
            dto.setQuantity(row.getQuantity());
            dto.setUnitCode(row.getUnitCode());
            dto.setEncounterId(row.getEncounterId());
            dto.setPatientId(row.getPatientId());
            if (row.getPatientId() != null && patientNameById != null) {
                dto.setPatientName(patientNameById.get(row.getPatientId()));
            }
            if (row.getEncounterId() != null && bedDisplayByEncounterId != null) {
                dto.setBedDisplay(bedDisplayByEncounterId.get(row.getEncounterId()));
            }
            if (row.getLocationId() != null && locationNameById != null) {
                dto.setLocationName(locationNameById.get(row.getLocationId()));
            }
            String productKey = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.MED_MEDICATION_DEFINITION, row.getMedicationId());
            ProductDefinitionDisplayDto product = productByKey.get(productKey);
            if (product != null) {
                dto.setMedicationName(StringUtils.firstNonBlank(product.getProductName(), product.getMerchandiseName()));
                dto.setSpecificationText(this.resolveSpecificationText(product));
                dto.setUnitCodeText(ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                    null, row.getUnitCode(), product));
            }
            items.add(dto);
        }
        return items;
    }

    /**
     * 汇总发药门控展示：已发药仅以发放表 COMPLETED 为准。
     *
     * @param gate 汇总单号门控读模型
     * @return 待核对 / 已发药；态不一致时 null
     */
    public String resolveSummaryDispenseGateDisplayText(SummaryDispenseGateQueryModel gate) {
        if (gate == null) {
            return null;
        }
        if (gate.isAllCompleted()) {
            return "已发药";
        }
        if (gate.isAllSummarized()) {
            return "待核对";
        }
        // 同号发放行态不一致：不输出误导性文案
        return null;
    }

    /**
     * 单头审批态展示（EnumUtil → getInfo）。
     *
     * @param supplyStatusEnum 持久化 code
     * @return 展示文案
     */
    public String resolveSupplyStatusText(String supplyStatusEnum) {
        if (StringUtils.isEmpty(supplyStatusEnum)) {
            return null;
        }
        SupplyStatus status =
            EnumUtil.fromCode(SupplyStatus.class, supplyStatusEnum.trim());
        if (status == null) {
            return null;
        }
        return status.getDisplay();
    }

    /**
     * 发放状态展示。
     *
     * @param statusEnum 持久化 code
     * @return 展示文案
     */
    public String resolveDispenseStatusText(String statusEnum) {
        if (StringUtils.isEmpty(statusEnum)) {
            return null;
        }
        DispenseStatus status = EnumUtil.fromCode(DispenseStatus.class, statusEnum.trim());
        if (status == null) {
            return null;
        }
        return status.getDisplay();
    }

    /**
     * 从产品读模型解析规格展示串。
     *
     * @param product 药品目录读模型
     * @return 规格展示；无产品时为 null
     */
    private String resolveSpecificationText(ProductDefinitionDisplayDto product) {
        if (product == null) {
            return null;
        }
        return StringUtils.firstNonBlank(product.getTotalVolume(), product.getMerchandiseName());
    }
}
