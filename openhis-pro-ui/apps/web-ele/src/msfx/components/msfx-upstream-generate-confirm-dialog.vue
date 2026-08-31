<!--
  上游拉单 → 确认生成进货行：人工修正院内药品 / 计量带 / 数量；记住上次选择。
-->
<script setup lang="ts">
import type { MsfxUpstreamBillRowDto, MsfxUpstreamTraceCodeItem } from '#/api/msfx/types';
import type { MsfxGenerateConfirmLine, MsfxTracePackRank } from '#/msfx/utils/generate-confirm';
import {
  canChooseMsfxInboundCodeRank,
  countMsfxWriteableTraceCodes,
  hasExplicitMsfxTracePackRank,
  isMsfxQtyCodeMismatch,
  isMsfxSplitAttrBlockedByPackCodes,
  msfxTracePackRankLabel,
  resolveMsfxConfirmBandKind,
  resolveMsfxExpectedTraceCount,
  resolveMsfxTracePackRank,
  resolveMsfxWriteableTraceCodes,
  splitMsfxTraceCodesByRank,
  stampMsfxTraceRank,
} from '#/msfx/utils/generate-confirm';
import type { InboundProductUnitBand } from '#/utils/inventory/inbound-product-unit-bands';

import { computed, reactive, ref, watch } from 'vue';

import {
  ElButton,
  ElCheckbox,
  ElDialog,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElPopover,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { pageProductsForInboundReceiptApi } from '#/api/inventory/warehouse/inbound-receipt';
import {
  getMsfxDrugPickMemory,
  resolveMsfxDrugMemoryKey,
  saveMsfxDrugPickMemory,
} from '#/msfx/utils/drug-pick-memory';
import {
  pickBestInboundProductMatch,
  resolveMsfxDrugDisplayName,
  resolveMsfxMatchSearchKeywords,
  resolveMsfxQtyForBandKind,
} from '#/msfx/utils/match-upstream-drug-to-product';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  inboundUnitBandSelectOptions,
  normalizeInboundProductUnitBands,
} from '#/utils/inventory/inbound-product-unit-bands';
import { mapInboundReceiptProductPickListRow } from '#/utils/inventory/inbound-receipt-product-pick-list';
import { inventoryPickStringField } from '#/utils/inventory/inventory-product-pick-list';

defineOptions({ name: 'MsfxUpstreamGenerateConfirmDialog' });

export type { MsfxGenerateConfirmLine };

type MatchSource = 'memory' | 'auto' | 'manual' | 'none';

type DraftRow = {
  key: string;
  include: boolean;
  drug: MsfxUpstreamBillRowDto;
  drugName: string;
  memoryKey: string;
  candidates: Record<string, unknown>[];
  productId: string;
  product?: Record<string, unknown>;
  purchaseQty?: number;
  selectedBandKind: string;
  matchSource: MatchSource;
  loading: boolean;
  pullingRank: MsfxTracePackRank | '';
  searchKeyword: string;
  /** 最近一次已拉取的检索词；用于跳过展开下拉的重复请求 */
  lastFetchedKeyword: string;
};

const props = withDefaults(
  defineProps<{
    drugRows?: MsfxUpstreamBillRowDto[];
    productTableName?: string;
    purposeLocationId?: number | string;
  }>(),
  {
    drugRows: () => [],
    productTableName: '',
    purposeLocationId: undefined,
  },
);

const visible = defineModel<boolean>({ default: false });

const TRACE_PACK_RANKS: MsfxTracePackRank[] = ['LARGE', 'MEDIUM', 'SMALL'];

const emit = defineEmits<{
  confirm: [lines: MsfxGenerateConfirmLine[]];
  pullSmallCodes: [
    payload: { key: string; packCodes: string[]; oneLevelOnly?: boolean; rank?: MsfxTracePackRank },
  ];
}>();

const bootLoading = ref(false);
const drafts = ref<DraftRow[]>([]);
const searchTimers = reactive<Record<string, ReturnType<typeof setTimeout> | undefined>>({});

const includedCount = computed(() => drafts.value.filter((d) => d.include).length);
const readyCount = computed(() => drafts.value.filter((d) => d.include && d.productId).length);
const qtyMismatchCount = computed(
  () => drafts.value.filter((d) => d.include && rowQtyMismatch(d)).length,
);
const splitAttrPackBlockedCount = computed(
  () =>
    drafts.value.filter((d) => {
      if (!d.include) return false;
      const bands = normalizeInboundProductUnitBands(
        d.product?.unitBands,
      ) as InboundProductUnitBand[];
      return isMsfxSplitAttrBlockedByPackCodes(d.drug, d.product, bands);
    }).length,
);
const canConfirm = computed(
  () =>
    includedCount.value > 0 &&
    readyCount.value === includedCount.value &&
    qtyMismatchCount.value === 0 &&
    splitAttrPackBlockedCount.value === 0 &&
    !bootLoading.value &&
    !drafts.value.some((d) => d.loading || d.pullingRank),
);

watch(visible, async (v) => {
  if (!v) return;
  await bootstrapDrafts();
});

