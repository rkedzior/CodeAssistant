package app.platform.delivery.web;

import app.core.projectconfig.ProjectConfigMode;
import jakarta.validation.constraints.NotNull;

public class ProjectSetupForm {
  @NotNull private ProjectConfigMode mode = ProjectConfigMode.LOCAL;

  private String openaiApiKey;
  private String openaiModel;
  private String openaiVectorStoreId;

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

  public String getOpenaiModel() {
    return openaiModel;
  }

  public void setOpenaiModel(String openaiModel) {
    this.openaiModel = openaiModel;
  }

  public String getOpenaiVectorStoreId() {
    return openaiVectorStoreId;
  }

  public void setOpenaiVectorStoreId(String openaiVectorStoreId) {
    this.openaiVectorStoreId = openaiVectorStoreId;
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
