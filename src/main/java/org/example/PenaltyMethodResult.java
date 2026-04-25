package org.example;

import java.util.List;

public record PenaltyMethodResult(
        double startX1,
        double startX2,
        List<PenaltyIterationData> iterations,
        String summary
) {
}
