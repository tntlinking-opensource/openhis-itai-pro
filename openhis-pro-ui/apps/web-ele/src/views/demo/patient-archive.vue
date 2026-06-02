<script setup lang="ts">
import { useRouter } from 'vue-router';

import { User } from '@element-plus/icons-vue';

defineOptions({ name: 'PatientArchiveDemoPage' });

interface ElderContact {
  name: string;
  relation: string;
  phone: string;
}

interface ElderMedicalInfo {
  bloodType: string;
  allergyHistory: string;
  chronicDiseases: string[];
  disabilityLevel: string;
  nursingLevel: string;
  medicationNotes: string;
}

interface ElderCheckInInfo {
  department: string;
  attendingDoctor: string;
  roomNo: string;
  bedNo: string;
  admittedAt: string;
  diagnosis: string;
  carePlan: string;
  monthlyFee: number;
  paymentStatus: '已缴费' | '待缴费';
}

interface ElderProfile {
  id: string;
  residentStatus: '在院' | '离院' | '转住';
  name: string;
  gender: '女' | '男';
  age: number;
  idCardNo: string;
  birthday: string;
  phone: string;
  emergencyContact: ElderContact;
  address: string;
  tags: string[];
  medical: ElderMedicalInfo;
  checkIn: ElderCheckInInfo;
}

const elderProfiles: ElderProfile[] = [
  {
    id: 'ELD-0001',
    residentStatus: '在院',
    name: '冯艳',
    gender: '女',
    age: 82,
    idCardNo: '110101194401058817',
    birthday: '1944-01-05',
    phone: '13810248891',
    emergencyContact: {
      name: '李红',
      relation: '儿子',
      phone: '13966779821',
    },
    address: '北京市海淀区人民东路 26 号',
    tags: ['高血压', '跌倒风险', '需助行'],
    medical: {
      bloodType: 'A',
      allergyHistory: '青霉素过敏',
      chronicDiseases: ['高血压', '冠心病'],
      disabilityLevel: '轻度失能',
      nursingLevel: '二级护理',
      medicationNotes: '每日早晚监测血压，早晨服用氨氯地平。',
    },
    checkIn: {
      department: '老年内科',
      attendingDoctor: '贺军',
      roomNo: 'A-302',
      bedNo: '02',
      admittedAt: '2025-11-12',
      diagnosis: '高血压伴冠脉供血不足。',
      carePlan: '低盐饮食、步行训练、每日血压观察。',
      monthlyFee: 5200,
      paymentStatus: '已缴费',
    },
  },
  {
    id: 'ELD-0002',
    residentStatus: '转住',
    name: '陈磊',
    gender: '男',
    age: 76,
    idCardNo: '320102195010211642',
    birthday: '1950-10-21',
    phone: '13799125534',
    emergencyContact: {
      name: '陈明',
      relation: '女儿',
      phone: '13671003328',
    },
    address: '南京市鼓楼区中山北路 88 号',
    tags: ['糖尿病', '胰岛素使用', '视力下降'],
    medical: {
      bloodType: 'O',
      allergyHistory: '无明确过敏史',
      chronicDiseases: ['2 型糖尿病', '白内障'],
      disabilityLevel: '无失能',
      nursingLevel: '一级护理',
      medicationNotes: '早晚餐前注射胰岛素，每日空腹血糖监测。',
    },
    checkIn: {
      department: '老年慢病管理',
      attendingDoctor: '薛洋',
      roomNo: 'B-115',
      bedNo: '01',
      admittedAt: '2026-02-08',
      diagnosis: '2 型糖尿病伴视网膜病变风险。',
      carePlan: '控糖饮食、血糖记录、眼底随访。',
      monthlyFee: 4800,
      paymentStatus: '待缴费',
    },
  },
  {
    id: 'ELD-0003',
    residentStatus: '离院',
    name: '冯洋',
    gender: '男',
    age: 89,
    idCardNo: '420106193708093251',
    birthday: '1937-08-09',
    phone: '13566557410',
    emergencyContact: {
      name: '冯凯',
      relation: '孙子',
      phone: '18832016099',
    },
    address: '武汉市江汉区建设大道 12 号',
    tags: ['卒中后遗症', '高依赖', '吞咽风险'],
    medical: {
      bloodType: 'B',
      allergyHistory: '海鲜过敏',
      chronicDiseases: ['脑卒中后遗症', '高脂血症'],
      disabilityLevel: '中度失能',
      nursingLevel: '三级护理',
      medicationNotes: '每日 10:00 康复训练，半流质饮食，防误吸护理。',
    },
    checkIn: {
      department: '康复医学科',
      attendingDoctor: '崔勇',
      roomNo: 'C-201',
      bedNo: '03',
      admittedAt: '2024-07-03',
      diagnosis: '卒中后偏瘫，吞咽功能减退。',
      carePlan: '康复训练、压疮预防、吞咽观察。',
      monthlyFee: 6800,
      paymentStatus: '已缴费',
    },
  },
];
const router = useRouter();

