import MarkdownIt from 'markdown-it'

export const RELEASE_NOTES_MAX_LENGTH = 4000
const MAX_FILE_SIZE = 64 * 1024
// Raw HTML stays escaped; the parser also rejects unsafe link protocols.
const markdown = new MarkdownIt({ html: false, breaks: true })
markdown.renderer.rules.link_open = (tokens, index, options, env, renderer) => {
  tokens[index].attrSet('target', '_blank')
  tokens[index].attrSet('rel', 'noopener noreferrer')
  return renderer.renderToken(tokens, index, options)
}

export function renderReleaseNotes(text) {
  return markdown.render(String(text || ''))
}

export async function readReleaseNotesFile(file) {
  if (!file || !/\.(md|markdown)$/i.test(file.name)) throw new Error('请选择 .md 或 .markdown 文件')
  if (!file.size || file.size > MAX_FILE_SIZE) throw new Error('Markdown 文件不能为空且不能超过64 KiB')
  let text
  try {
    text = new TextDecoder('utf-8', { fatal: true }).decode(await file.arrayBuffer()).replace(/\r\n?/g, '\n')
  } catch {
    throw new Error('文件读取失败，请确认文件使用 UTF-8 编码后重试')
  }
  if (!text.trim()) throw new Error('Markdown 文件内容不能为空')
  if (text.includes('\0')) throw new Error('请选择有效的 Markdown 文本文件')
  if (text.length > RELEASE_NOTES_MAX_LENGTH) throw new Error('更新说明最多4000字，请精简后重新导入')
  return text
}
