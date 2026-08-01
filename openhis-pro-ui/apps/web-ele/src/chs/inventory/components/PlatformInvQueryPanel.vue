<script setup lang="ts">
/**
 * 进销存平台查询面板：按序 3508–3513。
 * 3508–3511 须日期 +（批次号或机构目录编码）；3512/3513 五选一键即可（日期可选）。
 */
import type { QueryPlatformInvParams } from '#/chs/api';

import { computed, ref, watch } from 'vue';

import {
  ElAlert,
  ElButton,
  ElDatePicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElTabPane,
  ElTable,
  ElTableColumn,
  ElTabs,
} from 'element-plus';
import dayjs from 'dayjs';

import {
  queryPlatformInboundTrace3512Api,
  queryPlatformInvChange3509Api,
  queryPlatformInventory3508Api,
  queryPlatformPurchase3510Api,
  queryPlatformSale3511Api,
  queryPlatformSaleTrace3513Api,
} from '#/chs/api';
import { ensureInsuranceSignIn } from '#/chs/utils/ensure-insurance-sign-in';

defineOptions({ name: 'ChsPlatformInvQueryPanel' });

interface Props {
  /** 当前医保契约 busNo */
  contractBusNo?: string;
}

const props = withDefaults(defineProps<Props>(), {
  contractBusNo: undefined,
});

/** 平台查询交易号（按规范顺序） */
type PlatformQueryInfno = '3508' | '3509' | '3510' | '3511' | '3512' | '3513';

interface PlatformQueryColumn {
  label: string;
  camelKey: string;
  snakeKey: string;
  minWidth?: number;
  width?: number;
  align?: 'left' | 'center' | 'right';
}

const platformQueryTabs: Array<{ label: string; value: PlatformQueryInfno; tip: string }> = [
  {
    label: '3508 库存',
    value: '3508',
    tip: '查平台库存盘存信息。批次流水号与机构目录编码至少填一项；日期必填。',
  },
  {
    label: '3509 变更',
    value: '3509',
    tip: '查平台库存变更记录。批次流水号与机构目录编码至少填一项；日期必填。',
  },
  {
    label: '3510 采购',
    value: '3510',
    tip: '查平台采购/采购退货。吉林 3504 对单用原采购批次号（CIR），不是退供 CRT。',
  },
  {
    label: '3511 销售',
    value: '3511',
    tip: '查平台销售/销售退货。批次流水号与机构目录编码至少填一项；日期必填。',
  },
  {
    label: '3512 入库追溯',
    value: '3512',
    tip: '查入库追溯码。医保目录/机构目录/批次号/追溯码/证件号至少填一项。',
  },
  {
    label: '3513 销售追溯',
    value: '3513',
    tip: '查销售追溯码。医保目录/机构目录/批次号/追溯码/证件号至少填一项。',
  },
];

const activeQueryInfno = ref<PlatformQueryInfno>('3508');
const queryDateRange = ref<[string, string] | undefined>();
const queryBchno = ref('');
const queryMedinsListCodg = ref('');
const queryMedListCodg = ref('');
const queryMedinsProdPurcNo = ref('');
const queryMedinsProdSelNo = ref('');
const queryDrugTracCodg = ref('');
const queryCertno = ref('');
const queryLoading = ref(false);
const queryRows = ref<Array<Record<string, unknown>>>([]);
const detailVisible = ref(false);
const detailJson = ref('');

/** 当前 Tab 说明文案 */
const activeQueryTip = computed(() => {
  const hit = platformQueryTabs.find((t) => t.value === activeQueryInfno.value);
  return hit?.tip ?? '';
});

/** 是否追溯类（3512/3513）：日期非强制 */
const isTraceQuery = computed(() => {
  return activeQueryInfno.value === '3512' || activeQueryInfno.value === '3513';
});

