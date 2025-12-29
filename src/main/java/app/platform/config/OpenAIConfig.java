package app.platform.config;

import app.core.projectconfig.ProjectConfigPort;
import app.platform.openai.OpenAISettingsResolver;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

@Configuration
@Profile("!test & !e2etest")
public class OpenAIConfig {
  @Bean
  @Conditional(OpenAIConfiguredCondition.class)
  public OpenAIClient openAIClient(OpenAISettingsResolver resolver) {
    OpenAISettingsResolver.ResolvedApiKey resolvedApiKey = resolver.resolveApiKey();
    if (resolvedApiKey.apiKey() == null) {
      throw new IllegalStateException("OpenAI API key expected but not configured.");
    }
    return OpenAIOkHttpClient.builder().apiKey(resolvedApiKey.apiKey()).build();
  }

  static class OpenAIConfiguredCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
      String envKey =
          OpenAISettingsResolver.normalizeOptional(context.getEnvironment().getProperty("OPENAI_API_KEY"));
      if (envKey != null) return true;

      String propertyKey =
          OpenAISettingsResolver.normalizeOptional(context.getEnvironment().getProperty("openai.api.key"));
      if (propertyKey != null) return true;

      if (context.getBeanFactory() == null) return false;
      try {
        ProjectConfigPort projectConfigPort = context.getBeanFactory().getBean(ProjectConfigPort.class);
        return projectConfigPort.load().map(c -> OpenAISettingsResolver.normalizeOptional(c.openaiApiKey())).orElse(null)
            != null;
      } catch (NoSuchBeanDefinitionException e) {
        return false;
      }
    }
  }
}

