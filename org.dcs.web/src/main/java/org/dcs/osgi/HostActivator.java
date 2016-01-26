package org.dcs.osgi;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by cmathew on 16/01/16.
 */
public class HostActivator implements BundleActivator
{
  private BundleContext bundleContext = null;

  private Map<String, ServiceTracker> classNameTrackerMap;

  public void start(BundleContext context) throws Exception
  {
    bundleContext = context;
    classNameTrackerMap = new HashMap<>();
  }

  public void stop(BundleContext context)
  {
    bundleContext = null;
    for(ServiceTracker tracker : classNameTrackerMap.values()) {
      tracker.close();
    }
  }

  public BundleContext getContext() {
    return bundleContext;
  }

  public Object getService(String className) {
    ServiceTracker tracker = classNameTrackerMap.get(className);
    if(tracker == null) {
      tracker = new ServiceTracker(
              bundleContext,
              className,
              null);
      tracker.open();
    }
    Object service = tracker.getService();
    if(service != null) {
      classNameTrackerMap.put(className, tracker);
    }
    return service;
  }
}