package app.platform.config;

import app.core.analysis.LlmPort;
import app.core.analysis.RunAnalysisUseCase;
import app.core.projectconfig.ProjectConfigPort;
import app.core.projectstate.ProjectStatePort;
import app.core.search.SemanticSearchPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AnalysisConfig {
  @Bean
  public RunAnalysisUseCase runAnalysisUseCase(
      ProjectConfigPort projectConfigPort,
      ProjectStatePort projectStatePort,
      SemanticSearchPort semanticSearchPort,
      LlmPort llmPort) {
    return new RunAnalysisUseCase(projectConfigPort, projectStatePort, semanticSearchPort, llmPort);
  }
}

