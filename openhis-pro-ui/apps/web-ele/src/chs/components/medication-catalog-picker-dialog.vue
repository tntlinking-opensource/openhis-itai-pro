<script setup lang="ts">
import type { CatalogMatchCandidate, NationalCatalogItem } from '#/chs/api';
import type { ChargeType } from '#/api/core/common';

import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import {
  ElAlert,
  ElButton,
  ElDialog,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getChargeTypeListApi } from '#/api/core/common';
import { pageNationalCatalogApi, suggestMedicationCatalogMappingApi } from '#/chs/api';
import {
  formatCatalogListType,
  formatChrgitmLv,
  formatValiFlag,
  resolveCatalogDetailSourceInfno,
  YB_CATALOG_LIST_TYPE_OPTIONS,
} from '#/chs/constants/catalog-list-type';
import {
  filterInsuranceChargeTypes,
  resolveDefaultInsuranceBusNo,
} from '#/chs/constants/insurance-contract';
import { useRequest } from '#/hooks/use-request';

export interface MedicationPickerContext {
  name?: string;
  approvalNumber?: string;
  nationalDrugCode?: string;
  totalVolume?: string;
  listType?: string;
}

const props = defineProps<{
  modelValue: boolean;
  context: MedicationPickerContext;
}>();

const emit = defineEmits<{
  'update:modelValue': [boolean];
  confirm: [NationalCatalogItem];
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
});

