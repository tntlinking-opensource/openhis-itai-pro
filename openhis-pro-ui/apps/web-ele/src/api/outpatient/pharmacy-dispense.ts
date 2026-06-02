/**
 * 门诊发药工作站 — 对接 {@code POST /api/app/outpatientPharmacyDispense/<method>}
 */
import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';

const BASE = '/outpatientPharmacyDispense';

/** 与后端 AbstractPageQuery / LoadPharmacyDispensePageInitRequest 对齐 */
export interface LoadPharmacyDispensePageInitRequest {
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
  pharmacistRoleFilterCode?: number;
}

export interface OptionDto {
  label: string;
  value: string;
  sort?: number;
}

export interface PractitionerOptionDto {
  practitionerId?: number | string;
  displayName?: string;
  orgId?: number | string;
  orgName?: string;
}

/** Spring Data {@code Page<T>} JSON（Jackson） */
export interface SpringPage<T> {
  content: T[];
  totalElements?: number;
  totalPages?: number;
  size?: number;
  number?: number;
  numberOfElements?: number;
  first?: boolean;
  last?: boolean;
  empty?: boolean;
}

export interface LoadPharmacyDispensePageInitResponse {
  pharmacyOrganizationOptions?: OptionDto[];
  dispenseStatusFilterOptions?: OptionDto[];
  encounterClassOptions?: OptionDto[];
  notPerformedReasonOptions?: OptionDto[];
  preparerCandidatePage?: SpringPage<PractitionerOptionDto>;
}

export async function loadPharmacyDispensePageInitApi(body?: LoadPharmacyDispensePageInitRequest) {
  return requestClient.post<ApiResponse<LoadPharmacyDispensePageInitResponse>>(
    `${BASE}/loadPharmacyDispensePageInit`,
    body ?? {},
  );
}