function productOptionLabel(p: Record<string, unknown>): string {
  const name = inventoryPickStringField(p, 'productName') || '未命名';
  const spec = inventoryPickStringField(p, 'totalVolume');
  const mfr = inventoryPickStringField(p, 'manufacturer');
  const bits = [name];
  if (spec) bits.push(spec);
  if (mfr) bits.push(mfr);
  return bits.join(' · ');
}

function productIdOf(p: Record<string, unknown> | undefined): string {
  if (!p) return '';
  const id = p.productDefinitionId ?? p.id;
  return id === undefined || id === null ? '' : String(id).trim();
}

function bandOptionsOf(product?: Record<string, unknown>) {
  const bands = normalizeInboundProductUnitBands(product?.unitBands);
  return inboundUnitBandSelectOptions(bands);
}

function applyProductToDraft(
  draft: DraftRow,
  product: Record<string, unknown> | undefined,
  source: MatchSource,
) {
  draft.product = product;
  draft.productId = productIdOf(product);
  draft.matchSource = draft.productId ? source : 'none';
  const bands = normalizeInboundProductUnitBands(product?.unitBands) as InboundProductUnitBand[];
  const mem = getMsfxDrugPickMemory(draft.memoryKey);
  // 拆零属性→拆零单位；中大回填且非拆零→包装单位
  draft.selectedBandKind = resolveMsfxConfirmBandKind({
    drug: draft.drug,
    bands,
    memoryBandKind: mem?.qtyBandKind,
    product,
  });
  const qty = resolveMsfxQtyForBandKind(draft.drug, draft.selectedBandKind, bands);
  draft.purchaseQty = qty;
}

async function searchProducts(keyword: string): Promise<Record<string, unknown>[]> {
  const productTableName = String(props.productTableName ?? '').trim();
  const purposeLocationId = props.purposeLocationId;
  if (
    !productTableName ||
    purposeLocationId === undefined ||
    purposeLocationId === null ||
    String(purposeLocationId).trim() === ''
  ) {
    return [];
  }
  const res = await pageProductsForInboundReceiptApi({
    purposeLocationId,
    productTableName,
    keyword: String(keyword ?? '').trim() || undefined,
    pageNo: 1,
    pageSize: 30,
    activeFlag: true,
  });
  const parsed = parsePagedRowsFromApiData<Record<string, unknown>>(res.data);
  return parsed.content.map((r) => mapInboundReceiptProductPickListRow(r));
}

async function hydrateOne(draft: DraftRow) {
  draft.loading = true;
  try {
    const mem = getMsfxDrugPickMemory(draft.memoryKey);
    const keywords = resolveMsfxMatchSearchKeywords(draft.drug);
    if (mem?.productName) {
      keywords.unshift(String(mem.productName).trim());
    }
    // 去重保序
    const searchKeys = [...new Set(keywords.map((k) => k.trim()).filter(Boolean))];
    draft.searchKeyword = searchKeys[0] || draft.drugName;

    let candidates: Record<string, unknown>[] = [];
    for (const kw of searchKeys) {
      const page = await searchProducts(kw);
      candidates = mergeCandidates(candidates, page);
      if (pickBestInboundProductMatch(candidates, draft.drug)) {
        break;
      }
    }
    draft.candidates = candidates;
    draft.lastFetchedKeyword = draft.searchKeyword;

    // 1) 记忆命中
    if (mem?.productDefinitionId) {
      const remembered = candidates.find(
        (c) => productIdOf(c) === String(mem.productDefinitionId).trim(),
      );
      if (remembered) {
        applyProductToDraft(draft, remembered, 'memory');
        return;
      }
      if (mem.productName) {
        const more = await searchProducts(mem.productName);
        draft.candidates = mergeCandidates(candidates, more);
        draft.lastFetchedKeyword = String(mem.productName).trim() || draft.lastFetchedKeyword;
        const hit2 = draft.candidates.find(
          (c) => productIdOf(c) === String(mem.productDefinitionId).trim(),
        );
        if (hit2) {
          applyProductToDraft(draft, hit2, 'memory');
          return;
        }
      }
    }

    // 2) 按名称/规格/厂家自动匹配；匹配不上再留给人工选
    const auto = pickBestInboundProductMatch(draft.candidates, draft.drug);
    if (auto) {
      applyProductToDraft(draft, auto, 'auto');
      return;
    }
    applyProductToDraft(draft, undefined, 'none');
  } catch {
    applyProductToDraft(draft, undefined, 'none');
  } finally {
    draft.loading = false;
  }
}

function mergeCandidates(
  a: Record<string, unknown>[],
  b: Record<string, unknown>[],
): Record<string, unknown>[] {
  const map = new Map<string, Record<string, unknown>>();
  for (const row of [...a, ...b]) {
    const id = productIdOf(row);
    if (!id) continue;
    if (!map.has(id)) map.set(id, row);
  }
  return [...map.values()];
}

