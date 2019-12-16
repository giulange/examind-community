/*
 *    Geotoolkit - An Open Source Java GIS Toolkit
 *    http://www.geotoolkit.org
 *
 *    (C) 2014, Geomatys
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */
package org.constellation.store.observation.db;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.sis.internal.storage.AbstractFeatureSet;
import org.apache.sis.internal.storage.StoreResource;
import org.apache.sis.internal.system.DefaultFactories;
import org.apache.sis.metadata.iso.DefaultMetadata;
import org.apache.sis.parameter.Parameters;
import org.apache.sis.storage.Aggregate;
import org.apache.sis.storage.DataStore;
import org.apache.sis.storage.DataStoreException;
import org.apache.sis.storage.DataStoreProvider;
import org.apache.sis.storage.Resource;
import org.apache.sis.storage.WritableFeatureSet;
import org.apache.sis.storage.event.StoreEvent;
import org.apache.sis.storage.event.StoreListener;
import org.apache.sis.storage.event.StoreListeners;
import org.apache.sis.util.logging.Logging;
import org.constellation.api.CommonConstants;
import org.geotoolkit.storage.event.FeatureStoreContentEvent;
import org.geotoolkit.data.om.OMFeatureTypes;
import static org.geotoolkit.data.om.OMFeatureTypes.*;
import org.geotoolkit.data.om.xml.XmlObservationUtils;
import org.geotoolkit.feature.FeatureExt;
import org.geotoolkit.filter.identity.DefaultFeatureId;
import org.geotoolkit.gml.GMLUtilities;
import org.geotoolkit.gml.xml.AbstractGeometry;
import org.geotoolkit.gml.xml.AbstractRing;
import org.geotoolkit.gml.xml.Envelope;
import org.geotoolkit.gml.xml.LineString;
import org.geotoolkit.gml.xml.Point;
import org.geotoolkit.gml.xml.Polygon;
import org.geotoolkit.storage.feature.GenericNameIndex;
import org.geotoolkit.jdbc.DBCPDataSource;
import org.geotoolkit.jdbc.ManageableDataSource;
import org.geotoolkit.observation.ObservationFilter;
import org.geotoolkit.observation.ObservationFilterReader;
import org.geotoolkit.observation.ObservationReader;
import org.geotoolkit.observation.ObservationStore;
import org.geotoolkit.observation.ObservationWriter;
import org.geotoolkit.observation.xml.AbstractObservation;
import org.geotoolkit.sampling.xml.SamplingFeature;
import org.geotoolkit.sos.netcdf.ExtractionResult;
import org.geotoolkit.sos.netcdf.GeoSpatialBound;
import org.geotoolkit.sos.xml.ResponseModeType;
import org.geotoolkit.storage.DataStores;
import org.geotoolkit.swe.xml.PhenomenonProperty;
import org.geotoolkit.util.NamesExt;
import org.locationtech.jts.io.WKBWriter;
import org.opengis.feature.Feature;
import org.opengis.feature.FeatureType;
import org.opengis.filter.FilterFactory;
import org.opengis.filter.Id;
import org.opengis.filter.identity.FeatureId;
import org.opengis.geometry.Geometry;
import org.opengis.metadata.Metadata;
import org.opengis.observation.AnyFeature;
import org.opengis.observation.Observation;
import org.opengis.observation.Phenomenon;
import org.opengis.parameter.ParameterDescriptor;
import org.opengis.parameter.ParameterValueGroup;
import org.opengis.temporal.Instant;
import org.opengis.temporal.Period;
import org.opengis.temporal.TemporalGeometricPrimitive;
import org.opengis.temporal.TemporalObject;
import org.opengis.util.GenericName;

/**
 *
 * @author Guilhem Legal (Geomatys)
 */
public class SOSDatabaseObservationStore extends DataStore implements Aggregate, ObservationStore {

    private static final Logger LOGGER = Logging.getLogger("org.constellation.store.observation.db");

    private final String SQL_WRITE_SAMPLING_POINT;
    private final String SQL_GET_LAST_ID;
    private final Parameters parameters;

    private ObservationReader reader;
    private ObservationWriter writer;
    private ObservationFilterReader filter;
    private ManageableDataSource source;
    protected final String schemaPrefix;

    private final boolean isPostgres;
    protected final GenericNameIndex<FeatureType> types;
    private final List<Resource> components = new ArrayList<>();


