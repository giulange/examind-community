/*
 *    Constellation - An open source and standard compliant SDI
 *    http://www.constellation-sdi.org
 *
 * Copyright 2020 Geomatys.
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
package org.constellation.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.sis.geometry.GeneralEnvelope;
import org.apache.sis.parameter.Parameters;
import org.apache.sis.referencing.CRS;
import org.apache.sis.storage.DataStore;
import org.apache.sis.storage.DataStoreException;
import org.apache.sis.storage.WritableAggregate;
import org.constellation.api.TilingMode;
import static org.constellation.api.TilingMode.*;
import org.constellation.business.IConfigurationBusiness;
import org.constellation.business.IDataBusiness;
import org.constellation.business.IMapContextBusiness;
import org.constellation.business.IProcessBusiness;
import org.constellation.business.IProviderBusiness;
import org.constellation.business.IPyramidBusiness;
import org.constellation.business.IStyleBusiness;
import org.constellation.dto.DataBrief;
import org.constellation.dto.MapContextLayersDTO;
import org.constellation.dto.TilingResult;
import org.constellation.dto.StyleBrief;
import org.constellation.dto.process.TaskParameter;
import org.constellation.exception.ConfigurationException;
import org.constellation.exception.ConstellationException;
import org.constellation.exception.ConstellationStoreException;
import org.constellation.provider.Data;
import org.constellation.provider.DataProvider;
import org.constellation.provider.DataProviderFactory;
import org.constellation.provider.DataProviders;
import org.constellation.provider.ProviderParameters;
import org.constellation.repository.DataRepository;
import org.constellation.util.ParamUtilities;
import org.constellation.util.Util;
import org.geotoolkit.coverage.xmlstore.XMLCoverageResource;
import org.geotoolkit.coverage.xmlstore.XMLCoverageStore;
import org.geotoolkit.coverage.xmlstore.XMLCoverageStoreFactory;
import org.geotoolkit.map.MapBuilder;
import org.apache.sis.portrayal.MapLayers;
import org.constellation.api.ProviderType;
import org.constellation.provider.CoverageData;
import org.constellation.provider.PyramidData;
import org.constellation.provider.ScaleUtilities;
import org.constellation.util.CRSUtilities;
import static org.constellation.util.CRSUtilities.verifyCrs;
import org.geotoolkit.process.ProcessDescriptor;
import org.geotoolkit.process.ProcessFinder;
import org.geotoolkit.process.Process;
import org.geotoolkit.storage.coverage.DefiningGridCoverageResource;
import org.geotoolkit.storage.multires.DefiningTileMatrixSet;
import org.geotoolkit.storage.multires.TiledResource;
import org.geotoolkit.storage.multires.TileMatrixSetBuilder;
import org.geotoolkit.storage.multires.WritableTiledResource;
import org.geotoolkit.util.NamesExt;
import org.opengis.geometry.Envelope;
import org.opengis.parameter.ParameterValueGroup;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.TransformException;
import org.opengis.style.Style;
import org.opengis.util.GenericName;
import org.opengis.util.NoSuchIdentifierException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 *
 * @author Guilhem Legal (Geomatys)
 */
@Component("pyramidBusiness")
public class PyramidBusiness implements IPyramidBusiness {

    private static final Logger LOGGER = Logger.getLogger("org.constellation.admin");

    protected static final String RENDERED_PREFIX = "rendered_";

    private static final String CONFORM_PREFIX = "conform_";

    @Autowired
    private IProcessBusiness processBusiness;

    @Autowired
    protected IDataBusiness dataBusiness;

    @Autowired
    protected IStyleBusiness styleBusiness;

    @Autowired
    protected IMapContextBusiness mapContextBusiness;

    @Autowired
    protected IProviderBusiness providerBusiness;

    @Autowired
    protected IConfigurationBusiness configBusiness;

    @Autowired
    private DataRepository dataRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    public TilingResult pyramidDatas(Integer userId, String pyramidDataName, List<Integer> dataIds, final String crs, TilingMode tilingMode, int nbLevel) throws ConstellationException {

        // this method need to be executed in a transaction
        TilingContext t = preparePyramidDatas(userId, pyramidDataName, dataIds, crs, tilingMode, nbLevel);

        //add task in scheduler (previous transaction must be commited)
        processBusiness.runProcess("Create pyramid from datas.", t.p, t.taskId, userId);

        return new TilingResult(t.taskId, t.pyDataId);
    }

