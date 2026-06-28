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
