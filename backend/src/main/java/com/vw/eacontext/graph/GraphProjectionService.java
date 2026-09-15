package com.vw.eacontext.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.jgrapht.Graph;
import org.springframework.stereotype.Service;

import com.vw.eacontext.dto.GraphDto;
import com.vw.eacontext.dto.GraphEdge;
import com.vw.eacontext.dto.GraphNode;
import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.ApplicationOwnership;
import com.vw.eacontext.model.BusinessProcess;
import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.model.DataQualityGap;
import com.vw.eacontext.model.InformationObject;
import com.vw.eacontext.model.Interface;
import com.vw.eacontext.model.ProcessMapping;
import com.vw.eacontext.model.Relationship;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Projects a {@link CanonicalModel} into the four observation frames, each
 * returned as a frame-agnostic {@link GraphDto} (nodes + edges) for the frontend.
 *
 * <ul>
 *   <li>{@link #applicationView(CanonicalModel)} — applications as nodes, with
 *       relationships, interfaces and information flows each becoming their own
 *       edge (see {@link ApplicationEdgeAssembler}).</li>
 *   <li>{@link #businessProcessView(CanonicalModel)} — processes and the
 *       applications that support them, via {@link ProcessMapping}.</li>
 *   <li>{@link #domainView(CanonicalModel)} — applications aggregated by
 *       {@link Application#businessDomain()}, with every cross-domain
 *       relationship, interface and flow collapsed into domain &rarr; domain edges.</li>
 *   <li>{@link #informationFlowView(CanonicalModel)} — information objects as
 *       intermediary nodes: source &rarr; object (produces) &rarr; target (consumes).</li>
 * </ul>
 *
 * <p>Every frame draws its edges from the providing/originating side to the
 * dependent/consuming side, so an arrowhead means the same thing in all four.</p>
 *
 * <p>Application ids that records reference but which have no application row of
 * their own are rendered as placeholder nodes ({@code applicationGhost}) in every
 * frame that references them, rather than being dropped along with their edges.
 * They exist only in these projections — never in the canonical model or the
 * analytical graph — so no detector can mistake one for a real application.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphProjectionService {

    /** Domain-frame node id standing for every application with a blank business domain. */
    public static final String UNASSIGNED_DOMAIN = "__UNASSIGNED__";

    private static final String TYPE_APPLICATION = "application";
    private static final String TYPE_APPLICATION_GHOST = "applicationGhost";
    private static final String TYPE_DOMAIN = "domain";
    private static final String TYPE_PROCESS = "process";
    private static final String TYPE_PROCESS_GHOST = "processGhost";
    private static final String TYPE_INFORMATION_OBJECT = "informationObject";

    private final GraphBuilderService graphBuilderService;
    private final GhostReferenceResolver ghostReferenceResolver;
    private final ApplicationEdgeAssembler applicationEdgeAssembler;

    /** Application frame: applications as nodes; relationships, interfaces and flows as edges. */
    public GraphDto applicationView(CanonicalModel model) {
        return applicationView(model, graphBuilderService.build(model));
    }

    /**
     * Application frame reusing a pre-built graph.
     *
     * <p>The supplied graph provides the application vertices; its edges are
     * deliberately <em>not</em> used. Relationships alone describe only part of
     * the landscape, so edges come from {@link ApplicationEdgeAssembler}, which
     * turns every relationship, interface and information flow into its own
     * edge. The analytical graph stays relationship-only so hub, cycle and
     * impact results are unaffected.</p>
     */
    public GraphDto applicationView(CanonicalModel model, Graph<Application, RelationshipEdge> graph) {
        Map<String, ApplicationOwnership> ownershipByAppId = indexOwnership(model);
        Map<String, List<DataQualityGap>> gapsByAppId = indexGaps(model);
        GhostReferences ghosts = ghostReferenceResolver.resolve(model);

        List<GraphNode> nodes = new ArrayList<>();
        Set<String> nodeIds = new LinkedHashSet<>();
        for (Application app : graph.vertexSet()) {
            nodes.add(applicationNode(app, ownershipByAppId, gapsByAppId));
            nodeIds.add(app.id());
        }
        // Offer the ghosts as candidate endpoints, then keep only the ones an
        // edge actually landed on — a reference that exists solely in the
        // process sheet has no place in the application frame.
        Set<String> candidateIds = new LinkedHashSet<>(nodeIds);
        candidateIds.addAll(ghosts.ids());

        List<GraphEdge> edges = applicationEdgeAssembler.assemble(model, candidateIds);
        for (String ghostId : ghosts.ids()) {
            if (nodeIds.contains(ghostId) || !isEndpoint(edges, ghostId)) {
                continue;
            }
            nodes.add(ghostNode(ghostId, ghosts));
            nodeIds.add(ghostId);
        }
        return new GraphDto(TYPE_APPLICATION, nodes, edges);
    }

    /** Business-process frame: processes and their supporting applications via {@link ProcessMapping}. */
    public GraphDto businessProcessView(CanonicalModel model) {
        Map<String, Application> appById = indexApplications(model);
        Map<String, ApplicationOwnership> ownershipByAppId = indexOwnership(model);
        Map<String, List<DataQualityGap>> gapsByAppId = indexGaps(model);
        Map<String, BusinessProcess> processById = new LinkedHashMap<>();
        for (BusinessProcess process : model.businessProcesses()) {
            if (!isBlank(process.id())) {
                processById.put(process.id(), process);
            }
        }

        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();
        Set<String> addedApps = new HashSet<>();
        Set<String> addedProcesses = new HashSet<>();
        // Broken process references, grouped so a repeatedly-broken id still
        // gets one placeholder node listing every mapping that pointed at it.
        Map<String, Set<String>> unresolvedProcessReferences = new LinkedHashMap<>();

        GhostReferences ghosts = ghostReferenceResolver.resolve(model);

        for (ProcessMapping mapping : model.processMappings()) {
            String processId = mapping.businessProcessId();
            BusinessProcess process = processById.get(processId);
            if (process == null && isBlank(processId)) {
                continue; // nothing at all to anchor the edge to
            }
            // An unresolved process still gets a placeholder rather than
            // dropping the whole row — otherwise a perfectly valid application
            // on that row would silently vanish along with the broken process
            // reference. Recorded here rather than via GhostReferenceResolver,
            // which only tracks unresolved application ids.
            if (process == null) {
                unresolvedProcessReferences.computeIfAbsent(processId, id -> new LinkedHashSet<>())
                        .add(mapping.id());
            }

            Application app = appById.get(mapping.supportingApplicationId());
            String appId = app == null ? mapping.supportingApplicationId() : app.id();
            // A ghost supporting application still gets a placeholder node.
            // Dropping the row instead would erase the whole process whenever
            // every one of its mappings is broken — the process would vanish
            // from the diagram with nothing to show it ever existed.
            if (app == null && !ghosts.isGhost(appId)) {
                continue;
            }
            if (process != null && addedProcesses.add(process.id())) {
                Map<String, Object> data = GraphNode.attrs();
                data.put("processDomain", process.processDomain());
                nodes.add(new GraphNode(process.id(), process.name(), TYPE_PROCESS, data));
            }
            if (addedApps.add(appId)) {
                nodes.add(app == null
                        ? ghostNode(appId, ghosts)
                        : applicationNode(app, ownershipByAppId, gapsByAppId));
            }
            Map<String, Object> data = GraphNode.attrs();
            data.put("roleOfApplication",
                    mapping.roleOfApplication() == null ? null : mapping.roleOfApplication().name());
            data.put("processCriticality",
                    mapping.processCriticality() == null ? null : mapping.processCriticality().name());
            edges.add(new GraphEdge(
                    mapping.id() == null ? processId + "->" + appId : mapping.id(),
                    processId,
                    appId,
                    mapping.roleOfApplication() == null
                            ? "supports" : mapping.roleOfApplication().name().toLowerCase(Locale.ROOT),
                    "processMapping",
                    data));
        }
        unresolvedProcessReferences.forEach((processId, mappingIds) ->
                nodes.add(processGhostNode(processId, mappingIds)));
        return new GraphDto(TYPE_PROCESS, nodes, edges);
    }

    /**
     * Domain frame: applications aggregated by business domain, with every
     * cross-domain coupling — relationship, interface <em>and</em> information
     * flow — collapsed into one weighted edge per ordered pair of domains.
     *
     * <p>Counting relationships alone would hide couplings that only exist as an
     * interface or a flow, the same reason the application frame draws all three
     * (see {@link ApplicationEdgeAssembler}). Edges also follow that frame's
     * direction convention — from the providing/originating side to the
     * dependent/consuming side — so a relationship (recorded dependent &rarr;
     * provider) is flipped, while interfaces and flows are taken as recorded.</p>
     *
     * <p>Records are read straight off the model rather than the analytical
     * graph, which drops any relationship with an unresolved endpoint. An
     * unresolved (ghost) endpoint gets its own {@code applicationGhost} node; it
     * is never counted into a domain's {@code applicationCount}, nor folded into
     * {@link #UNASSIGNED_DOMAIN}, which means "a real application with a blank
     * domain field" — not "an id that doesn't resolve to any application."</p>
     */
    public GraphDto domainView(CanonicalModel model) {
        Map<String, Integer> appCountByDomain = new LinkedHashMap<>();
        Map<String, String> domainKeyByAppId = new HashMap<>();
        for (Application app : model.applications()) {
            if (isBlank(app.id())) {
                continue;
            }
            String domainKey = isBlank(app.businessDomain()) ? UNASSIGNED_DOMAIN : app.businessDomain();
            domainKeyByAppId.put(app.id(), domainKey);
            appCountByDomain.merge(domainKey, 1, Integer::sum);
        }

        List<GraphNode> nodes = new ArrayList<>();
        appCountByDomain.forEach((domainKey, count) -> {
            String name = UNASSIGNED_DOMAIN.equals(domainKey) ? "Unassigned" : domainKey;
            Map<String, Object> data = GraphNode.attrs();
            data.put("applicationCount", count);
            nodes.add(new GraphNode(domainKey, name + " (" + count + ")", TYPE_DOMAIN, data));
        });

        GhostReferences ghosts = ghostReferenceResolver.resolve(model);
        Map<String, DomainCoupling> couplings = new LinkedHashMap<>();
        for (Relationship relationship : model.relationships()) {
            couple(couplings, domainKeyByAppId, ghosts, relationship.targetApplicationId(),
                    relationship.sourceApplicationId(), DomainCoupling::addRelationship);
        }
        for (Interface iface : model.interfaces()) {
            couple(couplings, domainKeyByAppId, ghosts, iface.providerApplicationId(),
                    iface.consumerApplicationId(), DomainCoupling::addInterface);
        }
        for (InformationObject info : model.informationObjects()) {
            couple(couplings, domainKeyByAppId, ghosts, info.sourceApplicationId(),
                    info.targetApplicationId(), DomainCoupling::addFlow);
        }

        Set<String> referencedGhostIds = new LinkedHashSet<>();
        List<GraphEdge> edges = new ArrayList<>();
        for (DomainCoupling coupling : couplings.values()) {
            for (String endpoint : List.of(coupling.from, coupling.to)) {
                if (ghosts.isGhost(endpoint)) {
                    referencedGhostIds.add(endpoint);
                }
            }
            Map<String, Object> data = GraphNode.attrs();
            data.put("relationshipCount", coupling.relationships);
            data.put("interfaceCount", coupling.interfaces);
            data.put("flowCount", coupling.flows);
            data.put("couplingCount", coupling.relationships + coupling.interfaces + coupling.flows);
            edges.add(new GraphEdge("DOM_" + coupling.from + ">" + coupling.to,
                    coupling.from, coupling.to, coupling.label(), "domainFlow", data));
        }
        for (String ghostId : referencedGhostIds) {
            nodes.add(ghostNode(ghostId, ghosts));
        }
        return new GraphDto(TYPE_DOMAIN, nodes, edges);
    }

    /**
     * Counts one record's coupling between the domains of its two endpoints.
     * Skipped when the pair is intra-domain, when an endpoint neither belongs
     * to a domain nor is a ghost (e.g. blank), or when both ends are ghosts —
     * there's no domain on either side to attach it to.
     */
    private static void couple(Map<String, DomainCoupling> couplings, Map<String, String> domainKeyByAppId,
                               GhostReferences ghosts, String fromAppId, String toAppId,
                               Consumer<DomainCoupling> count) {
        String from = domainOrGhost(fromAppId, domainKeyByAppId, ghosts);
        String to = domainOrGhost(toAppId, domainKeyByAppId, ghosts);
        if (from == null || to == null || from.equals(to)
                || (ghosts.isGhost(fromAppId) && ghosts.isGhost(toAppId))) {
            return;
        }
        count.accept(couplings.computeIfAbsent(from + ">" + to, key -> new DomainCoupling(from, to)));
    }

    private static String domainOrGhost(String appId, Map<String, String> domainKeyByAppId, GhostReferences ghosts) {
        String domain = domainKeyByAppId.get(appId);
        if (domain != null) {
            return domain;
        }
        return ghosts.isGhost(appId) ? appId : null;
    }

    /** Relationship / interface / flow counts for one ordered pair of domain-frame endpoints. */
    private static final class DomainCoupling {
        private final String from;
        private final String to;
        private int relationships;
        private int interfaces;
        private int flows;

        private DomainCoupling(String from, String to) {
            this.from = from;
            this.to = to;
        }

        private void addRelationship() {
            relationships++;
        }

        private void addInterface() {
            interfaces++;
        }

        private void addFlow() {
            flows++;
        }

        /** e.g. {@code "2 relationships · 1 interface"} — zero counts are left out. */
        private String label() {
            List<String> parts = new ArrayList<>();
            if (relationships > 0) {
                parts.add(relationships + (relationships == 1 ? " relationship" : " relationships"));
            }
            if (interfaces > 0) {
                parts.add(interfaces + (interfaces == 1 ? " interface" : " interfaces"));
            }
            if (flows > 0) {
                parts.add(flows + (flows == 1 ? " flow" : " flows"));
            }
            return String.join(" · ", parts);
        }
    }

    /** Information-flow frame: source application -> information object -> target application. */
    public GraphDto informationFlowView(CanonicalModel model) {
        Map<String, Application> appById = indexApplications(model);
        Map<String, ApplicationOwnership> ownershipByAppId = indexOwnership(model);
        Map<String, List<DataQualityGap>> gapsByAppId = indexGaps(model);
        Map<String, Interface> interfaceById = new HashMap<>();
        for (Interface iface : model.interfaces()) {
            if (!isBlank(iface.id())) {
                interfaceById.put(iface.id(), iface);
            }
        }

        Map<String, GraphNode> nodes = new LinkedHashMap<>();
        List<GraphEdge> edges = new ArrayList<>();

        GhostReferences ghosts = ghostReferenceResolver.resolve(model);

        // One information object usually spans several flow rows. If they disagree
        // on its classification, the node must carry the most sensitive one — not
        // whichever row happened to come first.
        Map<String, InformationObject> mostSensitiveByObject = new HashMap<>();
        for (InformationObject info : model.informationObjects()) {
            if (info.classification() != null) {
                mostSensitiveByObject.merge(info.informationObject(), info, (kept, candidate) ->
                        candidate.classification().compareTo(kept.classification()) > 0 ? candidate : kept);
            }
        }

        for (InformationObject info : model.informationObjects()) {
            Application source = appById.get(info.sourceApplicationId());
            Application target = appById.get(info.targetApplicationId());
            String sourceId = source == null ? info.sourceApplicationId() : source.id();
            String targetId = target == null ? info.targetApplicationId() : target.id();
            // As in the process frame, a ghost endpoint becomes a placeholder so
            // the flow stays on the diagram instead of disappearing.
            if ((source == null && !ghosts.isGhost(sourceId))
                    || (target == null && !ghosts.isGhost(targetId))) {
                continue;
            }
            nodes.putIfAbsent(sourceId, source == null
                    ? ghostNode(sourceId, ghosts) : applicationNode(source, ownershipByAppId, gapsByAppId));
            nodes.putIfAbsent(targetId, target == null
                    ? ghostNode(targetId, ghosts) : applicationNode(target, ownershipByAppId, gapsByAppId));

            String ioId = "IO:" + info.informationObject();
            nodes.computeIfAbsent(ioId, id -> {
                InformationObject mostSensitive = mostSensitiveByObject.get(info.informationObject());
                var classification = mostSensitive == null ? null : mostSensitive.classification();
                Map<String, Object> data = GraphNode.attrs();
                data.put("classification", classification == null ? null : classification.name());
                data.put("sensitive", classification != null && classification.isSensitive());
                return new GraphNode(id, info.informationObject(), TYPE_INFORMATION_OBJECT, data);
            });

            Interface iface = interfaceById.get(info.interfaceId());
            Map<String, Object> data = GraphNode.attrs();
            data.put("flowId", info.id());
            data.put("operation", info.operation() == null ? null : info.operation().name());
            data.put("interfaceId", info.interfaceId());
            data.put("interfaceStatus",
                    iface == null || iface.interfaceStatus() == null ? null : iface.interfaceStatus().name());
            edges.add(new GraphEdge(info.id() + ":produces", sourceId, ioId, "produces", "produces", data));
            String consumeLabel = info.operation() == null ? "consumes" : info.operation().name().toLowerCase(Locale.ROOT);
            edges.add(new GraphEdge(info.id() + ":consumes", ioId, targetId, consumeLabel, "consumes", data));
        }
        return new GraphDto(TYPE_INFORMATION_OBJECT, new ArrayList<>(nodes.values()), edges);
    }

    // --- helpers --------------------------------------------------------------

    private GraphNode applicationNode(Application app, Map<String, ApplicationOwnership> ownershipByAppId,
                                      Map<String, List<DataQualityGap>> gapsByAppId) {
        Map<String, Object> data = GraphNode.attrs();
        data.put("description", app.description());
        data.put("businessDomain", app.businessDomain());
        data.put("businessCriticality",
                app.businessCriticality() == null ? null : app.businessCriticality().name());
        data.put("lifecycleStatus", app.lifecycleStatus() == null ? null : app.lifecycleStatus().name());
        data.put("lifecycleStartDate",
                app.lifecycleStartDate() == null ? null : app.lifecycleStartDate().toString());
        data.put("lifecycleEndDate", app.lifecycleEndDate() == null ? null : app.lifecycleEndDate().toString());
        data.put("hosting", app.hosting() == null ? null : app.hosting().name());
        data.put("vendorType", app.vendorType() == null ? null : app.vendorType().name());
        data.put("ownerEmployeeId", app.ownerEmployeeId());
        data.put("costCenter", app.costCenter());

        ApplicationOwnership ownership = ownershipByAppId.get(app.id());
        data.put("hasOwnershipRecord", ownership != null);
        data.put("applicationOwner", ownership == null ? null : ownership.applicationOwner());
        // The ownership record's own employee id — distinct from
        // Application.ownerEmployeeId above, which is the app record's own
        // (separately-sourced) claim. The dataset intends these to join to
        // each other; keeping both visible rather than silently preferring
        // one lets a mismatch between them be seen instead of hidden.
        data.put("ownershipEmployeeId", ownership == null ? null : ownership.ownerEmployeeId());
        data.put("systemCustodian", ownership == null ? null : ownership.systemCustodian());
        data.put("businessOwner", ownership == null ? null : ownership.businessOwner());
        data.put("supportGroup", ownership == null ? null : ownership.supportGroup());
        data.put("department", ownership == null ? null : ownership.department());

        data.put("declaredGaps", gapsByAppId.getOrDefault(app.id(), List.of()).stream()
                .map(GraphProjectionService::declaredGapAttrs)
                .toList());

        data.putAll(app.attributes());
        return new GraphNode(app.id(), app.name(), TYPE_APPLICATION, data);
    }

    /** One {@link DataQualityGap} reduced to the fields worth showing on an application popup. */
    private static Map<String, Object> declaredGapAttrs(DataQualityGap gap) {
        Map<String, Object> attrs = GraphNode.attrs();
        attrs.put("gapType", gap.gapType());
        attrs.put("description", gap.description());
        attrs.put("severity", gap.severity() == null ? null : gap.severity().name());
        return attrs;
    }

    /**
     * A placeholder for an application id that records point at but which has no
     * row of its own. Labelled by its id — that is the only fact the dataset
     * actually provides, and it is what an architect needs in order to chase the
     * broken reference back to its source system.
     */
    private GraphNode ghostNode(String id, GhostReferences ghosts) {
        Map<String, Object> data = GraphNode.attrs();
        data.put("unresolved", true);
        data.put("referencedBy", List.copyOf(ghosts.sources(id)));
        return new GraphNode(id, id, TYPE_APPLICATION_GHOST, data);
    }

    /**
     * A placeholder for a business-process id that a mapping row points at but
     * which has no row of its own. A different kind of "ghost" than
     * {@link #ghostNode} — {@link GhostReferenceResolver} only tracks unresolved
     * <em>application</em> ids, so this one is tracked locally within
     * {@link #businessProcessView(CanonicalModel)} instead.
     */
    private GraphNode processGhostNode(String id, Set<String> referencingMappingIds) {
        Map<String, Object> data = GraphNode.attrs();
        data.put("unresolved", true);
        data.put("referencedBy", List.copyOf(referencingMappingIds));
        return new GraphNode(id, id, TYPE_PROCESS_GHOST, data);
    }

    private static boolean isEndpoint(List<GraphEdge> edges, String nodeId) {
        return edges.stream()
                .anyMatch(edge -> nodeId.equals(edge.source()) || nodeId.equals(edge.target()));
    }

    private Map<String, Application> indexApplications(CanonicalModel model) {
        Map<String, Application> byId = new HashMap<>();
        for (Application app : model.applications()) {
            if (!isBlank(app.id())) {
                byId.putIfAbsent(app.id(), app);
            }
        }
        return byId;
    }

    private Map<String, ApplicationOwnership> indexOwnership(CanonicalModel model) {
        Map<String, ApplicationOwnership> byAppId = new HashMap<>();
        for (ApplicationOwnership ownership : model.applicationOwnerships()) {
            if (!isBlank(ownership.applicationId())) {
                byAppId.putIfAbsent(ownership.applicationId(), ownership);
            }
        }
        return byAppId;
    }

    /** Declared {@code KnownDataQualityGaps} rows, grouped by the application they relate to. */
    private Map<String, List<DataQualityGap>> indexGaps(CanonicalModel model) {
        Map<String, List<DataQualityGap>> byAppId = new LinkedHashMap<>();
        for (DataQualityGap gap : model.dataQualityGaps()) {
            if (!isBlank(gap.relatedApplicationId())) {
                byAppId.computeIfAbsent(gap.relatedApplicationId(), id -> new ArrayList<>()).add(gap);
            }
        }
        return byAppId;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
