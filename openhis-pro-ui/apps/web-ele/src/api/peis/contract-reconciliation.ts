/**
 * 团检合同对账 — {@code POST /api/app/peisGroupCheckAdmin/listContractReconciliation}
 */
import { publicRequestClient } from '#/api/request';

export interface ContractReconciliationSelectedPackageRowDto {
  packageActivityId?: string;
  rosterCount?: number;
  enrolledCount?: number;
  arrivedCount?: number;
}

export interface ContractReconciliationBatchRowDto {
  batchId?: string;
  batchName?: string;
  packageActivityId?: string;
  optionalPackageActivityIds?: string[];
  unselectedCount?: number;
  selectedPackages?: ContractReconciliationSelectedPackageRowDto[];
  bookingDate?: string;
  rosterTotal?: number;
  enrolledCount?: number;
  unenrolledCount?: number;
  arrivedCount?: number;
  pendingArrivalCount?: number;
}

export interface ContractReconciliationRowDto {
  contractId?: string;
  contractName?: string;
  companyId?: string;
  companyName?: string;
  contractAmount?: number | string;
  payStatusCode?: string;
  validFrom?: string;
  validTo?: string;
  batchCount?: number;
  rosterTotal?: number;
  enrolledCount?: number;
  unenrolledCount?: number;
  arrivedCount?: number;
  pendingArrivalCount?: number;
  addOnPendingCount?: number;
  addOnPendingAmount?: number | string;
  addOnSettledAmount?: number | string;
  batches?: ContractReconciliationBatchRowDto[];
}

export interface ListContractReconciliationRequest {
  companyId: string;
  payStatusCode?: string;
}

export function listContractReconciliationApi(params: ListContractReconciliationRequest) {
  return publicRequestClient.post<ContractReconciliationRowDto[]>(
    '/peisGroupCheckAdmin/listContractReconciliation',
    params,
  );
}
