/**
 * 门诊排班 — 对接 {@code POST /api/app/outpatientSchedule/*}
 */
import { publicRequestClient } from '#/api/request';

import type { BookingOptionDto } from './booking-types';

export interface LoadScheduleFormInitRequest {
  organizationId?: number;
}

export interface LoadScheduleFormInitResponse {
  departments?: BookingOptionDto[];
  practitioners?: BookingOptionDto[];
  clinicLocations?: BookingOptionDto[];
  healthcareServices?: BookingOptionDto[];
  actorTypeOptions?: BookingOptionDto[];
}

export interface PageSchedulesByCalendarRequest {
  organizationId: number;
  rangeStart: string;
  rangeEnd: string;
  practitionerId?: number;
  locationId?: number;
  healthcareServiceId?: number;
}

export interface ScheduleActorDto {
  actorType?: number;
  actorId?: number;
  sortNo?: number;
}

export interface ScheduleCalendarCellDto {
  scheduleId?: number;
  name?: string;
  statusEnum?: string;
  planningHorizonStart?: string;
  planningHorizonEnd?: string;
  healthcareServiceId?: number;
  actors?: ScheduleActorDto[];
  fromTemplate?: boolean;
  weekScheduleTemplateId?: number;
  dayScheduleTemplateId?: number;
  bookable?: boolean;
  substitutePractitionerId?: number;
  originalPractitionerId?: number;
  substituteDisplay?: string;
}

export interface ScheduleCalendarDayDto {
  date?: string;
  schedules?: ScheduleCalendarCellDto[];
}

export interface PageSchedulesByCalendarResponse {
  days?: ScheduleCalendarDayDto[];
}

export interface PageSchedulesByMonthRequest {
  organizationId: number;
  yearMonth: string;
  practitionerId?: number;
  locationId?: number;
  healthcareServiceId?: number;
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
  actorId: number;
  sortNo?: number;
}

export interface CreateScheduleRequest {
  organizationId: number;
  name?: string;
  actors: ScheduleActorRequest[];
}

export interface CreateScheduleResponse {
  scheduleId?: number;
}

export interface UpdateScheduleDraftRequest {
  scheduleId: number;
  actors?: ScheduleActorRequest[];
  planningHorizonStart?: string;
  planningHorizonEnd?: string;
  commentMarkdown?: string;
}

export interface PublishScheduleRequest {
  scheduleId: number;
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
  scheduleId: number;
  organizationId: number;
  slotTemplates: SlotTemplateRequest[];
}

export interface ProvisionSlotBatchResponse {
  slotIds?: number[];
  generatedCount?: number;
}

export interface DeactivateScheduleRequest {
  scheduleId: number;
  reason?: string;
}

export interface GenerateFromPractitionerTemplateRequest {
  organizationId: number;
  practitionerId: number;
  locationId?: number;
  healthcareServiceId?: number;
  rangeStart: string;
  rangeEnd: string;
}

export interface GenerateFromPractitionerTemplateResponse {
  scheduleIds?: number[];
  generatedCount?: number;
}

export interface CopyScheduleToNextWeekRequest {
  organizationId: number;
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
  organizationId: number;
  scheduleId: number;
  slotIds?: number[];
  targetPractitionerId?: number;
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
  scheduleId?: number;
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
  organizationId: number;
  scheduleId: number;
  slotIds?: number[];
  dispositionMode: 'TRANSFER' | 'CANCEL' | 'MANUAL';
  targetPractitionerId?: number;
  transferItems?: StopClinicTransferItemRequest[];
  reason?: string;
  deferManualHandling?: boolean;
}

export interface ReleaseStopClinicPreviewFreezeRequest {
  organizationId: number;
  scheduleId: number;
  slotIds?: number[];
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
  organizationId: number;
  scheduleTemplateId?: number;
  weekScheduleTemplateId?: number;
  templateKind?: 'DAY' | 'WEEK';
  rangeStart: string;
  rangeEnd: string;
  autoPublish?: boolean;
  autoProvision?: boolean;
}

export interface GenerateFromTemplateResponse {
  scheduleIds?: number[];
  generatedCount?: number;
  skippedCount?: number;
  provisionedSlotCount?: number;
}

export interface GenerateDepartmentFromPractitionerRolesRequest {
  organizationId: number;
  practitionerIds?: number[];
}

export interface DepartmentPractitionerGenerateItemDto {
  practitionerId?: number;
  practitionerName?: string;
  scheduleIds?: number[];
  message?: string;
}

export interface GenerateDepartmentFromPractitionerRolesResponse {
  successItems?: DepartmentPractitionerGenerateItemDto[];
  failedItems?: DepartmentPractitionerGenerateItemDto[];
  totalScheduleCount?: number;
}

export interface PreviewPublishScheduleConflictsRequest {
  scheduleId: number;
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
  scheduleId: number;
  substitutePractitionerId?: number;
  reason?: string;
}

export interface SubstitutePractitionerResponse {
  scheduleId?: number;
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
  scheduleId: number;
}

export interface ReactivateScheduleResponse {
  scheduleId?: number;
  needReprovision?: boolean;
}

export async function reactivateScheduleApi(params: ReactivateScheduleRequest) {
  return publicRequestClient.post<ReactivateScheduleResponse>(
    '/outpatientSchedule/reactivateSchedule',
    params,
  );
}

export async function generateDepartmentFromPractitionerRolesApi(
  params: GenerateDepartmentFromPractitionerRolesRequest,
) {
  return publicRequestClient.post<GenerateDepartmentFromPractitionerRolesResponse>(
    '/outpatientSchedule/generateDepartmentFromPractitionerRoles',
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

export async function generateFromPractitionerTemplateApi(
  params: GenerateFromPractitionerTemplateRequest,
) {
  return publicRequestClient.post<GenerateFromPractitionerTemplateResponse>(
    '/outpatientSchedule/generateFromPractitionerTemplate',
    params,
  );
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
