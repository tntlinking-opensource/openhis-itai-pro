/**
 * 库存单据工作站侧栏列表：与 {@code purposeLocationId/sourceLocationId = 0}、「全部库房」占位、
 * 待审核单 applyTime 为空时的前端日期筛选等约定对齐（见 inventory-warehouse-doc-workstation-checklist）。
 */
import { ElMessage } from 'element-plus';

import {
  preserveApiIdForRequest,
  resolveLoginPractitionerIdForRequest,
} from '#/utils/format/preserve-api-id';
import {
  normalizeStockOutStatusSort,
  stockOutStatusLabel,
} from '#/utils/inventory/stock-out-doc-actions';

/** 列表「全部库位」占位 id（与后端 OptionDto / 订货单约定一致，字符串） */
export const INV_ALL_LOCATIONS_FILTER_ID = '0';

/** 侧栏下拉「全部」默认展示文案（药库场景） */
export const INV_ALL_WAREHOUSES_LABEL = '全部库房';

/** 侧栏下拉「全部」展示文案（药房场景） */
export const INV_ALL_PHARMACIES_LABEL = '全部药房';

/** 侧栏下拉「全部」展示文案（科室药房场景） */
export const INV_ALL_DEPT_PHARMACIES_LABEL = '全部科室药房';

/** init 响应中 OptionDto 列表（兼容后端字段名不一致，如药库盘点返回 pharmacyLocationOptions）。 */
export function pickInitOptionDtoList(
  data: null | Record<string, unknown> | undefined,
  ...fieldNames: string[]
): { label?: string; sort?: string; value?: unknown }[] {
  if (!data) return [];
  for (const name of fieldNames) {
    const raw = data[name];
    if (Array.isArray(raw) && raw.length > 0) {
      return raw as { label?: string; sort?: string; value?: unknown }[];
    }
  }
  for (const name of fieldNames) {
    const raw = data[name];
    if (Array.isArray(raw)) {
      return raw as { label?: string; sort?: string; value?: unknown }[];
    }
  }
  return [];
}

/** OptionDto 列表 → 单头/选品用库位项（value 一律 string）。 */
export function mapInitOptionDtoToLocationSelectOptions(
  list?: { label?: string; value?: unknown }[],
): { displayName: string; locationId: string }[] {
  return (list ?? [])
    .map((o) => {
      const locationId = String(o.value ?? '').trim();
      if (locationId === '') return null;
      return {
        locationId,
        displayName: String(o.label ?? '').trim() || locationId,
      };
    })
    .filter((o): o is { displayName: string; locationId: string } => o !== null);
}

/** 归一 purposeLocationId：未选/清空 → `'0'`（全部可见库位）。 */
export function resolveWorkbenchListPurposeLocationId(
  purposeLocationId?: number | string | null,
): string {
  if (
    purposeLocationId === undefined ||
    purposeLocationId === null ||
    String(purposeLocationId).trim() === '' ||
    String(purposeLocationId).trim() === INV_ALL_LOCATIONS_FILTER_ID
  ) {
    return INV_ALL_LOCATIONS_FILTER_ID;
  }
  return String(purposeLocationId).trim();
}

/** 归一 sourceLocationId（语义同 purpose，用于侧栏「源库位」筛选）。 */
export function resolveWorkbenchListSourceLocationId(
  sourceLocationId?: number | string | null,
): string {
  return resolveWorkbenchListPurposeLocationId(sourceLocationId);
}

/** 列表行申请/制单时刻：待审核单 applyTime 可能为空，回落 occurrenceTime。 */
export function resolveWorkbenchListRowTime(row: {
  applyTime?: string | null;
  occurrenceTime?: string | null;
}): string {
  const raw = row.applyTime ?? row.occurrenceTime;
  return raw === undefined || raw === null ? '' : String(raw).trim();
}

/** 前端按 yyyy-MM-dd 闭区间过滤（不传 begin/end 则原样返回）。applyTime 为空的行保留。 */
export function filterWorkbenchListRowsByDateRange<
  T extends { applyTime?: string | null; occurrenceTime?: string | null },
