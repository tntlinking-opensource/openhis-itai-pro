/**
 * 门诊排班 — 对接 {@code POST /api/app/outpatientSchedule/*}
 */
import { publicRequestClient } from '#/api/request';

import type { BookingEntityId } from './booking-id';
import type { BookingOptionDto } from './booking-types';

export interface LoadScheduleFormInitRequest {
  organizationId?: BookingEntityId;
}

export interface LoadScheduleFormInitResponse {
  departments?: BookingOptionDto[];
  practitioners?: BookingOptionDto[];
  clinicLocations?: BookingOptionDto[];
  healthcareServices?: BookingOptionDto[];
  actorTypeOptions?: BookingOptionDto[];
}

export interface PageSchedulesByCalendarRequest {
  organizationId: BookingEntityId;
  rangeStart: string;
  rangeEnd: string;
  practitionerId?: BookingEntityId;
  locationId?: BookingEntityId;
  healthcareServiceId?: BookingEntityId;
}

export interface ScheduleActorDto {
  actorType?: number;
  actorId?: BookingEntityId;
  sortNo?: number;
}

export interface ScheduleCalendarCellDto {
  scheduleId?: BookingEntityId;
  name?: string;
  statusEnum?: string;
  planningHorizonStart?: string;
  planningHorizonEnd?: string;
  healthcareServiceId?: BookingEntityId;
  actors?: ScheduleActorDto[];
  fromTemplate?: boolean;
  weekScheduleTemplateId?: BookingEntityId;
  dayScheduleTemplateId?: BookingEntityId;
  bookable?: boolean;
  substitutePractitionerId?: BookingEntityId;
  originalPractitionerId?: BookingEntityId;
  substituteDisplay?: string;
  practitionerName?: string;
  locationName?: string;
  healthcareServiceName?: string;
}

export interface ScheduleCalendarDayDto {
  date?: string;
  schedules?: ScheduleCalendarCellDto[];
}

export interface PageSchedulesByCalendarResponse {
  days?: ScheduleCalendarDayDto[];
}

export interface PageSchedulesByMonthRequest {
  organizationId: BookingEntityId;
  yearMonth: string;
  practitionerId?: BookingEntityId;
  locationId?: BookingEntityId;
  healthcareServiceId?: BookingEntityId;
}

export interface ScheduleCalendarMonthDayDto {
  date?: string;
  inMonth?: boolean;
  activeCount?: number;
  draftCount?: number;
  inactiveCount?: number;
  schedules?: ScheduleCalendarCellDto[];
}

export interface ScheduleMonthSummaryDto {
  publishedDayCount?: number;
  draftDayCount?: number;
  unscheduledDayCount?: number;
  inactiveDayCount?: number;
  bookedAppointmentCount?: number;
  totalScheduleCount?: number;
}

export interface PageSchedulesByMonthResponse {
  yearMonth?: string;
  monthStart?: string;
  monthEnd?: string;
  gridStart?: string;
  gridEnd?: string;
  days?: ScheduleCalendarMonthDayDto[];
  summary?: ScheduleMonthSummaryDto;
}

export interface ScheduleActorRequest {
  actorType: number;
  actorId: BookingEntityId;
  sortNo?: number;
}

export interface CreateScheduleRequest {
  organizationId: BookingEntityId;
  name?: string;
  actors: ScheduleActorRequest[];
  /** 规划窗口起；新建草稿须绑定才能在周历占格 */
  planningHorizonStart?: string;
  planningHorizonEnd?: string;
}

export interface CreateScheduleResponse {
  scheduleId?: BookingEntityId;
}

export interface UpdateScheduleDraftRequest {
  scheduleId: BookingEntityId;
  name?: string;
  actors?: ScheduleActorRequest[];
  planningHorizonStart?: string;
  planningHorizonEnd?: string;
  commentMarkdown?: string;
}

export interface PublishScheduleRequest {
  scheduleId: BookingEntityId;
  planningHorizonStart: string;
  planningHorizonEnd: string;
  acknowledgeConflictWarnings?: boolean;
}

export interface SlotTemplateRequest {
  startTime: string;
  endTime: string;
  capacity?: number;
}

export interface ProvisionSlotBatchRequest {
  scheduleId: BookingEntityId;
  organizationId: BookingEntityId;
  slotTemplates: SlotTemplateRequest[];
}

