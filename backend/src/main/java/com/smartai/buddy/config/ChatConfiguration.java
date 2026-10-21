package com.smartai.buddy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ChatConfiguration {

	@Bean
	RestClient.Builder chatRestClientBuilder() {
		return RestClient.builder();
	}
}