>(rows: T[], applyTimeBegin?: string, applyTimeEnd?: string): T[] {
  const begin = applyTimeBegin?.trim();
  const end = applyTimeEnd?.trim();
  if (!begin || !end) return rows;
  const startMs = new Date(`${begin}T00:00:00`).getTime();
  const endMs = new Date(`${end}T23:59:59`).getTime();
  if (!Number.isFinite(startMs) || !Number.isFinite(endMs)) return rows;
  return rows.filter((row) => {
    const timeText = resolveWorkbenchListRowTime(row);
    if (timeText === '') return true;
    const normalized = timeText.includes('T') ? timeText : timeText.replace(' ', 'T');
    const ms = new Date(normalized).getTime();
    if (!Number.isFinite(ms)) return true;
    return ms >= startMs && ms <= endMs;
  });
}

/**
 * 草稿区是否应清空重开（低层判定：本地尚无落库单号则可清）。
 *
 * 注意：侧栏切单场景请勿单独依赖本函数——切换已落库单后再点回「新建草稿」时
 * headerBusNo 仍可能残留，会被误判为「刚保存勿清」从而污染草稿。
 * 切单同步请用 `#/composables/use-inv-doc-parent-sync-session`。
 */
export function shouldResetWorkbenchDraftDetail(persistedBusNo?: null | string): boolean {
  return String(persistedBusNo ?? '').trim() === '';
}

/**
 * 保存成功后回选侧栏行；列表未命中时合成非草稿行，强制主区退出草稿态。
 */
export function pickWorkbenchOrderAfterSave(
  orderList: Record<string, unknown>[],
  busNo: string,
  resolveBusNo: (row: Record<string, unknown>) => string | undefined,
): Record<string, unknown> {
  const no = busNo.trim();
  const found = orderList.find((row) => resolveBusNo(row) === no);
  if (found) return found;
  return {
    busNo: no,
    supplyBusNo: no,
    statusEnum: 'PENDING_APPROVAL',
    statusEnum_enumText: '待审核',
  };
}

/** init 下拉首项是否为「全部」占位（value=0）。 */
export function isAllLocationsFilterOptionValue(value: unknown): boolean {
  return String(value ?? '').trim() === INV_ALL_LOCATIONS_FILTER_ID;
}

/** 是否为具体库位 id（非空且非「全部」占位 0）。 */
export function isSpecificInventoryLocationId(raw: unknown): boolean {
  if (raw === undefined || raw === null) return false;
  const s = String(raw).trim();
  if (s === '') return false;
  return !isAllLocationsFilterOptionValue(s);
}

export type InventoryLocationFieldKind =
  | 'deptPharmacy'
  | 'pharmacy'
  | 'purposePharmacy'
  | 'purposeWarehouse'
  | 'sourceWarehouse'
  | 'warehouse';

const INVENTORY_LOCATION_FIELD_LABELS: Record<InventoryLocationFieldKind, string> = {
  warehouse: '药库',
  pharmacy: '药房',
  deptPharmacy: '科室药房',
  sourceWarehouse: '源药库',
  purposeWarehouse: '目的药库',
  purposePharmacy: '目的药房',
};

/** 单头库位未选时的友好提示（禁止把 0/「全部」传给必选库位接口）。 */
export function warnInventoryLocationRequired(
  kind: InventoryLocationFieldKind = 'warehouse',
): void {
  ElMessage.warning(`请先在单头选择${INVENTORY_LOCATION_FIELD_LABELS[kind]}`);
}

/** 按业务文案提示单头库位必选（如 gain-loss 的 locationFieldLabel）。 */
export function warnInventoryLocationRequiredByLabel(label: string): void {
  const name = String(label ?? '').trim() || '库位';
  ElMessage.warning(`请先在单头选择${name}`);
}

/**
 * 调用必选库位接口前校验；失败时 ElMessage.warning 并返回 undefined。
 * 成功时返回 string id（雪花 Long 不做 Number 转换）。
 */
export function requireSpecificInventoryLocationId(
  raw: unknown,
  kind: InventoryLocationFieldKind = 'warehouse',
): string | undefined {
  if (!isSpecificInventoryLocationId(raw)) {
    warnInventoryLocationRequired(kind);
    return undefined;
  }
  return preserveLocationIdForRequest(raw) ?? String(raw).trim();
}

/** 从多个候选库位 id 中取首个有效具体库位（不弹提示）。 */
export function resolveSpecificInventoryLocationId(...candidates: unknown[]): string | undefined {
  for (const raw of candidates) {
    if (isSpecificInventoryLocationId(raw)) {
      return preserveLocationIdForRequest(raw) ?? String(raw).trim();
    }
  }
  return undefined;
}

