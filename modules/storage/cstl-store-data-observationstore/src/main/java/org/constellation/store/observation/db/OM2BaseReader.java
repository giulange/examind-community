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

package org.constellation.store.observation.db;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.namespace.QName;

import org.apache.sis.storage.DataStoreException;
import static org.constellation.api.CommonConstants.MEASUREMENT_QNAME;
import org.constellation.util.FilterSQLRequest;
import org.constellation.util.MultiFilterSQLRequest;
import org.constellation.util.Util;
import org.constellation.util.SQLResult;
import org.constellation.util.SingleFilterSQLRequest;
import org.geotoolkit.geometry.jts.JTS;
import static org.geotoolkit.observation.AbstractObservationStoreFactory.OBSERVATION_ID_BASE_NAME;
import static org.geotoolkit.observation.AbstractObservationStoreFactory.OBSERVATION_TEMPLATE_ID_BASE_NAME;
import static org.geotoolkit.observation.AbstractObservationStoreFactory.PHENOMENON_ID_BASE_NAME;
import static org.geotoolkit.observation.AbstractObservationStoreFactory.SENSOR_ID_BASE_NAME;
import org.geotoolkit.observation.OMUtils;
import static org.geotoolkit.observation.OMUtils.buildTime;
import static org.geotoolkit.observation.OMUtils.getOverlappingComposite;
import org.geotoolkit.observation.model.ComplexResult;
import org.geotoolkit.observation.model.CompositePhenomenon;
import org.geotoolkit.observation.model.Field;
import org.geotoolkit.observation.model.FieldType;
import org.geotoolkit.observation.model.MeasureResult;
import org.geotoolkit.observation.model.Offering;
import org.geotoolkit.observation.model.Phenomenon;
import org.geotoolkit.observation.model.Procedure;
import org.geotoolkit.observation.model.Result;
import org.geotoolkit.observation.model.ResultMode;
import org.geotoolkit.observation.model.SamplingFeature;
import static org.geotoolkit.observation.model.TextEncoderProperties.DEFAULT_ENCODING;
import org.geotoolkit.observation.result.ResultBuilder;

import org.opengis.metadata.quality.Element;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.temporal.TemporalGeometricPrimitive;
import org.opengis.util.FactoryException;

/**
 *
 * @author Guilhem Legal (Geomatys)
 */
public class OM2BaseReader {

    protected final boolean isPostgres;
    
    protected final boolean timescaleDB;

    /**
     * The base for observation id.
     */
    protected final String observationIdBase;

    protected final String phenomenonIdBase;

    protected final String sensorIdBase;

    protected final String observationTemplateIdBase;

    protected final String schemaPrefix;

    /**
     * Some sub-classes of the base reader are used in a single session (Observation filters).
     * So they can activate the cache to avoid reading the same object in the same session.
     */
    protected boolean cacheEnabled;

    /**
     * A map of already read sampling feature.
     *
     * This map is only populated if {@link OM2BaseReader#cacheEnabled} is set to true.
     */
    private final Map<String, SamplingFeature> cachedFoi = new HashMap<>();

    /**
     * A map of already read Phenomenon.
     *
     * This map is only populated if {@link OM2BaseReader#cacheEnabled} is set to true.
     */
    private final Map<String, Phenomenon> cachedPhenomenon = new HashMap<>();

    protected final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    protected final SimpleDateFormat format2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.S");

    protected Field DEFAULT_TIME_FIELD = new Field(-1, FieldType.TIME, "time", null, "http://www.opengis.net/def/property/OGC/0/SamplingTime", null);

    public OM2BaseReader(final Map<String, Object> properties, final String schemaPrefix, final boolean cacheEnabled, final boolean isPostgres, final boolean timescaleDB) throws DataStoreException {
        this.isPostgres = isPostgres;
        this.timescaleDB = timescaleDB;
        this.phenomenonIdBase = (String) properties.getOrDefault(PHENOMENON_ID_BASE_NAME, "");
        this.sensorIdBase = (String) properties.getOrDefault(SENSOR_ID_BASE_NAME, "");
        this.observationTemplateIdBase = (String) properties.getOrDefault(OBSERVATION_TEMPLATE_ID_BASE_NAME, "urn:observation:template:");
        this.observationIdBase         = (String) properties.getOrDefault(OBSERVATION_ID_BASE_NAME, "");
        if (schemaPrefix == null) {
            this.schemaPrefix = "";
        } else {
            if (Util.containsForbiddenCharacter(schemaPrefix)) {
                throw new DataStoreException("Invalid schema prefix value");
            }
            this.schemaPrefix = schemaPrefix;
        }
        this.cacheEnabled = cacheEnabled;
    }

    public OM2BaseReader(final OM2BaseReader that) {
        this.phenomenonIdBase          = that.phenomenonIdBase;
        this.observationTemplateIdBase = that.observationTemplateIdBase;
        this.sensorIdBase              = that.sensorIdBase;
        this.isPostgres                = that.isPostgres;
        this.observationIdBase         = that.observationIdBase;
        this.schemaPrefix              = that.schemaPrefix;
        this.cacheEnabled              = that.cacheEnabled;
        this.timescaleDB               = that.timescaleDB;
    }

