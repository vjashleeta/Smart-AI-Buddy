package com.smartai.buddy.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfiguration {

	@Bean
	Clock applicationClock(@Value("${smart-ai-buddy.time-zone:Asia/Kolkata}") String timeZone) {
		return Clock.system(ZoneId.of(timeZone));
	}
}
