package com.vw.eacontext.graph;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.vw.eacontext.dto.GraphDto;
import com.vw.eacontext.dto.GraphNode;
import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.model.Classification;
import com.vw.eacontext.model.InformationObject;

class InformationFlowClassificationTest {

    private final GraphProjectionService projectionService = new GraphProjectionService(
            new GraphBuilderService(), new GhostReferenceResolver(), new ApplicationEdgeAssembler());

    @Test
    void anInformationObjectNodeCarriesTheMostSensitiveClassificationAcrossItsFlows() {
        // The INTERNAL row comes first; the node must still be marked as PII.
        CanonicalModel model = CanonicalModel.builder()
                .applications(List.of(app("A"), app("B"), app("C")))
                .informationObjects(List.of(
                        flow("FLOW-1", "A", "B", Classification.INTERNAL),
                        flow("FLOW-2", "A", "C", Classification.CONFIDENTIAL_PII)))
                .build();

        GraphDto dto = projectionService.informationFlowView(model);

        GraphNode customerData = dto.nodes().stream()
                .filter(n -> "IO:Customer Data".equals(n.id())).findFirst().orElseThrow();
        assertThat(customerData.data().get("classification")).isEqualTo("CONFIDENTIAL_PII");
        assertThat(customerData.data().get("sensitive")).isEqualTo(true);
    }

    private static InformationObject flow(String id, String source, String target, Classification classification) {
        return InformationObject.builder().id(id).informationObject("Customer Data")
                .sourceApplicationId(source).targetApplicationId(target).classification(classification).build();
    }

    private static Application app(String id) {
        return Application.builder().id(id).name(id).build();
    }
}
