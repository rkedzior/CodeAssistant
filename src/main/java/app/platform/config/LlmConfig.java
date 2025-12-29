package app.platform.config;

import app.core.analysis.LlmPort;
import app.platform.adapters.analysis.LocalLlmAdapter;
import app.platform.adapters.analysis.OpenAIResponsesLlmAdapter;
import app.platform.openai.OpenAISettingsResolver;
import com.openai.client.OpenAIClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmConfig {
  @Bean
  @ConditionalOnBean(OpenAIClient.class)
  public LlmPort openAiLlmPort(OpenAIClient client, OpenAISettingsResolver resolver) {
    return new OpenAIResponsesLlmAdapter(client, resolver);
  }

  @Bean
  @ConditionalOnMissingBean(LlmPort.class)
  public LlmPort localLlmPort(
      @Value("${codeassistant.llm.failOnce:false}") boolean failOnce,
      @Value("${codeassistant.llm.failAlways:false}") boolean failAlways) {
    return new LocalLlmAdapter(failOnce, failAlways);
  }
}
