/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.temperaturesheet.assembler;

import com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto.TemperatureSheetDerivedMetaDto;
import com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto.TemperatureSheetDetailItemDto;
import com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto.TemperatureSheetFillSlotDto;
import com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto.TemperatureSheetGridAssemblyResult;
import com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto.TemperatureSheetGridRowDto;
import com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto.TemperatureSheetGridSlotCellDto;
import com.openhis.health.application.inpatient.nursestation.temperaturesheet.support.TemperatureSheetDetailItemHelper;
import com.openhis.health.application.inpatient.nursestation.temperaturesheet.support.TemperatureSheetRecordedAtResolver;
import com.openhis.health.domain.diagnosis.vitalsign.queries.model.VitalSignDetailRowQueryModel;
import com.openhis.health.domain.diagnosis.vitalsign.queries.model.VitalSignHeaderQueryModel;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 体温单日×6 槽宽表与派生元数据装配（IF-IP-TMP-02 · SDD §3.2 步骤 7～8）。
 */
@Component
@RequiredArgsConstructor
public class TemperatureSheetGridAssembler {

    private final TemperatureSheetRecordedAtResolver temperatureSheetRecordedAtResolver;

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
        Map<Long, List<VitalSignDetailRowQueryModel>> detailsByHeaderId =
            TemperatureSheetDetailItemHelper.indexDetailsByHeaderId(details);
        LocalDate cursor = dateFrom;
        while (!cursor.isAfter(dateTo)) {
            TemperatureSheetGridRowDto row = new TemperatureSheetGridRowDto();
            row.setRecordDate(cursor);
            List<TemperatureSheetGridSlotCellDto> slotCells = new ArrayList<>();
            // 图表仍 6 列：同日就近落在该时次的记录取最晚一条
            for (String timeSlot : TemperatureSheetRecordedAtResolver.TIME_SLOTS) {
                VitalSignHeaderQueryModel header = resolveLatestHeaderInChartSlot(cursor, timeSlot, headers);
                slotCells.add(buildSlotCell(timeSlot, header, detailsByHeaderId));
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
     * 装配指定回显日的录入数据（按实际测量时分回显，含明细项与 remark 投影项）。
     *
     * @param focusRecordDate 回显日
     * @param headers         该日主表
     * @param details         该日明细
     * @return 回显列表（timeSlot 为 HH:mm；无记录时为空列表）
     */
    public List<TemperatureSheetFillSlotDto> assembleFillSlots(
        LocalDate focusRecordDate,
        List<VitalSignHeaderQueryModel> headers,
        List<VitalSignDetailRowQueryModel> details) {
        List<TemperatureSheetFillSlotDto> fillSlots = new ArrayList<>();
        if (focusRecordDate == null) {
            return fillSlots;
        }
        Map<Long, List<VitalSignDetailRowQueryModel>> detailsByHeaderId =
            TemperatureSheetDetailItemHelper.indexDetailsByHeaderId(details);
        List<VitalSignHeaderQueryModel> dayHeaders = listHeadersOnDate(focusRecordDate, headers);
        dayHeaders.sort(Comparator.comparing(VitalSignHeaderQueryModel::getRecordedAt));
        // 按实际 recordedAt 逐条回显，供前端自选时分编辑
        for (VitalSignHeaderQueryModel header : dayHeaders) {
            if (header == null || header.getRecordedAt() == null) {
                continue;
            }
            TemperatureSheetFillSlotDto slot = new TemperatureSheetFillSlotDto();
            slot.setTimeSlot(temperatureSheetRecordedAtResolver.formatRecordTimeHm(header.getRecordedAt()));
            slot.setVitalSignId(header.getId());
            List<TemperatureSheetDetailItemDto> itemDtos = TemperatureSheetDetailItemHelper.toItemDtos(
                detailsByHeaderId.get(header.getId()),
                header.getRemark());
            slot.setDetailItems(itemDtos);
            fillSlots.add(slot);
        }
        return fillSlots;
    }

    /**
     * 取指定日、就近落在该图表槽的最晚一条体征头。
     *
     * @param recordDate 业务日
     * @param chartSlot  图表槽
     * @param headers    体征头列表
     * @return 最晚一条；无则 null
     */
    private VitalSignHeaderQueryModel resolveLatestHeaderInChartSlot(
        LocalDate recordDate,
        String chartSlot,
        List<VitalSignHeaderQueryModel> headers) {
        VitalSignHeaderQueryModel latest = null;
        if (headers == null) {
            return null;
        }
        for (VitalSignHeaderQueryModel header : headers) {
            if (header == null || header.getRecordedAt() == null) {
                continue;
            }
            if (!recordDate.equals(header.getRecordedAt().toLocalDate())) {
                continue;
            }
            String resolvedSlot = temperatureSheetRecordedAtResolver.extractTimeSlot(header.getRecordedAt());
            if (!chartSlot.equals(resolvedSlot)) {
                continue;
            }
            if (latest == null || header.getRecordedAt().isAfter(latest.getRecordedAt())) {
                latest = header;
            }
        }
        return latest;
    }

    /**
     * 筛选指定自然日的体征头。
     *
     * @param recordDate 业务日
     * @param headers    体征头列表
     * @return 当日体征头
     */
    private List<VitalSignHeaderQueryModel> listHeadersOnDate(
        LocalDate recordDate,
        List<VitalSignHeaderQueryModel> headers) {
        List<VitalSignHeaderQueryModel> result = new ArrayList<>();
        if (headers == null) {
            return result;
        }
        for (VitalSignHeaderQueryModel header : headers) {
            if (header == null || header.getRecordedAt() == null) {
                continue;
            }
            if (recordDate.equals(header.getRecordedAt().toLocalDate())) {
                result.add(header);
            }
        }
        return result;
    }

    /**
     * 构建体温单网格单时段单元格：关联体征头与明细项。
     *
     * @param timeSlot           时段标签
     * @param header             该时段体征头（可 null）
     * @param detailsByHeaderId  headerId → 明细行
     * @return 网格单元格 DTO
     */
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
        // 带出真实时分，前端描点按 30 分界就近落格，不依赖本槽 code
        cell.setRecordTime(temperatureSheetRecordedAtResolver.formatRecordTimeHm(header.getRecordedAt()));
        cell.setDetailItems(TemperatureSheetDetailItemHelper.toItemDtos(
            detailsByHeaderId.get(header.getId()),
            header.getRemark()));
        return cell;
    }

    /**
     * 计算记录日相对最近一次手术日的术后天数（含手术当日为 0）。
     *
     * @param recordDate    网格记录日期
     * @param surgeryDates  历次手术日期
     * @return 术后天数；无有效手术前手术时为 null
     */
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
