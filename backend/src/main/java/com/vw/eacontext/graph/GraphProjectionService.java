package com.vw.eacontext.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jgrapht.Graph;
import org.springframework.stereotype.Service;

import com.vw.eacontext.dto.GraphDto;
import com.vw.eacontext.dto.GraphEdge;
import com.vw.eacontext.dto.GraphNode;
import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.ApplicationOwnership;
import com.vw.eacontext.model.BusinessProcess;
import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.model.InformationObject;
import com.vw.eacontext.model.Interface;
import com.vw.eacontext.model.ProcessMapping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Projects a {@link CanonicalModel} into the four observation frames, each
 * returned as a frame-agnostic {@link GraphDto} (nodes + edges) for the frontend.
 *
 * <ul>
 *   <li>{@link #applicationView(CanonicalModel)} — applications as nodes,
 *       relationships as source &rarr; target edges.</li>
 *   <li>{@link #businessProcessView(CanonicalModel)} — processes and the
 *       applications that support them, via {@link ProcessMapping}.</li>
 *   <li>{@link #domainView(CanonicalModel)} — applications aggregated by
 *       {@link Application#businessDomain()}, with cross-domain relationships
 *       collapsed into domain &rarr; domain edges.</li>
 *   <li>{@link #informationFlowView(CanonicalModel)} — information objects as
 *       intermediary nodes: source &rarr; object (produces) &rarr; target (consumes).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphProjectionService {

    private static final String TYPE_APPLICATION = "application";
    private static final String TYPE_DOMAIN = "domain";
    private static final String TYPE_PROCESS = "process";
    private static final String TYPE_INFORMATION_OBJECT = "informationObject";
    private static final String UNASSIGNED_DOMAIN = "__UNASSIGNED__";

    private final GraphBuilderService graphBuilderService;

    /** Application frame: applications as nodes, relationships as directed edges. */
    public GraphDto applicationView(CanonicalModel model) {
        return applicationView(model, graphBuilderService.build(model));
    }

    /** Application frame reusing a pre-built graph. */
    public GraphDto applicationView(CanonicalModel model, Graph<Application, RelationshipEdge> graph) {
        Map<String, ApplicationOwnership> ownershipByAppId = indexOwnership(model);
        List<GraphNode> nodes = new ArrayList<>();
        for (Application app : graph.vertexSet()) {
            nodes.add(applicationNode(app, ownershipByAppId));
        }

        List<GraphEdge> edges = new ArrayList<>();
        for (RelationshipEdge edge : graph.edgeSet()) {
            Application source = graph.getEdgeSource(edge);
            Application target = graph.getEdgeTarget(edge);
            Map<String, Object> data = GraphNode.attrs();
            data.put("dependencyCriticality",
                    edge.getDependencyCriticality() == null ? null : edge.getDependencyCriticality().name());
            String typeName = edge.getRelationshipType() == null ? null : edge.getRelationshipType().name();
            edges.add(new GraphEdge(edge.getRelationshipId(), source.id(), target.id(), typeName, typeName, data));
        }
        return new GraphDto(TYPE_APPLICATION, nodes, edges);
    }

    /** Business-process frame: processes and their supporting applications via {@link ProcessMapping}. */
    public GraphDto businessProcessView(CanonicalModel model) {
        Map<String, Application> appById = indexApplications(model);
        Map<String, ApplicationOwnership> ownershipByAppId = indexOwnership(model);
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

        for (ProcessMapping mapping : model.processMappings()) {
            BusinessProcess process = processById.get(mapping.businessProcessId());
            Application app = appById.get(mapping.supportingApplicationId());
            if (process == null || app == null) {
                continue; // unresolved process, or a ghost application reference
            }
            if (addedProcesses.add(process.id())) {
                Map<String, Object> data = GraphNode.attrs();
                data.put("processDomain", process.processDomain());
                nodes.add(new GraphNode(process.id(), process.name(), TYPE_PROCESS, data));
            }
            if (addedApps.add(app.id())) {
                nodes.add(applicationNode(app, ownershipByAppId));
            }
            Map<String, Object> data = GraphNode.attrs();
            data.put("roleOfApplication",
                    mapping.roleOfApplication() == null ? null : mapping.roleOfApplication().name());
            data.put("processCriticality",
                    mapping.processCriticality() == null ? null : mapping.processCriticality().name());
            edges.add(new GraphEdge(
                    mapping.id() == null ? process.id() + "->" + app.id() : mapping.id(),
                    process.id(),
                    app.id(),
                    mapping.roleOfApplication() == null ? "supports" : mapping.roleOfApplication().name(),
                    "processMapping",
                    data));
        }
        return new GraphDto(TYPE_PROCESS, nodes, edges);
    }

    /** Domain frame: apps aggregated by business domain; cross-domain edges collapsed. */
    public GraphDto domainView(CanonicalModel model) {
        return domainView(model, graphBuilderService.build(model));
    }

    /** Domain frame reusing a pre-built graph. */
    public GraphDto domainView(CanonicalModel model, Graph<Application, RelationshipEdge> graph) {
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

        // Collapse cross-domain relationships into weighted domain -> domain edges.
        Map<String, Integer> edgeWeights = new LinkedHashMap<>();
        for (RelationshipEdge edge : graph.edgeSet()) {
            String from = domainKeyByAppId.get(graph.getEdgeSource(edge).id());
            String to = domainKeyByAppId.get(graph.getEdgeTarget(edge).id());
            if (from == null || to == null || from.equals(to)) {
                continue; // ignore intra-domain relationships
            }
            edgeWeights.merge(from + ">" + to, 1, Integer::sum);
        }

        List<GraphEdge> edges = new ArrayList<>();
        edgeWeights.forEach((key, weight) -> {
            String[] parts = key.split(">", 2);
            Map<String, Object> data = GraphNode.attrs();
            data.put("relationshipCount", weight);
            edges.add(new GraphEdge(
                    "DOM_" + key, parts[0], parts[1], weight + " relationship(s)", "domainFlow", data));
        });
        return new GraphDto(TYPE_DOMAIN, nodes, edges);
    }

    /** Information-flow frame: source application -> information object -> target application. */
    public GraphDto informationFlowView(CanonicalModel model) {
        Map<String, Application> appById = indexApplications(model);
        Map<String, ApplicationOwnership> ownershipByAppId = indexOwnership(model);
        Map<String, Interface> interfaceById = new HashMap<>();
        for (Interface iface : model.interfaces()) {
            if (!isBlank(iface.id())) {
                interfaceById.put(iface.id(), iface);
            }
        }

        Map<String, GraphNode> nodes = new LinkedHashMap<>();
        List<GraphEdge> edges = new ArrayList<>();

        for (InformationObject info : model.informationObjects()) {
            Application source = appById.get(info.sourceApplicationId());
            Application target = appById.get(info.targetApplicationId());
            if (source == null || target == null) {
                continue;
            }
            nodes.putIfAbsent(source.id(), applicationNode(source, ownershipByAppId));
            nodes.putIfAbsent(target.id(), applicationNode(target, ownershipByAppId));

            String ioId = "IO:" + info.informationObject();
            nodes.computeIfAbsent(ioId, id -> {
                Map<String, Object> data = GraphNode.attrs();
                data.put("classification", info.classification() == null ? null : info.classification().name());
                data.put("sensitive", info.classification() != null && info.classification().isSensitive());
                return new GraphNode(id, info.informationObject(), TYPE_INFORMATION_OBJECT, data);
            });

            Interface iface = interfaceById.get(info.interfaceId());
            Map<String, Object> data = GraphNode.attrs();
            data.put("flowId", info.id());
            data.put("operation", info.operation() == null ? null : info.operation().name());
            data.put("interfaceId", info.interfaceId());
            data.put("interfaceStatus",
                    iface == null || iface.interfaceStatus() == null ? null : iface.interfaceStatus().name());
            edges.add(new GraphEdge(info.id() + ":produces", source.id(), ioId, "produces", "produces", data));
            String consumeLabel = info.operation() == null ? "consumes" : info.operation().name().toLowerCase(Locale.ROOT);
            edges.add(new GraphEdge(info.id() + ":consumes", ioId, target.id(), consumeLabel, "consumes", data));
        }
        return new GraphDto(TYPE_INFORMATION_OBJECT, new ArrayList<>(nodes.values()), edges);
    }

    // --- helpers --------------------------------------------------------------

    private GraphNode applicationNode(Application app, Map<String, ApplicationOwnership> ownershipByAppId) {
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
        data.put("systemCustodian", ownership == null ? null : ownership.systemCustodian());
        data.put("businessOwner", ownership == null ? null : ownership.businessOwner());
        data.put("supportGroup", ownership == null ? null : ownership.supportGroup());
        data.put("department", ownership == null ? null : ownership.department());

        data.putAll(app.attributes());
        return new GraphNode(app.id(), app.name(), TYPE_APPLICATION, data);
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

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
