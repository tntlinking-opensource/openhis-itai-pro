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
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.constants.VitalSignConstants;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 体温单日×6 槽宽表与派生元数据装配（IF-IP-TMP-02 · SDD §3.2 步骤 7～8）。
 *
 * <p>读侧网格：就近落入同一图表槽的多条主表按 {@code detailItems.item_code} 合并，
 * 禁止整头只留最晚一条。{@link #assembleFillSlots} 仍按主表逐条回显，不参与该合并。</p>
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
            // 图表仍 6 列：同日就近落入该槽的主表按体征项合并，避免血压-only 整格冲掉体温/脉搏
            for (String timeSlot : TemperatureSheetRecordedAtResolver.TIME_SLOTS) {
                List<VitalSignHeaderQueryModel> slotHeaders =
                    listHeadersInChartSlot(cursor, timeSlot, headers);
                slotCells.add(buildMergedSlotCell(timeSlot, slotHeaders, detailsByHeaderId));
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
     * 收集指定日、就近落入该图表槽的全部体征头（30 分界与 {@link TemperatureSheetRecordedAtResolver} 一致）。
     *
     * @param recordDate 业务日
     * @param chartSlot  图表槽
     * @param headers    体征头列表
     * @return 落入该槽的主表；无则空列表
     */
    private List<VitalSignHeaderQueryModel> listHeadersInChartSlot(
        LocalDate recordDate,
        String chartSlot,
        List<VitalSignHeaderQueryModel> headers) {
        List<VitalSignHeaderQueryModel> slotHeaders = new ArrayList<>();
        if (headers == null || recordDate == null || chartSlot == null) {
            return slotHeaders;
        }
        for (VitalSignHeaderQueryModel header : headers) {
            if (header == null || header.getRecordedAt() == null) {
                continue;
            }
            // 只收当日记录，跨日不并入本槽
            if (!recordDate.equals(header.getRecordedAt().toLocalDate())) {
                continue;
            }
            String resolvedSlot = temperatureSheetRecordedAtResolver.extractTimeSlot(header.getRecordedAt());
            if (!chartSlot.equals(resolvedSlot)) {
                continue;
            }
            slotHeaders.add(header);
        }
        return slotHeaders;
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
     * 将同图表槽的多条主表按 {@code item_code} 合并为一个展示格。
     *
     * <p>按 {@code recordedAt} 升序后写覆盖同码且有值的项；不同码并集。
     * 特殊事件等非生命体征项同样按 code 保留，避免被仅含血压的较晚头整格冲掉。
     * {@code recordTime}/{@code vitalSignId} 优先取贡献了体温或脉搏/心率的较晚一条，否则取最晚一条。</p>
     *
     * @param timeSlot           图表槽
     * @param slotHeaders        已落入该槽的主表（可空）
     * @param detailsByHeaderId  headerId → 明细行
     * @return 网格单元格；无主表时仅带槽 code
     */
    private TemperatureSheetGridSlotCellDto buildMergedSlotCell(
        String timeSlot,
        List<VitalSignHeaderQueryModel> slotHeaders,
        Map<Long, List<VitalSignDetailRowQueryModel>> detailsByHeaderId) {
        TemperatureSheetGridSlotCellDto cell = new TemperatureSheetGridSlotCellDto();
        cell.setTimeSlot(timeSlot);
        if (slotHeaders == null || slotHeaders.isEmpty()) {
            return cell;
        }
        List<VitalSignHeaderQueryModel> ordered = new ArrayList<>(slotHeaders);
        ordered.sort(Comparator.comparing(VitalSignHeaderQueryModel::getRecordedAt));
        Map<String, TemperatureSheetDetailItemDto> mergedByCode = new LinkedHashMap<>();
        VitalSignHeaderQueryModel curveHeader = null;
        VitalSignHeaderQueryModel latestHeader = null;
        for (VitalSignHeaderQueryModel header : ordered) {
            latestHeader = header;
            List<TemperatureSheetDetailItemDto> items = TemperatureSheetDetailItemHelper.toItemDtos(
                detailsByHeaderId.get(header.getId()),
                header.getRemark());
            boolean contributedCurve = false;
            for (TemperatureSheetDetailItemDto item : items) {
                // 空值不覆盖同码已有值；标记类特殊事件 itemValue 可空，存在即有效
                if (!hasMergeableDetailValue(item)) {
                    continue;
                }
                String itemCode = item.getItemCode().trim();
                mergedByCode.put(itemCode, item);
                if (isCurveVitalItemCode(itemCode) && StringUtils.trimToNull(item.getItemValue()) != null) {
                    contributedCurve = true;
                }
            }
            // 升序遍历：后出现的曲线头覆盖前者，得到较晚一条体温/脉搏/心率来源
            if (contributedCurve) {
                curveHeader = header;
            }
        }
        VitalSignHeaderQueryModel identityHeader = latestHeader;
        if (curveHeader != null) {
            identityHeader = curveHeader;
        }
        if (identityHeader == null) {
            return cell;
        }
        cell.setVitalSignId(identityHeader.getId());
        // 带出曲线来源（或最晚一条）的真实时分，前端再按 30 分界就近落格
        cell.setRecordTime(temperatureSheetRecordedAtResolver.formatRecordTimeHm(identityHeader.getRecordedAt()));
        if (!mergedByCode.isEmpty()) {
            cell.setDetailItems(new ArrayList<>(mergedByCode.values()));
        }
        return cell;
    }

    /**
     * 判断明细项是否参与同槽合并（有测量值、备注，或为标记类特殊事件）。
     *
     * @param item 明细项
     * @return 可并入展示格时为 true
     */
    private boolean hasMergeableDetailValue(TemperatureSheetDetailItemDto item) {
        if (item == null || StringUtils.trimToNull(item.getItemCode()) == null) {
            return false;
        }
        if (StringUtils.trimToNull(item.getItemValue()) != null) {
            return true;
        }
        if (StringUtils.trimToNull(item.getRemark()) != null) {
            return true;
        }
        String termCode = StringUtils.trimToNull(item.getTermCode());
        return TerminologyCodeSystemNameConstants.SPECIAL_EVENT_CODE.equals(termCode);
    }

    /**
     * 是否为曲线描点依赖的体温、脉搏或心率。
     *
     * @param itemCode 字典项 code
     * @return 是曲线项时为 true
     */
    private boolean isCurveVitalItemCode(String itemCode) {
        if (VitalSignConstants.TEMP.equals(itemCode)) {
            return true;
        }
        if (VitalSignConstants.PULSE.equals(itemCode)) {
            return true;
        }
        return VitalSignConstants.HR.equals(itemCode);
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
