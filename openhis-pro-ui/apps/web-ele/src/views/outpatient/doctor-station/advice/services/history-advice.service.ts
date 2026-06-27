import type {
  HistoryAdviceEncounterBundle,
  HistoryAdviceEncounterItem,
  HistoryAdviceRowItem,
} from './history-advice.types';

import type {
  DoctorStationAdviceRowDto,
  DoctorStationAdviceScopeTabCode,
  PageDoctorStationAdviceResponse,
  PatientHistoryDoctorStationAdviceBundleDto,
  PatientHistoryDoctorStationAdviceEncounterDto,
} from '#/api/doctor-station/advice.types';

import { formatDateTime } from '@vben/utils';

import { httpPageDoctorStationAdvice } from '#/api/doctor-station/advice.http';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

import { inferAdviceRowResourceType, mergeMedicationAdviceRowsForListDisplay } from './advice-list';

/** 历史医嘱默认一次拉取条数（结构-only 行，前端按 encounterId 分组） */
const HISTORY_ADVICE_PAGE_SIZE = 500;

/** 从收费/展示态推断是否允许复用 */
function mapReuseMeta(
  dto: DoctorStationAdviceRowDto,
): Pick<HistoryAdviceRowItem, 'reusable' | 'reuseWarning'> {
  const charge = String(dto.chargeStatusEnum ?? '').toUpperCase();
  const display = String(dto.displayStatusEnum ?? '').toUpperCase();
  const displayText = String(dto.displayStatusEnumText ?? '').trim();
  if (
    charge.includes('REFUND') ||
    display.includes('REFUND') ||
    displayText.includes('退费') ||
    displayText.includes('待退费')
  ) {
    return { reusable: false, reuseWarning: '该项目已退费或待退费，不可复用' };
  }
  return { reusable: true };
}

/** 组装左栏「药 x · 诊 x · 材 x」摘要 */
function buildAdviceCountSummary(rows: readonly DoctorStationAdviceRowDto[]): string {
  let med = 0;
  let srv = 0;
  let dev = 0;
  for (const row of rows) {
    switch (inferAdviceRowResourceType(row)) {
      case 'DEVICE': {
        dev += 1;
        break;
      }
      case 'MEDICATION': {
        med += 1;
        break;
      }
      case 'SERVICE': {
        srv += 1;
        break;
      }
    }
  }
  return `药 ${med} · 诊 ${srv} · 材 ${dev}`;
}

function formatVisitTime(raw?: null | string): string {
  const s = String(raw ?? '').trim();
  if (!s) return '—';
  return formatDateTime(s) || s;
}

function pickPrimaryDiagnosis(rows: readonly DoctorStationAdviceRowDto[]): string {
  for (const row of rows) {
    const name = String(row.conditionDefinitionName ?? row.diagnosisSummary ?? '').trim();
    if (name) return name;
  }
  return '—';
}

function pickLatestAuthoredAt(rows: readonly DoctorStationAdviceRowDto[]): string {
  return (
    rows
      .map((row) => String(row.authoredAt ?? '').trim())
      .filter(Boolean)
      .toSorted()
      .at(-1) ?? ''
  );
}

/** 接口就诊摘要 → 左栏卡片模型 */
function mapEncounterDto(
  dto: PatientHistoryDoctorStationAdviceEncounterDto,
  currentEncounterId: string,
  rawRows: readonly DoctorStationAdviceRowDto[],
): HistoryAdviceEncounterItem {
  const encounterId = String(dto.encounterId ?? '').trim();
  return {
    encounterId,
    visitTime: formatVisitTime(dto.visitTime),
    departmentName: String(dto.departmentName ?? '').trim() || '—',
    visitTypeText: String(dto.visitTypeText ?? '').trim() || '门诊',
    primaryDiagnosis: String(dto.primaryDiagnosis ?? '').trim() || pickPrimaryDiagnosis(rawRows),
    orderingDoctor: String(dto.orderingDoctor ?? '').trim() || '—',
    adviceCountSummary:
      String(dto.adviceCountSummary ?? '').trim() || buildAdviceCountSummary(rawRows),
    isCurrentEncounter: encounterId !== '' && encounterId === currentEncounterId.trim(),
  };
}

