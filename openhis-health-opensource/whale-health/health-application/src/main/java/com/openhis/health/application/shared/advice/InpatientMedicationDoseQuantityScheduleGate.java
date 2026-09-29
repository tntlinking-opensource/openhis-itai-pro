/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.advice;

import com.openhis.health.application.shared.option.FrequencyOptionLoader;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 住院长期药品分次用量不一致 Gate（AS-IP-DOR-003 · BR-IP-ORD-037 · EX-IP-ORD-310）。
 *
 * <p>域层已校验成对/正数/仅长期非中药；本 Gate 补「段数=频次 day_times 非空段数」与
 * 「有 schedule 时 doseUnitCode 非空」。中药携带非空由域层拒绝。</p>
 *
 * <p>落点：{@code application.shared.advice}（与 {@link InpatientMedicationAdviceGroupRateGate} 同包）。</p>
 *
 * <p><strong>边界（BR-IP-ORD-037⑧）</strong>：仅约束开立暂存/签发/改量<strong>写载荷</strong>的 schedule 合法性；
 * <strong>不</strong>改变停嘱、取消停嘱、不执行/取消执行、出院办理与待执行门禁、改业务时间的判定规则。
 * schedule 皆空 → 立即放行（原 dose/quantity 路径零侵入）。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientMedicationDoseQuantityScheduleGate {

    /** 频次 day_times 段数解析。 */
    private final FrequencyOptionLoader frequencyOptionLoader;

    /**
     * 断言草稿行分次 schedule 合法（空则放行）。
     *
     * @param therapyEnum      治疗类型 code
     * @param rateCode         频次 code
     * @param doseSchedule     计量序列；可空
     * @param quantitySchedule 拆零序列；可空
     * @param doseUnitCode     剂量单位；有 schedule 时必填
     * @throws BusinessException 段数/成对/therapy/单位非法 → EX-IP-ORD-310
     */
    public void assertDraftLineConsistent(
        String therapyEnum,
        String rateCode,
        String doseSchedule,
        String quantitySchedule,
        String doseUnitCode) {
        boolean doseBlank = StringUtils.isBlank(doseSchedule);
        boolean quantityBlank = StringUtils.isBlank(quantitySchedule);
        // 皆空：完全走原 dose/quantity，零侵入
        if (doseBlank && quantityBlank) {
            return;
        }
        // 一空一满：禁止半写
        if (doseBlank != quantityBlank) {
            throw new BusinessException(
                DoctorStationAdviceConstants.EX_IP_ORD_310,
                DoctorStationAdviceConstants.MSG_DOSE_QUANTITY_SCHEDULE_INVALID);
        }
        // 非长期禁止携带
        if (!TherapyType.LONG_TERM.getCode().equals(StringUtils.trimToEmpty(therapyEnum))) {
            throw new BusinessException(
                DoctorStationAdviceConstants.EX_IP_ORD_310,
                DoctorStationAdviceConstants.MSG_DOSE_QUANTITY_SCHEDULE_INVALID);
        }
        // 有 schedule 时计量单位必填且各次相同（单位列仍用 dose_unit_code）
        if (StringUtils.isBlank(doseUnitCode)) {
            throw new BusinessException(
                DoctorStationAdviceConstants.EX_IP_ORD_310,
                DoctorStationAdviceConstants.MSG_DOSE_QUANTITY_SCHEDULE_INVALID);
        }
        int doseSegments = this.countNonEmptyCommaSegments(doseSchedule);
        int quantitySegments = this.countNonEmptyCommaSegments(quantitySchedule);
        // 段数须一致（域层亦校验；此处提前给出统一 EX）
        if (doseSegments <= 0 || doseSegments != quantitySegments) {
            throw new BusinessException(
                DoctorStationAdviceConstants.EX_IP_ORD_310,
                DoctorStationAdviceConstants.MSG_DOSE_QUANTITY_SCHEDULE_INVALID);
        }
        Integer expected = frequencyOptionLoader.resolveDayTimesSegmentCount(rateCode);
        // 频次无 day_times / 无法解析：无法对齐段数，拒绝
        if (expected == null || expected.intValue() != doseSegments) {
            throw new BusinessException(
                DoctorStationAdviceConstants.EX_IP_ORD_310,
                DoctorStationAdviceConstants.MSG_DOSE_QUANTITY_SCHEDULE_INVALID);
        }
    }

    /**
     * 统计逗号分隔串中非空段数（与频次 day_times 段数口径一致）。
     *
     * @param csv 逗号串；可空
     * @return 非空段数；空串为 0
     */
    private int countNonEmptyCommaSegments(String csv) {
        if (StringUtils.isBlank(csv)) {
            return 0;
        }
        int count = 0;
        String[] parts = csv.split(",");
        for (String part : parts) {
            if (StringUtils.isNotEmpty(StringUtils.trimToEmpty(part))) {
                count++;
            }
        }
        return count;
    }
}
