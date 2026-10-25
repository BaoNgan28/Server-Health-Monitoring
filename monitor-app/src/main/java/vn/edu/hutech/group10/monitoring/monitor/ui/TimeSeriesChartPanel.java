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

    private static final Color[] DARK_SERIES_COLORS = {
            new Color(0x38, 0xBD, 0xF8), // Sky Blue (CPU)
            new Color(0xC0, 0x84, 0xFC), // Purple (RAM)
            new Color(0x10, 0xB9, 0x81), // Emerald (Net IN)
            new Color(0xF5, 0x9E, 0x0B)  // Amber (Net OUT)
    };

    private static final Color[] LIGHT_SERIES_COLORS = {
            new Color(0x1F, 0x77, 0xB4), // Original Blue
            new Color(0xD6, 0x27, 0x28), // Original Red
            new Color(0x2C, 0xA0, 0x2C), // Original Green
            new Color(0xFF, 0x7F, 0x0E)  // Original Orange
    };

    private final TimeSeriesCollection dataset = new TimeSeriesCollection();
    private final Map<String, TimeSeries> series = new LinkedHashMap<>();
    private final int maximumItemCount;
    private final JFreeChart chart;
    private final String[] seriesNames;

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
        this.seriesNames = seriesNames;

        for (String name : seriesNames) {
            TimeSeries timeSeries = new TimeSeries(name);
            timeSeries.setMaximumItemCount(maximumItemCount);
            series.put(name, timeSeries);
            dataset.addSeries(timeSeries);
        }

        chart = ChartFactory.createTimeSeriesChart(title, "Time", valueAxisLabel,
                dataset, seriesNames.length > 1, true, false);
        chart.setAntiAlias(true);

        XYPlot plot = chart.getXYPlot();
        DateAxis domainAxis = (DateAxis) plot.getDomainAxis();
        domainAxis.setAutoRange(true);

        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        if (fixedRange) {
            rangeAxis.setRange(0.0d, 100.0d);
        } else {
            rangeAxis.setAutoRangeIncludesZero(true);
        }

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        plot.setRenderer(renderer);

        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(520, 220));
        chartPanel.setMouseWheelEnabled(false);
        chartPanel.setDomainZoomable(false);
        chartPanel.setRangeZoomable(false);
        add(chartPanel, BorderLayout.CENTER);

        applyTheme(UiConstants.isDarkMode());
    }

    /** Applies Dark or Light theme colors to the JFreeChart instance. */
    public void applyTheme(boolean isDark) {
        Color bg = isDark ? UiConstants.DARK_CARD_BACKGROUND : Color.WHITE;
        Color plotBg = isDark ? UiConstants.DARK_PANEL_BACKGROUND : new Color(0xFA, 0xFA, 0xFA);
        Color gridColor = isDark ? new Color(0x24, 0x33, 0x47) : new Color(0xDD, 0xDD, 0xDD);
        Color borderColor = isDark ? UiConstants.DARK_CARD_BORDER : new Color(0xDD, 0xDD, 0xDD);
        Color textColor = isDark ? UiConstants.DARK_TEXT_PRIMARY : new Color(0x22, 0x22, 0x22);
        Color textMuted = isDark ? UiConstants.DARK_TEXT_MUTED : new Color(0x66, 0x66, 0x66);
        Color[] colors = isDark ? DARK_SERIES_COLORS : LIGHT_SERIES_COLORS;

        chart.setBackgroundPaint(bg);
        if (chart.getTitle() != null) {
            chart.getTitle().setPaint(textColor);
            chart.getTitle().setFont(UiConstants.TITLE_FONT);
        }
        if (chart.getLegend() != null) {
            chart.getLegend().setBackgroundPaint(bg);
            chart.getLegend().setItemPaint(textColor);
            chart.getLegend().setItemFont(UiConstants.LABEL_FONT);
        }

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(plotBg);
        plot.setDomainGridlinePaint(gridColor);
        plot.setRangeGridlinePaint(gridColor);
        plot.setOutlinePaint(borderColor);

        DateAxis domainAxis = (DateAxis) plot.getDomainAxis();
        domainAxis.setLabelPaint(textMuted);
        domainAxis.setLabelFont(UiConstants.LABEL_FONT);
        domainAxis.setTickLabelPaint(textMuted);
        domainAxis.setTickLabelFont(UiConstants.LABEL_FONT);
        domainAxis.setAxisLinePaint(borderColor);
        domainAxis.setTickMarkPaint(borderColor);

        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setLabelPaint(textMuted);
        rangeAxis.setLabelFont(UiConstants.LABEL_FONT);
        rangeAxis.setTickLabelPaint(textMuted);
        rangeAxis.setTickLabelFont(UiConstants.LABEL_FONT);
        rangeAxis.setAxisLinePaint(borderColor);
        rangeAxis.setTickMarkPaint(borderColor);

        XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
        float strokeWidth = isDark ? 2.2f : 1.8f;
        for (int i = 0; i < seriesNames.length; i++) {
            renderer.setSeriesPaint(i, colors[i % colors.length]);
            renderer.setSeriesStroke(i, new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        }

        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, 1),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        repaint();
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