/** 从多个候选库位 id 中取首个有效具体库位；均无效时弹一次友好提示。 */
export function requireAnySpecificInventoryLocationId(
  kind: InventoryLocationFieldKind,
  ...candidates: unknown[]
): string | undefined {
  const id = resolveSpecificInventoryLocationId(...candidates);
  if (!id) {
    warnInventoryLocationRequired(kind);
  }
  return id;
}

/** init 库位选项 → 首个真实库位 id（跳过「全部」占位 0）。 */
export function resolveFirstRealLocationIdFromInitOptions(
  opts?: { locationId?: unknown; value?: unknown }[],
): string | undefined {
  return resolveFirstRealLocationIdFromOptionItems(opts ?? []);
}

/** init/LocationOptionDto → 按库位 id 查展示名（侧栏卡片 enrich 用）。 */
export function resolveLocationDisplayNameFromOptions(
  locationId: unknown,
  options: { locationId?: unknown; displayName?: string; value?: unknown; label?: string }[],
): string {
  const idStr = String(locationId ?? '').trim();
  if (!idStr) return '';
  for (const o of options) {
    const oid = String(o.locationId ?? o.value ?? '').trim();
    if (oid === idStr) return String(o.displayName ?? o.label ?? '').trim();
  }
  return '';
}

/** 出库/调拨/退库类侧栏卡片：补状态文案、单号、库位字典（后端未 enrich 时）。 */
export function enrichStockOutQueueCardRow(
  row: Record<string, unknown>,
  options?: {
    sourceOptions?: {
      locationId?: unknown;
      displayName?: string;
      value?: unknown;
      label?: string;
    }[];
    purposeOptions?: {
      locationId?: unknown;
      displayName?: string;
      value?: unknown;
      label?: string;
    }[];
  },
): Record<string, unknown> {
  const statusSort = normalizeStockOutStatusSort(row.statusEnum);
  /** 侧栏制单人/审批人/金额：兼容后端 enrich 的 creatorName/approverName/totalAmount */
  const creator = row.applicantId_dictText || row.creatorName || row.applicantName || '';
  const approver = row.approverId_dictText || row.approverName || '';
  const amount = row.totalAmount ?? row.actualAmountSum;
  return {
    ...row,
    supplyBusNo: row.supplyBusNo ?? row.busNo,
    statusEnum_enumText: stockOutStatusLabel(statusSort ?? row.statusEnum),
    supplierId_dictText: row.supplierId_dictText ?? row.supplierName ?? '',
    applicantId_dictText: creator,
    approverId_dictText: approver,
    totalAmount: amount,
    rejectReason: row.rejectReason ?? row.reason ?? '',
    sourceLocationId_dictText:
      row.sourceLocationId_dictText ??
      resolveLocationDisplayNameFromOptions(row.sourceLocationId, options?.sourceOptions ?? []),
    purposeLocationId_dictText:
      row.purposeLocationId_dictText ??
      resolveLocationDisplayNameFromOptions(row.purposeLocationId, options?.purposeOptions ?? []),
  };
}

/** 为侧栏 cabinet 选项前置「全部」占位（若 init 未带）。 */
export function prependAllLocationsCabinetOption(
  rows: Record<string, unknown>[],
  label: string = INV_ALL_WAREHOUSES_LABEL,
): Record<string, unknown>[] {
  if (rows.some((r) => isAllLocationsFilterOptionValue(r.value ?? r.id))) {
    return rows.map((r) => {
      if (!isAllLocationsFilterOptionValue(r.value ?? r.id)) return r;
      return {
        ...r,
        name: label,
        label,
      };
    });
  }
  return [
    {
      id: INV_ALL_LOCATIONS_FILTER_ID,
      value: INV_ALL_LOCATIONS_FILTER_ID,
      name: label,
      label,
    },
    ...rows,
  ];
}

