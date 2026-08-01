import dayjs from 'dayjs';

/**
 * 住院改时 / 执行时间窗校验（前端提示，后端不双保险）：
 * - 开立 ≥ 入院；有出院时开立 ≤ 出院（与 DatePicker 分钟精度对齐）
 * - 停嘱 > 开立（秒级，禁止相等）；停嘱 ≥ 入院；有出院时停嘱 ≤ 出院
 * - 执行 actual：入院 ≤ actual ≤ 出院；有开立/停嘱时 开立 ≤ actual ≤ 停嘱（秒级）
 *
 * 缺边界字段时不校，避免拖垮正常开立/停嘱/执行主路径。
 */

/** 归一到分钟：入院/出院与列表 DatePicker 分钟精度对齐 */
export function toComparableMinute(raw?: null | string): dayjs.Dayjs | null {
  const s = String(raw ?? '').trim();
  if (!s) {
    return null;
  }
  const parsed = dayjs(s);
  if (!parsed.isValid()) {
    return null;
  }
  return parsed.second(0).millisecond(0);
}

/**
 * 归一到秒：开立↔停嘱、执行↔开立/停嘱用秒级，避免「同一分钟内开立后立刻停嘱」被误拦。
 */
export function toComparableSecond(raw?: null | string): dayjs.Dayjs | null {
  const s = String(raw ?? '').trim();
  if (!s) {
    return null;
  }
  const parsed = dayjs(s);
  if (!parsed.isValid()) {
    return null;
  }
  return parsed.millisecond(0);
}

/**
 * 开嘱/开立时间：不得早于入院时间。
 * @returns 违规文案；通过或缺少入院时间时返回 null
 */
export function validateOpenedTimeNotBeforeAdmission(
  newTime: string,
  admissionTime?: null | string,
): null | string {
  const opened = toComparableMinute(newTime);
  const admission = toComparableMinute(admissionTime);
  if (!opened || !admission) {
    return null;
  }
  if (opened.isBefore(admission)) {
    return '开嘱时间不能早于入院时间';
  }
  return null;
}

/**
 * 开立时间：不得晚于出院时间（仅已有出院时间时校验）。
 */
export function validateOpenedTimeNotAfterDischarge(
  newTime: string,
  dischargeTime?: null | string,
): null | string {
  const opened = toComparableMinute(newTime);
  const discharge = toComparableMinute(dischargeTime);
  if (!opened || !discharge) {
    return null;
  }
  if (opened.isAfter(discharge)) {
    return '开嘱时间不能晚于出院时间';
  }
  return null;
}

/**
 * 停嘱时间：不得晚于出院时间（仅已有出院时间时校验）。
 * @returns 违规文案；通过或未出院时返回 null
 */
export function validateStopTimeNotAfterDischarge(
  newTime: string,
  dischargeTime?: null | string,
): null | string {
  const stop = toComparableMinute(newTime);
  const discharge = toComparableMinute(dischargeTime);
  if (!stop || !discharge) {
    return null;
  }
  if (stop.isAfter(discharge)) {
    return '停嘱时间不能晚于出院时间';
  }
  return null;
}

/**
 * 停嘱时间：不得早于入院时间。
 */
export function validateStopTimeNotBeforeAdmission(
  newTime: string,
  admissionTime?: null | string,
): null | string {
  const stop = toComparableMinute(newTime);
  const admission = toComparableMinute(admissionTime);
  if (!stop || !admission) {
    return null;
  }
  if (stop.isBefore(admission)) {
    return '停嘱时间不能早于入院时间';
  }
  return null;
}

/**
 * 停嘱时间必须严格晚于开立时间（秒级；开立==停嘱禁止）。
 */
export function validateStopTimeAfterOpened(
  stopTime: string,
  openedTime?: null | string,
): null | string {
  const stop = toComparableSecond(stopTime);
  const opened = toComparableSecond(openedTime);
  if (!stop || !opened) {
    return null;
  }
  if (!stop.isAfter(opened)) {
    return '停嘱时间必须晚于开立时间';
  }
  return null;
}

/**
 * 开立时间必须严格早于已有停嘱时间（秒级）。
 */
export function validateOpenedTimeBeforeStop(
  openedTime: string,
  stopTime?: null | string,
): null | string {
  const opened = toComparableSecond(openedTime);
  const stop = toComparableSecond(stopTime);
  if (!opened || !stop) {
    return null;
  }
  if (!opened.isBefore(stop)) {
    return '开立时间必须早于停嘱时间';
  }
  return null;
}

/**
 * 改开立时间：入院下界 + 出院上界 + 已有停嘱上界。
 */
export function validateChangeOpenedBusinessTime(
  newOpenedTime: string,
  bounds: {
    admissionTime?: null | string;
    dischargeTime?: null | string;
    stopTime?: null | string;
  },
): null | string {
  return (
    validateOpenedTimeNotBeforeAdmission(newOpenedTime, bounds.admissionTime) ||
    validateOpenedTimeNotAfterDischarge(newOpenedTime, bounds.dischargeTime) ||
    validateOpenedTimeBeforeStop(newOpenedTime, bounds.stopTime)
  );
}

/**
 * 改停嘱 / 停嘱动作：入院下界 + 出院上界 + 必须晚于开立。
 */
export function validateChangeStopBusinessTime(
  newStopTime: string,
  bounds: {
    admissionTime?: null | string;
    dischargeTime?: null | string;
    openedTime?: null | string;
  },
): null | string {
  return (
    validateStopTimeNotBeforeAdmission(newStopTime, bounds.admissionTime) ||
    validateStopTimeNotAfterDischarge(newStopTime, bounds.dischargeTime) ||
    validateStopTimeAfterOpened(newStopTime, bounds.openedTime)
  );
}

/**
 * 执行时刻不得早于开立（秒级；相等允许；缺字段不校）。
 * @returns 违规文案；通过或缺字段时返回 null
 */
export function validateExecuteActualNotBeforeOpened(
  actualTime: string,
  openedTime?: null | string,
): null | string {
  const actualSecond = toComparableSecond(actualTime);
  const opened = toComparableSecond(openedTime);
  if (!actualSecond || !opened) {
    return null;
  }
  if (actualSecond.isBefore(opened)) {
    return '执行时间不能早于开立时间';
  }
  return null;
}

/**
 * 护士站执行改时：实际执行时刻相对入院/出院/开立/停嘱。
 * @returns 违规文案；通过或缺边界字段时返回 null
 */
export function validateNurseExecuteActualTimeBounds(
  actualTime: string,
  admissionTime?: null | string,
  dischargeTime?: null | string,
  openedTime?: null | string,
  stopTime?: null | string,
): null | string {
  const actualMinute = toComparableMinute(actualTime);
  if (!actualMinute) {
    return null;
  }
  const admission = toComparableMinute(admissionTime);
  if (admission && actualMinute.isBefore(admission)) {
    return '执行时间不能早于入院时间';
  }
  const discharge = toComparableMinute(dischargeTime);
  if (discharge && actualMinute.isAfter(discharge)) {
    return '执行时间不能晚于出院时间';
  }
  // 相对开立/停嘱用秒级，与改时交叉校验同口径
  const beforeOpened = validateExecuteActualNotBeforeOpened(actualTime, openedTime);
  if (beforeOpened) {
    return beforeOpened;
  }
  const actualSecond = toComparableSecond(actualTime);
  if (!actualSecond) {
    return null;
  }
  const stop = toComparableSecond(stopTime);
  if (stop && actualSecond.isAfter(stop)) {
    return '执行时间不能晚于停嘱时间';
  }
  return null;
}
