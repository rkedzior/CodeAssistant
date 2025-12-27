package app.platform.delivery.web;

import app.core.projectconfig.ProjectConfigMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ProjectSetupForm {
  @NotNull private ProjectConfigMode mode = ProjectConfigMode.LOCAL;

  @NotBlank private String openaiApiKey;

  private String localRepoPath;
  private String githubRepo;
  private String githubToken;

  public ProjectConfigMode getMode() {
    return mode;
  }

  public void setMode(ProjectConfigMode mode) {
    this.mode = mode;
  }

  public String getOpenaiApiKey() {
    return openaiApiKey;
  }

  public void setOpenaiApiKey(String openaiApiKey) {
    this.openaiApiKey = openaiApiKey;
  }

  public String getLocalRepoPath() {
    return localRepoPath;
  }

  public void setLocalRepoPath(String localRepoPath) {
    this.localRepoPath = localRepoPath;
  }

  public String getGithubRepo() {
    return githubRepo;
  }

  public void setGithubRepo(String githubRepo) {
    this.githubRepo = githubRepo;
  }

  public String getGithubToken() {
    return githubToken;
  }

  public void setGithubToken(String githubToken) {
    this.githubToken = githubToken;
  }
}

