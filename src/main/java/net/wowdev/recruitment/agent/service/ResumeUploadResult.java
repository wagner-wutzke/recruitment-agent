package net.wowdev.recruitment.agent.service;

import net.wowdev.recruitment.agent.entity.ResumeDocument;

public record ResumeUploadResult(ResumeDocument resume, boolean created) {}
