import axios from 'axios'

const api = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('im_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

export interface LoginResponse {
  code: number
  msg: string
  token: string
  userId: number
  username: string
  nickname: string
}

export async function login(username: string, password: string): Promise<LoginResponse> {
  const res = await api.post('/login', { username, password })
  return res.data
}

export async function register(username: string, password: string): Promise<LoginResponse> {
  const res = await api.post('/register', { username, password })
  return res.data
}

export async function getUserInfo(userId: number) {
  const res = await api.get(`/user/${userId}`)
  return res.data
}

export async function getFriendList(lastUserId = 0, limit = 50) {
  const res = await api.get('/friend/list', { params: { lastUserId, limit } })
  return res.data
}

export async function applyFriend(targetUserId: number, message = '') {
  const res = await api.post('/friend/apply', { targetUserId, message })
  return res.data
}

export async function searchUsers(q: string, limit = 20) {
  const res = await api.get('/user/search', { params: { q, limit } })
  return res.data
}

export async function getGroupMembers(groupId: number) {
  const res = await api.get(`/group/${groupId}/members`)
  return res.data
}

// Phase 2 APIs

export async function uploadFile(file: File): Promise<{ code: number; url: string; fileName: string; fileSize: number }> {
  const formData = new FormData()
  formData.append('file', file)
  const res = await api.post('/file/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
  })
  return res.data
}

export async function getFriendRequests() {
  const res = await api.get('/friend/requests')
  return res.data
}

export async function acceptFriend(fromUserId: number) {
  const res = await api.post('/friend/accept', { fromUserId })
  return res.data
}

export async function rejectFriend(fromUserId: number) {
  const res = await api.post('/friend/reject', { fromUserId })
  return res.data
}

export async function createGroup(name: string, memberIds: number[]) {
  const res = await api.post('/group/create', { name, memberIds })
  return res.data
}

export async function inviteToGroup(groupId: number, userIds: number[]) {
  const res = await api.post('/group/invite', { groupId, userIds })
  return res.data
}

export async function kickGroupMember(groupId: number, userId: number) {
  const res = await api.post('/group/kick', { groupId, userId })
  return res.data
}

export async function dissolveGroup(groupId: number) {
  const res = await api.post('/group/dissolve', { groupId })
  return res.data
}

export async function getGroupInfo(groupId: number) {
  const res = await api.get(`/group/${groupId}`)
  return res.data
}

export async function recallMessage(msgId: number, sessionId: string) {
  const res = await api.post('/message/recall', { msgId, sessionId })
  return res.data
}

export async function markMessagesRead(sessionId: string, lastReadSeq: number) {
  const res = await api.post('/message/read', { sessionId, lastReadSeq })
  return res.data
}

export async function searchMessages(q: string, sessionId?: string, limit = 20) {
  const res = await api.get('/message/search', { params: { q, sessionId, limit } })
  return res.data
}
