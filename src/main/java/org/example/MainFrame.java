package org.example;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Locale;

public class MainFrame extends JFrame {

    private static final String DEFAULT_X1 = "1.0";
    private static final String DEFAULT_X2 = "1.0";
    private static final String DEFAULT_MU = "10.0";
    private static final String DEFAULT_EPSILON = "0.001";
    private static final String DEFAULT_STATUS = "";

    private static final String[] TABLE_COLUMNS = {
            "k", "μk", "Xk+1 = Xμk", "F(Xk+1)", "α(Xμk)", "θ(μk)", "μk α(Xμk)"
    };

    private final JTextField tfX1 = new JTextField(DEFAULT_X1, 8);
    private final JTextField tfX2 = new JTextField(DEFAULT_X2, 8);
    private final JTextField tfMu = new JTextField(DEFAULT_MU, 8);
    private final JTextField tfEpsilon = new JTextField(DEFAULT_EPSILON, 8);
    private final JLabel lbModel = new JLabel(
            "  F(x1, x2) = exp(x1) + x1^2 + 2x1x2 + 4x2^4; h(x1, x2) = x1 + 2x2 - 6; Φ(x, μ) = F(x) + μ·α(x); α(x) = h(x)^2"
    );
    private final JLabel lbStatus = new JLabel(DEFAULT_STATUS);
    private final PenaltyMethodChartPanel chartPanel = new PenaltyMethodChartPanel();
    private final DefaultTableModel tableModel = new DefaultTableModel(TABLE_COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(tableModel);
    private final PenaltyFunctionMethod penaltyFunctionMethod = new PenaltyFunctionMethod();

    private PenaltyMethodResult currentResult;

    public MainFrame() {
        super("Метод штрафных функций");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1380, 820);
        setMinimumSize(new Dimension(1100, 700));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        add(buildInputPanel(), BorderLayout.NORTH);
        add(buildCenterPanel(), BorderLayout.CENTER);

        table.setRowHeight(28);
        table.setRowSelectionAllowed(true);
        table.setColumnSelectionAllowed(true);
        table.setCellSelectionEnabled(true);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setFocusable(true);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = table.getSelectedRow();
                if (row >= 0) {
                    chartPanel.setSelectedIteration(row);
                }
            }
        });
    }

    private JPanel buildInputPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Параметры"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        JButton btnRun = new JButton("Старт");
        JButton btnReset = new JButton("Очистить");

        btnRun.addActionListener(e -> runCalculation());
        btnReset.addActionListener(e -> resetForm());

        addComponent(panel, gbc, 0, 0, 1, new JLabel("X0:"));
        addComponent(panel, gbc, 1, 0, 3, buildStartPointPanel());
        addComponent(panel, gbc, 0, 1, 1, new JLabel("Доп. параметры:"));
        addComponent(panel, gbc, 1, 1, 3, buildAdvancedParamsPanel());
        addComponent(panel, gbc, 0, 2, 1, new JLabel("Модель задачи:"));
        addComponent(panel, gbc, 1, 2, 3, lbModel);
        addComponent(panel, gbc, 0, 3, 1, btnRun);
        addComponent(panel, gbc, 1, 3, 1, btnReset);
        addComponent(panel, gbc, 0, 4, 4, lbStatus);

        return panel;
    }

    private JPanel buildStartPointPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        panel.setOpaque(false);
        panel.add(new JLabel("x1:"));
        panel.add(tfX1);
        panel.add(new JLabel("x2:"));
        panel.add(tfX2);
        return panel;
    }

    private JPanel buildAdvancedParamsPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        panel.setOpaque(false);
        panel.add(new JLabel("μ:"));
        panel.add(tfMu);
        panel.add(new JLabel("ε:"));
        panel.add(tfEpsilon);
        return panel;
    }

    private JSplitPane buildCenterPanel() {
        chartPanel.setBorder(BorderFactory.createTitledBorder("Схема метода"));

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Журнал итераций"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chartPanel, scrollPane);
        splitPane.setDividerLocation(460);
        return splitPane;
    }

    private void runCalculation() {
        Double x1 = parseDoubleOrNull(tfX1.getText());
        Double x2 = parseDoubleOrNull(tfX2.getText());
        Double mu = parseDoubleOrNull(tfMu.getText());
        Double epsilon = parseDoubleOrNull(tfEpsilon.getText());

        if (x1 == null || x2 == null || mu == null || epsilon == null) {
            JOptionPane.showMessageDialog(
                    this,
                    "Проверьте ввод x1, x2, mu и epsilon. Все значения должны быть числовыми.",
                    "Ошибка ввода",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        PenaltyMethodRequest request = new PenaltyMethodRequest(x1, x2, mu, epsilon);
        currentResult = penaltyFunctionMethod.solve(request);
        refillTable(currentResult);
        chartPanel.updateData(currentResult);
        lbStatus.setText(currentResult.summary());

        if (table.getRowCount() > 0) {
            table.setRowSelectionInterval(0, 0);
        }
    }

    private void refillTable(PenaltyMethodResult result) {
        tableModel.setRowCount(0);
        for (PenaltyIterationData iteration : result.iterations()) {
            tableModel.addRow(new Object[] {
                    iteration.iteration(),
                    format(iteration.mu()),
                    point(iteration.x1(), iteration.x2()),
                    format(iteration.fx()),
                    format(iteration.alpha()),
                    format(iteration.theta()),
                    format(iteration.muAlpha())
            });
        }
    }

    private void resetForm() {
        tfX1.setText(DEFAULT_X1);
        tfX2.setText(DEFAULT_X2);
        tfMu.setText(DEFAULT_MU);
        tfEpsilon.setText(DEFAULT_EPSILON);
        lbStatus.setText(DEFAULT_STATUS);
        tableModel.setRowCount(0);
        currentResult = null;
        chartPanel.clear();
    }

    private static void addComponent(JPanel panel, GridBagConstraints gbc, int x, int y, int width, Component component) {
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.gridwidth = width;
        gbc.weightx = width > 1 ? 1.0 : 0.0;
        panel.add(component, gbc);
    }

    private static Double parseDoubleOrNull(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String format(double value) {
        return String.format(Locale.US, "%.6f", value);
    }

    private static String point(double x1, double x2) {
        return "(" + format(x1) + ", " + format(x2) + ")";
    }
}
