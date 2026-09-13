package com.vw.eacontext.dto;

/**
 * A generated export artifact.
 *
 * @param filename    suggested download file name
 * @param contentType the MIME type
 * @param content     the file bytes
 */
public record ExportFile(String filename, String contentType, byte[] content) {
}

