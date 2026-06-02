import type {
  DocumentManagementInitData,
  DocumentManagementPageQuery,
} from './document-management.types';

import { publicRequestClient } from '#/api/request';

import { mockDocumentManagementInit, mockDocumentManagementPage } from './document-management.mock';

export type {
  DocumentManagementInitData,
  DocumentManagementPageQuery,
  WarehouseTypeOptionItem,
} from './document-management.types';

function useDocumentManagementMock(): boolean {
  return import.meta.env.VITE_USE_MOCK_DOCUMENT_MANAGEMENT === 'true';
}

/** 单据审批页：初始化下拉（库房类型 / 单据状态等） */
export async function documentManagementInitApi(): Promise<DocumentManagementInitData> {
  return useDocumentManagementMock()
    ? mockDocumentManagementInit()
    : publicRequestClient.get<DocumentManagementInitData>(
        '/pharmacy-warehouse/documentManagement/init',
      );
}

/** 单据审批页：分页列表 */
export async function documentManagementPageApi(
  params?: DocumentManagementPageQuery,
): Promise<{ records: Record<string, unknown>[]; total: number }> {
  return useDocumentManagementMock()
    ? mockDocumentManagementPage(params)
    : publicRequestClient.get<{ records: Record<string, unknown>[]; total: number }>(
        '/pharmacy-warehouse/documentManagement/documentManagement-page',
        { params },
      );
}