// 根据在院状态返回标签颜色，便于快速区分老人去向。
function getResidentTagType(status: ElderProfile['residentStatus']) {
  if (status === '在院') {
    return 'success';
  }
  if (status === '转住') {
    return 'warning';
  }
  return 'info';
}

// 根据缴费状态返回颜色，提醒是否需要催缴。
function getPaymentTagType(status: ElderCheckInInfo['paymentStatus']) {
  return status === '已缴费' ? 'success' : 'danger';
}

// 格式化金额，统一中文金额展示格式。
function formatCurrency(amount: number) {
  return `¥${amount.toLocaleString('zh-CN')}`;
}

// 跳转到健康画像页面（/demo），并带上档案侧选中的老人信息；与独立 mini 演示工程路由保持一致。
function handleCollectData(elder: ElderProfile) {
  router.push({
    path: '/demo',
    query: {
      source: 'archive',
      elderId: elder.id,
      elderName: elder.name,
      elderGender: elder.gender,
      elderAge: `${elder.age}`,
      elderCareType: elder.medical.nursingLevel,
    },
  });
}
</script>

<template>
  <div class="archive-page p-3">
    <div class="mb-2 flex flex-wrap items-center justify-between gap-2">
      <div>
        <h2 class="font-600 text-lg">长者档案</h2>
        <p class="mt-1 text-sm text-[var(--el-text-color-secondary)]">
          养老院老人信息卡（演示数据）
        </p>
      </div>
      <el-tag type="primary" effect="light">共 {{ elderProfiles.length }} 人</el-tag>
    </div>

    <div class="archive-list grid grid-cols-2 gap-2">
      <el-card
        v-for="elder in elderProfiles"
        :key="elder.id"
        shadow="hover"
        class="elder-card border border-[var(--el-border-color-light)]"
      >
        <div class="flex items-start justify-between gap-2">
          <div class="flex items-center gap-2">
            <div class="avatar-box">
              <el-icon :size="18">
                <User />
              </el-icon>
            </div>
            <div>
              <div class="flex items-center gap-2">
                <span class="font-600 text-base">{{ elder.name }}</span>
                <el-tag size="small" effect="light" type="danger">{{ elder.gender }}</el-tag>
                <span class="text-sm text-[var(--el-text-color-secondary)]">{{ elder.age }}岁</span>
              </div>
              <div class="mt-1 text-xs text-[var(--el-text-color-secondary)]">{{ elder.id }}</div>
            </div>
          </div>
          <el-tag :type="getResidentTagType(elder.residentStatus)" effect="light">
            {{ elder.residentStatus }}
          </el-tag>
        </div>

        <div class="mt-2 border-t border-dashed border-[var(--el-border-color)] pt-2 text-sm">
          <div class="mb-1 grid grid-cols-3 gap-x-3 gap-y-1">
            <p><span class="label">科室</span>{{ elder.checkIn.department }}</p>
            <p><span class="label">床号</span>{{ elder.checkIn.bedNo }} 床</p>
            <p><span class="label">主治医生</span>{{ elder.checkIn.attendingDoctor }}</p>
          </div>
          <p class="mb-1"><span class="label">诊断</span>{{ elder.checkIn.diagnosis }}</p>
        </div>

        <div class="compact-grid mt-1 grid grid-cols-2 gap-1 text-sm">
          <div class="card-section">
            <div class="section-title">基础档案</div>
            <p><span class="label">身份证</span>{{ elder.idCardNo }}</p>
            <p><span class="label">出生日期</span>{{ elder.birthday }}</p>
            <p><span class="label">联系电话</span>{{ elder.phone }}</p>
            <p><span class="label">家庭住址</span>{{ elder.address }}</p>
          </div>

          <div class="card-section">
            <div class="section-title">联系人信息</div>
            <p><span class="label">联系人</span>{{ elder.emergencyContact.name }}</p>
            <p><span class="label">关系</span>{{ elder.emergencyContact.relation }}</p>
            <p><span class="label">联系电话</span>{{ elder.emergencyContact.phone }}</p>
            <p><span class="label">入住时间</span>{{ elder.checkIn.admittedAt }}</p>
          </div>

          <div class="card-section">
            <div class="section-title">医疗护理</div>
            <div class="grid grid-cols-2 gap-x-2 gap-y-1">
              <p><span class="label">血型</span>{{ elder.medical.bloodType }}</p>
              <p><span class="label">护理</span>{{ elder.medical.nursingLevel }}</p>
              <p><span class="label">失能</span>{{ elder.medical.disabilityLevel }}</p>
              <p><span class="label">过敏</span>{{ elder.medical.allergyHistory }}</p>
            </div>
            <p><span class="label">慢病</span>{{ elder.medical.chronicDiseases.join('、') }}</p>
            <p><span class="label">用药备注</span>{{ elder.medical.medicationNotes }}</p>
          </div>

          <div class="card-section">
            <div class="section-title">入住与费用</div>
            <p><span class="label">房间号</span>{{ elder.checkIn.roomNo }}</p>
            <p>
              <span class="label">缴费状态</span>
              <el-tag
                :type="getPaymentTagType(elder.checkIn.paymentStatus)"
                size="small"
                effect="light"
              >
                {{ elder.checkIn.paymentStatus }}
              </el-tag>
            </p>
            <p><span class="label">月费用</span>{{ formatCurrency(elder.checkIn.monthlyFee) }}</p>
            <p><span class="label">照护计划</span>{{ elder.checkIn.carePlan }}</p>
          </div>
        </div>

        <div class="mt-2 flex flex-wrap gap-1">
          <el-tag v-for="tag in elder.tags" :key="tag" effect="plain" size="small">
            {{ tag }}
          </el-tag>
        </div>

        <div
          class="mt-2 flex items-center justify-end gap-2 border-t border-[var(--el-border-color-light)] pt-1"
        >
          <el-button size="small" type="primary" @click="handleCollectData(elder)">
            数据采集
          </el-button>
          <el-button link type="primary">详情</el-button>
          <el-button link type="primary">病历</el-button>
          <el-button link type="primary">护理记录</el-button>
        </div>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.archive-page {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: auto;
}

.archive-list {
  flex: none;
  min-height: auto;
  overflow: visible;
}

.elder-card {
  border-left: 3px solid var(--el-color-primary-light-5);
}

.avatar-box {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.card-section {
  border-radius: 6px;
  padding: 6px 8px;
  border: 1px solid var(--el-border-color-lighter);
}

.section-title {
  margin-bottom: 4px;
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 600;
}

.label {
  margin-right: 6px;
  color: var(--el-text-color-secondary);
}

.card-section p,
.grid p {
  margin-bottom: 2px;
  line-height: 1.35;
}

.card-section p:last-child,
.grid p:last-child {
  margin-bottom: 0;
}

.compact-grid .card-section {
  min-height: 100%;
}
</style>