    @Transactional
    private TilingContext preparePyramidDatas(Integer userId, String pyramidDataName, List<Integer> dataIds, final String crsId, TilingMode tilingMode, final int nbLevel) throws ConstellationException {
        // verify CRS validity
        final CoordinateReferenceSystem crs = verifyCrs(crsId, true).orElse(null);

        final List<DataBrief> briefs = new ArrayList<>();
        for (Integer dataId : dataIds) {
            briefs.add(dataBusiness.getDataBrief(dataId, false, true));
        }

        if (briefs.isEmpty()) {
            throw new ConstellationException("The given list of data to pyramid is empty.");
        }

        /**
         * 1) calculate best scales array. loop on each data and determine
         * the largest scales that wrap all data.
         */
        final double[] scales = ScaleUtilities.getBestScales(briefs, crs, 256, nbLevel);

        /**
         * 2) - Build the map context that contains all selected layers.
         *    - Calculate the global envelope.
         */
        GeneralEnvelope globalEnv = null;
        final MapLayers context = MapBuilder.createContext();
        for (final DataBrief db : briefs) {
            final Integer dataId = db.getId();

            if (pyramidDataName == null) {
                pyramidDataName = db.getName();
            }

            //get data
            final Data inData;
            try {
                inData = DataProviders.getProviderData(dataId);
            } catch (ConfigurationException ex) {
                LOGGER.log(Level.WARNING, ex.getMessage() + " Moving to next data.");
                continue;
            }

            if (inData == null) {
                LOGGER.log(Level.WARNING, "Data " + dataId + " does not exist in provider. Moving to next data.");
                continue;
            }

            Envelope dataEnv;
            try {
                dataEnv = inData.getEnvelope(crs);
            } catch (ConstellationStoreException ex) {
                LOGGER.log(Level.WARNING, "Failed to extract envelope for data {0}.  Moving to next data.", dataId);
                continue;
            }

            if (RENDERED.equals(tilingMode)) {
                //if style is null, a default style will be used in maplayer.
                Style style = null;
                try {
                    final StyleBrief styleB = db.getFirstStyle();
                    if (styleB != null && styleB.getId() != null) {
                        style = styleBusiness.getStyle(styleB.getId());
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, ex.getLocalizedMessage(), ex);
                }

                try {
                    context.getComponents().add(inData.getMapLayer(style));
                } catch (ConstellationStoreException ex) {
                    LOGGER.log(Level.WARNING, "Failed to create map layer for data " + ex.getMessage() +  "Moving to next data.", ex);
                    continue;
                }
            } else {

                if (!(inData instanceof CoverageData)) {
                    throw new ConstellationException("Cannot create pyramid conform for no raster data, it is not supported yet!");
                }
                context.getComponents().add(((CoverageData)inData).getMapLayer(null, true));
            }

            if (globalEnv == null) {
                globalEnv = new GeneralEnvelope(dataEnv);
            } else {
                globalEnv.add(dataEnv);
            }
        }

        if (globalEnv != null && crs != null) {
            Envelope domain = CRS.getDomainOfValidity(crs);
            if (domain != null) {
                globalEnv.intersect(domain);
            }
        }

        final int tileSize = 256;
        return generatePyramidAndProcess(userId, context, pyramidDataName, tilingMode, crsId, globalEnv, tileSize, scales, dataIds);
    }

    @Override
    public TilingResult pyramidMapContext(Integer userId, String pyramidDataName, final String crs, final Integer mcId, TilingMode mode, int nbLevel) throws ConstellationException {

        // this method need to be executed in a transaction
        TilingContext t = preparePyramidMapContext(userId, pyramidDataName, crs, mcId, mode, nbLevel);

        //add task in scheduler (previous transaction must be commited)
        processBusiness.runProcess("Create pyramid from Mapcontext.", t.p, t.taskId, userId);

        return new TilingResult(t.taskId, t.pyDataId);
    }

    @Transactional
    private TilingContext preparePyramidMapContext(Integer userId, String pyramidDataName, final String crs, final Integer mcId, TilingMode tilingMode, int nbLevel) throws ConstellationException {

        final MapContextLayersDTO mc = mapContextBusiness.findMapContextLayers(mcId, true);
        if (mc.getLayers().isEmpty()) {
            throw  new ConstellationException("The given mapcontext to pyramid is empty.");
        }

        // verify CRS validity
        final CoordinateReferenceSystem crsOutput = verifyCrs(crs, true).orElseThrow(() -> new ConstellationException("Missing output CRS parameter."));
        final CoordinateReferenceSystem crsObj = verifyCrs(mc.getCrs(), true).orElseThrow(() -> new ConstellationException("Missing mapcontext CRS."));

        // build output envelope
        final GeneralEnvelope env = new GeneralEnvelope(crsObj);
        env.setRange(0, mc.getWest(), mc.getEast());
        env.setRange(1, mc.getSouth(), mc.getNorth());
        GeneralEnvelope globalEnv;
        try {
            globalEnv = CRSUtilities.reprojectWithNoInfinity(env, crsOutput);
        } catch (TransformException ex) {
            globalEnv = null;
        }

        if (globalEnv == null || globalEnv.isEmpty()) {
            globalEnv = new GeneralEnvelope(CRS.getDomainOfValidity(crsOutput));
        }

        // compute scales
        final int tileSize = 256;
        final double[] scales;
        if (mc.isAllInternalData()) {
            // TODO
            scales = ScaleUtilities.generateScales(globalEnv, tileSize, nbLevel);
        } else {
            scales = ScaleUtilities.generateScales(globalEnv, tileSize, nbLevel);
        }

        Integer mcDataId = mapContextBusiness.getMapContextData(mcId).getId();
        Data d = DataProviders.getProviderData(mcDataId);
        final MapLayers context =  (MapLayers) d.getMapLayer(null);

        return generatePyramidAndProcess(userId, context, pyramidDataName, tilingMode, crs, globalEnv, tileSize, scales, Arrays.asList(mcDataId));
    }

