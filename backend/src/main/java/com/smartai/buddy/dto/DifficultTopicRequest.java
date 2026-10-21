package com.smartai.buddy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DifficultTopicRequest(
	@NotBlank @Size(max = 200) String topic
) {
}
