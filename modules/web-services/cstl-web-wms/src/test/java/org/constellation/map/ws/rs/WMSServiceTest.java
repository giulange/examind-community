/*
 *    Constellation - An open source and standard compliant SDI
 *    http://www.constellation-sdi.org
 *
 * Copyright 2014 Geomatys.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.constellation.map.ws.rs;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.xml.namespace.QName;
import org.apache.sis.referencing.CRS;
import org.apache.sis.util.logging.Logging;
import org.constellation.admin.SpringHelper;
import org.constellation.business.IDataBusiness;
import org.constellation.business.ILayerBusiness;
import org.constellation.business.IProviderBusiness;
import org.constellation.business.IServiceBusiness;
import org.constellation.configuration.ConfigDirectory;
import org.constellation.dto.service.config.wxs.LayerContext;
import org.constellation.exception.ConstellationException;
import org.constellation.map.core.QueryContext;
import org.constellation.test.utils.SpringTestRunner;
import org.constellation.test.utils.TestEnvironment;
import org.constellation.test.utils.TestEnvironment.TestResource;
import org.constellation.ws.IWSEngine;
import org.constellation.ws.Worker;
import org.constellation.ws.rs.AbstractWebService;
import org.geotoolkit.wms.xml.GetFeatureInfo;
import org.geotoolkit.wms.xml.GetMap;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opengis.geometry.Envelope;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;

import static org.constellation.test.utils.TestEnvironment.initDataDirectory;
import static org.junit.Assert.assertEquals;

/**
 * Testing wms service value parsing.
 *
 * @author Johann Sorel (Geomatys)
 */