/** LocationOptionDto / OptionDto 行 → cabinet 行，并对 value=0 强制文案。 */
export function mapLocationOptionsToCabinetRows(
  list: {
    locationId?: number | string;
    displayName?: string;
    label?: string;
    value?: number | string;
  }[],
  allLabel: string = INV_ALL_WAREHOUSES_LABEL,
): Record<string, unknown>[] {
  return list.map((w) => {
    const value = w.locationId ?? w.value;
    const isAll = isAllLocationsFilterOptionValue(value);
    const label = isAll ? allLabel : (w.displayName ?? w.label ?? String(value ?? ''));
    return {
      id: value,
      value,
      name: label,
      label,
    };
  });
}

/** init 后对齐 tableForm 与「全部」option 的 value 类型（避免 ElSelect 显示裸 0）。 */
export function resolveDefaultAllLocationsFilterValue(
  cabinetOptions: Record<string, unknown>[],
): string {
  const allValue = cabinetOptions[0]?.value ?? cabinetOptions[0]?.id;
  if (allValue === undefined || allValue === null) {
    return INV_ALL_LOCATIONS_FILTER_ID;
  }
  return isAllLocationsFilterOptionValue(allValue) ? INV_ALL_LOCATIONS_FILTER_ID : String(allValue);
}

/** init 后默认侧栏源库位：有具体库位时用首项（跳过「全部」占位）。 */
export function resolveDefaultSidebarSourceLocationId(
  cabinetOptions: Record<string, unknown>[],
): number | string | undefined {
  if (cabinetOptions.length === 0) return undefined;
  for (const row of cabinetOptions) {
    const value = row.value ?? row.id;
    if (value === undefined || value === null) continue;
    if (isAllLocationsFilterOptionValue(value)) continue;
    return value as number | string;
  }
  const first = cabinetOptions[0]?.value ?? cabinetOptions[0]?.id;
  return first === undefined || first === null ? undefined : (first as number | string);
}

/** 侧栏 locationId  second arg is optional label override */
export function normalizeSidebarLocationFilterValue(
  value: number | string | undefined | null,
): string {
  if (value === undefined || value === null || String(value).trim() === '' || value === 0) {
    return INV_ALL_LOCATIONS_FILTER_ID;
  }
  return String(value);
}

/** 前端关键字过滤：单据号、制单人/经手人/审批人、库位名等（后端 keyword 不可靠时的兜底）。 */
export function filterWorkbenchListRowsByKeyword<T extends Record<string, unknown>>(
  rows: T[],
  keyword?: string,
): T[] {
  const kw = keyword?.trim().toLowerCase();
  if (!kw) return rows;
  return rows.filter((row) => {
    const parts = [
      row.busNo,
      row.supplyBusNo,
      row.orderBusNo,
      row.purchaseBusNo,
      row.originalBusNo,
      row.applicantName,
      row.applicantId_dictText,
      row.creatorName,
      row.practitionerName,
      row.approverName,
      row.approverId_dictText,
      row.purposeLocationName,
      row.sourceLocationName,
      row.consumingOrganizationName,
      row.purposeLocationId_dictText,
      row.sourceLocationId_dictText,
    ];
    return parts.some((f) => {
      if (f === undefined || f === null) return false;
      const s = String(f).trim();
      return s !== '' && s.toLowerCase().includes(kw);
    });
  });
}

/** init OptionDto / ApiOptionItem → 下拉项（value 一律 string，保留拼音/五笔）。 */
export function mapInitOptionDtoToApiItems(
  list?: { label?: string; value?: unknown; pyStr?: string; wbStr?: string }[],
): { label: string; value: string; pyStr?: string; wbStr?: string }[] {
  return (list ?? []).map((o) => {
    const item: { label: string; value: string; pyStr?: string; wbStr?: string } = {
      label: String(o.label ?? ''),
      value: String(o.value ?? ''),
    };
    const py = String(o.pyStr ?? '').trim();
    const wb = String(o.wbStr ?? '').trim();
    if (py) item.pyStr = py;
    if (wb) item.wbStr = wb;
    return item;
  });
}

/** 单头已选真实库位 → 选品门禁；否则 init 首个真实库位（侧栏「全部」不参与）。 */
export function resolveDocHeadProductPickLocationId(
  headLocationId: unknown,
  initOptions: { locationId?: unknown; value?: unknown }[],
): string | undefined {
  if (isSpecificInventoryLocationId(headLocationId)) {
    const preserved = preserveLocationIdForRequest(headLocationId);
    if (preserved) return preserved;
    return String(headLocationId).trim();
  }
  return resolveFirstRealLocationIdFromOptionItems(initOptions);
}

