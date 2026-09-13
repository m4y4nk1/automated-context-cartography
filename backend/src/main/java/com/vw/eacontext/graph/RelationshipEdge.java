package com.vw.eacontext.graph;

import org.jgrapht.graph.DefaultEdge;

import com.vw.eacontext.model.DependencyCriticality;
import com.vw.eacontext.model.RelationshipType;

import lombok.Getter;

/**
 * A graph edge representing a {@link com.vw.eacontext.model.Relationship}
 * between two applications, carrying the relationship metadata.
 *
 * <p>Extends {@link DefaultEdge} so the source/target applications remain
 * accessible via the graph. Multiple edges may connect the same pair of
 * applications (hence a pseudograph), so identity is per-instance.</p>
 */
@Getter
public class RelationshipEdge extends DefaultEdge {

    /** The originating relationship id. */
    private final String relationshipId;

    /** Nature of the dependency ({@code depends on} / {@code uses}). */
    private final RelationshipType relationshipType;

    /** How critical this dependency is. */
    private final DependencyCriticality dependencyCriticality;

    public RelationshipEdge(String relationshipId, RelationshipType relationshipType,
                            DependencyCriticality dependencyCriticality) {
        this.relationshipId = relationshipId;
        this.relationshipType = relationshipType;
        this.dependencyCriticality = dependencyCriticality;
    }

    @Override
    public String toString() {
        return "RelationshipEdge{id=" + relationshipId + ", type=" + relationshipType
                + ", " + getSource() + " -> " + getTarget() + "}";
    }
}
