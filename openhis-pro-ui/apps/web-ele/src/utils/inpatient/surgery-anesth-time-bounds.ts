import dayjs from 'dayjs';

import { toComparableMinute } from '#/utils/inpatient/encounter-order-time-bounds';

/**
 * 手术/麻醉业务时间相对入院～出院窗（与后端 EncounterStayTimeBoundsUtil 对齐）。
 *
 * 缺边界字段时不校；未填业务时刻不校。
 */

/**
 * 单时刻：不得早于入院、不得晚于出院。
 *
 * @param eventTime 业务时刻
 * @param bounds 入院/出院
 * @param fieldLabel 字段中文名
 * @returns 违规文案；通过返回 null
 */
export function validateClinicalTimeWithinStay(
  eventTime: null | string | undefined,
  bounds: {
    admissionTime?: null | string;
    dischargeTime?: null | string;
  },
  fieldLabel: string,
): null | string {
  const event = toComparableMinute(eventTime);
  if (!event) {
    return null;
  }
  const label = String(fieldLabel || '时间').trim() || '时间';
  const admission = toComparableMinute(bounds.admissionTime);
  if (admission && event.isBefore(admission)) {
    return `${label}不能早于入院时间`;
  }
  const discharge = toComparableMinute(bounds.dischargeTime);
  if (discharge && event.isAfter(discharge)) {
    return `${label}不能晚于出院时间`;
  }
  return null;
}

/**
 * 起止两端皆填时：结束不得早于开始。
 *
 * @param startTime 开始
 * @param endTime 结束
 * @param startLabel 开始字段名
 * @param endLabel 结束字段名
 * @returns 违规文案；通过或缺一端返回 null
 */
export function validateClinicalTimeRangeOrder(
  startTime: null | string | undefined,
  endTime: null | string | undefined,
  startLabel = '开始时间',
  endLabel = '结束时间',
): null | string {
  const start = toComparableMinute(startTime);
  const end = toComparableMinute(endTime);
  if (!start || !end) {
    return null;
  }
  if (end.isBefore(start)) {
    return `${endLabel}不能早于${startLabel}`;
  }
  return null;
}

/**
 * 拟手术时间（开立第一关）：入院～出院窗。
 *
 * @param procedureTime 拟手术时间
 * @param bounds 入院/出院
 * @returns 违规文案；通过返回 null
 */
export function validatePlannedProcedureTime(
  procedureTime: null | string | undefined,
  bounds: {
    admissionTime?: null | string;
    dischargeTime?: null | string;
  },
): null | string {
  return validateClinicalTimeWithinStay(procedureTime, bounds, '拟手术时间');
}

/**
 * 病案首页手术行：手术时间 + 麻醉起止（入院～出院 + 起止有序）。
 *
 * @param row 手术行字段
 * @param bounds 入院/出院（与表单同批）
 * @returns 首条违规文案；通过返回 null
 */
export function validateHomepageProcedureRowTimes(
  row: {
    anstEndTime?: null | string;
    anstStartTime?: null | string;
    oprnDate?: null | string;
    oprnTime?: null | string;
  },
  bounds: {
    admissionTime?: null | string;
    dischargeTime?: null | string;
  },
): null | string {
  let surgeryTime = String(row.oprnTime ?? '').trim();
  // 仅有手术日期无时分时，按当日 00:00 参与日窗校验
  if (!surgeryTime && row.oprnDate) {
    const dateOnly = String(row.oprnDate).trim();
    if (dateOnly) {
      const parsed = dayjs(dateOnly);
      if (parsed.isValid()) {
        surgeryTime = parsed.startOf('day').format('YYYY-MM-DD HH:mm:ss');
      }
    }
  }
  return (
    validateClinicalTimeWithinStay(surgeryTime, bounds, '手术时间') ||
    validateClinicalTimeWithinStay(row.anstStartTime, bounds, '麻醉开始时间') ||
    validateClinicalTimeWithinStay(row.anstEndTime, bounds, '麻醉结束时间') ||
    validateClinicalTimeRangeOrder(
      row.anstStartTime,
      row.anstEndTime,
      '麻醉开始时间',
      '麻醉结束时间',
    )
  );
}
