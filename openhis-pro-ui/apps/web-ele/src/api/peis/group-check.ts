/**
 * 团检筹备 — {@code POST /api/app/peisGroupCheckAdmin/<method>}
 *
 * <p>实体主键统一使用 {@code string}（Snowflake Long 前端字符串化）。</p>
 */
import { publicRequestClient } from '#/api/request';

export interface CompanyRowDto {
  companyId?: string;
  name?: string;
  orgId?: string;
  campusCollegeId?: string;
  contactName?: string;
  contactPhone?: string;
}

export interface ContractRowDto {
  contractId?: string;
  companyId?: string;
  packageActivityId?: string;
  packageActivityIds?: string[];
  contractName?: string;
  validFrom?: string;
  validTo?: string;
  /** 合同金额（元） */
  contractAmount?: number | string;
  /** 合同支付状态：UNPAID / PAID */
  payStatusCode?: string;
  orgId?: string;
}

export interface BatchRowDto {
  batchId?: string;
  companyId?: string;
  contractId?: string;
  batchName?: string;
  packageActivityId?: string;
  bookingDate?: string;
  statusCode?: string;
  orgId?: string;
}

export interface GroupRosterRowDto {
  rosterId?: string;
  batchId?: string;
  patientId?: string;
  fullName?: string;
  genderEnum?: string;
  idCardNo?: string;
  studentNo?: string;
  employeeNo?: string;
  collegeId?: string;
  className?: string;
  mobilePhone?: string;
  encounterId?: string;
  payStatusCode?: string;
  arrivalStatusCode?: string;
  enrolled?: boolean;
}

export interface RosterImportRowDto {
  fullName?: string;
  genderEnum?: string;
  studentNo?: string;
  employeeNo?: string;
  collegeId?: string;
  className?: string;
  idCardNo?: string;
  mobilePhone?: string;
}

export interface SaveCompanyRequest {
  companyId?: string;
  name: string;
  orgId: string;
  campusCollegeId?: string;
  contactName?: string;
  contactPhone?: string;
}

export interface SaveContractRequest {
  contractId?: string;
  companyId: string;
  /** 兼容单选 */
  packageActivityId?: string;
  /** 合同可选套餐（至少一项） */
  packageActivityIds?: string[];
  contractName?: string;
  validFrom?: string;
  validTo?: string;
  /** 合同金额（元） */
  contractAmount?: number | string;
  orgId?: string;
}

export interface SaveGroupBatchRequest {
  batchId?: string;
  companyId?: string;
  contractId: string;
  batchName: string;
  packageActivityId?: string;
  bookingDate?: string;
  orgId?: string;
}

export interface ImportRosterRequest {
  batchId: string;
  rows: RosterImportRowDto[];
  /** 导入后自动建单，默认 true */
  autoEnroll?: boolean;
  /** STUDENT / STAFF / ENTERPRISE */
  memberKind?: string;
  /** 教职工同步 Campus，默认 true */
  syncCampus?: boolean;
}

export interface ConfirmGroupArrivalRequest {
  rosterId: string;
}

export interface PageGroupRosterRequest {
  batchId: string;
  payStatusCode?: string;
  enrollmentFilter?: 'ENROLLED' | 'UNENROLLED';
  arrivalStatusCode?: string;
  collegeId?: string;
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
  companyId?: string;
}

export interface SaveContractResponse {
  contractId?: string;
}

export interface SaveGroupBatchResponse {
  batchId?: string;
}

export interface ImportRosterResponse {
  importedCount?: number;
  enrolledCount?: number;
  campusSyncedCount?: number;
  enrollmentFailures?: EnrollmentFailureRowDto[];
  campusFailures?: EnrollmentFailureRowDto[];
}

export interface ConfirmGroupArrivalResponse {
  rosterId?: string;
  arrivalStatusCode?: string;
}

export interface ConfirmBatchEnrollmentRequest {
  rosterIds: string[];
  orgId?: string;
}

export interface EnrollmentFailureRowDto {
  rosterId?: string;
  fullName?: string;
  message?: string;
}

export interface ConfirmBatchEnrollmentResponse {
  successCount?: number;
  failures?: EnrollmentFailureRowDto[];
}

export interface LoadRosterSummaryRequest {
  batchId: string;
}

export interface LoadRosterSummaryResponse {
  totalCount?: number;
  enrolledCount?: number;
  unenrolledCount?: number;
  arrivedCount?: number;
  pendingArrivalCount?: number;
}

export function listCompaniesApi() {
  return publicRequestClient.post<CompanyRowDto[]>('/peisGroupCheckAdmin/listCompanies', {});
}

export function listContractsApi(params: { companyId: string }) {
  return publicRequestClient.post<ContractRowDto[]>('/peisGroupCheckAdmin/listContracts', params);
}

