package com.mindbridgex.assessment.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/** Matches a client IP against rules such as "10.0.0.5" or "192.168.1.0/24" (IPv4 and IPv6 literals only, no DNS). */
public final class IpMatcher {

    private static final Pattern LITERAL = Pattern.compile("^[0-9a-fA-F:.]+$");

    private IpMatcher() {
    }

    public static boolean isValidRule(String rule) {
        try {
            parse(rule.trim());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** True when the allow-list is empty or any rule matches. */
    public static boolean allowed(String rules, String clientIp) {
        if (rules == null || rules.isBlank()) {
            return true;
        }
        if (clientIp == null) {
            return false;
        }
        byte[] ip;
        try {
            ip = addr(clientIp.trim());
        } catch (IllegalArgumentException e) {
            return false;
        }
        for (String r : rules.split("[,;\\s]+")) {
            if (r.isBlank()) {
                continue;
            }
            try {
                Object[] p = parse(r.trim());
                byte[] net = (byte[]) p[0];
                int bits = (Integer) p[1];
                if (net.length == ip.length && matches(net, ip, bits)) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // invalid rules never match
            }
        }
        return false;
    }

    private static Object[] parse(String rule) {
        String a = rule;
        int bits = -1;
        int slash = rule.indexOf('/');
        if (slash >= 0) {
            a = rule.substring(0, slash);
            try {
                bits = Integer.parseInt(rule.substring(slash + 1));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("bad prefix");
            }
        }
        byte[] b = addr(a);
        if (bits < 0) {
            bits = b.length * 8;
        }
        if (bits > b.length * 8) {
            throw new IllegalArgumentException("bad prefix");
        }
        return new Object[]{b, bits};
    }

    private static byte[] addr(String s) {
        if (s.isEmpty() || !LITERAL.matcher(s).matches()) {
            throw new IllegalArgumentException("not an IP literal");
        }
        try {
            return InetAddress.getByName(s).getAddress();
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("not an IP literal");
        }
    }

    private static boolean matches(byte[] net, byte[] ip, int bits) {
        int full = bits / 8;
        for (int i = 0; i < full; i++) {
            if (net[i] != ip[i]) {
                return false;
            }
        }
        int rem = bits % 8;
        if (rem == 0) {
            return true;
        }
        int mask = 0xFF << (8 - rem);
        return (net[full] & mask) == (ip[full] & mask);
    }
}
