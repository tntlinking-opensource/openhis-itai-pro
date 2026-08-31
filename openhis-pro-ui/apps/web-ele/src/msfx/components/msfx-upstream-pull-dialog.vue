<!--
  码上放心上游出库单拉单弹窗（INB）：勾选 → 下一步拉明细 → 确认页核对后生成。
  扫码反查命中后自动勾选；药品/批号/码在确认页核对，本页不再单独预览明细。
  约束：一张进货单仅同一上游企业（同供应商可多选合并；跨供应商须分单）。
  上游企业优先：本单对照 / 已维护对照表 / 手填 ent_id；平台名搜索仅作辅助。
-->
<script setup lang="ts">
import type { MsfxEntInfoRowDto } from '#/api/msfx/box-enterprise';
import type { MsfxSupplierMapItem } from '#/api/msfx/supplier-map';
import type { MsfxUpstreamBillDetailDto, MsfxUpstreamBillRowDto } from '#/api/msfx/types';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import {
  ElAlert,
  ElButton,
  ElDatePicker,
  ElDialog,
  ElInput,
  ElMessage,
  ElOption,
  ElOptionGroup,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { listMsfxEntInfoApi } from '#/api/msfx/box-enterprise';
import {
  getMsfxUpstreamBillDetailApi,
  listMsfxUpstreamBillsApi,
  resolveMsfxUpstreamByScanApi,
} from '#/api/msfx/inbound-upstream';
import { getMsfxSupplierMapBySupplierIdApi, pageMsfxSupplierMapApi } from '#/api/msfx/supplier-map';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

defineOptions({ name: 'MsfxUpstreamPullDialog' });

const props = withDefaults(
  defineProps<{
    /** 本单已选 HIS 供应商 id */
    hisSupplierId?: string | number | null;
    /** 本单供应商显示名 */
    hisSupplierName?: string | null;
    /** 状态条/对照已带回的 ent_id */
    preferredFromEntId?: string | null;
    /** 已带回的上游企业名称（展示用，避免只显示 UUID） */
    preferredFromEntName?: string | null;
    /**
     * 是否用 preferred 硬锁上游企业。
     * 有进货明细时应 true（防混供应商）；明细清空后传 false，便于换供应商再拉。
     */
    lockPreferredSupplier?: boolean;
  }>(),
  {
    hisSupplierId: '',
    hisSupplierName: '',
    preferredFromEntId: '',
    preferredFromEntName: '',
    lockPreferredSupplier: true,
  },
);

const visible = defineModel<boolean>({ default: false });

const emit = defineEmits<{
  apply: [
    payload: {
      /** 首个导入账（兼容）；完整见 importIds */
      importId: number | string;
      /** 多选时全部导入账 */
      importIds: Array<number | string>;
      fromEntId?: string;
      fromEntName?: string;
      /** 单号；多选时顿号拼接 */
      billCode?: string;
      /** 上游药品明细行（生成进货行用；多单合并） */
      drugRows: MsfxUpstreamBillRowDto[];
      traceCodes: string[];
    },
  ];
}>();

const loading = ref(false);
const detailLoading = ref(false);
const rows = ref<MsfxUpstreamBillRowDto[]>([]);
const total = ref(0);
/** 表格勾选的上游单据 */
const selectedRows = ref<MsfxUpstreamBillRowDto[]>([]);
/** billCode → 已拉明细缓存（下一步复用，避免重复请求） */
const detailCache = ref<Record<string, MsfxUpstreamBillDetailDto>>({});
const scanCode = ref('');
/** 用于切换企业筛选时清空勾选、扫码后自动勾选 */
const billTableRef = ref<{
  clearSelection?: () => void;
  toggleRowSelection?: (row: MsfxUpstreamBillRowDto, selected?: boolean) => void;
} | null>(null);

const mapOptions = ref<MsfxSupplierMapItem[]>([]);
const remoteEntOptions = ref<MsfxEntInfoRowDto[]>([]);
const entLoading = ref(false);
const mapHint = ref('');
let entSearchTimer: ReturnType<typeof setTimeout> | undefined;

const query = reactive({
  billBeginDate: '',
  billEndDate: '',
  billCode: '',
  fromUserId: '',
  pageNo: 1,
  pageSize: 20,
});

function drugRowName(row: MsfxUpstreamBillRowDto): string {
  return (
    String(row.drugName ?? '').trim() ||
    String(row.physicName ?? '').trim() ||
    String(row.prodName ?? '').trim() ||
    '—'
  );
}

const selectedCount = computed(() => selectedRows.value.length);

const canApply = computed(() => selectedCount.value > 0 && !detailLoading.value);

/** 对照下拉按 msfxEntId 去重（多 HIS 供应商可对同一上游企业） */
const uniqueMapOptions = computed(() => {
  const byEnt = new Map<string, MsfxSupplierMapItem>();
  for (const m of mapOptions.value) {
    const entId = String(m.msfxEntId ?? '').trim();
    if (!entId) continue;
    const existing = byEnt.get(entId);
    if (!existing) {
      byEnt.set(entId, { ...m, msfxEntId: entId });
      continue;
    }
    const his = String(m.supplierName ?? '').trim();
    const entName = readableEntName(m.msfxEntName, entId);
    const existHis = String(existing.supplierName ?? '').trim();
    const existEnt = readableEntName(existing.msfxEntName, entId);
    // 优先保留「院内名与平台名实质不同」的对照，便于下拉说明
    if (
      his &&
      entName &&
      !namesLookSame(his, entName) &&
      (!existHis || namesLookSame(existHis, existEnt))
    ) {
      existing.supplierName = his;
      existing.msfxEntName = entName;
    } else {
      if (his && !existHis) existing.supplierName = his;
      if (entName && !existEnt) existing.msfxEntName = entName;
    }
    if (!existing.supplierId && m.supplierId) {
      existing.supplierId = m.supplierId;
    }
  }
  return [...byEnt.values()];
});

/** 平台搜索结果去掉已在对照中的 ent_id，避免两组重复 */
const filteredRemoteEntOptions = computed(() => {
  const mapped = new Set(uniqueMapOptions.value.map((m) => String(m.msfxEntId ?? '').trim()));
  const seen = new Set<string>();
  const out: MsfxEntInfoRowDto[] = [];
  for (const opt of remoteEntOptions.value) {
    const id = String(opt.entId ?? '').trim();
    if (!id || mapped.has(id) || seen.has(id)) continue;
    seen.add(id);
    out.push(opt);
  }
  return out;
});

const supplierHint = computed(() => {
  const name = String(props.hisSupplierName ?? '').trim();
  const id = String(props.hisSupplierId ?? '').trim();
  if (name) return name;
  if (id) return `供应商 #${id}`;
  return '';
});

watch(visible, async (v) => {
  if (!v) return;
  selectedRows.value = [];
  if (!query.billBeginDate || !query.billEndDate) {
    const end = new Date();
    const begin = new Date();
    begin.setDate(end.getDate() - 14);
    query.billEndDate = formatYmd(end);
    query.billBeginDate = formatYmd(begin);
  }
  await bootstrapFromHisSupplier();
  void fetchList({ quietEmpty: true });
});

watch(
  () => query.fromUserId,
  () => {
    if (!visible.value) return;
    clearBillSelection();
  },
);

function formatYmd(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

function pickErrMsg(error: unknown): string {
  if (error && typeof error === 'object') {
    const e = error as { message?: string; response?: { data?: { message?: string } } };
    const m = e.response?.data?.message ?? e.message;
    if (m) return String(m);
  }
  return '请求失败';
}

/** 打开时：对照表 + 本单 preferred → 预填 fromUserId */
async function bootstrapFromHisSupplier() {
  mapHint.value = '';
  remoteEntOptions.value = [];
  await loadMapOptions();

  const supplierId = String(props.hisSupplierId ?? '').trim();
  const hisName = String(props.hisSupplierName ?? '').trim();
  let preferred = String(props.preferredFromEntId ?? '').trim();
  let preferredName = String(props.preferredFromEntName ?? '').trim();

  // HIS 供应商主键被误当作 ent_id：丢弃，避免下拉出现 UUID「乱码」
  if (preferred && supplierId && preferred === supplierId) {
    preferred = '';
    preferredName = '';
  }
  if (looksLikeOpaqueId(preferredName) || preferredName === preferred) {
    preferredName = '';
  }

  let entId = preferred;
  let entName = preferredName;

  if (supplierId) {
    try {
      const res = await getMsfxSupplierMapBySupplierIdApi({ supplierId });
      const hit = res.data;
      const mapped = String(hit?.msfxEntId ?? '').trim();
      if (mapped) {
        entId = mapped;
        const mappedName = readableEntName(hit?.msfxEntName, mapped);
        if (mappedName) entName = mappedName;
        else if (!entName && hisName) entName = hisName;
        mapHint.value = entName
          ? `已按本单供应商对照填入：${entName}`
          : '已按本单供应商对照填入上游企业';
        ensureMapOption({
          ...hit,
          msfxEntId: mapped,
          msfxEntName: entName || undefined,
          supplierName: String(hit?.supplierName ?? '').trim() || hisName || undefined,
        });
      } else if (!preferred) {
        mapHint.value =
          '本单供应商尚未维护码上放心对照；可手填 ent_id，或到「码上放心 → 供应商对照」维护';
      }
    } catch {
      if (!preferred) {
        mapHint.value = '对照查询失败时可手填 ent_id，或留空后按日期查询';
      }
    }
  } else if (!preferred) {
    mapHint.value = '未选 HIS 供应商：可留空查全部，或手填 / 从对照中选择上游企业';
  }

  // 对照表里已有同 ent_id：补名称
  if (entId && !entName) {
    const fromMap = mapOptions.value.find((m) => String(m.msfxEntId) === entId);
    const n = readableEntName(fromMap?.msfxEntName, entId);
    if (n) entName = n;
    else if (hisName) entName = hisName;
  }

  if (entId) {
    query.fromUserId = entId;
    ensureMapOption({
      msfxEntId: entId,
      msfxEntName: entName || undefined,
      supplierName: hisName || (entName ? undefined : '本单已选上游'),
    });
  }
}

/** 长 hex / UUID：不可当作企业名展示 */
function looksLikeOpaqueId(v: unknown): boolean {
  const s = String(v ?? '').trim();
  if (!s) return false;
  if (/[\u4e00-\u9fff]/.test(s)) return false;
  return /^[0-9a-fA-F-]{16,}$/.test(s);
}

function readableEntName(name: unknown, entId: string): string {
  const n = String(name ?? '').trim();
  if (!n || n === entId || looksLikeOpaqueId(n)) return '';
  return n;
}

/** 比企业名是否实质上同一家（全角/半角括号、空格不敏感） */
function namesLookSame(a: string, b: string): boolean {
  const left = normalizeEntLabel(a);
  const right = normalizeEntLabel(b);
  return !!left && left === right;
}

function normalizeEntLabel(name: string): string {
  return String(name ?? '')
    .normalize('NFKC')
    .replace(/\s+/g, '')
    .trim();
}

/** 无中文名时才露出短 ID，避免下拉里一排哈希 */
function shortEntId(id: string): string {
  const s = String(id ?? '').trim();
  if (s.length <= 12) return s;
  return `${s.slice(0, 4)}…${s.slice(-4)}`;
}

function ensureMapOption(item: Partial<MsfxSupplierMapItem>) {
  const entId = String(item.msfxEntId ?? '').trim();
  if (!entId) return;
  const entName = readableEntName(item.msfxEntName, entId);
  const supplierName = String(item.supplierName ?? '').trim();
  const existing = mapOptions.value.find((m) => String(m.msfxEntId) === entId);
  if (existing) {
    // 补全可读名，不新增重复项
    if (entName && !readableEntName(existing.msfxEntName, entId)) {
      existing.msfxEntName = entName;
    }
    if (supplierName && !String(existing.supplierName ?? '').trim()) {
      existing.supplierName = supplierName;
    }
    return;
  }
  mapOptions.value = [
    {
      supplierId: item.supplierId,
      supplierName: supplierName || undefined,
      msfxEntId: entId,
      msfxEntName: entName || undefined,
      msfxRefEntId: item.msfxRefEntId,
    },
    ...mapOptions.value,
  ];
}

async function loadMapOptions() {
  try {
    const all: MsfxSupplierMapItem[] = [];
    let pageNo = 1;
    const pageSize = 100;
    for (;;) {
      const res = await pageMsfxSupplierMapApi({ pageNo, pageSize });
      const parsed = parsePagedRowsFromApiData<MsfxSupplierMapItem>(res.data);
      all.push(...parsed.content);
      const total = Number(parsed.totalCount ?? parsed.content.length);
      if (parsed.content.length < pageSize || all.length >= total) break;
      pageNo += 1;
      if (pageNo > 50) break;
    }
    mapOptions.value = all
      .filter((r) => String(r.msfxEntId ?? '').trim())
      .map((r) => {
        const entId = String(r.msfxEntId ?? '').trim();
        return {
          ...r,
          msfxEntId: entId,
          msfxEntName: readableEntName(r.msfxEntName, entId) || undefined,
        };
      });
  } catch {
    mapOptions.value = [];
  }
}

/** 平台名搜索（辅助；失败不阻断主流程）。 */
function remoteSearchEnt(keyword: string) {
  if (entSearchTimer) clearTimeout(entSearchTimer);
  const q = String(keyword ?? '').trim();
  if (q.length < 2) {
    remoteEntOptions.value = [];
    return;
  }
  entSearchTimer = setTimeout(() => {
    void doSearchEnt(q);
  }, 300);
}

async function doSearchEnt(entName: string) {
  entLoading.value = true;
  try {
    const res = await listMsfxEntInfoApi({ entName });
    const data = res.data;
    remoteEntOptions.value = data?.rows ?? [];
    // 平台失败：不打断主流程（对照/手填仍可用）
    if (data && data.responseSuccess === false && remoteEntOptions.value.length === 0) {
      ElMessage.info(data.msgInfo || '平台企业名搜索暂不可用，请用手填 ent_id 或「已维护对照」');
    }
  } catch {
    remoteEntOptions.value = [];
    ElMessage.info('平台企业名搜索暂不可用，请用手填 ent_id 或「已维护对照」');
  } finally {
    entLoading.value = false;
  }
}

async function fetchList(opts?: { quietEmpty?: boolean }) {
  loading.value = true;
  selectedRows.value = [];
  try {
    const res = await listMsfxUpstreamBillsApi({
      billBeginDate: query.billBeginDate || undefined,
      billEndDate: query.billEndDate || undefined,
      billCode: String(query.billCode ?? '').trim() || undefined,
      fromUserId: String(query.fromUserId ?? '').trim() || undefined,
      pageNo: query.pageNo,
      pageSize: query.pageSize,
    });
    const data = res.data;
    rows.value = data?.rows ?? [];
    const tn = data?.totalNum;
    total.value = tn !== undefined && tn !== null ? Number(tn) : rows.value.length;
    if (data && data.responseSuccess === false) {
      ElMessage.warning(data.msgInfo || data.msgCode || '上游出库单查询失败');
    } else if (rows.value.length === 0 && !opts?.quietEmpty) {
      ElMessage.info(
        data?.msgInfo || '未查到上游出库单：核对日期与 ent_id；也可留空上游企业、或用下方扫码反查',
      );
    }
  } catch (error) {
    rows.value = [];
    total.value = 0;
    ElMessage.error(pickErrMsg(error));
  } finally {
    loading.value = false;
  }
}

function handleQuery() {
  query.pageNo = 1;
  void fetchList();
}

function handlePageChange(page: number) {
  query.pageNo = page;
  void fetchList();
}

function clearFromEnt() {
  // 有明细且已对照：不允许清空成「查全部」，避免勾到其他供应商
  const preferred = String(props.preferredFromEntId ?? '').trim();
  if (preferred && props.lockPreferredSupplier) {
    query.fromUserId = preferred;
    ElMessage.info('本单已有明细且已对照上游企业，已恢复为该企业；换供应商请先清空明细并改单头');
    return;
  }
  query.fromUserId = '';
}

function handleSelectionChange(selection: MsfxUpstreamBillRowDto[]) {
  selectedRows.value = selection;
}

/** 点击行切换勾选（点复选框列时不处理，避免连点两次） */
function handleBillRowClick(row: MsfxUpstreamBillRowDto, column: { type?: string }) {
  if (column?.type === 'selection') return;
  if (!isBillSelectable(row)) return;
  billTableRef.value?.toggleRowSelection?.(row);
}

function rowFromEntId(row: MsfxUpstreamBillRowDto | null | undefined): string {
  return String(row?.fromEntId ?? '').trim();
}

/** 本单对照优先（仅 lock 时），其次当前查询条件（用于禁选/勾选校验） */
function lockedSupplierEntId(): string {
  const preferred = String(props.preferredFromEntId ?? '').trim();
  if (preferred && props.lockPreferredSupplier) return preferred;
  return String(query.fromUserId ?? '').trim();
}

/**
 * 一张进货单只允许同一上游企业：勾选间须一致，且须与本单对照/筛选一致。
 * 只依据列表行 fromEntId（明细接口字段名常与 listupout 不一致，不作二次硬拦）。
 * @returns 错误文案；通过则 null
 */
function assertSelectionSameSupplier(selection: MsfxUpstreamBillRowDto[]): string | null {
  if (!selection.length) return null;
  const byEnt = new Map<string, string>();
  for (const row of selection) {
    const id = rowFromEntId(row);
    const bill = String(row.billCode ?? '').trim() || '（无单号）';
    if (!id) {
      return `上游单 ${bill} 缺少上游企业，无法确认是否同一供应商`;
    }
    if (!byEnt.has(id)) {
      byEnt.set(id, String(row.fromEntName ?? '').trim() || id);
    }
  }
  if (byEnt.size > 1) {
    return `不可同时勾选多家供应商的上游单（${[...byEnt.values()].join('、')}）。请按供应商分别拉单，或另建进货单`;
  }
  const selectedEnt = [...byEnt.keys()][0]!;
  const locked = lockedSupplierEntId();
  if (locked && selectedEnt !== locked) {
    const his = String(props.hisSupplierName ?? '').trim();
    if (props.lockPreferredSupplier) {
      return his
        ? `所选上游企业与本单供应商「${his}」不一致。请勾选该供应商的出库单，或先清空明细后更换单头供应商再拉`
        : '所选上游企业与本单已对照的上游企业不一致';
    }
  }
  const filtered = String(query.fromUserId ?? '').trim();
  if (filtered && selectedEnt !== filtered) {
    return '所选上游单与当前筛选的上游企业不一致，请重新查询后再勾选';
  }
  return null;
}

/** 表格多选：锁定本单企业后禁选其他；未锁定时首勾企业锁定其余 */
function isBillSelectable(row: MsfxUpstreamBillRowDto): boolean {
  const id = rowFromEntId(row);
  if (!id) return false;
  const locked = lockedSupplierEntId();
  if (locked) return id === locked;
  const first = selectedRows.value.find((r) => rowFromEntId(r));
  if (!first) return true;
  return id === rowFromEntId(first);
}

function clearBillSelection() {
  selectedRows.value = [];
  billTableRef.value?.clearSelection?.();
}

/**
 * 扫码/列表中可自动勾选的单据：须有企业 id，且与本单锁定企业一致；多家时只勾同一家。
 *
 * @param list 当前表格行
 * @returns 应勾选的行
 */
function pickAutoSelectRows(list: MsfxUpstreamBillRowDto[]): MsfxUpstreamBillRowDto[] {
  const locked = lockedSupplierEntId();
  const selectable = list.filter((row) => {
    const id = rowFromEntId(row);
    if (!id) return false;
    if (locked) return id === locked;
    return true;
  });
  if (selectable.length === 0) return [];
  const ent = rowFromEntId(selectable[0]!);
  return selectable.filter((row) => rowFromEntId(row) === ent);
}

/** 程序勾选表格行（等 DOM 更新后再 toggle，否则 ref 对不上） */
async function applyTableSelection(bills: MsfxUpstreamBillRowDto[]) {
  await nextTick();
  const table = billTableRef.value;
  table?.clearSelection?.();
  for (const row of bills) {
    table?.toggleRowSelection?.(row, true);
  }
  if (!table) {
    selectedRows.value = bills;
  }
}

/** 拉取单张上游明细（带缓存） */
async function loadBillDetail(
  row: MsfxUpstreamBillRowDto,
): Promise<MsfxUpstreamBillDetailDto | null> {
  const billCode = String(row.billCode ?? '').trim();
  if (!billCode) {
    ElMessage.warning('上游单据号为空');
    return null;
  }
  const fromRef = String(row.fromRefUserId ?? '').trim();
  if (!fromRef) {
    ElMessage.warning(`上游单 ${billCode} 缺少 fromRefUserId，无法拉明细`);
    return null;
  }
  const cached = detailCache.value[billCode];
  if (cached?.importId) {
    return cached;
  }
  const res = await getMsfxUpstreamBillDetailApi({
    billCode,
    fromRefUserId: fromRef,
  });
  const data = res.data ?? null;
  if (data?.importId) {
    detailCache.value = { ...detailCache.value, [billCode]: data };
  }
  return data;
}

async function handleScanResolve() {
  const code = String(scanCode.value ?? '').trim();
  if (!code) {
    ElMessage.warning('请输入或扫描追溯码');
    return;
  }
  loading.value = true;
  let autoRows: MsfxUpstreamBillRowDto[] = [];
  try {
    const res = await resolveMsfxUpstreamByScanApi({ code });
    rows.value = res.data?.rows ?? [];
    total.value = rows.value.length;
    query.pageNo = 1;
    selectedRows.value = [];
    if (rows.value.length === 0) {
      ElMessage.warning('未反查到上游单');
      return;
    }
    autoRows = pickAutoSelectRows(rows.value);
    if (autoRows.length === 0) {
      ElMessage.warning(
        '反查到上游单，但与本单供应商不一致，无法勾选。可清空明细后换供应商，或改上游企业筛选',
      );
      return;
    }
    ElMessage.success(
      autoRows.length === 1
        ? '已勾选该上游单，点「下一步」核对药品后生成'
        : `已勾选 ${autoRows.length} 张上游单，点「下一步」核对药品后生成`,
    );
  } catch (error) {
    ElMessage.error(pickErrMsg(error));
  } finally {
    loading.value = false;
  }
  if (autoRows.length > 0) {
    await applyTableSelection(autoRows);
  }
}

/** 多选批量加载明细并套用（仅允许同一上游企业） */
async function handleApply() {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请勾选至少一张上游单');
    return;
  }
  const selectionErr = assertSelectionSameSupplier(selectedRows.value);
  if (selectionErr) {
    ElMessage.warning(selectionErr);
    return;
  }
  detailLoading.value = true;
  try {
    const importIds: Array<number | string> = [];
    const drugRows: MsfxUpstreamBillRowDto[] = [];
    const codeSet = new Set<string>();
    const billCodes: string[] = [];
    let fromEntId: string | undefined;
    let fromEntName: string | undefined;
    let packRemain = 0;
    const expandHints: string[] = [];

    for (const row of selectedRows.value) {
      const billCode = String(row.billCode ?? '').trim();
      const listEnt = rowFromEntId(row);
      const detail = await loadBillDetail(row);
      if (!detail?.importId) {
        ElMessage.warning(`上游单 ${billCode || '（无单号）'} 未生成导入账，已跳过`);
        continue;
      }
      const hint = String(detail.expandHint ?? '').trim();
      if (hint) {
        expandHints.push(hint);
      }
      importIds.push(detail.importId);
      if (billCode) billCodes.push(billCode);
      for (const r of detail.rows ?? []) {
        // 明细头企业字段可能与列表不一致：套用时回填列表已校验的 fromEnt
        if (listEnt && !String(r.fromEntId ?? '').trim()) {
          r.fromEntId = listEnt;
        }
        if (!String(r.fromEntName ?? '').trim() && row.fromEntName) {
          r.fromEntName = row.fromEntName;
        }
        drugRows.push(r);
        for (const c of r.traceCodes ?? []) {
          const t = String(c ?? '').trim();
          if (t) codeSet.add(t);
        }
        packRemain += (r.packTraceCodes ?? []).filter((c) => String(c ?? '').trim()).length;
      }
      if (!fromEntId) {
        // 以列表行企业为准（已与本单对照/筛选校验），避免明细 ent_send_id 等字段误拦
        fromEntId = listEnt || undefined;
        fromEntName =
          String(row.fromEntName ?? detail.rows?.[0]?.fromEntName ?? '').trim() || undefined;
      }
    }

    if (importIds.length === 0 || (drugRows.length === 0 && codeSet.size === 0)) {
      ElMessage.warning(
        expandHints[0] ||
          (packRemain > 0
            ? `上游为中大码 ${packRemain} 个，将随正式入库上报`
            : '所选上游单无可套用的药品或追溯码'),
      );
      return;
    }
    if (packRemain > 0) {
      ElMessage.info(
        expandHints[0] ||
          `含 ${packRemain} 个中大码未展开为小码，将随正式入库上报。已套用可写码 ${codeSet.size} 个`,
      );
    }

    emit('apply', {
      importId: importIds[0]!,
      importIds,
      fromEntId,
      fromEntName,
      billCode: billCodes.join('、'),
      drugRows,
      traceCodes: [...codeSet],
    });
    visible.value = false;
  } catch (error) {
    ElMessage.error(pickErrMsg(error));
  } finally {
    detailLoading.value = false;
  }
}

function mapOptionLabel(m: MsfxSupplierMapItem): string {
  const entId = String(m.msfxEntId ?? '').trim();
  const his = String(m.supplierName ?? '').trim();
  const entName = readableEntName(m.msfxEntName, entId);
  if (his && entName && !namesLookSame(his, entName)) {
    return `${his}（平台：${entName}）`;
  }
  if (his) return his;
  if (entName) return entName;
  return entId ? `未命名企业（${shortEntId(entId)}）` : '未命名上游';
}

function remoteEntOptionLabel(opt: MsfxEntInfoRowDto): string {
  const entId = String(opt.entId ?? '').trim();
  const name = readableEntName(opt.entName, entId);
  if (name) return name;
  return entId ? `未命名企业（${shortEntId(entId)}）` : '未命名';
}

/** 表格单元格展示：压成单行，避免换行叠字 */
function cellText(v: unknown): string {
  return String(v ?? '')
    .replace(/[\r\n\t]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}
</script>

<template>
  <ElDialog
    v-model="visible"
    title="码上放心 · 上游出库单"
    width="980px"
    class="msfx-upstream-pull-dialog"
    destroy-on-close
    append-to-body
  >
    <ElAlert
      v-if="mapHint || supplierHint"
      class="msfx-up-alert"
      type="info"
      :closable="false"
      show-icon
    >
      <template v-if="supplierHint">
        本单供应商：<strong>{{ supplierHint }}</strong>
        <span v-if="mapHint"> · {{ mapHint }}</span>
        <span v-if="!lockPreferredSupplier">
          · 明细为空，可清空上游企业筛选或改单头后换供应商拉单</span
        >
      </template>
      <template v-else>{{ mapHint }}</template>
    </ElAlert>

    <div class="msfx-up-toolbar">
      <div class="msfx-up-field">
        <span class="msfx-up-label">开单起</span>
        <ElDatePicker
          v-model="query.billBeginDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="开始"
          class="msfx-up-control msfx-up-date"
        />
      </div>
      <div class="msfx-up-field">
        <span class="msfx-up-label">开单止</span>
        <ElDatePicker
          v-model="query.billEndDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="结束"
          class="msfx-up-control msfx-up-date"
        />
      </div>
      <div class="msfx-up-field">
        <span class="msfx-up-label">单号</span>
        <ElInput
          v-model="query.billCode"
          clearable
          class="msfx-up-control msfx-up-code"
          placeholder="上游单号"
          @keyup.enter="handleQuery"
        />
      </div>
      <div class="msfx-up-field msfx-up-field-grow">
        <span class="msfx-up-label">上游企业</span>
        <ElSelect
          v-model="query.fromUserId"
          clearable
          filterable
          remote
          allow-create
          default-first-option
          reserve-keyword
          :remote-method="remoteSearchEnt"
          :loading="entLoading"
          class="msfx-up-control msfx-up-ent"
          placeholder="选择上游企业"
        >
          <ElOptionGroup v-if="uniqueMapOptions.length" label="本院已对照">
            <ElOption
              v-for="m in uniqueMapOptions"
              :key="`map-${m.msfxEntId}`"
              :label="mapOptionLabel(m)"
              :value="String(m.msfxEntId)"
            />
          </ElOptionGroup>
          <ElOptionGroup v-if="filteredRemoteEntOptions.length" label="按名称搜索">
            <ElOption
              v-for="opt in filteredRemoteEntOptions"
              :key="`remote-${opt.entId}`"
              :label="remoteEntOptionLabel(opt)"
              :value="String(opt.entId ?? '')"
              :disabled="!opt.entId"
            />
          </ElOptionGroup>
        </ElSelect>
      </div>
      <div class="msfx-up-actions">
        <ElButton type="primary" :loading="loading" @click="handleQuery">查询</ElButton>
        <ElButton @click="clearFromEnt">清空企业</ElButton>
      </div>
    </div>

    <div class="msfx-up-scan">
      <ElInput
        v-model="scanCode"
        clearable
        class="msfx-up-scan-input"
        placeholder="扫码反查上游单"
        @keyup.enter="handleScanResolve"
      />
      <ElButton :loading="loading" @click="handleScanResolve">扫码反查</ElButton>
      <span class="msfx-up-hint"
        >扫码成功会自动勾选。按日期查默认近 14 天，更早单据请改开单起或填单号</span
      >
    </div>

    <ElTable
      ref="billTableRef"
      v-loading="loading"
      class="msfx-up-table"
      :data="rows"
      height="360"
      stripe
      border
      highlight-current-row
      row-key="billCode"
      @selection-change="handleSelectionChange"
      @row-click="handleBillRowClick"
    >
      <ElTableColumn type="selection" width="48" :selectable="isBillSelectable" />
      <ElTableColumn label="上游单号" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="msfx-up-cell">{{ cellText(row.billCode) }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="药品" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="msfx-up-cell">{{ cellText(drugRowName(row)) }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="上游企业" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="msfx-up-cell">{{ cellText(row.fromEntName) }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="开单时间" width="140" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="msfx-up-cell">{{ cellText(row.billTime) }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="类型" width="72" align="center">
        <template #default="{ row }">
          <span class="msfx-up-cell">{{ cellText(row.billType) }}</span>
        </template>
      </ElTableColumn>
    </ElTable>

    <div class="msfx-up-pager">
      <ElPagination
        background
        layout="total, prev, pager, next"
        :total="total"
        :page-size="query.pageSize"
        :current-page="query.pageNo"
        @current-change="handlePageChange"
      />
    </div>

    <div class="msfx-up-status">
      <span v-if="selectedCount > 0">
        已勾选 {{ selectedCount }} 张，点「下一步」进入确认页核对药品、批号和追溯码
      </span>
      <span v-else>勾选上游单（或点行）后点「下一步」；扫码反查成功会自动勾选</span>
    </div>

    <template #footer>
      <div class="msfx-up-footer">
        <ElButton @click="visible = false">取消</ElButton>
        <ElButton
          type="primary"
          :disabled="!canApply"
          :loading="detailLoading"
          @click="handleApply"
        >
          下一步：确认生成
          <template v-if="selectedCount > 0">（已选 {{ selectedCount }}）</template>
        </ElButton>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped>
.msfx-up-alert {
  margin-bottom: 12px;
}

.msfx-up-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 12px;
  margin-bottom: 10px;
}

.msfx-up-field {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.msfx-up-field-grow {
  flex: 1 1 280px;
}

.msfx-up-label {
  flex-shrink: 0;
  width: 56px;
  text-align: right;
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 32px;
}

.msfx-up-control {
  width: 100%;
}

.msfx-up-date {
  width: 148px;
}

.msfx-up-code {
  width: 148px;
}

.msfx-up-ent {
  min-width: 220px;
  width: 100%;
}

.msfx-up-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.msfx-up-scan {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
  margin-bottom: 12px;
}

.msfx-up-scan-input {
  width: 240px;
}

.msfx-up-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.4;
}

.msfx-up-table :deep(.el-table__cell) {
  padding: 8px 0;
}

.msfx-up-table :deep(.el-table__body tr) {
  cursor: pointer;
}

.msfx-up-cell {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
  line-height: 22px;
}

.msfx-up-pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}

.msfx-up-status {
  margin-top: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.msfx-up-footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
}
</style>