    private TilingContext generatePyramidAndProcess(Integer userId, final MapLayers context, String pyramidDataName, TilingMode tilingMode, String crs, Envelope globalEnv, int tileSize, double[] scales, List<Integer> dataIds) throws ConstellationException {
        final String prefix      = tilingMode.equals(RENDERED) ? RENDERED_PREFIX : CONFORM_PREFIX;
        String pyramidIdentifier = prefix + UUID.randomUUID().toString();
        context.setIdentifier("Styled pyramid " + crs + " for " + pyramidIdentifier + ": " + pyramidDataName);

        //create the output provider
        final GenericName pGname       = NamesExt.create(pyramidDataName);
        final String tileFormat        = tilingMode.equals(RENDERED) ? "PNG" : "TIFF";
        final Integer pyramidProvider  = createPyramidProvider(pyramidIdentifier, pGname, tilingMode, tileFormat, globalEnv, tileSize, scales);
        final Data pyData              = DataProviders.getProviderData(pyramidProvider, null, pyramidDataName);

        if (!(pyData instanceof PyramidData)) {
            throw new ConstellationException("No pyramid data created (in provider).");
        }

        //create database data object
        providerBusiness.createOrUpdateData(pyramidProvider, null, false, true, userId);

        // Get the new data created
        Integer pyDataId;
        List<Integer> createdDataIds = providerBusiness.getDataIdsFromProviderId(pyramidProvider);
        if (!createdDataIds.isEmpty() && createdDataIds.size() == 1) {
            pyDataId = createdDataIds.get(0);

            //set RENDERED status
            dataBusiness.updateDataRendered(pyDataId, RENDERED.equals(tilingMode));

            //link pyramid data to original data
            for (final Integer did : dataIds) {
                dataBusiness.linkDataToData(did, pyDataId);
            }
        } else if (createdDataIds.size()> 1) {
            // i don't think this could happen
            throw new ConstellationException("Multiple pyramid data has been created.");
        } else {
            throw new ConstellationException("No pyramid data has been created.");
        }

        return buildTilingProcess(userId, (PyramidData) pyData, context, tilingMode, pyDataId);
    }

    private static final class TilingContext {
        public Process p;
        public Integer taskId;
        public Integer pyDataId;
        public TilingContext(Process p,Integer taskId, Integer pyDataId) {
            this.p = p;
            this.taskId = taskId;
            this.pyDataId = pyDataId;
        }
    }

