package org.dcs.web.intg.osgi;

import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.List;

import org.dcs.api.service.ModulesApiService;
import org.dcs.osgi.FrameworkService;
import org.dcs.web.MockFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by cmathew on 19/01/16.
 */
//FIXME: Enable once the e2e test environment
//       is configured
@Ignore
public class FrameworkServiceIT {

  private static final Logger logger = LoggerFactory.getLogger(FrameworkServiceIT.class);

  private static FrameworkService frameworkService;

  List<String> serviceClassNames = Arrays.asList(ModulesApiService.class.getName());

  @BeforeClass
  public static void setup() throws Exception {
    frameworkService = new FrameworkService(MockFactory.getMockServletContext(FrameworkServiceIT.class, "dcs"));
    frameworkService.start();
  }

  @AfterClass
  public static void cleanup() {
    frameworkService.stop();
  }

  @Test
  public void testApiServicesLoad() {
    for(String serviceClassName : serviceClassNames) {
      ModulesApiService service =
              (ModulesApiService) FrameworkService.getService(serviceClassName);
      assertNotNull(service);
    }
  }

}
