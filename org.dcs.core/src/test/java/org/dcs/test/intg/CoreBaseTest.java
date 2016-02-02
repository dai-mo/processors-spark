package org.dcs.test.intg;

import org.dcs.api.data.DataManager;
import org.dcs.api.data.impl.DataManagerImpl;
import org.dcs.api.service.DataApiService;
import org.dcs.api.service.impl.DataApiServiceImpl;
import org.dcs.api.utils.DataManagerUtils;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Before;
import org.junit.BeforeClass;

import javax.inject.Inject;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * Created by cmathew on 01/02/16.
 */
public class CoreBaseTest {


  @Inject
  protected DataManager dataManager;


  public static List<Class<?>> getClassesToAdd() {
    return null;
  }


  public static JavaArchive createBaseDeployment() {
    return ShrinkWrap.create(JavaArchive.class)
            .addClass(DataManager.class)
            .addClass(DataManagerImpl.class)
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
  }

  @Before
  public void testDeleteDataHomeDirContents() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    assertTrue(new File(dataManager.getDataHomePath()).exists());
    DataManagerUtils.deleteDirContents(new File(dataManager.getDataHomePath()));
    assertTrue(new File(dataManager.getDataHomePath()).listFiles().length == 0);
  }
}