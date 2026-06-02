<script setup lang="ts">
import type { CaseRecordStatusCode, RefreshSectionCode } from '#/api/mrms/types';

import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import {
  ElAlert,
  ElAnchor,
  ElAnchorLink,
  ElButton,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTag,
} from 'element-plus';

import { generateHomepagePdfApi, receiveCaseRecordApi, returnCaseRecordApi } from '#/api/mrms';
import AppDialog from '#/components/app-dialog.vue';
import { useRequest } from '#/hooks/use-request';
import { useUserStore } from '@vben/stores';
import { pdfBase64ToObjectUrl, revokePdfObjectUrl } from '#/emrEditor/utils/emr-pdf-blob';

import {
  CASE_RECORD_STATUS_LABEL,
  CASE_RECORD_STATUS_TAG,
  DOCTOR_EDITABLE_STATUS,
  GENDER_OPTIONS,
  HOMEPAGE_ANCHOR_SECTIONS,
  RECORD_ROOM_RECEIVE_STATUS,
  RECORD_ROOM_RETURN_STATUS,
} from '../case-record.constants';
import HomepageDiagnosisGrid from './components/homepage-diagnosis-grid.vue';
import HomepageProcedureGrid from './components/homepage-procedure-grid.vue';
import { useCaseRecordContext } from './composables/use-case-record-context';
import {
  detectRecordRoomRole,
  isHomepageContentEmpty,
  sectionToRefreshCode,
} from './homepage-form.util';

defineOptions({ name: 'MrmsCaseRecordHomepage' });

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

const caseRecordId = computed(() => String(route.params.caseRecordId ?? '').trim());

const {
  caseRecord,
  homepage,
  formModel,
  fieldSources,
  formDirty,
  loadError,
  loading,
  saving,
  refreshing,
  submitting,
  loadAll,
  save,
  refreshSections,
  submit,
  markDirty,
} = useCaseRecordContext(caseRecordId);

const isRecordRoom = computed(() =>
  detectRecordRoomRole(userStore.userInfo?.roles as string[] | undefined),
);

const returnDialogVisible = ref(false);
const returnReason = ref('');

const statusCode = computed(
  () => (caseRecord.value?.statusCode ?? '') as CaseRecordStatusCode | '',
);

const isFrozen = computed(() => homepage.value?.frozen === true);

const canDoctorEdit = computed(
  () =>
    !isFrozen.value &&
    !isRecordRoom.value &&
    DOCTOR_EDITABLE_STATUS.includes(statusCode.value as CaseRecordStatusCode),
);

const canSave = computed(() => canDoctorEdit.value);
const canImportAll = computed(() => canDoctorEdit.value);
const canSubmit = computed(() => canDoctorEdit.value);
const canReceive = computed(
  () => isRecordRoom.value && RECORD_ROOM_RECEIVE_STATUS.includes(statusCode.value as never),
);
const canReturn = computed(
  () => isRecordRoom.value && RECORD_ROOM_RETURN_STATUS.includes(statusCode.value as never),
);

const showImportHint = computed(
  () => formModel.value && isHomepageContentEmpty(homepage.value) && canDoctorEdit.value,
);

const { run: runReceive, loading: receiving } = useRequest(receiveCaseRecordApi, {
  manual: true,
  onSuccess: () => {
    ElMessage.success('接收成功');
    loadAll();
  },
});

const { run: runReturn, loading: returning } = useRequest(returnCaseRecordApi, {
  manual: true,
  onSuccess: () => {
    returnDialogVisible.value = false;
    returnReason.value = '';
    ElMessage.success('已退回医生');
    loadAll();
  },
});

const { run: runPrint, loading: printing } = useRequest(generateHomepagePdfApi, {
  manual: true,
  showErrorMessage: true,
});

/** 返回病案列表 */
function goBack() {
  router.back();
}

/** 章节 HIS 导入 */
async function importSection(section: string) {
  const code = sectionToRefreshCode(section) as RefreshSectionCode;
  await refreshSections([code], false);
}

