import { BOOKING_ACTOR_TYPE } from '#/api/booking/booking-constants';
import { toBookingId } from '#/api/booking/booking-id';
import type { ScheduleActorDto, ScheduleActorRequest } from '#/api/booking/outpatient-schedule';
import type { SlotProvisionTemplateItemDto } from '#/api/booking/schedule-template';

import type { ScheduleDraftFormModel } from './schedule-draft-form.vue';

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
    practitionerId: undefined,
    locationId: undefined,
    healthcareServiceId: undefined,
    planningDate: '',
    startTime: SESSION_PRESETS.full.startTime,
    endTime: SESSION_PRESETS.full.endTime,
    slotProvisionTemplateId: undefined,
  };
}

export function resetDraftFormForDate(form: ScheduleDraftFormModel, date: string) {
  form.planningDate = date;
  applySessionPreset(form, 'full');
}

export function applySessionPreset(form: ScheduleDraftFormModel, preset: SessionPreset) {
  const session = SESSION_PRESETS[preset];
  form.startTime = session.startTime;
  form.endTime = session.endTime;
}

/** 将 T1 时段行合并为出诊起止（取最早开始、最晚结束）。 */
export function applyT1ItemsToForm(
  form: ScheduleDraftFormModel,
  items?: SlotProvisionTemplateItemDto[],
) {
  if (!items?.length) {
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
  if (minStart) {
    form.startTime = minStart;
  }
  if (maxEnd) {
    form.endTime = maxEnd;
  }
}

export function fillDraftFormFromActors(form: ScheduleDraftFormModel, actors?: ScheduleActorDto[]) {
  form.practitionerId = undefined;
  form.locationId = undefined;
  form.healthcareServiceId = undefined;
  if (!actors?.length) {
    return;
  }
  for (const actor of actors) {
    const actorId = toBookingId(actor.actorId);
    if (actor.actorType === BOOKING_ACTOR_TYPE.PRACTITIONER) {
      form.practitionerId = actorId;
    } else if (actor.actorType === BOOKING_ACTOR_TYPE.LOCATION) {
      form.locationId = actorId;
    } else if (actor.actorType === BOOKING_ACTOR_TYPE.HEALTHCARE_SERVICE) {
      form.healthcareServiceId = actorId;
    }
  }
}

export function applyHorizonToForm(
  form: ScheduleDraftFormModel,
  planningHorizonStart?: string,
  planningHorizonEnd?: string,
  fallbackDate?: string,
) {
  if (planningHorizonStart) {
    form.planningDate = planningHorizonStart.slice(0, 10);
    form.startTime = planningHorizonStart.slice(11, 19) || SESSION_PRESETS.full.startTime;
  } else if (fallbackDate) {
    form.planningDate = fallbackDate;
    form.startTime = SESSION_PRESETS.full.startTime;
  }
  if (planningHorizonEnd) {
    form.endTime = planningHorizonEnd.slice(11, 19) || SESSION_PRESETS.full.endTime;
  } else if (fallbackDate) {
    form.endTime = SESSION_PRESETS.full.endTime;
  }
}

export function buildPlanningHorizon(form: ScheduleDraftFormModel) {
  return {
    planningHorizonStart: `${form.planningDate}T${form.startTime}`,
    planningHorizonEnd: `${form.planningDate}T${form.endTime}`,
  };
}

export function buildDraftActors(form: ScheduleDraftFormModel): ScheduleActorRequest[] {
  const actors: ScheduleActorRequest[] = [];
  if (form.practitionerId) {
    actors.push({
      actorType: BOOKING_ACTOR_TYPE.PRACTITIONER,
      actorId: form.practitionerId,
      sortNo: 1,
    });
  }
  if (form.locationId) {
    actors.push({
      actorType: BOOKING_ACTOR_TYPE.LOCATION,
      actorId: form.locationId,
      sortNo: 2,
    });
  }
  if (form.healthcareServiceId) {
    actors.push({
      actorType: BOOKING_ACTOR_TYPE.HEALTHCARE_SERVICE,
      actorId: form.healthcareServiceId,
      sortNo: 3,
    });
  }
  return actors;
}

export function assertDraftFormValid(form: ScheduleDraftFormModel) {
  if (!form.healthcareServiceId) {
    throw new Error('请选择号源类型');
  }
  if (!form.planningDate) {
    throw new Error('请选择出诊日期');
  }
  if (!form.startTime || !form.endTime) {
    throw new Error('请填写出诊起止时间');
  }
  const { planningHorizonStart, planningHorizonEnd } = buildPlanningHorizon(form);
  if (planningHorizonEnd <= planningHorizonStart) {
    throw new Error('出诊结束时间须晚于开始时间');
  }
}

export function extractPlanningDate(form: ScheduleDraftFormModel): string | undefined {
  return form.planningDate || undefined;
}
