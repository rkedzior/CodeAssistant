package app.platform.delivery.web;

import app.core.projectconfig.ProjectConfigMode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class ProjectSetupFormValidator implements Validator {
  private static final String GITHUB_REPO_PATTERN = "^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$";

  @Override
  public boolean supports(Class<?> clazz) {
    return ProjectSetupForm.class.isAssignableFrom(clazz);
  }

  @Override
  public void validate(Object target, Errors errors) {
    ProjectSetupForm form = (ProjectSetupForm) target;
    if (form.getMode() == null) {
      return;
    }

    if (form.getMode() == ProjectConfigMode.LOCAL) {
      if (isBlank(form.getLocalRepoPath())) {
        errors.rejectValue("localRepoPath", "localRepoPath.required", "Local repository path is required.");
        return;
      }

      Path repoPath = Path.of(form.getLocalRepoPath());
      if (!Files.isDirectory(repoPath)) {
        errors.rejectValue(
            "localRepoPath", "localRepoPath.invalid", "Local repository path must be an existing directory.");
        return;
      }

      if (!Files.isDirectory(repoPath.resolve(".git"))) {
        errors.rejectValue(
            "localRepoPath",
            "localRepoPath.notGit",
            "Local repository path must contain a .git directory.");
      }
    }

    if (form.getMode() == ProjectConfigMode.GITHUB) {
      if (isBlank(form.getGithubRepo())) {
        errors.rejectValue("githubRepo", "githubRepo.required", "GitHub repository is required.");
      } else if (!form.getGithubRepo().matches(GITHUB_REPO_PATTERN)) {
        errors.rejectValue("githubRepo", "githubRepo.invalid", "GitHub repository must match owner/name.");
      }

      if (isBlank(form.getGithubToken())) {
        errors.rejectValue("githubToken", "githubToken.required", "GitHub token is required.");
      }
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}

