package app.platform.config;

import app.core.git.GitPort;
import app.core.indexing.StartInitialIndexUseCase;
import app.core.indexing.TrackedFileClassifier;
import app.core.projectstate.ProjectStatePort;
import app.core.vectorstore.VectorStorePort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;

@Configuration
public class IndexingConfig {
  @Bean
  public StartInitialIndexUseCase startInitialIndexUseCase(
      GitPort gitPort,
      ProjectStatePort projectStatePort,
      VectorStorePort vectorStorePort,
      @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
    return new StartInitialIndexUseCase(
        gitPort,
        projectStatePort,
        vectorStorePort,
        new TrackedFileClassifier(projectStatePort),
        taskExecutor);
  }
}