/** 选品门禁：跳过「全部」占位，取首个真实库位 id（string）。 */
export function resolveFirstRealLocationIdFromOptionItems(
  options: { value?: unknown; locationId?: unknown }[],
): string | undefined {
  for (const o of options) {
    const v = o.locationId ?? o.value;
    if (v === undefined || v === null) continue;
    if (isAllLocationsFilterOptionValue(v)) continue;
    const s = String(v).trim();
    if (s !== '') return s;
  }
  return undefined;
}

/** 侧栏 cabinet 行 → 首个真实库位（跳过 value=0）。 */
export function resolveFirstRealLocationIdFromCabinetRows(
  rows: Record<string, unknown>[],
): number | string | undefined {
  for (const row of rows) {
    const v = row.value ?? row.id;
    if (v === undefined || v === null) continue;
    if (isAllLocationsFilterOptionValue(v)) continue;
    return v as number | string;
  }
  return undefined;
}

/** 库存选品项目类型表名（与后端 productTableName / itemTable 一致）。 */
export const INV_ITEM_TABLE_TYPE_MEDICATION = 'med_medication_definition';
export const INV_ITEM_TABLE_TYPE_DEVICE = 'adm_device_definition';

/** 库存选品项目类型：init label 为空或未返回时的兜底文案。 */
export const INVENTORY_ITEM_TABLE_TYPE_LABELS: Record<string, string> = {
  [INV_ITEM_TABLE_TYPE_MEDICATION]: '药品',
  [INV_ITEM_TABLE_TYPE_DEVICE]: '耗材',
};

/** 项目类型 code → 展示名；未知 code 原样返回。 */
export function resolveInventoryItemTableTypeLabel(value?: unknown): string {
  const v = String(value ?? '').trim();
  if (v === '') return '';
  return INVENTORY_ITEM_TABLE_TYPE_LABELS[v] ?? v;
}

/**
 * 从 options 取默认项目类型：优先 preferred（若在列表中），否则首项，否则 preferred 兜底。
 * 科室单据默认传 {@link INV_ITEM_TABLE_TYPE_DEVICE}。
 */
export function resolveDefaultInventoryItemTableType(
  options?: { value?: unknown }[] | null,
  preferred: string = INV_ITEM_TABLE_TYPE_MEDICATION,
): string {
  const pref = String(preferred ?? '').trim() || INV_ITEM_TABLE_TYPE_MEDICATION;
  for (const o of options ?? []) {
    if (String(o?.value ?? '').trim() === pref) return pref;
  }
  for (const o of options ?? []) {
    const v = String(o?.value ?? '').trim();
    if (v !== '') return v;
  }
  return pref;
}

/**
 * init 项目类型下拉：补全 label；列表为空时返回药品/耗材兜底项。
 * `preferFirst` 可将指定类型排到首位（科室默认耗材）。
 */
export function normalizeInventoryItemTableTypeOptions(
  list?: { label?: string; value?: unknown }[],
  preferFirst?: string,
): { label: string; value: string }[] {
  let mapped = (list ?? [])
    .map((o) => {
      const value = String(o.value ?? '').trim();
      if (value === '') return null;
      const label =
        String(o.label ?? '').trim() || resolveInventoryItemTableTypeLabel(value) || value;
      return { label, value };
    })
    .filter((o): o is { label: string; value: string } => o !== null);
  if (mapped.length === 0) {
    mapped = Object.entries(INVENTORY_ITEM_TABLE_TYPE_LABELS).map(([value, label]) => ({
      label,
      value,
    }));
  }
  const pref = String(preferFirst ?? '').trim();
  if (!pref) return mapped;
  const idx = mapped.findIndex((o) => o.value === pref);
  if (idx <= 0) return mapped;
  const hit = mapped[idx]!;
  return [hit, ...mapped.slice(0, idx), ...mapped.slice(idx + 1)];
}

/**
 * 打印/展示用制单人：与侧栏队列卡片同口径（applicantName → applicantId_dictText → creatorName）。
 * 禁止用当前登录人/打印人冒充。
 */
export function resolveInvDocPreparedByName(source?: null | Record<string, unknown>): string {
  if (!source) {
    return '';
  }
  for (const c of [source.applicantName, source.applicantId_dictText, source.creatorName]) {
    const s = String(c ?? '').trim();
    if (s) {
      return s;
    }
  }
  return '';
}

