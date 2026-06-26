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

package com.openhis.health.application.inpatient.nursestation.temperaturesheet.assembler;

import com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto.TemperatureSheetDerivedMetaDto;
import com.openhis.health.module.common.dto.nursestation.TemperatureSheetDetailItemDto;
import com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto.TemperatureSheetFillSlotDto;
import com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto.TemperatureSheetGridAssemblyResult;
import com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto.TemperatureSheetGridRowDto;
import com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto.TemperatureSheetGridSlotCellDto;
import com.openhis.health.module.common.assembler.nursestation.TemperatureSheetDetailItemSupport;
import com.openhis.health.module.common.assembler.nursestation.TemperatureSheetRecordedAtSupport;
import com.openhis.health.domain.diagnosis.vitalsign.queries.model.VitalSignDetailRowQueryModel;
import com.openhis.health.domain.diagnosis.vitalsign.queries.model.VitalSignHeaderQueryModel;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 体温单日×6 槽宽表与派生元数据装配（IF-IP-TMP-02 · SDD §3.2 步骤 7～8）。
 */
@Component
public class TemperatureSheetGridAssembler {

    /**
     * 装配日×6 槽宽表行与派生元数据（住院天数/周次/术后天数）。
     *
     * @param dateFrom       日期下界
     * @param dateTo         日期上界
     * @param admissionDate  入科日
     * @param surgeryDates   手术日期列表
     * @param headers        同时段主表
     * @param details        同时段明细
     * @return 网格行 + 派生 meta
     */
    public TemperatureSheetGridAssemblyResult assemble(
        LocalDate dateFrom,
        LocalDate dateTo,
        LocalDate admissionDate,
        List<LocalDate> surgeryDates,
        List<VitalSignHeaderQueryModel> headers,
        List<VitalSignDetailRowQueryModel> details) {
        TemperatureSheetGridAssemblyResult result = new TemperatureSheetGridAssemblyResult();
        result.setGridRows(assembleGridRows(dateFrom, dateTo, headers, details));
        result.setDerivedMeta(assembleDerivedMeta(dateFrom, dateTo, admissionDate, surgeryDates));
        return result;
    }

    /**
     * 装配日×6 槽宽表行（每槽携带明细项列表）。
     *
     * @param dateFrom  日期下界
     * @param dateTo    日期上界
     * @param headers   同时段主表
     * @param details   同时段明细
     * @return 网格行列表
     */
    public List<TemperatureSheetGridRowDto> assembleGridRows(
        LocalDate dateFrom,
        LocalDate dateTo,
        List<VitalSignHeaderQueryModel> headers,
        List<VitalSignDetailRowQueryModel> details) {
        List<TemperatureSheetGridRowDto> rows = new ArrayList<>();
        if (dateFrom == null || dateTo == null) {
            return rows;
        }
        Map<LocalDateTime, VitalSignHeaderQueryModel> headerByRecordedAt = indexHeadersByRecordedAt(headers);
        Map<Long, List<VitalSignDetailRowQueryModel>> detailsByHeaderId =
            TemperatureSheetDetailItemSupport.indexDetailsByHeaderId(details);
        LocalDate cursor = dateFrom;
        while (!cursor.isAfter(dateTo)) {
            TemperatureSheetGridRowDto row = new TemperatureSheetGridRowDto();
            row.setRecordDate(cursor);
            List<TemperatureSheetGridSlotCellDto> slotCells = new ArrayList<>();
            for (String timeSlot : TemperatureSheetRecordedAtSupport.TIME_SLOTS) {
                LocalDateTime recordedAt = cursor.atTime(Integer.parseInt(timeSlot), 0);
                slotCells.add(buildSlotCell(
                    timeSlot,
                    headerByRecordedAt.get(recordedAt),
                    detailsByHeaderId));
            }
            row.setSlotCells(slotCells);
            rows.add(row);
            cursor = cursor.plusDays(1);
        }
        return rows;
    }

