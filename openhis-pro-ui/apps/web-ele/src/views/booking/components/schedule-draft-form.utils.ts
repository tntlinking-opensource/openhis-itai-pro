import { BOOKING_ACTOR_TYPE } from '#/api/booking/booking-constants';
import { toBookingId } from '#/api/booking/booking-id';
import type { ScheduleActorDto, ScheduleActorRequest } from '#/api/booking/outpatient-schedule';
import { loadSlotProvisionTemplateApi } from '#/api/booking/schedule-template';
import type { SlotProvisionTemplateItemDto } from '#/api/booking/schedule-template';

import type { ScheduleDraftFormModel } from './schedule-draft-form.vue';
import {
  inferScheduleModeFromPractitioner,
  isDepartmentScheduleMode,
  type ScheduleModeFormValue,
} from './schedule-template-shared';

export type SessionPreset = 'full' | 'morning' | 'afternoon';

export const SESSION_PRESETS: Record<
  SessionPreset,
  { label: string; startTime: string; endTime: string }
> = {
  full: { label: '全天', startTime: '00:00:00', endTime: '23:59:59' },
  morning: { label: '上午', startTime: '08:00:00', endTime: '12:00:00' },
  afternoon: { label: '下午', startTime: '14:00:00', endTime: '17:00:00' },
};

export function createEmptyDraftForm(): ScheduleDraftFormModel {
  return {
    name: '',
    scheduleMode: 'MODE-A',
    practitionerId: undefined,
    healthcareServiceId: undefined,
    planningDate: '',
    startTime: '',
    endTime: '',
    slotProvisionTemplateId: undefined,
  };
}

/** 新建时绑定出诊日期，出诊时段由放号时段（T1）带出 */
export function resetDraftFormForDate(form: ScheduleDraftFormModel, date: string) {
  form.planningDate = date;
}

/** 将 T1 时段行合并为出诊起止（取最早开始、最晚结束）。 */
export function applyT1ItemsToForm(
  form: ScheduleDraftFormModel,
  items?: SlotProvisionTemplateItemDto[],
) {
  if (!items?.length) {
    form.startTime = '';
    form.endTime = '';
    return;
  }
  let minStart: string | undefined;
  let maxEnd: string | undefined;
  for (const item of items) {
    if (!item.startTime || !item.endTime) {
      continue;
    }
    const start = item.startTime.length <= 5 ? `${item.startTime}:00` : item.startTime;
    const end = item.endTime.length <= 5 ? `${item.endTime}:00` : item.endTime;
    if (!minStart || start < minStart) {
      minStart = start;
    }
    if (!maxEnd || end > maxEnd) {
      maxEnd = end;
    }
  }
  form.startTime = minStart ?? '';
  form.endTime = maxEnd ?? '';
}

export function fillDraftFormFromActors(form: ScheduleDraftFormModel, actors?: ScheduleActorDto[]) {
  form.practitionerId = undefined;
  form.healthcareServiceId = undefined;
  if (!actors?.length) {
    form.scheduleMode = 'MODE-B';
    return;
  }
  for (const actor of actors) {
    const actorId = toBookingId(actor.actorId);
    if (actor.actorType === BOOKING_ACTOR_TYPE.PRACTITIONER) {
      form.practitionerId = actorId;
    } else if (actor.actorType === BOOKING_ACTOR_TYPE.HEALTHCARE_SERVICE) {
      form.healthcareServiceId = actorId;
    }
  }
  form.scheduleMode = inferScheduleModeFromPractitioner(form.practitionerId);
}

export function applyHorizonToForm(
  form: ScheduleDraftFormModel,
  planningHorizonStart?: string,
  planningHorizonEnd?: string,
  fallbackDate?: string,
) {
  if (planningHorizonStart) {
    form.planningDate = planningHorizonStart.slice(0, 10);
    form.startTime = planningHorizonStart.slice(11, 19) || '';
  } else if (fallbackDate) {
    form.planningDate = fallbackDate;
    form.startTime = '';
  }
  if (planningHorizonEnd) {
    form.endTime = planningHorizonEnd.slice(11, 19) || '';
  } else if (fallbackDate) {
    form.endTime = '';
  }
}

export function buildPlanningHorizon(form: ScheduleDraftFormModel) {
  return {
    planningHorizonStart: `${form.planningDate}T${form.startTime}`,
    planningHorizonEnd: `${form.planningDate}T${form.endTime}`,
  };
}

export function buildDraftActors(form: ScheduleDraftFormModel): ScheduleActorRequest[] {
  const scheduleMode = form.scheduleMode ?? 'MODE-A';
  const actors: ScheduleActorRequest[] = [];
  if (scheduleMode === 'MODE-A' && form.practitionerId) {
    actors.push({
      actorType: BOOKING_ACTOR_TYPE.PRACTITIONER,
      actorId: form.practitionerId,
      sortNo: 1,
    });
  }
  if (form.healthcareServiceId) {
    actors.push({
      actorType: BOOKING_ACTOR_TYPE.HEALTHCARE_SERVICE,
      actorId: form.healthcareServiceId,
      sortNo: 2,
    });
  }
  return actors;
}

/** 与排班设置「日排班」保存校验一致（放号时段可选，具体时段在放号时维护） */
export function assertDraftFormValid(form: ScheduleDraftFormModel) {
  const scheduleMode: ScheduleModeFormValue = form.scheduleMode ?? 'MODE-A';
  if (scheduleMode === 'MODE-A' && !form.practitionerId) {
    throw new Error('医师号须先选择出诊医师');
  }
  if (!form.healthcareServiceId) {
    throw new Error(scheduleMode === 'MODE-A' ? '请选择该医师可接诊的号源类型' : '请选择号源类型');
  }
  if (!form.planningDate) {
    throw new Error('请选择出诊日期');
  }
}

/** 保存/发布前解析规划窗口：有 T1 则合并时段，否则占全天格 */
export async function resolveDraftPlanningHorizon(form: ScheduleDraftFormModel) {
  assertDraftFormValid(form);
  const t1Id = toBookingId(form.slotProvisionTemplateId);
  if (t1Id) {
    const detail = await loadSlotProvisionTemplateApi({ id: t1Id });
    applyT1ItemsToForm(form, detail?.slotTemplates);
    if (form.startTime && form.endTime) {
      const horizon = buildPlanningHorizon(form);
      if (horizon.planningHorizonEnd > horizon.planningHorizonStart) {
        return horizon;
      }
    }
  }
  form.startTime = SESSION_PRESETS.full.startTime;
  form.endTime = SESSION_PRESETS.full.endTime;
  return buildPlanningHorizon(form);
}

export function extractPlanningDate(form: ScheduleDraftFormModel): string | undefined {
  return form.planningDate || undefined;
}

export function isPractitionerScheduleMode(scheduleMode?: string): boolean {
  return !isDepartmentScheduleMode(scheduleMode);
}