/**
 * 打印/展示用审批人：与侧栏队列卡片同口径。
 */
export function resolveInvDocApprovedByName(source?: null | Record<string, unknown>): string {
  if (!source) {
    return '';
  }
  for (const c of [
    source.approverName,
    source.approverId_dictText,
    source.auditBy_dictText,
    source.reviewerId_dictText,
  ]) {
    const s = String(c ?? '').trim();
    if (s) {
      return s;
    }
  }
  return '';
}

/**
 * 打印「制单时间」：优先实际审批时间（与侧栏卡片同字段口径），禁止用当前打印时刻冒充。
 * 无审批时间时再回落制单/申请时间；皆无则空串。
 */
export function resolveInvDocPrintApplyTimeText(params?: {
  approvalTime?: null | string;
  applyTime?: null | string;
  queueCardRow?: null | Record<string, unknown>;
}): string {
  const row = params?.queueCardRow;
  for (const c of [
    params?.approvalTime,
    row?.approvalTime,
    row?.auditTime,
    row?.approveTime,
    row?.reviewTime,
    params?.applyTime,
    row?.applyTime,
  ]) {
    const s = String(c ?? '').trim();
    if (!s) continue;
    return s.includes('T') ? s.replace('T', ' ') : s;
  }
  return '';
}

/** 登录用户姓名，供经手人下拉兜底展示。 */
export function resolveLoginPractitionerLabelFromUserInfo(
  userInfo: null | Record<string, unknown> | undefined,
): string {
  if (!userInfo) return '';
  const ext = (userInfo.extraProperties ?? {}) as Record<string, unknown>;
  for (const c of [
    userInfo.practitionerName,
    userInfo.practitionerName_dictText,
    userInfo.realName,
    userInfo.nickName,
    userInfo.name,
    userInfo.username,
    userInfo.userName,
    ext.realName,
    ext.RealName,
    ext.nickName,
    ext.NickName,
    ext.practitionerName,
    ext.PractitionerName,
    ext.userName,
    ext.UserName,
  ]) {
    const s = String(c ?? '').trim();
    if (s !== '') return s;
  }
  return '';
}

/** 单据单头经手人下拉：init 选项 + 详情 pin + 当前登录人（避免 ElSelect 只显示 id）。 */
export function buildInventoryDocPractitionerSelectOptions(
  baseOptions: { label?: string; value?: unknown; pyStr?: string; wbStr?: string }[],
  extras?: {
    pinned?: { label?: string; value?: unknown; pyStr?: string; wbStr?: string }[];
    userInfo?: null | Record<string, unknown> | undefined;
  },
): { label: string; value: string; pyStr?: string; wbStr?: string }[] {
  const loginId = resolveLoginPractitionerIdForRequest(extras?.userInfo);
  const loginLabel = resolveLoginPractitionerLabelFromUserInfo(extras?.userInfo);
  const pinnedExtras = (extras?.pinned ?? []).map((p) => ({
    id: p.value,
    label: p.label,
    pyStr: p.pyStr,
    wbStr: p.wbStr,
  }));
  return mergePractitionerSelectOptions(baseOptions, [
    ...pinnedExtras,
    { id: loginId, label: loginLabel },
  ]);
}

