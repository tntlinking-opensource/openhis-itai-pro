<script setup lang="ts">
import type { CatalogMatchCandidate, NationalCatalogItem } from '#/chs/api';
import type { ChargeType } from '#/api/core/common';

import { computed, ref, watch } from 'vue';

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
  YB_CATALOG_LIST_TYPE_OPTIONS,
} from '#/chs/constants/catalog-list-type';
import {
  filterInsuranceChargeTypes,
  resolveDefaultInsuranceBusNo,
} from '#/chs/constants/insurance-contract';
import { ensureInsuranceSignIn } from '#/chs/utils/ensure-insurance-sign-in';
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
const recommendLevel = ref<string>();
const recommendHint = ref<string>();
const selectedRow = ref<CatalogMatchCandidate | null>(null);

const chargeTypeOptions = ref<ChargeType[]>([]);

const levelTagType = computed(() => {
  if (recommendLevel.value === 'UNIQUE_SUGGEST') return 'success';
  if (recommendLevel.value === 'NO_MATCH') return 'warning';
  return 'info';
});

const { run: runSuggest, loading: suggestLoading } = useRequest(
  suggestMedicationCatalogMappingApi,
  {
    manual: true,
    onSuccess: (data) => {
      recommendLevel.value = data.recommendLevel;
      recommendHint.value = data.hint;
      if (data.candidates?.length) {
        tableData.value = data.candidates;
        total.value = data.candidates.length;
        if (data.recommendLevel === 'UNIQUE_SUGGEST' && data.candidates[0]) {
          selectedRow.value = data.candidates[0];
        }
      }
    },
  },
);

const { run: runPage, loading: pageLoading } = useRequest(pageNationalCatalogApi, {
  manual: true,
  onSuccess: (page) => {
    tableData.value = page.rows ?? [];
    total.value = page.total ?? 0;
  },
});

const loading = computed(() => suggestLoading.value || pageLoading.value);

async function loadContracts() {
  const list = await getChargeTypeListApi();
  const eligible = filterInsuranceChargeTypes(list ?? []);
  chargeTypeOptions.value = eligible;
  contractBusNo.value = resolveDefaultInsuranceBusNo(eligible, contractBusNo.value);
}

async function runSearch(resetPage = true) {
  if (!contractBusNo.value) {
    ElMessage.warning('请选择医保契约');
    return;
  }
  const signedIn = await ensureInsuranceSignIn(contractBusNo.value);
  if (!signedIn) return;
  if (resetPage) pageNo.value = 1;
  selectedRow.value = null;
  await runSuggest({
    contractBusNo: contractBusNo.value,
    listType: listType.value,
    name: props.context.name || keyword.value || undefined,
    approvalNumber: aprvno.value || props.context.approvalNumber,
    nationalDrugCode: props.context.nationalDrugCode,
    totalVolume: props.context.totalVolume,
    queryPlatformIfMiss: true,
    maxCandidates: 30,
  });
  if (recommendLevel.value === 'NO_MATCH' || !tableData.value.length) {
    const use1301Detail = Boolean(aprvno.value?.trim());
    await runPage({
      contractBusNo: contractBusNo.value,
      listType: listType.value,
      keyword: keyword.value || props.context.name,
      aprvno: aprvno.value || undefined,
      valiFlag: '1',
      sourceInfno: use1301Detail ? '1301' : '1312',
      dedupeByHilistCode: !use1301Detail,
      queryPlatformIfMiss: true,
      pageNo: pageNo.value,
      pageSize: pageSize.value,
    });
  }
}

function handlePageChange(p: number) {
  pageNo.value = p;
  const use1301Detail = Boolean(aprvno.value?.trim());
  void runPage({
    contractBusNo: contractBusNo.value!,
    listType: listType.value,
    keyword: keyword.value || props.context.name,
    aprvno: aprvno.value || undefined,
    valiFlag: '1',
    sourceInfno: use1301Detail ? '1301' : '1312',
    dedupeByHilistCode: !use1301Detail,
    queryPlatformIfMiss: false,
    pageNo: pageNo.value,
    pageSize: pageSize.value,
  });
}

