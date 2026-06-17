/**
 * 病案首页打印 API（OpenPDF · MRMS 包内实现）
 */

import type { PdfGenerateResultDto } from './types';

import { publicRequestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/preserve-api-id';

export async function generateHomepagePdfApi(caseRecordId: string) {
  return publicRequestClient.post<PdfGenerateResultDto>('/caseRecordHomepage/generateHomepagePdf', {
    value: preserveApiIdForRequest(caseRecordId),
  });
}
