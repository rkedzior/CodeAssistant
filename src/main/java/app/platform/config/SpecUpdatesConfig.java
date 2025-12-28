package app.platform.config;

import app.core.observations.ObservationsPort;
import app.core.spec.SpecStoragePort;
import app.core.specupdates.ApplySpecUpdatesUseCase;
import app.core.specupdates.ProposeSpecUpdatesUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpecUpdatesConfig {
  @Bean
  public ProposeSpecUpdatesUseCase proposeSpecUpdatesUseCase(
      ObservationsPort observationsPort, SpecStoragePort specStoragePort) {
    return new ProposeSpecUpdatesUseCase(observationsPort, specStoragePort);
  }

  @Bean
  public ApplySpecUpdatesUseCase applySpecUpdatesUseCase(
      ProposeSpecUpdatesUseCase proposeSpecUpdatesUseCase, SpecStoragePort specStoragePort) {
    return new ApplySpecUpdatesUseCase(proposeSpecUpdatesUseCase, specStoragePort);
  }
}
