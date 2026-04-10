package org.example;

public record PenaltyIterationData(
        int iteration,
        double mu,
        double x1,
        double x2,
        double fx,
        double alpha,
        double theta,
        double muAlpha,
        double gradientNorm,
        String comment
) {
}
