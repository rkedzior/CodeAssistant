package app.platform.config;

import app.core.git.GitPort;
import app.core.indexing.StartInitialIndexUseCase;
import app.core.projectstate.ProjectStatePort;
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
      @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
    return new StartInitialIndexUseCase(gitPort, projectStatePort, taskExecutor);
  }
}