/** 按交易号展示的关键列 */
const queryColumns = computed((): PlatformQueryColumn[] => {
  const common: PlatformQueryColumn[] = [
    { label: '批次流水号', camelKey: 'fixmedinsBchno', snakeKey: 'fixmedins_bchno', minWidth: 150 },
    { label: '医保目录编码', camelKey: 'medListCodg', snakeKey: 'med_list_codg', minWidth: 150 },
    {
      label: '机构目录编码',
      camelKey: 'medinsListCodg',
      snakeKey: 'medins_list_codg',
      minWidth: 120,
    },
  ];
  if (activeQueryInfno.value === '3508') {
    return [
      ...common,
      { label: '库存数量', camelKey: 'invCnt', snakeKey: 'inv_cnt', width: 100, align: 'right' },
      { label: '库存日期', camelKey: 'invdate', snakeKey: 'invdate', width: 120, align: 'center' },
      {
        label: '生产批号',
        camelKey: 'manuLotnum',
        snakeKey: 'manu_lotnum',
        width: 110,
        align: 'center',
      },
    ];
  }
  if (activeQueryInfno.value === '3509') {
    return [
      ...common,
      {
        label: '变更类型',
        camelKey: 'invChgType',
        snakeKey: 'inv_chg_type',
        width: 100,
        align: 'center',
      },
      { label: '变更数量', camelKey: 'cnt', snakeKey: 'cnt', width: 100, align: 'right' },
      { label: '变更时间', camelKey: 'invChgTime', snakeKey: 'inv_chg_time', minWidth: 160 },
    ];
  }
  if (activeQueryInfno.value === '3510') {
    return [
      ...common,
      {
        label: '商品采购流水号',
        camelKey: 'medinsProdPurcNo',
        snakeKey: 'medins_prod_purc_no',
        minWidth: 140,
      },
      { label: '随货单号', camelKey: 'dyntNo', snakeKey: 'dynt_no', minWidth: 140 },
      {
        label: '生产批号',
        camelKey: 'manuLotnum',
        snakeKey: 'manu_lotnum',
        width: 110,
        align: 'center',
      },
      { label: '供应商', camelKey: 'splerName', snakeKey: 'spler_name', minWidth: 140 },
      {
        label: '发票号',
        camelKey: 'purcInvoNo',
        snakeKey: 'purc_invo_no',
        width: 100,
        align: 'center',
      },
      {
        label: '数量',
        camelKey: 'purcRetnCnt',
        snakeKey: 'purc_retn_cnt',
        width: 90,
        align: 'right',
      },
    ];
  }
  if (activeQueryInfno.value === '3511') {
    return [
      ...common,
      {
        label: '商品销售流水号',
        camelKey: 'medinsProdSelNo',
        snakeKey: 'medins_prod_sel_no',
        minWidth: 140,
      },
      {
        label: '生产批号',
        camelKey: 'manuLotnum',
        snakeKey: 'manu_lotnum',
        width: 110,
        align: 'center',
      },
      {
        label: '销售/退货数量',
        camelKey: 'selRetnCnt',
        snakeKey: 'sel_retn_cnt',
        width: 120,
        align: 'right',
      },
      { label: '人员姓名', camelKey: 'psnName', snakeKey: 'psn_name', width: 100, align: 'center' },
      { label: '就诊 ID', camelKey: 'mdtrtId', snakeKey: 'mdtrt_id', minWidth: 140 },
    ];
  }
  // 3512 / 3513
  return [
    ...common,
    { label: '追溯码', camelKey: 'drugTracCodg', snakeKey: 'drug_trac_codg', minWidth: 180 },
    {
      label: '生产批号',
      camelKey: 'manuLotnum',
      snakeKey: 'manu_lotnum',
      width: 110,
      align: 'center',
    },
  ];
});

/**
 * 默认查询日期：当天。
 */
function defaultQueryDateRange(): [string, string] {
  const today = dayjs().format('YYYY-MM-DD');
  return [today, today];
}

/**
 * 从平台行读取字段（兼容 camel / snake）。
 */
function readPlatformField(
  row: Record<string, unknown> | null | undefined,
  camelKey: string,
  snakeKey: string,
): string {
  if (!row) {
    return '';
  }
  const raw = row[camelKey] ?? row[snakeKey];
  if (raw == null) {
    return '';
  }
  return String(raw);
}