/** 全部 HIS 导入 */
async function importAll() {
  await refreshSections(['all'], false);
}

/** 保存前确认脏数据 */
async function onSave() {
  await save();
}

/** 医生提交首页 */
async function onSubmit() {
  await ElMessageBox.confirm('确认提交病案首页？提交后需病案室接收。', '提交', {
    type: 'warning',
  });
  if (formDirty.value) {
    await save();
  }
  await submit();
}

/** 病案室接收 */
async function onReceive() {
  const id = caseRecordId.value;
  if (!id) {
    return;
  }
  await runReceive({ caseRecordId: id });
}

/** 打开退回弹窗 */
function openReturnDialog() {
  returnReason.value = '';
  returnDialogVisible.value = true;
}

/** 病案室退回 */
async function confirmReturn() {
  const reason = returnReason.value.trim();
  if (!reason) {
    ElMessage.warning('请填写退回原因');
    return;
  }
  await runReturn({ caseRecordId: caseRecordId.value, reason });
}

/** 打印预览（后端未实现时会报错） */
async function onPrint() {
  const id = caseRecordId.value;
  if (!id) {
    return;
  }
  if (formDirty.value) {
    try {
      await ElMessageBox.confirm('有未保存修改，是否先保存再打印？', '打印', {
        confirmButtonText: '保存并打印',
        cancelButtonText: '取消',
        type: 'warning',
      });
      await save();
    } catch {
      return;
    }
  }
  let url: string | undefined;
  try {
    const res = await runPrint(id);
    if (!res?.pdfBase64) {
      ElMessage.warning('未生成 PDF');
      return;
    }
    url = pdfBase64ToObjectUrl(res.pdfBase64);
    window.open(url, '_blank');
  } finally {
    revokePdfObjectUrl(url);
  }
}

/** 字段来源角标（his / manual） */
function sourceHint(field: string): string {
  const src = fieldSources.value[field];
  if (src === 'his') {
    return 'HIS';
  }
  if (src === 'manual') {
    return '手填';
  }
  return '';
}

watch(caseRecordId, () => {
  loadAll();
});

onMounted(() => {
  loadAll();
});
</script>

