package com.mindbridgex.assessment.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IpMatcherTest {

    @Test
    void emptyRuleAllowsEveryone() {
        assertTrue(IpMatcher.allowed(null, "1.2.3.4"));
        assertTrue(IpMatcher.allowed("  ", "1.2.3.4"));
    }

    @Test
    void exactAndCidr() {
        assertTrue(IpMatcher.allowed("10.0.0.5", "10.0.0.5"));
        assertFalse(IpMatcher.allowed("10.0.0.5", "10.0.0.6"));
        assertTrue(IpMatcher.allowed("192.168.1.0/24", "192.168.1.200"));
        assertFalse(IpMatcher.allowed("192.168.1.0/24", "192.168.2.1"));
        assertTrue(IpMatcher.allowed("10.0.0.0/8, 172.16.0.0/12", "172.20.1.1"));
        assertTrue(IpMatcher.allowed("203.0.113.64/26", "203.0.113.100"));
        assertFalse(IpMatcher.allowed("203.0.113.64/26", "203.0.113.130"));
    }

    @Test
    void unknownClientIsRejectedWhenRestricted() {
        assertFalse(IpMatcher.allowed("10.0.0.0/8", null));
        assertFalse(IpMatcher.allowed("10.0.0.0/8", "not-an-ip"));
    }

    @Test
    void ipv6() {
        assertTrue(IpMatcher.allowed("2001:db8::/32", "2001:db8:1::1"));
        assertFalse(IpMatcher.allowed("2001:db8::/32", "2001:dead::1"));
        assertFalse(IpMatcher.allowed("10.0.0.0/8", "2001:db8::1"));
    }

    @Test
    void ruleValidation() {
        assertTrue(IpMatcher.isValidRule("10.0.0.0/8"));
        assertTrue(IpMatcher.isValidRule("127.0.0.1"));
        assertFalse(IpMatcher.isValidRule("example.com"));
        assertFalse(IpMatcher.isValidRule("10.0.0.0/40"));
        assertFalse(IpMatcher.isValidRule("10.0.0.0/x"));
    }
}