    private TilingContext buildTilingProcess(Integer userId, PyramidData pyData, MapLayers context, TilingMode mode, Integer pyDataId) throws ConstellationException {
        try {
            final ProcessDescriptor desc = ProcessFinder.getProcessDescriptor("administration", "gen-pyramid");
            final ParameterValueGroup input = desc.getInputDescriptor().createValue();
            input.parameter("mapcontext").setValue(context);
            input.parameter("resource").setValue(pyData.getOrigin());
            input.parameter("mode").setValue(mode.name());
            final org.geotoolkit.process.Process p = desc.createProcess(input);

            //add task in scheduler
            final String taskName = context.getIdentifier() + " | " + System.currentTimeMillis();
            TaskParameter taskParameter = new TaskParameter();
            taskParameter.setProcessAuthority(Util.getProcessAuthorityCode(desc));
            taskParameter.setProcessCode(desc.getIdentifier().getCode());
            taskParameter.setDate(System.currentTimeMillis());
            taskParameter.setInputs(ParamUtilities.writeParameterJSON(input));
            taskParameter.setOwner(userId);
            taskParameter.setName(taskName);
            taskParameter.setType("INTERNAL");
            Integer taskId = processBusiness.addTaskParameter(taskParameter);

            return new TilingContext(p, taskId, pyDataId);
        } catch (NoSuchIdentifierException | JsonProcessingException ex) {
            throw new ConstellationException("Error while tiling data", ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void createAllPyramidConformForProvider(final int providerId, int nbLevel) throws ConstellationException {
        final List<org.constellation.dto.Data> dataList = dataRepository.findByProviderId(providerId);
        for (final org.constellation.dto.Data d : dataList) {
            try {
                // there is probably an issue here with null value for CRS param
                pyramidDatas(d.getOwnerId(), d.getName(), Arrays.asList(d.getId()), null, CONFORM, nbLevel);
            } catch (ConstellationException ex) {
                LOGGER.log(Level.WARNING, ex.getLocalizedMessage(), ex);
            }
        }
    }

    protected int createPyramidProvider(String pyramidProviderId, GenericName pyramidGname, TilingMode mode, String tileFormat, Envelope globalEnv, int tileSize, double[] scales) throws ConstellationException {
        try {
            Integer pid = buildSpecificPyramidProvider("xml-coverage", null, pyramidProviderId);

            final DataProvider outProvider = DataProviders.getProvider(pid);

            //create the output pyramid coverage reference
            DataStore pyramidStore = outProvider.getMainStore();
            if (RENDERED.equals(mode)) {
                TiledResource outRef = (TiledResource) ((WritableAggregate) pyramidStore).add(new DefiningGridCoverageResource(pyramidGname));
                ((XMLCoverageResource) outRef).setPackMode(RENDERED.name());
                ((XMLCoverageResource) outRef).setPreferredFormat(tileFormat);
            } else if (CONFORM.equals(mode)) {
                ((XMLCoverageStore) pyramidStore).create(pyramidGname, "GEOPHYSICS", tileFormat);
            } else {
                throw new IllegalArgumentException("Unexpected tiling mode:" + mode);
            }
            //this produces an update event which will create the DataRecord
            outProvider.reload();

            WritableTiledResource outRef;
            Data pyData = outProvider.get(pyramidGname);
            if (pyData != null && pyData.getOrigin() instanceof TiledResource) {
                outRef = (WritableTiledResource) pyData.getOrigin();
            } else {
                throw new ConstellationException("No pyramid data created (in provider).");
            }
            createTemplate(outRef, globalEnv, tileSize, scales);

            return pid;
        } catch (DataStoreException ex) {
            throw new ConstellationException(ex);
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Integer buildSpecificPyramidProvider(String providerType, Path pyramidDirectory, String pyramidProviderId) throws ConstellationException {
        try {
            //create the output folder for pyramid if not supplied
            if (pyramidDirectory == null) {
                pyramidDirectory = configBusiness.getDataIntegratedDirectory(pyramidProviderId, true);
            }

            final DataProviderFactory factory = DataProviders.getFactory("data-store");
            final Parameters pparams = Parameters.castOrWrap(factory.getProviderDescriptor().createValue());
            pparams.getOrCreate(ProviderParameters.SOURCE_ID_DESCRIPTOR).setValue(pyramidProviderId);
            pparams.getOrCreate(ProviderParameters.SOURCE_TYPE_DESCRIPTOR).setValue("data-store");
            final String storeChoiceName = factory.getStoreDescriptor().getName().getCode();
            final ParameterValueGroup choiceParams = pparams.groups(storeChoiceName).stream()
                    .findFirst()
                    .orElseGet(() -> pparams.addGroup(storeChoiceName));

            final String xmlParamName = XMLCoverageStoreFactory.PARAMETERS_DESCRIPTOR.getName().getCode();
            final Parameters xmlParams = choiceParams.groups(xmlParamName).stream()
                    .findFirst()
                    .map(Parameters::castOrWrap)
                    .orElseGet(() -> Parameters.castOrWrap(choiceParams.addGroup(xmlParamName)));

            xmlParams.getOrCreate(XMLCoverageStoreFactory.PATH).setValue(pyramidDirectory.toUri());
            xmlParams.getOrCreate(XMLCoverageStoreFactory.CACHE_TILE_STATE).setValue(true);

            return  providerBusiness.storeProvider(pyramidProviderId, ProviderType.LAYER, factory.getName(), pparams);
        } catch (IOException ex) {
            throw new ConstellationException(ex);
        }
    }

    protected void createTemplate(WritableTiledResource outRef, Envelope globalEnv, int tileSize, double[] scales) throws ConstellationException {
        try {
            //prepare the pyramid and mosaics
            final int[] tileDim = {tileSize, tileSize};
            final DefiningTileMatrixSet template = new TileMatrixSetBuilder()
                                                        .setDomain(globalEnv, 1)
                                                        .setScales(scales)
                                                        .setNbTileThreshold(1)
                                                        .setTileSize(tileDim)
                                                        .build();
            LOGGER.log(Level.INFO, "Creating pyramid with template:\n" + template.toString());
            outRef.createTileMatrixSet(template);
        } catch (DataStoreException ex) {
            throw new ConstellationException("Error while creating pyramid template.", ex);
        }
    }
}
