package app.core.projectconfig;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public record ProjectConfig(
    ProjectConfigMode mode,
    String openaiApiKey,
    String localRepoPath,
    String githubRepo,
    String githubToken) {}