<template>
  <Page auto-content-height class="mrms-homepage-page">
    <div v-loading="loading" class="flex h-full min-h-0 flex-col">
      <header
        class="mb-12px gap-8px pb-12px flex flex-wrap items-center justify-between border-b border-[var(--el-border-color-light)]"
      >
        <div class="gap-12px flex flex-wrap items-center">
          <ElButton @click="goBack">返回</ElButton>
          <span class="text-16px font-600" style="color: var(--el-text-color-primary)">
            病案首页
          </span>
          <span style="color: var(--el-text-color-regular)">
            {{ caseRecord?.patientName }} / {{ caseRecord?.medcasno }} /
            {{ caseRecord?.inpatientNo }}
          </span>
          <ElTag :type="CASE_RECORD_STATUS_TAG[statusCode] ?? 'info'" size="small">
            {{ CASE_RECORD_STATUS_LABEL[statusCode] ?? statusCode }}
          </ElTag>
        </div>
        <div class="gap-8px flex flex-wrap">
          <ElButton v-if="canImportAll" :loading="refreshing" @click="importAll">
            全部导入
          </ElButton>
          <ElButton v-if="canSave" type="primary" :loading="saving" @click="onSave">
            保存
          </ElButton>
          <ElButton v-if="canSubmit" type="primary" :loading="submitting" @click="onSubmit">
            提交
          </ElButton>
          <ElButton v-if="canReceive" type="success" :loading="receiving" @click="onReceive">
            接收
          </ElButton>
          <ElButton v-if="canReturn" type="warning" :loading="returning" @click="openReturnDialog">
            退回
          </ElButton>
          <ElButton :loading="printing" @click="onPrint"> 打印预览 </ElButton>
        </div>
      </header>

      <ElEmpty
        v-if="!loading && !formModel"
        class="flex-1"
        :description="loadError || '未加载到病案首页数据'"
      >
        <ElButton type="primary" @click="loadAll">重新加载</ElButton>
      </ElEmpty>

      <ElAlert
        v-if="showImportHint"
        class="mb-12px"
        type="info"
        show-icon
        :closable="false"
        title="首页尚未写入明细"
        description="病案主档创建后，首页表默认为空。请点击顶栏「全部导入」从 HIS 拉取患者/住院/诊断/手术等；导入后需保存。"
      />

      <div v-if="formModel" class="gap-16px flex min-h-0 flex-1">
        <aside class="w-120px shrink-0">
          <ElAnchor :offset="80">
            <ElAnchorLink
              v-for="sec in HOMEPAGE_ANCHOR_SECTIONS"
              :key="sec.id"
              :href="`#${sec.id}`"
              :title="sec.label"
            />
          </ElAnchor>
        </aside>

        <main class="pr-8px min-h-0 flex-1 overflow-y-auto">
          <ElForm
            :model="formModel"
            label-width="108px"
            label-position="right"
            :disabled="!canDoctorEdit"
            size="default"
            class="mrms-homepage-form"
          >
            <section :id="HOMEPAGE_ANCHOR_SECTIONS[0].id" class="mb-24px">
              <div class="mb-12px flex items-center justify-between">
                <h3 class="section-title">基本信息</h3>
                <ElButton
                  v-if="canImportAll"
                  link
                  type="primary"
                  :loading="refreshing"
                  @click="importSection('basic')"
                >
                  导入本章
                </ElButton>
              </div>
              <div class="gap-x-16px grid grid-cols-2">
                <ElFormItem label="姓名">
                  <ElInput v-model="formModel.psnName" @input="markDirty" />
                  <span v-if="sourceHint('psnName')" class="ml-4px text-12px opacity-60">
                    {{ sourceHint('psnName') }}
                  </span>
                </ElFormItem>
                <ElFormItem label="性别">
                  <ElSelect
                    v-model="formModel.gendCode"
                    clearable
                    class="w-full"
                    @change="markDirty"
                  >
                    <ElOption
                      v-for="opt in GENDER_OPTIONS"
                      :key="opt.value"
                      :label="opt.label"
                      :value="opt.value"
                    />
                  </ElSelect>
                </ElFormItem>
                <ElFormItem label="出生日期">
                  <ElInput v-model="formModel.brdy" placeholder="YYYY-MM-DD" @input="markDirty" />
                </ElFormItem>
                <ElFormItem label="证件号">
                  <ElInput v-model="formModel.certno" @input="markDirty" />
                </ElFormItem>
                <ElFormItem label="现住址" class="col-span-2">
                  <ElInput v-model="formModel.currAddr" @input="markDirty" />
                </ElFormItem>
                <ElFormItem label="电话">
                  <ElInput v-model="formModel.psnTel" @input="markDirty" />
                </ElFormItem>
                <ElFormItem label="联系人">
                  <ElInput v-model="formModel.conerName" @input="markDirty" />
                </ElFormItem>
                <ElFormItem label="联系人电话">
                  <ElInput v-model="formModel.conerTel" @input="markDirty" />
                </ElFormItem>
                <ElFormItem label="联系人地址" class="col-span-2">
                  <ElInput v-model="formModel.conerAddr" @input="markDirty" />
                </ElFormItem>
              </div>
            </section>

            <section :id="HOMEPAGE_ANCHOR_SECTIONS[1].id" class="mb-24px">
              <div class="mb-12px flex items-center justify-between">
                <h3 class="section-title">住院信息</h3>
                <ElButton
                  v-if="canImportAll"
                  link
                  type="primary"
                  :loading="refreshing"
                  @click="importSection('inpatient')"
                >
                  导入本章
                </ElButton>
              </div>
              <div class="gap-x-16px grid grid-cols-2">
                <ElFormItem label="住院号">
                  <ElInput v-model="formModel.iptNo" @input="markDirty" />
                </ElFormItem>
                <ElFormItem label="实际住院天数">
                  <ElInputNumber
                    v-model="formModel.actIptDays"
                    :min="0"
                    class="w-full"
                    @change="markDirty"
                  />
                </ElFormItem>
                <ElFormItem label="入院时间">
                  <ElInput v-model="formModel.admTime" @input="markDirty" />
                </ElFormItem>
                <ElFormItem label="出院时间">
                  <ElInput v-model="formModel.dscgTime" @input="markDirty" />
                </ElFormItem>
                <ElFormItem label="入院科别">
                  <ElInput v-model="formModel.admCatyName" @input="markDirty" />
                </ElFormItem>
                <ElFormItem label="出院科别">
                  <ElInput v-model="formModel.dscgCatyName" @input="markDirty" />
                </ElFormItem>
                <ElFormItem label="转科摘要" class="col-span-2">
                  <ElInput
                    v-model="formModel.transferDeptSummary"
                    type="textarea"
                    :rows="2"
                    @input="markDirty"
                  />
                </ElFormItem>
              </div>
            </section>

            <section :id="HOMEPAGE_ANCHOR_SECTIONS[2].id" class="mb-24px">
              <div class="mb-12px flex items-center justify-between">
                <h3 class="section-title">诊断编码</h3>
                <ElButton
                  v-if="canImportAll"
                  link
                  type="primary"
                  :loading="refreshing"
                  @click="importSection('diagnosis')"
                >
                  导入本章
                </ElButton>
              </div>
              <HomepageDiagnosisGrid
                v-model:rows="formModel.diagnoses"
                :readonly="!canDoctorEdit"
                @change="markDirty"
              />
            </section>

            <section :id="HOMEPAGE_ANCHOR_SECTIONS[3].id" class="mb-24px">
              <div class="mb-12px flex items-center justify-between">
                <h3 class="section-title">手术操作</h3>
                <ElButton
                  v-if="canImportAll"
                  link
                  type="primary"
                  :loading="refreshing"
                  @click="importSection('procedure')"
                >
                  导入本章
                </ElButton>
              </div>
              <HomepageProcedureGrid
                v-model:rows="formModel.procedures"
                :readonly="!canDoctorEdit"
                @change="markDirty"
              />
            </section>

            <section :id="HOMEPAGE_ANCHOR_SECTIONS[4].id" class="mb-24px">
              <div class="mb-12px flex items-center justify-between">
                <h3 class="section-title">医护人员</h3>
                <ElButton
                  v-if="canImportAll"
                  link
                  type="primary"
                  :loading="refreshing"
                  @click="importSection('staff')"
                >
                  导入本章
                </ElButton>
              </div>
              <div class="gap-x-16px grid grid-cols-2">
                <ElFormItem label="科主任">
                  <ElInput v-model="formModel.deptDirectorName" @input="markDirty" />
                </ElFormItem>
                <ElFormItem label="主任医师">
                  <ElInput v-model="formModel.chfdrName" @input="markDirty" />
                </ElFormItem>
                <ElFormItem label="主治医师">
                  <ElInput v-model="formModel.atddrName" @input="markDirty" />
                </ElFormItem>
                <ElFormItem label="住院医师">
                  <ElInput v-model="formModel.iptDrName" @input="markDirty" />
                </ElFormItem>
                <ElFormItem label="责任护士">
                  <ElInput v-model="formModel.respNursName" @input="markDirty" />
                </ElFormItem>
              </div>
            </section>
          </ElForm>
        </main>
      </div>
    </div>

    <AppDialog v-model="returnDialogVisible" title="退回病案" width="480px">
      <ElInput
        v-model="returnReason"
        type="textarea"
        :rows="4"
        placeholder="请输入退回原因（必填）"
      />
      <template #footer>
        <ElButton @click="returnDialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="returning" @click="confirmReturn">确认退回</ElButton>
      </template>
    </AppDialog>
  </Page>
</template>

<style scoped>
.section-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.mrms-homepage-page :deep(.page-content) {
  display: flex;
  flex-direction: column;
  height: 100%;
}
</style>
