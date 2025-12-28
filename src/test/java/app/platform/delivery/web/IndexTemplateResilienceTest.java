package app.platform.delivery.web;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import app.core.indexing.IndexJobState;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ActiveProfiles;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

@SpringBootTest
@ActiveProfiles("test")
class IndexTemplateResilienceTest {
  @Autowired private SpringTemplateEngine templateEngine;

  @Test
  void indexTemplate_rendersWhenConfiguredIsMissing() {
    MockServletContext servletContext = new MockServletContext();
    MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
    MockHttpServletResponse response = new MockHttpServletResponse();

    JakartaServletWebApplication application =
        JakartaServletWebApplication.buildApplication(servletContext);
    IWebExchange exchange = application.buildExchange(request, response);
    WebContext context = new WebContext(exchange, Locale.ENGLISH); // no "configured"
    context.setVariable("job", IndexJobState.idle());

    String html = templateEngine.process("index", context);
    assertThat(html, containsString("Project is not configured yet."));
  }
}
