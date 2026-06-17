/**
 * 医保目录对码 Workbench — {@code POST /api/app/insuranceCatalogWorkbench/<method>}
 */
import type { ApiResponse } from '#/types/api-response';
import type { PageParams } from '#/types/api-response';

import { requestClient } from '#/api/request';

const BASE = '/insuranceCatalogWorkbench';

export type CatalogMappingWorkbenchStatus = 'PENDING_UPLOAD' | 'MATCHED' | 'NO_YB_NO';

export interface PendingCatalogMappingRow {
  productTable: string;
  productId: number | string;
  productTypeLabel?: string;
  busNo?: string;
  name?: string;
  ybNo?: string;
  ybMatchFlag?: boolean;
  chrgitmLv?: number;
  listType?: string;
}

export interface PagePendingCatalogMappingParams extends PageParams {
  contractBusNo?: string;
  productTable?: string;
  mappingStatus?: CatalogMappingWorkbenchStatus;
  keyword?: string;
}

export interface PagePendingCatalogMappingResult {
  rows?: PendingCatalogMappingRow[];
  total?: number;
  pageNo?: number;
  pageSize?: number;
  pendingUploadCount?: number;
  matchedCount?: number;
  noYbNoCount?: number;
}

export async function pagePendingCatalogMappingsApi(
  params: PagePendingCatalogMappingParams,
): Promise<PagePendingCatalogMappingResult> {
  const res = await requestClient.post<ApiResponse<PagePendingCatalogMappingResult>>(
    `${BASE}/pagePendingMappings`,
    params,
  );
  return (
    res.data ?? {
      rows: [],
      total: 0,
      pageNo: params.pageNo ?? 1,
      pageSize: params.pageSize ?? 20,
    }
  );
}
