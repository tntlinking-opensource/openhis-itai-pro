<script setup lang="ts">
import type { DetailAuditStatsHitRow, DetailAuditStatsLineRow } from '#/chs/api/detail-audit-stats';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import dayjs from 'dayjs';
import {
  ElButton,
  ElDatePicker,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { listDetailAuditHitLinesApi, pageDetailAuditStatsApi } from '#/chs/api/detail-audit-stats';
import {
  DETAIL_AUDIT_DSPO_WAY_OPTIONS,
  DETAIL_AUDIT_TRIG_SCEN_OPTIONS,
  formatSevDeg,
} from '#/chs/constants/detail-audit-try';
import { useRequest } from '#/hooks/use-request';

defineOptions({ name: 'ChsDetailAuditStatsPage' });

const SEV_OPTIONS = [
  { value: '1', label: '1 明确违规' },
  { value: '2', label: '2 高度可疑' },
  { value: '3', label: '3 轻度可疑' },
];

const filters = reactive({
  dateRange: [] as string[],
  drNameKeyword: '',
  drCodg: '',
  deptCodg: '',
  trigScen: '',
  sevDeg: '',
  dspoWay: '',
  keyword: '',
});

const pager = reactive({
  pageNo: 1,
  pageSize: 20,
  total: 0,
});

const rows = ref<DetailAuditStatsHitRow[]>([]);
const summary = reactive({
  hitCount: 0,
  mdtrtCount: 0,
  doctorCount: 0,
  volaAmtSum: 0 as number | string,
});

const drawerVisible = ref(false);
const drawerJrId = ref('');
const drawerLines = ref<DetailAuditStatsLineRow[]>([]);

/** 默认近 7 天（含今天）创建时间窗。 */
function defaultDateRange(): string[] {
  const end = dayjs().endOf('day');
  const start = dayjs().subtract(6, 'day').startOf('day');
  return [start.format('YYYY-MM-DD HH:mm:ss'), end.format('YYYY-MM-DD HH:mm:ss')];
}

/** 末次 3103 处置展示。 */
function formatDspoWay(code?: string | null) {
  if (code === '1') return '继续执行';
  if (code === '2') return '返回修改';
  if (!code) return '未反馈';
  return code;
}

/** trig_scen 码 → 文案。 */
function formatTrig(code?: string | null) {
  const hit = DETAIL_AUDIT_TRIG_SCEN_OPTIONS.find((o) => o.value === code);
  return hit?.label ?? code ?? '—';
}

/** 金额两位小数展示。 */
function formatMoney(value: null | number | string | undefined) {
  const n = Number(value ?? 0);
  return Number.isFinite(n) ? n.toFixed(2) : '0.00';
}

const summaryText = computed(
  () =>
    `违规 ${summary.hitCount} 条 · 就诊 ${summary.mdtrtCount} · 医生 ${summary.doctorCount} · 金额合计 ¥${formatMoney(summary.volaAmtSum)}`,
);

const { run: loadPage, loading } = useRequest(
  async () => {
    const [from, to] = filters.dateRange;
    if (!from || !to) {
      ElMessage.warning('请选择创建时间范围');
      return;
    }
    const data = await pageDetailAuditStatsApi({
      createdFrom: from,
      createdTo: to,
      drCodg: filters.drCodg.trim() || undefined,
      drNameKeyword: filters.drNameKeyword.trim() || undefined,
      deptCodg: filters.deptCodg.trim() || undefined,
      trigScen: filters.trigScen || undefined,
      sevDeg: filters.sevDeg || undefined,
      dspoWay: filters.dspoWay || undefined,
      keyword: filters.keyword.trim() || undefined,
      pageNo: pager.pageNo,
      pageSize: pager.pageSize,
    });
    rows.value = data.content ?? [];
    pager.total = data.totalCount ?? 0;
    summary.hitCount = data.hitCount ?? 0;
    summary.mdtrtCount = data.mdtrtCount ?? 0;
    summary.doctorCount = data.doctorCount ?? 0;
    summary.volaAmtSum = data.volaAmtSum ?? 0;
  },
  { manual: true },
);

/** 查询：重置到第 1 页。 */
function handleSearch() {
  pager.pageNo = 1;
  void loadPage();
}

/** 重置筛选为默认时间窗并查询。 */
function handleReset() {
  filters.dateRange = defaultDateRange();
  filters.drNameKeyword = '';
  filters.drCodg = '';
  filters.deptCodg = '';
  filters.trigScen = '';
  filters.sevDeg = '';
  filters.dspoWay = '';
  filters.keyword = '';
  pager.pageNo = 1;
  void loadPage();
}

/** 行点击：打开明细抽屉并拉取 hit_line。 */
async function openLines(row: DetailAuditStatsHitRow) {
  const jrId = String(row.jrId ?? '').trim();
  if (!jrId) {
    ElMessage.warning('缺少 jrId');
    return;
  }
  drawerJrId.value = jrId;
  drawerVisible.value = true;
  try {
    drawerLines.value = await listDetailAuditHitLinesApi(jrId);
  } catch (error) {
    drawerLines.value = [];
    ElMessage.error(error instanceof Error ? error.message : '加载明细失败');
  }
}

onMounted(() => {
  filters.dateRange = defaultDateRange();
  void loadPage();
});
</script>

<template>
  <Page
    title="事前提醒统计"
    description="只读查询已落库的 3101 违规，供医保办/质控研讨（不调平台）"
  >
    <ElForm class="mb-3 flex flex-wrap gap-2" inline @submit.prevent="handleSearch">
      <ElFormItem label="创建时间">
        <ElDatePicker
          v-model="filters.dateRange"
          type="datetimerange"
          value-format="YYYY-MM-DD HH:mm:ss"
          range-separator="至"
          start-placeholder="起"
          end-placeholder="止"
          :clearable="false"
        />
      </ElFormItem>
      <ElFormItem label="医生姓名">
        <ElInput
          v-model="filters.drNameKeyword"
          clearable
          placeholder="模糊"
          style="width: 120px"
        />
      </ElFormItem>
      <ElFormItem label="医生编码">
        <ElInput v-model="filters.drCodg" clearable placeholder="精确" style="width: 120px" />
      </ElFormItem>
      <ElFormItem label="科室编码">
        <ElInput v-model="filters.deptCodg" clearable style="width: 120px" />
      </ElFormItem>
      <ElFormItem label="触发场景">
        <ElSelect v-model="filters.trigScen" clearable placeholder="全部" style="width: 160px">
          <ElOption
            v-for="opt in DETAIL_AUDIT_TRIG_SCEN_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="严重程度">
        <ElSelect v-model="filters.sevDeg" clearable placeholder="全部" style="width: 140px">
          <ElOption
            v-for="opt in SEV_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="处置">
        <ElSelect v-model="filters.dspoWay" clearable placeholder="全部" style="width: 130px">
          <ElOption
            v-for="opt in DETAIL_AUDIT_DSPO_WAY_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="关键字">
        <ElInput
          v-model="filters.keyword"
          clearable
          placeholder="规则名/违规内容"
          style="width: 180px"
        />
      </ElFormItem>
      <ElFormItem>
        <ElButton type="primary" :loading="loading" @click="handleSearch">查询</ElButton>
        <ElButton @click="handleReset">重置</ElButton>
      </ElFormItem>
    </ElForm>

    <div class="mb-2 text-sm text-[var(--el-text-color-regular)]">{{ summaryText }}</div>

    <ElTable v-loading="loading" :data="rows" stripe border max-height="560" @row-click="openLines">
      <ElTableColumn label="时间" prop="createdAt" min-width="160" show-overflow-tooltip />
      <ElTableColumn label="医生" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.drName || row.drCodg || '—' }}
        </template>
      </ElTableColumn>
      <ElTableColumn label="科室" min-width="100" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.deptName || row.deptCodg || '—' }}
        </template>
      </ElTableColumn>
      <ElTableColumn label="就诊号" prop="mdtrtId" min-width="120" show-overflow-tooltip />
      <ElTableColumn label="规则" prop="ruleName" min-width="140" show-overflow-tooltip />
      <ElTableColumn label="严重程度" min-width="100">
        <template #default="{ row }">
          <ElTag size="small">{{ formatSevDeg(row.sevDeg) }}</ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn label="违规内容" prop="volaCont" min-width="200" show-overflow-tooltip />
      <ElTableColumn label="金额" min-width="90" align="right">
        <template #default="{ row }">¥{{ formatMoney(row.volaAmt) }}</template>
      </ElTableColumn>
      <ElTableColumn label="场景" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ formatTrig(row.trigScen) }}</template>
      </ElTableColumn>
      <ElTableColumn label="处置" min-width="90">
        <template #default="{ row }">{{ formatDspoWay(row.latestDspoWay) }}</template>
      </ElTableColumn>
    </ElTable>

    <div class="mt-3 flex justify-end">
      <ElPagination
        v-model:current-page="pager.pageNo"
        v-model:page-size="pager.pageSize"
        :total="pager.total"
        layout="total, sizes, prev, pager, next"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="loadPage"
        @size-change="
          () => {
            pager.pageNo = 1;
            loadPage();
          }
        "
      />
    </div>

    <ElDrawer v-model="drawerVisible" :title="`违规明细 · ${drawerJrId}`" size="480px">
      <ElTable :data="drawerLines" stripe border>
        <ElTableColumn label="jrdId" prop="jrdId" min-width="160" show-overflow-tooltip />
        <ElTableColumn label="类型" prop="volaItemType" width="80" />
        <ElTableColumn label="处方/医嘱" prop="rxId" min-width="120" show-overflow-tooltip />
        <ElTableColumn label="金额" width="90" align="right">
          <template #default="{ row }">¥{{ formatMoney(row.volaAmt) }}</template>
        </ElTableColumn>
      </ElTable>
    </ElDrawer>
  </Page>
</template>