async function bootstrapDrafts() {
  bootLoading.value = true;
  const rows = props.drugRows ?? [];
  drafts.value = rows.map((drug, idx) => {
    const drugName = resolveMsfxDrugDisplayName(drug) || `药品${idx + 1}`;
    const memoryKey = resolveMsfxDrugMemoryKey({
      drugEntBaseInfoId: drug.drugEntBaseInfoId,
      drugName,
    });
    return {
      key: `${memoryKey || 'row'}-${idx}`,
      include: true,
      drug,
      drugName,
      memoryKey,
      candidates: [],
      productId: '',
      product: undefined,
      purchaseQty: resolveMsfxQtyForBandKind(drug, 'PURCHASE'),
      selectedBandKind: 'PURCHASE',
      matchSource: 'none' as MatchSource,
      loading: false,
      pullingRank: '' as MsfxTracePackRank | '',
      searchKeyword: drugName,
      lastFetchedKeyword: '',
    };
  });
  try {
    await Promise.all(drafts.value.map((d) => hydrateOne(d)));
  } finally {
    bootLoading.value = false;
  }
}

function onProductChange(draft: DraftRow, productId: string | number | null | undefined) {
  const id = String(productId ?? '').trim();
  const hit = draft.candidates.find((c) => productIdOf(c) === id);
  applyProductToDraft(draft, hit, hit ? 'manual' : 'none');
}

function onBandChange(draft: DraftRow) {
  const bands = normalizeInboundProductUnitBands(
    draft.product?.unitBands,
  ) as InboundProductUnitBand[];
  const qty = resolveMsfxQtyForBandKind(draft.drug, draft.selectedBandKind, bands);
  if (qty !== undefined) {
    draft.purchaseQty = qty;
  }
  if (draft.productId) {
    draft.matchSource = draft.matchSource === 'none' ? 'manual' : draft.matchSource;
  }
}

function remoteSearch(draft: DraftRow, keyword: string) {
  const raw = String(keyword ?? '').trim();
  // 展开下拉时 remote 常回传空串或当前已选 label：已有候选则跳过，避免闪一下
  if (!raw) {
    if (draft.candidates.length > 0) return;
  } else if (
    draft.candidates.length > 0 &&
    (raw === draft.lastFetchedKeyword ||
      raw === draft.drugName ||
      (draft.product && raw === productOptionLabel(draft.product)))
  ) {
    return;
  }

  const prev = searchTimers[draft.key];
  if (prev) clearTimeout(prev);
  searchTimers[draft.key] = setTimeout(() => {
    void doRemoteSearch(draft, raw);
  }, 280);
}

async function doRemoteSearch(draft: DraftRow, keyword: string) {
  const kw = String(keyword ?? '').trim() || draft.searchKeyword || draft.drugName || '';
  if (!kw) return;
  if (kw === draft.lastFetchedKeyword && draft.candidates.length > 0) {
    return;
  }

  // 已有选项时静默刷新，不打 loading，避免下拉收起/闪烁
  const silent = draft.candidates.length > 0;
  if (!silent) draft.loading = true;
  try {
    const list = await searchProducts(kw);
    draft.searchKeyword = kw;
    draft.lastFetchedKeyword = kw;
    let next = list;
    if (
      draft.product &&
      productIdOf(draft.product) &&
      !list.some((c) => productIdOf(c) === draft.productId)
    ) {
      next = [draft.product, ...list];
    }
    draft.candidates = next;
  } catch {
    /* keep */
  } finally {
    if (!silent) draft.loading = false;
  }
}

function matchTagType(source: MatchSource): 'success' | 'warning' | 'info' | 'danger' {
  if (source === 'memory') return 'success';
  if (source === 'auto') return 'warning';
  if (source === 'manual') return 'info';
  return 'danger';
}

function matchTagLabel(source: MatchSource): string {
  if (source === 'memory') return '上次';
  if (source === 'auto') return '自动';
  if (source === 'manual') return '已选';
  return '待匹配';
}

