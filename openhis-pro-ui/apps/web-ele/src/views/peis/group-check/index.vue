<script setup lang="ts">
import type {
  BatchRowDto,
  CompanyRowDto,
  ContractRowDto,
  GroupRosterRowDto,
  RosterImportRowDto,
} from '#/api/peis/group-check';

import { computed, onMounted, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';

import {
  confirmGroupArrivalApi,
  importRosterApi,
  listBatchesApi,
  listCompaniesApi,
  listContractsApi,
  pageRosterApi,
  saveCompanyApi,
  saveContractApi,
  saveGroupBatchApi,
} from '#/api/peis/group-check';
import {
  confirmBatchSettlementApi,
  pagePaidRosterApi,
  pageUnpaidRosterApi,
  preSettleBatchApi,
} from '#/api/peis/group-charge';
import {
  ElButton,
  ElDatePicker,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElPagination,
  ElSelect,
  ElTabPane,
  ElTable,
  ElTableColumn,
  ElTabs,
  ElTag,
} from 'element-plus';

defineOptions({ name: 'PeisGroupCheck' });

const userStore = useUserStore();
const activeTab = ref('company');

const orgId = computed(() => {
  const fromUser = userStore.userInfo?.organizationId;
  return fromUser != null ? String(fromUser) : '';
});

const companies = ref<CompanyRowDto[]>([]);
const contracts = ref<ContractRowDto[]>([]);
const batches = ref<BatchRowDto[]>([]);
const selectedCompanyId = ref<string>('');
const selectedBatchId = ref<string>('');

const companyForm = ref({
  companyId: '',
  name: '',
  contactName: '',
  contactPhone: '',
});

const contractForm = ref({
  contractId: '',
  packageActivityId: '',
  contractName: '',
  validFrom: '',
  validTo: '',
});

const batchForm = ref({
  batchId: '',
  contractId: '',
  batchName: '',
  packageActivityId: '',
  bookingDate: '',
});

const rosterRows = ref<GroupRosterRowDto[]>([]);
const rosterTotal = ref(0);
const rosterPageNo = ref(1);
const rosterPageSize = ref(10);
const rosterLoading = ref(false);
const selectedRosterIds = ref<string[]>([]);

const importForm = ref<RosterImportRowDto>({
  patientId: '',
  fullName: '',
  studentNo: '',
  employeeNo: '',
  collegeId: '',
  className: '',
  idCardNo: '',
  mobilePhone: '',
});

const chargeSubTab = ref<'paid' | 'unpaid'>('unpaid');
const preSettleInfo = ref('');

async function loadCompanies() {
  companies.value = (await listCompaniesApi()) ?? [];
  if (!selectedCompanyId.value && companies.value.length > 0) {
    selectedCompanyId.value = String(companies.value[0]?.companyId ?? '');
  }
}

async function loadContractsAndBatches() {
  if (!selectedCompanyId.value) {
    contracts.value = [];
    batches.value = [];
    return;
  }
  contracts.value = (await listContractsApi({ companyId: selectedCompanyId.value })) ?? [];
  batches.value = (await listBatchesApi({ companyId: selectedCompanyId.value })) ?? [];
  if (!selectedBatchId.value && batches.value.length > 0) {
    selectedBatchId.value = String(batches.value[0]?.batchId ?? '');
  }
}

async function loadRoster() {
  if (!selectedBatchId.value) {
    rosterRows.value = [];
    rosterTotal.value = 0;
    return;
  }
  rosterLoading.value = true;
  try {
    const resp = await pageRosterApi({
      batchId: selectedBatchId.value,
      pageNo: rosterPageNo.value,
      pageSize: rosterPageSize.value,
    });
    rosterRows.value = resp?.content ?? [];
    rosterTotal.value = resp?.totalCount ?? 0;
  } finally {
    rosterLoading.value = false;
  }
}

async function loadChargeRoster() {
  if (!selectedBatchId.value) {
    rosterRows.value = [];
    rosterTotal.value = 0;
    return;
  }
  rosterLoading.value = true;
  try {
    const params = {
      batchId: selectedBatchId.value,
      pageNo: rosterPageNo.value,
      pageSize: rosterPageSize.value,
    };
    const resp =
      chargeSubTab.value === 'unpaid'
        ? await pageUnpaidRosterApi(params)
        : await pagePaidRosterApi(params);
    rosterRows.value = resp?.content ?? [];
    rosterTotal.value = resp?.totalCount ?? 0;
  } finally {
    rosterLoading.value = false;
  }
}

async function handleSaveCompany() {
  if (!companyForm.value.name || !orgId.value) {
    ElMessage.warning('请填写单位名称并确认机构');
    return;
  }
  const resp = await saveCompanyApi({
    companyId: companyForm.value.companyId || undefined,
    name: companyForm.value.name,
    orgId: orgId.value,
    contactName: companyForm.value.contactName || undefined,
    contactPhone: companyForm.value.contactPhone || undefined,
  });
  ElMessage.success(`单位已保存：${resp?.companyId ?? ''}`);
  companyForm.value.companyId = '';
  companyForm.value.name = '';
  companyForm.value.contactName = '';
  companyForm.value.contactPhone = '';
  await loadCompanies();
}

async function handleSaveContract() {
  if (!selectedCompanyId.value || !contractForm.value.packageActivityId) {
    ElMessage.warning('请选择单位并填写套餐 ID');
    return;
  }
  const resp = await saveContractApi({
    contractId: contractForm.value.contractId || undefined,
    companyId: selectedCompanyId.value,
    packageActivityId: contractForm.value.packageActivityId,
    contractName: contractForm.value.contractName || undefined,
    validFrom: contractForm.value.validFrom || undefined,
    validTo: contractForm.value.validTo || undefined,
    orgId: orgId.value || undefined,
  });
  ElMessage.success(`合同已保存：${resp?.contractId ?? ''}`);
  contractForm.value = {
    contractId: '',
    packageActivityId: '',
    contractName: '',
    validFrom: '',
    validTo: '',
  };
  await loadContractsAndBatches();
}

async function handleSaveBatch() {
  if (!batchForm.value.contractId || !batchForm.value.batchName) {
    ElMessage.warning('请选择合同并填写批次名称');
    return;
  }
  const resp = await saveGroupBatchApi({
    batchId: batchForm.value.batchId || undefined,
    companyId: selectedCompanyId.value || undefined,
    contractId: batchForm.value.contractId,
    batchName: batchForm.value.batchName,
    packageActivityId: batchForm.value.packageActivityId || undefined,
    bookingDate: batchForm.value.bookingDate || undefined,
    orgId: orgId.value || undefined,
  });
  ElMessage.success(`批次已保存：${resp?.batchId ?? ''}`);
  batchForm.value = {
    batchId: '',
    contractId: '',
    batchName: '',
    packageActivityId: '',
    bookingDate: '',
  };
  await loadContractsAndBatches();
}

async function handleImportRoster() {
  if (!selectedBatchId.value || !importForm.value.fullName) {
    ElMessage.warning('请选择批次并填写姓名');
    return;
  }
  const resp = await importRosterApi({
    batchId: selectedBatchId.value,
    rows: [{ ...importForm.value }],
  });
  ElMessage.success(`导入 ${resp?.importedCount ?? 0} 人`);
  importForm.value = {
    patientId: '',
    fullName: '',
    studentNo: '',
    employeeNo: '',
    collegeId: '',
    className: '',
    idCardNo: '',
    mobilePhone: '',
  };
  await loadRoster();
}

async function handleConfirmArrival(row: GroupRosterRowDto) {
  if (!row.rosterId) return;
  await confirmGroupArrivalApi({ rosterId: row.rosterId });
  ElMessage.success(`${row.fullName ?? ''} 已到检确认`);
  await loadRoster();
}

function handleRosterSelection(rows: GroupRosterRowDto[]) {
  selectedRosterIds.value = rows.map((r) => String(r.rosterId ?? '')).filter(Boolean);
}

async function handlePreSettle() {
  if (selectedRosterIds.value.length === 0) {
    ElMessage.warning('请勾选名册');
    return;
  }
  const resp = await preSettleBatchApi({ rosterIds: selectedRosterIds.value });
  preSettleInfo.value = `预计 ${resp?.estimatedCount ?? 0} 人，金额 ¥${resp?.estimatedAmount ?? 0}`;
  ElMessage.success('预结算完成');
}

async function handleConfirmSettlement() {
  if (selectedRosterIds.value.length === 0) {
    ElMessage.warning('请勾选名册');
    return;
  }
  const resp = await confirmBatchSettlementApi({
    rosterIds: selectedRosterIds.value,
    orgId: orgId.value || undefined,
  });
  const failCount = resp?.failures?.length ?? 0;
  ElMessage.success(`成功 ${resp?.successCount ?? 0} 人，失败 ${failCount} 人`);
  if (failCount > 0) {
    console.warn(resp?.failures);
  }
  selectedRosterIds.value = [];
  await loadChargeRoster();
}

watch(selectedCompanyId, () => {
  loadContractsAndBatches();
});

watch(selectedBatchId, () => {
  rosterPageNo.value = 1;
  if (activeTab.value === 'roster' || activeTab.value === 'arrival') {
    loadRoster();
  }
  if (activeTab.value === 'charge') {
    loadChargeRoster();
  }
});

watch(activeTab, (tab) => {
  rosterPageNo.value = 1;
  if (tab === 'roster' || tab === 'arrival') {
    loadRoster();
  }
  if (tab === 'charge') {
    loadChargeRoster();
  }
});

watch(chargeSubTab, () => {
  rosterPageNo.value = 1;
  loadChargeRoster();
});

onMounted(async () => {
  await loadCompanies();
  await loadContractsAndBatches();
});
</script>

<template>
  <Page auto-content-height content-class="p-4">
    <div class="mb-4 flex flex-wrap items-center gap-3">
      <span class="text-sm text-gray-500">团检单位</span>
      <ElSelect v-model="selectedCompanyId" placeholder="选择单位" style="width: 220px">
        <ElOption
          v-for="item in companies"
          :key="String(item.companyId)"
          :label="item.name"
          :value="String(item.companyId)"
        />
      </ElSelect>
      <span class="text-sm text-gray-500">批次</span>
      <ElSelect v-model="selectedBatchId" placeholder="选择批次" style="width: 220px">
        <ElOption
          v-for="item in batches"
          :key="String(item.batchId)"
          :label="item.batchName"
          :value="String(item.batchId)"
        />
      </ElSelect>
    </div>

    <ElTabs v-model="activeTab">
      <ElTabPane label="单位" name="company">
        <ElForm label-width="100px" class="max-w-xl">
          <ElFormItem label="单位名称">
            <ElInput v-model="companyForm.name" placeholder="请输入单位名称" />
          </ElFormItem>
          <ElFormItem label="联系人">
            <ElInput v-model="companyForm.contactName" />
          </ElFormItem>
          <ElFormItem label="联系电话">
            <ElInput v-model="companyForm.contactPhone" />
          </ElFormItem>
          <ElFormItem>
            <ElButton type="primary" @click="handleSaveCompany">保存单位</ElButton>
          </ElFormItem>
        </ElForm>
        <ElTable :data="companies" stripe class="mt-4">
          <ElTableColumn prop="companyId" label="ID" width="100" />
          <ElTableColumn prop="name" label="名称" />
          <ElTableColumn prop="contactName" label="联系人" />
          <ElTableColumn prop="contactPhone" label="电话" />
        </ElTable>
      </ElTabPane>

      <ElTabPane label="批次" name="batch">
        <ElForm label-width="120px" class="max-w-xl">
          <ElFormItem label="合同">
            <ElSelect v-model="batchForm.contractId" placeholder="选择合同" style="width: 100%">
              <ElOption
                v-for="item in contracts"
                :key="String(item.contractId)"
                :label="item.contractName || String(item.contractId)"
                :value="String(item.contractId)"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="批次名称">
            <ElInput v-model="batchForm.batchName" />
          </ElFormItem>
          <ElFormItem label="套餐 ID">
            <ElInput v-model="batchForm.packageActivityId" placeholder="可选，默认取合同" />
          </ElFormItem>
          <ElFormItem label="预约日期">
            <ElDatePicker
              v-model="batchForm.bookingDate"
              type="date"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </ElFormItem>
          <ElFormItem>
            <ElButton type="primary" @click="handleSaveBatch">保存批次</ElButton>
          </ElFormItem>
        </ElForm>
        <div class="mb-4 mt-6 text-sm font-medium">合同维护</div>
        <ElForm label-width="120px" class="max-w-xl">
          <ElFormItem label="套餐 ID">
            <ElInput v-model="contractForm.packageActivityId" />
          </ElFormItem>
          <ElFormItem label="合同名称">
            <ElInput v-model="contractForm.contractName" />
          </ElFormItem>
          <ElFormItem>
            <ElButton type="primary" @click="handleSaveContract">保存合同</ElButton>
          </ElFormItem>
        </ElForm>
        <ElTable :data="batches" stripe class="mt-4">
          <ElTableColumn prop="batchId" label="批次 ID" width="100" />
          <ElTableColumn prop="batchName" label="名称" />
          <ElTableColumn prop="statusCode" label="状态" width="100" />
          <ElTableColumn prop="bookingDate" label="预约日" width="120" />
        </ElTable>
      </ElTabPane>

      <ElTabPane label="名册" name="roster">
        <ElForm inline class="mb-4">
          <ElFormItem label="姓名">
            <ElInput v-model="importForm.fullName" placeholder="必填" />
          </ElFormItem>
          <ElFormItem label="患者 ID">
            <ElInput v-model="importForm.patientId" />
          </ElFormItem>
          <ElFormItem label="学号">
            <ElInput v-model="importForm.studentNo" />
          </ElFormItem>
          <ElFormItem label="身份证">
            <ElInput v-model="importForm.idCardNo" />
          </ElFormItem>
          <ElFormItem>
            <ElButton type="primary" @click="handleImportRoster">导入一行</ElButton>
          </ElFormItem>
        </ElForm>
        <ElTable v-loading="rosterLoading" :data="rosterRows" stripe>
          <ElTableColumn prop="fullName" label="姓名" />
          <ElTableColumn prop="studentNo" label="学号" />
          <ElTableColumn prop="className" label="班级" />
          <ElTableColumn prop="payStatusCode" label="收费" width="90">
            <template #default="{ row }">
              <ElTag :type="row.payStatusCode === 'PAID' ? 'success' : 'info'" size="small">
                {{ row.payStatusCode === 'PAID' ? '已收费' : '未收费' }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn prop="arrivalStatusCode" label="到检" width="90" />
        </ElTable>
        <ElPagination
          v-model:current-page="rosterPageNo"
          v-model:page-size="rosterPageSize"
          :total="rosterTotal"
          layout="total, prev, pager, next"
          class="mt-4"
          @current-change="loadRoster"
        />
      </ElTabPane>

      <ElTabPane label="收费" name="charge">
        <div class="mb-3 flex items-center gap-2">
          <ElButton
            :type="chargeSubTab === 'unpaid' ? 'primary' : 'default'"
            @click="chargeSubTab = 'unpaid'"
          >
            未收费
          </ElButton>
          <ElButton
            :type="chargeSubTab === 'paid' ? 'primary' : 'default'"
            @click="chargeSubTab = 'paid'"
          >
            已收费
          </ElButton>
          <span v-if="preSettleInfo" class="ml-4 text-sm text-gray-600">{{ preSettleInfo }}</span>
        </div>
        <div v-if="chargeSubTab === 'unpaid'" class="mb-3 flex gap-2">
          <ElButton @click="handlePreSettle">预结算</ElButton>
          <ElButton type="primary" @click="handleConfirmSettlement">确认批量收费</ElButton>
        </div>
        <ElTable
          v-loading="rosterLoading"
          :data="rosterRows"
          stripe
          @selection-change="handleRosterSelection"
        >
          <ElTableColumn v-if="chargeSubTab === 'unpaid'" type="selection" width="48" />
          <ElTableColumn prop="fullName" label="姓名" />
          <ElTableColumn prop="patientId" label="患者 ID" width="100" />
          <ElTableColumn prop="payStatusCode" label="收费态" width="90" />
          <ElTableColumn prop="encounterId" label="就诊 ID" width="100" />
        </ElTable>
        <ElPagination
          v-model:current-page="rosterPageNo"
          v-model:page-size="rosterPageSize"
          :total="rosterTotal"
          layout="total, prev, pager, next"
          class="mt-4"
          @current-change="loadChargeRoster"
        />
      </ElTabPane>

      <ElTabPane label="到检" name="arrival">
        <ElTable v-loading="rosterLoading" :data="rosterRows" stripe>
          <ElTableColumn prop="fullName" label="姓名" />
          <ElTableColumn prop="payStatusCode" label="收费" width="90">
            <template #default="{ row }">
              <ElTag :type="row.payStatusCode === 'PAID' ? 'success' : 'danger'" size="small">
                {{ row.payStatusCode === 'PAID' ? '已收费' : '未收费' }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn prop="arrivalStatusCode" label="到检" width="90" />
          <ElTableColumn label="操作" width="120">
            <template #default="{ row }">
              <ElButton
                link
                type="primary"
                :disabled="row.payStatusCode !== 'PAID' || row.arrivalStatusCode === 'ARRIVED'"
                @click="handleConfirmArrival(row)"
              >
                确认到检
              </ElButton>
            </template>
          </ElTableColumn>
        </ElTable>
        <ElPagination
          v-model:current-page="rosterPageNo"
          v-model:page-size="rosterPageSize"
          :total="rosterTotal"
          layout="total, prev, pager, next"
          class="mt-4"
          @current-change="loadRoster"
        />
      </ElTabPane>
    </ElTabs>
  </Page>
</template>
