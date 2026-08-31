<script lang="ts" setup>
/**
 * 码上放心 · 供应商对照工作台（左 HIS / 右平台往来单位）。
 * 菜单入口：码上放心 → 供应商对照。
 * 交互参考医保目录对码工作台：先拉平台，再左右选中建立对照。
 */
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type {
  MsfxHisSupplierWorkbenchRow,
  MsfxMapStatus,
  MsfxPlatformPartnerWorkbenchRow,
  MsfxSupplierMapItem,
} from '#/api/msfx/supplier-map';
import type { PageParams } from '#/types/api-response';

import { computed, onActivated, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import {
  ElAlert,
  ElButton,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElSpace,
  ElStatistic,
  ElTag,
} from 'element-plus';

import {
  deleteMsfxSupplierMapApi,
  pageMsfxHisSuppliersApi,
  pageMsfxPlatformPartnersApi,
  pullAllMsfxPlatformPartnersApi,
  saveMsfxSupplierMapApi,
} from '#/api/msfx/supplier-map';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

import EditDialog from './edit-dialog.vue';

defineOptions({ name: 'BasicMsfxSupplierMapIndex' });

interface HisQuery extends PageParams {
  keyword?: string;
  mapStatus?: MsfxMapStatus;
}

interface PlatformQuery extends PageParams {
  entName?: string;
  mapStatus?: MsfxMapStatus;
}

const MAP_STATUS_OPTIONS = [
  { label: '全部', value: 'ALL' },
  { label: '未对照', value: 'UNMAPPED' },
  { label: '已对照', value: 'MAPPED' },
];

const sharedKeyword = ref('');

const hisQuery = ref<HisQuery>({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
  mapStatus: 'UNMAPPED',
});

const platformQuery = ref<PlatformQuery>({
  pageNo: 1,
  pageSize: 20,
  entName: '',
  mapStatus: 'ALL',
});

/** 左右默认每页 20，切换 10/20/50/100 由表格底栏提供 */ const hisRows = ref<
  MsfxHisSupplierWorkbenchRow[]
>([]);
const hisTotal = ref(0);
const hisLoading = ref(false);
const selectedHis = ref<MsfxHisSupplierWorkbenchRow | null>(null);

const platformRows = ref<MsfxPlatformPartnerWorkbenchRow[]>([]);
const platformTotal = ref(0);
const platformLoading = ref(false);
const pullingAll = ref(false);
const selectedPlatform = ref<MsfxPlatformPartnerWorkbenchRow | null>(null);
const platformPulled = ref(false);
/** 全局拉取后右侧展示全量缓存，不再按页打 TOP */
const platformAllMode = ref(false);

const editVisible = ref(false);
const editRow = ref<Partial<MsfxSupplierMapItem>>({});

const hisUnmappedCount = computed(() => hisRows.value.filter((r) => !r.mapped).length);
const hisMappedCount = computed(() => hisRows.value.filter((r) => r.mapped).length);

const selectedHisLabel = computed(() => {
  const h = selectedHis.value;
  if (!h?.supplierId) return '';
  return String(h.supplierName || h.busNo || h.supplierId);
});

const selectedPlatformLabel = computed(() => {
  const p = selectedPlatform.value;
  if (!p?.entId) return '';
  return String(p.entName || p.entId);
});

const canLink = computed(
  () => Boolean(selectedHis.value?.supplierId) && Boolean(selectedPlatform.value?.entId),
);

function hisRowClassName({ row }: { row: Record<string, unknown> }): string {
  const id = String(row.supplierId ?? '');
  const selected = String(selectedHis.value?.supplierId ?? '');
  return id && selected && id === selected ? 'msfx-row-selected' : '';
}

function platformRowClassName({ row }: { row: Record<string, unknown> }): string {
  const id = String(row.entId ?? '');
  const selected = String(selectedPlatform.value?.entId ?? '');
  return id && selected && id === selected ? 'msfx-row-selected' : '';
}

function clearSelection() {
  selectedHis.value = null;
  selectedPlatform.value = null;
}

const hisFormItems = computed<FormItemConfig[]>(() => [
  {
    type: 'select',
    prop: 'mapStatus',
    label: '对照状态',
    clearable: false,
    multiple: false,
    options: MAP_STATUS_OPTIONS,
  },
]);

const platformFormItems = computed<FormItemConfig[]>(() => [
  {
    type: 'select',
    prop: 'mapStatus',
    label: '对照状态',
    clearable: false,
    multiple: false,
    options: MAP_STATUS_OPTIONS,
  },
]);

function asMapStatus(raw: unknown): MsfxMapStatus {
  if (Array.isArray(raw)) {
    return (String(raw[0] ?? 'ALL') || 'ALL') as MsfxMapStatus;
  }
  const s = String(raw ?? 'ALL').trim();
  return (s || 'ALL') as MsfxMapStatus;
}
const hisColumns = computed<TableColumn[]>(() => [
  { prop: 'supplierName', label: 'HIS 供应商', minWidth: 140, showOverflowTooltip: true },
  { prop: 'busNo', label: '编码', width: 100, showOverflowTooltip: true },
  {
    prop: 'mapped',
    label: '状态',
    width: 88,
    align: 'center',
    slot: 'mapped',
  },
  {
    prop: 'msfxEntName',
    label: '已对平台',
    minWidth: 120,
    showOverflowTooltip: true,
  },
  { prop: 'action', label: '操作', width: 160, fixed: 'right', slot: 'action' },
]);

const platformColumns = computed<TableColumn[]>(() => [
  { prop: 'entName', label: '平台企业', minWidth: 150, showOverflowTooltip: true },
  { prop: 'entId', label: 'ent_id', minWidth: 120, showOverflowTooltip: true },
  { prop: 'refEntId', label: 'ref_ent_id', minWidth: 120, showOverflowTooltip: true },
  {
    prop: 'mapped',
    label: '院内',
    width: 96,
    align: 'center',
    slot: 'mapped',
  },
  {
    prop: 'supplierName',
    label: '已对 HIS',
    minWidth: 110,
    showOverflowTooltip: true,
  },
  { prop: 'action', label: '操作', width: 140, fixed: 'right', slot: 'action' },
]);

function pickErr(error: unknown): string {
  if (error && typeof error === 'object' && 'message' in error) {
    return String((error as { message?: string }).message);
  }
  return '请求失败';
}

function syncSharedKeyword() {
  const kw = sharedKeyword.value.trim();
  hisQuery.value.keyword = kw;
  platformQuery.value.entName = kw;
}

async function loadHis() {
  hisLoading.value = true;
  try {
    const res = await pageMsfxHisSuppliersApi({
      pageNo: hisQuery.value.pageNo,
      pageSize: hisQuery.value.pageSize,
      keyword: hisQuery.value.keyword || undefined,
      mapStatus: asMapStatus(hisQuery.value.mapStatus),
    });
    const parsed = parsePagedRowsFromApiData<MsfxHisSupplierWorkbenchRow>(res.data);
    hisRows.value = parsed.content;
    hisTotal.value = parsed.totalCount;
  } catch (error) {
    hisRows.value = [];
    hisTotal.value = 0;
    ElMessage.error(pickErr(error));
  } finally {
    hisLoading.value = false;
  }
}

async function loadPlatform() {
  platformLoading.value = true;
  platformAllMode.value = false;
  try {
    const res = await pageMsfxPlatformPartnersApi({
      pageNo: platformQuery.value.pageNo,
      pageSize: platformQuery.value.pageSize,
      entName: platformQuery.value.entName || undefined,
      mapStatus: asMapStatus(platformQuery.value.mapStatus),
    });
    const data = res.data;
    platformRows.value = data?.content ?? [];
    platformTotal.value = Number(data?.totalCount ?? platformRows.value.length) || 0;
    platformPulled.value = true;
    if (data && data.responseSuccess === false) {
      ElMessage.warning(
        data.msgInfo ||
          data.msgCode ||
          '平台往来单位查询失败（请核对 TOP 凭据；若本院尚未在码上放心维护往来单位，右侧会为空）',
      );
    } else if (platformRows.value.length === 0) {
      ElMessage.info(
        data?.msgInfo ||
          '平台往来单位为空：请先在码上放心平台维护往来单位，或改用「手工新增」填 ent_id',
      );
    }
  } catch (error) {
    platformRows.value = [];
    platformTotal.value = 0;
    ElMessage.error(pickErr(error));
  } finally {
    platformLoading.value = false;
  }
}

async function handleCompareQuery() {
  syncSharedKeyword();
  hisQuery.value.pageNo = 1;
  platformQuery.value.pageNo = 1;
  await Promise.all([loadHis(), loadPlatform()]);
}

async function handlePullAll() {
  syncSharedKeyword();
  pullingAll.value = true;
  try {
    const res = await pullAllMsfxPlatformPartnersApi({
      entName: platformQuery.value.entName || undefined,
      mapStatus: asMapStatus(platformQuery.value.mapStatus),
    });
    const data = res.data;
    platformRows.value = data?.content ?? [];
    platformTotal.value = platformRows.value.length;
    platformQuery.value.pageNo = 1;
    platformPulled.value = true;
    platformAllMode.value = true;
    if (data && data.responseSuccess === false) {
      ElMessage.warning(data.msgInfo || data.msgCode || '全局拉取失败');
    } else {
      ElMessage.success(`已拉取 ${platformRows.value.length} 条平台往来单位`);
    }
    await loadHis();
  } catch (error) {
    ElMessage.error(pickErr(error));
  } finally {
    pullingAll.value = false;
  }
}

function handleHisQuery() {
  hisQuery.value.pageNo = 1;
  void loadHis();
}

function handleHisReset() {
  hisQuery.value.mapStatus = 'UNMAPPED';
  hisQuery.value.pageNo = 1;
  void loadHis();
}

function handleHisPagination(pagination: { limit: number; page: number }) {
  hisQuery.value.pageNo = pagination.page;
  hisQuery.value.pageSize = pagination.limit;
  void loadHis();
}

function handlePlatformQuery() {
  platformQuery.value.pageNo = 1;
  platformAllMode.value = false;
  void loadPlatform();
}

function handlePlatformReset() {
  platformQuery.value.mapStatus = 'ALL';
  platformQuery.value.pageNo = 1;
  platformAllMode.value = false;
  void loadPlatform();
}

function handlePlatformPagination(pagination: { limit: number; page: number }) {
  if (platformAllMode.value) return;
  platformQuery.value.pageNo = pagination.page;
  platformQuery.value.pageSize = pagination.limit;
  void loadPlatform();
}

function onHisRowClick(row: MsfxHisSupplierWorkbenchRow) {
  selectedHis.value = row ?? null;
}

function onPlatformRowClick(row: MsfxPlatformPartnerWorkbenchRow) {
  selectedPlatform.value = row ?? null;
}

/** 操作列点「对照」时先选中该行，避免只改数据无高亮 */
function selectHisOnly(his: MsfxHisSupplierWorkbenchRow) {
  selectedHis.value = his;
}

function selectPlatformOnly(plat: MsfxPlatformPartnerWorkbenchRow) {
  selectedPlatform.value = plat;
}

async function linkSelected() {
  const his = selectedHis.value;
  const plat = selectedPlatform.value;
  if (!his?.supplierId) {
    ElMessage.warning('请先在左侧选中 HIS 供应商');
    return;
  }
  if (!plat?.entId) {
    ElMessage.warning('请先在右侧选中平台企业');
    return;
  }
  try {
    await ElMessageBox.confirm(
      `将「${his.supplierName || his.supplierId}」对照到「${plat.entName || plat.entId}」？`,
      '建立对照',
      { type: 'info' },
    );
  } catch {
    return;
  }
  try {
    await saveMsfxSupplierMapApi({
      id: his.mapId,
      supplierId: his.supplierId,
      msfxEntId: String(plat.entId),
      msfxRefEntId: plat.refEntId || undefined,
      msfxEntName: plat.entName || undefined,
    });
    ElMessage.success('对照已保存');
    await Promise.all([loadHis(), platformPulled.value ? loadPlatform() : Promise.resolve()]);
  } catch (error) {
    ElMessage.error(pickErr(error));
  }
}

async function linkHisToPlatform(his: MsfxHisSupplierWorkbenchRow) {
  selectedHis.value = his;
  if (!selectedPlatform.value?.entId) {
    ElMessage.info('请再在右侧选中平台企业，或点顶部「建立对照」');
    return;
  }
  await linkSelected();
}

async function linkPlatformToHis(plat: MsfxPlatformPartnerWorkbenchRow) {
  selectedPlatform.value = plat;
  if (!selectedHis.value?.supplierId) {
    ElMessage.info('请再在左侧选中 HIS 供应商，或点顶部「建立对照」');
    return;
  }
  await linkSelected();
}

async function unlinkHis(row: MsfxHisSupplierWorkbenchRow) {
  if (!row.mapId) return;
  try {
    await ElMessageBox.confirm(`解除「${row.supplierName || row.supplierId}」的对照？`, '解除', {
      type: 'warning',
    });
  } catch {
    return;
  }
  try {
    await deleteMsfxSupplierMapApi({ id: row.mapId });
    ElMessage.success('已解除');
    await Promise.all([loadHis(), platformPulled.value ? loadPlatform() : Promise.resolve()]);
  } catch (error) {
    ElMessage.error(pickErr(error));
  }
}

function openManualCreate() {
  editRow.value = selectedHis.value?.supplierId ? { supplierId: selectedHis.value.supplierId } : {};
  editVisible.value = true;
}

onMounted(async () => {
  await loadHis();
});

onActivated(() => {
  void loadHis();
});
</script>

<template>
  <Page auto-content-height content-class="msfx-supplier-map-workbench">
    <div class="workbench-root">
      <div class="workbench-toolbar">
        <ElAlert
          type="info"
          :closable="false"
          show-icon
          title="左右对照"
          description="左侧=院内供应商；右侧=码上放心往来单位（listparts）。先「从平台拉取」或「全局拉取」，再左右各选一行点「建立对照」。也可手工新增。"
        />
        <div class="workbench-toolbar-row">
          <div class="workbench-field grow">
            <span class="workbench-label">关键字</span>
            <ElInput
              v-model="sharedKeyword"
              clearable
              placeholder="两边同查：供应商名 / 平台企业名"
              @keyup.enter="handleCompareQuery"
            />
          </div>
          <ElButton type="primary" @click="handleCompareQuery">对照查询</ElButton>
          <ElButton :loading="platformLoading" @click="loadPlatform">从平台拉取</ElButton>
          <ElButton :loading="pullingAll" type="warning" plain @click="handlePullAll">
            全局拉取往来单位
          </ElButton>
          <ElButton type="success" :disabled="!canLink" @click="linkSelected"> 建立对照 </ElButton>
          <ElButton plain @click="openManualCreate">手工新增</ElButton>
        </div>
        <div class="workbench-selection">
          <span class="workbench-label">已选</span>
          <ElTag
            v-if="selectedHisLabel"
            type="primary"
            effect="plain"
            closable
            @close="selectedHis = null"
          >
            院内：{{ selectedHisLabel }}
          </ElTag>
          <ElTag v-else type="info" effect="plain">院内：未选（点击左侧行）</ElTag>
          <span class="workbench-selection-arrow">→</span>
          <ElTag
            v-if="selectedPlatformLabel"
            type="success"
            effect="plain"
            closable
            @close="selectedPlatform = null"
          >
            平台：{{ selectedPlatformLabel }}
          </ElTag>
          <ElTag v-else type="info" effect="plain">平台：未选（点击右侧行）</ElTag>
          <ElButton
            v-if="selectedHisLabel || selectedPlatformLabel"
            link
            type="info"
            @click="clearSelection"
          >
            清空选中
          </ElButton>
        </div>
      </div>

      <div class="workbench-split">
        <section class="workbench-pane">
          <header class="workbench-pane-header">
            <div>
              <h3 class="workbench-pane-title">院内供应商</h3>
              <p class="workbench-pane-desc">默认看未对照；选中后与右侧平台企业建立对照</p>
            </div>
            <div class="workbench-stats">
              <ElStatistic title="本页未对照" :value="hisUnmappedCount" />
              <ElStatistic title="本页已对照" :value="hisMappedCount" />
              <ElStatistic title="合计" :value="hisTotal" />
            </div>
          </header>
          <div class="workbench-pane-body">
            <TableLayout
              v-model:query-params="hisQuery"
              :table-data="hisRows"
              :loading="hisLoading"
              :total="hisTotal"
              :form-items="hisFormItems"
              :table-columns="hisColumns"
              :show-top-query="true"
              :show-pagination="true"
              :highlight-current-row="true"
              :row-class-name="hisRowClassName"
              border
              stripe
              row-key="supplierId"
              @query="handleHisQuery"
              @reset="handleHisReset"
              @pagination="handleHisPagination"
              @row-click="(row) => onHisRowClick(row as MsfxHisSupplierWorkbenchRow)"
            >
              <template #mapped="{ row }">
                <ElTag v-if="row.mapped" type="success" effect="light">已对照</ElTag>
                <ElTag v-else type="warning" effect="light">未对照</ElTag>
              </template>
              <template #action="{ row }">
                <ElSpace>
                  <ElButton
                    link
                    type="primary"
                    @click.stop="selectHisOnly(row as MsfxHisSupplierWorkbenchRow)"
                  >
                    选中
                  </ElButton>
                  <ElButton
                    link
                    type="success"
                    @click.stop="linkHisToPlatform(row as MsfxHisSupplierWorkbenchRow)"
                  >
                    对照
                  </ElButton>
                  <ElButton
                    v-if="row.mapped"
                    link
                    type="danger"
                    @click.stop="unlinkHis(row as MsfxHisSupplierWorkbenchRow)"
                  >
                    解除
                  </ElButton>
                </ElSpace>
              </template>
            </TableLayout>
          </div>
        </section>

        <section class="workbench-pane">
          <header class="workbench-pane-header">
            <div>
              <h3 class="workbench-pane-title">平台往来单位</h3>
              <p class="workbench-pane-desc">
                {{
                  platformPulled
                    ? '来自码上放心 listparts；全局拉取会合并多页'
                    : '尚未拉取，请点「从平台拉取」或「全局拉取往来单位」'
                }}
              </p>
            </div>
            <div class="workbench-stats">
              <ElStatistic title="平台行数" :value="platformTotal" />
            </div>
          </header>
          <div class="workbench-pane-body">
            <TableLayout
              v-model:query-params="platformQuery"
              :table-data="platformRows"
              :loading="platformLoading || pullingAll"
              :total="platformTotal"
              :form-items="platformFormItems"
              :table-columns="platformColumns"
              :show-top-query="true"
              :show-pagination="!platformAllMode"
              :highlight-current-row="true"
              :row-class-name="platformRowClassName"
              border
              stripe
              row-key="entId"
              @query="handlePlatformQuery"
              @reset="handlePlatformReset"
              @pagination="handlePlatformPagination"
              @row-click="(row) => onPlatformRowClick(row as MsfxPlatformPartnerWorkbenchRow)"
            >
              <template #mapped="{ row }">
                <ElTag v-if="row.mapped" type="success" effect="light">已对照</ElTag>
                <ElTag v-else type="info" effect="light">未对照</ElTag>
              </template>
              <template #action="{ row }">
                <ElSpace>
                  <ElButton
                    link
                    type="primary"
                    @click.stop="selectPlatformOnly(row as MsfxPlatformPartnerWorkbenchRow)"
                  >
                    选中
                  </ElButton>
                  <ElButton
                    link
                    type="success"
                    @click.stop="linkPlatformToHis(row as MsfxPlatformPartnerWorkbenchRow)"
                  >
                    对照
                  </ElButton>
                </ElSpace>
              </template>
            </TableLayout>
          </div>
        </section>
      </div>
    </div>

    <EditDialog v-model:visible="editVisible" :form-data="editRow" @success="handleCompareQuery" />
  </Page>
</template>

<style scoped>
.workbench-root {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
  min-height: 0;
}

.workbench-toolbar {
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex-shrink: 0;
}

.workbench-toolbar-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.workbench-field {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 220px;
}

.workbench-field.grow {
  flex: 1 1 280px;
}

.workbench-label {
  flex-shrink: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  white-space: nowrap;
}

.workbench-selection {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 2px 0 0;
}

.workbench-selection-arrow {
  color: var(--el-text-color-secondary);
  font-weight: 600;
}

.workbench-split {
  display: flex;
  gap: 12px;
  flex: 1;
  min-height: 0;
}

.workbench-pane {
  display: flex;
  flex: 1 1 0;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
  overflow: hidden;
}

.workbench-pane-header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  padding: 12px 12px 0;
  flex-shrink: 0;
}

.workbench-pane-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.workbench-pane-desc {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.workbench-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.workbench-pane-body {
  flex: 1;
  min-height: 0;
  padding: 8px 12px 12px;
  overflow: auto;
}

/* VXE 当前选中行（highlight-current-row 实际只开悬停，需自定义 class） */
:deep(.vxe-body--row.msfx-row-selected),
:deep(tr.msfx-row-selected) {
  background-color: var(--el-color-primary-light-9) !important;
}

:deep(.vxe-body--row.msfx-row-selected > .vxe-body--column),
:deep(tr.msfx-row-selected > td) {
  background-color: transparent !important;
}
</style>
