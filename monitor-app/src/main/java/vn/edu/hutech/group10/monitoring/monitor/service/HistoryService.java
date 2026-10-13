package vn.edu.hutech.group10.monitoring.monitor.service;

import vn.edu.hutech.group10.monitoring.monitor.config.MonitorConfig;
import vn.edu.hutech.group10.monitoring.monitor.model.HistoryPoint;
import vn.edu.hutech.group10.monitoring.monitor.model.MetricAggregate;
import vn.edu.hutech.group10.monitoring.monitor.model.MetricSample;
import vn.edu.hutech.group10.monitoring.monitor.repository.MetricRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Answers history questions from the right table.
 *
 * <p>A short range is served from {@code metric_raw} and shows every sample; a
 * range older than the raw retention window has no raw rows left, so it is
 * served from {@code metric_5m}. The panel asks for a time range and gets back
 * {@link HistoryPoint}s either way - it never writes SQL and never needs to know
 * which table was used.
 */
public class HistoryService {

    private static final Logger LOG = Logger.getLogger(HistoryService.class.getName());

    /** The ranges offered in the history panel. */
    public enum Range {
        LAST_HOUR("Last 1 Hour", 60L * 60_000L),
        LAST_24_HOURS("Last 24 Hours", 24L * 60L * 60_000L),
        LAST_7_DAYS("Last 7 Days", 7L * 24L * 60L * 60_000L),
        LAST_30_DAYS("Last 30 Days", 30L * 24L * 60L * 60_000L);

        private final String label;
        private final long durationMs;

        Range(String label, long durationMs) {
            this.label = label;
            this.durationMs = durationMs;
        }

        public String getLabel() {
            return label;
        }

        public long getDurationMs() {
            return durationMs;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final MetricRepository metricRepository;
    private final MonitorConfig config;

    public HistoryService(MetricRepository metricRepository, MonitorConfig config) {
        this.metricRepository = metricRepository;
        this.config = config;
    }

    /** History of one agent over {@code range}, ending now. */
    public List<HistoryPoint> load(String agentId, Range range) {
        long now = System.currentTimeMillis();
        return load(agentId, now - range.getDurationMs(), now);
    }

    /**
     * History of one agent between two instants.
     *
     * <p>The raw table is used when the window starts inside the raw retention
     * period, because it is the only source with per-second detail. Otherwise the
     * aggregated table is used, and if that turns out to be empty - which happens
     * on a fresh database, before the first aggregation run - the raw table is
     * tried as a fallback so the panel is not blank.
     */
    public List<HistoryPoint> load(String agentId, long fromMs, long toMs) {
        if (agentId == null || agentId.isEmpty() || toMs <= fromMs) {
            return List.of();
        }
        long rawHorizon = System.currentTimeMillis() - config.getRawRetentionMs();

        if (fromMs >= rawHorizon) {
            List<HistoryPoint> points = fromSamples(metricRepository.findRaw(agentId, fromMs, toMs));
            LOG.fine("History for " + agentId + " served from metric_raw: " + points.size() + " point(s)");
            return points;
        }

        List<HistoryPoint> points = fromAggregates(metricRepository.findAggregates(agentId, fromMs, toMs));
        if (points.isEmpty()) {
            points = fromSamples(metricRepository.findRaw(agentId, fromMs, toMs));
            LOG.fine("History for " + agentId + " fell back to metric_raw: " + points.size() + " point(s)");
        } else {
            LOG.fine("History for " + agentId + " served from metric_5m: " + points.size() + " point(s)");
        }
        return points;
    }

    /** True when a range of this length is answered from the aggregated table. */
    public boolean usesAggregates(Range range) {
        return range.getDurationMs() > config.getRawRetentionMs();
    }

    private List<HistoryPoint> fromSamples(List<MetricSample> samples) {
        List<HistoryPoint> points = new ArrayList<>(samples.size());
        for (MetricSample sample : samples) {
            points.add(new HistoryPoint(sample.getTimestamp(), sample.getCpuUsage(),
                    sample.getMemoryUsage(), sample.getConnectionCount(),
                    sample.getNetworkInBytesPerSecond(), sample.getNetworkOutBytesPerSecond(),
                    false));
        }
        return points;
    }

    private List<HistoryPoint> fromAggregates(List<MetricAggregate> aggregates) {
        List<HistoryPoint> points = new ArrayList<>(aggregates.size());
        for (MetricAggregate aggregate : aggregates) {
            points.add(new HistoryPoint(aggregate.getBucketStart(), aggregate.getAvgCpu(),
                    aggregate.getAvgMemory(), aggregate.getAvgConnections(),
                    aggregate.getAvgNetworkInBps(), aggregate.getAvgNetworkOutBps(),
                    true));
        }
        return points;
    }
}
