package org.example;

import java.util.List;

public record PenaltyMethodResult(
        List<PenaltyIterationData> iterations,
        String summary
) {
}