    /**
     * use for debugging purpose
     */
    protected static final Logger LOGGER = Logger.getLogger("org.constellation.store.observation.db");

    /**
     * Extract a Feature of interest from its identifier.
     * If cache is enabled, it can be returned from it if already read.
     * 
     * @param id FOI identifier.
     * @param c A SQL connection.
     *
     * @return
     * @throws SQLException If an error occurs during query execution.
     * @throws DataStoreException If an error occurs during geometry instanciation.
     */
    protected SamplingFeature getFeatureOfInterest(final String id, final Connection c) throws SQLException, DataStoreException {
        if (cacheEnabled && cachedFoi.containsKey(id)) {
            return cachedFoi.get(id);
        }
        try {
            final String name;
            final String description;
            final String sampledFeature;
            final byte[] b;
            final int srid;
            final Map<String, Object> properties = readProperties("sampling_features_properties", "id_sampling_feature", id, c);
            try (final PreparedStatement stmt = (isPostgres) ?
                c.prepareStatement("SELECT \"id\", \"name\", \"description\", \"sampledfeature\", st_asBinary(\"shape\"), \"crs\" FROM \"" + schemaPrefix + "om\".\"sampling_features\" WHERE \"id\"=?") ://NOSONAR
                c.prepareStatement("SELECT * FROM \"" + schemaPrefix + "om\".\"sampling_features\" WHERE \"id\"=?")) {//NOSONAR
                stmt.setString(1, id);
                try (final ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        name = rs.getString(2);
                        description = rs.getString(3);
                        sampledFeature = rs.getString(4);
                        b = rs.getBytes(5);
                        srid = rs.getInt(6);
                    } else {
                        return null;
                    }
                }
                final CoordinateReferenceSystem crs = OM2Utils.parsePostgisCRS(srid);
                final Geometry geom;
                if (b != null) {
                    WKBReader reader = new WKBReader();
                    geom = reader.read(b);
                    JTS.setCRS(geom, crs);
                } else {
                    geom = null;
                }
                final SamplingFeature sf = new SamplingFeature(id, name, description, properties, sampledFeature, geom);
                if (cacheEnabled) {
                    cachedFoi.put(id, sf);
                }
                return sf;
            }

        } catch (ParseException | FactoryException ex) {
            throw new DataStoreException(ex.getMessage(), ex);
        }
    }

    /**
     * Read specified entity properties.
     *
     * @param tableName Properties table name, depending on the type of the entity.
     * @param columnName Column name of the entity identifier depending on the type of the entity.
     * @param id Entity identifier.
     * @param c A SQL connection.
     * 
     * @return A Map of properties.
     * @throws SQLException If an error occurs during query execution.
     */
    protected Map<String, Object> readProperties(String tableName, String columnName, String id, Connection c) throws SQLException {
        String request = "SELECT \"property_name\", \"value\" FROM \"" + schemaPrefix + "om\".\"" + tableName + "\" WHERE \"" + columnName + "\"=?";
        LOGGER.fine(request);
        Map<String, Object> results = new HashMap<>();
        try(final PreparedStatement stmt = c.prepareStatement(request)) {//NOSONAR
            stmt.setString(1, id);
            try (final ResultSet rs   = stmt.executeQuery()) {
                while (rs.next()) {
                    String pName = rs.getString("property_name");
                    String pValue = rs.getString("value");
                    results.put(pName, pValue);
                }
            }
        }
        return results;
    }

    @SuppressWarnings("squid:S2695")
    protected TemporalGeometricPrimitive getTimeForTemplate(Connection c, String procedure, String observedProperty, String foi) {
        String request = "SELECT min(\"time_begin\"), max(\"time_begin\"), max(\"time_end\") FROM \"" + schemaPrefix + "om\".\"observations\" WHERE \"procedure\"=?";
        if (observedProperty != null) {
             request = request + " AND (\"observed_property\"=? OR \"observed_property\" IN (SELECT \"phenomenon\" FROM \"" + schemaPrefix + "om\".\"components\" WHERE \"component\"=?))";
        }
        if (foi != null) {
            request = request + " AND \"foi\"=?";
        }
        LOGGER.fine(request);
        try(final PreparedStatement stmt = c.prepareStatement(request)) {//NOSONAR
            stmt.setString(1, procedure);
            int cpt = 2;
            if (observedProperty != null) {
                stmt.setString(cpt, observedProperty);
                cpt++;
                stmt.setString(cpt, observedProperty);
                cpt++;
            }
            if (foi != null) {
                stmt.setString(cpt, foi);
            }
            try (final ResultSet rs   = stmt.executeQuery()) {
                if (rs.next()) {
                    Date minBegin = rs.getTimestamp(1);
                    Date maxBegin = rs.getTimestamp(2);
                    Date maxEnd   = rs.getTimestamp(3);
                    if (minBegin != null && maxEnd != null && maxEnd.after(maxBegin)) {
                        return buildTime(procedure, minBegin, maxEnd);
                    } else if (minBegin != null && !minBegin.equals(maxBegin)) {
                        return buildTime(procedure, minBegin, maxBegin);
                    } else if (minBegin != null) {
                        return buildTime(procedure, minBegin, null);
                    }
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Error while looking for template time.", ex);
        }
        return null;
    }

    protected Set<Phenomenon> getAllPhenomenon(final Connection c) throws DataStoreException {
        try(final Statement stmt       = c.createStatement();
            final ResultSet rs         = stmt.executeQuery("SELECT \"id\" FROM \"" + schemaPrefix + "om\".\"observed_properties\" ")) {//NOSONAR
            final Set<Phenomenon> results = new HashSet<>();
            while (rs.next()) {
                results.add(getPhenomenon(rs.getString(1), c));
            }
            return results;
        } catch (SQLException ex) {
            throw new DataStoreException("Error while retrieving all phenomenons.", ex);
        }
    }

    protected Phenomenon getPhenomenon(final String observedProperty, final Connection c) throws DataStoreException {
        final String id;
        // cleanup phenomenon id because of its XML ype (NCName)
        if (observedProperty.startsWith(phenomenonIdBase)) {
            id = observedProperty.substring(phenomenonIdBase.length()).replace(':', '-');
        } else {
            id = observedProperty.replace(':', '-');
        }
        if (cacheEnabled && cachedPhenomenon.containsKey(id)) {
            return cachedPhenomenon.get(id);
        }
        try {
            // look for composite phenomenon
            try (final PreparedStatement stmt = c.prepareStatement("SELECT \"component\" FROM \"" + schemaPrefix + "om\".\"components\" WHERE \"phenomenon\"=? ORDER BY \"order\" ASC")) {//NOSONAR
                stmt.setString(1, observedProperty);
                try(final ResultSet rs = stmt.executeQuery()) {
                    final List<Phenomenon> phenomenons = new ArrayList<>();
                    while (rs.next()) {
                        final String phenID = rs.getString(1);
                        phenomenons.add(getSinglePhenomenon(phenID, c));
                    }
                    Phenomenon base = getSinglePhenomenon(observedProperty, c);
                    Phenomenon result = null;
                    if (base != null) {
                        if (phenomenons.isEmpty()) {
                            result = base;
                        } else {
                            result = new CompositePhenomenon(id, base.getName(), base.getDefinition(), base.getDescription(), base.getProperties(), phenomenons);
                        }
                        if (cacheEnabled) {
                            cachedPhenomenon.put(id, result);
                        }
                    }
                    return result;
                }
            }
        } catch (SQLException ex) {
            throw new DataStoreException(ex.getMessage(), ex);
        }
    }

     protected Phenomenon getPhenomenonForFields(final List<Field> fields, final Connection c) throws DataStoreException {
         FilterSQLRequest request = new SingleFilterSQLRequest("SELECT \"phenomenon\", COUNT(\"component\") FROM \"" + schemaPrefix + "om\".\"components\" ");
         request.append(" WHERE \"component\" IN (");
         request.appendValues(fields.stream().map(f -> f.name).toList());
         request.append(" ) AND \"phenomenon\" NOT IN (");
         request.append("SELECT cc.\"phenomenon\" FROM \"" + schemaPrefix + "om\".\"components\" cc WHERE ");
         for (Field field : fields) {
             request.append(" \"component\" <> ").appendValue(field.name).append(" AND ");
         }
         request.deleteLastChar(4);
         request.append(" ) ");
         request.append(" GROUP BY \"phenomenon\" HAVING COUNT(\"component\") = ").append(Integer.toString(fields.size()));

         String result = null;
         try (final SQLResult rs = request.execute(c)) {
             if (rs.next()) {
                 result = rs.getString(1);
             }
         } catch (SQLException ex) {
             throw new DataStoreException(ex.getMessage(), ex);
         }
         return (result != null) ? getPhenomenon(result, c) : null;
     }

    private Phenomenon getSinglePhenomenon(final String id, final Connection c) throws DataStoreException {
        try {
            final Map<String, Object> properties = readProperties("observed_properties_properties", "id_phenomenon", id, c);
            try (final PreparedStatement stmt = c.prepareStatement("SELECT * FROM \"" + schemaPrefix + "om\".\"observed_properties\" WHERE \"id\"=?")) {//NOSONAR
                stmt.setString(1, id);
                try(final ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String phenID = rs.getString(1);
                        String name = rs.getString(3);
                        String definition = rs.getString(4);
                        final String description = rs.getString(5);

                        // hack for valid phenomenon ID in 1.0.0 static fields
                        if (phenID != null) {
                            if (phenID.equals("http://mmisw.org/ont/cf/parameter/latitude")) {
                                name = "latitude";
                            } else if (phenID.equals("http://mmisw.org/ont/cf/parameter/longitude")) {
                                name = "longitude";
                            } else if (phenID.equals("http://www.opengis.net/def/property/OGC/0/SamplingTime")) {
                                name = "samplingTime";
                            }
                            if (name == null) {
                                name = phenID.startsWith(phenomenonIdBase) ? phenID.substring(phenomenonIdBase.length()) : phenID;
                            }
                        }
                        return new Phenomenon(phenID, name, definition, description, properties);
                    }
                }
            }
        } catch (SQLException ex) {
            throw new DataStoreException(ex.getMessage(), ex);
        }
        return null;
    }

     /**
     * Return the global phenomenon for a procedure.
     * We need this method because some procedure got multiple observation with only a phenomon component,
     * and not the full composite.
     * some other are registered with composite that are a subset of the global procedure phenomenon.
     *
     * @return
     */
    protected Phenomenon getGlobalCompositePhenomenon(Connection c, String procedure) throws DataStoreException {
       String request = "SELECT DISTINCT(\"observed_property\") FROM \"" + schemaPrefix + "om\".\"observations\" o "
                      + "WHERE \"procedure\"=? ";
       LOGGER.fine(request);
       try(final PreparedStatement stmt = c.prepareStatement(request)) {//NOSONAR
            stmt.setString(1, procedure);
            try (final ResultSet rs   = stmt.executeQuery()) {
                List<CompositePhenomenon> composites = new ArrayList<>();
                List<Phenomenon> singles = new ArrayList<>();
                while (rs.next()) {
                    Phenomenon phen = getPhenomenon(rs.getString("observed_property"), c);
                    if (phen instanceof CompositePhenomenon compo) {
                        composites.add(compo);
                    } else {
                        singles.add(phen);
                    }
                }
                if (composites.isEmpty()) {
                    if (singles.isEmpty()) {
                        // i don't think this will ever happen
                        return null;
                    } else if (singles.size() == 1) {
                        return singles.get(0);
                    } else  {
                        // multiple phenomenons are present, but no composite... TODO
                        return getVirtualCompositePhenomenon(c, procedure);
                    }
                } else if (composites.size() == 1) {
                    return composites.get(0);
                } else  {
                    // multiple composite phenomenons are present, we must choose the global one or create a virtual
                    try {
                        return (Phenomenon) getOverlappingComposite(composites);

                    // TODO replace tha catch when the method will return an optional
                    } catch (DataStoreException ex) {
                        return getVirtualCompositePhenomenon(c, procedure);
                    }
                }
            }
       } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Error while looking for global phenomenon.", ex);
            throw new DataStoreException("Error while looking for global phenomenon.");
       }
    }

    protected Phenomenon getVirtualCompositePhenomenon(Connection c, String procedure) throws DataStoreException {
       String request = "SELECT \"field_name\" FROM \"" + schemaPrefix + "om\".\"procedure_descriptions\" "
                      + "WHERE \"procedure\"=? "
                      + "AND NOT (\"order\"=1 AND \"field_type\"='Time') "
                      + "AND \"parent\" IS NULL "
                      + "order by \"order\"";
       LOGGER.fine(request);
       try(final PreparedStatement stmt = c.prepareStatement(request)) {//NOSONAR
            stmt.setString(1, procedure);
            try (final ResultSet rs   = stmt.executeQuery()) {
                List<Phenomenon> components = new ArrayList<>();
                int i = 0;
                while (rs.next()) {
                    final String fieldName = rs.getString("field_name");
                    Phenomenon phen = getPhenomenon(fieldName, c);
                    if (phen == null) {
                        throw new DataStoreException("Unable to link a procedure field to a phenomenon:" + fieldName);
                    }
                    components.add(phen);
                }
                if (components.size() == 1) {
                    return components.get(0);
                } else {
                    final String name = "computed-phen-" + procedure;
                    return new CompositePhenomenon(name, name, name, null, null, components);
                }
            }
       } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Error while building virtual composite phenomenon.", ex);
            throw new DataStoreException("Error while building virtual composite phenomenon.");
       }
    }

    protected Phenomenon createCompositePhenomenonFromField(Connection c, List<Field> fields) throws DataStoreException {
        if (fields == null || fields.size() < 2) {
            throw new DataStoreException("at least two fields are required for building composite phenomenon");
        }
        final List<Phenomenon> components = new ArrayList<>();
        for (Field field : fields) {
            final Phenomenon phen = getPhenomenon(field.name, c);
            if (phen == null) {
                throw new DataStoreException("Unable to link a field to a phenomenon: " + field.name);
            }
            components.add(phen);
        }
        final String name = "computed-phen-" + UUID.randomUUID().toString();
        return new CompositePhenomenon(name, name, name, null, null, components);
    }

    protected Offering readObservationOffering(final String offeringId, final Connection c) throws DataStoreException {
        final String id;
        final String name;
        final String description;
        final TemporalGeometricPrimitive time;
        final String procedure;
        final List<String> phens = new ArrayList<>();
        final List<String> foi       = new ArrayList<>();

        try(final PreparedStatement stmt = c.prepareStatement("SELECT * FROM \"" + schemaPrefix + "om\".\"offerings\" WHERE \"identifier\"=?")) {//NOSONAR
            stmt.setString(1, offeringId);
            try(final ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    id                 = rs.getString(1);
                    description        = rs.getString(2);
                    name               = rs.getString(3);
                    final Timestamp b  = rs.getTimestamp(4);
                    final Timestamp e  = rs.getTimestamp(5);
                    procedure          = rs.getString(6);
                    time               = OMUtils.buildTime(id, b, e);
                } else {
                    return null;
                }
            }

            try(final PreparedStatement stmt2 = c.prepareStatement("SELECT \"phenomenon\" FROM \"" + schemaPrefix + "om\".\"offering_observed_properties\" WHERE \"id_offering\"=?")) {//NOSONAR
                stmt2.setString(1, offeringId);
                try(final ResultSet rs2 = stmt2.executeQuery()) {
                    while (rs2.next()) {
                        phens.add(rs2.getString(1));
                    }
                }
            }

            try(final PreparedStatement stmt3 = c.prepareStatement("SELECT \"foi\" FROM \"" + schemaPrefix + "om\".\"offering_foi\" WHERE \"id_offering\"=?")) {//NOSONAR
                stmt3.setString(1, offeringId);
                try(final ResultSet rs3 = stmt3.executeQuery()) {
                    while (rs3.next()) {
                        foi.add(rs3.getString(1));
                    }
                }
            }

            return new Offering( id,
                                 name,
                                 description,
                                 null,
                                 null, // bounds
                                 new ArrayList<>(),
                                 time,
                                 procedure,
                                 phens,
                                 foi);

        } catch (SQLException e) {
            throw new DataStoreException("Error while retrieving offering: " + offeringId, e);
        }
    }

    protected List<Field> readFields(final String procedureID, final Connection c) throws SQLException {
        return readFields(procedureID, false, c);
    }
    
    protected List<Field> readFields(final String procedureID, final boolean removeMainTimeField, final Connection c) throws SQLException {
        final List<Field> results = new ArrayList<>();
        String query = "SELECT * FROM \"" + schemaPrefix + "om\".\"procedure_descriptions\" WHERE \"procedure\"=? AND \"parent\" IS NULL";
        if (removeMainTimeField) {
            query = query + " AND NOT(\"order\"= 1 AND \"field_type\"= 'Time')";
        }
        query = query + " ORDER BY \"order\"";
        try(final PreparedStatement stmt = c.prepareStatement(query)) {//NOSONAR
            stmt.setString(1, procedureID);
            try(final ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(getFieldFromDb(rs, procedureID, c, true));
                }
                return results;
            }
        }
    }

    protected List<InsertDbField> completeDbField(final String procedureID, final List<Field> inputFields, final Connection c) throws SQLException {
        List<InsertDbField> results = new ArrayList<>();
        for (Field inputField : inputFields) {
            results.add(completeDbField(procedureID, inputField, c));
        }
        return results;
    }

    protected InsertDbField completeDbField(final String procedureID, final Field inputField, final Connection c) throws SQLException {
        final String query = "SELECT * FROM \"" + schemaPrefix + "om\".\"procedure_descriptions\" WHERE \"procedure\"=? AND \"parent\" IS NULL AND \"field_name\" = ?";
        try(final PreparedStatement stmt = c.prepareStatement(query)) {//NOSONAR
            stmt.setString(1, procedureID);
            stmt.setString(2, inputField.name);
            try(final ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    DbField dbField = getFieldFromDb(rs, procedureID, c, true);
                    InsertDbField result = new InsertDbField(dbField);
                    result.setInputUom(inputField.uom);
                    return result;
                }
            }
        }
        throw new SQLException("No field " + inputField.name + " found for procedure:" + procedureID);
    }

    /**
     * Return the main field of the timeseries/trajectory (TIME) or profile (DEPTH, PRESSION, ...).
     * This method assume that the main field is !ALWAYS! set a the order 1.
     *
     * @param procedureID
     * @param c
     * @return
     * @throws SQLException
     */
    protected Field getMainField(final String procedureID, final Connection c) throws SQLException {
        return getFieldByIndex(procedureID, 1, false, c);
    }

    protected Field getFieldByIndex(final String procedureID, final int index, final boolean fetchQualityFields, final Connection c) throws SQLException {
        try(final PreparedStatement stmt = c.prepareStatement("SELECT * FROM \"" + schemaPrefix + "om\".\"procedure_descriptions\" WHERE \"procedure\"=? AND \"order\"=?  AND \"parent\" IS NULL")) {//NOSONAR
            stmt.setString(1, procedureID);
            stmt.setInt(2, index);
            try (final ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return getFieldFromDb(rs, procedureID, c, fetchQualityFields);
                }
                return null;
            }
        }
    }

    /**
     * Return the positions field for trajectory.
     * This method assume that the fields are names 'lat' or 'lon'
     *
     * @param procedureID
     * @param c
     * @return
     * @throws SQLException
     */
    protected List<Field> getPosFields(final String procedureID, final Connection c) throws SQLException {
        final List<Field> results = new ArrayList<>();
        try(final PreparedStatement stmt = c.prepareStatement("SELECT * FROM \"" + schemaPrefix + "om\".\"procedure_descriptions\" WHERE \"procedure\"=? AND (\"field_name\"='lat' OR \"field_name\"='lon') AND \"parent\" IS NULL ORDER BY \"order\" DESC")) {//NOSONAR
            stmt.setString(1, procedureID);
            try (final ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(getFieldFromDb(rs, procedureID, c, false));
                }
            }
        }
        return results;
    }

    protected Field getProcedureField(final String procedureID, final String fieldName, final Connection c) throws SQLException {
        try(final PreparedStatement stmt = c.prepareStatement("SELECT * FROM \"" + schemaPrefix + "om\".\"procedure_descriptions\" WHERE \"procedure\"=? AND (\"field_name\"= ?) AND \"parent\" IS NULL")) {//NOSONAR
            stmt.setString(1, procedureID);
            stmt.setString(2, fieldName);
            try(final ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return getFieldFromDb(rs, procedureID, c, true);
                }
                return null;
            }
        }
    }

    protected DbField getFieldFromDb(final ResultSet rs, String procedureID, Connection c, boolean fetchQualityFields) throws SQLException {
        final String fieldName = rs.getString("field_name");
        final DbField f = new DbField(
                         rs.getInt("order"),
                         FieldType.fromLabel(rs.getString("field_type")),
                         fieldName,
                         fieldName,
                         rs.getString("field_definition"),
                         rs.getString("uom"),
                         rs.getInt("table_number"));

        if (fetchQualityFields) {
            try(final PreparedStatement stmt = c.prepareStatement("SELECT * FROM \"" + schemaPrefix + "om\".\"procedure_descriptions\" WHERE \"procedure\"=? AND \"parent\"=? ORDER BY \"order\"")) {//NOSONAR
                stmt.setString(1, procedureID);
                stmt.setString(2, fieldName);
                try(final ResultSet rss = stmt.executeQuery()) {
                    while (rss.next()) {
                        f.qualityFields.add(getFieldFromDb(rss, procedureID, c, false));
                    }
                }
            }
        }
        return f;
    }

    protected static class ProcedureInfo  {
        public final int pid;
        public final int nbTable;
        public final String procedureId;
        public final String type;

        public ProcedureInfo(int pid, int nbTable, String procedureId, String type) {
            this.pid = pid;
            this.nbTable = nbTable;
            this.procedureId = procedureId;
            this.type = type;
        }
    }

    /**
     * Return the information about the procedure:  PID (internal int procedure identifier) , the number of measure table, ... associated for the specified observation.
     * If there is no procedure for the specified procedure id, this method will return PID = -1, nb table = 0 (for backward compatibility).
     * 
     * @param obsIdentifier Observation identifier.
     * @param c A SQL connection.
     *
     * @return Information about the procedure such as PID and number of measure table.
     * @throws SQLException id The sql query fails.
     */
    protected Optional<ProcedureInfo> getPIDFromObservation(final String obsIdentifier, final Connection c) throws SQLException {
        try(final PreparedStatement stmt = c.prepareStatement("SELECT \"pid\", \"nb_table\", p.\"id\", p.\"om_type\" FROM \"" + schemaPrefix + "om\".\"observations\", \"" + schemaPrefix + "om\".\"procedures\" p WHERE \"identifier\"=? AND \"procedure\"=p.\"id\"")) {//NOSONAR
            stmt.setString(1, obsIdentifier);
            try (final ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new ProcedureInfo(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4)));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Return  the information about the procedure: PID (internal int procedure identifier) and the number of measure table associated for the specified observation.
     * If there is no procedure for the specified procedure id, thsi method will return {-1, 0}
     *
     * @param oid Observation id.
     * @param c A SQL connection.
     *
     * @return A int array with PID and number of measure table.
     * @throws SQLException id The sql query fails.
     */
    protected Optional<ProcedureInfo> getPIDFromOID(final int oid, final Connection c) throws SQLException {
        try(final PreparedStatement stmt = c.prepareStatement("SELECT \"pid\", \"nb_table\", p.\"id\", p.\"om_type\" FROM \"" + schemaPrefix + "om\".\"observations\" o, \"" + schemaPrefix + "om\".\"procedures\" p WHERE o.\"id\"=? AND \"procedure\"=p.\"id\"")) {//NOSONAR
            stmt.setInt(1, oid);
            try (final ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                   return Optional.of(new ProcedureInfo(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4)));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Return  the information about the procedure: PID (internal int procedure identifier) and the number of measure table associated for the specified procedure.
     *
     * @param procedure Procedure identifier.
     * @param c A SQL connection.
     *
     * @return Information about the procedure such as PID and number of measure table.
     */
    protected Optional<ProcedureInfo> getPIDFromProcedure(final String procedure, final Connection c) throws SQLException {
        try(final PreparedStatement stmt = c.prepareStatement("SELECT \"pid\", \"nb_table\", \"om_type\" FROM \"" + schemaPrefix + "om\".\"procedures\" WHERE \"id\"=?")) {//NOSONAR
            stmt.setString(1, procedure);
            try(final ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new ProcedureInfo(rs.getInt(1), rs.getInt(2), procedure, rs.getString(3)));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Return the observation internal id for the specified observation identifier.
     *
     * @param identifier  observation identifier.
     * @param c An SQL connection.
     *
     * @return The observation internal id or -1 if the observation does not exist.
     * @throws SQLException
     */
    protected int getOIDFromIdentifier(final String identifier, final Connection c) throws SQLException {
        try(final PreparedStatement stmt = c.prepareStatement("SELECT \"id\" FROM \"" + schemaPrefix + "om\".\"observations\" WHERE \"identifier\"=?")) {//NOSONAR
            stmt.setString(1, identifier);
            try (final ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return -1;
            }
        }
    }

    /**
     * Return the number of registred fields in the measure table identified by its procedure and table number.
     *
     * @param procedure Procedure identifier.
     * @param tableNum measure table number.
     * @param c A SQL connection.
     * 
     * @return The number of fields (meaning SQL column) of the specified measure table for the specified procedure.
     * If the procedure or the the table number does not exist it will return 0.
     */
    protected int getNbFieldInTable(final String procedure, final int tableNum, final Connection c) throws SQLException {
        try (final PreparedStatement stmt = c.prepareStatement("SELECT COUNT(*) FROM \"" + schemaPrefix + "om\".\"procedure_descriptions\" WHERE \"procedure\"=? AND \"table_number\"=?")) {//NOSONAR
            stmt.setString(1, procedure);
            stmt.setInt(2, tableNum);
            try (final ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new IllegalStateException("Unexpected no results");
    }
    
    public Procedure getProcess(String id, final Connection c) throws SQLException {
        final Map<String, Object> properties = readProperties("procedures_properties", "id_procedure", id, c);
        try(final PreparedStatement stmt = c.prepareStatement("SELECT * FROM \"" + schemaPrefix + "om\".\"procedures\" WHERE \"id\"=?")) {//NOSONAR
            stmt.setString(1, id);
            try (final ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new Procedure(rs.getString("id"), rs.getString("name"), rs.getString("description"), properties);
                }
            }
        }
        return null;
    }

    public Procedure getProcessSafe(String identifier, final Connection c) throws RuntimeException {
        try {
            return getProcess(identifier, c);
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    protected String getProcedureParent(final String procedureId, final Connection c) throws SQLException {
        try(final PreparedStatement stmt = c.prepareStatement("SELECT \"parent\" FROM \"" + schemaPrefix + "om\".\"procedures\" WHERE \"id\"=?")) {//NOSONAR
            stmt.setString(1, procedureId);
            try(final ResultSet rs = stmt.executeQuery()) {
                String parent = null;
                if (rs.next()) {
                    parent = rs.getString(1);
                }
                return parent;
            }
        }
    }

    protected List<Element> buildResultQuality(Field parent, SQLResult rs) throws SQLException {
        List<Element> results = new ArrayList<>();
        if (parent.qualityFields != null) {
            for (Field field : parent.qualityFields) {
                int rsIndex = ((DbField)field).tableNumber - 1;
                String fieldName = parent.name + "_quality_" + field.name;
                Object value = null;
                if (rs != null) {
                    switch(field.type) {
                        case BOOLEAN: value = rs.getBoolean(fieldName, rsIndex);break;
                        case QUANTITY: value = rs.getDouble(fieldName, rsIndex);break;
                        case TIME: value = rs.getTimestamp(fieldName, rsIndex);break;
                        case TEXT:
                        default: value = rs.getString(fieldName, rsIndex);
                    }
                    
                }
                results.add(OMUtils.createQualityElement(field, value));
            }
        }
        return results;
    }

    protected int getNbMeasureForProcedure(int pid, Connection c) {
        try(final PreparedStatement stmt = c.prepareStatement("SELECT COUNT(\"id\") FROM \"" + schemaPrefix + "mesures\".\"mesure" + pid + "\"");
            final ResultSet rs = stmt.executeQuery()) {//NOSONAR
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException ex) {
            // we catch it because the table can not exist.
            LOGGER.log(Level.FINE, "Error while looking for measure count", ex);
        }
        return 0;
    }

    protected int getNbMeasureForObservation(int pid, int oid, Connection c) {
        try (final PreparedStatement stmt = c.prepareStatement("SELECT COUNT(\"id\") FROM \"" + schemaPrefix + "mesures\".\"mesure" + pid + "\" WHERE \"id_observation\" = ?")) {
            stmt.setInt(1, oid);
            try (final ResultSet rs = stmt.executeQuery()) {//NOSONAR
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException ex) {
            // we catch it because the table can not exist.
            LOGGER.log(Level.FINE, "Error while looking for measure count", ex);
        }
        return -1;
    }

    protected Result getResult(int oid , final QName resultModel, final Integer measureId, final Field selectedField, final Connection c) throws DataStoreException, SQLException {
        final ProcedureInfo ti      = getPIDFromOID(oid, c).orElseThrow(IllegalArgumentException::new);
        if (resultModel.equals(MEASUREMENT_QNAME)) {
            return buildMeasureResult(ti, oid, measureId, selectedField, c);
        } else {
            return buildComplexResult(ti, oid, measureId, c);
        }
    }

    private ComplexResult buildComplexResult(final ProcedureInfo ti, final int oid, final Integer measureId, final Connection c) throws DataStoreException, SQLException {

        final List<Field> fields    = readFields(ti.procedureId, false, c);
        final Field mainField       = fields.get(0); // main is always first
        int nbValue                 = 0;
        final ResultBuilder values  = new ResultBuilder(ResultMode.CSV, DEFAULT_ENCODING, false);

        FilterSQLRequest measureFilter = null;
        if (measureId != null) {
            measureFilter = new SingleFilterSQLRequest(" AND m.\"id\" = ").appendValue(measureId);
        }

        final MultiFilterSQLRequest queries = buildMesureRequests(ti, mainField, measureFilter,  oid, false, true, false);

        final FieldParser parser    = new FieldParser(fields, values, false, false, true, null);
        try (SQLResult rs = queries.execute(c)) {
            while (rs.next()) {
                parser.parseLine(rs, 0);
                nbValue = nbValue + parser.nbValue;
            }
            return OMUtils.buildComplexResult(fields, nbValue, DEFAULT_ENCODING, values);
        }
    }

    private MeasureResult buildMeasureResult(final ProcedureInfo ti, final int oid, final Integer measureId, final Field selectedField, final Connection c) throws DataStoreException, SQLException {
        if (selectedField == null) {
            throw new DataStoreException("Measurement extraction need a field index specified");
        }
        if (measureId == null) {
            throw new DataStoreException("Measurement extraction need a measure id specified");
        }

        final FieldType fType  = selectedField.type;
        String tableName = "mesure" + ti.pid;
        int tn = ((DbField) selectedField).tableNumber;
        if (tn > 1) {
            tableName = tableName + "_" + tn;
        }

        String query = "SELECT \"" + selectedField.name + "\" FROM  \"" + schemaPrefix + "mesures\".\"" + tableName + "\" m " +
                       "WHERE \"id_observation\" = ? " +
                       "AND m.\"id\" = ? ";

        try(final PreparedStatement stmt  = c.prepareStatement(query)) {//NOSONAR
            stmt.setInt(1, oid);
            stmt.setInt(2, measureId);
            try(final ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Object value;
                    switch (fType) {
                        case QUANTITY: value = rs.getDouble(selectedField.name);break;
                        case BOOLEAN: value = rs.getBoolean(selectedField.name);break;
                        case TIME: value = new Date(rs.getTimestamp(selectedField.name).getTime());break;
                        case TEXT:
                        default: value = rs.getString(selectedField.name);break;
                    }
                    return new MeasureResult(selectedField, value);
                } else {
                    return null;
                }
            }
        } catch (NumberFormatException ex) {
            throw new DataStoreException("Unable ta parse the result value as a double");
        }
    }

    /**
     * Build one or more SQL request on the measure(s) tables.
     * The point of this mecanism is to bypass the issue of selecting more than 1664 columns in a select.
     * The measure table columns are already multiple to avoid the probleme of limited columns in a table,
     * so we build a select request for eache measure table, join with the main field of the primary table.
     *
     * @param pti Informations abut the measure tables.
     * @param mainField Main field of the procedure.
     * @param measureFilter Piece of SQL to apply to all the measure query. (can be null)
     * @param oid An Observation id used to filter the measure. (can be null)
     * @param obsJoin If true, a join with the observation table will be applied.
     * @param addOrderBy If true, an order by main filed wille be applied.
     * @param idOnly If true, only the measure identifier will be selected.
     * 
     * @return A Multi filter request on measure tables.
     */
    protected MultiFilterSQLRequest buildMesureRequests(ProcedureInfo pti, Field mainField, FilterSQLRequest measureFilter, Integer oid, boolean obsJoin, boolean addOrderBy, boolean idOnly) {
        final boolean profile = "profile".equals(pti.type);
        final MultiFilterSQLRequest measureRequests = new MultiFilterSQLRequest();
        for (int i = 0; i < pti.nbTable; i++) {
            String baseTableName = "mesure" + pti.pid;
            final FilterSQLRequest measureRequest;
            if (i > 0) {
                String tableName = baseTableName + "_" + (i + 1);
                String select;
                if (idOnly) {
                    select = "m2.\"id\"";
                } else {
                    select = "m2.*, m.\"" + mainField.name + "\"";
                }
                measureRequest = new SingleFilterSQLRequest("SELECT ").append(select);
                measureRequest.append(" FROM \"" + schemaPrefix + "mesures\".\"" + tableName + "\" m2, \"" + schemaPrefix + "mesures\".\"" + baseTableName + "\" m");
                if (obsJoin) {
                    measureRequest.append(",\"" + schemaPrefix + "om\".\"observations\" o ");
                }
                measureRequest.append(" WHERE (m.\"id\" = m2.\"id\" AND  m.\"id_observation\" = m2.\"id_observation\") ");
                if (oid != null) {
                    measureRequest.append(" AND m2.\"id_observation\" = ").appendValue(oid);
                }
                if (obsJoin) {
                    measureRequest.append(" AND o.\"id\" = m2.\"id_observation\" ");
                }

            } else {
                String select;
                if (idOnly) {
                    select = "m.\"id\"";
                } else {
                    select = "m.*";
                }
                measureRequest = new SingleFilterSQLRequest("SELECT ").append(select).append(" FROM \"" + schemaPrefix + "mesures\".\"" + baseTableName + "\" m");
                if (obsJoin) {
                    measureRequest.append(",\"" + schemaPrefix + "om\".\"observations\" o ");
                }
                if (oid != null) {
                    measureRequest.append(" WHERE m.\"id_observation\" = ").appendValue(oid);
                }
                if (obsJoin) {
                    String where = (oid != null) ? " AND " : " WHERE ";
                    measureRequest.append(where).append(" o.\"id\" = m.\"id_observation\" ");
                }
            }
            if (measureFilter instanceof MultiFilterSQLRequest mf) {
                measureRequest.append(mf.getRequest(i), !profile);
            } else if (measureFilter != null) {
                measureRequest.append(measureFilter, !profile);
            }
            if (addOrderBy) {
                measureRequest.append(" ORDER BY ").append("m.\"" + mainField.name + "\"");
            }
            measureRequests.addRequest(measureRequest);
        }
        return measureRequests;
    }
}
