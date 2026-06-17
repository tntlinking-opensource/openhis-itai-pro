<script setup lang="ts">
import type { BookableSlotDto } from '#/api/booking/booking-register';
import type {
  GroupBatchDto,
  GroupOrgDto,
  GroupRosterDto,
  HealthCheckPackageDto,
} from '#/api/booking/health-checkup-booking';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { pagePatientsForSelectApi } from '#/api/booking/booking-register';
import {
  batchBookGroupRosterApi,
  checkInHealthCheckupApi,
  createIndividualBookingApi,
  deleteGroupBatchApi,
  deleteGroupOrgApi,
  importGroupRosterApi,
  listIndividualBookableSlotsApi,
  listIndividualPackagesApi,
  pageGroupBatchesApi,
  pageGroupOrgsApi,
  pageGroupRostersApi,
  saveGroupBatchApi,
  saveGroupOrgApi,
} from '#/api/booking/health-checkup-booking';
import SelectTable from '#/components/select-table.vue';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { useBookingOrganization } from '#/views/booking/composables/use-booking-organization';
import { useRequest } from '#/hooks/use-request';
import {
  ElButton,
  ElDatePicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTabPane,
  ElTabs,
  ElTable,
  ElTableColumn,
} from 'element-plus';

defineOptions({ name: 'BookingHealthCheckup' });

const { organizationId } = useBookingOrganization();
const mainTab = ref('group');
const groupSubTab = ref('org');

const orgList = ref<GroupOrgDto[]>([]);
const orgTotal = ref(0);
const orgQuery = reactive({ pageNo: 1, pageSize: 20, keyword: '' });
const orgEditVisible = ref(false);
const orgForm = ref<Partial<GroupOrgDto>>({});

const batchList = ref<GroupBatchDto[]>([]);
const batchTotal = ref(0);
const batchQuery = reactive({
  pageNo: 1,
  pageSize: 20,
  groupOrgId: undefined as number | undefined,
});
const batchEditVisible = ref(false);
const batchForm = ref<Partial<GroupBatchDto>>({});

const rosterList = ref<GroupRosterDto[]>([]);
const rosterTotal = ref(0);
const rosterQuery = reactive({
  pageNo: 1,
  pageSize: 20,
  batchId: undefined as number | undefined,
  keyword: '',
});

const packages = ref<HealthCheckPackageDto[]>([]);
const selectedPackageId = ref<number>();
const individualDate = ref('');
const individualSlots = ref<BookableSlotDto[]>([]);
const selectedSlotId = ref<number>();
const selectedPatientId = ref('');
const checkInBusNo = ref('');

const patientColumns = [
  { prop: 'name', label: '姓名', minWidth: 100 },
  { prop: 'patientBusNo', label: '患者号', minWidth: 120 },
];

const { run: runOrgs, loading: orgLoading } = useRequest(pageGroupOrgsApi, {
  manual: true,
  onSuccess: (res) => {
    orgList.value = res.content ?? [];
    orgTotal.value = Number(res.totalCount ?? 0);
  },
});

const { run: runBatches, loading: batchLoading } = useRequest(pageGroupBatchesApi, {
  manual: true,
  onSuccess: (res) => {
    batchList.value = res.content ?? [];
    batchTotal.value = Number(res.totalCount ?? 0);
  },
});

const { run: runRosters, loading: rosterLoading } = useRequest(pageGroupRostersApi, {
  manual: true,
  onSuccess: (res) => {
    rosterList.value = res.content ?? [];
    rosterTotal.value = Number(res.totalCount ?? 0);
  },
});

const { run: runPackages, loading: pkgLoading } = useRequest(listIndividualPackagesApi, {
  manual: true,
  onSuccess: (res) => {
    packages.value = res?.packages ?? [];
  },
});

const { run: runIndSlots, loading: indSlotsLoading } = useRequest(listIndividualBookableSlotsApi, {
  manual: true,
  onSuccess: (res) => {
    individualSlots.value = res?.slots ?? [];
    selectedSlotId.value = undefined;
  },
});

const { run: runIndBook, loading: indBookLoading } = useRequest(createIndividualBookingApi, {
  manual: true,
  onSuccess: (res) => {
    ElMessage.success(`个检预约成功：${res?.busNo ?? ''}`);
    void loadIndividualSlots();
  },
});

async function loadOrgs() {
  await runOrgs({
    keyword: orgQuery.keyword.trim() || undefined,
    pageNo: orgQuery.pageNo,
    pageSize: orgQuery.pageSize,
  });
}

async function loadBatches() {
  await runBatches({
    groupOrgId: batchQuery.groupOrgId,
    pageNo: batchQuery.pageNo,
    pageSize: batchQuery.pageSize,
  });
}

