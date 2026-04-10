package org.example;

public record PenaltyMethodRequest(
        double startX1,
        double startX2,
        double mu,
        double epsilon,
        int maxIterations
) {
}
