package com.smartai.buddy.dto;

import com.smartai.buddy.entity.LearningActivityType;
import jakarta.validation.constraints.NotNull;

public record StartLearningActivityRequest(@NotNull LearningActivityType activityType) {
}
