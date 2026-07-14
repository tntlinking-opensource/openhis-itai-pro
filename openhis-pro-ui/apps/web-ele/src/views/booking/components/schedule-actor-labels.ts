import { BOOKING_ACTOR_TYPE } from '#/api/booking/booking-constants';
import { isSameBookingId, toBookingId } from '#/api/booking/booking-id';
import type { ScheduleCalendarCellDto, ScheduleActorDto } from '#/api/booking/outpatient-schedule';
import type { BookingOptionDto } from '#/api/booking/booking-types';

export function findActorId(
  actors: ScheduleActorDto[] | undefined,
  actorType: number,
): string | undefined {
  const hit = actors?.find((item) => item.actorType === actorType);
  return toBookingId(hit?.actorId);
}

export function resolveOptionLabel(options: BookingOptionDto[], id?: unknown) {
  const idText = toBookingId(id);
  if (!idText) return '';
  return options.find((item) => isSameBookingId(item.value, idText))?.label ?? '';
}

export function schedulePractitionerLabel(
  schedule: ScheduleCalendarCellDto | null | undefined,
  practitioners?: BookingOptionDto[],
) {
  if (!schedule) return '';
  if (schedule.practitionerName) return schedule.practitionerName;
  const id =
    toBookingId(schedule.originalPractitionerId) ??
    findActorId(schedule.actors, BOOKING_ACTOR_TYPE.PRACTITIONER);
  return resolveOptionLabel(practitioners ?? [], id);
}

export function scheduleLocationLabel(
  schedule: ScheduleCalendarCellDto | null | undefined,
  clinicLocations?: BookingOptionDto[],
) {
  if (!schedule) return '';
  if (schedule.locationName) return schedule.locationName;
  const id = findActorId(schedule.actors, BOOKING_ACTOR_TYPE.LOCATION);
  return resolveOptionLabel(clinicLocations ?? [], id);
}

/** 出诊科室：优先日程所属机构，否则从科室下拉解析 */
export function scheduleDepartmentLabel(
  organizationId?: unknown,
  departments?: BookingOptionDto[],
) {
  return resolveOptionLabel(departments ?? [], organizationId);
}

/** 日程是否绑定了出诊医师（医师号） */
export function scheduleHasPractitioner(
  schedule: ScheduleCalendarCellDto | null | undefined,
): boolean {
  if (!schedule) {
    return false;
  }
  if (schedule.practitionerName) {
    return true;
  }
  return Boolean(
    toBookingId(schedule.originalPractitionerId) ??
      findActorId(schedule.actors, BOOKING_ACTOR_TYPE.PRACTITIONER),
  );
}

/** 周历卡片第二行：医师号显示出诊医师，科室号显示出诊科室 */
export function scheduleCellActorLabel(
  schedule: ScheduleCalendarCellDto | null | undefined,
  options: {
    practitioners?: BookingOptionDto[];
    organizationId?: unknown;
    departments?: BookingOptionDto[];
  },
): string {
  if (!schedule) {
    return '';
  }
  if (scheduleHasPractitioner(schedule)) {
    return schedulePractitionerLabel(schedule, options.practitioners) || '未指定医师';
  }
  return scheduleDepartmentLabel(options.organizationId, options.departments) || '未指定科室';
}

export function scheduleServiceTypeLabel(
  schedule: ScheduleCalendarCellDto | null | undefined,
  healthcareServices?: BookingOptionDto[],
) {
  if (!schedule) return '';
  if (schedule.healthcareServiceName) return schedule.healthcareServiceName;
  const id =
    toBookingId(schedule.healthcareServiceId) ??
    findActorId(schedule.actors, BOOKING_ACTOR_TYPE.HEALTHCARE_SERVICE);
  return resolveOptionLabel(healthcareServices ?? [], id);
}
