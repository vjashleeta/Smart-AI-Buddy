package com.smartai.buddy.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetRateLimiter {

	private static final Duration WINDOW = Duration.ofHours(1);
	private static final int MAX_REQUESTS_PER_IP = 10;
	private static final int MAX_TRACKED_IPS = 10_000;

	private final Map<String, RateWindow> windows = new HashMap<>();

	public synchronized boolean allow(String remoteAddress, Instant now) {
		windows.entrySet().removeIf(entry -> !entry.getValue().startedAt().plus(WINDOW).isAfter(now));
		RateWindow current = windows.get(remoteAddress);
		if (current == null) {
			if (windows.size() >= MAX_TRACKED_IPS) {
				return false;
			}
			windows.put(remoteAddress, new RateWindow(now, 1));
			return true;
		}
		if (current.requestCount() >= MAX_REQUESTS_PER_IP) {
			return false;
		}
		windows.put(remoteAddress, new RateWindow(current.startedAt(), current.requestCount() + 1));
		return true;
	}

	private record RateWindow(Instant startedAt, int requestCount) {
	}
}
