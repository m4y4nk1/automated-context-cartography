package com.vw.eacontext.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.vw.eacontext.dto.GraphEdge;
import com.vw.eacontext.dto.GraphNode;
import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.model.InformationObject;
import com.vw.eacontext.model.Interface;
import com.vw.eacontext.model.Relationship;
import com.vw.eacontext.model.RelationshipType;

import lombok.extern.slf4j.Slf4j;

/**
 * Builds the application frame's edges from <em>all three</em> edge-bearing
 * record types — relationships, interfaces and information flows — each
 * becoming its own edge, labelled with what it specifically is (the
 * relationship type, the interface name, or the information object name).
 *
 * <p>Rendering only relationship edges hides most of the landscape: interfaces
 * and information flows describe real couplings that never reached the
 * diagram. Every record becomes its own line rather than being merged with
 * others connecting the same pair — the same two applications can and often
 * do have a dependency, an interface and a flow all shown as three distinct
 * lines, which is what an architect sees when they ask "what exactly connects
 * these two systems."</p>
 *
 * <p>Relationship edges are drawn provider &rarr; dependent (i.e. the record's
 * target &rarr; source), the opposite of the raw field order. This is
 * deliberate: {@link Interface} edges are drawn provider &rarr; consumer and
 * {@link InformationObject} edges origin &rarr; destination, and both already
 * point away from the providing/originating application toward the dependent
 * one. A {@link Relationship}'s raw field order is source = the dependent
 * application, target = the depended-upon one — literally the opposite
 * convention — so drawing it unflipped would make the arrowhead mean
 * "the provider" for one edge type and "the dependent" for the other two.
 * Flipping it here keeps every arrowhead in the diagram meaning the same
 * thing: it points at the application that depends on / consumes / receives
 * from the other end.</p>
 *
 * <p>This is a presentation concern only. The analytical graph built by
 * {@link GraphBuilderService} is untouched by this flip — it still uses the
 * relationship's own source/target exactly as recorded — so hub scores, cycle
 * detection and blast radius are unaffected.</p>
 */
@Slf4j
@Service
public class ApplicationEdgeAssembler {

    /** Edge types for interfaces and information flows, mirroring RelationshipType's role. */
    static final String TYPE_INTERFACE = "INTERFACE";
    static final String TYPE_FLOW = "FLOW";

    /**
     * Builds one edge per relationship/interface/information-flow record for
     * the application frame.
     *
     * @param model   the canonical model
     * @param nodeIds ids that exist as nodes in this frame — real applications
     *                plus any materialised ghost placeholders. An edge is only
     *                emitted when both of its endpoints are present.
     * @return one {@link GraphEdge} per resolvable record
     */
    public List<GraphEdge> assemble(CanonicalModel model, Set<String> nodeIds) {
        List<GraphEdge> edges = new ArrayList<>();
        int skipped = 0;

        for (Relationship relationship : model.relationships()) {
            String source = relationship.sourceApplicationId();
            String target = relationship.targetApplicationId();
            if (!endpointsPresent(source, target, nodeIds)) {
                skipped++;
                continue;
            }
            RelationshipType type = relationship.relationshipType();
            String typeName = type == null ? null : type.name();
            Map<String, Object> data = GraphNode.attrs();
            data.put("memberIds", List.of(relationship.id()));
            data.put("edgeTypes", List.of("DEPENDENCY"));
            data.put("dependencyCriticality",
                    relationship.dependencyCriticality() == null ? null : relationship.dependencyCriticality().name());
            // Flipped: provider (target) -> dependent (source). See class javadoc.
            edges.add(new GraphEdge(relationship.id(), target, source,
                    typeName == null ? null : humanize(typeName), typeName, data));
        }

        for (Interface iface : model.interfaces()) {
            String provider = iface.providerApplicationId();
            String consumer = iface.consumerApplicationId();
            if (!endpointsPresent(provider, consumer, nodeIds)) {
                skipped++;
                continue;
            }
            Map<String, Object> data = GraphNode.attrs();
            data.put("memberIds", List.of(iface.id()));
            data.put("edgeTypes", List.of("INTERFACE"));
            data.put("protocols", presentOrEmpty(name(iface.protocol())));
            data.put("interfaceStatuses", presentOrEmpty(name(iface.interfaceStatus())));
            data.put("dataFormats", presentOrEmpty(name(iface.dataFormat())));
            data.put("frequencies", presentOrEmpty(name(iface.frequency())));
            edges.add(new GraphEdge(iface.id(), provider, consumer, iface.name(), TYPE_INTERFACE, data));
        }

        for (InformationObject info : model.informationObjects()) {
            String source = info.sourceApplicationId();
            String target = info.targetApplicationId();
            if (!endpointsPresent(source, target, nodeIds)) {
                skipped++;
                continue;
            }
            Map<String, Object> data = GraphNode.attrs();
            data.put("memberIds", List.of(info.id()));
            data.put("edgeTypes", List.of("FLOW"));
            data.put("classifications", presentOrEmpty(name(info.classification())));
            data.put("operations", presentOrEmpty(name(info.operation())));
            edges.add(new GraphEdge(info.id(), source, target, info.informationObject(), TYPE_FLOW, data));
        }

        int total = model.relationships().size() + model.interfaces().size() + model.informationObjects().size();
        log.info("Assembled {} application edge(s) from {} record(s) ({} unresolvable)",
                edges.size(), total - skipped, skipped);
        return edges;
    }

    private static boolean endpointsPresent(String source, String target, Set<String> nodeIds) {
        return !isBlank(source) && !isBlank(target) && nodeIds.contains(source) && nodeIds.contains(target);
    }

    /** {@code DEPENDS_ON} &rarr; {@code depends on}. */
    private static String humanize(String enumName) {
        return enumName.replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    private static List<String> presentOrEmpty(String value) {
        return isBlank(value) ? List.of() : List.of(value);
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
