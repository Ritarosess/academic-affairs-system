<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="toolbar">
        <el-radio-group v-model="auditStatus" @change="handleSearch">
          <el-radio-button
            v-for="opt in COURSE_AUDIT_OPTIONS"
            :key="opt.value || 'all'"
            :value="opt.value"
          >
            {{ opt.label }}
          </el-radio-button>
        </el-radio-group>
        <el-select
          v-model="semesterId"
          placeholder="选择学期"
          clearable
          style="width: 220px"
          @change="handleSearch"
        >
          <el-option
            v-for="item in semesterOptions"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
        <el-input
          v-model="keyword"
          placeholder="课程号 / 课程名称"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
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
        <el-table-column prop="credit" label="学分" width="70" align="center" />
        <el-table-column label="性质" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="courseTypeTag(row.courseType)" size="small">
              {{ row.courseType || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="teacherName" label="任课教师" width="100" />
        <el-table-column prop="semesterName" label="学期" min-width="170" show-overflow-tooltip />
        <el-table-column label="容量/已选" width="90" align="center">
          <template #default="{ row }">{{ row.capacity }} / {{ row.selectedCount }}</template>
        </el-table-column>
        <el-table-column label="上课安排" min-width="160">
          <template #default="{ row }">{{ formatSchedule(row) }}</template>
        </el-table-column>
        <el-table-column label="审核状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="courseAuditTag(row.auditStatus)" size="small">
              {{ row.auditStatusLabel || courseAuditText(row.auditStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reviewRemark" label="审核意见" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reviewRemark || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row)">详情</el-button>
            <template v-if="row.auditStatus === 'PENDING'">
              <el-button type="success" link @click="handleApprove(row)">通过</el-button>
              <el-button type="danger" link @click="handleReject(row)">拒绝</el-button>
            </template>
          </template>
        </el-table-column>
      </PageTable>
    </el-card>

    <!-- 课程详情 / 审核 -->
    <el-dialog v-model="detailVisible" title="课程申报详情" width="640px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="课程号">{{ current.courseNo }}</el-descriptions-item>
        <el-descriptions-item label="课程名称">{{ current.name }}</el-descriptions-item>
        <el-descriptions-item label="学分">{{ current.credit }}</el-descriptions-item>
        <el-descriptions-item label="学时">{{ current.hours }}</el-descriptions-item>
        <el-descriptions-item label="课程性质">{{ current.courseType }}</el-descriptions-item>
        <el-descriptions-item label="容量">{{ current.capacity }}</el-descriptions-item>
        <el-descriptions-item label="任课教师">{{ current.teacherName }}</el-descriptions-item>
        <el-descriptions-item label="开课学期">{{ current.semesterName }}</el-descriptions-item>
        <el-descriptions-item label="上课时间" :span="2">{{ formatSchedule(current) }}</el-descriptions-item>
        <el-descriptions-item label="课程简介" :span="2">{{ current.description || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核状态">
          <el-tag :type="courseAuditTag(current.auditStatus)" size="small">
            {{ current.auditStatusLabel || courseAuditText(current.auditStatus) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="审核时间">{{ current.reviewTime || '—' }}</el-descriptions-item>
        <el-descriptions-item v-if="current.reviewRemark" label="审核意见" :span="2">
          {{ current.reviewRemark }}
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <template v-if="current.auditStatus === 'PENDING'">
          <el-button type="danger" @click="handleReject(current)">拒绝</el-button>
          <el-button type="success" @click="handleApprove(current)">通过</el-button>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageTable from '@/components/PageTable.vue'
import { approveCourse, fetchCourses, rejectCourse } from '@/api/adminCourse'
import { fetchSemesterOptions } from '@/api/meta'
import {
  COURSE_AUDIT_OPTIONS,
  courseAuditTag,
  courseAuditText,
  courseTypeTag,
  formatSchedule
} from '@/utils/dict'

const auditStatus = ref('PENDING')
const semesterId = ref(null)
const keyword = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const listData = ref({ total: 0, page: 1, size: 10, records: [] })
const loading = ref(false)
const semesterOptions = ref([])

const detailVisible = ref(false)
const current = reactive({})

async function loadList() {
  loading.value = true
  try {
    const res = await fetchCourses({
      auditStatus: auditStatus.value || undefined,
      semesterId: semesterId.value ?? undefined,
      keyword: keyword.value.trim() || undefined,
      page: page.value,
      size: size.value
    })
    listData.value = res.data
    total.value = res.data?.total || 0
  } catch (e) {
    listData.value = { total: 0, page: 1, size: size.value, records: [] }
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  try {
    const res = await fetchSemesterOptions()
    semesterOptions.value = res?.data || []
  } catch (e) {
    /* 拦截器已提示 */
  }
}

function handleSearch() {
  page.value = 1
  loadList()
}

function handleReset() {
  auditStatus.value = 'PENDING'
  semesterId.value = null
  keyword.value = ''
  page.value = 1
  loadList()
}

function openDetail(row) {
  Object.assign(current, row)
  detailVisible.value = true
}

async function handleApprove(row) {
  let remark = ''
  try {
    const { value } = await ElMessageBox.prompt('可填写审核意见（可选）', `通过课程「${row.name}」`, {
      confirmButtonText: '确定通过',
      cancelButtonText: '取消',
      inputPlaceholder: '审核意见（可选）',
      inputValidator: (v) => !v || v.length <= 255 || '审核意见不能超过 255 个字符'
    })
    remark = value || ''
  } catch (e) {
    return
  }
  try {
    await approveCourse(row.id, remark)
    ElMessage.success('已通过该课程申报')
    detailVisible.value = false
    loadList()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

async function handleReject(row) {
  let remark = ''
  try {
    const { value } = await ElMessageBox.prompt('请填写拒绝原因（教师可在课程申报中查看）', `拒绝课程「${row.name}」`, {
      confirmButtonText: '确定拒绝',
      cancelButtonText: '取消',
      inputPlaceholder: '拒绝原因（可选）',
      inputValidator: (v) => !v || v.length <= 255 || '审核意见不能超过 255 个字符'
    })
    remark = value || ''
  } catch (e) {
    return
  }
  try {
    await rejectCourse(row.id, remark)
    ElMessage.success('已拒绝该课程申报')
    detailVisible.value = false
    loadList()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

onMounted(() => {
  loadOptions()
  loadList()
})
</script>
