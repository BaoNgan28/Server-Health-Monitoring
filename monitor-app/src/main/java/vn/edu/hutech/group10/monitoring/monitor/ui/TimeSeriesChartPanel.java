package vn.edu.hutech.group10.monitoring.monitor.ui;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A time-series chart that is built once and then only fed data.
 *
 * <p>Two things keep this cheap enough to update every second: the chart, plot
 * and renderer objects are created in the constructor and never replaced, and
 * each series has a maximum item count, so old points fall off the left instead
 * of accumulating for the lifetime of the process.
 *
 * <p>All methods must be called on the Swing event dispatch thread.
 */
public class TimeSeriesChartPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final Color[] SERIES_COLORS = {
            new Color(0x1F77B4), new Color(0xD62728), new Color(0x2CA02C), new Color(0xFF7F0E)
    };

    private final TimeSeriesCollection dataset = new TimeSeriesCollection();
    private final Map<String, TimeSeries> series = new LinkedHashMap<>();
    private final int maximumItemCount;
    private final JFreeChart chart;

    /**
     * @param title            chart title
     * @param valueAxisLabel   label of the value axis
     * @param maximumItemCount how many points each series keeps
     * @param fixedRange       0..100 axis when true, auto-ranging when false
     * @param seriesNames      one line per name, in legend order
     */
    public TimeSeriesChartPanel(String title, String valueAxisLabel, int maximumItemCount,
                                boolean fixedRange, String... seriesNames) {
        super(new BorderLayout());
        this.maximumItemCount = maximumItemCount;

        for (String name : seriesNames) {
            TimeSeries timeSeries = new TimeSeries(name);
            timeSeries.setMaximumItemCount(maximumItemCount);
            series.put(name, timeSeries);
            dataset.addSeries(timeSeries);
        }

        chart = ChartFactory.createTimeSeriesChart(title, "Time", valueAxisLabel,
                dataset, seriesNames.length > 1, true, false);
        chart.setBackgroundPaint(Color.WHITE);

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(new Color(0xFAFAFA));
        plot.setDomainGridlinePaint(new Color(0xDDDDDD));
        plot.setRangeGridlinePaint(new Color(0xDDDDDD));

        DateAxis domainAxis = (DateAxis) plot.getDomainAxis();
        domainAxis.setAutoRange(true);

        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        if (fixedRange) {
            rangeAxis.setRange(0.0d, 100.0d);
        } else {
            rangeAxis.setAutoRangeIncludesZero(true);
        }

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        for (int i = 0; i < seriesNames.length; i++) {
            renderer.setSeriesPaint(i, SERIES_COLORS[i % SERIES_COLORS.length]);
            renderer.setSeriesStroke(i, new BasicStroke(1.8f));
        }
        plot.setRenderer(renderer);

        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(520, 220));
        chartPanel.setMouseWheelEnabled(false);
        // Zooming a live chart only fights the auto-ranging domain axis.
        chartPanel.setDomainZoomable(false);
        chartPanel.setRangeZoomable(false);
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        add(chartPanel, BorderLayout.CENTER);
    }

    /**
     * Adds or replaces the point at {@code timestamp}.
     *
     * <p>{@code addOrUpdate} rather than {@code add}: two samples can share a
     * millisecond, and {@code add} would throw on the duplicate.
     */
    public void addPoint(String seriesName, long timestamp, double value) {
        TimeSeries target = series.get(seriesName);
        if (target == null) {
            return;
        }
        target.addOrUpdate(new Millisecond(new Date(timestamp)), value);
    }

    /** Replaces the contents of one series, used when a history range is loaded. */
    public void setPoints(String seriesName, long[] timestamps, double[] values) {
        TimeSeries target = series.get(seriesName);
        if (target == null) {
            return;
        }
        target.setNotify(false);
        try {
            target.clear();
            int count = Math.min(timestamps.length, values.length);
            // Keep only the newest points when a long range has more than the window.
            int start = Math.max(0, count - maximumItemCount);
            for (int i = start; i < count; i++) {
                target.addOrUpdate(new Millisecond(new Date(timestamps[i])), values[i]);
            }
        } finally {
            target.setNotify(true);
        }
    }

    /** Empties every series, for example when another agent is selected. */
    public void clear() {
        for (TimeSeries timeSeries : series.values()) {
            timeSeries.clear();
        }
    }

    public JFreeChart getChart() {
        return chart;
    }

    public int getMaximumItemCount() {
        return maximumItemCount;
    }
}