function handleConfirm() {
  const included = drafts.value.filter((d) => d.include);
  if (included.length === 0) {
    ElMessage.warning('请至少勾选一行');
    return;
  }
  const missing = included.filter((d) => !d.productId || !d.product);
  if (missing.length > 0) {
    ElMessage.warning(
      `还有 ${missing.length} 行未选择院内药品：${missing
        .slice(0, 3)
        .map((m) => m.drugName)
        .join('、')}`,
    );
    return;
  }
  const mismatched = included.filter((d) => rowQtyMismatch(d));
  if (mismatched.length > 0) {
    const sample = mismatched
      .slice(0, 3)
      .map((m) => {
        const name = m.drugName || '药品';
        const bands = normalizeInboundProductUnitBands(
          m.product?.unitBands,
        ) as InboundProductUnitBand[];
        const codeN = countMsfxWriteableTraceCodes(m.drug);
        const expected =
          m.purchaseQty !== undefined && m.purchaseQty !== null
            ? resolveMsfxExpectedTraceCount({
                purchaseQty: Number(m.purchaseQty),
                selectedBandKind: m.selectedBandKind,
                bands,
                product: m.product,
                drug: m.drug,
              })
            : undefined;
        return `${name}(应扫${expected ?? '—'}≠码${codeN})`;
      })
      .join('、');
    ElMessage.warning(`追溯码数量与入库数量不一致，不允许带入：${sample}`);
    return;
  }
  const splitBlocked = included.filter((d) => {
    const bands = normalizeInboundProductUnitBands(
      d.product?.unitBands,
    ) as InboundProductUnitBand[];
    return isMsfxSplitAttrBlockedByPackCodes(d.drug, d.product, bands);
  });
  if (splitBlocked.length > 0) {
    const sample = splitBlocked
      .slice(0, 3)
      .map((m) => m.drugName || '药品')
      .join('、');
    ElMessage.warning(
      `拆分属性为「拆零单位」且追溯码贴在拆零单位上，须展开到最小码后再入库：${sample}${splitBlocked.length > 3 ? '…' : ''}（请重新拉单或改扫小码）`,
    );
    return;
  }
  const lines: MsfxGenerateConfirmLine[] = [];
  for (const d of included) {
    const product = d.product!;
    const productDefinitionId = productIdOf(product);
    if (d.memoryKey && productDefinitionId) {
      saveMsfxDrugPickMemory(d.memoryKey, {
        productDefinitionId,
        productName: inventoryPickStringField(product, 'productName') || d.drugName,
        productTableName:
          inventoryPickStringField(product, 'productTableName') ||
          String(props.productTableName ?? ''),
        qtyBandKind: d.selectedBandKind,
      });
    }
    lines.push({
      drug: d.drug,
      product,
      purchaseQty: d.purchaseQty,
      selectedBandKind: d.selectedBandKind || 'PURCHASE',
    });
  }
  emit('confirm', lines);
  visible.value = false;
}

function formatUpstreamQty(drug: MsfxUpstreamBillRowDto): string {
  const pkg = String(drug.leastPkgAmount ?? '').trim();
  const unit = String(drug.pkgUnitDesc ?? '').trim();
  if (pkg && unit) return `${pkg} ${unit}`;
  if (pkg) return pkg;
  const prepn = String(drug.leastPrepnAmount ?? '').trim();
  const pu = String(drug.prepnUnit ?? '').trim();
  if (prepn && pu) return `${prepn} ${pu}`;
  return prepn || '—';
}

function uniqTraceCodes(codes: unknown[] | undefined): string[] {
  return [...new Set((codes ?? []).map((c) => String(c ?? '').trim()).filter(Boolean))];
}

function rankedTraceList(drug: MsfxUpstreamBillRowDto) {
  const split = splitMsfxTraceCodesByRank(drug);
  return [...split.large, ...split.medium, ...split.small];
}

function inboundRankedTraceList(drug: MsfxUpstreamBillRowDto) {
  const split = splitMsfxTraceCodesByRank(drug);
  const pack = [...split.large, ...split.medium];
  if (!canChooseMsfxInboundCodeRank(drug)) {
    return [...pack, ...split.small];
  }
  const prefer = String(drug.inboundCodeRank ?? '')
    .trim()
    .toUpperCase();
  if (prefer === 'PACK') return pack;
  if (prefer === 'SMALL') return split.small;
  const writeable = new Set(resolveMsfxWriteableTraceCodes(drug));
  const all = [...pack, ...split.small];
  const picked = all.filter((row) => writeable.has(row.code));
  return picked.length ? picked : all;
}

function previewTraceLines(drug: MsfxUpstreamBillRowDto) {
  return inboundRankedTraceList(drug).slice(0, 1);
}

function shortTraceCode(code: string): string {
  const t = String(code ?? '').trim();
  if (t.length <= 14) return t;
  return `${t.slice(0, 6)}…${t.slice(-4)}`;
}

function rankCount(drug: MsfxUpstreamBillRowDto, rank: MsfxTracePackRank): number {
  const split = splitMsfxTraceCodesByRank(drug);
  if (rank === 'LARGE') return split.large.length;
  if (rank === 'MEDIUM') return split.medium.length;
  return split.small.length;
}

function codesForRankPull(drug: MsfxUpstreamBillRowDto, rank: MsfxTracePackRank): string[] {
  const split = splitMsfxTraceCodesByRank(drug);
  if (rank === 'LARGE') return [];
  if (rank === 'MEDIUM') return split.large.map((r) => r.code);
  return [...split.large, ...split.medium].map((r) => r.code);
}

function rowNeedsExpand(row: DraftRow): boolean {
  if (rowQtyMismatch(row)) return true;
  const bands = normalizeInboundProductUnitBands(
    row.product?.unitBands,
  ) as InboundProductUnitBand[];
  return isMsfxSplitAttrBlockedByPackCodes(row.drug, row.product, bands);
}

function canPullRank(row: DraftRow, rank: MsfxTracePackRank): boolean {
  if (!rowNeedsExpand(row)) return false;
  const split = splitMsfxTraceCodesByRank(row.drug);
  if (rank === 'LARGE') return false;
  if (rank === 'MEDIUM') return split.large.length > 0;
  return split.large.length + split.medium.length > 0;
}

