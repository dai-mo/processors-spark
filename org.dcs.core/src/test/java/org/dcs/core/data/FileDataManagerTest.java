package org.dcs.core.data;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;

import org.apache.commons.io.FileUtils;
import org.dcs.api.RESTException;
import org.dcs.api.model.ErrorCode;
import org.dcs.api.utils.DataManagerUtils;
import org.dcs.config.ConfigurationFacade;
import org.dcs.config.DataConfiguration;
import org.dcs.test.DataUtils;
import org.dcs.test.ReflectionUtils;
import org.dcs.test.intg.CoreBaseTest;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by cmathew on 27/01/16.
 */
@RunWith(Arquillian.class)
public class FileDataManagerTest extends CoreBaseTest {

  static final Logger logger = LoggerFactory.getLogger(FileDataManagerTest.class);
  
  DataConfiguration dataConfiguration = ConfigurationFacade.getCurrentDataConfiguration();

  @Deployment
  public static JavaArchive createDeployment() {
    return createBaseDeployment();
  }


  @Before
  public void testDeleteDataHomeDirContents() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    assertTrue(new File(dataConfiguration.getDataHomePath()).exists());
    DataManagerUtils.deleteDirContents(new File(dataConfiguration.getDataHomePath()));
    assertTrue(new File(dataConfiguration.getDataHomePath()).listFiles().length == 0);
  }
  
  @Test
  public void testManageDataHomeDirectory() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, IOException, RESTException {

    DataManagerUtils.createDirectory(new File(dataConfiguration.getDataRootPath()));

    // check load of data
    InputStream inputStream = DataUtils.getInputResourceAsStream(this.getClass(), "/test.csv");
    dataManager.load(inputStream, "test.csv");

    File dataSourceFile = new File(dataConfiguration.getDataHomePath() + "/test.csv/test.csv");
    assertTrue(dataSourceFile.exists());

    File dataInputFile = new File(DataUtils.getDataInputAbsolutePath(this.getClass())  + "/valid-test.csv");
    assertEquals(FileUtils.readLines(dataInputFile), FileUtils.readLines(dataSourceFile));
    
    try {
    	dataManager.load(inputStream, "test.csv");
    	fail("DataManagerException should be thrown here");
    } catch(RESTException dme) {
    	assertEquals(ErrorCode.DCS101(), dme.getErrorCode());
    }
    
    File dataSourceDir = new File(dataConfiguration.getDataHomePath() + "/test.csv");
    assertTrue(dataManager.delete("test.csv"));
    assertFalse(dataSourceDir.exists());
    
  }

}
