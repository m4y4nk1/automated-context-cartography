/**
 * API layer: Spring MVC REST controllers exposing the application's use cases.
 *
 * <p>Handles HTTP concerns only â€” routing, request validation, status codes and
 * content negotiation â€” while delegating business logic to the ingestion,
 * graph, insight and AI layers. Controllers exchange {@link com.vw.eacontext.dto
 * DTOs} rather than domain entities to keep the transport contract decoupled
 * from the internal model.</p>
 */
package com.vw.eacontext.api;

