import type {
  ElepActionResultDto,
  ElepCloneRequest,
  ElepGeneratePdfRequest,
  ElepHistoryDetailDto,
  ElepPatientHistoryResultDto,
  ElepPlatformQueryRequest,
  ElepPreCheckRequest,
  ElepPrescriptionDraftDto,
  ElepRevokeRequest,
  ElepSaveDraftRequest,
  ElepSignRequest,
  ElepUploadFileRequest,
  ListElepEncounterPrescriptionsRequest,
  ListElepPatientHistoryRequest,
} from './elep.types';

import type { ElepEncounterPrescriptionItemDto } from './elep.types';

import { publicRequestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

const PREFIX = '/elepPrescription';

export async function httpListElepEncounterPrescriptions(
  body: ListElepEncounterPrescriptionsRequest,
): Promise<ElepEncounterPrescriptionItemDto[]> {
  return publicRequestClient.post<ElepEncounterPrescriptionItemDto[]>(
    `${PREFIX}/listEncounterPrescriptions`,
    {
      encounterId: preserveApiIdForRequest(body.encounterId),
    },
  );
}

export async function httpListElepPatientHistory(
  body: ListElepPatientHistoryRequest,
): Promise<ElepPatientHistoryResultDto> {
  return publicRequestClient.post<ElepPatientHistoryResultDto>(`${PREFIX}/listPatientHistory`, {
    patientId: preserveApiIdForRequest(body.patientId),
    currentEncounterId: body.currentEncounterId
      ? preserveApiIdForRequest(body.currentEncounterId)
      : undefined,
    contractBusNo: body.contractBusNo,
    page: body.page ?? 0,
    pageSize: body.pageSize ?? 20,
  });
}

export async function httpGetElepHistoryDetail(
  prescriptionId: string,
): Promise<ElepHistoryDetailDto> {
  return publicRequestClient.post<ElepHistoryDetailDto>(
    `${PREFIX}/getHistoryDetail`,
    preserveApiIdForRequest(prescriptionId),
  );
}

export async function httpElepPreCheck(body: ElepPreCheckRequest): Promise<ElepActionResultDto> {
  return publicRequestClient.post<ElepActionResultDto>(`${PREFIX}/preCheck`, {
    contractBusNo: body.contractBusNo,
    contractId: body.contractId ? preserveApiIdForRequest(body.contractId) : undefined,
    encounterId: preserveApiIdForRequest(body.encounterId),
    patientId: preserveApiIdForRequest(body.patientId),
    platformBody: body.platformBody,
    pdfInput: body.pdfInput,
  });
}

export async function httpElepSign(body: ElepSignRequest): Promise<ElepActionResultDto> {
  return publicRequestClient.post<ElepActionResultDto>(`${PREFIX}/sign`, {
    contractBusNo: body.contractBusNo,
    prescriptionId: preserveApiIdForRequest(body.prescriptionId),
    platformBody: body.platformBody,
  });
}

export async function httpElepUploadFile(
  body: ElepUploadFileRequest,
): Promise<ElepActionResultDto> {
  return publicRequestClient.post<ElepActionResultDto>(`${PREFIX}/uploadFile`, {
    contractBusNo: body.contractBusNo,
    prescriptionId: preserveApiIdForRequest(body.prescriptionId),
    platformBody: body.platformBody,
  });
}

export async function httpElepRevoke(body: ElepRevokeRequest): Promise<ElepActionResultDto> {
  return publicRequestClient.post<ElepActionResultDto>(`${PREFIX}/revoke`, {
    contractBusNo: body.contractBusNo,
    prescriptionId: preserveApiIdForRequest(body.prescriptionId),
    platformBody: body.platformBody,
  });
}

export async function httpElepCloneToEncounter(
  body: ElepCloneRequest,
): Promise<ElepPrescriptionDraftDto> {
  return publicRequestClient.post<ElepPrescriptionDraftDto>(`${PREFIX}/cloneToEncounter`, {
    sourcePrescriptionId: preserveApiIdForRequest(body.sourcePrescriptionId),
    targetEncounterId: preserveApiIdForRequest(body.targetEncounterId),
    patientId: preserveApiIdForRequest(body.patientId),
    contractBusNo: body.contractBusNo,
    contractId: body.contractId ? preserveApiIdForRequest(body.contractId) : undefined,
    continuationRx: body.continuationRx,
    copyDiagnosis: body.copyDiagnosis ?? true,
    newHospRxno: body.newHospRxno,
  });
}

export async function httpElepQueryPlatform(
  body: ElepPlatformQueryRequest,
): Promise<ElepActionResultDto> {
  return publicRequestClient.post<ElepActionResultDto>(`${PREFIX}/queryPlatform`, {
    contractBusNo: body.contractBusNo,
    prescriptionId: preserveApiIdForRequest(body.prescriptionId),
    contractId: body.contractId ? preserveApiIdForRequest(body.contractId) : undefined,
    hiRxno: body.hiRxno,
    refreshLocal: body.refreshLocal ?? true,
  });
}

export async function httpElepSaveDraft(
  body: ElepSaveDraftRequest,
): Promise<ElepPrescriptionDraftDto> {
  return publicRequestClient.post<ElepPrescriptionDraftDto>(`${PREFIX}/saveDraft`, {
    contractBusNo: body.contractBusNo,
    contractId: body.contractId ? preserveApiIdForRequest(body.contractId) : undefined,
    encounterId: preserveApiIdForRequest(body.encounterId),
    patientId: preserveApiIdForRequest(body.patientId),
    prescriptionId: body.prescriptionId ? preserveApiIdForRequest(body.prescriptionId) : undefined,
    platformBody: body.platformBody,
  });
}

export async function httpElepGeneratePdf(
  body: ElepGeneratePdfRequest,
): Promise<ElepActionResultDto> {
  return publicRequestClient.post<ElepActionResultDto>(`${PREFIX}/generatePdf`, {
    contractBusNo: body.contractBusNo,
    prescriptionId: preserveApiIdForRequest(body.prescriptionId),
    pdfInput: body.pdfInput,
  });
}
