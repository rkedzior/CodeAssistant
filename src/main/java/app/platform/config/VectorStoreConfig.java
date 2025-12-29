package app.platform.config;

import app.core.projectconfig.ProjectConfigPort;
import app.core.vectorstore.VectorStorePort;
import app.platform.adapters.vectorstore.FileSystemVectorStoreAdapter;
import app.platform.adapters.vectorstore.OpenAIVectorStoreAdapter;
import app.platform.openai.OpenAISettingsResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

@Configuration
@Profile("!test & !e2etest")
public class VectorStoreConfig {
  @Bean
  @ConditionalOnBean(OpenAIClient.class)
  @Conditional(OpenAIVectorStoreIdConfiguredCondition.class)
  public VectorStorePort openAIVectorStorePort(OpenAIClient client, OpenAISettingsResolver resolver) {
    String vectorStoreId = resolver.resolve().vectorStoreId();
    if (vectorStoreId == null) {
      throw new IllegalStateException("openaiVectorStoreId expected but not configured.");
    }
    return new OpenAIVectorStoreAdapter(client, vectorStoreId);
  }

  @Bean
  @ConditionalOnMissingBean(VectorStorePort.class)
  public VectorStorePort fileSystemVectorStorePort(
      ObjectMapper objectMapper,
      @Value("${codeassistant.vectorstore.path:.codeassistant/vectorstore}") String rootPath) {
    return new FileSystemVectorStoreAdapter(objectMapper, rootPath);
  }

  static class OpenAIVectorStoreIdConfiguredCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
      if (context.getBeanFactory() == null) return false;
      try {
        ProjectConfigPort projectConfigPort = context.getBeanFactory().getBean(ProjectConfigPort.class);
        return projectConfigPort
                .load()
                .map(c -> OpenAISettingsResolver.normalizeOptional(c.openaiVectorStoreId()))
                .orElse(null)
            != null;
      } catch (NoSuchBeanDefinitionException e) {
        return false;
      }
    }
  }
}

