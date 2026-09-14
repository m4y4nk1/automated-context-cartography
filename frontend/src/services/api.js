import axios from 'axios';

/**
 * Shared Axios instance pointed at the EA context backend.
 */
const api = axios.create({
  baseURL: 'http://localhost:8080/api',
});

/**
 * Logs the error with a contextual label and rethrows it so callers can handle it.
 * @param {string} context - Human-readable description of the failed operation.
 * @param {unknown} error - The error thrown by Axios.
 */
function handleError(context, error) {
  const message = error?.response?.data ?? error?.message ?? error;
  console.error(`[api] ${context} failed:`, message);
  throw error;
}

/**
 * Uploads a dataset file (JSON, XLSX, or ZIP of CSVs) and returns the validation report.
 * @param {File} file - The dataset file to upload.
 * @returns {Promise<object>} The validation report.
 */
export async function uploadDataset(file) {
  try {
    const formData = new FormData();
    formData.append('file', file);
    const { data } = await api.post('/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return data;
  } catch (error) {
    handleError('uploadDataset', error);
  }
}

/**
 * Drops empty/false filter values so unset dimensions are never sent, keeping
 * an unfiltered request byte-identical to the historical one.
 *
 * Accepts finite numbers as well as non-blank strings — added for the `depth`
 * anchor-scoping param, which is a number rather than a filter string; every
 * existing string-only filter is unaffected.
 * @param {object} [filters]
 * @returns {object} Only the populated filters, values coerced to strings.
 */
function cleanFilters(filters) {
  const params = {};
  for (const [key, value] of Object.entries(filters ?? {})) {
    if (typeof value === 'string' && value.trim() !== '') {
      params[key] = value.trim();
    } else if (typeof value === 'number' && Number.isFinite(value)) {
      params[key] = String(value);
    }
  }
  return params;
}

/**
 * Fetches the node/edge graph projection for the requested observation frame.
 * @param {string} frame - The frame slug.
 * @param {object} [filters] - Optional server-side filters (landscape, site,
 *   brand, businessArea, processLevel, activity, capability, application,
 *   lifecycle, dependencyType, criticality, viewpoint), plus `anchor` (a node
 *   id, or a business domain value for the domain frame) and `depth` (1-4) to
 *   request a scoped context diagram instead of the whole frame.
 * @returns {Promise<object>} The graph DTO.
 */
export async function getGraph(frame, filters) {
  try {
    const params = cleanFilters(filters);
    const { data } = await api.get(`/graph/${encodeURIComponent(frame)}`,
      Object.keys(params).length ? { params } : undefined);
    return data;
  } catch (error) {
    handleError('getGraph', error);
  }
}

/**
 * Fetches the distinct selectable values for every filter dimension.
 *
 * <p>Derived from the full cached model, so the available options never narrow
 * as filters are applied. All lists are empty for non-matrix datasets.
 * @returns {Promise<object>} The filter options keyed by dimension, each an
 *   array of `{ value, label }` entries.
 */
export async function getFilters() {
  try {
    const { data } = await api.get('/filters');
    return data;
  } catch (error) {
    handleError('getFilters', error);
  }
}

/**
 * Fetches the blast radius (upstream + downstream impact) for a node.
 * @param {string} id - The node/application id.
 * @returns {Promise<object>} The impact analysis result.
 */
export async function getNodeImpact(id) {
  try {
    const { data } = await api.get(`/node/${encodeURIComponent(id)}/impact`);
    return data;
  } catch (error) {
    handleError('getNodeImpact', error);
  }
}

/**
 * Fetches all insight findings for the cached model.
 * @returns {Promise<Array<object>>} The list of findings.
 */
export async function getInsights() {
  try {
    const { data } = await api.get('/insights');
    return data;
  } catch (error) {
    handleError('getInsights', error);
  }
}

/**
 * Fetches a natural-language summary of the cached model.
 * @returns {Promise<object>} The summary response.
 */
export async function getSummary() {
  try {
    const { data } = await api.get('/summary');
    return data;
  } catch (error) {
    handleError('getSummary', error);
  }
}

/**
 * Fetches the declared-vs-detected data-quality gap comparison.
 * @returns {Promise<object>} `{ declaredCount, detectedCount, newlyDetected }`.
 */
export async function getGapComparison() {
  try {
    const { data } = await api.get('/insights/gaps');
    return data;
  } catch (error) {
    handleError('getGapComparison', error);
  }
}

/** Pulls the server's suggested filename out of a Content-Disposition header. */
function filenameFrom(disposition, fallback) {
  const match = /filename="?([^";]+)"?/i.exec(disposition ?? '');
  return match ? match[1] : fallback;
}

/**
 * Exports one observation frame as an interoperable diagram file other
 * architecture tooling can open (draw.io XML, or PlantUML source — both render
 * in Confluence via their respective plugins).
 *
 * The backend has no node coordinates of its own, so the caller sends the
 * geometry it rendered; that also scopes the export, since only the nodes
 * passed here end up in the file.
 *
 * @param {string} format - 'drawio' or 'puml'.
 * @param {string} frame - The frame slug to export.
 * @param {Record<string, {x: number, y: number, w: number, h: number, color: string|null}>} placements
 * @returns {Promise<{blob: Blob, filename: string}>} The file and its server-suggested name.
 */
export async function exportDiagramFile(format, frame, placements) {
  try {
    const response = await api.post(
      `/export/diagram/${encodeURIComponent(format)}`,
      { frame, placements },
      { responseType: 'blob' },
    );
    return {
      blob: response.data,
      // CorsConfig exposes Content-Disposition, so the backend stays the one
      // place that decides what these files are called.
      filename: filenameFrom(response.headers['content-disposition'], `ea-context-diagram.${format}`),
    };
  } catch (error) {
    handleError('exportDiagramFile', error);
  }
}

/**
 * Projects the effect of retiring one application: which applications would
 * be directly affected (blast radius), which findings would newly appear,
 * and which existing findings would disappear.
 * @param {string} id - The application id being considered for removal.
 * @returns {Promise<object>} `{ removedApplicationId, upstream, downstream, newFindings, resolvedFindings }`.
 */
export async function getSimulatedRemoval(id) {
  try {
    const { data } = await api.get(`/node/${encodeURIComponent(id)}/simulate-removal`);
    return data;
  } catch (error) {
    handleError('getSimulatedRemoval', error);
  }
}

