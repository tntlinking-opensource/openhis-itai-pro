<script setup lang="ts">
import type {
  ElepCatalogItemDto,
  ElepCatalogPickerKind,
} from '#/api/doctor-station/elep-catalog.types';

import { computed, ref, watch } from 'vue';

import { ElButton, ElDialog, ElInput, ElPagination, ElTable, ElTableColumn } from 'element-plus';

import {
  httpSearchElepDiagnosisCatalog,
  httpSearchElepDrugCatalog,
} from '#/api/doctor-station/elep-catalog.http';
import { useRequest } from '#/hooks/use-request';

const props = defineProps<{
  modelValue: boolean;
  kind: ElepCatalogPickerKind;
  contractBusNo?: string;
  contractId?: string;
}>();

const emit = defineEmits<{
  'update:modelValue': [boolean];
  confirm: [ElepCatalogItemDto];
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
});

const keyword = ref('');
const pageNo = ref(1);
const pageSize = ref(20);
const total = ref(0);
const tableData = ref<ElepCatalogItemDto[]>([]);
const selectedRow = ref<ElepCatalogItemDto | null>(null);

const title = computed(() => (props.kind === 'drug' ? '选择医保药品' : '选择医保诊断'));

const { run: runSearch, loading } = useRequest(
  async () => {
    const busNo = props.contractBusNo?.trim();
    if (!busNo) {
      tableData.value = [];
      total.value = 0;
      return;
    }
    const request = {
      contractBusNo: busNo,
      contractId: props.contractId,
      keyword: keyword.value.trim(),
      page: Math.max(pageNo.value - 1, 0),
      pageSize: pageSize.value,
    };
    const res =
      props.kind === 'drug'
        ? await httpSearchElepDrugCatalog(request)
        : await httpSearchElepDiagnosisCatalog(request);
    tableData.value = res.items ?? [];
    total.value = Number(res.total ?? 0);
  },
  { showErrorMessage: true },
);

function handleSearch() {
  pageNo.value = 1;
  void runSearch();
}

function handleConfirm() {
  if (!selectedRow.value) return;
  emit('confirm', selectedRow.value);
  visible.value = false;
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return;
    selectedRow.value = null;
    keyword.value = '';
    pageNo.value = 1;
    void runSearch();
  },
);

watch([pageNo, pageSize], () => {
  if (visible.value) void runSearch();
});
</script>

<template>
  <ElDialog v-model="visible" :title="title" destroy-on-close width="760px">
    <div class="mb-3 flex flex-nowrap items-center gap-2">
      <div class="min-w-0 flex-1">
        <ElInput
          v-model="keyword"
          class="w-full"
          clearable
          placeholder="名称 / 拼音 / 医保编码（≥2 字）"
          @keyup.enter="handleSearch"
        />
      </div>
      <ElButton class="shrink-0" :loading="loading" type="primary" @click="handleSearch">
        搜索
      </ElButton>
    </div>
    <ElTable
      v-loading="loading"
      :data="tableData"
      highlight-current-row
      max-height="360"
      size="small"
      @current-change="(row) => (selectedRow = row)"
    >
      <ElTableColumn label="医保编码" min-width="140" prop="hilistCode" />
      <ElTableColumn label="名称" min-width="180" prop="hilistName" show-overflow-tooltip />
      <ElTableColumn label="规格" min-width="100" prop="spec" show-overflow-tooltip />
      <ElTableColumn label="剂型" min-width="80" prop="dosform" />
      <ElTableColumn label="来源" width="72" prop="sourceInfno" />
    </ElTable>
    <div class="mt-3 flex justify-end">
      <ElPagination
        v-model:current-page="pageNo"
        v-model:page-size="pageSize"
        :total="total"
        background
        layout="total, prev, pager, next"
        small
      />
    </div>
    <template #footer>
      <ElButton @click="visible = false">取消</ElButton>
      <ElButton :disabled="!selectedRow" type="primary" @click="handleConfirm">确定</ElButton>
    </template>
  </ElDialog>
</template>
