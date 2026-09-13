/**
 * Cytoscape camera/viewport helpers shared between GraphCanvas (initial fit
 * after layout) and App.jsx (the "Reset view" control, which needs to re-fit
 * a manually panned/zoomed camera — clearing filters alone doesn't do that).
 * Kept in its own file rather than GraphCanvas.jsx so that file can stay
 * component-only (Vite Fast Refresh requirement).
 */

/** Fits the graph, then moves closer for a readable first view. */
export function fitReadable(cy, padding = 56) {
  if (!cy) return
  const visibleElements = cy.elements(':visible')
  if (visibleElements.length === 0) return

  cy.fit(visibleElements, padding)
  cy.zoom({
    level: Math.min(cy.zoom() * 2.25, cy.maxZoom()),
    renderedPosition: {
      x: cy.width() / 2,
      y: cy.height() / 2,
    },
  })
}
