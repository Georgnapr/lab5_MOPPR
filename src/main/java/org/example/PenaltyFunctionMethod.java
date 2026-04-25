package org.example;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PenaltyFunctionMethod {

    private static final int DIMENSION = 2;
    private static final int MAX_INNER_ITERATIONS = 200;
    private static final int MAX_LINE_SEARCH_ITERATIONS = 50;
    private static final double ARMIJO_C = 1e-4;
    private static final double LINE_SEARCH_REDUCTION = 0.5;
    private static final double DEFAULT_FALLBACK_STEP = 0.001;
    private static final double MU_GROWTH_FACTOR = 10.0;
    private static final double MIN_DESCENT = 1e-12;

    public PenaltyMethodResult solve(PenaltyMethodRequest request) {
        List<PenaltyIterationData> iterations = new ArrayList<>();

        double startX1 = request.startX1();
        double startX2 = request.startX2();
        double x1 = startX1;
        double x2 = startX2;
        double mu = Math.max(request.mu(), 1e-12);
        double epsilon = Math.max(request.epsilon(), 1e-12);
        int maxPenaltyIterations = Math.max(request.maxIterations(), 1);

        boolean constraintSatisfied = false;
        for (int k = 0; k < maxPenaltyIterations; k++) {
            Point minimum = minimizePhiByConjugateGradients(x1, x2, mu, epsilon);
            x1 = minimum.x1();
            x2 = minimum.x2();

            double alpha = penalty(x1, x2);
            double gradientNorm = norm(gradientX1(x1, x2, mu), gradientX2(x1, x2, mu));
            constraintSatisfied = alpha <= epsilon;

            String comment = constraintSatisfied
                    ? "Штрафное условие выполнено"
                    : "Минимизация Phi(x, μk) методом сопряжённых градиентов";
            iterations.add(buildIteration(k, mu, x1, x2, gradientNorm, comment));

            double muAlpha = mu * penalty(x1, x2);
            if (muAlpha < epsilon) {
                break;
            }

            mu *= MU_GROWTH_FACTOR;
        }

        String summary = String.format(
                Locale.US,
                "Расчёт завершен. mu = %.3f, X* = (%.6f, %.6f), F(X*) = %.6f, alpha(X*) = %.6f, h(X*) = %.6f, итераций = %d",
                mu,
                x1,
                x2,
                objective(x1, x2),
                penalty(x1, x2),
                constraint(x1, x2),
                iterations.size()
        );

        return new PenaltyMethodResult(startX1, startX2, iterations, summary);
    }

    private Point minimizePhiByConjugateGradients(double startX1, double startX2, double mu, double epsilon) {
        double x1 = startX1;
        double x2 = startX2;

        double g1 = gradientX1(x1, x2, mu);
        double g2 = gradientX2(x1, x2, mu);
        double d1 = -g1;
        double d2 = -g2;

        for (int iteration = 0; iteration < MAX_INNER_ITERATIONS; iteration++) {
            if (norm(g1, g2) <= epsilon) {
                break;
            }

            double slope = g1 * d1 + g2 * d2;
            if (slope >= -MIN_DESCENT) {
                d1 = -g1;
                d2 = -g2;
                slope = -(g1 * g1 + g2 * g2);
            }

            double lambda = lineSearch(x1, x2, d1, d2, mu, slope);
            double newX1 = x1 + lambda * d1;
            double newX2 = x2 + lambda * d2;
            double newG1 = gradientX1(newX1, newX2, mu);
            double newG2 = gradientX2(newX1, newX2, mu);

            double denominator = g1 * g1 + g2 * g2;
            double beta = denominator <= MIN_DESCENT
                    ? 0.0
                    : (newG1 * newG1 + newG2 * newG2) / denominator;

            if ((iteration + 1) % DIMENSION == 0) {
                beta = 0.0;
            }

            d1 = -newG1 + beta * d1;
            d2 = -newG2 + beta * d2;
            x1 = newX1;
            x2 = newX2;
            g1 = newG1;
            g2 = newG2;
        }

        return new Point(x1, x2);
    }

    private double objective(double x1, double x2) {
        return Math.exp(x1) + x1 * x1 + 2.0 * x1 * x2 + 4.0 * Math.pow(x2, 4);
    }

    private double constraint(double x1, double x2) {
        return x1 + 2.0 * x2 - 6.0;
    }

    private double penalty(double x1, double x2) {
        double h = constraint(x1, x2);
        return h * h;
    }

    private double phi(double x1, double x2, double mu) {
        return objective(x1, x2) + mu * penalty(x1, x2);
    }

    private double gradientX1(double x1, double x2, double mu) {
        return Math.exp(x1) + 2.0 * x1 + 2.0 * x2 + 2.0 * mu * constraint(x1, x2);
    }

    private double gradientX2(double x1, double x2, double mu) {
        return 2.0 * x1 + 16.0 * Math.pow(x2, 3) + 4.0 * mu * constraint(x1, x2);
    }

    private double norm(double g1, double g2) {
        return Math.sqrt(g1 * g1 + g2 * g2);
    }

    private double lineSearch(double x1, double x2, double d1, double d2, double mu, double slope) {
        double step = 1.0;
        double phi0 = phi(x1, x2, mu);

        for (int i = 0; i < MAX_LINE_SEARCH_ITERATIONS; i++) {
            double newX1 = x1 + step * d1;
            double newX2 = x2 + step * d2;
            double phiNew = phi(newX1, newX2, mu);

            if (phiNew <= phi0 + ARMIJO_C * step * slope) {
                return step;
            }
            step *= LINE_SEARCH_REDUCTION;
        }

        return DEFAULT_FALLBACK_STEP;
    }

    private PenaltyIterationData buildIteration(int iteration, double mu, double x1, double x2, double gradientNorm, String comment) {
        double fx = objective(x1, x2);
        double alpha = penalty(x1, x2);
        double muAlpha = mu * alpha;
        return new PenaltyIterationData(
                iteration,
                mu,
                x1,
                x2,
                fx,
                alpha,
                fx + muAlpha,
                muAlpha,
                gradientNorm,
                comment
        );
    }

    private record Point(double x1, double x2) {
    }
}