/** 合并经手人下拉（去重）；extras 用于注入当前登录人或详情回显 id，并可覆盖 base 中的 id 占位 label。 */
export function mergePractitionerSelectOptions(
  baseOptions: { label?: string; value?: unknown; pyStr?: string; wbStr?: string }[],
  extras?: { id?: unknown; label?: string; pyStr?: string; wbStr?: string }[],
): { label: string; value: string; pyStr?: string; wbStr?: string }[] {
  const out: { label: string; value: string; pyStr?: string; wbStr?: string }[] = [];
  const indexByValue = new Map<string, number>();

  const isWeakPractitionerLabel = (value: string, label: string) => label === '' || label === value;

  const pushOrUpgrade = (
    value: unknown,
    label?: string,
    allowWeakLabel = true,
    pyStr?: string,
    wbStr?: string,
  ) => {
    const v = String(value ?? '').trim();
    if (v === '') return;
    const lbl = String(label ?? '').trim();
    const strongLabel = lbl !== '' && lbl !== v ? lbl : '';
    const py = String(pyStr ?? '').trim();
    const wb = String(wbStr ?? '').trim();
    const existingIdx = indexByValue.get(v);
    if (existingIdx !== undefined) {
      const existing = out[existingIdx];
      if (isWeakPractitionerLabel(v, existing.label) && strongLabel !== '') {
        existing.label = strongLabel;
      }
      // 补齐拼音/五笔（登录人/pin 常无码，以 init 为准）
      if (!existing.pyStr && py) existing.pyStr = py;
      if (!existing.wbStr && wb) existing.wbStr = wb;
      return;
    }
    if (!allowWeakLabel && strongLabel === '') return;
    indexByValue.set(v, out.length);
    const row: { label: string; value: string; pyStr?: string; wbStr?: string } = {
      label: strongLabel !== '' ? strongLabel : v,
      value: v,
    };
    if (py) row.pyStr = py;
    if (wb) row.wbStr = wb;
    out.push(row);
  };

  for (const o of baseOptions) {
    pushOrUpgrade(o.value, o.label, true, o.pyStr, o.wbStr);
  }
  // extras（登录人/详情 pin）允许弱 label，避免下拉整表为空
  for (const e of extras ?? []) {
    pushOrUpgrade(e.id, e.label, true, e.pyStr, e.wbStr);
  }
  return out;
}

/** 新建草稿默认经手人：优先当前登录人（须在 options 内），否则首项。 */
export function resolveDefaultPractitionerIdForSelect(
  options: { value?: unknown }[],
  userInfo: null | Record<string, unknown> | undefined,
): string | undefined {
  const loginId = resolveLoginPractitionerIdForRequest(userInfo);
  if (loginId && options.some((o) => String(o.value ?? '').trim() === loginId)) {
    return loginId;
  }
  const first = options[0]?.value;
  if (first === undefined || first === null || String(first).trim() === '') {
    return undefined;
  }
  return String(first);
}

/** 经手人 ElSelect：详情 id 与 options.value 类型对齐，便于 filterable 检索。 */
export function coercePractitionerIdForSelect(
  id: unknown,
  options: { value?: unknown; label?: string }[],
): string | undefined {
  if (id === undefined || id === null || String(id).trim() === '') return undefined;
  const idStr = String(id).trim();
  for (const o of options) {
    if (String(o.value ?? '').trim() === idStr) return String(o.value);
  }
  return idStr;
}

/** init 经手人/药师/库管等多源合并，并注入当前登录人以便 ElSelect 可检索。 */
export function mapInventoryPractitionerInitOptions(
  lists: ({ label?: string; value?: unknown }[] | undefined)[],
  userInfo: null | Record<string, unknown> | undefined,
): { label: string; value: string }[] {
  const merged: { label?: string; value?: unknown }[] = [];
  for (const list of lists) {
    if (list?.length) merged.push(...list);
  }
  const loginId = resolveLoginPractitionerIdForRequest(userInfo);
  const loginLabel = resolveLoginPractitionerLabelFromUserInfo(userInfo);
  return mergePractitionerSelectOptions(mapInitOptionDtoToApiItems(merged), [
    { id: loginId, label: loginLabel },
  ]);
}

/** 写入 POST 请求体的库位 id（雪花 Long 禁止 Number()）。 */
export function preserveLocationIdForRequest(raw: unknown): string | undefined {
  return preserveApiIdForRequest(raw);
}

/** 侧栏列表查询：「全部库位」→ 0，其余保持 string（避免 JSON number 丢精度）。 */
export function coerceWorkbenchListLocationIdForApi(
  locationId?: number | string | null,
): number | string {
  const normalized = resolveWorkbenchListSourceLocationId(locationId);
  return normalized === INV_ALL_LOCATIONS_FILTER_ID ? 0 : normalized;
}

/** init 下拉 → 侧栏 cabinet（前置「全部」占位）。 */
export function mapInitOptionsToCabinetRowsWithAll(
  list: { label?: string; value?: string | number }[],
  allLabel: string = INV_ALL_WAREHOUSES_LABEL,
): Record<string, unknown>[] {
  const rows = mapLocationOptionsToCabinetRows(
    list.map((o) => ({ value: o.value, label: o.label })),
    allLabel,
  );
  return prependAllLocationsCabinetOption(rows, allLabel);
}
