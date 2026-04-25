package org.example;

import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYLineAnnotation;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.event.AxisChangeEvent;
import org.jfree.chart.event.AxisChangeListener;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PenaltyMethodChartPanel extends JPanel {

    private static final int CONTOUR_GRID = 55;
    private static final int CONTOUR_LEVELS = 12;
    private static final double POINT_SIZE = 4.0;
    private static final Bounds DEFAULT_VIEWPORT = new Bounds(-4.0, 8.0, -2.0, 4.0);

    private final ChartPanel chartPanel;
    private final JLabel cursorLabel;

    private PenaltyMethodResult result;
    private int selectedIteration = -1;
    private double[] contourLevels;
    private Bounds viewport;
    private boolean updatingViewport;

    public PenaltyMethodChartPanel() {
        setLayout(new BorderLayout());

        chartPanel = new ChartPanel(new JFreeChart(new XYPlot()));
        chartPanel.setMouseWheelEnabled(true);
        chartPanel.addChartMouseListener(new ChartMouseListener() {
            @Override
            public void chartMouseClicked(ChartMouseEvent event) {
            }

            @Override
            public void chartMouseMoved(ChartMouseEvent event) {
                onMouseMoved(event);
            }
        });

        cursorLabel = new JLabel("x1: -, x2: -", SwingConstants.CENTER);
        cursorLabel.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

        add(chartPanel, BorderLayout.CENTER);
        add(cursorLabel, BorderLayout.SOUTH);
        installKeyboardPan();
    }

    public void updateData(PenaltyMethodResult result) {
        this.result = result;
        this.selectedIteration = result == null || result.iterations().isEmpty() ? -1 : 0;
        this.contourLevels = null;
        this.viewport = null;
        rebuildChart();
    }

    public void setSelectedIteration(int selectedIteration) {
        this.selectedIteration = selectedIteration;
        rebuildChart();
    }

    public void clear() {
        this.result = null;
        this.selectedIteration = -1;
        this.contourLevels = null;
        this.viewport = null;
        rebuildChart();
    }

    private void rebuildChart() {
        NumberAxis xAxis = new NumberAxis("x1");
        NumberAxis yAxis = new NumberAxis("x2");
        xAxis.setAutoRange(false);
        yAxis.setAutoRange(false);
        xAxis.setLowerMargin(0.0);
        xAxis.setUpperMargin(0.0);
        yAxis.setLowerMargin(0.0);
        yAxis.setUpperMargin(0.0);

        XYPlot plot = new XYPlot(null, xAxis, yAxis, null);
        plot.setDomainPannable(true);
        plot.setRangePannable(true);
        plot.setBackgroundPaint(new Color(248, 249, 252));
        plot.setDomainGridlinePaint(new Color(225, 229, 233));
        plot.setRangeGridlinePaint(new Color(225, 229, 233));

        if (result != null && !result.iterations().isEmpty()) {
            List<PenaltyIterationData> iterations = result.iterations();
            int current = Math.max(0, Math.min(selectedIteration, iterations.size() - 1));

            Bounds currentViewport = viewport != null ? viewport : DEFAULT_VIEWPORT;
            if (contourLevels == null) {
                contourLevels = buildContourLevels(DEFAULT_VIEWPORT);
            }

            plot.setDataset(0, contourDataset(currentViewport, contourLevels));
            plot.setRenderer(0, contourRenderer());

            plot.setDataset(1, constraintDataset(currentViewport));
            plot.setRenderer(1, lineRenderer(new Color(200, 39, 39), 2.2f));

            plot.setDataset(2, trajectoryDataset(result));
            plot.setRenderer(2, lineRenderer(new Color(206, 80, 54), 1.8f));

            plot.setDataset(3, markersDataset(result, current));
            plot.setRenderer(3, markerRenderer());

            xAxis.setRange(currentViewport.minX, currentViewport.maxX);
            yAxis.setRange(currentViewport.minY, currentViewport.maxY);
            addConstraintLabel(plot, currentViewport);
            addCurrentArrow(plot, result, current);
            attachViewportListener(plot, xAxis, yAxis);
        }

        chartPanel.setChart(new JFreeChart("Линии уровня, ограничение и траектория", JFreeChart.DEFAULT_TITLE_FONT, plot, false));
    }

    private void attachViewportListener(XYPlot plot, NumberAxis xAxis, NumberAxis yAxis) {
        AxisChangeListener listener = (AxisChangeEvent e) -> {
            if (updatingViewport || result == null || result.iterations().isEmpty()) {
                return;
            }
            updatingViewport = true;
            try {
                viewport = new Bounds(
                        xAxis.getRange().getLowerBound(),
                        xAxis.getRange().getUpperBound(),
                        yAxis.getRange().getLowerBound(),
                        yAxis.getRange().getUpperBound()
                );
                plot.setDataset(0, contourDataset(viewport, contourLevels));
                plot.setDataset(1, constraintDataset(viewport));
                plot.clearAnnotations();
                int current = Math.max(0, Math.min(selectedIteration, result.iterations().size() - 1));
                addConstraintLabel(plot, viewport);
                addCurrentArrow(plot, result, current);
            } finally {
                updatingViewport = false;
            }
        };
        xAxis.addChangeListener(listener);
        yAxis.addChangeListener(listener);
    }

    private XYSeriesCollection contourDataset(Bounds b, double[] levels) {
        double[] xs = linspace(b.minX, b.maxX, CONTOUR_GRID);
        double[] ys = linspace(b.minY, b.maxY, CONTOUR_GRID);
        double[][] z = new double[CONTOUR_GRID][CONTOUR_GRID];

        for (int i = 0; i < CONTOUR_GRID; i++) {
            for (int j = 0; j < CONTOUR_GRID; j++) {
                z[i][j] = objective(xs[i], ys[j]);
            }
        }

        XYSeriesCollection out = new XYSeriesCollection();
        for (int levelIdx = 0; levelIdx < levels.length; levelIdx++) {
            int segIdx = 0;
            for (Segment s : marchingSquares(xs, ys, z, levels[levelIdx])) {
                XYSeries line = new XYSeries("L" + levelIdx + "-" + segIdx++);
                line.add(s.ax, s.ay);
                line.add(s.bx, s.by);
                out.addSeries(line);
            }
        }
        return out;
    }

    private XYSeriesCollection constraintDataset(Bounds b) {
        XYSeries line = new XYSeries("constraint");
        line.add(b.minX, (6.0 - b.minX) / 2.0);
        line.add(b.maxX, (6.0 - b.maxX) / 2.0);
        XYSeriesCollection ds = new XYSeriesCollection();
        ds.addSeries(line);
        return ds;
    }

    private XYSeriesCollection trajectoryDataset(PenaltyMethodResult result) {
        // Preserve iteration order (k -> k+1); default XYSeries auto-sorts by X.
        XYSeries path = new XYSeries("path", false, true);
        path.add(result.startX1(), result.startX2());
        List<PenaltyIterationData> iterations = result.iterations();
        for (PenaltyIterationData it : iterations) {
            path.add(it.x1(), it.x2());
        }
        XYSeriesCollection ds = new XYSeriesCollection();
        ds.addSeries(path);
        return ds;
    }

    private XYSeriesCollection markersDataset(PenaltyMethodResult result, int currentIndex) {
        List<PenaltyIterationData> iterations = result.iterations();
        PenaltyIterationData end = iterations.get(iterations.size() - 1);
        PenaltyIterationData current = iterations.get(currentIndex);

        XYSeries s0 = new XYSeries("start");
        XYSeries s1 = new XYSeries("end");
        XYSeries s2 = new XYSeries("current");
        s0.add(result.startX1(), result.startX2());
        s1.add(end.x1(), end.x2());
        s2.add(current.x1(), current.x2());

        XYSeriesCollection ds = new XYSeriesCollection();
        ds.addSeries(s0);
        ds.addSeries(s1);
        ds.addSeries(s2);
        return ds;
    }

    private XYLineAndShapeRenderer contourRenderer() {
        XYLineAndShapeRenderer r = new XYLineAndShapeRenderer(true, false);
        r.setAutoPopulateSeriesPaint(false);
        r.setAutoPopulateSeriesStroke(false);
        r.setDefaultPaint(new Color(140, 154, 167, 140));
        r.setDefaultStroke(new BasicStroke(0.9f));
        return r;
    }

    private XYLineAndShapeRenderer lineRenderer(Color color, float width) {
        XYLineAndShapeRenderer r = new XYLineAndShapeRenderer(true, false);
        r.setDefaultPaint(color);
        r.setDefaultStroke(new BasicStroke(width));
        return r;
    }

    private XYLineAndShapeRenderer markerRenderer() {
        XYLineAndShapeRenderer r = new XYLineAndShapeRenderer(false, true);
        Shape p = new Ellipse2D.Double(-POINT_SIZE / 2.0, -POINT_SIZE / 2.0, POINT_SIZE, POINT_SIZE);
        r.setSeriesShape(0, p);
        r.setSeriesShape(1, p);
        r.setSeriesShape(2, p);
        r.setSeriesPaint(0, new Color(46, 125, 50));
        r.setSeriesPaint(1, new Color(13, 110, 253));
        r.setSeriesPaint(2, new Color(220, 53, 69));
        return r;
    }

    private void addConstraintLabel(XYPlot plot, Bounds b) {
        double x = b.minX + 0.22 * (b.maxX - b.minX);
        double y = (6.0 - x) / 2.0;
        XYTextAnnotation label = new XYTextAnnotation("Ограничение: x1 + 2x2 - 6 = 0", x, y);
        label.setPaint(new Color(200, 39, 39));
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        plot.addAnnotation(label);
    }

    private void addCurrentArrow(XYPlot plot, PenaltyMethodResult result, int currentIndex) {
        List<Point> points = trajectoryPoints(result);
        if (points.size() < 2) {
            return;
        }
        if (currentIndex < 0 || currentIndex >= points.size() - 1) {
            return;
        }

        int from = currentIndex;
        int to = currentIndex + 1;

        Point a = points.get(from);
        Point b = points.get(to);
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double step = Math.hypot(dx, dy);
        if (step < 1e-9) {
            return;
        }

        double ux = dx / step;
        double uy = dy / step;
        double len = Math.min(plot.getDomainAxis().getRange().getLength(), plot.getRangeAxis().getRange().getLength()) * 0.03;
        double wing = Math.toRadians(24.0);

        double bx = -ux;
        double by = -uy;
        double lx = bx * Math.cos(wing) - by * Math.sin(wing);
        double ly = bx * Math.sin(wing) + by * Math.cos(wing);
        double rx = bx * Math.cos(-wing) - by * Math.sin(-wing);
        double ry = bx * Math.sin(-wing) + by * Math.cos(-wing);

        plot.addAnnotation(new XYLineAnnotation(
                b.x, b.y,
                b.x + len * lx, b.y + len * ly,
                new BasicStroke(0.9f), new Color(206, 80, 54)
        ));
        plot.addAnnotation(new XYLineAnnotation(
                b.x, b.y,
                b.x + len * rx, b.y + len * ry,
                new BasicStroke(0.9f), new Color(206, 80, 54)
        ));
    }

    private List<Point> trajectoryPoints(PenaltyMethodResult result) {
        List<Point> points = new ArrayList<>();
        points.add(new Point(result.startX1(), result.startX2()));
        for (PenaltyIterationData iteration : result.iterations()) {
            points.add(new Point(iteration.x1(), iteration.x2()));
        }
        return points;
    }

    private void onMouseMoved(ChartMouseEvent event) {
        JFreeChart chart = chartPanel.getChart();
        if (chart == null || !(chart.getPlot() instanceof XYPlot plot) || plot.getDomainAxis() == null || plot.getRangeAxis() == null) {
            cursorLabel.setText("x1: -, x2: -");
            return;
        }

        Rectangle2D area = chartPanel.getScreenDataArea();
        int sx = event.getTrigger().getX();
        int sy = event.getTrigger().getY();
        if (area == null || !area.contains(sx, sy)) {
            cursorLabel.setText("x1: -, x2: -");
            return;
        }

        double x = plot.getDomainAxis().java2DToValue(sx, area, plot.getDomainAxisEdge());
        double y = plot.getRangeAxis().java2DToValue(sy, area, plot.getRangeAxisEdge());
        cursorLabel.setText(String.format(Locale.US, "x1: %.4f, x2: %.4f", x, y));
    }

    private void installKeyboardPan() {
        InputMap im = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();

        bindPan(im, am, "W", "pan-up", 0.0, 0.08);
        bindPan(im, am, "S", "pan-down", 0.0, -0.08);
        bindPan(im, am, "A", "pan-left", -0.08, 0.0);
        bindPan(im, am, "D", "pan-right", 0.08, 0.0);
    }

    private void bindPan(InputMap im, ActionMap am, String key, String action, double dxFraction, double dyFraction) {
        im.put(KeyStroke.getKeyStroke(key), action);
        im.put(KeyStroke.getKeyStroke(key.toLowerCase(Locale.ROOT)), action);
        am.put(action, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                pan(dxFraction, dyFraction);
            }
        });
    }

    private void pan(double dxFraction, double dyFraction) {
        JFreeChart chart = chartPanel.getChart();
        if (chart == null || !(chart.getPlot() instanceof XYPlot plot) || plot.getDomainAxis() == null || plot.getRangeAxis() == null) {
            return;
        }
        double xMin = plot.getDomainAxis().getRange().getLowerBound();
        double xMax = plot.getDomainAxis().getRange().getUpperBound();
        double yMin = plot.getRangeAxis().getRange().getLowerBound();
        double yMax = plot.getRangeAxis().getRange().getUpperBound();
        double xShift = (xMax - xMin) * dxFraction;
        double yShift = (yMax - yMin) * dyFraction;

        plot.getDomainAxis().setRange(xMin + xShift, xMax + xShift);
        plot.getRangeAxis().setRange(yMin + yShift, yMax + yShift);
    }

    private double[] buildContourLevels(Bounds b) {
        double[] xs = linspace(b.minX, b.maxX, 70);
        double[] ys = linspace(b.minY, b.maxY, 70);
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        for (double x : xs) {
            for (double y : ys) {
                double v = objective(x, y);
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
        }

        double[] levels = new double[CONTOUR_LEVELS];
        double span = Math.max(1e-12, max - min);
        for (int i = 0; i < CONTOUR_LEVELS; i++) {
            double t = (i + 1.0) / (CONTOUR_LEVELS + 1.0);
            // Нелинейная шкала: больше уровней около минимума функции.
            levels[i] = min + span * t * t;
        }
        return levels;
    }

    private double objective(double x1, double x2) {
        return Math.exp(x1) + x1 * x1 + 2.0 * x1 * x2 + 4.0 * Math.pow(x2, 4);
    }

    private static double[] linspace(double from, double to, int n) {
        double[] arr = new double[n];
        for (int i = 0; i < n; i++) {
            arr[i] = from + (to - from) * i / (n - 1.0);
        }
        return arr;
    }

    private static List<Segment> marchingSquares(double[] xs, double[] ys, double[][] z, double level) {
        List<Segment> out = new ArrayList<>();
        for (int i = 0; i < xs.length - 1; i++) {
            for (int j = 0; j < ys.length - 1; j++) {
                double x0 = xs[i], x1 = xs[i + 1], y0 = ys[j], y1 = ys[j + 1];
                double v0 = z[i][j], v1 = z[i + 1][j], v2 = z[i + 1][j + 1], v3 = z[i][j + 1];
                int mask = (v0 >= level ? 1 : 0) | (v1 >= level ? 2 : 0) | (v2 >= level ? 4 : 0) | (v3 >= level ? 8 : 0);
                if (mask == 0 || mask == 15) {
                    continue;
                }

                Point e0 = interp(x0, y0, x1, y0, v0, v1, level);
                Point e1 = interp(x1, y0, x1, y1, v1, v2, level);
                Point e2 = interp(x1, y1, x0, y1, v2, v3, level);
                Point e3 = interp(x0, y1, x0, y0, v3, v0, level);

                switch (mask) {
                    case 1, 14 -> out.add(new Segment(e3.x, e3.y, e0.x, e0.y));
                    case 2, 13 -> out.add(new Segment(e0.x, e0.y, e1.x, e1.y));
                    case 3, 12 -> out.add(new Segment(e3.x, e3.y, e1.x, e1.y));
                    case 4, 11 -> out.add(new Segment(e1.x, e1.y, e2.x, e2.y));
                    case 5 -> {
                        out.add(new Segment(e3.x, e3.y, e2.x, e2.y));
                        out.add(new Segment(e0.x, e0.y, e1.x, e1.y));
                    }
                    case 6, 9 -> out.add(new Segment(e0.x, e0.y, e2.x, e2.y));
                    case 7, 8 -> out.add(new Segment(e3.x, e3.y, e2.x, e2.y));
                    case 10 -> {
                        out.add(new Segment(e0.x, e0.y, e3.x, e3.y));
                        out.add(new Segment(e1.x, e1.y, e2.x, e2.y));
                    }
                    default -> {
                    }
                }
            }
        }
        return out;
    }

    private static Point interp(double xa, double ya, double xb, double yb, double va, double vb, double level) {
        double t = Math.abs(vb - va) < 1e-12 ? 0.5 : (level - va) / (vb - va);
        t = Math.max(0.0, Math.min(1.0, t));
        return new Point(xa + (xb - xa) * t, ya + (yb - ya) * t);
    }

    private record Bounds(double minX, double maxX, double minY, double maxY) {}

    private record Segment(double ax, double ay, double bx, double by) {}

    private record Point(double x, double y) {}
}
