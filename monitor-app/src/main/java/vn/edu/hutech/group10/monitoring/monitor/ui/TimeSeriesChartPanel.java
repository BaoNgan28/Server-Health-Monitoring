package vn.edu.hutech.group10.monitoring.monitor.ui;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.Date;
import javax.swing.SwingUtilities;

public class TimeSeriesChartPanel extends JPanel {
    private final TimeSeries series;
    private static final int MAX_POINTS = 60; // Không giữ vô hạn points

    public TimeSeriesChartPanel(String title, String yAxisLabel) {
        setLayout(new BorderLayout());
        
        series = new TimeSeries(title);
        series.setMaximumItemCount(MAX_POINTS);
        
        TimeSeriesCollection dataset = new TimeSeriesCollection(series);
        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                title, "Time", yAxisLabel, dataset, false, true, false
        );
        
        ChartPanel chartPanel = new ChartPanel(chart);
        add(chartPanel, BorderLayout.CENTER);
    }

    public void updateData(double value) {
        SwingUtilities.invokeLater(() -> {
            series.addOrUpdate(new Second(new Date()), value);
        });
    }
}