/**
 * 切换交易号时清空结果，保留筛选条件。
 * @param _name 新 Tab 名（Element Plus tab-change 入参，未使用）
 */
function onQueryInfnoChange(_name: string | number) {
  queryRows.value = [];
}

/**
 * 重置筛选为默认并清空结果。
 */
function resetQueryFilters() {
  queryDateRange.value = defaultQueryDateRange();
  queryBchno.value = '';
  queryMedinsListCodg.value = '';
  queryMedListCodg.value = '';
  queryMedinsProdPurcNo.value = '';
  queryMedinsProdSelNo.value = '';
  queryDrugTracCodg.value = '';
  queryCertno.value = '';
  queryRows.value = [];
}

/**
 * 组装并调用当前交易号对应的平台查询。
 */
async function loadPlatformQuery() {
  if (!props.contractBusNo) {
    ElMessage.warning('请先选择医保契约');
    return;
  }
  const bchno = queryBchno.value.trim();
  const medinsListCodg = queryMedinsListCodg.value.trim();
  const medListCodg = queryMedListCodg.value.trim();
  const drugTracCodg = queryDrugTracCodg.value.trim();
  const certno = queryCertno.value.trim();

  // 3512/3513：五选一；3508–3511：批次号与机构目录二选一
  if (isTraceQuery.value) {
    if (!bchno && !medinsListCodg && !medListCodg && !drugTracCodg && !certno) {
      ElMessage.warning('请至少填写批次号、机构目录、医保目录、追溯码或证件号之一');
      return;
    }
  } else if (!bchno && !medinsListCodg) {
    ElMessage.warning('批次流水号与机构目录编码至少填写一项');
    return;
  }

  const range = queryDateRange.value;
  if (!isTraceQuery.value && (!range?.[0] || !range?.[1])) {
    ElMessage.warning('请选择查询起止日期');
    return;
  }

  const params: QueryPlatformInvParams = {
    contractBusNo: props.contractBusNo,
    fixmedinsBchno: bchno || undefined,
    medinsListCodg: medinsListCodg || undefined,
    medListCodg: medListCodg || undefined,
    medinsProdPurcNo: queryMedinsProdPurcNo.value.trim() || undefined,
    medinsProdSelNo: queryMedinsProdSelNo.value.trim() || undefined,
    drugTracCodg: drugTracCodg || undefined,
    certno: certno || undefined,
    begndate: range?.[0],
    enddate: range?.[1],
  };

  queryLoading.value = true;
  try {
    await ensureInsuranceSignIn(props.contractBusNo);
    const infno = activeQueryInfno.value;
    let result;
    // 按交易号分发到已封装 API
    if (infno === '3508') {
      result = await queryPlatformInventory3508Api(params);
    } else if (infno === '3509') {
      result = await queryPlatformInvChange3509Api(params);
    } else if (infno === '3510') {
      result = await queryPlatformPurchase3510Api(params);
    } else if (infno === '3511') {
      result = await queryPlatformSale3511Api(params);
    } else if (infno === '3512') {
      result = await queryPlatformInboundTrace3512Api(params);
    } else {
      result = await queryPlatformSaleTrace3513Api(params);
    }
    queryRows.value = result.rows ?? [];
    if (queryRows.value.length === 0) {
      ElMessage.info('平台无匹配记录');
    }
  } finally {
    queryLoading.value = false;
  }
}

/**
 * 打开行整包 JSON 详情。
 */
function openRowDetail(row: Record<string, unknown>) {
  try {
    detailJson.value = JSON.stringify(row, null, 2);
  } catch {
    detailJson.value = String(row);
  }
  detailVisible.value = true;
}

watch(
  () => props.contractBusNo,
  () => {
    queryRows.value = [];
  },
);

queryDateRange.value = defaultQueryDateRange();
</script>

