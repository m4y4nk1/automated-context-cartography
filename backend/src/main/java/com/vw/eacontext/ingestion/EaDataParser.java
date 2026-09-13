package com.vw.eacontext.ingestion;

import java.io.InputStream;

import com.vw.eacontext.model.CanonicalModel;

/**
 * Parses an EA data source into the {@link CanonicalModel canonical model}.
 *
 * <p>Implementations are format-specific (JSON, Excel, CSV, ...) and are the
 * only place in the application that understands the shape of external input.</p>
 */
public interface EaDataParser {

    /**
     * Reads and maps the given stream into the canonical model.
     *
     * @param in the input stream to read; the caller is responsible for closing it
     * @return the populated canonical model (never {@code null})
     * @throws com.vw.eacontext.exception.EaIngestionException if the input cannot be parsed or mapped
     */
    CanonicalModel parse(InputStream in);
}