    public SOSDatabaseObservationStore(final ParameterValueGroup params) throws DataStoreException {
        this.parameters = Parameters.castOrWrap(params);

        try {
            //create a datasource
            final BasicDataSource dataSource = new BasicDataSource();

            // some default data source behaviour
            dataSource.setPoolPreparedStatements(true);

            // driver
            final String driver = SOSDatabaseParamsUtils.getDriverClassName(params);
            dataSource.setDriverClassName(driver);
            isPostgres = driver.startsWith("org.postgresql");
            types = OMFeatureTypes.getFeatureTypes("SamplingPoint");

            // url
            dataSource.setUrl(SOSDatabaseParamsUtils.getJDBCUrl(params));

            // username
            final String user = (String) params.parameter(SOSDatabaseObservationStoreFactory.USER.getName().toString()).getValue();
            dataSource.setUsername(user);

            // password
            final String passwd = (String) params.parameter(SOSDatabaseObservationStoreFactory.PASSWD.getName().toString()).getValue();
            if (passwd != null) {
                dataSource.setPassword(passwd);
            }

            // some datastores might need this
            dataSource.setAccessToUnderlyingConnectionAllowed(true);

            source = new DBCPDataSource(dataSource);
            final Map<String,Object> properties = new HashMap<>();

            extractParameter(params, CommonConstants.PHENOMENON_ID_BASE, SOSDatabaseObservationStoreFactory.PHENOMENON_ID_BASE, properties);
            extractParameter(params, CommonConstants.OBSERVATION_ID_BASE, SOSDatabaseObservationStoreFactory.OBSERVATION_ID_BASE, properties);
            extractParameter(params, CommonConstants.OBSERVATION_TEMPLATE_ID_BASE, SOSDatabaseObservationStoreFactory.OBSERVATION_TEMPLATE_ID_BASE, properties);
            extractParameter(params, CommonConstants.SENSOR_ID_BASE, SOSDatabaseObservationStoreFactory.SENSOR_ID_BASE, properties);

            String sp =  (String) params.parameter(SOSDatabaseObservationStoreFactory.SCHEMA_PREFIX.getName().toString()).getValue();
            if (sp == null) {
                this.schemaPrefix = "";
            } else {
                this.schemaPrefix = sp;
            }

            // build database structure if needed
            buildDatasource();

            reader = new OM2ObservationReader(source, isPostgres, schemaPrefix, properties);
            writer = new OM2ObservationWriter(source, isPostgres, schemaPrefix, properties);
            filter = new OM2ObservationFilterReader(source, isPostgres, schemaPrefix, properties);
        } catch(IOException ex) {
            throw new DataStoreException(ex);
        }

        SQL_WRITE_SAMPLING_POINT = "INSERT INTO \"" + schemaPrefix + "om\".\"sampling_features\" VALUES(?,?,?,?,?,?)";
        SQL_GET_LAST_ID = "SELECT COUNT(*) FROM \"" + schemaPrefix + "om\".\"sampling_features\"";

        for (GenericName name : types.getNames()) {
            components.add(new FeatureView(name));
        }

    }

    private void extractParameter(final ParameterValueGroup params, String key, ParameterDescriptor<String> param, final Map<String,Object> properties) {
        final String value = (String) params.parameter(param.getName().toString()).getValue();
        if (value != null) {
            properties.put(key, value);
        }
    }

    @Override
    public DataStoreProvider getProvider() {
        return DataStores.getProviderById(SOSDatabaseObservationStoreFactory.NAME);
    }

    @Override
    public Metadata getMetadata() throws DataStoreException {
        return new DefaultMetadata();
    }

    @Override
    public Optional<ParameterValueGroup> getOpenParameters() {
        return Optional.of(parameters);
    }

    @Override
    public Collection<? extends Resource> components() throws DataStoreException {
        return components;
    }

    private Connection getConnection() throws SQLException{
        return source.getConnection();
    }

    public FeatureId getNewFeatureId() {
        Connection cnx = null;
        PreparedStatement stmtLastId = null;
        try {
            cnx = getConnection();
            stmtLastId = cnx.prepareStatement(SQL_GET_LAST_ID);
            final ResultSet result = stmtLastId.executeQuery();
            if (result.next()) {
                final int nb = result.getInt(1) + 1;
                return new DefaultFeatureId("sampling-point-"+nb);
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, null, ex);
        } finally {
            if (stmtLastId != null) {
                try {
                    stmtLastId.close();
                } catch (SQLException ex) {
                    LOGGER.log(Level.WARNING, null, ex);
                }
            }

            if (cnx != null) {
                try {
                    cnx.close();
                } catch (SQLException ex) {
                    LOGGER.log(Level.WARNING, null, ex);
                }
            }
        }
        return null;
    }