@RunWith(SpringTestRunner.class)
@TestExecutionListeners({DependencyInjectionTestExecutionListener.class,DirtiesContextTestExecutionListener.class})
@DirtiesContext(hierarchyMode = DirtiesContext.HierarchyMode.EXHAUSTIVE,classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(inheritInitializers = false, locations={"classpath:/cstl/spring/test-context.xml"})
public class WMSServiceTest {

    private static final Logger LOGGER = Logging.getLogger("org.constellation.map.ws.rs");

    @Inject
    private IServiceBusiness serviceBusiness;
    @Inject
    protected ILayerBusiness layerBusiness;
    @Inject
    protected IProviderBusiness providerBusiness;
    @Inject
    protected IDataBusiness dataBusiness;
    @Inject
    private IWSEngine wsengine;

    private static final double DELTA = 0.00000001;
    private static WMSService service;

    private static final Map<String, String[]> kvpMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private static boolean initialized = false;

    private static final String confDirName = "WMSServiceTest" + UUID.randomUUID().toString();

    @BeforeClass
    public static void start() {
        ConfigDirectory.setupTestEnvironement(confDirName);
    }

    @AfterClass
    public static void releas(){
        SpringHelper.closeApplicationContext();
    }

    @PostConstruct
    public void init() {
        if (!initialized) {
            try {
                layerBusiness.removeAll();
                serviceBusiness.deleteAll();
                dataBusiness.deleteAll();
                providerBusiness.removeAll();

                final TestEnvironment.TestResources testResource = initDataDirectory();

                // coverage-file datastore
                Integer pid = testResource.createProvider(TestResource.PNG, providerBusiness);
                Integer d = dataBusiness.create(new QName("SSTMDE200305"), pid, "COVERAGE", false, true, null, null);

                pid = testResource.createProvider(TestResource.WMS111_SHAPEFILES, providerBusiness);

                Integer d1  = dataBusiness.create(new QName("BuildingCenters"), pid, "VECTOR", false, true, true, null, null);
                Integer d2  = dataBusiness.create(new QName("BasicPolygons"),   pid, "VECTOR", false, true, true, null, null);
                Integer d3  = dataBusiness.create(new QName("Bridges"),         pid, "VECTOR", false, true, true, null, null);
                Integer d4  = dataBusiness.create(new QName("Streams"),         pid, "VECTOR", false, true, true, null, null);
                Integer d5  = dataBusiness.create(new QName("Lakes"),           pid, "VECTOR", false, true, true, null, null);
                Integer d6  = dataBusiness.create(new QName("NamedPlaces"),     pid, "VECTOR", false, true, true, null, null);
                Integer d7  = dataBusiness.create(new QName("Buildings"),       pid, "VECTOR", false, true, true, null, null);
                Integer d8  = dataBusiness.create(new QName("RoadSegments"),    pid, "VECTOR", false, true, true, null, null);
                Integer d9  = dataBusiness.create(new QName("DividedRoutes"),   pid, "VECTOR", false, true, true, null, null);
                Integer d10 = dataBusiness.create(new QName("Forests"),         pid, "VECTOR", false, true, true, null, null);
                Integer d11 = dataBusiness.create(new QName("MapNeatline"),     pid, "VECTOR", false, true, true, null, null);
                Integer d12 = dataBusiness.create(new QName("Ponds"),           pid, "VECTOR", false, true, true, null, null);

                final LayerContext config = new LayerContext();

                Integer defId = serviceBusiness.create("wms", "default", config, null, null);
                layerBusiness.add(d,    null,  null,                        "SSTMDE200305",     defId, null);
                layerBusiness.add(d1,   null, "http://www.opengis.net/gml", "BuildingCenters",  defId, null);
                layerBusiness.add(d2,   null, "http://www.opengis.net/gml", "BasicPolygons",    defId, null);
                layerBusiness.add(d3,   null, "http://www.opengis.net/gml", "Bridges",          defId, null);
                layerBusiness.add(d4,   null, "http://www.opengis.net/gml", "Streams",          defId, null);
                layerBusiness.add(d5,   null, "http://www.opengis.net/gml", "Lakes",            defId, null);
                layerBusiness.add(d6,   null, "http://www.opengis.net/gml", "NamedPlaces",      defId, null);
                layerBusiness.add(d7,   null, "http://www.opengis.net/gml", "Buildings",        defId, null);
                layerBusiness.add(d8,   null, "http://www.opengis.net/gml", "RoadSegments",     defId, null);
                layerBusiness.add(d9,   null, "http://www.opengis.net/gml", "DividedRoutes",    defId, null);
                layerBusiness.add(d10,  null, "http://www.opengis.net/gml", "Forests",          defId, null);
                layerBusiness.add(d11,  null, "http://www.opengis.net/gml", "MapNeatline",      defId, null);
                layerBusiness.add(d12,  null, "http://www.opengis.net/gml", "Ponds",            defId, null);

                serviceBusiness.start(defId);

                // let the worker start
                Thread.sleep(2000);
                service = new WMSService();
                initialized = true;
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }
    }

    @PreDestroy
    public static void end(){
        SpringHelper.closeApplicationContext();
    }

    @AfterClass
    public static void finish() {
        service.destroy();
        try {
            final ILayerBusiness layerBean = SpringHelper.getBean(ILayerBusiness.class);
            if (layerBean != null) {
                layerBean.removeAll();
            }
            final IServiceBusiness service = SpringHelper.getBean(IServiceBusiness.class);
            if (service != null) {
                service.deleteAll();
            }
            final IDataBusiness dataBean = SpringHelper.getBean(IDataBusiness.class);
            if (dataBean != null) {
                dataBean.deleteAll();
            }
            final IProviderBusiness provider = SpringHelper.getBean(IProviderBusiness.class);
            if (provider != null) {
                provider.removeAll();
            }
            ConfigDirectory.shutdownTestEnvironement(confDirName);
        } catch (ConstellationException ex) {
            LOGGER.log(Level.WARNING, ex.getMessage());
        }
    }

    public void setFields() throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException {
        //do not use this in real code, just for testing
        Field privateStringField = AbstractWebService.class.getDeclaredField("postKvpParameters");
        privateStringField.setAccessible(true);
        ThreadLocal<Map<String, String[]>> postKvpParameters = (ThreadLocal<Map<String, String[]>>) privateStringField.get(service);
        kvpMap.put("serviceId", new String[]{"default"});
        postKvpParameters.set(kvpMap);
    }

    private GetMap callGetMap() throws IllegalAccessException, IllegalArgumentException,
                                       InvocationTargetException, NoSuchMethodException{

        //do not use this in real code, just for testing
        final Worker worker = wsengine.getInstance("WMS", "default");
        Assert.assertNotNull(worker);
        final Method adaptGetMapMethod = WMSService.class.getDeclaredMethod(
                "adaptGetMap", boolean.class, QueryContext.class, Worker.class);
        adaptGetMapMethod.setAccessible(true);
        final GetMap getMap = (GetMap)adaptGetMapMethod.invoke(service, true, new QueryContext(), worker);
        return getMap;
    }

    private GetFeatureInfo callGetFeatureInfo() throws IllegalAccessException, IllegalArgumentException,
                                       InvocationTargetException, NoSuchMethodException{
        //do not use this in real code, just for testing
        final Worker worker = wsengine.getInstance("WMS", "default");
        Assert.assertNotNull(worker);
        final Method adaptGetMapMethod = WMSService.class.getDeclaredMethod(
                "adaptGetFeatureInfo", QueryContext.class, Worker.class);
        adaptGetMapMethod.setAccessible(true);
        final GetFeatureInfo getFI = (GetFeatureInfo)adaptGetMapMethod.invoke(service, new QueryContext(), worker);
        return getFI;
    }


    /**
     * TODO must test :
     * - Errors returned when missing parameters
     * - Version 1.1 and 1.3
     * - Dim_Range value
     */
    @Test
    public void testAdaptGetMap() throws Exception {
        addAndCheckGetMapParams(false);
    }

    private GetMap addAndCheckGetMapParams(final boolean isGetFeatureInfo) throws Exception {
        kvpMap.clear();
        kvpMap.put("AZIMUTH", new String[] {"49"});
        kvpMap.put("BBOX", new String[] {"-4000,-150,3200,560"});
        kvpMap.put("CRS", new String[] {"EPSG:3395"});
        kvpMap.put("ELEVATION", new String[] {"156.789"});
        kvpMap.put("FORMAT", new String[] {"image/png"});
        kvpMap.put("HEIGHT", new String[] {"600"});
        kvpMap.put("LAYERS", new String[] {"BlueMarble"});
        kvpMap.put("STYLES", new String[] {""});
        kvpMap.put("TIME", new String[] {"2007-06-23T14:31:56"});
        kvpMap.put("WIDTH", new String[] {"800"});
        kvpMap.put("VERSION", new String[] {"1.3.0"});

        if (isGetFeatureInfo) {
            kvpMap.put("I", new String[] {"230"});
            kvpMap.put("J", new String[] {"315"});
            kvpMap.put("QUERY_LAYERS", new String[] {"BlueMarble"});
        }
        setFields();

        final GetMap parsedQuery = isGetFeatureInfo? callGetFeatureInfo() : callGetMap();

        //azimuth
        assertEquals(49, parsedQuery.getAzimuth(), DELTA);

        //elevation
        assertEquals(156.789d, parsedQuery.getElevation().doubleValue(), DELTA);

        //time
        Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT+0"));
        cal.set(Calendar.YEAR, 2007);
        cal.set(Calendar.MONTH, 05);
        cal.set(Calendar.DAY_OF_MONTH, 23);
        cal.set(Calendar.HOUR_OF_DAY, 14);
        cal.set(Calendar.MINUTE, 31);
        cal.set(Calendar.SECOND, 56);
        cal.set(Calendar.MILLISECOND, 0);
        Date time = cal.getTime();


        //envelope 2D
        Envelope env2D = parsedQuery.getEnvelope2D();
        final CoordinateReferenceSystem crs2d = env2D.getCoordinateReferenceSystem();
        assertEquals(CRS.forCode("EPSG:3395"), crs2d);
        assertEquals(-4000d, env2D.getMinimum(0),DELTA);
        assertEquals(-150d, env2D.getMinimum(1),DELTA);
        assertEquals(3200d, env2D.getMaximum(0),DELTA);
        assertEquals(560d, env2D.getMaximum(1),DELTA);

        //envelope 4D
        final List<Date> times = parsedQuery.getTime();
        final Date[] dates = new Date[2];
        if (times != null && !times.isEmpty()) {
            dates[0] = times.get(0);
            dates[1] = times.get(times.size()-1);
        }

        /*
        DO NOT work anymore

        Envelope env4D = ReferencingUtilities.combine(parsedQuery.getEnvelope2D(), dates, new Double[]{parsedQuery.getElevation(), parsedQuery.getElevation()});
        CoordinateReferenceSystem crs = env4D.getCoordinateReferenceSystem();
        assertEquals(4, crs.getCoordinateSystem().getDimension());
        CoordinateReferenceSystem crs2D = CRS.getHorizontalComponent(crs);
        assertTrue(Utilities.equalsIgnoreMetadata(CRS.forCode("EPSG:3395"), crs2D));
        assertEquals(-4000d, env4D.getMinimum(0),DELTA);
        assertEquals(-150d, env4D.getMinimum(1),DELTA);
        assertEquals(3200d, env4D.getMaximum(0),DELTA);
        assertEquals(560d, env4D.getMaximum(1),DELTA);
        assertEquals(156.789d, env4D.getMinimum(2), DELTA);
        assertEquals(156.789d, env4D.getMaximum(2), DELTA);
        assertEquals(time.getTime(), env4D.getMinimum(3), DELTA);
        assertEquals(time.getTime(), env4D.getMaximum(3), DELTA);
        */
        return parsedQuery;
    }

    @Test
    public void testAdaptGetFeatureInfo() throws Exception{

        //mouse coordinate
        final GetFeatureInfo gfi = (GetFeatureInfo) addAndCheckGetMapParams(true);
        assertEquals(230, gfi.getX());
        assertEquals(315, gfi.getY());

        kvpMap.clear();
        kvpMap.put("AZIMUTH", new String[] {"49"});
        kvpMap.put("BBOX", new String[] {"-4000,-150,3200,560"});
        kvpMap.put("CRS", new String[] {"EPSG:3395"});
        kvpMap.put("ELEVATION", new String[] {"156.789"});
        kvpMap.put("FORMAT", new String[] {"image/png"});
        kvpMap.put("HEIGHT", new String[] {"600"});
        kvpMap.put("I", new String[] {"230"});
        kvpMap.put("J", new String[] {"315"});
        kvpMap.put("LAYERS", new String[] {"BlueMarble"});
        kvpMap.put("QUERY_LAYERS", new String[] {"BlueMarble"});
        kvpMap.put("STYLES", new String[] {""});
        kvpMap.put("TIME", new String[] {"2007-06-23T14:31:56"});
        kvpMap.put("WIDTH", new String[] {"800"});
        kvpMap.put("VERSION", new String[] {"1.3.0"});
        setFields();

        final GetFeatureInfo parsedQuery = callGetFeatureInfo();

        //azimuth
        assertEquals(49, parsedQuery.getAzimuth(), DELTA);

        //elevation
        assertEquals(156.789d, parsedQuery.getElevation().doubleValue(), DELTA);

        //time
        Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT+0"));
        cal.set(Calendar.YEAR, 2007);
        cal.set(Calendar.MONTH, 05);
        cal.set(Calendar.DAY_OF_MONTH, 23);
        cal.set(Calendar.HOUR_OF_DAY, 14);
        cal.set(Calendar.MINUTE, 31);
        cal.set(Calendar.SECOND, 56);
        cal.set(Calendar.MILLISECOND, 0);
        Date time = cal.getTime();
        assertEquals(time, parsedQuery.getTime().get(0));

        //envelope 2D
        Envelope env2D = parsedQuery.getEnvelope2D();
        assertEquals(CRS.forCode("EPSG:3395"), env2D.getCoordinateReferenceSystem());
        assertEquals(-4000d, env2D.getMinimum(0),DELTA);
        assertEquals(-150d, env2D.getMinimum(1),DELTA);
        assertEquals(3200d, env2D.getMaximum(0),DELTA);
        assertEquals(560d, env2D.getMaximum(1),DELTA);

        //envelope 4D
        final List<Date> times = parsedQuery.getTime();
        final Date[] dates = new Date[2];
        if (times != null && !times.isEmpty()) {
            dates[0] = times.get(0);
            dates[1] = times.get(times.size()-1);
        }

         /*
        DO NOT work anymore

        Envelope env4D = ReferencingUtilities.combine(parsedQuery.getEnvelope2D(), dates, new Double[]{parsedQuery.getElevation(), parsedQuery.getElevation()});
        CoordinateReferenceSystem crs = env4D.getCoordinateReferenceSystem();
        assertEquals(4, crs.getCoordinateSystem().getDimension());
        CoordinateReferenceSystem crs2D = CRS.getHorizontalComponent(crs);
        assertTrue(Utilities.equalsIgnoreMetadata(CRS.forCode("EPSG:3395"), crs2D));
        assertEquals(-4000d, env4D.getMinimum(0),DELTA);
        assertEquals(-150d, env4D.getMinimum(1),DELTA);
        assertEquals(3200d, env4D.getMaximum(0),DELTA);
        assertEquals(560d, env4D.getMaximum(1),DELTA);
        assertEquals(156.789d, env4D.getMinimum(2), DELTA);
        assertEquals(156.789d, env4D.getMaximum(2), DELTA);
        assertEquals(time.getTime(), env4D.getMinimum(3), DELTA);
        assertEquals(time.getTime(), env4D.getMaximum(3), DELTA);*/

        //mouse coordinate
        assertEquals(230, parsedQuery.getX());
        assertEquals(315, parsedQuery.getY());

    }
}