function pullRankTitle(row: DraftRow, rank: MsfxTracePackRank): string {
  if (!rowNeedsExpand(row)) return '当前码已覆盖入库量，无需再拉';
  if (rank === 'LARGE') return '大码由上游出库单带出，无需拉取';
  if (rank === 'MEDIUM') {
    return canPullRank(row, rank) ? '从大码展开一层中码' : '没有待展开的大码';
  }
  return canPullRank(row, rank) ? '将剩余中大码展开为小码' : '没有待展开的中大码';
}

function handlePullRankClick(row: DraftRow, rank: MsfxTracePackRank) {
  if (!canPullRank(row, rank)) {
    ElMessage.info(pullRankTitle(row, rank));
    return;
  }
  const packCodes = codesForRankPull(row.drug, rank);
  if (packCodes.length === 0) {
    ElMessage.info(pullRankTitle(row, rank));
    return;
  }
  emit('pullSmallCodes', { key: row.key, packCodes, oneLevelOnly: rank !== 'SMALL', rank });
}

function rankLines(drug: MsfxUpstreamBillRowDto, rank: MsfxTracePackRank) {
  const split = splitMsfxTraceCodesByRank(drug);
  if (rank === 'LARGE') return split.large;
  if (rank === 'MEDIUM') return split.medium;
  return split.small;
}

function setRowPulling(key: string, pulling: boolean, rank?: MsfxTracePackRank) {
  const d = drafts.value.find((x) => x.key === key);
  if (!d) return;
  d.pullingRank = pulling ? (rank ?? d.pullingRank ?? 'SMALL') : '';
}

function rebuildDrugTraceFields(drug: MsfxUpstreamBillRowDto, items: MsfxUpstreamTraceCodeItem[]) {
  const split = splitMsfxTraceCodesByRank({
    traceCodeItems: items,
    packTraceCodes: drug.packTraceCodes,
    traceCodes: drug.traceCodes,
  });
  drug.traceCodeItems = items;
  drug.packTraceCodes = [...split.large, ...split.medium].map((r) => r.code);
  drug.traceCodes = split.small.map((r) => r.code);
}

function applyExpandedCodes(
  key: string,
  sourceCodes: string[],
  items: MsfxUpstreamTraceCodeItem[],
  fallbackRank?: MsfxTracePackRank,
) {
  const d = drafts.value.find((x) => x.key === key);
  if (!d) return;
  const sourceSet = new Set(uniqTraceCodes(sourceCodes));
  const currentItems = rankedTraceList(d.drug).map((row) => row.item);
  const currentByCode = new Map(
    currentItems.map((item) => [String(item.code ?? '').trim(), item] as const),
  );
  const incoming = items
    .map((item) => {
      const code = String(item.code ?? '').trim();
      if (!code) return null;
      const hasRank = hasExplicitMsfxTracePackRank(item);
      if (!hasRank && sourceSet.has(code)) {
        // 拉取小码后仍是源码：平台 NOT_BOX（已是最小码），不要继续当中码
        if (fallbackRank === 'SMALL') {
          return { ...stampMsfxTraceRank(item, 'SMALL'), code };
        }
        if (currentByCode.has(code)) {
          return { ...currentByCode.get(code)!, code };
        }
      }
      const rank = hasRank ? resolveMsfxTracePackRank(item) : (fallbackRank ?? 'MEDIUM');
      return { ...stampMsfxTraceRank(item, rank), code };
    })
    .filter((x): x is MsfxUpstreamTraceCodeItem => x !== null);
  const uniqIncoming: MsfxUpstreamTraceCodeItem[] = [];
  const seen = new Set<string>();
  for (const item of incoming) {
    const code = String(item.code ?? '').trim();
    if (!code || seen.has(code)) continue;
    seen.add(code);
    uniqIncoming.push(item);
  }
  if (uniqIncoming.length === 0) return;
  const existing = currentItems.filter((item) => {
    const code = String(item.code ?? '').trim();
    return code && !sourceSet.has(code);
  });
  const leftoverSet = new Set(
    uniqIncoming
      .map((item) => String(item.code ?? '').trim())
      .filter((code) => sourceSet.has(code)),
  );
  const childIncoming = uniqIncoming.filter(
    (item) => !sourceSet.has(String(item.code ?? '').trim()),
  );
  const expandedPack = currentItems.filter((item) => {
    const code = String(item.code ?? '').trim();
    return code && sourceSet.has(code) && !leftoverSet.has(code);
  });
  const nextItems =
    childIncoming.length > 0 && expandedPack.length > 0
      ? [...existing, ...expandedPack, ...uniqIncoming]
      : [...existing, ...uniqIncoming];
  rebuildDrugTraceFields(d.drug, nextItems);
  if (canChooseMsfxInboundCodeRank(d.drug)) {
    d.drug.inboundCodeRank = suggestInboundCodeRank(d);
  } else {
    d.drug.inboundCodeRank = undefined;
  }
  d.pullingRank = '';
}