    /**
     * 装配派生元数据（住院天数、周次、术后天数按日索引）。
     *
     * @param dateFrom       日期下界
     * @param dateTo         日期上界
     * @param admissionDate  入科日
     * @param surgeryDates   手术日期列表
     * @return 派生 meta
     */
    public TemperatureSheetDerivedMetaDto assembleDerivedMeta(
        LocalDate dateFrom,
        LocalDate dateTo,
        LocalDate admissionDate,
        List<LocalDate> surgeryDates) {
        TemperatureSheetDerivedMetaDto meta = new TemperatureSheetDerivedMetaDto();
        if (dateFrom == null || dateTo == null || admissionDate == null) {
            return meta;
        }
        LocalDate cursor = dateFrom;
        while (!cursor.isAfter(dateTo)) {
            String dateKey = cursor.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
            long hospitalDays = ChronoUnit.DAYS.between(admissionDate, cursor) + 1;
            if (hospitalDays < 1) {
                hospitalDays = 1;
            }
            meta.getHospitalizationDaysByDate().put(dateKey, (int) hospitalDays);
            int weekNo = (int) ((hospitalDays - 1) / 7) + 1;
            meta.getWeekNoByDate().put(dateKey, weekNo);
            Integer postOpDay = resolvePostoperativeDay(cursor, surgeryDates);
            if (postOpDay != null) {
                meta.getPostoperativeDaysByDate().put(dateKey, postOpDay);
            }
            cursor = cursor.plusDays(1);
        }
        return meta;
    }

    /**
     * 装配指定回显日的 6 槽录入数据（含明细项与 remark 投影项）。
     *
     * @param focusRecordDate 回显日
     * @param headers         该日主表
     * @param details         该日明细
     * @return 6 槽回显列表
     */
    public List<TemperatureSheetFillSlotDto> assembleFillSlots(
        LocalDate focusRecordDate,
        List<VitalSignHeaderQueryModel> headers,
        List<VitalSignDetailRowQueryModel> details) {
        List<TemperatureSheetFillSlotDto> fillSlots = new ArrayList<>();
        if (focusRecordDate == null) {
            return fillSlots;
        }
        Map<LocalDateTime, VitalSignHeaderQueryModel> headerByRecordedAt = indexHeadersByRecordedAt(headers);
        Map<Long, List<VitalSignDetailRowQueryModel>> detailsByHeaderId =
            TemperatureSheetDetailItemSupport.indexDetailsByHeaderId(details);
        for (String timeSlot : TemperatureSheetRecordedAtSupport.TIME_SLOTS) {
            LocalDateTime recordedAt = focusRecordDate.atTime(Integer.parseInt(timeSlot), 0);
            VitalSignHeaderQueryModel header = headerByRecordedAt.get(recordedAt);
            TemperatureSheetFillSlotDto slot = new TemperatureSheetFillSlotDto();
            slot.setTimeSlot(timeSlot);
            if (header != null) {
                slot.setVitalSignId(header.getId());
                List<TemperatureSheetDetailItemDto> itemDtos = TemperatureSheetDetailItemSupport.toItemDtos(
                    detailsByHeaderId.get(header.getId()),
                    header.getRemark());
                slot.setDetailItems(itemDtos);
            }
            fillSlots.add(slot);
        }
        return fillSlots;
    }

    private Map<LocalDateTime, VitalSignHeaderQueryModel> indexHeadersByRecordedAt(
        List<VitalSignHeaderQueryModel> headers) {
        Map<LocalDateTime, VitalSignHeaderQueryModel> result = new HashMap<>();
        if (headers == null) {
            return result;
        }
        for (VitalSignHeaderQueryModel header : headers) {
            if (header == null || header.getRecordedAt() == null) {
                continue;
            }
            if (!result.containsKey(header.getRecordedAt())) {
                result.put(header.getRecordedAt(), header);
            }
        }
        return result;
    }

    private TemperatureSheetGridSlotCellDto buildSlotCell(
        String timeSlot,
        VitalSignHeaderQueryModel header,
        Map<Long, List<VitalSignDetailRowQueryModel>> detailsByHeaderId) {
        TemperatureSheetGridSlotCellDto cell = new TemperatureSheetGridSlotCellDto();
        cell.setTimeSlot(timeSlot);
        if (header == null) {
            return cell;
        }
        cell.setVitalSignId(header.getId());
        cell.setDetailItems(TemperatureSheetDetailItemSupport.toItemDtos(
            detailsByHeaderId.get(header.getId()),
            header.getRemark()));
        return cell;
    }

    private Integer resolvePostoperativeDay(LocalDate recordDate, List<LocalDate> surgeryDates) {
        if (recordDate == null || surgeryDates == null || surgeryDates.isEmpty()) {
            return null;
        }
        LocalDate latestSurgery = null;
        for (LocalDate surgeryDate : surgeryDates) {
            if (surgeryDate == null) {
                continue;
            }
            if (!surgeryDate.isAfter(recordDate)) {
                if (latestSurgery == null || surgeryDate.isAfter(latestSurgery)) {
                    latestSurgery = surgeryDate;
                }
            }
        }
        if (latestSurgery == null) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(latestSurgery, recordDate);
        return (int) days;
    }
}
