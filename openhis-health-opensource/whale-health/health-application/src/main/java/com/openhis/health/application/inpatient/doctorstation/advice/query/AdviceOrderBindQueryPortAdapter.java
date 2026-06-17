/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.doctorstation.advice.query;

import com.openhis.health.application.inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceOrderBindRowDto;
import com.openhis.health.module.querysupport.advice.AdviceOrderBindQueryPort;
import com.openhis.health.module.querysupport.advice.dto.AdviceOrderBindRowDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 医嘱绑定读查询端口适配（health-application → querysupport）。
 */
@Component
@RequiredArgsConstructor
public class AdviceOrderBindQueryPortAdapter implements AdviceOrderBindQueryPort {

    private final InpatientDoctorStationAdviceOrderBindQueryService orderBindQueryService;

    @Override
    public List<AdviceOrderBindRowDto> listBindingsBySourceItemKey(
        String bindType,
        String sourceItemKey,
        Long organizationUnitId) {
        List<InpatientDoctorStationAdviceOrderBindRowDto> rows =
            orderBindQueryService.listBindingsBySourceItemKey(bindType, sourceItemKey, organizationUnitId);
        return toAdviceOrderBindRows(rows);
    }

    private static List<AdviceOrderBindRowDto> toAdviceOrderBindRows(
        List<InpatientDoctorStationAdviceOrderBindRowDto> rows) {
        List<AdviceOrderBindRowDto> mapped = new ArrayList<>();
        if (rows == null) {
            return mapped;
        }
        for (InpatientDoctorStationAdviceOrderBindRowDto row : rows) {
            if (row == null) {
                continue;
            }
            AdviceOrderBindRowDto target = new AdviceOrderBindRowDto();
            target.setBindType(row.getBindType());
            target.setBindingId(row.getBindingId());
            target.setSourceItemId(row.getSourceItemId());
            target.setSourceItemName(row.getSourceItemName());
            target.setTargetItemId(row.getTargetItemId());
            target.setTargetItemTable(row.getTargetItemTable());
            target.setTargetItemName(row.getTargetItemName());
            target.setBusNo(row.getBusNo());
            target.setSpecificationText(row.getSpecificationText());
            target.setUnitPrice(row.getUnitPrice());
            target.setChargeItemDefinitionId(row.getChargeItemDefinitionId());
            target.setQuantityNumerator(row.getQuantityNumerator());
            target.setQuantityDenominator(row.getQuantityDenominator());
            target.setUnitCode(row.getUnitCode());
            target.setUnitName(row.getUnitName());
            target.setPriority(row.getPriority());
            target.setActiveFlag(row.getActiveFlag());
            mapped.add(target);
        }
        return mapped;
    }
}