export interface ProvisionSlotBatchResponse {
  slotIds?: BookingEntityId[];
  generatedCount?: number;
}

export interface DeactivateScheduleRequest {
  scheduleId: BookingEntityId;
  reason?: string;
}

export interface CopyScheduleToNextWeekRequest {
  organizationId: BookingEntityId;
  sourceRangeStart: string;
  sourceRangeEnd: string;
  targetRangeStart: string;
  targetRangeEnd: string;
  copySlotProvision?: boolean;
}

export interface CopyScheduleToNextWeekResponse {
  copiedScheduleCount?: number;
  provisionedSlotCount?: number;
}

export interface PreviewStopClinicImpactRequest {
  organizationId: BookingEntityId;
  scheduleId: BookingEntityId;
  slotIds?: BookingEntityId[];
  targetPractitionerId?: BookingEntityId;
  dispositionMode?: 'TRANSFER' | 'CANCEL' | 'MANUAL';
}

export interface StopClinicImpactItemDto {
  appointmentId?: number;
  busNo?: string;
  patientId?: number;
  patientName?: string;
  sourceSlotId?: number;
  sourceStartTime?: string;
  sourceEndTime?: string;
  targetSlotId?: number;
  targetStartTime?: string;
  targetEndTime?: string;
  dispositionStatus?: string;
  dispositionMessage?: string;
}

export interface PreviewStopClinicImpactResponse {
  scheduleId?: BookingEntityId;
  scheduleCount?: number;
  slotCount?: number;
  appointmentCount?: number;
  registeredCount?: number;
  checkedInCount?: number;
  channelCount?: number;
  blockDeactivateIfUnhandled?: boolean;
  defaultDispositionMode?: string;
  transferMatchRule?: string;
  sourceSlotsFrozen?: boolean;
  frozenSlotCount?: number;
  freezeExpiresAt?: string;
  items?: StopClinicImpactItemDto[];
}

export interface StopClinicTransferItemRequest {
  appointmentId: number;
  targetSlotId: number;
}

export interface TransferAppointmentsOnStopClinicRequest {
  organizationId: BookingEntityId;
  scheduleId: BookingEntityId;
  slotIds?: BookingEntityId[];
  dispositionMode: 'TRANSFER' | 'CANCEL' | 'MANUAL';
  targetPractitionerId?: BookingEntityId;
  transferItems?: StopClinicTransferItemRequest[];
  reason?: string;
  deferManualHandling?: boolean;
}

export interface ReleaseStopClinicPreviewFreezeRequest {
  organizationId: BookingEntityId;
  scheduleId: BookingEntityId;
  slotIds?: BookingEntityId[];
}

export interface StopClinicTransferResultItemDto {
  appointmentId?: number;
  targetSlotId?: number;
  message?: string;
}

export interface TransferAppointmentsOnStopClinicResponse {
  successCount?: number;
  failedCount?: number;
  scheduleDeactivated?: boolean;
  blockedSlotCount?: number;
  successItems?: StopClinicTransferResultItemDto[];
  failedItems?: StopClinicTransferResultItemDto[];
}

export interface GenerateFromTemplateRequest {
  organizationId: BookingEntityId;
  scheduleTemplateId?: BookingEntityId;
  weekScheduleTemplateId?: BookingEntityId;
  templateKind?: 'DAY' | 'WEEK';
  rangeStart: string;
  rangeEnd: string;
  autoPublish?: boolean;
  autoProvision?: boolean;
}

export interface GenerateFromTemplateResponse {
  scheduleIds?: BookingEntityId[];
  generatedCount?: number;
  skippedCount?: number;
  provisionedSlotCount?: number;
}

export interface PreviewPublishScheduleConflictsRequest {
  scheduleId: BookingEntityId;
  planningHorizonStart: string;
  planningHorizonEnd: string;
}

export interface SchedulePublishConflictDto {
  conflictType?: string;
  resourceId?: number;
  conflictScheduleId?: number;
  conflictScheduleName?: string;
  message?: string;
}

export interface PreviewPublishScheduleConflictsResponse {
  hasConflict?: boolean;
  conflicts?: SchedulePublishConflictDto[];
}

