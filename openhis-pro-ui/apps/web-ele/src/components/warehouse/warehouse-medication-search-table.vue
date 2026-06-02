<!--
  库房 medication-info 检索表格：弹层选药品共用。
  具体订货 / 损益 / 退货等接口由父组件通过 fetchPage 注入；
  列集：showBatchSupplierColumns（批次+供应商）、showSupplierColumn（仅供应商）。
-->
<script lang="ts" setup>
import { ref, watch } from 'vue';

import { useThrottleFn } from '@vueuse/core';
import { ElTable, ElTableColumn } from 'element-plus';

import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

defineOptions({
  name: 'WarehouseMedicationSearchTable',
});

/** medication-info 分页拉取（订货 / 损益等由业务注入） */
export type WarehouseMedicationPageFetcher = (
  params: Record<string, unknown>,
) => Promise<{ records?: Record<string, unknown>[] }>;

const props = withDefaults(
  defineProps<{
    keyword?: string;
    /** 库房维度（损益等必填；订货可不传） */
    locationId?: number | string;
    /** 供应商维度（采购退货选药等） */
    supplierId?: number | string;
    /** 药品类型等筛选；不传则不写入请求参数 */
    typeEnum?: number;
    /** 是否展示批次库存、供应商列（损益单常用） */
    showBatchSupplierColumns?: boolean;
    /** 仅展示供应商列（不退货批次列时用） */
    showSupplierColumn?: boolean;
    fetchPage: WarehouseMedicationPageFetcher;
  }>(),
  {
    keyword: '',
    showBatchSupplierColumns: false,
    showSupplierColumn: false,
  },
);

const emit = defineEmits<{
  selectRow: [row: Record<string, unknown>];
}>();

const queryParams = ref({
  pageNo: 1,
  pageSize: 50,
});

const medicineList = ref<Record<string, unknown>[]>([]);

/** 组装查询参数并调用业务注入的分页接口 */
async function loadList() {
  const body: Record<string, unknown> = {
    pageNo: queryParams.value.pageNo,
    pageSize: queryParams.value.pageSize,
    keyword: props.keyword ?? '',
  };
  const loc = props.locationId;
  if (loc !== undefined && loc !== null && `${loc}` !== '') {
    body.locationId = loc;
  }
  const sup = props.supplierId;
  if (sup !== undefined && sup !== null && `${sup}` !== '') {
    body.supplierId = sup;
  }
  if (props.typeEnum !== undefined && props.typeEnum !== null) {
    body.typeEnum = props.typeEnum;
  }
  try {
    const data = await props.fetchPage(body);
    const { content } = parsePagedRowsFromApiData(data);
    medicineList.value = content as Record<string, unknown>[];
  } catch {
    medicineList.value = [];
  }
}

/** 节流刷新，减轻检索输入抖动压力 */
const throttledLoad = useThrottleFn(() => void loadList(), 300);

watch(
  () => [props.keyword, props.locationId, props.supplierId, props.typeEnum],
  () => {
    queryParams.value.pageNo = 1;
    throttledLoad();
  },
  { immediate: true },
);

/** 单元格点击选中一行药品 */
function clickRow(row: Record<string, unknown>) {
  emit('selectRow', row);
}
</script>

<template>
  <div>
    <ElTable
      border
      class="w-full"
      height="400"
      size="small"
      stripe
      :data="medicineList"
      @cell-click="clickRow"
    >
      <ElTableColumn
        align="center"
        label="项目编码"
        prop="busNo"
        show-overflow-tooltip
        width="150"
      />
      <ElTableColumn
        align="center"
        label="项目名称"
        prop="name"
        show-overflow-tooltip
        width="180"
      />
      <ElTableColumn align="center" label="进货价" prop="price" />
      <ElTableColumn align="center" label="零售价" prop="retailPrice" />
      <ElTableColumn align="center" label="包装单位" prop="unitCode_dictText" />
      <ElTableColumn align="center" label="最小单位" prop="minUnitCode_dictText" />
      <ElTableColumn align="center" label="规格" prop="totalVolume" show-overflow-tooltip />
      <ElTableColumn align="center" label="规格库存" prop="specificationInventory" />
      <template v-if="showBatchSupplierColumns">
        <ElTableColumn align="center" label="批次库存" prop="batchInventory" />
        <ElTableColumn
          align="center"
          label="供应商"
          prop="supplierId_dictText"
          show-overflow-tooltip
        />
      </template>
      <ElTableColumn
        v-else-if="showSupplierColumn"
        align="center"
        label="供应商"
        prop="supplierId_dictText"
        show-overflow-tooltip
      />
      <ElTableColumn
        align="center"
        label="生产厂家"
        prop="manufacturerText"
        show-overflow-tooltip
      />
      <ElTableColumn align="center" label="批准文号" prop="approvalNumber" show-overflow-tooltip />
    </ElTable>
  </div>
</template>
