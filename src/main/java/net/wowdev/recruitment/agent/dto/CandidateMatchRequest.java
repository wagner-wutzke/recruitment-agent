package net.wowdev.recruitment.agent.dto;

import jakarta.validation.constraints.NotBlank;

public record CandidateMatchRequest(@NotBlank String jobOfferId) {}
