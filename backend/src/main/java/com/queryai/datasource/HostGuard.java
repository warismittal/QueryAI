package com.queryai.datasource;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Basic SSRF protection for user-supplied connection hosts: rejects configured blocked hosts and link-local
 * addresses (which include cloud metadata services such as 169.254.169.254).
 */
public class HostGuard {
    private final Set<String> blocked;

    public HostGuard(List<String> blockedHosts) {
        this.blocked = blockedHosts == null ? Set.of() : blockedHosts.stream()
                .map(h -> h.trim().toLowerCase(Locale.ROOT)).filter(h -> !h.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    /** @return null when the host is acceptable, otherwise a human readable reason. */
    public String check(String host) {
        if (host == null || host.isBlank()) {
            return "Host is required";
        }
        String h = host.trim().toLowerCase(Locale.ROOT);
        if (!h.matches("[a-z0-9._:\\-\\[\\]]+")) {
            return "Host contains invalid characters";
        }
        if (blocked.contains(h)) {
            return "Connections to " + host + " are blocked by the administrator";
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(h)) {
                if (address.isLinkLocalAddress() || blocked.contains(address.getHostAddress())) {
                    return "Connections to link-local or blocked addresses (" + address.getHostAddress() + ") are not allowed";
                }
            }
        } catch (UnknownHostException e) {
            return null; // reported later as a DNS failure by the diagnostics
        }
        return null;
    }
}