    ////////////////////////////////////////////////////////////////////////////
    // OBSERVATION STORE ///////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public Set<GenericName> getProcedureNames() {
        final Set<GenericName> names = new HashSet<>();
        try {
            for (String process : reader.getProcedureNames()) {
                names.add(NamesExt.create(process));
            }

        } catch (DataStoreException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
        return names;
    }

    @Override
    public ExtractionResult getResults() throws DataStoreException {
        return getResults(null, null, new HashSet<>(), new HashSet<>());
    }

    @Override
    public ExtractionResult getResults(final List<String> sensorIds) throws DataStoreException {
        return getResults(null, sensorIds, new HashSet<>(), new HashSet<>());
    }

    @Override
    public ExtractionResult getResults(String affectedSensorID, List<String> sensorIds) throws DataStoreException {
        return getResults(affectedSensorID, sensorIds, new HashSet<>(), new HashSet<>());
    }

    @Override
    public ExtractionResult getResults(final String affectedSensorId, final List<String> sensorIDs,
            final Set<Phenomenon> phenomenons, final Set<org.opengis.observation.sampling.SamplingFeature> samplingFeatures) throws DataStoreException {

        if (affectedSensorId != null) {
            LOGGER.warning("CSVObservation store does not allow to override sensor ID");
        }

        final ExtractionResult result = new ExtractionResult();
        result.spatialBound.initBoundary();

        final ObservationFilterReader currentFilter = (ObservationFilterReader) getFilter();
        currentFilter.setProcedure(sensorIDs);

        final List<Observation> observations = currentFilter.getObservations(Collections.emptyMap());
        for (Observation obs : observations) {
            final AbstractObservation o = (AbstractObservation)obs;
            final ExtractionResult.ProcedureTree procedure = new ExtractionResult.ProcedureTree(o.getProcedure().getHref(), "Component");
            if (sensorIDs == null || sensorIDs.contains(procedure.id)) {
                if (!result.procedures.contains(procedure)) {
                    result.procedures.add(procedure);
                }
                final PhenomenonProperty phenProp = o.getPropertyObservedProperty();
                final List<String> fields = XmlObservationUtils.getPhenomenonsFields(phenProp);
                for (String field : fields) {
                    if (!result.fields.contains(field)) {
                        result.fields.add(field);
                    }
                }
                final Phenomenon phen = XmlObservationUtils.getPhenomenons(phenProp);
                if (!result.phenomenons.contains(phen)) {
                    result.phenomenons.add(phen);
                }
                appendTime(obs.getSamplingTime(), result.spatialBound);
                appendTime(obs.getSamplingTime(), procedure.spatialBound);
                appendGeometry(obs.getFeatureOfInterest(), result.spatialBound);
                appendGeometry(obs.getFeatureOfInterest(), procedure.spatialBound);
                result.observations.add(o);
            }
        }


        return result;
    }

    @Override
    public List<ExtractionResult.ProcedureTree> getProcedures() throws DataStoreException {
        final List<ExtractionResult.ProcedureTree> result = new ArrayList<>();

        // TODO optimize we don't need to call the filter here
        final ObservationFilterReader currentFilter = (ObservationFilterReader) getFilter();
        currentFilter.initFilterObservation(ResponseModeType.INLINE, CommonConstants.OBSERVATION_QNAME, Collections.emptyMap());
        final List<Observation> observations = currentFilter.getObservations(Collections.emptyMap());
        for (Observation obs : observations) {
            final AbstractObservation o = (AbstractObservation)obs;
            final ExtractionResult.ProcedureTree procedure = new ExtractionResult.ProcedureTree(o.getProcedure().getHref(), "Component");

            if (!result.contains(procedure)) {
                result.add(procedure);
            }
            final PhenomenonProperty phenProp = o.getPropertyObservedProperty();
            final List<String> fields = XmlObservationUtils.getPhenomenonsFields(phenProp);
            for (String field : fields) {
                if (!procedure.fields.contains(field)) {
                    procedure.fields.add(field);
                }
            }
            appendTime(obs.getSamplingTime(), procedure.spatialBound);
            appendGeometry(obs.getFeatureOfInterest(), procedure.spatialBound);
        }
        return result;
    }

    private void appendTime(final TemporalObject time, final GeoSpatialBound spatialBound) {
        if (time instanceof Instant) {
            final Instant i = (Instant) time;
            spatialBound.addDate(i.getDate());
        } else if (time instanceof Period) {
            final Period p = (Period) time;
            spatialBound.addDate(p.getBeginning().getDate());
            spatialBound.addDate(p.getEnding().getDate());
        }
    }

