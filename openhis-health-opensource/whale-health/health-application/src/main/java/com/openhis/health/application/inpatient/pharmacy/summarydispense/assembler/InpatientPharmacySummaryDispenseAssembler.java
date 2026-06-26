/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.inpatient.pharmacy.summarydispense.assembler;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.module.common.assembler.product.MedicationPackageSplitQuantityDisplaySupport;
import com.openhis.health.module.common.assembler.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.application.inpatient.pharmacy.summarydispense.dto.InpatientPharmacySummaryDispenseItemDto;
import com.openhis.health.application.inpatient.pharmacy.summarydispense.dto.InpatientPharmacySummarySlipLineDto;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseSummaryItemQueryModel;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.MedicationSummarySlipLineQueryModel;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
     * @param supplyLines          {@link com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService#listMedicationSummarySlipLinesByBusNo} 结果
     * @param productByKey         药品目录读模型索引
     * @param purposeLocationName  汇总单头发放药房展示名
     * @return 合并明细行列表
     */
    public List<InpatientPharmacySummarySlipLineDto> buildMergedLineDtos(
        List<MedicationSummarySlipLineQueryModel> supplyLines,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        String purposeLocationName) {
        List<InpatientPharmacySummarySlipLineDto> lines = new ArrayList<>();
        if (supplyLines == null) {
            return lines;
        }
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
            String productKey = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.MED_MEDICATION_DEFINITION, row.getItemId());
            ProductDefinitionDisplayDto product = productByKey.get(productKey);
            if (product != null) {
                dto.setMedicationName(StringUtils.firstNonBlank(product.getProductName(), product.getMerchandiseName()));
                dto.setSpecificationText(this.resolveSpecificationText(product));
                dto.setUnitCodeText(ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                    null, row.getUnitCode(), product));
                dto.setManufacturerName(trimToNull(product.getManufacturer()));
                dto.setDisplayQuantityText(MedicationPackageSplitQuantityDisplaySupport.buildDisplayQuantityText(
                    row.getItemQuantity(), product));
            }
            lines.add(dto);
        }
        return lines;
    }

    /**
     * 域发放行 → 展示 DTO（含批号、追溯与患者/床号）。
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
            dto.setTraceNo(row.getTraceNo());
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
     * 单头审批态展示（EnumUtil → getInfo）。
     *
     * @param supplyStatusEnum 持久化 code
     * @return 展示文案
     */
    public String resolveSupplyStatusText(String supplyStatusEnum) {
        if (StringUtils.isBlank(supplyStatusEnum)) {
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
        if (StringUtils.isBlank(statusEnum)) {
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