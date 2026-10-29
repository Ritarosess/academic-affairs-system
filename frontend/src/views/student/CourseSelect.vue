<template>
  <div class="page-container">
    <!-- 选课周期横幅 -->
    <el-alert
      :type="windowOpen ? 'success' : 'warning'"
      :closable="false"
      class="page-card"
      show-icon
    >
      <template #title>
        <span class="banner-title">
          {{ windowInfo.message || (windowOpen ? '当前处于选课时段' : '当前非选课时段') }}
        </span>
        <el-tag :type="windowOpen ? 'success' : 'info'" size="small" class="ml-8">
          {{ windowOpen ? '选课开放中' : '选课已关闭' }}
        </el-tag>
      </template>
      <div class="banner-sub">
        <span v-if="windowInfo.semesterName">
          学期：<strong>{{ windowInfo.semesterName }}</strong>&nbsp;&nbsp;
        </span>
        选课时间：{{ windowInfo.selectStartTime || '-' }} ~ {{ windowInfo.selectEndTime || '-' }}
      </div>
    </el-alert>

    <el-card shadow="never">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="请输入课程号 / 课程名称 / 教师"
          clearable
          style="width: 280px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select
          v-model="courseType"
          placeholder="课程性质"
          clearable
          style="width: 150px"
          @change="handleSearch"
        >
          <el-option
            v-for="item in COURSE_TYPE_OPTIONS"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-button type="primary" @click="handleSearch">
          <el-icon><Search /></el-icon>查询
        </el-button>
        <el-button @click="handleReset">
          <el-icon><Refresh /></el-icon>重置
        </el-button>
      </div>

      <PageTable
        v-model:page="page"
        v-model:size="size"
        :data="listData"
        :loading="loading"
        :total="total"
        mode="server"
        row-key="id"
        @change="loadList"
      >
        <el-table-column prop="courseNo" label="课程号" width="110" fixed="left" />
        <el-table-column prop="name" label="课程名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="credit" label="学分" width="80" align="center" />
        <el-table-column prop="hours" label="学时" width="80" align="center" />
        <el-table-column label="性质" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="courseTypeTag(row.courseType)" size="small">
              {{ row.courseType || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="teacherName" label="任课教师" width="110" />
        <el-table-column label="上课时间" width="150">
          <template #default="{ row }">
            {{ row.weekdayLabel || formatWeekday(row.weekday) }}
            第{{ row.startSection }}-{{ row.endSection }}节
          </template>
        </el-table-column>
        <el-table-column prop="location" label="上课地点" width="120" show-overflow-tooltip />
        <el-table-column prop="capacity" label="容量" width="80" align="center" />
        <el-table-column prop="selectedCount" label="已选" width="80" align="center" />
        <el-table-column label="余量" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="Number(row.remaining) > 0 ? 'success' : 'danger'" size="small">
              {{ row.remaining ?? 0 }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.selected"
              type="success"
              plain
              size="small"
              disabled
            >
              <el-icon><Check /></el-icon>已选
            </el-button>
            <el-button
              v-else
              type="primary"
              size="small"
              :loading="selectingId === row.id"
              :disabled="Number(row.remaining) <= 0 || !windowOpen"
              @click="handleSelect(row)"
            >
              选课
            </el-button>
          </template>
        </el-table-column>
      </PageTable>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageTable from '@/components/PageTable.vue'
import { fetchSelectableCourses, selectCourse } from '@/api/student'
import { fetchSelectionWindow } from '@/api/meta'
import { COURSE_TYPE_OPTIONS, courseTypeTag, formatWeekday } from '@/utils/dict'

const keyword = ref('')
const courseType = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const listData = ref({ total: 0, page: 1, size: 10, records: [] })
const loading = ref(false)
const selectingId = ref(null)

const windowOpen = ref(false)
const windowInfo = ref({})

async function loadWindow() {
  try {
    const res = await fetchSelectionWindow()
    const data = res?.data || {}
    windowInfo.value = data
    windowOpen.value = !!data.open
  } catch (e) {
    windowInfo.value = {}
    windowOpen.value = false
  }
}

async function loadList() {
  loading.value = true
  try {
    const res = await fetchSelectableCourses({
      keyword: keyword.value.trim() || undefined,
      courseType: courseType.value || undefined,
      page: page.value,
      size: size.value
    })
    const data = res?.data || {}
    listData.value = data
    total.value = data.total ?? 0
  } catch (e) {
    listData.value = { total: 0, page: 1, size: size.value, records: [] }
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  loadList()
}

function handleReset() {
  keyword.value = ''
  courseType.value = ''
  page.value = 1
  loadList()
}

async function handleSelect(row) {
  try {
    await ElMessageBox.confirm(
      `确定要选修「${row.name}」（${row.courseNo}）吗？`,
      '选课确认',
      { confirmButtonText: '确定选课', cancelButtonText: '取消', type: 'info' }
    )
  } catch (e) {
    return
  }

  selectingId.value = row.id
  try {
    const res = await selectCourse(row.id)
    ElMessage.success(res?.message || '选课成功')
    await Promise.all([loadList(), loadWindow()])
  } catch (e) {
    // 失败提示由拦截器统一处理，仅刷新列表以同步余量
    loadList()
  } finally {
    selectingId.value = null
  }
}

onMounted(() => {
  loadWindow()
  loadList()
})
</script>

<style scoped>
.banner-title {
  font-weight: 600;
}

.banner-sub {
  margin-top: 4px;
  font-size: 13px;
}

.ml-8 {
  margin-left: 8px;
}
</style>
