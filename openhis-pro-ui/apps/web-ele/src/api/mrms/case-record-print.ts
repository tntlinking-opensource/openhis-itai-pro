/**
 * 病案首页打印 API（待后端 generateHomepagePdf 落地）
 */

import type { PdfGenerateResultDto } from './types';

import { publicRequestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/preserve-api-id';

export async function generateHomepagePdfApi(caseRecordId: string) {
  return publicRequestClient.post<PdfGenerateResultDto>('/caseRecordHomepage/generateHomepagePdf', {
    value: preserveApiIdForRequest(caseRecordId),
  });
}
