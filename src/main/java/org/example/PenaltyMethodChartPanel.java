package org.example;

import javax.swing.*;
import java.awt.*;

public class PenaltyMethodChartPanel extends JPanel {

    private PenaltyMethodResult result;
    private int selectedIteration = -1;

    public void updateData(PenaltyMethodResult result) {
        this.result = result;
        this.selectedIteration = result == null || result.iterations().isEmpty() ? -1 : 0;
        repaint();
    }

    public void setSelectedIteration(int selectedIteration) {
        this.selectedIteration = selectedIteration;
        repaint();
    }

    public void clear() {
        this.result = null;
        this.selectedIteration = -1;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        g2.setColor(new Color(248, 249, 252));
        g2.fillRect(0, 0, w, h);

        int left = 60;
        int right = w - 30;
        int top = 30;
        int bottom = h - 60;

        g2.setColor(Color.DARK_GRAY);
        g2.drawLine(left, bottom, right, bottom);
        g2.drawLine(left, bottom, left, top);

        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 14f));
        g2.drawString("Визуализация метода штрафных функций", left, 20);

        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 12f));
        g2.drawString("x1", right - 10, bottom + 20);
        g2.drawString("x2", left - 25, top + 5);

        Stroke oldStroke = g2.getStroke();
        g2.setColor(new Color(70, 130, 180));
        g2.setStroke(new BasicStroke(2f));
        g2.drawLine(left + 40, top + 40, right - 20, bottom - 40);
        g2.drawString("h(x1, x2) = x1 + 2x2 - 6 = 0", left + 50, top + 35);
        g2.setStroke(oldStroke);

        if (result == null || result.iterations().isEmpty()) {
            g2.setColor(Color.GRAY);
            g2.drawString("Нет данных. Запустите расчет, чтобы увидеть стартовую точку и шаги метода.", left, bottom + 35);
            g2.dispose();
            return;
        }

        PenaltyIterationData iteration = result.iterations().get(Math.max(0, Math.min(selectedIteration, result.iterations().size() - 1)));
        int pointX = left + (int) ((iteration.x1() + 4.0) / 12.0 * (right - left));
        int pointY = bottom - (int) ((iteration.x2() + 2.0) / 8.0 * (bottom - top));

        g2.setColor(new Color(220, 53, 69));
        g2.fillOval(pointX - 6, pointY - 6, 12, 12);
        g2.drawString("Текущая точка", pointX + 10, pointY - 10);

        g2.setColor(Color.BLACK);
        g2.drawString(String.format("k = %d, mu = %.3f", iteration.iteration(), iteration.mu()), left, bottom + 35);
        g2.drawString(String.format("x = (%.3f, %.3f), Theta = %.3f", iteration.x1(), iteration.x2(), iteration.theta()), left + 170, bottom + 35);

        g2.dispose();
    }
}