const contractBusNo = ref<string>();
const listType = ref(props.context.listType ?? '101');
const keyword = ref('');
const aprvno = ref(props.context.approvalNumber ?? '');
const tableData = ref<CatalogMatchCandidate[]>([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = ref(20);
/** suggest=推荐列表（不分页）；page=本地镜像分页浏览 */
const resultMode = ref<'suggest' | 'page'>('suggest');
/** page 模式下实际查询的来源：优先 1312，空结果再按目录类型回退 1301/1302/1305/1306 */
const pageSourceInfno = ref<string>('1312');
const recommendLevel = ref<string>();
const recommendHint = ref<string>();
const selectedRow = ref<CatalogMatchCandidate | null>(null);
/** 选码说明默认折叠，把垂直空间留给表格 */
const guideExpanded = ref(false);

const chargeTypeOptions = ref<ChargeType[]>([]);

/** 接近全屏宽度，上限 1480px */
const dialogWidth = 'min(96vw, 1680px)';

const viewportHeight = ref(typeof window === 'undefined' ? 900 : window.innerHeight);

/** 仅西药中成药(101)有批准文号维度；饮片/诊疗/耗材不展示 */
const showAprvnoFilter = computed(() => listType.value === '101');

/** 按目录类型解析 1312 空结果时的明细回退接口号 */
function resolveDetailSourceInfno(type: string | undefined): string {
  return resolveCatalogDetailSourceInfno(type);
}

const guideDescription = computed(() => {
  const detail = resolveDetailSourceInfno(listType.value);
  const detailLabelMap: Record<string, string> = {
    '1301': '1301 西药中成药明细',
    '1302': '1302 中药饮片明细',
    '1305': '1305 医疗服务明细',
    '1306': '1306 耗材明细',
  };
  const detailLabel = detailLabelMap[detail] ?? detail;
  const aprvHint = showAprvnoFilter.value ? '；填写批准文号时查 1301 明细' : '';
  return `仅查询本地已下载的国家目录镜像，不出站调用医保。结果已按已应用局文合成：停用码不可选；甲乙类/限价优先取局文补丁。默认 1312 去重，无结果时回退 ${detailLabel}${aprvHint}。无数据请先到「国家目录」页下载/导入（饮片请先下 1302；若仅有 1301/1312，饮片关键字会放宽检索）。`;
});

function syncViewportHeight() {
  viewportHeight.value = window.innerHeight;
}

/**
 * 表格可视高度：约 100vh - 320px，下限 420、上限 70vh，
 * 保证筛选区 / 页脚仍可见。
 */
const tableMaxHeight = computed(() => {
  const byOffset = viewportHeight.value - 320;
  const byCap = Math.floor(viewportHeight.value * 0.7);
  return Math.max(420, Math.min(byOffset, byCap));
});

onMounted(() => {
  syncViewportHeight();
  window.addEventListener('resize', syncViewportHeight);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncViewportHeight);
});

const levelTagType = computed(() => {
  if (recommendLevel.value === 'UNIQUE_SUGGEST') return 'success';
  if (recommendLevel.value === 'NO_MATCH') return 'warning';
  return 'info';
});

const showPager = computed(() => resultMode.value === 'page' && total.value > pageSize.value);

function isRowSelectable(row: CatalogMatchCandidate | null | undefined): boolean {
  if (!row?.hilistCode) return false;
  return row.isSelectable !== false;
}

function firstSelectable(rows: CatalogMatchCandidate[] | undefined): CatalogMatchCandidate | null {
  if (!rows?.length) return null;
  return rows.find((r) => isRowSelectable(r)) ?? null;
}

const canConfirm = computed(() => isRowSelectable(selectedRow.value));

function buildPageParams(page: number, sourceInfno: string = pageSourceInfno.value) {
  const useDrugDetail =
    showAprvnoFilter.value && Boolean(aprvno.value?.trim()) && sourceInfno === '1301';
  const searchName = (keyword.value || props.context.name || '').trim();
  return {
    contractBusNo: contractBusNo.value!,
    listType: listType.value,
    keyword: searchName || undefined,
    aprvno: showAprvnoFilter.value ? aprvno.value?.trim() || undefined : undefined,
    valiFlag: '1',
    sourceInfno,
    // 无批准文号明细模式时按 hilist_code 去重
    dedupeByHilistCode: !useDrugDetail,
    excludePlatformExpired: true,
    pageNo: page,
    pageSize: pageSize.value,
  };
}

const { run: runSuggest, loading: suggestLoading } = useRequest(
  suggestMedicationCatalogMappingApi,
  {
    manual: true,
    onSuccess: (data) => {
      recommendLevel.value = data.recommendLevel;
      recommendHint.value = data.hint;
      if (data.candidates?.length) {
        resultMode.value = 'suggest';
        tableData.value = data.candidates;
        total.value = data.candidates.length;
        pageNo.value = 1;
        if (data.recommendLevel === 'UNIQUE_SUGGEST') {
          selectedRow.value = firstSelectable(data.candidates);
        }
      }
    },
  },
);

const { run: runPage, loading: pageLoading } = useRequest(pageNationalCatalogApi, {
  manual: true,
  onSuccess: (page) => {
    resultMode.value = 'page';
    tableData.value = page.content ?? [];
    total.value = page.totalCount ?? 0;
  },
});

const loading = computed(() => suggestLoading.value || pageLoading.value);

function trimOrUndef(v: string | undefined | null): string | undefined {
  const t = v?.trim();
  return t ? t : undefined;
}

async function loadContracts() {
  const list = await getChargeTypeListApi();
  const eligible = filterInsuranceChargeTypes(list ?? []);
  chargeTypeOptions.value = eligible;
  contractBusNo.value = resolveDefaultInsuranceBusNo(eligible, contractBusNo.value);
}

async function runPageBrowse(page: number) {
  const hasAprvno = showAprvnoFilter.value && Boolean(aprvno.value?.trim());
  const detailSource = resolveDetailSourceInfno(listType.value);
  // 有批准文号时直查明细源；否则先 1312，空再回退明细
  pageSourceInfno.value = hasAprvno ? detailSource : '1312';
  await runPage(buildPageParams(page, pageSourceInfno.value));
  // 无批准文号且 1312 无结果时，按目录类型回退本地明细（去重）
  if (!hasAprvno && !tableData.value.length) {
    pageSourceInfno.value = detailSource;
    await runPage(buildPageParams(page, detailSource));
  }
}

async function runSearch(resetPage = true) {
  if (!contractBusNo.value) {
    ElMessage.warning('请选择医保契约');
    return;
  }
  // 仅查本地国家目录镜像，不签到、不触发 1312 平台补库
  if (resetPage) pageNo.value = 1;
  selectedRow.value = null;
  tableData.value = [];
  total.value = 0;
  resultMode.value = 'suggest';
  pageSourceInfno.value = '1312';
  recommendLevel.value = undefined;
  recommendHint.value = undefined;
  const searchName = trimOrUndef(keyword.value) || trimOrUndef(props.context.name);
  const approvalNumber = showAprvnoFilter.value
    ? trimOrUndef(aprvno.value) || trimOrUndef(props.context.approvalNumber)
    : undefined;
  // 输入框像医保码时同时走 nationalDrugCode，便于精确命中
  const codeLike = searchName && /^[A-Za-z0-9]{6,50}$/.test(searchName) ? searchName : undefined;
  await runSuggest({
    contractBusNo: contractBusNo.value,
    listType: listType.value,
    name: searchName,
    approvalNumber,
    nationalDrugCode:
      codeLike ||
      (showAprvnoFilter.value ? trimOrUndef(props.context.nationalDrugCode) : undefined),
    totalVolume: showAprvnoFilter.value ? trimOrUndef(props.context.totalVolume) : undefined,
    queryPlatformIfMiss: false,
    maxCandidates: 30,
  });
  // suggest 无结果时再走分页浏览（含 1302/1301 明细回退），避免有关键字时跳过本地文件镜像
  if (recommendLevel.value === 'NO_MATCH' || !tableData.value.length) {
    await runPageBrowse(pageNo.value);
  }
}

function handlePageChange(p: number) {
  if (resultMode.value !== 'page') return;
  pageNo.value = p;
  selectedRow.value = null;
  void runPage(buildPageParams(pageNo.value, pageSourceInfno.value));
}

function handleCurrentChange(row: CatalogMatchCandidate | undefined) {
  if (!row) {
    selectedRow.value = null;
    return;
  }
  if (!isRowSelectable(row)) {
    ElMessage.warning(row.unselectableReason || '该编码当前不可选（局文/平台停用）');
    selectedRow.value = null;
    return;
  }
  selectedRow.value = row;
}

function rowClassName({ row }: { row: CatalogMatchCandidate }) {
  return isRowSelectable(row) ? '' : 'catalog-picker-row--disabled';
}

/** 目录包装/计价单位提示（选码确认用） */
function formatPackHint(row: CatalogMatchCandidate): string {
  const cnt = String(row.minPacCnt ?? '').trim();
  const pacunt = String(row.minPacunt ?? '').trim();
  const prcunt = String(row.minPrcunt ?? '').trim();
  const prepunt = String(row.minPrepunt ?? '').trim();
  const pack = cnt || pacunt ? `目录包装：${cnt || '-'}${pacunt}` : '';
  const priceUnit = prcunt ? `计价单位：${prcunt}` : '';
  const prep = prepunt ? `制剂单位：${prepunt}` : '';
  return [pack, priceUnit, prep].filter(Boolean).join('；');
}

async function handleConfirm() {
  if (!selectedRow.value?.hilistCode) {
    ElMessage.warning('请先选择一条国家目录记录');
    return;
  }
  if (!isRowSelectable(selectedRow.value)) {
    ElMessage.warning(selectedRow.value.unselectableReason || '该编码当前不可选');
    return;
  }
  const level = recommendLevel.value;
  const lvText = formatChrgitmLv(selectedRow.value.chrgitmLv);
  const priceText = selectedRow.value.priceUplmtAmt
    ? `\n限价上限：${selectedRow.value.priceUplmtAmt}`
    : '';
  const packHint = formatPackHint(selectedRow.value);
  const packText = packHint ? `\n${packHint}` : '';
  const msg =
    level === 'UNIQUE_SUGGEST'
      ? `确认选用医保编码 ${selectedRow.value.hilistCode}？\n${selectedRow.value.hilistName ?? ''}\n支付等级：${lvText}${priceText}${packText}`
      : `当前为多条候选，请确认选用：\n${selectedRow.value.hilistCode} — ${selectedRow.value.hilistName ?? ''}\n支付等级：${lvText}${priceText}${packText}`;
  try {
    await ElMessageBox.confirm(msg, '确认医保目录对码', {
      type: level === 'MULTI_CHOICE' ? 'warning' : 'info',
      confirmButtonText: '确认选用',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  emit('confirm', selectedRow.value);
  visible.value = false;
}

watch(listType, (t) => {
  if (t !== '101') {
    aprvno.value = '';
  }
});

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return;
    keyword.value = props.context.name ?? '';
    listType.value = props.context.listType ?? '101';
    aprvno.value = listType.value === '101' ? (props.context.approvalNumber ?? '') : '';
    selectedRow.value = null;
    tableData.value = [];
    total.value = 0;
    pageNo.value = 1;
    recommendLevel.value = undefined;
    recommendHint.value = undefined;
    resultMode.value = 'suggest';
    pageSourceInfno.value = '1312';
    guideExpanded.value = false;
    syncViewportHeight();
    await loadContracts();
  },
);
</script>

<template>
  <ElDialog
    v-model="visible"
    title="从国家医保目录选择"
    :width="dialogWidth"
    class="medication-catalog-picker-dialog"
    append-to-body
    destroy-on-close
    align-center
    :close-on-click-modal="false"
  >
    <div v-loading="loading" class="catalog-picker-body flex flex-col gap-3">
      <ElAlert type="info" :closable="false" show-icon class="catalog-picker-guide">
        <template #title>
          <div class="flex items-center justify-between gap-2">
            <span>选码说明</span>
            <ElButton link type="primary" @click="guideExpanded = !guideExpanded">
              {{ guideExpanded ? '收起' : '展开' }}
            </ElButton>
          </div>
        </template>
        <p v-if="guideExpanded" class="catalog-picker-guide__desc m-0 text-sm leading-5">
          {{ guideDescription }}
        </p>
      </ElAlert>
      <div class="flex flex-wrap items-center gap-2">
        <span class="text-sm text-gray-600">医保契约</span>
        <ElSelect v-model="contractBusNo" style="width: 220px" placeholder="契约">
          <ElOption
            v-for="item in chargeTypeOptions"
            :key="item.busNo"
            :label="item.busNo ? `${item.label}（${item.busNo}）` : item.label"
            :value="item.busNo"
          />
        </ElSelect>
        <span class="text-sm text-gray-600">目录类型</span>
        <ElSelect v-model="listType" style="width: 140px">
          <ElOption
            v-for="opt in YB_CATALOG_LIST_TYPE_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </ElSelect>
        <ElInput
          v-model="keyword"
          placeholder="名称 / 医保码"
          clearable
          style="width: 200px"
          @keyup.enter="runSearch(true)"
        />
        <ElInput
          v-if="showAprvnoFilter"
          v-model="aprvno"
          placeholder="批准文号"
          clearable
          style="width: 160px"
        />
        <ElButton type="primary" @click="runSearch(true)">搜索 / 推荐</ElButton>
      </div>
      <ElAlert
        v-if="recommendHint"
        :type="levelTagType"
        :closable="false"
        show-icon
        :title="
          recommendLevel === 'UNIQUE_SUGGEST'
            ? '高置信推荐'
            : recommendLevel === 'NO_MATCH'
              ? '无匹配'
              : '需人工选择'
        "
        :description="recommendHint"
      />
      <ElTable
        :data="tableData"
        highlight-current-row
        :max-height="tableMaxHeight"
        :row-class-name="rowClassName"
        @current-change="handleCurrentChange"
      >
        <ElTableColumn prop="matchScore" label="得分" width="56" />
        <ElTableColumn prop="hilistCode" label="医保编码" min-width="200" show-overflow-tooltip />
        <ElTableColumn prop="hilistName" label="名称" min-width="220" show-overflow-tooltip />
        <ElTableColumn prop="chrgitmLv" label="等级" width="56">
          <template #default="{ row }">{{ formatChrgitmLv(row.chrgitmLv) }}</template>
        </ElTableColumn>
        <ElTableColumn label="可选" width="72">
          <template #default="{ row }">
            <ElTag v-if="isRowSelectable(row)" type="success" size="small">可选</ElTag>
            <ElTag v-else type="danger" size="small" :title="row.unselectableReason">
              {{ row.unselectableReason || '不可选' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="priceUplmtAmt" label="限价" width="72" show-overflow-tooltip />
        <ElTableColumn label="目录包装" width="80" show-overflow-tooltip>
          <template #default="{ row }">
            {{ [row.minPacCnt, row.minPacunt].filter(Boolean).join('') || '-' }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="minPrcunt" label="计价单位" width="72" show-overflow-tooltip />
        <ElTableColumn prop="minPrepunt" label="制剂单位" width="72" show-overflow-tooltip />
        <ElTableColumn prop="dosform" label="剂型" width="72" show-overflow-tooltip />
        <ElTableColumn prop="aprvno" label="批准文号" min-width="160" show-overflow-tooltip />
        <ElTableColumn prop="prodentpName" label="生产企业" min-width="200" show-overflow-tooltip />
        <ElTableColumn prop="spec" label="规格" min-width="160" show-overflow-tooltip />
        <ElTableColumn prop="listType" label="类型" width="64">
          <template #default="{ row }">{{ formatCatalogListType(row.listType) }}</template>
        </ElTableColumn>
        <ElTableColumn prop="valiFlag" label="有效" width="56">
          <template #default="{ row }">{{ formatValiFlag(row.valiFlag) }}</template>
        </ElTableColumn>
        <ElTableColumn prop="matchReason" label="命中" min-width="120" show-overflow-tooltip />
        <ElTableColumn prop="sourceInfno" label="来源" width="56" />
      </ElTable>
      <div v-if="showPager" class="flex justify-end">
        <ElButton :disabled="pageNo <= 1" @click="handlePageChange(pageNo - 1)"> 上一页 </ElButton>
        <span class="mx-2 text-sm">{{ pageNo }} / {{ Math.ceil(total / pageSize) }}</span>
        <ElButton :disabled="pageNo * pageSize >= total" @click="handlePageChange(pageNo + 1)">
          下一页
        </ElButton>
      </div>
    </div>
    <template #footer>
      <ElButton @click="visible = false">取消</ElButton>
      <ElButton type="primary" :disabled="!canConfirm" @click="handleConfirm">确认选用</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.catalog-picker-body {
  overflow: hidden;
}

.catalog-picker-guide :deep(.el-alert__content) {
  width: 100%;
}

.catalog-picker-guide__desc {
  color: var(--el-text-color-regular);
}

:deep(.catalog-picker-row--disabled) {
  color: var(--el-text-color-disabled);
  cursor: not-allowed;
  opacity: 0.65;
}
</style>

<style>
/* append-to-body 后需非 scoped 才能作用到 dialog 本体 */
.medication-catalog-picker-dialog.el-dialog {
  max-width: 1680px;
}

.medication-catalog-picker-dialog .el-dialog__body {
  overflow: hidden;
  padding-top: 12px;
  padding-bottom: 8px;
}
</style>