    private void appendGeometry(final AnyFeature feature, final GeoSpatialBound spatialBound){
        if (feature instanceof SamplingFeature) {
            final SamplingFeature sf = (SamplingFeature) feature;
            final Geometry geom = sf.getGeometry();
            final AbstractGeometry ageom;
            if (geom instanceof AbstractGeometry) {
                ageom = (AbstractGeometry)geom;
            } else if (geom != null) {
                ageom = GMLUtilities.getGMLFromISO(geom);
            } else {
                ageom = null;
            }
            spatialBound.addGeometry(ageom);
            spatialBound.addGeometry(ageom);
            extractBoundary(ageom, spatialBound);
            extractBoundary(ageom, spatialBound);
        }
    }

    private void extractBoundary(final AbstractGeometry geom, final GeoSpatialBound spatialBound) {
        if (geom instanceof Point) {
            final Point p = (Point) geom;
            if (p.getPos() != null) {
                spatialBound.addXCoordinate(p.getPos().getOrdinate(0));
                spatialBound.addYCoordinate(p.getPos().getOrdinate(1));
            }
        } else if (geom instanceof LineString) {
            final LineString ls = (LineString) geom;
            final Envelope env = ls.getBounds();
            if (env != null) {
                spatialBound.addXCoordinate(env.getMinimum(0));
                spatialBound.addXCoordinate(env.getMaximum(0));
                spatialBound.addYCoordinate(env.getMinimum(1));
                spatialBound.addYCoordinate(env.getMaximum(1));
            }
        } else if (geom instanceof Polygon) {
            final Polygon p = (Polygon) geom;
            AbstractRing ext = p.getExterior().getAbstractRing();
            // TODO
        }
    }


    @Override
    public void close() throws DataStoreException {
        if (reader != null) reader.destroy();
        if (writer != null) writer.destroy();
        if (filter != null) filter.destroy();
    }

    @Override
    public Set<String> getPhenomenonNames() {
        try {
            return new HashSet(reader.getPhenomenonNames());
        } catch (DataStoreException ex) {
            LOGGER.log(Level.WARNING, "Error while retrieving phenomenons", ex);
        }
        return new HashSet<>();
    }