/** 单就诊医嘱 DTO 列表 → 表格行（含成组合并） */
function mapHistoryAdviceRows(
  encounterId: string,
  dtos: readonly DoctorStationAdviceRowDto[],
): HistoryAdviceRowItem[] {
  const reuseByRequestId = new Map<
    string,
    Pick<HistoryAdviceRowItem, 'reusable' | 'reuseWarning'>
  >();
  for (const dto of dtos) {
    const requestId = String(dto.requestId ?? '').trim();
    if (requestId) reuseByRequestId.set(requestId, mapReuseMeta(dto));
  }

  const merged = mergeMedicationAdviceRowsForListDisplay(dtos);
  return merged.map((row) => {
    const requestId = String(row.requestId ?? '').trim();
    const members = row.mergedAdviceRows ?? [row];
    const blockedMember = members.find((member) => {
      const rid = String(member.requestId ?? '').trim();
      return rid && reuseByRequestId.get(rid)?.reusable === false;
    });
    if (blockedMember) {
      const rid = String(blockedMember.requestId ?? '').trim();
      const warning =
        (rid ? reuseByRequestId.get(rid)?.reuseWarning : undefined) || '该医嘱不可复用';
      return { ...row, encounterId, reusable: false, reuseWarning: warning };
    }
    const reuseMeta = requestId ? reuseByRequestId.get(requestId) : undefined;
    return { ...row, encounterId, ...(reuseMeta ?? { reusable: true }) };
  });
}

function mapBundleDto(
  bundle: PatientHistoryDoctorStationAdviceBundleDto,
  currentEncounterId: string,
): HistoryAdviceEncounterBundle | null {
  const rawRows = bundle.rows ?? [];
  const encounterDto = bundle.encounter;
  if (!encounterDto) return null;
  const encounter = mapEncounterDto(encounterDto, currentEncounterId, rawRows);
  if (!encounter.encounterId) return null;
  return {
    encounter,
    rows: mapHistoryAdviceRows(encounter.encounterId, rawRows),
  };
}

function isRecord(v: unknown): v is Record<string, unknown> {
  return v !== null && typeof v === 'object' && !Array.isArray(v);
}

/** 兼容外层 data 包装 */
function unwrapHistoryAdviceResponse(res: unknown): PageDoctorStationAdviceResponse {
  if (!isRecord(res)) {
    return {};
  }
  const data = res.data;
  if (isRecord(data) && (data.bundles || data.content || data.rows || data.adviceRows)) {
    return data as PageDoctorStationAdviceResponse;
  }
  return res as PageDoctorStationAdviceResponse;
}

function isAdviceRowDto(row: unknown): row is DoctorStationAdviceRowDto {
  if (!isRecord(row)) {
    return false;
  }
  const dto = row as DoctorStationAdviceRowDto;
  return String(dto.requestId ?? dto.listRowKey ?? dto.productDefinitionId ?? '').trim() !== '';
}

function isAdviceBundleDto(row: unknown): row is PatientHistoryDoctorStationAdviceBundleDto {
  if (!isRecord(row)) {
    return false;
  }
  return isRecord((row as PatientHistoryDoctorStationAdviceBundleDto).encounter);
}

/** 扁平医嘱行按 encounterId 分组（pageDoctorStationAdvice 标准：content 为结构-only 行） */
function buildBundlesFromFlatAdviceRows(
  rows: readonly DoctorStationAdviceRowDto[],
  currentEncounterId: string,
): HistoryAdviceEncounterBundle[] {
  const byEncounter = new Map<string, DoctorStationAdviceRowDto[]>();
  for (const row of rows) {
    const encounterId = String(row.encounterId ?? '').trim();
    if (!encounterId) {
      continue;
    }
    const list = byEncounter.get(encounterId) ?? [];
    list.push(row);
    byEncounter.set(encounterId, list);
  }

  const pending: Array<{ bundle: HistoryAdviceEncounterBundle; rawVisitTime: string }> = [];
  for (const [encounterId, encounterRows] of byEncounter) {
    const visitTimeRaw = pickLatestAuthoredAt(encounterRows);
    const encounter = mapEncounterDto(
      {
        encounterId,
        visitTime: visitTimeRaw,
        departmentName: '—',
        visitTypeText: '门诊',
        primaryDiagnosis: pickPrimaryDiagnosis(encounterRows),
        orderingDoctor: String(encounterRows[0]?.authoredByName ?? '').trim() || '—',
      },
      currentEncounterId,
      encounterRows,
    );
    pending.push({
      rawVisitTime: visitTimeRaw,
      bundle: {
        encounter,
        rows: mapHistoryAdviceRows(encounterId, encounterRows),
      },
    });
  }

  return pending
    .toSorted((a, b) => b.rawVisitTime.localeCompare(a.rawVisitTime))
    .map((item) => item.bundle);
}

