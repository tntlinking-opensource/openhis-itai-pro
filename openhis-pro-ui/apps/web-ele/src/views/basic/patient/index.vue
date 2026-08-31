<script setup lang="ts">
import type { TableColumn } from '@vben-core/table-layout-ui';

import type { GetPatientPageParams, Patient } from '#/api/basic/patient';
import type { ApiOptionItem } from '#/types/api-response';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox } from 'element-plus';

import {
  deletePatientApi,
  getPatientApi,
  getPatientListApi,
  patientInitApi,
} from '#/api/basic/patient';

import { BASIC_TABLE_MAX_HEIGHT } from '../basic-table-max-height';
import EditDialog from './edit-dialog.vue';
// 学籍相关 UI 暂隐藏（产品未上线）；入口恢复时再挂回 StudentInfoDialog。

defineOptions({
  name: 'PatientList',
});

const tableData = ref<Patient[]>([]);
const loading = ref(false);
const total = ref(0);

const initQueryParams: GetPatientPageParams = {
  pageNo: 1,
  pageSize: 10,
  keyword: '',
};

const queryParams = reactive<GetPatientPageParams>({ ...initQueryParams });

const formItems = ref([
  {
    label: '关键字',
    prop: 'keyword',
    type: 'input',
    placeholder: '请输入姓名或证件号',
    clearable: true,
  },
]);

const tableColumns = ref<TableColumn[]>([
  {
    type: 'index',
    label: '序号',
  },
  {
    label: '姓名',
    prop: 'name',
  },
  {
    label: '年龄',
    prop: 'age',
  },
  {
    label: '性别',
    prop: 'genderEnumText',
  },
  {
    label: '电话',
    prop: 'phone',
    showOverflowTooltip: true,
  },
  {
    label: '证件号',
    prop: 'idCard',
    showOverflowTooltip: true,
  },
  {
    label: '操作',
    prop: 'action',
    fixed: 'right',
    slot: 'action',
    align: 'center',
    headerAlign: 'center',
  },
]);

const handleQuery = async (params?: Partial<GetPatientPageParams>) => {
  queryParams.pageNo = 1;
  if (params?.keyword !== undefined) {
    queryParams.keyword = String(params.keyword ?? '');
  }
  if (params?.pageSize !== undefined) {
    queryParams.pageSize = params.pageSize;
  }
  await loadPatientList();
};

const handleReset = () => {
  Object.assign(queryParams, initQueryParams);
  void loadPatientList();
};

const handlePagination = (pagination: { limit: number; page: number }) => {
  queryParams.pageSize = pagination.limit;
  queryParams.pageNo = pagination.page;
  void loadPatientList();
};

/** 防止连续查询竞态：仅最后一次请求结果写回表格 */
let loadPatientListSeq = 0;

async function loadPatientList() {
  const seq = ++loadPatientListSeq;
  loading.value = true;
  try {
    const response = await getPatientListApi({ ...queryParams });
    if (seq !== loadPatientListSeq) {
      return;
    }
    tableData.value = response.content;
    total.value = response.totalCount;
  } catch (error) {
    if (seq !== loadPatientListSeq) {
      return;
    }
    console.error('加载数据失败:', error);
    ElMessage.error('加载数据失败，请稍后重试');
    tableData.value = [];
    total.value = 0;
  } finally {
    if (seq === loadPatientListSeq) {
      loading.value = false;
    }
  }
}

/** 证件类型下拉选项（来自 init 的 identityDocumentType，排除身份证后供「其他证件」使用） */
const certTypeOptions = ref<ApiOptionItem[]>([]);
/** 编辑弹窗用：婚姻状况、血型 ABO、Rh 血型、性别（来自 patient/init） */
const maritalStatusOptions = ref<ApiOptionItem[]>([]);
const bloodTypeAboOptions = ref<ApiOptionItem[]>([]);
const bloodTypeRhOptions = ref<ApiOptionItem[]>([]);
const sexOptions = ref<ApiOptionItem[]>([]);
const educationLevelOptions = ref<ApiOptionItem[]>([]);

const dialogVisible = ref(false);
const dialogFormData = ref<null | Partial<Patient>>(null);

/** 新增：打开弹窗，不传表单数据 */
const handleAdd = () => {
  dialogFormData.value = null;
  dialogVisible.value = true;
};

/** 编辑：先拉取患者详情（含其他证件列表 patientIdInfoList），再打开弹窗回显 */
const handleEdit = async (row: Patient) => {
  if (!row.id) {
    ElMessage.warning('缺少患者ID');
    return;
  }
  try {
    const detail = await getPatientApi(String(row.id));
    dialogFormData.value = detail ?? { ...row };
    dialogVisible.value = true;
  } catch (error) {
    console.error('获取患者详情失败:', error);
    ElMessage.error('获取患者详情失败，请稍后重试');
  }
};

const handleDelete = async (row: Patient) => {
  if (!row.id) {
    ElMessage.warning('缺少患者ID');
    return;
  }

  try {
    await ElMessageBox.confirm('确定要删除该患者吗？删除后无法恢复！', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    await deletePatientApi(row.id);
    ElMessage.success('删除成功');
    await loadPatientList();
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error?.msg || '删除失败，请稍后重试');
    }
  }
};

/** 页面加载时拉取患者管理初始化数据（含证件类型等），供编辑弹窗「其他证件」下拉使用 */
onMounted(async () => {
  try {
    const initRes = await patientInitApi();
    const allCertTypes = initRes?.identityDocumentType ?? [];
    // 其他证件类型：排除居民身份证（value 为 1），仅展示户口簿、护照、军官证等
    certTypeOptions.value = allCertTypes.filter(
      (opt) => opt.value !== null && String(opt.value) !== '1',
    );
    maritalStatusOptions.value = initRes?.maritalStatus ?? [];
    bloodTypeAboOptions.value = initRes?.bloodTypeABO ?? [];
    bloodTypeRhOptions.value = initRes?.bloodTypeRH ?? [];
    sexOptions.value = initRes?.sex ?? [];
    educationLevelOptions.value = initRes?.educationLevelOptions ?? [];
  } catch (error) {
    console.error('患者管理初始化失败:', error);
  }
  void loadPatientList();
});
</script>

<template>
  <Page>
    <TableLayout
      :table-data="tableData"
      :loading="loading"
      :total="total"
      :query-params="queryParams"
      :form-items="formItems"
      :table-columns="tableColumns"
      :max-height="BASIC_TABLE_MAX_HEIGHT"
      :show-top-query="true"
      :show-pagination="true"
      row-key="id"
      @query="handleQuery"
      @reset="handleReset"
      @pagination="handlePagination"
    >
      <template #toolbar-actions>
        <ElButton type="primary" @click="handleAdd">新增</ElButton>
      </template>
      <template #action="{ row }">
        <ElButton type="primary" link @click="handleEdit(row)">编辑</ElButton>
        <ElButton type="danger" link @click="handleDelete(row)">删除</ElButton>
      </template>
    </TableLayout>
    <EditDialog
      v-model:visible="dialogVisible"
      :cert-type-options="certTypeOptions"
      :marital-status-options="maritalStatusOptions"
      :blood-type-abo-options="bloodTypeAboOptions"
      :blood-type-rh-options="bloodTypeRhOptions"
      :sex-options="sexOptions"
      :education-level-options="educationLevelOptions"
      :form-data="dialogFormData || undefined"
      @success="handleQuery"
    />
  </Page>
</template>