function suggestInboundCodeRank(row: DraftRow): 'PACK' | 'SMALL' {
  const bands = normalizeInboundProductUnitBands(
    row.product?.unitBands,
  ) as InboundProductUnitBand[];
  const packDrug = { ...row.drug, inboundCodeRank: 'PACK' as const };
  const smallDrug = { ...row.drug, inboundCodeRank: 'SMALL' as const };
  const smallOk = !isMsfxQtyCodeMismatch(row.purchaseQty, smallDrug, {
    selectedBandKind: row.selectedBandKind,
    bands,
    product: row.product,
  });
  if (smallOk) return 'SMALL';
  const packOk = !isMsfxQtyCodeMismatch(row.purchaseQty, packDrug, {
    selectedBandKind: row.selectedBandKind,
    bands,
    product: row.product,
  });
  return packOk ? 'PACK' : 'SMALL';
}

function inboundTraceCount(drug: MsfxUpstreamBillRowDto): number {
  return inboundRankedTraceList(drug).length;
}

function onInboundCodeRankChange(row: DraftRow, rank: string) {
  row.drug.inboundCodeRank = rank === 'PACK' ? 'PACK' : 'SMALL';
}

function applySmallCodes(key: string, codes: string[]) {
  const d = drafts.value.find((x) => x.key === key);
  if (!d) return;
  const incoming = uniqTraceCodes(codes);
  if (incoming.length === 0) return;
  applyExpandedCodes(
    key,
    codesForRankPull(d.drug, 'SMALL'),
    incoming.map((code) => stampMsfxTraceRank({ code }, 'SMALL')),
  );
}

defineExpose({ applySmallCodes, applyExpandedCodes, setRowPulling });

function rowQtyMismatch(row: DraftRow): boolean {
  if (!row.include) return false;
  const bands = normalizeInboundProductUnitBands(
    row.product?.unitBands,
  ) as InboundProductUnitBand[];
  return isMsfxQtyCodeMismatch(row.purchaseQty, row.drug, {
    selectedBandKind: row.selectedBandKind,
    bands,
    product: row.product,
  });
}
</script>