async function loadRosters() {
  if (!rosterQuery.batchId) {
    rosterList.value = [];
    rosterTotal.value = 0;
    return;
  }
  await runRosters({
    batchId: rosterQuery.batchId,
    keyword: rosterQuery.keyword.trim() || undefined,
    pageNo: rosterQuery.pageNo,
    pageSize: rosterQuery.pageSize,
  });
}

async function loadIndividualPackages() {
  if (!organizationId.value) return;
  await runPackages({ organizationId: organizationId.value });
}

async function loadIndividualSlots() {
  if (!organizationId.value || !selectedPackageId.value || !individualDate.value) return;
  await runIndSlots({
    organizationId: organizationId.value,
    packageId: selectedPackageId.value,
    date: individualDate.value,
  });
}

async function handleSaveOrg() {
  if (!orgForm.value.orgName?.trim()) {
    ElMessage.warning('请填写单位名称');
    return;
  }
  await saveGroupOrgApi({
    id: orgForm.value.id,
    orgCode: orgForm.value.orgCode,
    orgName: orgForm.value.orgName.trim(),
    contactName: orgForm.value.contactName,
    contactPhone: orgForm.value.contactPhone,
    settlementMode: orgForm.value.settlementMode,
  });
  ElMessage.success('单位档案已保存');
  orgEditVisible.value = false;
  await loadOrgs();
}

async function handleDeleteOrg(row: GroupOrgDto) {
  if (!row.id) return;
  try {
    await ElMessageBox.confirm(`确认删除「${row.orgName}」？`, '删除单位');
    await deleteGroupOrgApi({ id: row.id });
    ElMessage.success('已删除');
    await loadOrgs();
  } catch {
    // cancel
  }
}

async function handleSaveBatch() {
  if (!batchForm.value.batchName?.trim()) {
    ElMessage.warning('请填写批次名称');
    return;
  }
  await saveGroupBatchApi({
    id: batchForm.value.id,
    groupOrgId: batchForm.value.groupOrgId,
    batchName: batchForm.value.batchName.trim(),
    bookingDate: batchForm.value.bookingDate,
    slotStartTime: batchForm.value.slotStartTime,
    capacity: batchForm.value.capacity,
    packageName: batchForm.value.packageName,
  });
  ElMessage.success('批次已保存');
  batchEditVisible.value = false;
  await loadBatches();
}

async function handleDeleteBatch(row: GroupBatchDto) {
  if (!row.id) return;
  try {
    await ElMessageBox.confirm(`确认删除批次「${row.batchName}」？`, '删除批次');
    await deleteGroupBatchApi({ id: row.id });
    ElMessage.success('已删除');
    await loadBatches();
  } catch {
    // cancel
  }
}

async function handleImportRoster() {
  if (!rosterQuery.batchId) {
    ElMessage.warning('请先选择批次');
    return;
  }
  const res = await importGroupRosterApi({ batchId: rosterQuery.batchId });
  ElMessage.success(`名单导入完成，共 ${res?.importedCount ?? 0} 条`);
  await loadRosters();
}

async function handleBatchBook() {
  if (!rosterQuery.batchId) return;
  const res = await batchBookGroupRosterApi({ batchId: rosterQuery.batchId });
  ElMessage.success(`批量预约完成，共 ${res?.bookedCount ?? 0} 人`);
  await loadRosters();
}

async function handleCheckIn() {
  if (!organizationId.value || !checkInBusNo.value.trim()) {
    ElMessage.warning('请输入预约单号');
    return;
  }
  const res = await checkInHealthCheckupApi({
    organizationId: organizationId.value,
    appointmentBusNo: checkInBusNo.value.trim(),
  });
  ElMessage.success(`到检签到成功，排队号 ${res?.displayOrder ?? ''}`);
  checkInBusNo.value = '';
}

async function handleIndividualBook() {
  if (
    !organizationId.value ||
    !selectedPatientId.value ||
    !selectedPackageId.value ||
    !selectedSlotId.value
  ) {
    ElMessage.warning('请选择患者、套餐与时段');
    return;
  }
  await runIndBook({
    organizationId: organizationId.value,
    patientId: Number(selectedPatientId.value),
    packageId: selectedPackageId.value,
    slotId: selectedSlotId.value,
    originChannel: 'WINDOW',
  });
}

async function queryPatients({
  keyword = '',
  pageNo = 1,
  pageSize = 20,
}: {
  keyword?: string;
  pageNo?: number;
  pageSize?: number;
}) {
  const data = await pagePatientsForSelectApi({ keyword, pageNo, pageSize });
  const { content, totalCount } = parsePagedRowsFromApiData(data);
  return {
    content: content.map((row) => ({
      ...row,
      value: String(row.patientId ?? ''),
      label: row.name,
    })),
    totalElements: totalCount,
  };
}

onMounted(async () => {
  individualDate.value = new Date().toISOString().slice(0, 10);
  await loadOrgs();
  await loadBatches();
  await loadIndividualPackages();
});
</script>