async function handleConfirm() {
  if (!selectedRow.value?.hilistCode) {
    ElMessage.warning('请先选择一条国家目录记录');
    return;
  }
  const level = recommendLevel.value;
  const msg =
    level === 'UNIQUE_SUGGEST'
      ? `确认选用医保编码 ${selectedRow.value.hilistCode}？\n${selectedRow.value.hilistName ?? ''}`
      : `当前为多条候选，请确认选用：\n${selectedRow.value.hilistCode} — ${selectedRow.value.hilistName ?? ''}`;
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

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return;
    keyword.value = props.context.name ?? '';
    aprvno.value = props.context.approvalNumber ?? '';
    listType.value = props.context.listType ?? '101';
    selectedRow.value = null;
    recommendLevel.value = undefined;
    recommendHint.value = undefined;
    await loadContracts();
    await runSearch(true);
  },
);
</script>

<template>
  <ElDialog
    v-model="visible"
    title="从国家医保目录选择"
    width="960px"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
  >
    <div v-loading="loading" class="flex flex-col gap-3">
      <ElAlert
        type="info"
        :closable="false"
        show-icon
        title="选码说明"
        description="默认展示 1312 摘要（按医保编码去重）。填写批准文号或规格时将查 1301 文件明细（含厂家）；须您确认后回填。"
      />
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
        <ElSelect v-model="listType" style="width: 120px">
          <ElOption
            v-for="opt in YB_CATALOG_LIST_TYPE_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </ElSelect>
        <ElInput v-model="keyword" placeholder="名称关键字" clearable style="width: 160px" />
        <ElInput v-model="aprvno" placeholder="批准文号" clearable style="width: 160px" />
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
        max-height="360"
        @current-change="(row: CatalogMatchCandidate | undefined) => (selectedRow = row ?? null)"
      >
        <ElTableColumn prop="matchScore" label="得分" width="64" />
        <ElTableColumn prop="hilistCode" label="医保编码" width="140" />
        <ElTableColumn prop="hilistName" label="名称" min-width="160" show-overflow-tooltip />
        <ElTableColumn prop="chrgitmLv" label="等级" width="72">
          <template #default="{ row }">{{ formatChrgitmLv(row.chrgitmLv) }}</template>
        </ElTableColumn>
        <ElTableColumn prop="dosform" label="剂型" width="88" show-overflow-tooltip />
        <ElTableColumn prop="aprvno" label="批准文号" width="140" show-overflow-tooltip />
        <ElTableColumn prop="prodentpName" label="生产企业" min-width="140" show-overflow-tooltip />
        <ElTableColumn prop="spec" label="规格" width="100" show-overflow-tooltip />
        <ElTableColumn prop="listType" label="类型" width="80">
          <template #default="{ row }">{{ formatCatalogListType(row.listType) }}</template>
        </ElTableColumn>
        <ElTableColumn prop="valiFlag" label="有效" width="64">
          <template #default="{ row }">{{ formatValiFlag(row.valiFlag) }}</template>
        </ElTableColumn>
        <ElTableColumn prop="matchReason" label="命中" min-width="120" show-overflow-tooltip />
        <ElTableColumn prop="sourceInfno" label="来源" width="64" />
      </ElTable>
      <div v-if="total > pageSize" class="flex justify-end">
        <ElButton :disabled="pageNo <= 1" @click="handlePageChange(pageNo - 1)"> 上一页 </ElButton>
        <span class="mx-2 text-sm">{{ pageNo }} / {{ Math.ceil(total / pageSize) }}</span>
        <ElButton :disabled="pageNo * pageSize >= total" @click="handlePageChange(pageNo + 1)">
          下一页
        </ElButton>
      </div>
    </div>
    <template #footer>
      <ElButton @click="visible = false">取消</ElButton>
      <ElButton type="primary" :disabled="!selectedRow" @click="handleConfirm">确认选用</ElButton>
    </template>
  </ElDialog>
</template>