<template>
  <ElDialog
    v-model="visible"
    title="确认生成/追加进货明细"
    width="96vw"
    class="msfx-gen-confirm-dialog"
    destroy-on-close
    append-to-body
    :close-on-click-modal="false"
  >
    <div class="msfx-gen-hint">
      <strong>核对后生成</strong>
      ：左侧为上游出库信息；右侧默认按名称等自动匹配院内药品，匹配不上再人工检索。确认后追加到本进货单（可多次拉不同上游单）。确认过的选择会记住，下次同药优先带出。
      追溯码以上游出库单为准。大码由上游带出，不必再拉。
      仅当<strong>当前码盖不住入库量</strong>，或拆零属性必须最小码时，才显示中/小「拉取」。
      中：从大码展开一层；小：把剩余中大码拉到最小包装。
      拉取后若同时有中大码和小码，可在追溯码列选择入库用哪一档。码已够可直接确认生成。
    </div>

    <div class="msfx-gen-stats">
      <span>共 {{ drafts.length }} 种</span>
      <span>纳入 {{ includedCount }}</span>
      <span>已选院内品 {{ readyCount }}</span>
      <span v-if="drafts.some((d) => d.matchSource === 'none' && d.include)" class="is-warn">
        有待人工匹配
      </span>
      <span v-if="qtyMismatchCount > 0" class="is-warn">
        {{ qtyMismatchCount }} 行码未覆盖入库量
      </span>
      <span v-if="splitAttrPackBlockedCount > 0" class="is-warn">
        {{ splitAttrPackBlockedCount }} 行拆零属性仍为中大码
      </span>
    </div>

    <ElTable
      v-loading="bootLoading"
      class="msfx-gen-table"
      :data="drafts"
      border
      stripe
      size="small"
      max-height="480"
      row-key="key"
      table-layout="fixed"
    >
      <ElTableColumn label="纳入" width="44" align="center">
        <template #default="{ row }">
          <ElCheckbox v-model="row.include" />
        </template>
      </ElTableColumn>
      <ElTableColumn label="状态" width="52" align="center">
        <template #default="{ row }">
          <ElTag size="small" :type="matchTagType(row.matchSource)">
            {{ matchTagLabel(row.matchSource) }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn label="上游药品" min-width="108" show-overflow-tooltip>
        <template #default="{ row }">
          <div class="msfx-gen-up-name">{{ row.drugName }}</div>
          <div class="msfx-gen-up-sub">
            {{ row.drug.packageSpec || '—' }}
            <span v-if="row.drug.productEntName"> · {{ row.drug.productEntName }}</span>
          </div>
        </template>
      </ElTableColumn>
      <ElTableColumn label="上游数量" width="72" align="right">
        <template #default="{ row }">
          {{ formatUpstreamQty(row.drug) }}
        </template>
      </ElTableColumn>
      <ElTableColumn label="批号" width="80" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.drug.produceBatchNo || '—' }}
        </template>
      </ElTableColumn>
      <ElTableColumn label="院内药品" min-width="150">
        <template #default="{ row }">
          <ElSelect
            :model-value="row.productId"
            filterable
            remote
            reserve-keyword
            clearable
            :remote-method="(q: string) => remoteSearch(row, q)"
            :loading="row.loading"
            placeholder="检索院内药品"
            class="msfx-gen-select"
            @update:model-value="(v: string) => onProductChange(row, v)"
          >
            <ElOption
              v-for="opt in row.candidates"
              :key="productIdOf(opt)"
              :label="productOptionLabel(opt)"
              :value="productIdOf(opt)"
            />
          </ElSelect>
        </template>
      </ElTableColumn>
      <ElTableColumn label="计量带" width="84">
        <template #default="{ row }">
          <ElSelect
            v-model="row.selectedBandKind"
            :disabled="!row.productId"
            class="msfx-gen-select"
            @change="onBandChange(row)"
          >
            <ElOption
              v-for="opt in bandOptionsOf(row.product)"
              :key="opt.value"
              :label="opt.label || opt.value"
              :value="opt.value"
            />
            <ElOption v-if="!bandOptionsOf(row.product).length" value="LEGACY" label="默认单位" />
          </ElSelect>
        </template>
      </ElTableColumn>
      <ElTableColumn label="入库数量" width="72" align="center">
        <template #default="{ row }">
          <ElInputNumber
            v-model="row.purchaseQty"
            :min="0"
            :controls="false"
            class="msfx-gen-qty"
            :class="{ 'is-mismatch': rowQtyMismatch(row) }"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn label="追溯码" width="168">
        <template #default="{ row }">
          <div class="msfx-gen-code-cell">
            <ElSelect
              v-if="canChooseMsfxInboundCodeRank(row.drug)"
              :model-value="row.drug.inboundCodeRank === 'PACK' ? 'PACK' : 'SMALL'"
              size="small"
              class="msfx-gen-code-rank-select"
              @click.stop
              @update:model-value="(v: string) => onInboundCodeRankChange(row, v)"
            >
              <ElOption
                value="PACK"
                :label="`入库用中大码 ${rankCount(row.drug, 'LARGE') + rankCount(row.drug, 'MEDIUM')}`"
              />
              <ElOption value="SMALL" :label="`入库用小码 ${rankCount(row.drug, 'SMALL')}`" />
            </ElSelect>
            <ElPopover placement="left-start" :width="460" trigger="click">
              <template #reference>
                <button
                  type="button"
                  class="msfx-gen-code-trigger"
                  :class="{ 'is-mismatch': rowQtyMismatch(row) }"
                  title="点击查看全部追溯码"
                >
                  <template v-if="inboundRankedTraceList(row.drug).length">
                    <span
                      v-for="line in previewTraceLines(row.drug)"
                      :key="line.code"
                      class="msfx-gen-code-line"
                    >
                      <span class="msfx-gen-code-text">{{ shortTraceCode(line.code) }}</span>
                      <span class="msfx-gen-code-rank" :data-rank="line.rank">
                        {{ msfxTracePackRankLabel(line.rank) }}
                      </span>
                    </span>
                    <span class="msfx-gen-code-more">共 {{ inboundTraceCount(row.drug) }} 个</span>
                  </template>
                  <span v-else class="msfx-gen-code-empty">暂无</span>
                </button>
              </template>
              <div class="msfx-gen-code-pop">
                <section v-for="rank in TRACE_PACK_RANKS" :key="rank">
                  <header>
                    {{ msfxTracePackRankLabel(rank) }}码 {{ rankCount(row.drug, rank) }}
                  </header>
                  <ul v-if="rankCount(row.drug, rank)">
                    <li v-for="line in rankLines(row.drug, rank)" :key="`${rank}-${line.code}`">
                      {{ line.code }}
                    </li>
                  </ul>
                  <p v-else class="msfx-gen-code-empty">暂无{{ msfxTracePackRankLabel(rank) }}码</p>
                </section>
              </div>
            </ElPopover>
          </div>
        </template>
      </ElTableColumn>
      <ElTableColumn label="大" width="58" align="center">
        <template #default="{ row }">
          <div class="msfx-gen-rank-cell" :title="pullRankTitle(row, 'LARGE')">
            <span class="msfx-gen-rank-count">{{ rankCount(row.drug, 'LARGE') }}</span>
            <ElButton
              v-if="canPullRank(row, 'LARGE')"
              type="primary"
              link
              size="small"
              :loading="row.pullingRank === 'LARGE'"
              :disabled="Boolean(row.pullingRank) && row.pullingRank !== 'LARGE'"
              @click="handlePullRankClick(row, 'LARGE')"
            >
              拉取
            </ElButton>
            <span v-else-if="rankCount(row.drug, 'LARGE') === 0" class="msfx-gen-op-empty">—</span>
          </div>
        </template>
      </ElTableColumn>
      <ElTableColumn label="中" width="58" align="center">
        <template #default="{ row }">
          <div class="msfx-gen-rank-cell" :title="pullRankTitle(row, 'MEDIUM')">
            <span class="msfx-gen-rank-count">{{ rankCount(row.drug, 'MEDIUM') }}</span>
            <ElButton
              v-if="canPullRank(row, 'MEDIUM')"
              type="primary"
              link
              size="small"
              :loading="row.pullingRank === 'MEDIUM'"
              :disabled="Boolean(row.pullingRank) && row.pullingRank !== 'MEDIUM'"
              @click="handlePullRankClick(row, 'MEDIUM')"
            >
              拉取
            </ElButton>
            <span v-else-if="rankCount(row.drug, 'MEDIUM') === 0" class="msfx-gen-op-empty">—</span>
          </div>
        </template>
      </ElTableColumn>
      <ElTableColumn label="小" width="58" align="center">
        <template #default="{ row }">
          <div class="msfx-gen-rank-cell" :title="pullRankTitle(row, 'SMALL')">
            <span class="msfx-gen-rank-count">{{ rankCount(row.drug, 'SMALL') }}</span>
            <ElButton
              v-if="canPullRank(row, 'SMALL')"
              type="primary"
              link
              size="small"
              :loading="row.pullingRank === 'SMALL'"
              :disabled="Boolean(row.pullingRank) && row.pullingRank !== 'SMALL'"
              @click="handlePullRankClick(row, 'SMALL')"
            >
              拉取
            </ElButton>
            <span v-else-if="rankCount(row.drug, 'SMALL') === 0" class="msfx-gen-op-empty">—</span>
          </div>
        </template>
      </ElTableColumn>
    </ElTable>

    <template #footer>
      <div class="msfx-gen-footer">
        <span class="msfx-gen-footer-hint">
          已选 {{ readyCount }}/{{ includedCount }} 行可生成
          <template v-if="qtyMismatchCount > 0"> · 中大码须能覆盖入库量</template>
          <template v-if="splitAttrPackBlockedCount > 0"> · 拆零属性须最小码</template>
        </span>
        <ElButton @click="visible = false">取消</ElButton>
        <ElButton type="primary" :disabled="!canConfirm" @click="handleConfirm">
          确认生成
        </ElButton>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped>