<template>
  <Page auto-content-height>
    <div class="health-checkup-page">
      <ElTabs v-model="mainTab">
        <ElTabPane label="团检" name="group">
          <ElTabs v-model="groupSubTab" type="card">
            <ElTabPane label="单位档案" name="org">
              <div class="health-checkup-page__toolbar">
                <ElInput
                  v-model="orgQuery.keyword"
                  placeholder="单位名称"
                  style="width: 200px"
                  clearable
                />
                <ElButton type="primary" :loading="orgLoading" @click="loadOrgs">查询</ElButton>
                <ElButton
                  @click="
                    orgForm = {};
                    orgEditVisible = true;
                  "
                  >新建单位</ElButton
                >
              </div>
              <ElTable v-loading="orgLoading" :data="orgList" border stripe>
                <ElTableColumn prop="orgName" label="单位名称" min-width="160" />
                <ElTableColumn prop="orgCode" label="编码" width="120" />
                <ElTableColumn prop="contactName" label="联系人" width="100" />
                <ElTableColumn prop="contactPhone" label="电话" width="120" />
                <ElTableColumn label="操作" width="140">
                  <template #default="{ row }">
                    <ElButton
                      link
                      type="primary"
                      @click="
                        orgForm = { ...row };
                        orgEditVisible = true;
                      "
                      >编辑</ElButton
                    >
                    <ElButton link type="danger" @click="handleDeleteOrg(row)">删除</ElButton>
                  </template>
                </ElTableColumn>
              </ElTable>
            </ElTabPane>

            <ElTabPane label="批次管理" name="batch">
              <div class="health-checkup-page__toolbar">
                <ElSelect
                  v-model="batchQuery.groupOrgId"
                  clearable
                  placeholder="单位"
                  style="width: 200px"
                >
                  <ElOption
                    v-for="org in orgList"
                    :key="org.id"
                    :label="org.orgName"
                    :value="org.id!"
                  />
                </ElSelect>
                <ElButton type="primary" :loading="batchLoading" @click="loadBatches"
                  >查询</ElButton
                >
                <ElButton
                  @click="
                    batchForm = {};
                    batchEditVisible = true;
                  "
                  >新建批次</ElButton
                >
              </div>
              <ElTable v-loading="batchLoading" :data="batchList" border stripe>
                <ElTableColumn prop="batchName" label="批次" min-width="140" />
                <ElTableColumn prop="groupOrgName" label="单位" min-width="120" />
                <ElTableColumn prop="bookingDate" label="日期" width="110" />
                <ElTableColumn prop="slotStartTime" label="时段" width="90" />
                <ElTableColumn label="容量" width="100">
                  <template #default="{ row }"
                    >{{ row.bookedCount ?? 0 }} / {{ row.capacity ?? 0 }}</template
                  >
                </ElTableColumn>
                <ElTableColumn label="操作" width="140">
                  <template #default="{ row }">
                    <ElButton
                      link
                      type="primary"
                      @click="
                        batchForm = { ...row };
                        batchEditVisible = true;
                      "
                      >编辑</ElButton
                    >
                    <ElButton link type="danger" @click="handleDeleteBatch(row)">删除</ElButton>
                  </template>
                </ElTableColumn>
              </ElTable>
            </ElTabPane>

            <ElTabPane label="名单与批量预约" name="roster">
              <div class="health-checkup-page__toolbar">
                <ElSelect
                  v-model="rosterQuery.batchId"
                  clearable
                  placeholder="选择批次"
                  style="width: 220px"
                  @change="loadRosters"
                >
                  <ElOption
                    v-for="batch in batchList"
                    :key="batch.id"
                    :label="batch.batchName"
                    :value="batch.id!"
                  />
                </ElSelect>
                <ElInput
                  v-model="rosterQuery.keyword"
                  placeholder="姓名/学号"
                  style="width: 160px"
                  clearable
                />
                <ElButton type="primary" :loading="rosterLoading" @click="loadRosters"
                  >查询</ElButton
                >
                <ElButton @click="handleImportRoster">导入名单</ElButton>
                <ElButton type="primary" @click="handleBatchBook">批量预约</ElButton>
                <ElInput v-model="checkInBusNo" placeholder="预约单号到检" style="width: 160px" />
                <ElButton @click="handleCheckIn">到检签到</ElButton>
              </div>
              <ElTable v-loading="rosterLoading" :data="rosterList" border stripe>
                <ElTableColumn prop="name" label="姓名" width="100" />
                <ElTableColumn prop="studentNo" label="学号" width="120" />
                <ElTableColumn prop="idCardNoMasked" label="证件号" min-width="140" />
                <ElTableColumn prop="bookingStatusView" label="预约状态" width="100" />
                <ElTableColumn prop="appointmentBusNo" label="预约单号" min-width="120" />
              </ElTable>
            </ElTabPane>
          </ElTabs>
        </ElTabPane>

        <ElTabPane label="个检" name="individual">
          <div v-loading="pkgLoading" class="health-checkup-page__individual">
            <ElForm label-width="88px" class="health-checkup-page__individual-form">
              <ElFormItem label="套餐">
                <ElSelect
                  v-model="selectedPackageId"
                  filterable
                  placeholder="选择套餐"
                  style="width: 280px"
                >
                  <ElOption
                    v-for="pkg in packages"
                    :key="pkg.packageId"
                    :label="pkg.packageName"
                    :value="pkg.packageId!"
                  />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="患者">
                <SelectTable
                  v-model="selectedPatientId"
                  :columns="patientColumns"
                  value-key="value"
                  label-key="label"
                  placeholder="搜索患者"
                  :remote-method="queryPatients"
                />
              </ElFormItem>
              <ElFormItem label="预约日期">
                <ElDatePicker
                  v-model="individualDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  style="width: 160px"
                  @change="loadIndividualSlots"
                />
                <ElButton class="ml-2" :loading="indSlotsLoading" @click="loadIndividualSlots"
                  >刷新可约</ElButton
                >
              </ElFormItem>
            </ElForm>
            <div v-loading="indSlotsLoading" class="health-checkup-page__slots">
              <button
                v-for="slot in individualSlots"
                :key="slot.slotId"
                type="button"
                class="health-checkup-page__slot"
                :class="{ 'is-selected': selectedSlotId === slot.slotId, 'is-full': slot.fullFlag }"
                :disabled="slot.fullFlag"
                @click="selectedSlotId = slot.slotId"
              >
                <span>{{ slot.startTime?.slice(11, 16) }} - {{ slot.endTime?.slice(11, 16) }}</span>
                <span>余 {{ slot.remainingCount ?? 0 }}</span>
              </button>
            </div>
            <ElButton type="primary" :loading="indBookLoading" @click="handleIndividualBook"
              >确认个检预约</ElButton
            >
          </div>
        </ElTabPane>
      </ElTabs>

      <ElDialog
        v-model="orgEditVisible"
        :title="orgForm.id ? '编辑单位' : '新建单位'"
        width="480px"
      >
        <ElForm label-width="88px">
          <ElFormItem label="单位名称" required><ElInput v-model="orgForm.orgName" /></ElFormItem>
          <ElFormItem label="编码"><ElInput v-model="orgForm.orgCode" /></ElFormItem>
          <ElFormItem label="联系人"><ElInput v-model="orgForm.contactName" /></ElFormItem>
          <ElFormItem label="电话"><ElInput v-model="orgForm.contactPhone" /></ElFormItem>
        </ElForm>
        <template #footer>
          <ElButton @click="orgEditVisible = false">取消</ElButton>
          <ElButton type="primary" @click="handleSaveOrg">保存</ElButton>
        </template>
      </ElDialog>

      <ElDialog
        v-model="batchEditVisible"
        :title="batchForm.id ? '编辑批次' : '新建批次'"
        width="520px"
      >
        <ElForm label-width="88px">
          <ElFormItem label="单位">
            <ElSelect v-model="batchForm.groupOrgId" filterable style="width: 100%">
              <ElOption
                v-for="org in orgList"
                :key="org.id"
                :label="org.orgName"
                :value="org.id!"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="批次名称" required
            ><ElInput v-model="batchForm.batchName"
          /></ElFormItem>
          <ElFormItem label="预约日期"
            ><ElDatePicker v-model="batchForm.bookingDate" type="date" value-format="YYYY-MM-DD"
          /></ElFormItem>
          <ElFormItem label="时段"
            ><ElInput v-model="batchForm.slotStartTime" placeholder="08:00"
          /></ElFormItem>
          <ElFormItem label="容量"
            ><ElInputNumber v-model="batchForm.capacity" :min="1"
          /></ElFormItem>
          <ElFormItem label="套餐"><ElInput v-model="batchForm.packageName" /></ElFormItem>
        </ElForm>
        <template #footer>
          <ElButton @click="batchEditVisible = false">取消</ElButton>
          <ElButton type="primary" @click="handleSaveBatch">保存</ElButton>
        </template>
      </ElDialog>
    </div>
  </Page>
</template>

<style scoped>
.health-checkup-page {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.health-checkup-page__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.health-checkup-page__individual {
  max-width: 720px;
}

.health-checkup-page__slots {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-height: 72px;
  margin: 12px 0;
}

.health-checkup-page__slot {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 108px;
  padding: 10px 12px;
  cursor: pointer;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.health-checkup-page__slot.is-selected {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.health-checkup-page__slot.is-full {
  cursor: not-allowed;
  opacity: 0.55;
}
</style>
