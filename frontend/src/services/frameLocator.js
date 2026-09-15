import { getGraph } from './api'

/** Frames an entity id can be shown in. The domain frame is aggregates only, never a record. */
const CANDIDATE_FRAMES = ['application', 'process', 'infoflow']

/**
 * Whether a projected frame contains any of `ids` — as a node, as an edge, or
 * inside edge data (an information-flow frame edge is "<flowId>:produces" and
 * carries the flow/interface ids in its data). Mirrors GraphCanvas's own
 * focus-matching rules so a suggestion never names a frame that then can't
 * highlight the record.
 */
function frameContains(graph, ids) {
  const wanted = new Set(ids)
  if ((graph?.nodes ?? []).some((node) => wanted.has(node.id))) return true
  return (graph?.edges ?? []).some((edge) => {
    const data = edge.data ?? {}
    return wanted.has(edge.id)
      || wanted.has(data.flowId)
      || wanted.has(data.interfaceId)
      || (Array.isArray(data.memberIds) && data.memberIds.some((id) => wanted.has(id)))
      || ids.some((id) => edge.id.startsWith(`${id}:`))
  })
}

/**
 * The first other frame whose projection actually contains one of `ids`,
 * decided from the data itself rather than from an id's naming convention —
 * so it works for any dataset's id scheme.
 *
 * @param {string[]} ids - Entity ids a finding refers to.
 * @param {string} currentFrame - The frame that just failed to show them.
 * @returns {Promise<string | null>} A frame slug, or null if no frame contains them.
 */
export async function findFrameContaining(ids, currentFrame) {
  if (!ids?.length) return null
  for (const frame of CANDIDATE_FRAMES) {
    if (frame === currentFrame) continue
    try {
      if (frameContains(await getGraph(frame), ids)) return frame
    } catch {
      // A frame that fails to load just can't be suggested; try the next one.
    }
  }
  return null
}