.msfx-gen-hint {
  margin-bottom: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.msfx-gen-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  margin-bottom: 10px;
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.msfx-gen-stats .is-warn {
  color: var(--el-color-warning);
}

.msfx-gen-up-name,
.msfx-gen-up-sub {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.msfx-gen-up-name {
  font-weight: 500;
  line-height: 1.3;
}

.msfx-gen-up-sub {
  margin-top: 2px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.msfx-gen-select {
  width: 100%;
}

.msfx-gen-qty {
  width: 100%;
}

.msfx-gen-qty.is-mismatch :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--el-color-danger) inset;
}

.msfx-gen-table {
  width: 100%;
}

.msfx-gen-table :deep(.el-scrollbar__wrap) {
  overflow-x: hidden;
}

.msfx-gen-code-cell {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 4px;
}

.msfx-gen-code-rank-select {
  width: 100%;
}

.msfx-gen-code-trigger {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  margin: 0;
  padding: 0;
  border: 0;
  background: transparent;
  font: inherit;
  line-height: 1.3;
  color: var(--el-color-primary);
  cursor: pointer;
  text-align: left;
  max-width: 100%;
  overflow: hidden;
}

.msfx-gen-code-trigger.is-mismatch {
  color: var(--el-color-danger);
}

.msfx-gen-code-line {
  display: flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  overflow: hidden;
}

.msfx-gen-code-text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  font-weight: 500;
}

.msfx-gen-code-rank {
  flex: none;
  padding: 0 4px;
  border-radius: 2px;
  font-size: 11px;
  font-weight: 600;
  line-height: 16px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.msfx-gen-code-rank[data-rank='LARGE'] {
  color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
}

.msfx-gen-code-rank[data-rank='MEDIUM'] {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.msfx-gen-code-rank[data-rank='SMALL'] {
  color: var(--el-color-success);
  background: var(--el-color-success-light-9);
}

.msfx-gen-code-more {
  font-size: 12px;
  font-weight: 500;
  color: var(--el-text-color-secondary);
}

.msfx-gen-code-pop {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 10px;
}

.msfx-gen-code-pop header {
  margin-bottom: 6px;
  font-weight: 600;
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.msfx-gen-code-pop ul {
  max-height: 280px;
  margin: 0;
  padding: 0;
  overflow: auto;
  list-style: none;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.55;
  color: var(--el-text-color-regular);
  word-break: break-all;
}

.msfx-gen-code-empty {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.msfx-gen-op-empty {
  color: var(--el-text-color-placeholder);
}

.msfx-gen-rank-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0;
  line-height: 1.2;
}

.msfx-gen-rank-cell :deep(.el-button) {
  padding: 0;
  height: auto;
  min-height: 0;
}

.msfx-gen-rank-count {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.msfx-gen-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.msfx-gen-footer-hint {
  margin-right: auto;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>

<style>
.msfx-gen-confirm-dialog.el-dialog {
  max-width: calc(100vw - 24px);
}

.msfx-gen-confirm-dialog .el-dialog__body {
  overflow-x: hidden;
}
</style>
