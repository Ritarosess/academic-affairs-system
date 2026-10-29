import request from './request'

/** 管理员：课程审核列表（可按审核状态 / 学期 / 关键字过滤） */
export function fetchCourses(params) {
  return request.get('/admin/courses', { params })
}

/** 管理员：审核通过 */
export function approveCourse(id, remark) {
  return request.put(`/admin/courses/${id}/approve`, { remark: remark || '' })
}

/** 管理员：审核拒绝 */
export function rejectCourse(id, remark) {
  return request.put(`/admin/courses/${id}/reject`, { remark: remark || '' })
}
