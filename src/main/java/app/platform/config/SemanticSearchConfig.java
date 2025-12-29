package app.platform.config;

import app.core.projectconfig.ProjectConfigPort;
import app.core.search.SemanticSearchPort;
import app.core.vectorstore.VectorStorePort;
import app.platform.adapters.search.LocalSemanticSearchAdapter;
import app.platform.adapters.search.OpenAIResponsesSemanticSearchAdapter;
import app.platform.openai.OpenAISettingsResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;

@Configuration
public class SemanticSearchConfig {
  @Bean
  @ConditionalOnBean(OpenAIClient.class)
  @Conditional(OpenAIVectorStoreIdConfiguredCondition.class)
  public SemanticSearchPort openAISemanticSearchPort(
      OpenAIClient client, OpenAISettingsResolver resolver, ObjectMapper objectMapper) {
    return new OpenAIResponsesSemanticSearchAdapter(client, resolver, objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean(SemanticSearchPort.class)
  public SemanticSearchPort localSemanticSearchPort(
      ProjectConfigPort projectConfigPort, VectorStorePort vectorStorePort) {
    return new LocalSemanticSearchAdapter(projectConfigPort, vectorStorePort);
  }

  static class OpenAIVectorStoreIdConfiguredCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
      if (context.getBeanFactory() == null) return false;
      try {
        ProjectConfigPort projectConfigPort =
            context.getBeanFactory().getBean(ProjectConfigPort.class);
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
