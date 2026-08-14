/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.inpatient;

import com.openhis.health.domain.diagnosis.vitalsign.command.SaveVitalSignCommand;
import com.openhis.health.domain.diagnosis.vitalsign.command.SaveVitalSignDetailItemCommand;
import com.openhis.health.domain.diagnosis.vitalsign.enums.VitalSignTypeEnum;
import com.openhis.health.domain.diagnosis.vitalsign.queries.VitalSignEncounterQuery;
import com.openhis.health.domain.diagnosis.vitalsign.queries.model.VitalSignDetailRowQueryModel;
import com.openhis.health.domain.diagnosis.vitalsign.queries.model.VitalSignHeaderQueryModel;
import com.openhis.health.domain.diagnosis.vitalsign.service.VitalSignDomainService;
import com.openhis.health.domain.diagnosis.vitalsign.service.VitalSignQueryService;
import com.openhis.health.domain.diagnosis.vitalsign.support.VitalSignDetailSupport;
import com.openhis.health.domain.share.constants.VitalSignConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 入科入院体征读回填与写编排（AS-IP-NUR-001/002；001/002 AppService 共用）。
 *
 * <p>写侧按 {@code encounter_id + recorded_at(秒级窗) + type_enum} 定位主表后调
 * {@link VitalSignDomainService#saveVitalSign}；体温/心率/脉搏/血压锚定入科 {@code start_time}，
 * 身高/体重写入入科日 22:00 槽，便于体温单底部汇总与对应时次曲线回显。</p>
 */
@Component
@RequiredArgsConstructor
public class AdmissionVitalSignOrchestrator {

    /** 生命体征写侧领域服务（主表 upsert 与明细覆盖写）。 */
    private final VitalSignDomainService vitalSignDomainService;
    /** 生命体征读侧查询服务（按就诊/时点定位主表与明细）。 */
    private final VitalSignQueryService vitalSignQueryService;

    /**
     * 按就诊与记录时点加载入科体征提交块（IF-IP-NUR-03 详情回填）。
     *
     * @param encounterId 就诊主键
     * @param recordedAt  锚点时点（须为 {@code adm_encounter.start_time}）
     * @return 提交块；无数据时各字段为 null
     */
    public AdmissionVitalSignSubmitDto toSubmitDto(Long encounterId, LocalDateTime recordedAt) {
        AdmissionVitalSignSubmitDto dto = new AdmissionVitalSignSubmitDto();
        // Gate：缺锚点时不查库，避免误用当前时刻作入科时点
        if (encounterId == null || recordedAt == null) {
            return dto;
        }
        // Pre：入科锚点归一到秒，与写侧/时点窗查询一致（规避 timestamptz 微秒漂移）
        LocalDateTime slotRecordedAt = normalizeRecordedAt(recordedAt);
        // Pre：入科时点槽主表（体温/心率/脉搏/血压）
        VitalSignHeaderQueryModel slotHeader = findTemperatureSheetHeader(encounterId, slotRecordedAt);
        Map<String, String> slotValues = loadDetailValuesByCode(slotHeader);
        dto.setTemperature(slotValues.get(VitalSignConstants.TEMP));
        dto.setHeartRate(slotValues.get(VitalSignConstants.HR));
        dto.setPulse(slotValues.get(VitalSignConstants.PULSE));
        dto.setBloodPressureHigh(slotValues.get(VitalSignConstants.SBP));
        dto.setBloodPressureLow(slotValues.get(VitalSignConstants.DBP));

        // Pre：身高/体重优先读入科日 22:00 补充槽，兼容历史写在入科时点的数据
        LocalDateTime daySupplementAt = toDaySupplementRecordedAt(slotRecordedAt);
        VitalSignHeaderQueryModel dayHeader = findTemperatureSheetHeader(encounterId, daySupplementAt);
        Map<String, String> dayValues = loadDetailValuesByCode(dayHeader);
        String height = firstNonBlank(
            dayValues.get(VitalSignConstants.HEIGHT),
            slotValues.get(VitalSignConstants.HEIGHT));
        String weight = firstNonBlank(
            dayValues.get(VitalSignConstants.WEIGHT),
            slotValues.get(VitalSignConstants.WEIGHT));
        dto.setHeightCm(height);
        dto.setWeightKg(weight);
        return dto;
    }

    /**
     * 提交块是否至少含一项非空测量值。
     *
     * @param submitDto 提交块
     * @return 是否有非空项
     */
    public boolean hasAnySubmittedValue(AdmissionVitalSignSubmitDto submitDto) {
        if (submitDto == null) {
            return false;
        }
        String[] rawValues = {
            submitDto.getHeightCm(),
            submitDto.getWeightKg(),
            submitDto.getTemperature(),
            submitDto.getHeartRate(),
            submitDto.getPulse(),
            submitDto.getBloodPressureHigh(),
            submitDto.getBloodPressureLow()
        };
        for (String rawValue : rawValues) {
            if (trimToNull(rawValue) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * merge 后写入生命体征；无有效测量项时跳过（BR-IP-ATD-024）。
     *
     * <p>时点测量项（体温/心率/脉搏/血压）写入秒级归一后的 {@code start_time}；
     * 身高/体重写入同日 22:00，带入体温单底部汇总。</p>
     *
     * @param context 写上下文
     */
    public void saveIfPresent(AdmissionVitalSignSaveContext context) {
        // Gate：就诊、患者、入科锚点时点缺一不可
        if (context == null || context.getEncounterId() == null
            || context.getPatientId() == null || context.getRecordedAt() == null) {
            return;
        }
        if (!hasAnySubmittedValue(context.getSubmitDto())) {
            return;
        }
        // Pre：入科锚点归一到秒，保证同秒重复保存命中同一主表（upsert，避免每次 insert）
        LocalDateTime slotRecordedAt = normalizeRecordedAt(context.getRecordedAt());
        // Cmd：入科时点槽仅写体温/心率/脉搏/血压 → 体温单对应时次；身高体重只落 22:00
        saveMergedAtRecordedAt(
            context,
            slotRecordedAt,
            VitalSignConstants.admissionSlotCodes());
        // Cmd：入科日 22:00 补充槽写身高/体重 → 体温单底部汇总（AS-IP-NUR-007 锚定 22:00）
        saveMergedAtRecordedAt(
            context,
            toDaySupplementRecordedAt(slotRecordedAt),
            VitalSignConstants.admissionDaySupplementCodes());
    }

    /**
     * 在指定时点 merge 指定入科编码后 upsert 体温单主表。
     *
     * @param context      写上下文
     * @param recordedAt   目标记录时点
     * @param overlayCodes 本次允许覆盖的入科编码
     */
    private void saveMergedAtRecordedAt(
        AdmissionVitalSignSaveContext context,
        LocalDateTime recordedAt,
        List<String> overlayCodes) {
        if (recordedAt == null || overlayCodes == null || overlayCodes.isEmpty()) {
            return;
        }
        // Pre：仅当提交块含本批编码时才写，避免空覆盖冲掉库内值
        if (!submitHasAnyCode(context.getSubmitDto(), overlayCodes)) {
            return;
        }
        LocalDateTime normalizedRecordedAt = normalizeRecordedAt(recordedAt);
        List<SaveVitalSignDetailItemCommand> items = buildMergedDetailItems(
            context.getEncounterId(),
            normalizedRecordedAt,
            context.getSubmitDto(),
            overlayCodes);
        // 业务分支：明细均为空时跳过写，禁止空命令调领域层（BR-IP-ATD-024）
        if (items.isEmpty()) {
            return;
        }

        // Pre：同秒窗内已有主表则复用 id（入院体征只建一次，后续保存禁止再 insert）
        VitalSignHeaderQueryModel existingHeader = findTemperatureSheetHeader(
            context.getEncounterId(), normalizedRecordedAt);
        SaveVitalSignCommand command = new SaveVitalSignCommand();
        if (existingHeader != null) {
            command.setId(existingHeader.getId());
            command.setBusNo(existingHeader.getBusNo());
            // 业务分支：更新时保留首次落库时点，避免改时点后再查不到、又插新行
            command.setRecordedAt(existingHeader.getRecordedAt());
        } else {
            // Cmd：首次写入用秒级归一时点，便于后续同秒窗命中
            command.setRecordedAt(normalizedRecordedAt);
        }
        command.setEncounterId(context.getEncounterId());
        command.setPatientId(context.getPatientId());
        command.setTypeEnum(VitalSignTypeEnum.TEMPERATURE_SHEET.getCode());
        command.setLocationId(context.getLocationId());
        command.setRecorderId(context.getRecorderId());
        command.setOperatorId(context.getOperatorId());
        command.setItems(items);
        // Cmd：主表级覆盖写（提交集含 merge 后全部明细，避免误软删同槽其它项）
        vitalSignDomainService.saveVitalSign(command);
    }

    /**
     * 定位体温单主表（同就诊+同秒时点窗+类型；多条时取最早一条）。
     *
     * <p>时点窗为 {@code [recordedAt, recordedAt+1s)}，兼容历史微秒精度落库与本次秒级归一写。
     * 入院体征语义为「每就诊每锚点仅一份」：同窗多条时覆盖最早主表，不再新增。</p>
     *
     * @param encounterId 就诊 id
     * @param recordedAt  记录时点（调用方宜已 {@link #normalizeRecordedAt}）
     * @return 主表读模型或 null
     */
    private VitalSignHeaderQueryModel findTemperatureSheetHeader(
        Long encounterId,
        LocalDateTime recordedAt) {
        if (encounterId == null || recordedAt == null) {
            return null;
        }
        LocalDateTime windowFrom = normalizeRecordedAt(recordedAt);
        // Pre：半开窗上界（含微秒历史行，不含下一整秒）
        LocalDateTime windowTo = windowFrom.plusSeconds(1).minusNanos(1);
        VitalSignEncounterQuery query = new VitalSignEncounterQuery();
        query.setEncounterId(encounterId);
        query.setRecordedAtFrom(windowFrom);
        query.setRecordedAtTo(windowTo);
        query.setTypeEnum(VitalSignTypeEnum.TEMPERATURE_SHEET.getCode());
        List<VitalSignHeaderQueryModel> headers = vitalSignQueryService.listHeadersByEncounterId(query);
        if (headers.isEmpty()) {
            return null;
        }
        // 业务分支：同秒多条时取 id 最小（首次入院落库），后续保存只更新该行
        VitalSignHeaderQueryModel earliest = headers.get(0);
        for (VitalSignHeaderQueryModel header : headers) {
            if (header == null || header.getId() == null) {
                continue;
            }
            if (earliest == null || earliest.getId() == null
                || header.getId() < earliest.getId()) {
                earliest = header;
            }
        }
        return earliest;
    }

    /**
     * 记录时点归一到秒（清纳秒），统一入科锚点与 22:00 补充槽的定位键。
     *
     * @param recordedAt 原始时点
     * @return 秒级时点；入参 null 时返回 null
     */
    private LocalDateTime normalizeRecordedAt(LocalDateTime recordedAt) {
        if (recordedAt == null) {
            return null;
        }
        return recordedAt.withNano(0);
    }

    /**
     * 加载主表下明细 code → value（仅 vital_signs 体系，供入科字段回填）。
     *
     * @param header 主表；可空
     * @return 编码映射
     */
    private Map<String, String> loadDetailValuesByCode(VitalSignHeaderQueryModel header) {
        Map<String, String> valueByCode = new LinkedHashMap<>();
        if (header == null || header.getId() == null) {
            return valueByCode;
        }
        // Pre：按主表 id 加载有效明细行
        for (VitalSignDetailRowQueryModel row : vitalSignQueryService.getWithDetailsById(header.getId()).getDetails()) {
            if (row != null && trimToNull(row.getItemValue()) != null) {
                valueByCode.put(row.getItemCode(), row.getItemValue());
            }
        }
        return valueByCode;
    }

    /**
     * 加载主表下全部明细（含特殊事件），按自然键索引以便 merge 保真。
     *
     * @param vitalSignId 主表 id
     * @return 自然键 → 明细命令
     */
    private Map<String, SaveVitalSignDetailItemCommand> loadDetailsByNaturalKey(Long vitalSignId) {
        Map<String, SaveVitalSignDetailItemCommand> byKey = new LinkedHashMap<>();
        if (vitalSignId == null) {
            return byKey;
        }
        for (VitalSignDetailRowQueryModel row : vitalSignQueryService.getWithDetailsById(vitalSignId).getDetails()) {
            if (row == null || trimToNull(row.getItemCode()) == null || trimToNull(row.getItemValue()) == null) {
                continue;
            }
            SaveVitalSignDetailItemCommand item = new SaveVitalSignDetailItemCommand();
            item.setTermCode(VitalSignDetailSupport.normalizeTermCode(row.getTermCode()));
            item.setItemCode(row.getItemCode().trim());
            item.setItemValue(row.getItemValue().trim());
            item.setRemark(trimToNull(row.getRemark()));
            byKey.put(VitalSignDetailSupport.buildNaturalKey(item), item);
        }
        return byKey;
    }

    /**
     * merge 时点已有明细与本次入科覆盖编码；保留同槽非入科项，避免 BR-VS-005 误软删。
     *
     * @param encounterId  就诊 id
     * @param recordedAt   记录时点
     * @param submitDto    提交块
     * @param overlayCodes 允许覆盖的入科编码
     * @return 明细命令列表
     */
    private List<SaveVitalSignDetailItemCommand> buildMergedDetailItems(
        Long encounterId,
        LocalDateTime recordedAt,
        AdmissionVitalSignSubmitDto submitDto,
        List<String> overlayCodes) {
        VitalSignHeaderQueryModel existingHeader = findTemperatureSheetHeader(encounterId, recordedAt);
        Long existingHeaderId = null;
        if (existingHeader != null) {
            existingHeaderId = existingHeader.getId();
        }
        Map<String, SaveVitalSignDetailItemCommand> byKey = loadDetailsByNaturalKey(existingHeaderId);
        applySubmitOverlay(byKey, submitDto, overlayCodes);

        List<SaveVitalSignDetailItemCommand> items = new ArrayList<>();
        // 业务分支：输出 merge 后全部非空明细（含特殊事件等非入科项）
        for (SaveVitalSignDetailItemCommand item : byKey.values()) {
            if (item == null || trimToNull(item.getItemValue()) == null) {
                continue;
            }
            items.add(item);
        }
        return items;
    }

    /**
     * 将提交块中指定编码的非空字段覆盖到 merge 映射。
     *
     * @param byKey        自然键 → 明细
     * @param submitDto    提交块
     * @param overlayCodes 允许覆盖的编码
     */
    private void applySubmitOverlay(
        Map<String, SaveVitalSignDetailItemCommand> byKey,
        AdmissionVitalSignSubmitDto submitDto,
        List<String> overlayCodes) {
        if (submitDto == null || overlayCodes == null) {
            return;
        }
        for (String code : overlayCodes) {
            String rawValue = resolveSubmitValueByCode(submitDto, code);
            putVitalOverlay(byKey, code, rawValue);
        }
    }

    /**
     * 从提交块按入科编码取值。
     *
     * @param submitDto 提交块
     * @param code      明细编码
     * @return 原始值；未知编码返回 null
     */
    private String resolveSubmitValueByCode(AdmissionVitalSignSubmitDto submitDto, String code) {
        if (VitalSignConstants.HEIGHT.equals(code)) {
            return submitDto.getHeightCm();
        }
        if (VitalSignConstants.WEIGHT.equals(code)) {
            return submitDto.getWeightKg();
        }
        if (VitalSignConstants.TEMP.equals(code)) {
            return submitDto.getTemperature();
        }
        if (VitalSignConstants.HR.equals(code)) {
            return submitDto.getHeartRate();
        }
        if (VitalSignConstants.PULSE.equals(code)) {
            return submitDto.getPulse();
        }
        if (VitalSignConstants.SBP.equals(code)) {
            return submitDto.getBloodPressureHigh();
        }
        if (VitalSignConstants.DBP.equals(code)) {
            return submitDto.getBloodPressureLow();
        }
        return null;
    }

    /**
     * 非空测量值写入 merge 映射（vital_signs_code）。
     *
     * @param byKey    目标映射
     * @param code     字典项 code
     * @param rawValue 原始输入
     */
    private void putVitalOverlay(
        Map<String, SaveVitalSignDetailItemCommand> byKey,
        String code,
        String rawValue) {
        String normalized = trimToNull(rawValue);
        if (normalized == null) {
            return;
        }
        SaveVitalSignDetailItemCommand item = VitalSignDetailSupport.vitalSignItem(code, normalized);
        byKey.put(VitalSignDetailSupport.buildNaturalKey(item), item);
    }

    /**
     * 提交块是否含指定编码集合中的任一项非空值。
     *
     * @param submitDto 提交块
     * @param codes     编码集合
     * @return 是否有值
     */
    private boolean submitHasAnyCode(AdmissionVitalSignSubmitDto submitDto, List<String> codes) {
        if (submitDto == null || codes == null) {
            return false;
        }
        for (String code : codes) {
            if (trimToNull(resolveSubmitValueByCode(submitDto, code)) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * 入科日 22:00（体温单身高/体重补充槽）。
     *
     * @param recordedAt 入科锚点时点
     * @return 22:00 时点
     */
    private LocalDateTime toDaySupplementRecordedAt(LocalDateTime recordedAt) {
        LocalDateTime normalized = normalizeRecordedAt(recordedAt);
        if (normalized == null) {
            return null;
        }
        return normalized.toLocalDate().atTime(22, 0, 0);
    }

    /**
     * 取首个非空白字符串。
     *
     * @param first  优先值
     * @param second 回退值
     * @return 非空白值或 null
     */
    private String firstNonBlank(String first, String second) {
        String normalizedFirst = trimToNull(first);
        if (normalizedFirst != null) {
            return normalizedFirst;
        }
        return trimToNull(second);
    }
}
