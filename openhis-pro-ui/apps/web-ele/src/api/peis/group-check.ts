/**
 * 团检管理 — {@code POST /api/app/peisGroupCheckAdmin/<method>}
 */
import { publicRequestClient } from '#/api/request';

import type { IdLike } from './catalog-config';

export interface CompanyRowDto {
  companyId?: IdLike;
  name?: string;
  orgId?: IdLike;
  campusCollegeId?: IdLike;
  contactName?: string;
  contactPhone?: string;
}

export interface ContractRowDto {
  contractId?: IdLike;
  companyId?: IdLike;
  packageActivityId?: IdLike;
  contractName?: string;
  validFrom?: string;
  validTo?: string;
  orgId?: IdLike;
}

export interface BatchRowDto {
  batchId?: IdLike;
  companyId?: IdLike;
  contractId?: IdLike;
  batchName?: string;
  packageActivityId?: IdLike;
  bookingDate?: string;
  statusCode?: string;
  orgId?: IdLike;
}

export interface GroupRosterRowDto {
  rosterId?: IdLike;
  batchId?: IdLike;
  patientId?: IdLike;
  fullName?: string;
  idCardNo?: string;
  studentNo?: string;
  employeeNo?: string;
  collegeId?: IdLike;
  className?: string;
  mobilePhone?: string;
  encounterId?: IdLike;
  payStatusCode?: string;
  arrivalStatusCode?: string;
}

export interface RosterImportRowDto {
  patientId?: IdLike;
  fullName?: string;
  studentNo?: string;
  employeeNo?: string;
  collegeId?: IdLike;
  className?: string;
  idCardNo?: string;
  mobilePhone?: string;
}

export interface SaveCompanyRequest {
  companyId?: IdLike;
  name: string;
  orgId: IdLike;
  campusCollegeId?: IdLike;
  contactName?: string;
  contactPhone?: string;
}

export interface SaveContractRequest {
  contractId?: IdLike;
  companyId: IdLike;
  packageActivityId: IdLike;
  contractName?: string;
  validFrom?: string;
  validTo?: string;
  orgId?: IdLike;
}

export interface SaveGroupBatchRequest {
  batchId?: IdLike;
  companyId?: IdLike;
  contractId: IdLike;
  batchName: string;
  packageActivityId?: IdLike;
  bookingDate?: string;
  orgId?: IdLike;
}

export interface ImportRosterRequest {
  batchId: IdLike;
  rows: RosterImportRowDto[];
}

export interface ConfirmGroupArrivalRequest {
  rosterId: IdLike;
}

export interface PageGroupRosterRequest {
  batchId: IdLike;
  payStatusCode?: string;
  collegeId?: IdLike;
  className?: string;
  pageNo: number;
  pageSize: number;
}

export interface PageGroupRosterResponse {
  content?: GroupRosterRowDto[];
  totalCount?: number;
  pageNo?: number;
  pageSize?: number;
}

export interface SaveCompanyResponse {
  companyId?: IdLike;
}

export interface SaveContractResponse {
  contractId?: IdLike;
}

export interface SaveGroupBatchResponse {
  batchId?: IdLike;
}

export interface ImportRosterResponse {
  importedCount?: number;
}

export interface ConfirmGroupArrivalResponse {
  rosterId?: IdLike;
  arrivalStatusCode?: string;
}

export function listCompaniesApi() {
  return publicRequestClient.post<CompanyRowDto[]>('/peisGroupCheckAdmin/listCompanies', {});
}

export function listContractsApi(params: { companyId: IdLike }) {
  return publicRequestClient.post<ContractRowDto[]>('/peisGroupCheckAdmin/listContracts', params);
}

export function listBatchesApi(params: { companyId: IdLike }) {
  return publicRequestClient.post<BatchRowDto[]>('/peisGroupCheckAdmin/listBatches', params);
}

export function saveCompanyApi(params: SaveCompanyRequest) {
  return publicRequestClient.post<SaveCompanyResponse>('/peisGroupCheckAdmin/saveCompany', params);
}

export function saveContractApi(params: SaveContractRequest) {
  return publicRequestClient.post<SaveContractResponse>(
    '/peisGroupCheckAdmin/saveContract',
    params,
  );
}

export function saveGroupBatchApi(params: SaveGroupBatchRequest) {
  return publicRequestClient.post<SaveGroupBatchResponse>(
    '/peisGroupCheckAdmin/saveGroupBatch',
    params,
  );
}

export function importRosterApi(params: ImportRosterRequest) {
  return publicRequestClient.post<ImportRosterResponse>(
    '/peisGroupCheckAdmin/importRoster',
    params,
  );
}

export function confirmGroupArrivalApi(params: ConfirmGroupArrivalRequest) {
  return publicRequestClient.post<ConfirmGroupArrivalResponse>(
    '/peisGroupCheckAdmin/confirmGroupArrival',
    params,
  );
}

export function pageRosterApi(params: PageGroupRosterRequest) {
  return publicRequestClient.post<PageGroupRosterResponse>(
    '/peisGroupCheckAdmin/pageRoster',
    params,
  );
}
