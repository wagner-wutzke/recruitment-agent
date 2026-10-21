package net.wowdev.recruitment.agent.service.resume.model;

import net.wowdev.recruitment.agent.entity.ResumeDocument;

public record ResumeUploadResult(ResumeDocument resume, boolean created) {}