export function listBatchesApi(params: { companyId: string }) {
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

export interface MarkContractPaidRequest {
  contractId: string;
}

export interface MarkContractPaidResponse {
  contractId?: string;
  payStatusCode?: string;
}

export function markContractPaidApi(params: MarkContractPaidRequest) {
  return publicRequestClient.post<MarkContractPaidResponse>(
    '/peisGroupCheckAdmin/markContractPaid',
    params,
  );
}

export interface AdjustGroupRosterServiceRequestsRequest {
  rosterId: string;
  addActivityIds?: string[];
  removeServiceRequestIds?: string[];
}

export function adjustRosterServiceRequestsApi(params: AdjustGroupRosterServiceRequestsRequest) {
  return publicRequestClient.post<void>('/peisGroupCheckAdmin/adjustRosterServiceRequests', params);
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

export interface GroupRosterImportFileResult {
  fileName?: string;
  contentType?: string;
  fileBytes?: string;
}

export function downloadRosterImportTemplateApi(params: {
  memberKind: 'ENTERPRISE' | 'STAFF' | 'STUDENT';
}) {
  return publicRequestClient.post<GroupRosterImportFileResult>(
    '/peisGroupCheckAdmin/downloadRosterImportTemplate',
    params,
  );
}

export function importRosterFromTemplateApi(params: {
  batchId: string;
  memberKind: 'ENTERPRISE' | 'STAFF' | 'STUDENT';
  fileBytes: string;
  autoEnroll?: boolean;
  syncCampus?: boolean;
}) {
  return publicRequestClient.post<ImportRosterResponse>(
    '/peisGroupCheckAdmin/importRosterFromTemplate',
    params,
  );
}

export interface CampusRosterDepartmentOptionDto {
  departmentId?: string;
  departmentName?: string;
  collegeId?: string;
}

export interface CampusRosterCandidateDto {
  patientId?: string;
  fullName?: string;
  studentNo?: string;
  employeeNo?: string;
  className?: string;
  departmentName?: string;
}

export function listCampusRosterDepartmentsApi(params: { companyId: string }) {
  return publicRequestClient.post<{ departments?: CampusRosterDepartmentOptionDto[] }>(
    '/peisGroupCheckAdmin/listCampusRosterDepartments',
    params,
  );
}

export function listCampusRosterClassNamesApi(params: { companyId: string }) {
  return publicRequestClient.post<{ classNames?: string[] }>(
    '/peisGroupCheckAdmin/listCampusRosterClassNames',
    params,
  );
}

export function previewCampusRosterImportApi(params: {
  companyId: string;
  memberKind: 'STAFF' | 'STUDENT';
  className?: string;
  departmentId?: string;
}) {
  return publicRequestClient.post<{ candidates?: CampusRosterCandidateDto[] }>(
    '/peisGroupCheckAdmin/previewCampusRosterImport',
    params,
  );
}

export function importRosterFromCampusApi(params: {
  batchId: string;
  companyId: string;
  memberKind: 'STAFF' | 'STUDENT';
  className?: string;
  departmentId?: string;
  patientIds?: string[];
  autoEnroll?: boolean;
}) {
  return publicRequestClient.post<ImportRosterResponse>(
    '/peisGroupCheckAdmin/importRosterFromCampus',
    params,
  );
}

export function confirmGroupArrivalApi(params: ConfirmGroupArrivalRequest) {
  return publicRequestClient.post<ConfirmGroupArrivalResponse>(
    '/peisGroupCheckAdmin/confirmGroupArrival',
    params,
  );
}

export function cancelGroupArrivalApi(params: ConfirmGroupArrivalRequest) {
  return publicRequestClient.post<ConfirmGroupArrivalResponse>(
    '/peisGroupCheckAdmin/cancelGroupArrival',
    params,
  );
}

export function cancelGroupEnrollmentApi(params: ConfirmGroupArrivalRequest) {
  return publicRequestClient.post<ConfirmGroupArrivalResponse>(
    '/peisGroupCheckAdmin/cancelGroupEnrollment',
    params,
  );
}

export function confirmBatchEnrollmentApi(params: ConfirmBatchEnrollmentRequest) {
  return publicRequestClient.post<ConfirmBatchEnrollmentResponse>(
    '/peisGroupCheckAdmin/confirmBatchEnrollment',
    params,
  );
}

export function loadRosterSummaryApi(params: LoadRosterSummaryRequest) {
  return publicRequestClient.post<LoadRosterSummaryResponse>(
    '/peisGroupCheckAdmin/loadRosterSummary',
    params,
  );
}

export interface GroupRosterDeskContextDto {
  rosterId?: string;
  batchId?: string;
  patientId?: string;
  fullName?: string;
  companyId?: string;
  companyName?: string;
  batchName?: string;
  packageActivityId?: string;
  packageName?: string;
  encounterId?: string;
  payStatusCode?: string;
  arrivalStatusCode?: string;
  enrolled?: boolean;
  arrived?: boolean;
}

export interface LookupGroupRosterForDeskResponse {
  matches?: GroupRosterDeskContextDto[];
}

export function lookupRosterForDeskApi(params: { patientId: string }) {
  return publicRequestClient.post<LookupGroupRosterForDeskResponse>(
    '/peisGroupCheckAdmin/lookupRosterForDesk',
    params,
  );
}

export function pageRosterApi(params: PageGroupRosterRequest) {
  return publicRequestClient.post<PageGroupRosterResponse>(
    '/peisGroupCheckAdmin/pageRoster',
    params,
  );
}
