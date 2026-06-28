import type { ElepCatalogItemDto } from '#/api/doctor-station/elep-catalog.types';

/** 新建空白 PreCheckPrescription 草稿体（最小字段）。 */
export function createEmptyElepPlatformBody(hospRxno?: string): Record<string, unknown> {
  const id = hospRxno?.trim() || `E${Date.now()}`;
  return {
    hospRxno: id,
    rxTypeCode: '1',
    rxDrugCnt: '0',
    valiDays: '3',
    rxdrugdetail: [],
    diseinfo: {},
    mdtrtinfo: {},
  };
}

export function drugCatalogItemToRxLine(item: ElepCatalogItemDto): Record<string, unknown> {
  return {
    medListCodg: item.hilistCode,
    drugGenname: item.hilistName,
    drugName: item.hilistName,
    drugSpec: item.spec,
    drugDosform: item.dosform,
    rxItemTypeCode: '11',
    rxItemTypeName: '西药',
    drugCnt: '1',
    drugDosunt: '盒',
  };
}

export function diagnosisCatalogItemToDiseinfo(item: ElepCatalogItemDto): Record<string, unknown> {
  return {
    diagCode: item.hilistCode,
    diagName: item.hilistName,
  };
}

export function clonePlatformBody(body: Record<string, unknown>): Record<string, unknown> {
  return JSON.parse(JSON.stringify(body)) as Record<string, unknown>;
}

export function appendDrugLine(
  body: Record<string, unknown>,
  item: ElepCatalogItemDto,
): Record<string, unknown> {
  const next = clonePlatformBody(body);
  const lines = Array.isArray(next.rxdrugdetail) ? [...next.rxdrugdetail] : [];
  lines.push(drugCatalogItemToRxLine(item));
  next.rxdrugdetail = lines;
  next.rxDrugCnt = String(lines.length);
  return next;
}

export function applyDiagnosis(
  body: Record<string, unknown>,
  item: ElepCatalogItemDto,
): Record<string, unknown> {
  const next = clonePlatformBody(body);
  next.diseinfo = diagnosisCatalogItemToDiseinfo(item);
  return next;
}

export function removeDrugLine(
  body: Record<string, unknown>,
  lineNo: number,
): Record<string, unknown> {
  const next = clonePlatformBody(body);
  const lines = Array.isArray(next.rxdrugdetail) ? [...next.rxdrugdetail] : [];
  next.rxdrugdetail = lines.filter((_, index) => index + 1 !== lineNo);
  next.rxDrugCnt = String((next.rxdrugdetail as unknown[]).length);
  return next;
}
