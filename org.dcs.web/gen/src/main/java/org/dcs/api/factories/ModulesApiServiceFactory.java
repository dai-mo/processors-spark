package org.dcs.api.factories;

import org.dcs.api.ModulesApiService;
import org.dcs.api.impl.ModulesApiServiceImpl;

@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JaxRSServerCodegen", date = "2016-01-15T18:33:44.874+01:00")
public class ModulesApiServiceFactory {

   private final static ModulesApiService service = new ModulesApiServiceImpl();

   public static ModulesApiService getModulesApi()
   {
      return service;
   }
}