export interface SubstitutePractitionerRequest {
  scheduleId: BookingEntityId;
  substitutePractitionerId?: number;
  reason?: string;
}

export interface SubstitutePractitionerResponse {
  scheduleId?: BookingEntityId;
  originalPractitionerId?: number;
  substitutePractitionerId?: number;
  substituteReason?: string;
  cleared?: boolean;
}

export async function substitutePractitionerApi(params: SubstitutePractitionerRequest) {
  return publicRequestClient.post<SubstitutePractitionerResponse>(
    '/outpatientSchedule/substitutePractitioner',
    params,
  );
}

export interface ReactivateScheduleRequest {
  scheduleId: BookingEntityId;
}

export interface ReactivateScheduleResponse {
  scheduleId?: BookingEntityId;
  needReprovision?: boolean;
}

export async function reactivateScheduleApi(params: ReactivateScheduleRequest) {
  return publicRequestClient.post<ReactivateScheduleResponse>(
    '/outpatientSchedule/reactivateSchedule',
    params,
  );
}

export async function previewPublishScheduleConflictsApi(
  params: PreviewPublishScheduleConflictsRequest,
) {
  return publicRequestClient.post<PreviewPublishScheduleConflictsResponse>(
    '/outpatientSchedule/previewPublishScheduleConflicts',
    params,
  );
}

export async function generateFromTemplateApi(params: GenerateFromTemplateRequest) {
  return publicRequestClient.post<GenerateFromTemplateResponse>(
    '/outpatientSchedule/generateFromTemplate',
    params,
  );
}

export async function loadScheduleFormInitApi(params: LoadScheduleFormInitRequest) {
  return publicRequestClient.post<LoadScheduleFormInitResponse>(
    '/outpatientSchedule/loadScheduleFormInit',
    params,
  );
}

export async function pageSchedulesByCalendarApi(params: PageSchedulesByCalendarRequest) {
  return publicRequestClient.post<PageSchedulesByCalendarResponse>(
    '/outpatientSchedule/pageSchedulesByCalendar',
    params,
  );
}

export async function pageSchedulesByMonthApi(params: PageSchedulesByMonthRequest) {
  return publicRequestClient.post<PageSchedulesByMonthResponse>(
    '/outpatientSchedule/pageSchedulesByMonth',
    params,
  );
}

export async function createScheduleApi(params: CreateScheduleRequest) {
  return publicRequestClient.post<CreateScheduleResponse>(
    '/outpatientSchedule/createSchedule',
    params,
  );
}

export async function updateScheduleDraftApi(params: UpdateScheduleDraftRequest) {
  return publicRequestClient.post<void>('/outpatientSchedule/updateScheduleDraft', params);
}

export async function publishScheduleApi(params: PublishScheduleRequest) {
  return publicRequestClient.post<void>('/outpatientSchedule/publishSchedule', params);
}

export async function provisionSlotBatchApi(params: ProvisionSlotBatchRequest) {
  return publicRequestClient.post<ProvisionSlotBatchResponse>(
    '/outpatientSchedule/provisionSlotBatch',
    params,
  );
}

export async function deactivateScheduleApi(params: DeactivateScheduleRequest) {
  return publicRequestClient.post<void>('/outpatientSchedule/deactivateSchedule', params);
}

export async function copyScheduleToNextWeekApi(params: CopyScheduleToNextWeekRequest) {
  return publicRequestClient.post<CopyScheduleToNextWeekResponse>(
    '/outpatientSchedule/copyScheduleToNextWeek',
    params,
  );
}

export async function previewStopClinicImpactApi(params: PreviewStopClinicImpactRequest) {
  return publicRequestClient.post<PreviewStopClinicImpactResponse>(
    '/outpatientSchedule/previewStopClinicImpact',
    params,
  );
}

export async function transferAppointmentsOnStopClinicApi(
  params: TransferAppointmentsOnStopClinicRequest,
) {
  return publicRequestClient.post<TransferAppointmentsOnStopClinicResponse>(
    '/outpatientSchedule/transferAppointmentsOnStopClinic',
    params,
  );
}

export async function releaseStopClinicPreviewFreezeApi(
  params: ReleaseStopClinicPreviewFreezeRequest,
) {
  return publicRequestClient.post<void>(
    '/outpatientSchedule/releaseStopClinicPreviewFreeze',
    params,
  );
}
