/**
 * Post-ingestion data-quality validation of the {@link com.vw.eacontext.model
 * canonical model}.
 *
 * <p>Runs after parsing to check required fields, unique identifiers and
 * referential integrity, collecting all findings into a
 * {@link com.vw.eacontext.validation.ValidationReport} rather than throwing, so
 * callers can surface every issue at once.</p>
 */
package com.vw.eacontext.validation;