<template>
  <div>
    <ElAlert type="info" :closable="false" class="mb-3" :title="activeQueryTip" />

    <ElTabs v-model="activeQueryInfno" class="mb-3" @tab-change="onQueryInfnoChange">
      <ElTabPane
        v-for="tab in platformQueryTabs"
        :key="tab.value"
        :label="tab.label"
        :name="tab.value"
      />
    </ElTabs>

    <ElForm inline class="mb-3">
      <ElFormItem v-if="!isTraceQuery" label="查询日期" required>
        <ElDatePicker
          v-model="queryDateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始"
          end-placeholder="结束"
          value-format="YYYY-MM-DD"
          :clearable="false"
          style="width: 280px"
        />
      </ElFormItem>
      <ElFormItem v-else label="查询日期">
        <ElDatePicker
          v-model="queryDateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始"
          end-placeholder="结束"
          value-format="YYYY-MM-DD"
          clearable
          style="width: 280px"
        />
      </ElFormItem>
      <ElFormItem label="批次流水号">
        <ElInput
          v-model="queryBchno"
          clearable
          placeholder="fixmedins_bchno"
          style="width: 180px"
          @keyup.enter="loadPlatformQuery"
        />
      </ElFormItem>
      <ElFormItem label="机构目录编码">
        <ElInput
          v-model="queryMedinsListCodg"
          clearable
          placeholder="medins_list_codg"
          style="width: 150px"
          @keyup.enter="loadPlatformQuery"
        />
      </ElFormItem>
      <ElFormItem label="医保目录编码">
        <ElInput
          v-model="queryMedListCodg"
          clearable
          placeholder="med_list_codg"
          style="width: 180px"
          @keyup.enter="loadPlatformQuery"
        />
      </ElFormItem>
      <ElFormItem v-if="activeQueryInfno === '3510'" label="商品采购流水号">
        <ElInput
          v-model="queryMedinsProdPurcNo"
          clearable
          placeholder="可选"
          style="width: 160px"
          @keyup.enter="loadPlatformQuery"
        />
      </ElFormItem>
      <ElFormItem v-if="activeQueryInfno === '3511'" label="商品销售流水号">
        <ElInput
          v-model="queryMedinsProdSelNo"
          clearable
          placeholder="可选"
          style="width: 160px"
          @keyup.enter="loadPlatformQuery"
        />
      </ElFormItem>
      <ElFormItem v-if="isTraceQuery" label="追溯码">
        <ElInput
          v-model="queryDrugTracCodg"
          clearable
          placeholder="drug_trac_codg"
          style="width: 200px"
          @keyup.enter="loadPlatformQuery"
        />
      </ElFormItem>
      <ElFormItem v-if="isTraceQuery" label="证件号">
        <ElInput
          v-model="queryCertno"
          clearable
          placeholder="certno"
          style="width: 160px"
          @keyup.enter="loadPlatformQuery"
        />
      </ElFormItem>
      <ElFormItem>
        <ElButton type="primary" :loading="queryLoading" @click="loadPlatformQuery">
          查询平台
        </ElButton>
        <ElButton @click="resetQueryFilters">重置</ElButton>
      </ElFormItem>
    </ElForm>

    <ElTable v-loading="queryLoading" :data="queryRows" border stripe max-height="520">
      <ElTableColumn
        v-for="col in queryColumns"
        :key="col.camelKey"
        :label="col.label"
        :min-width="col.minWidth"
        :width="col.width"
        :align="col.align ?? 'left'"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ readPlatformField(row, col.camelKey, col.snakeKey) }}
        </template>
      </ElTableColumn>
      <ElTableColumn label="操作" width="90" fixed="right" align="center">
        <template #default="{ row }">
          <ElButton link type="primary" @click="openRowDetail(row)">详情</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>

    <ElDialog v-model="detailVisible" :title="`${activeQueryInfno} 行详情`" width="720px">
      <ElInput v-model="detailJson" type="textarea" :rows="18" readonly placeholder="无数据" />
      <template #footer>
        <ElButton type="primary" @click="detailVisible = false">关闭</ElButton>
      </template>
    </ElDialog>
  </div>
</template>
