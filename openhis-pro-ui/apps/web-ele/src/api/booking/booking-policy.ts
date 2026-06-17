/**
 * 放号策略与挂号策略 — 对接 {@code POST /api/app/bookingPolicy/*}
 */
import { publicRequestClient } from '#/api/request';

import type { BookingPagedResult } from './booking-types';

export interface ReleasePolicyDto {
  id?: number;
  policyCode?: string;
  policyName?: string;
  advanceDays?: number;
  slotMinutes?: number;
  capacityPerSlot?: number;
  channelQuotaJson?: string;
}

export interface PageReleasePoliciesRequest {
  keyword?: string;
  pageNo: number;
  pageSize: number;
}

export interface SaveReleasePolicyRequest {
  id?: number;
  policyCode: string;
  policyName: string;
  advanceDays: number;
  slotMinutes: number;
  capacityPerSlot: number;
  channelQuotaJson?: string;
}

export interface RegistrationPolicyDto {
  id?: number;
  orgId?: number;
  walkInSlotMode?: string;
  autoCheckInOnRegister?: boolean;
  onCancelPolicy?: string;
  allowEditPractitionerFromAppointment?: boolean;
}

export interface LoadRegistrationPolicyRequest {
  organizationId: number;
}

export interface SaveRegistrationPolicyRequest {
  organizationId: number;
  walkInSlotMode: string;
  autoCheckInOnRegister?: boolean;
  onCancelPolicy?: string;
  allowEditPractitionerFromAppointment?: boolean;
}

export interface GetRegistrationModeRequest {
  organizationId?: number;
}

export interface GetRegistrationModeResponse {
  bookingEnabled?: boolean;
  walkInSlotMode?: string;
  registrationPolicyLoaded?: boolean;
}

export interface ScheduleChangePolicyDto {
  id?: number;
  organizationId?: number;
  persisted?: boolean;
  onPractitionerStopMode?: string;
  transferMatchRule?: string;
  transferRequireSameService?: boolean;
  transferAllowCrossPractitioner?: boolean;
  transferPatientConfirmMode?: string;
  blockDeactivateIfUnhandled?: boolean;
  notifyOnTransfer?: boolean;
  notifyOnCancelStop?: boolean;
  freezeSourceSlotOnPreview?: boolean;
  freezeTtlMinutes?: number;
}

export interface GetScheduleChangePolicyRequest {
  organizationId: number;
}

export interface SaveScheduleChangePolicyRequest {
  id?: number;
  organizationId: number;
  onPractitionerStopMode: string;
  transferMatchRule: string;
  transferRequireSameService: boolean;
  transferAllowCrossPractitioner: boolean;
  transferPatientConfirmMode: string;
  blockDeactivateIfUnhandled: boolean;
  notifyOnTransfer: boolean;
  notifyOnCancelStop: boolean;
  freezeSourceSlotOnPreview: boolean;
  freezeTtlMinutes: number;
}

export async function pageReleasePoliciesApi(params: PageReleasePoliciesRequest) {
  return publicRequestClient.post<BookingPagedResult<ReleasePolicyDto>>(
    '/bookingPolicy/pageReleasePolicies',
    params,
  );
}

export async function loadReleasePolicyApi(params: { id: number }) {
  return publicRequestClient.post<ReleasePolicyDto>('/bookingPolicy/loadReleasePolicy', params);
}

export async function saveReleasePolicyApi(params: SaveReleasePolicyRequest) {
  return publicRequestClient.post<{ id?: number }>('/bookingPolicy/saveReleasePolicy', params);
}

export async function deleteReleasePolicyApi(params: { id: number }) {
  return publicRequestClient.post<void>('/bookingPolicy/deleteReleasePolicy', params);
}

export async function loadRegistrationPolicyApi(params: LoadRegistrationPolicyRequest) {
  return publicRequestClient.post<RegistrationPolicyDto>(
    '/bookingPolicy/getRegistrationPolicy',
    params,
  );
}

export async function saveRegistrationPolicyApi(params: SaveRegistrationPolicyRequest) {
  return publicRequestClient.post<void>('/bookingPolicy/saveRegistrationPolicy', params);
}

export async function getRegistrationModeApi(params: GetRegistrationModeRequest = {}) {
  return publicRequestClient.post<GetRegistrationModeResponse>(
    '/bookingPolicy/getRegistrationMode',
    params,
  );
}

export async function loadScheduleChangePolicyApi(params: GetScheduleChangePolicyRequest) {
  return publicRequestClient.post<ScheduleChangePolicyDto>(
    '/bookingPolicy/getScheduleChangePolicy',
    params,
  );
}

export async function saveScheduleChangePolicyApi(params: SaveScheduleChangePolicyRequest) {
  return publicRequestClient.post<ScheduleChangePolicyDto>(
    '/bookingPolicy/saveScheduleChangePolicy',
    params,
  );
}
