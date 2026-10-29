<template>
  <div class="page-container">
    <!-- 学业红线预警提示（仅学生展示本人预警） -->
    <WarningAlert v-if="auth.isStudent" :data="myWarning" />

    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>工作台 · {{ auth.roleLabel }}</span>
          <el-button type="primary" link :loading="loading" @click="load">
            <el-icon><Refresh /></el-icon>刷新
          </el-button>
        </div>
      </template>

      <el-skeleton v-if="loading && !cards.length" :rows="3" animated />

      <el-empty v-else-if="!cards.length" description="暂无统计数据" />

      <el-row v-else :gutter="16" class="stat-cards">
        <el-col
          v-for="card in cards"
          :key="card.label"
          :xs="12"
          :sm="8"
          :md="6"
        >
          <el-card shadow="hover" class="stat-card">
            <div class="stat-body">
              <el-icon class="stat-icon" :style="{ color: card.color }">
                <component :is="card.icon" />
              </el-icon>
              <div>
                <div class="stat-value">{{ card.value }}</div>
                <div class="stat-label">{{ card.label }}</div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <span>快捷入口</span>
      </template>
      <el-space wrap>
        <el-button
          v-for="link in quickLinks"
          :key="link.path"
          type="primary"
          plain
          @click="router.push(link.path)"
        >
          <el-icon><component :is="link.icon" /></el-icon>
          {{ link.title }}
        </el-button>
      </el-space>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchDashboardSummary } from '@/api/dashboard'
import { fetchMyWarning } from '@/api/warning'
import { useAuthStore } from '@/stores/auth'
import WarningAlert from '@/components/WarningAlert.vue'

const auth = useAuthStore()
const router = useRouter()

const loading = ref(false)
const summary = ref({})
const myWarning = ref(null)

function toText(value, suffix = '') {
  if (value === null || value === undefined || value === '') return '-'
  return `${value}${suffix}`
}

const cards = computed(() => {
  const s = summary.value || {}
  if (auth.isAdmin) {
    return [
      { label: '学生总数', value: toText(s.studentCount), icon: 'User', color: '#409eff' },
      { label: '教师总数', value: toText(s.teacherCount), icon: 'Avatar', color: '#67c23a' },
      { label: '课程总数', value: toText(s.courseCount), icon: 'Notebook', color: '#e6a23c' },
      { label: '选课总数', value: toText(s.selectionCount), icon: 'ShoppingCart', color: '#f56c6c' },
      {
        label: '学业预警人数',
        value: toText(s.warningFlagged),
        icon: 'WarnTriangleFilled',
        color: Number(s.warningFlagged) > 0 ? '#f56c6c' : '#67c23a'
      },
      { label: '当前学期', value: toText(s.currentSemester), icon: 'Calendar', color: '#909399' },
      {
        label: '选课周期',
        value: s.selectionOpen ? '开放中' : '已关闭',
        icon: 'Timer',
        color: s.selectionOpen ? '#67c23a' : '#909399'
      }
    ]
  }
  if (auth.isTeacher) {
    return [
      { label: '我的课程', value: toText(s.courseCount), icon: 'Notebook', color: '#409eff' },
      { label: '选课学生', value: toText(s.studentCount), icon: 'User', color: '#67c23a' },
      { label: '已录成绩', value: toText(s.gradedCount), icon: 'DataLine', color: '#e6a23c' },
      { label: '未录成绩', value: toText(s.ungradedCount), icon: 'Warning', color: '#f56c6c' },
      {
        label: '预警学生',
        value: toText(s.warningStudentCount),
        icon: 'WarnTriangleFilled',
        color: Number(s.warningStudentCount) > 0 ? '#f56c6c' : '#67c23a'
      }
    ]
  }
  return [
    { label: '已选课程', value: toText(s.selectedCount), icon: 'Notebook', color: '#409eff' },
    { label: '已获学分', value: toText(s.totalCredit), icon: 'Medal', color: '#67c23a' },
    { label: '已出成绩', value: toText(s.scoredCount), icon: 'DataLine', color: '#e6a23c' },
    {
      label: '选课周期',
      value: s.selectionOpen ? '开放中' : '已关闭',
      icon: 'Timer',
      color: s.selectionOpen ? '#67c23a' : '#909399'
    }
  ]
})

const ALL_LINKS = [
  { title: '用户管理', path: '/admin/users', icon: 'UserFilled', roles: ['ADMIN'] },
  { title: '课程审核', path: '/admin/courses', icon: 'Notebook', roles: ['ADMIN'] },
  { title: '学业预警', path: '/admin/warnings', icon: 'WarnTriangleFilled', roles: ['ADMIN'] },
  { title: '院系管理', path: '/admin/org', icon: 'OfficeBuilding', roles: ['ADMIN'] },
  { title: '学期管理', path: '/admin/semesters', icon: 'Calendar', roles: ['ADMIN'] },
  { title: '课程申报', path: '/teacher/course-applies', icon: 'EditPen', roles: ['TEACHER'] },
  { title: '我的课程', path: '/teacher/courses', icon: 'Notebook', roles: ['TEACHER'] },
  { title: '选课大厅', path: '/student/courses', icon: 'ShoppingCart', roles: ['STUDENT'] },
  { title: '我的课表', path: '/student/my-courses', icon: 'Calendar', roles: ['STUDENT'] },
  { title: '我的成绩', path: '/student/scores', icon: 'DataLine', roles: ['STUDENT'] },
  { title: '个人中心', path: '/profile', icon: 'User', roles: ['ADMIN', 'TEACHER', 'STUDENT'] }
]

const quickLinks = computed(() =>
  ALL_LINKS.filter((item) => item.roles.includes(auth.role))
)

async function loadMyWarning() {
  if (!auth.isStudent) {
    myWarning.value = null
    return
  }
  try {
    const res = await fetchMyWarning()
    myWarning.value = res?.data || null
  } catch (e) {
    myWarning.value = null
  }
}

async function load() {
  loading.value = true
  try {
    const res = await fetchDashboardSummary()
    summary.value = res?.data || {}
  } catch (e) {
    summary.value = {}
  } finally {
    loading.value = false
  }
  await loadMyWarning()
}

onMounted(load)
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.stat-card {
  margin-bottom: 16px;
}

.stat-body {
  display: flex;
  align-items: center;
  gap: 14px;
}

.stat-icon {
  font-size: 32px;
}

.stat-value {
  font-size: 22px;
  font-weight: 600;
  line-height: 1.2;
  word-break: break-all;
}

.stat-label {
  margin-top: 2px;
  font-size: 13px;
  color: #909399;
}
</style>