/** 解析历史医嘱分页响应 → 左栏就诊 + 行数据 */
function parseHistoryAdviceBundles(
  res: PageDoctorStationAdviceResponse,
  currentEncounterId: string,
): HistoryAdviceEncounterBundle[] {
  const flatRows = collectDoctorStationAdviceFlatRows(res);
  if (flatRows.length > 0) {
    return buildBundlesFromFlatAdviceRows(flatRows, currentEncounterId);
  }

  const bundles = res.bundles ?? [];
  if (bundles.length > 0) {
    const list: HistoryAdviceEncounterBundle[] = [];
    for (const bundle of bundles) {
      const mapped = mapBundleDto(bundle, currentEncounterId);
      if (mapped) list.push(mapped);
    }
    return list;
  }

  const { content: bundleContent } =
    parsePagedRowsFromApiData<PatientHistoryDoctorStationAdviceBundleDto>(res);
  const legacyBundles = bundleContent.filter((item) => isAdviceBundleDto(item));
  if (legacyBundles.length > 0) {
    return legacyBundles
      .map((b) => mapBundleDto(b, currentEncounterId))
      .filter((b): b is HistoryAdviceEncounterBundle => b !== null);
  }

  return [];
}

/** 从 pageDoctorStationAdvice 响应中提取扁平医嘱行（处方单/历史医嘱共用） */
export function collectDoctorStationAdviceFlatRows(
  res: PageDoctorStationAdviceResponse,
): DoctorStationAdviceRowDto[] {
  const { content: pagedContent } = parsePagedRowsFromApiData<DoctorStationAdviceRowDto>(res);
  const fromContent = pagedContent.filter((item) => isAdviceRowDto(item));
  if (fromContent.length > 0) {
    return fromContent;
  }

  const direct = (res.adviceRows ?? res.rows ?? []).filter((item) => isAdviceRowDto(item));
  if (direct.length > 0) {
    return direct;
  }

  const fromBundles: DoctorStationAdviceRowDto[] = [];
  for (const bundle of res.bundles ?? []) {
    for (const row of bundle.rows ?? []) {
      if (isAdviceRowDto(row)) {
        fromBundles.push(row);
      }
    }
  }
  return fromBundles;
}

/** 拉取 pageDoctorStationAdvice 单页响应 */
async function fetchDoctorStationAdvicePage(
  patientId: string,
  encounterId: string,
  adviceScopeTabCode: DoctorStationAdviceScopeTabCode,
  pageSize = HISTORY_ADVICE_PAGE_SIZE,
): Promise<null | PageDoctorStationAdviceResponse> {
  const pid = patientId.trim();
  const eid = encounterId.trim();
  if (!pid || !eid) {
    return null;
  }
  try {
    return await httpPageDoctorStationAdvice({
      patientId: pid,
      encounterId: eid,
      pageNo: 1,
      pageSize,
      adviceScopeTabCode,
    });
  } catch {
    return null;
  }
}

/** 解析单次分页响应为就诊 bundles */
function resolveHistoryAdviceBundlesFromPage(
  res: null | PageDoctorStationAdviceResponse,
  currentEncounterId: string,
): HistoryAdviceEncounterBundle[] {
  if (!res) {
    return [];
  }
  const body = unwrapHistoryAdviceResponse(res);
  return parseHistoryAdviceBundles(body, currentEncounterId);
}

/** 拉取 pageDoctorStationAdvice 扁平医嘱行（处方单查看等） */
export async function fetchDoctorStationAdviceFlatRows(
  patientId: string,
  encounterId: string,
  adviceScopeTabCode: DoctorStationAdviceScopeTabCode,
  pageSize = HISTORY_ADVICE_PAGE_SIZE,
): Promise<DoctorStationAdviceRowDto[]> {
  const res = await fetchDoctorStationAdvicePage(
    patientId,
    encounterId,
    adviceScopeTabCode,
    pageSize,
  );
  if (!res) {
    return [];
  }
  const body = unwrapHistoryAdviceResponse(res);
  return collectDoctorStationAdviceFlatRows(body);
}

/** 左栏就诊排序：本次置顶，其余按 visitTime 倒序 */
export function sortHistoryEncounterList(
  list: HistoryAdviceEncounterItem[],
): HistoryAdviceEncounterItem[] {
  const current = list.filter((e) => e.isCurrentEncounter);
  const others = list.filter((e) => !e.isCurrentEncounter);
  return [...current, ...others];
}

/** 拉取患者历史医嘱（按就诊分组的 bundles）；仅查 HISTORY，排除当前就诊 */
export async function fetchPatientHistoryDoctorStationAdvice(
  patientId: string,
  encounterId: string,
): Promise<HistoryAdviceEncounterBundle[]> {
  const pid = patientId.trim();
  const eid = encounterId.trim();
  if (!pid || !eid) return [];

  const res = await fetchDoctorStationAdvicePage(pid, eid, 'HISTORY');
  return resolveHistoryAdviceBundlesFromPage(res, eid);
}