    @Override
    public TemporalGeometricPrimitive getTemporalBounds() throws DataStoreException {
        final ExtractionResult result = new ExtractionResult();
        result.spatialBound.initBoundary();
        appendTime(reader.getEventTime("2.0.0"), result.spatialBound);
        return result.spatialBound.getTimeObject("2.0.0");
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public ObservationReader getReader() {
        return reader;
    }

    @Override
    public ObservationWriter getWriter() {
        return writer;
    }

    @Override
    public ObservationFilter getFilter() {
        return new OM2ObservationFilterReader((OM2ObservationFilter) filter);
    }

    private boolean buildDatasource() throws DataStoreException {
        try {
            if (OM2DatabaseCreator.validConnection(source)) {
                if (OM2DatabaseCreator.isPostgisInstalled(source, true)) {
                    if (!OM2DatabaseCreator.structurePresent(source, schemaPrefix)) {
                        OM2DatabaseCreator.createObservationDatabase(source, true, null, schemaPrefix);
                        return true;
                    } else {
                        LOGGER.info("OM2 structure already present");
                    }
                    return true;
                } else {
                    LOGGER.warning("Missing Postgis extension.");
                }
            } else {
                LOGGER.warning("unable to connect OM datasource");
            }
            return false;
        } catch (SQLException | IOException ex) {
            throw new DataStoreException("Erro while building OM2 datasource", ex);
        }
    }

    private final class FeatureView extends AbstractFeatureSet implements StoreResource, WritableFeatureSet {

        private final StoreListeners listeners;
        private final GenericName name;

        FeatureView(GenericName name) {
            super(null);
            listeners = new StoreListeners(null, this);
            this.name = name;
        }

        @Override
        public synchronized FeatureType getType() throws DataStoreException {
            FeatureType type = types.get(name.toString());
            if (FeatureExt.getCRS(type) == null) {
                //read a first feature to find the crs
                try (final SOSDatabaseFeatureReader r = new SOSDatabaseFeatureReader(getConnection(), isPostgres, type, schemaPrefix)){
                    if (r.hasNext()) {
                        type = r.next().getType();
                        types.remove(type.getName());
                        types.add(type.getName(), type);
                    }
                } catch (SQLException ex) {
                    throw new DataStoreException("Error while building feature type from first record", ex);
                }
            }

            return types.get(name.toString());
        }

        @Override
        public DataStore getOriginator() {
            return SOSDatabaseObservationStore.this;
        }

        @Override
        public Stream<Feature> features(boolean parallel) throws DataStoreException {
            final FeatureType sft = getType();
            try {
                final SOSDatabaseFeatureReader reader = new SOSDatabaseFeatureReader(getConnection(), isPostgres, sft, schemaPrefix);
                final Spliterator<Feature> spliterator = Spliterators.spliteratorUnknownSize(reader, Spliterator.ORDERED);
                final Stream<Feature> stream = StreamSupport.stream(spliterator, false);
                return stream.onClose(reader::close);
            } catch (SQLException ex) {
                throw new DataStoreException(ex);
            }
        }

        @Override
        public void add(Iterator<? extends Feature> features) throws DataStoreException {
            final Set<FeatureId> result = new LinkedHashSet<>();

            try (Connection cnx = getConnection();
                 PreparedStatement stmtWrite = cnx.prepareStatement(SQL_WRITE_SAMPLING_POINT)) {

                while (features.hasNext()) {
                    final Feature feature = features.next();
                    FeatureId identifier = FeatureExt.getId(feature);
                    if (identifier == null || identifier.getID().isEmpty()) {
                        identifier = getNewFeatureId();
                    }

                    stmtWrite.setString(1, identifier.getID());
                    Collection<String> names = ((Collection)feature.getPropertyValue(ATT_NAME.toString()));
                    Collection<String> sampleds = ((Collection)feature.getPropertyValue(ATT_SAMPLED.toString()));
                    stmtWrite.setString(2, (String) names.iterator().next());
                    stmtWrite.setString(3, (String)feature.getPropertyValue(ATT_DESC.toString()));
                    stmtWrite.setString(4, sampleds.isEmpty() ? null : sampleds.iterator().next());
                    final Optional<org.locationtech.jts.geom.Geometry> geometry = FeatureExt.getDefaultGeometryValue(feature)
                            .filter(org.locationtech.jts.geom.Geometry.class::isInstance)
                            .map(org.locationtech.jts.geom.Geometry.class::cast);
                    if (geometry.isPresent()) {
                        final org.locationtech.jts.geom.Geometry geom = geometry.get();
                        WKBWriter writer = new WKBWriter();
                        final int SRID = geom.getSRID();
                        stmtWrite.setBytes(5, writer.write(geom));
                        stmtWrite.setInt(6, SRID);
                    } else {
                        stmtWrite.setNull(5, java.sql.Types.VARBINARY);
                        stmtWrite.setNull(6, java.sql.Types.INTEGER);
                    }
                    stmtWrite.executeUpdate();
                    result.add(identifier);
                }
            } catch (SQLException ex) {
                LOGGER.log(Level.WARNING, SQL_WRITE_SAMPLING_POINT, ex);
            }
            //todo return created ids
            //return result;
            Id id = DefaultFactories.forBuildin(FilterFactory.class).id(result);
            listeners.fire(new FeatureStoreContentEvent(this, FeatureStoreContentEvent.Type.ADD, getType().getName(), id), FeatureStoreContentEvent.class);
        }

        @Override
        public boolean removeIf(Predicate<? super Feature> filter) throws DataStoreException {
            boolean match = false;
            try (SOSDatabaseFeatureWriter writer = new SOSDatabaseFeatureWriter(getConnection(),isPostgres,getType(), schemaPrefix)) {
                while (writer.hasNext()) {
                    Feature feature = writer.next();
                    if (filter.test(feature)) {
                        writer.remove();
                        match = true;
                    }
                }
            } catch (SQLException ex) {
                Logger.getLogger(OM2FeatureStore.class.getName()).log(Level.SEVERE, null, ex);
            }
            return match;
        }

        @Override
        public void updateType(FeatureType newType) throws DataStoreException {
            throw new DataStoreException("Not supported.");
        }

        @Override
        public void replaceIf(Predicate<? super Feature> filter, UnaryOperator<Feature> updater) throws DataStoreException {
            throw new DataStoreException("Not supported.");
        }

        @Override
        public <T extends StoreEvent> void addListener(Class<T> eventType, StoreListener<? super T> listener) {
            listeners.addListener(eventType, listener);
        }

        @Override
        public synchronized <T extends StoreEvent> void removeListener(Class<T> eventType, StoreListener<? super T> listener) {
            listeners.removeListener(eventType, listener);
        }

    }
}
