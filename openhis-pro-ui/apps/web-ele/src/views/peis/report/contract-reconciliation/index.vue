<script setup lang="ts">
import type { CompanyRowDto } from '#/api/peis/group-check';
import type { ContractReconciliationRowDto } from '#/api/peis/contract-reconciliation';

import { computed, onMounted, ref } from 'vue';

import { Page } from '@zxplat/common-ui';

import { listContractReconciliationApi } from '#/api/peis/contract-reconciliation';
import { listCompaniesApi, markContractPaidApi } from '#/api/peis/group-check';
import { formatMoneyDisplay } from '#/utils/format/money-scale';
import {
  CONTRACT_PAY_STATUS,
  contractPayStatusLabel,
  contractPayStatusTagType,
  packageLabel,
} from '#/views/peis/group-check/constants';
import { searchPackageApi } from '#/api/peis/catalog-config';
import type { PeisPackageDto } from '#/api/peis/catalog-config';
import {
  ElButton,
  ElCard,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

defineOptions({ name: 'PeisContractReconciliation' });

const companies = ref<CompanyRowDto[]>([]);
const packages = ref<PeisPackageDto[]>([]);
const selectedCompanyId = ref('');
const payStatusFilter = ref('');
const loading = ref(false);
const rows = ref<ContractReconciliationRowDto[]>([]);
const detailRow = ref<ContractReconciliationRowDto | null>(null);
const detailOpen = ref(false);

const companyName = computed(
  () =>
    companies.value.find((item) => String(item.companyId) === selectedCompanyId.value)?.name ?? '',
);

const unpaidCount = computed(
  () => rows.value.filter((row) => row.payStatusCode !== CONTRACT_PAY_STATUS.PAID).length,
);

const totalContractAmount = computed(() =>
  rows.value.reduce((sum, row) => sum + Number(row.contractAmount ?? 0), 0),
);

function formatAmount(value?: number | string | null) {
  if (value == null || value === '') return '—';
  return formatMoneyDisplay(value);
}

async function loadCompanies() {
  companies.value = (await listCompaniesApi()) ?? [];
  if (!selectedCompanyId.value && companies.value.length > 0) {
    selectedCompanyId.value = String(companies.value[0]?.companyId ?? '');
  }
}

async function loadPackages() {
  const resp = await searchPackageApi({ pageNo: 1, pageSize: 200, statusEnum: 'active' });
  packages.value = resp?.content ?? [];
}

async function loadReport() {
  if (!selectedCompanyId.value) {
    rows.value = [];
    return;
  }
  loading.value = true;
  try {
    rows.value =
      (await listContractReconciliationApi({
        companyId: selectedCompanyId.value,
        payStatusCode: payStatusFilter.value || undefined,
      })) ?? [];
  } finally {
    loading.value = false;
  }
}

function openDetail(row: ContractReconciliationRowDto) {
  detailRow.value = row;
  detailOpen.value = true;
}

async function handleMarkPaid(row: ContractReconciliationRowDto) {
  const contractId = String(row.contractId ?? '');
  if (!contractId) return;
  try {
    await ElMessageBox.confirm(
      `确认单位已对合同「${row.contractName || contractId}」完成后结收款？`,
      '单位后结确认',
      { type: 'warning', confirmButtonText: '确认已收', cancelButtonText: '取消' },
    );
  } catch {
    return;
  }
  await markContractPaidApi({ contractId });
  ElMessage.success('合同已标记为已收');
  await loadReport();
  if (detailRow.value && String(detailRow.value.contractId) === contractId) {
    detailRow.value =
      rows.value.find((item) => String(item.contractId) === contractId) ?? detailRow.value;
  }
}

onMounted(async () => {
  await Promise.all([loadCompanies(), loadPackages()]);
  await loadReport();
});
</script>

<template>
  <Page
    auto-content-height
    content-class="contract-reconciliation-page !flex !h-full !min-h-0 !flex-col !gap-3 !p-4"
  >
    <ElCard shadow="never" class="page-intro shrink-0">
      <div class="page-intro__title">团检合同对账</div>
      <div class="page-intro__sub">
        汇总合同协议金额、名册建单/到检进度与加项收费；套餐费现场免收费，单位款项在合同上后结确认。
      </div>
    </ElCard>

    <ElCard shadow="never" class="query-card shrink-0">
      <ElForm :inline="true" class="query-form">
        <ElFormItem label="单位">
          <ElSelect
            v-model="selectedCompanyId"
            filterable
            placeholder="选择单位"
            class="!w-[280px]"
            @change="loadReport"
          >
            <ElOption
              v-for="item in companies"
              :key="String(item.companyId)"
              :label="item.name"
              :value="String(item.companyId)"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="收款状态">
          <ElSelect
            v-model="payStatusFilter"
            clearable
            placeholder="全部"
            class="!w-[120px]"
            @change="loadReport"
          >
            <ElOption label="待收" :value="CONTRACT_PAY_STATUS.UNPAID" />
            <ElOption label="已收" :value="CONTRACT_PAY_STATUS.PAID" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" :loading="loading" @click="loadReport">刷新</ElButton>
        </ElFormItem>
        <div v-if="selectedCompanyId && rows.length > 0" class="query-summary">
          <span>合同 {{ rows.length }} 份</span>
          <span>待收 {{ unpaidCount }} 份</span>
          <span>协议总额 {{ formatAmount(totalContractAmount) }}</span>
        </div>
      </ElForm>
    </ElCard>

    <ElCard shadow="never" class="table-card min-h-0 flex-1">
      <ElTable
        v-loading="loading"
        :data="rows"
        stripe
        height="100%"
        class="contract-table"
        @row-click="openDetail"
      >
        <ElTableColumn prop="contractName" label="合同" min-width="160" show-overflow-tooltip />
        <ElTableColumn label="合同金额" width="112" align="right">
          <template #default="{ row }">{{ formatAmount(row.contractAmount) }}</template>
        </ElTableColumn>
        <ElTableColumn label="收款状态" width="96" align="center">
          <template #default="{ row }">
            <ElTag :type="contractPayStatusTagType(row.payStatusCode)" size="small">
              {{ contractPayStatusLabel(row.payStatusCode) }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="rosterTotal" label="名册" width="72" align="center" />
        <ElTableColumn prop="enrolledCount" label="已建单" width="80" align="center" />
        <ElTableColumn prop="arrivedCount" label="已到检" width="80" align="center" />
        <ElTableColumn label="加项待收" width="120" align="right">
          <template #default="{ row }">
            {{ formatAmount(row.addOnPendingAmount) }}
            <span v-if="row.addOnPendingCount" class="text-xs text-gray-400">
              ({{ row.addOnPendingCount }}笔)
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="加项已收" width="104" align="right">
          <template #default="{ row }">{{ formatAmount(row.addOnSettledAmount) }}</template>
        </ElTableColumn>
        <ElTableColumn label="分组" width="72" align="center">
          <template #default="{ row }">{{ row.batchCount ?? 0 }}</template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="148" fixed="right">
          <template #default="{ row }">
            <ElButton link type="primary" @click.stop="openDetail(row)">明细</ElButton>
            <ElButton
              v-if="row.payStatusCode !== CONTRACT_PAY_STATUS.PAID"
              link
              type="success"
              @click.stop="handleMarkPaid(row)"
            >
              单位已收
            </ElButton>
          </template>
        </ElTableColumn>
        <template #empty>
          <div class="py-10 text-sm text-[var(--el-text-color-secondary)]">暂无合同对账数据</div>
        </template>
      </ElTable>
    </ElCard>

    <ElDrawer v-model="detailOpen" class="detail-drawer" size="55%" destroy-on-close>
      <template #header>
        <div class="detail-drawer__header">
          <div class="detail-drawer__title">{{ detailRow?.contractName || '合同明细' }}</div>
          <div class="detail-drawer__sub">{{ companyName }}</div>
        </div>
      </template>

      <template v-if="detailRow">
        <div class="detail-drawer__body">
          <div class="detail-stats-grid">
            <ElCard shadow="never" class="stat-card">
              <div class="stat-card__label">合同金额</div>
              <div class="stat-card__value">{{ formatAmount(detailRow.contractAmount) }}</div>
            </ElCard>
            <ElCard shadow="never" class="stat-card">
              <div class="stat-card__label">收款状态</div>
              <div class="stat-card__value stat-card__value--tag">
                <ElTag :type="contractPayStatusTagType(detailRow.payStatusCode)" size="small">
                  {{ contractPayStatusLabel(detailRow.payStatusCode) }}
                </ElTag>
              </div>
            </ElCard>
            <ElCard shadow="never" class="stat-card">
              <div class="stat-card__label">名册总数</div>
              <div class="stat-card__value">{{ detailRow.rosterTotal ?? 0 }}</div>
            </ElCard>
            <ElCard shadow="never" class="stat-card">
              <div class="stat-card__label">已建单</div>
              <div class="stat-card__value text-primary">{{ detailRow.enrolledCount ?? 0 }}</div>
            </ElCard>
            <ElCard shadow="never" class="stat-card">
              <div class="stat-card__label">已到检</div>
              <div class="stat-card__value text-success">{{ detailRow.arrivedCount ?? 0 }}</div>
            </ElCard>
            <ElCard shadow="never" class="stat-card">
              <div class="stat-card__label">待到检</div>
              <div class="stat-card__value text-warning">
                {{ detailRow.pendingArrivalCount ?? 0 }}
              </div>
            </ElCard>
            <ElCard shadow="never" class="stat-card">
              <div class="stat-card__label">加项待收</div>
              <div class="stat-card__value text-warning">
                {{ formatAmount(detailRow.addOnPendingAmount) }}
              </div>
            </ElCard>
            <ElCard shadow="never" class="stat-card">
              <div class="stat-card__label">加项已收</div>
              <div class="stat-card__value text-success">
                {{ formatAmount(detailRow.addOnSettledAmount) }}
              </div>
            </ElCard>
          </div>

          <div class="detail-section">
            <div class="detail-section__head">
              <span class="detail-section__title">分组明细</span>
              <span class="detail-section__hint"
                >共 {{ detailRow.batches?.length ?? 0 }} 个分组；套餐统计按名册选定套餐</span
              >
            </div>
            <ElTable
              :data="detailRow.batches ?? []"
              stripe
              class="detail-batch-table"
              max-height="calc(100vh - 360px)"
            >
              <ElTableColumn prop="batchName" label="分组" min-width="140" show-overflow-tooltip />
              <ElTableColumn label="可选套餐" width="88" align="center">
                <template #default="{ row }">
                  {{ row.optionalPackageActivityIds?.length ?? (row.packageActivityId ? 1 : 0) }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="未选定" width="80" align="center">
                <template #default="{ row }">{{ row.unselectedCount ?? 0 }}</template>
              </ElTableColumn>
              <ElTableColumn label="选定套餐分布" min-width="260">
                <template #default="{ row }">
                  <div
                    v-if="(row.selectedPackages?.length ?? 0) === 0"
                    class="text-xs text-[var(--el-text-color-secondary)]"
                  >
                    暂无选定记录
                  </div>
                  <div v-else class="selected-package-tags">
                    <span
                      v-for="pkg in row.selectedPackages ?? []"
                      :key="String(pkg.packageActivityId)"
                      class="selected-package-tag"
                    >
                      {{ packageLabel(String(pkg.packageActivityId ?? ''), packages) }}
                      <span class="selected-package-tag__meta">
                        {{ pkg.rosterCount ?? 0 }}人 / 建单{{ pkg.enrolledCount ?? 0 }} / 到检{{
                          pkg.arrivedCount ?? 0
                        }}
                      </span>
                    </span>
                  </div>
                </template>
              </ElTableColumn>
              <ElTableColumn prop="bookingDate" label="预约日" width="120" />
              <ElTableColumn prop="rosterTotal" label="名册" width="80" align="center" />
              <ElTableColumn prop="enrolledCount" label="建单" width="80" align="center" />
              <ElTableColumn prop="arrivedCount" label="到检" width="80" align="center" />
            </ElTable>
          </div>
        </div>
      </template>

      <template v-if="detailRow?.payStatusCode !== CONTRACT_PAY_STATUS.PAID" #footer>
        <div class="detail-drawer__footer">
          <ElButton @click="detailOpen = false">关闭</ElButton>
          <ElButton type="success" @click="handleMarkPaid(detailRow!)">确认单位已收</ElButton>
        </div>
      </template>
    </ElDrawer>
  </Page>
</template>

<style scoped>
.page-intro :deep(.el-card__body),
.query-card :deep(.el-card__body) {
  padding: 14px 18px;
}

.table-card {
  display: flex;
  flex-direction: column;
}

.table-card :deep(.el-card__body) {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  padding: 12px 14px;
}

.page-intro__title {
  font-size: 16px;
  font-weight: 600;
}

.page-intro__sub {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.query-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}

.query-form :deep(.el-form-item) {
  margin-right: 16px;
  margin-bottom: 0;
}

.query-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-left: auto;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.contract-table {
  flex: 1;
  min-height: 0;
}

.detail-drawer :deep(.el-drawer__header) {
  margin-bottom: 0;
  padding: 18px 20px 12px;
}

.detail-drawer :deep(.el-drawer__body) {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 0 20px 16px;
}

.detail-drawer :deep(.el-drawer__footer) {
  padding: 12px 20px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.detail-drawer__header {
  min-width: 0;
}

.detail-drawer__title {
  font-size: 18px;
  font-weight: 600;
  line-height: 1.4;
}

.detail-drawer__sub {
  margin-top: 4px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.detail-drawer__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 20px;
  min-height: 0;
}

.detail-drawer__footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.detail-stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.stat-card :deep(.el-card__body) {
  padding: 14px 16px;
}

.stat-card__label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.stat-card__value {
  margin-top: 8px;
  font-size: 22px;
  font-weight: 600;
  line-height: 1.2;
}

.stat-card__value--tag {
  margin-top: 10px;
}

.text-primary {
  color: var(--el-color-primary);
}

.text-success {
  color: var(--el-color-success);
}

.text-warning {
  color: var(--el-color-warning);
}

.detail-section {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.detail-section__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 12px;
}

.detail-section__title {
  font-size: 15px;
  font-weight: 600;
}

.detail-section__hint {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.detail-batch-table {
  width: 100%;
}

.selected-package-tags {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.selected-package-tag {
  font-size: 13px;
  line-height: 1.4;
}

.selected-package-tag__meta {
  margin-left: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

@media (max-width: 1200px) {
  .detail-stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
