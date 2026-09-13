/**
 * Ingestion layer: parsers that read external EA data sources and map them
 * into the canonical {@link com.vw.eacontext.model domain model}.
 *
 * <p>Holds format-specific readers (e.g. Excel/XLSX via Apache POI, CSV via
 * Commons CSV, JSON via Jackson) and the normalization logic that turns raw,
 * heterogeneous input into consistent domain entities. This is the only layer
 * that should be aware of source file formats.</p>
 */
package com.vw.eacontext.ingestion;

