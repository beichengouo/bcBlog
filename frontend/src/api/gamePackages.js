import request from '@/utils/request'
export const adminGamePackages = () => request.get('/admin/game-packages')
export const portalGamePackages = () => request.get('/portal/game-packages')
export const publishGamePackage = (platform, published) => request.put(`/admin/game-packages/${platform}/published`, null, { params: { published } })
export const startGameUpload = (data) => request.post('/admin/game-packages/sessions', data)
export const gameUploadStatus = (id) => request.get(`/admin/game-packages/sessions/${id}`)
export const completeGameUpload = (id) => request.post(`/admin/game-packages/sessions/${id}/complete`, null, { timeout: 600000 })
export function uploadGameChunk(id, index, blob, onProgress, signal) {
  const data = new FormData()
  data.append('file', blob, 'chunk.bin')
  return request.post(`/admin/game-packages/sessions/${id}/chunks/${index}`, data, { timeout: 180000, signal, onUploadProgress: (event) => onProgress(event.loaded) })
}
