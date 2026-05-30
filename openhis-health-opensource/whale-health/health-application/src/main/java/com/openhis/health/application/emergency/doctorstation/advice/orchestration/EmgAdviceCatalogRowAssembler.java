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

package com.openhis.health.application.emergency.doctorstation.advice.orchestration;

import com.openhis.health.application.emergency.doctorstation.advice.request.PageEmergencyAdviceCatalogRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.assembler.DoctorStationAdviceCatalogAssembler;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceCatalogRowDto;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.openhis.health.domain.medication.medicationdefinition.queries.model.MedicationDefinitionPageQueryModel;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionPageQueryModel;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 急诊医嘱目录行装配：域分页模型 + 批量定价 → {@link DoctorStationAdviceCatalogRowDto}（不含库存字段）。
 */
@Component
@RequiredArgsConstructor
public class EmgAdviceCatalogRowAssembler {

    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;

    /**
     * 药品目录页 → DTO 列表。
     */
    public List<DoctorStationAdviceCatalogRowDto> assembleMedicationPage(
        PageEmergencyAdviceCatalogRequest request,
        Page<MedicationDefinitionPageQueryModel> page
    ) {
        List<MedicationDefinitionPageQueryModel> content = page.getContent();
        if (content.isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (MedicationDefinitionPageQueryModel row : content) {
            if (row.getId() != null) {
                ids.add(row.getId());
            }
        }
        Map<Long, List<ChargeItemDefinition>> priceByDefId = loadPricingGrouped(TableNameConstants.MED_MEDICATION_DEFINITION, ids);
        String pricingSource = StringUtils.defaultIfBlank(request.getPricingSourceCode(), DoctorStationAdviceConstants.PRICING_SOURCE_RETAIL_PRICE);
        List<DoctorStationAdviceCatalogRowDto> out = new ArrayList<>();
        for (MedicationDefinitionPageQueryModel row : content) {
            DoctorStationAdviceCatalogRowDto dto = new DoctorStationAdviceCatalogRowDto();
            dto.setProductDefinitionId(row.getId());
            dto.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
            dto.setBusNo(row.getBusNo());
            dto.setName(row.getName());
            dto.setCategoryCode(row.getCategoryCode());
            dto.setTotalVolume(row.getTotalVolume());
            dto.setPricingSourceCode(pricingSource);
            applyPriceSelection(dto, pricingSource, TableNameConstants.MED_MEDICATION_DEFINITION, row.getId(), priceByDefId);
            out.add(dto);
        }
        return out;
    }

    /**
     * 耗材目录页 → DTO 列表。
     */
    public List<DoctorStationAdviceCatalogRowDto> assembleDevicePage(
        PageEmergencyAdviceCatalogRequest request,
        Page<DeviceDefinition> page
    ) {
        List<DeviceDefinition> content = page.getContent();
        if (content.isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (DeviceDefinition row : content) {
            if (row.getId() != null) {
                ids.add(row.getId());
            }
        }
        Map<Long, List<ChargeItemDefinition>> priceByDefId = loadPricingGrouped(TableNameConstants.ADM_DEVICE_DEFINITION, ids);
        String pricingSource = StringUtils.defaultIfBlank(request.getPricingSourceCode(), DoctorStationAdviceConstants.PRICING_SOURCE_RETAIL_PRICE);
        List<DoctorStationAdviceCatalogRowDto> out = new ArrayList<>();
        for (DeviceDefinition def : content) {
            DoctorStationAdviceCatalogRowDto dto = new DoctorStationAdviceCatalogRowDto();
            dto.setProductDefinitionId(def.getId());
            dto.setProductTableName(TableNameConstants.ADM_DEVICE_DEFINITION);
            dto.setBusNo(def.getBusNo());
            dto.setName(def.getName());
            dto.setCategoryCode(def.getCategoryCode());
            dto.setPyCode(def.getPyStr());
            dto.setWbCode(def.getWbStr());
            dto.setTotalVolume(def.getTotalVolume());
            dto.setPricingSourceCode(pricingSource);
            applyPriceSelection(dto, pricingSource, TableNameConstants.ADM_DEVICE_DEFINITION, def.getId(), priceByDefId);
            out.add(dto);
        }
        return out;
    }

    /**
     * 诊疗目录页 → DTO 列表。
     */
    public List<DoctorStationAdviceCatalogRowDto> assembleActivityPage(
        PageEmergencyAdviceCatalogRequest request,
        Page<ActivityDefinitionPageQueryModel> page
    ) {
        List<ActivityDefinitionPageQueryModel> content = page.getContent();
        if (content.isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (ActivityDefinitionPageQueryModel row : content) {
            if (row.getId() != null) {
                ids.add(row.getId());
            }
        }
        Map<Long, List<ChargeItemDefinition>> priceByDefId = loadPricingGrouped(TableNameConstants.WKF_ACTIVITY_DEFINITION, ids);
        String pricingSource = StringUtils.defaultIfBlank(request.getPricingSourceCode(), DoctorStationAdviceConstants.PRICING_SOURCE_RETAIL_PRICE);
        List<DoctorStationAdviceCatalogRowDto> out = new ArrayList<>();
        for (ActivityDefinitionPageQueryModel row : content) {
            DoctorStationAdviceCatalogRowDto dto = new DoctorStationAdviceCatalogRowDto();
            dto.setProductDefinitionId(row.getId());
            dto.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
            dto.setBusNo(row.getBusNo());
            dto.setName(row.getName());
            dto.setCategoryCode(row.getCategoryCode());
            dto.setPricingSourceCode(pricingSource);
            if (row.getTypeEnum() != null) {
                dto.setActivityTypeCode(row.getTypeEnum());
            }
            applyPriceSelection(dto, pricingSource, TableNameConstants.WKF_ACTIVITY_DEFINITION, row.getId(), priceByDefId);
            out.add(dto);
        }
        return out;
    }

    private Map<Long, List<ChargeItemDefinition>> loadPricingGrouped(String instanceTable, List<Long> instanceIds) {
        Map<Long, List<ChargeItemDefinition>> out = new HashMap<>();
        if (instanceIds == null || instanceIds.isEmpty()) {
            return out;
        }
        ChargeItemDefinitionQueryCommand cmd = new ChargeItemDefinitionQueryCommand();
        cmd.setInstanceTable(instanceTable);
        cmd.setInstanceIds(instanceIds);
        List<ChargeItemDefinition> lines = chargeItemDefinitionQueryService.findPriceLinesByProjects(cmd);
        for (ChargeItemDefinition line : lines) {
            Long id = line.getInstanceId();
            if (id == null) {
                continue;
            }
            out.computeIfAbsent(id, k -> new ArrayList<>()).add(line);
        }
        return out;
    }

    private static void applyPriceSelection(
        DoctorStationAdviceCatalogRowDto dto,
        String pricingSource,
        String instanceTable,
        Long instanceId,
        Map<Long, List<ChargeItemDefinition>> priceByDefId
    ) {
        if (instanceId == null) {
            return;
        }
        List<ChargeItemDefinition> lines = priceByDefId.getOrDefault(instanceId, List.of());
        ChargeItemDefinition selected = DoctorStationAdviceCatalogAssembler.selectCatalogPriceLine(pricingSource, lines, instanceTable);
        if (selected != null) {
            dto.setChargeItemDefinitionId(selected.getId());
            dto.setUnitPrice(selected.getPriceValue());
        }
    }
}
