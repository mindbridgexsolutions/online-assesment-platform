package com.mindbridgex.assessment.util;

import com.mindbridgex.assessment.config.AppProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Formatting helpers exposed to JSPs as ${fmt.xxx(...)} - JSTL cannot format java.time.Instant directly. */
@Component
public class Fmt {

    private final ZoneId zone;
    private final DateTimeFormatter dateTime;
    private final DateTimeFormatter date;
    private final DateTimeFormatter time;
    private final DateTimeFormatter input = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    public Fmt(AppProperties props) {
        this.zone = props.zone();
        this.dateTime = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH).withZone(zone);
        this.date = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH).withZone(zone);
        this.time = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH).withZone(zone);
    }

    public String dateTime(Instant i) {
        return i == null ? "-" : dateTime.format(i);
    }

    public String date(Instant i) {
        return i == null ? "-" : date.format(i);
    }

    public String time(Instant i) {
        return i == null ? "-" : time.format(i);
    }

    /** Value for an HTML datetime-local input, in the application time zone. */
    public String input(Instant i) {
        return i == null ? "" : input.format(i.atZone(zone));
    }

    /** Value for a datetime-local input from a zone-less local date-time (seconds dropped). */
    public String local(java.time.LocalDateTime t) {
        return t == null ? "" : input.format(t);
    }

    public String zoneName() {
        return zone.getId();
    }

    public long millis(Instant i) {
        return i == null ? 0 : i.toEpochMilli();
    }

    /** 2.50 -> "2.5", 3.00 -> "3". */
    public String num(BigDecimal n) {
        if (n == null) {
            return "-";
        }
        BigDecimal s = n.stripTrailingZeros();
        return (s.scale() < 0 ? s.setScale(0) : s).toPlainString();
    }

    public String pct(BigDecimal n) {
        return n == null ? "-" : num(n) + "%";
    }

    public String duration(long totalSeconds) {
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%02d:%02d", m, s);
    }
}
