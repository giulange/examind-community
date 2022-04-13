/*
 *    Constellation - An open source and standard compliant SDI
 *    http://www.constellation-sdi.org
 *
 * Copyright 2017 Geomatys.
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
package org.constellation.api.rest;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.xml.bind.JAXBException;
import org.apache.sis.filter.DefaultFilterFactory;
import org.apache.sis.storage.FeatureQuery;
import org.apache.sis.internal.system.DefaultFactories;

import org.apache.sis.util.DefaultInternationalString;
import org.constellation.business.IDataBusiness;
import org.constellation.business.IStyleBusiness;
import org.constellation.exception.TargetNotFoundException;
import org.constellation.dto.ParameterValues;
import org.constellation.json.binding.AutoIntervalValues;
import org.constellation.json.binding.AutoUniqueValues;
import org.constellation.json.binding.ChartDataModel;
import org.constellation.json.binding.InterpolationPoint;
import org.constellation.json.binding.Repartition;
import org.constellation.json.binding.Style;
import org.constellation.json.binding.WrapperInterval;
import org.constellation.provider.DataProviders;
import org.geotoolkit.display2d.GO2Utilities;
import org.geotoolkit.storage.coverage.ImageStatistics;
import org.geotoolkit.nio.IOUtilities;
import org.geotoolkit.sld.MutableLayer;
import org.geotoolkit.sld.MutableStyledLayerDescriptor;
import org.geotoolkit.sld.xml.Specification;
import org.geotoolkit.sld.xml.StyleXmlIO;
import org.apache.sis.storage.FeatureSet;
import org.apache.sis.storage.Resource;
import org.constellation.business.IStyleConverterBusiness;
import org.constellation.dto.StatInfo;
import org.constellation.json.util.StyleUtilities;
import org.constellation.provider.Data;
import org.constellation.provider.util.StatsUtilities;
import org.geotoolkit.display2d.ext.isoline.symbolizer.IsolineSymbolizer;
import org.geotoolkit.filter.FilterUtilities;
import org.geotoolkit.internal.InternalUtilities;
import org.geotoolkit.style.DefaultDescription;
import org.geotoolkit.style.DefaultLineSymbolizer;
import org.geotoolkit.style.DefaultPointSymbolizer;
import org.geotoolkit.style.DefaultPolygonSymbolizer;
import org.geotoolkit.style.MutableRule;
import org.geotoolkit.style.MutableStyle;
import org.geotoolkit.style.MutableStyleFactory;
import org.geotoolkit.style.function.Categorize;
import org.geotoolkit.style.function.Interpolate;
import org.geotoolkit.style.interval.DefaultIntervalPalette;
import org.geotoolkit.style.interval.IntervalPalette;
import org.opengis.feature.Feature;
import org.opengis.filter.Filter;
import org.opengis.sld.LayerStyle;
import org.opengis.sld.NamedLayer;
import org.opengis.sld.UserLayer;
import org.opengis.style.Fill;
import org.opengis.style.Graphic;
import org.opengis.style.GraphicalSymbol;
import org.opengis.style.LineSymbolizer;
import org.opengis.style.Mark;
import org.opengis.style.PointSymbolizer;
import org.opengis.style.PolygonSymbolizer;
import org.opengis.style.RasterSymbolizer;
import org.opengis.style.Stroke;
import org.opengis.style.Symbolizer;
import org.opengis.util.FactoryException;
import org.springframework.http.HttpStatus;

import static org.springframework.http.HttpStatus.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;
import org.springframework.web.bind.annotation.RestController;
import static org.geotoolkit.style.StyleConstants.*;
import org.geotoolkit.style.io.PaletteReader;
import org.opengis.feature.AttributeType;
import org.opengis.feature.PropertyType;
import org.opengis.filter.Expression;
import org.opengis.filter.ValueReference;
import org.opengis.style.ColorMap;
import org.opengis.style.StyleFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/**
 *
 * @author Johann Sorel (Geomatys)
 */
@RestController
public class InternalStyleRestAPI extends AbstractRestAPI {

    @Inject
    private IStyleBusiness styleBusiness;

    @Inject
    private IStyleConverterBusiness styleConverterBusiness;

    @Inject
    private IDataBusiness dataBusiness;

    /**
     * @param styleId
     * @param ruleName
     * @param interval
     */
    @RequestMapping(value="/internal/styles/{styleId}/{ruleName}/{interval}", method=GET, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity getPaletteStyle(
            @PathVariable("styleId") int styleId,
            @PathVariable("ruleName") String ruleName,
            @PathVariable("interval") Integer interval) {
        try {

            final MutableStyle style = (MutableStyle) styleBusiness.getStyle(styleId);
            final List<MutableRule> mutableRules = new ArrayList<>();
            if (!style.featureTypeStyles().isEmpty()) {
                mutableRules.addAll(style.featureTypeStyles().get(0).rules());
            }
            // search related rule
            Expression function = null;
            boolean ruleFound = false;
            search:
            for (final MutableRule mutableRule : mutableRules) {
                if (mutableRule.getName().equalsIgnoreCase(ruleName)) {
                    ruleFound = true;
                    for (final Symbolizer symbolizer : mutableRule.symbolizers()) {
                        // search raster symbolizer and return function
                        if (symbolizer instanceof RasterSymbolizer rasterSymbolizer) {
                            if (rasterSymbolizer.getColorMap() != null){
                                function = rasterSymbolizer.getColorMap().getFunction();
                                break search;
                            }
                        } else if (symbolizer instanceof IsolineSymbolizer isolineSymbolizer) {
                            if (isolineSymbolizer.getLineSymbolizer() != null &&
                                isolineSymbolizer.getLineSymbolizer().getStroke() != null &&
                                isolineSymbolizer.getLineSymbolizer().getStroke().getColor() instanceof Expression) {
                                function = (Expression) isolineSymbolizer.getLineSymbolizer().getStroke().getColor();
                                break search;
                            }
                        }
                    }
                    break search;
                }
            }
            if (!ruleFound) {
                return new ErrorMessage(HttpStatus.UNPROCESSABLE_ENTITY).i18N(I18nCodes.Style.RULE_NOT_FOUND).build();
            }

            if (function instanceof Categorize categ) {
                final org.constellation.json.binding.Categorize categorize = new org.constellation.json.binding.Categorize(categ);
                final List<InterpolationPoint> points = categorize.reComputePoints(interval);
                return new ResponseEntity(new Repartition(points),OK);
            } else if(function instanceof Interpolate interpolateFunc) {
                final org.constellation.json.binding.Interpolate interpolate =new org.constellation.json.binding.Interpolate(interpolateFunc);
                final List<InterpolationPoint> points = interpolate.reComputePoints(interval);
                return new ResponseEntity(new Repartition(points),OK);
            }
            return new ErrorMessage(HttpStatus.UNPROCESSABLE_ENTITY).i18N(I18nCodes.Style.NOT_COLORMAP).build();
        } catch(TargetNotFoundException ex) {
            return new ErrorMessage(ex).i18N(I18nCodes.Style.NOT_FOUND).build();
        } catch(Exception ex) {
            LOGGER.log(Level.WARNING, ex.getLocalizedMessage(), ex);
            return new ErrorMessage(ex).build();
        }
    }


    /**
     * Creates a style and calculate the rules as palette defined as interval set.
     * Returns the new style object as json.
     *
     * @param type
     * @param wrapper object that contains the style and the config parameter to generate the palette rules.
     * @return the style as json.
     */
    @RequestMapping(value="/internal/styles/generateAutoInterval", method=POST, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity generateAutoIntervalStyle(
            @RequestParam(value="type",required=false,defaultValue = "sld") String type,
            @RequestBody WrapperInterval wrapper) {
        try {
            //get style and interval params
            final Style style = wrapper.getStyle();
            final AutoIntervalValues intervalValues = wrapper.getIntervalValues();

            final int dataId = wrapper.getDataId();

            final String attribute = intervalValues.getAttr();
            if (attribute ==null || attribute.trim().isEmpty()){
                return new ErrorMessage(UNPROCESSABLE_ENTITY)
                        .message("Attribute field should not be empty!")
                        .i18N(I18nCodes.Style.INVALID_ARGUMENT).build();
            }

            final String method = intervalValues.getMethod();
            final int intervals = intervalValues.getNbIntervals();

            final String symbolizerType = intervalValues.getSymbol();
            final List<String> colorsList = intervalValues.getColors();

            //rules that will be added to the style
            final List<MutableRule> newRules = new ArrayList<>();

            /*
             * I - Get feature type and feature data.
             */
            final org.constellation.dto.Data data  = dataBusiness.getData(dataId);
            final Data dataP      = DataProviders.getProviderData(data.getProviderId(), data.getNamespace(), data.getName());
            final Resource rs     = dataP.getOrigin();

            if (rs instanceof FeatureSet fs) {

               /*
                * II - Search extreme values.
                */
                final Set<Double> values = new HashSet<>();
                double minimum = Double.POSITIVE_INFINITY;
                double maximum = Double.NEGATIVE_INFINITY;

                final MutableStyleFactory SF = (MutableStyleFactory) DefaultFactories.forBuildin(StyleFactory.class);
                final DefaultFilterFactory FF = FilterUtilities.FF;

                final ValueReference property = FF.property(attribute);

                final FeatureQuery query = new FeatureQuery();
                query.setProjection(attribute);

                try (final Stream<Feature> featureSet = fs.subset(query).features(false)) {
                    Iterator<Feature> it = featureSet.iterator();
                    while(it.hasNext()){
                        final Feature feature = it.next();
                        final Number number = (Number) property.apply(feature);
                        final Double value = number.doubleValue();
                        values.add(value);
                        if (value < minimum) {
                            minimum = value;
                        }
                        if (value > maximum) {
                            maximum = value;
                        }
                    }
                }

                /*
                * III - Analyze values.
                */
                final Double[] allValues = values.toArray(Double[]::new);
                double[] interValues = new double[0];
                if ("equidistant".equals(method)) {
                    interValues = new double[intervals + 1];
                    for (int i = 0; i < interValues.length; i++) {
                        interValues[i] = minimum + (float) i / (interValues.length - 1) * (maximum - minimum);
                    }
                } else if ("mediane".equals(method)) {
                    interValues = new double[intervals + 1];
                    for (int i = 0; i < interValues.length; i++) {
                        interValues[i] = allValues[i * (allValues.length - 1) / (interValues.length - 1)];
                    }
                } else {
                    if (interValues.length != intervals + 1) {
                        interValues = Arrays.copyOf(interValues, intervals + 1);
                    }
                }

                /*
                * IV - Generate rules deriving symbolizer with given colors.
                */
                final Symbolizer symbolizer = createSymbolizer(symbolizerType);
                final Color[] colors = new Color[colorsList.size()];
                int loop = 0;
                for(final String c : colorsList){
                    colors[loop] = new Color(InternalUtilities.parseColor(c));
                    loop++;
                }
                final IntervalPalette palette = new DefaultIntervalPalette(colors);
                int count = 0;

                /*
                 * Create one rule for each interval.
                 */
                for (int i = 1; i < interValues.length; i++) {
                    final double step = (double) (i - 1) / (interValues.length - 2); // derivation step
                    double start = interValues[i - 1];
                    double end = interValues[i];
                    /*
                    * Create the interval filter.
                    */
                    final Filter above = FF.greaterOrEqual(property, FF.literal(start));
                    final Filter under;
                    if (i == interValues.length - 1) {
                        under = FF.lessOrEqual(property, FF.literal(end));
                    } else {
                        under = FF.less(property, FF.literal(end));
                    }
                    final Filter interval = FF.and(above, under);
                    /*
                    * Create new rule deriving the base symbolizer.
                    */
                    final MutableRule rule = SF.rule();
                    rule.setName((count++)+" - AutoInterval - " + property.getXPath());
                    rule.setDescription(new DefaultDescription(new DefaultInternationalString(property.getXPath()+" "+start+" - "+end),null));
                    rule.setFilter(interval);
                    rule.symbolizers().add(derivateSymbolizer(symbolizer, palette.interpolate(step)));
                    newRules.add(rule);
                }
            }

            //add rules to the style
            final MutableStyle mutableStyle = StyleUtilities.type(style);
            //remove all auto intervals rules if exists before adding the new list.
            final List<MutableRule> backupRules = new ArrayList<>(mutableStyle.featureTypeStyles().get(0).rules());
            final List<MutableRule> rulesToRemove = new ArrayList<>();
            for(final MutableRule r : backupRules){
                if(r.getName().contains("AutoInterval")){
                    rulesToRemove.add(r);
                }
            }
            backupRules.removeAll(rulesToRemove);
            mutableStyle.featureTypeStyles().get(0).rules().clear();
            mutableStyle.featureTypeStyles().get(0).rules().addAll(backupRules);
            mutableStyle.featureTypeStyles().get(0).rules().addAll(newRules);

            //create the style in server
            styleBusiness.createStyle(type, mutableStyle);
            Style json = styleConverterBusiness.getJsonStyle(mutableStyle);
            return new ResponseEntity(json,OK);
        } catch(Exception ex) {
            LOGGER.log(Level.WARNING, ex.getLocalizedMessage(), ex);
            return new ErrorMessage(ex).build();
        }
    }

    private Symbolizer createSymbolizer(final String symbolizerType) {
        final MutableStyleFactory SF = GO2Utilities.STYLE_FACTORY;
        final DefaultFilterFactory FF = GO2Utilities.FILTER_FACTORY;
        final Symbolizer symbolizer;
        if ("polygon".equals(symbolizerType)) {
            final Stroke stroke = SF.stroke(Color.BLACK, 1);
            final Fill fill = SF.fill(Color.BLUE);
            symbolizer = new DefaultPolygonSymbolizer(
                    stroke,
                    fill,
                    DEFAULT_DISPLACEMENT,
                    FF.literal(0),
                    DEFAULT_UOM,
                    null,
                    "polygon",
                    DEFAULT_DESCRIPTION);
        } else if ("line".equals(symbolizerType)) {
            final Stroke stroke = SF.stroke(Color.BLUE, 2);
            symbolizer = new DefaultLineSymbolizer(
                    stroke,
                    FF.literal(0),
                    DEFAULT_UOM,
                    null,
                    "line",
                    DEFAULT_DESCRIPTION);
        } else {
            final Stroke stroke = SF.stroke(Color.BLACK, 1);
            final Fill fill = SF.fill(Color.BLUE);
            final List<GraphicalSymbol> symbols = new ArrayList<>();
            symbols.add(SF.mark(MARK_CIRCLE, fill, stroke));
            final Graphic gra = SF.graphic(symbols, FF.literal(1), FF.literal(12), FF.literal(0), SF.anchorPoint(), SF.displacement());
            symbolizer = new DefaultPointSymbolizer(
                    gra,
                    DEFAULT_UOM,
                    null,
                    "point",
                    DEFAULT_DESCRIPTION);
        }
        return symbolizer;
    }

    private Symbolizer derivateSymbolizer(final Symbolizer symbol, final Color color) {
        final MutableStyleFactory SF = GO2Utilities.STYLE_FACTORY;
        if (symbol instanceof PolygonSymbolizer ps) {
            final Fill fill = SF.fill(SF.literal(color), ps.getFill().getOpacity());
            return SF.polygonSymbolizer(ps.getName(), ps.getGeometryPropertyName(),
                    ps.getDescription(), ps.getUnitOfMeasure(), ps.getStroke(),
                    fill, ps.getDisplacement(), ps.getPerpendicularOffset());
        } else if (symbol instanceof LineSymbolizer ls) {
            final Stroke oldStroke = ls.getStroke();
            final Stroke stroke = SF.stroke(SF.literal(color), oldStroke.getOpacity(), oldStroke.getWidth(),
                    oldStroke.getLineJoin(), oldStroke.getLineCap(), oldStroke.getDashArray(), oldStroke.getDashOffset());
            return SF.lineSymbolizer(ls.getName(), ls.getGeometryPropertyName(),
                    ls.getDescription(), ls.getUnitOfMeasure(), stroke, ls.getPerpendicularOffset());
        } else if (symbol instanceof PointSymbolizer ps) {
            final Graphic oldGraphic = ps.getGraphic();
            final Mark oldMark = (Mark) oldGraphic.graphicalSymbols().get(0);
            final Fill fill = SF.fill(SF.literal(color), oldMark.getFill().getOpacity());
            final List<GraphicalSymbol> symbols = new ArrayList<>();
            symbols.add(SF.mark(oldMark.getWellKnownName(), fill, oldMark.getStroke()));
            final Graphic graphic = SF.graphic(symbols, oldGraphic.getOpacity(), oldGraphic.getSize(),
                    oldGraphic.getRotation(), oldGraphic.getAnchorPoint(), oldGraphic.getDisplacement());
            return SF.pointSymbolizer(graphic, ps.getGeometryPropertyName());
        } else {
            throw new IllegalArgumentException("Unexpected symbolizer type: " + symbol);
        }
    }

    /**
     * Creates a style and calculate the rules as palette defined as unique values set.
     * Returns the new style object as json.
     *
     * @param type style provider identifier, 'sld' or 'sld-temp'
     * @param wrapper object that contains the style and the config parameter to generate the palette rules.
     * @return new style as json
     */
    @RequestMapping(value="/internal/styles/generateAutoUnique", method=POST, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity generateAutoUniqueStyle(
            @RequestParam(value="type",required=false,defaultValue = "sld") String type,
            @RequestBody WrapperInterval wrapper) {
        try {
            //get style and interval params
            final Style style = wrapper.getStyle();
            final AutoUniqueValues autoUniqueValues = wrapper.getUniqueValues();

            final org.constellation.dto.Data data = dataBusiness.getData(wrapper.getDataId());

            final String attribute = autoUniqueValues.getAttr();
            if(attribute ==null || attribute.trim().isEmpty()){
                return new ErrorMessage(UNPROCESSABLE_ENTITY)
                        .message("Attribute field should not be empty!")
                        .i18N(I18nCodes.Style.INVALID_ARGUMENT).build();
            }

            final String symbolizerType = autoUniqueValues.getSymbol();
            final List<String> colorsList = autoUniqueValues.getColors();

            //rules that will be added to the style
            final List<MutableRule> newRules = new ArrayList<>();

            /*
             * I - Get feature type and feature data.
             */
            final Data dataP      = DataProviders.getProviderData(data.getProviderId(), data.getNamespace(), data.getName());
            final Resource rs     = dataP.getOrigin();

            if (rs instanceof FeatureSet fs) {

                /*
                * II - Extract all different values.
                */
                final MutableStyleFactory SF = (MutableStyleFactory) DefaultFactories.forBuildin(StyleFactory.class);
                final DefaultFilterFactory FF = FilterUtilities.FF;
                final ValueReference property = FF.property(attribute);
                final List<Object> differentValues = new ArrayList<>();

                final FeatureQuery query = new FeatureQuery();
                query.setProjection(attribute);

                try (final Stream<Feature> featureSet = fs.subset(query).features(false)) {
                    Iterator<Feature> it = featureSet.iterator();
                    while(it.hasNext()){
                        final Feature feature = it.next();
                        final Object value = property.apply(feature);
                        if (!differentValues.contains(value)) {
                            differentValues.add(value);
                        }
                    }
                }
                /*
                * III - Generate rules deriving symbolizer with colors array.
                */
                final Symbolizer symbolizer = createSymbolizer(symbolizerType);
                final Color[] colors = new Color[colorsList.size()];
                int loop = 0;
                for(final String c : colorsList){
                    colors[loop] = new Color(InternalUtilities.parseColor(c));
                    loop++;
                }
                final IntervalPalette palette = new DefaultIntervalPalette(colors);
                int count = 0;
                /*
                * Create one rule for each different value.
                */
                for (int i = 0; i < differentValues.size(); i++) {
                    final double step = ((double) i) / (differentValues.size() - 1); // derivation step
                    final Object value = differentValues.get(i);
                    /*
                     * Create the unique value filter.
                     */
                    final Filter filter;
                    if(value instanceof String && !value.toString().isEmpty() && value.toString().contains("'")){
                        final String val = ((String) value).replaceAll("'","\\"+"'");
                        filter = FF.like(property, FF.literal(val).toString(), '*', '?', '\\', true);
                    }else {
                        filter = FF.equal(property, FF.literal(value));
                    }

                    /*
                     * Create new rule derivating the base symbolizer.
                     */
                    final MutableRule rule = SF.rule(derivateSymbolizer(symbolizer, palette.interpolate(step)));
                    rule.setName((count++)+" - AutoUnique - " + property.getXPath());
                    final Object valStr = value instanceof String && ((String) value).isEmpty() ? "''":value;
                    rule.setDescription(new DefaultDescription(new DefaultInternationalString(property.getXPath()+" = "+valStr),null));
                    rule.setFilter(filter);
                    newRules.add(rule);
                }
            }

            //add rules to the style
            final MutableStyle mutableStyle = StyleUtilities.type(style);
            //remove all auto unique values rules if exists before adding the new list.
            final List<MutableRule> backupRules = new ArrayList<>(mutableStyle.featureTypeStyles().get(0).rules());
            final List<MutableRule> rulesToRemove = new ArrayList<>();
            for(final MutableRule r : backupRules){
                if(r.getName().contains("AutoUnique")){
                    rulesToRemove.add(r);
                }
            }
            backupRules.removeAll(rulesToRemove);
            mutableStyle.featureTypeStyles().get(0).rules().clear();
            mutableStyle.featureTypeStyles().get(0).rules().addAll(backupRules);
            mutableStyle.featureTypeStyles().get(0).rules().addAll(newRules);

            //create the style in server
            styleBusiness.createStyle(type, mutableStyle);
            Style json = styleConverterBusiness.getJsonStyle(mutableStyle);
            return new ResponseEntity(json,OK);
        } catch(Exception ex) {
            LOGGER.log(Level.WARNING, ex.getLocalizedMessage(), ex);
            return new ErrorMessage(ex).build();
        }
    }


    @RequestMapping(value="/internal/styles/getChartDataJson", method=POST, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity getChartDataJson(@RequestBody ParameterValues params) {
        try {
            final int dataId = Integer.parseInt(params.get("dataId"));
            final String attribute = params.get("attribute");
            final int intervals = params.get("intervals") != null ? Integer.valueOf(params.get("intervals")):10;

            if(attribute ==null || attribute.trim().isEmpty()){
                return new ErrorMessage(UNPROCESSABLE_ENTITY)
                        .message("Attribute field should not be empty!")
                        .i18N(I18nCodes.Style.INVALID_ARGUMENT).build();
            }

            final ChartDataModel result = new ChartDataModel();

            final org.constellation.dto.Data data  = dataBusiness.getData(dataId);
            final Data dataP      = DataProviders.getProviderData(data.getProviderId(), data.getNamespace(), data.getName());
            final Resource rs     = dataP.getOrigin();

            if (rs instanceof FeatureSet fs) {

                final Map<Object,Long> mapping = new LinkedHashMap<>();
                final DefaultFilterFactory FF = FilterUtilities.FF;
                final ValueReference property = FF.property(attribute);

                final FeatureQuery query = new FeatureQuery();
                query.setProjection(attribute);
                fs = fs.subset(query);

                //check if property is numeric
                // if it is numeric then proceed to create intervals as the keys and get for each interval the feature count.
                // otherwise put each (string value, count) into the map

                final PropertyType p = fs.getType().getProperty(attribute);
                if (p instanceof AttributeType at) {
                    final Class cl = at.getValueClass();
                    result.setNumberField(Number.class.isAssignableFrom(cl));
                }

                try (final Stream<Feature> featureSet = fs.features(false)) {

                    if (result.isNumberField()) {
                        final Set<Double> values = new HashSet<>();
                        Iterator<Feature> it = featureSet.iterator();
                        while (it.hasNext()) {
                            final Feature feature = it.next();
                            final Number number = (Number) property.apply(feature);
                            if (number != null) {
                                final Double value = number.doubleValue();
                                values.add(value);
                            }
                        }

                        final Double[] allValues = values.toArray(Double[]::new);
                        double maximum = 0, minimum = 0;
                        if (allValues.length > 0) {
                            Arrays.sort(allValues);
                            minimum = allValues[0];
                            maximum = allValues[allValues.length-1];
                        }

                        result.setMinimum(minimum);
                        result.setMaximum(maximum);

                        double[] interValues = new double[intervals + 1];
                        for (int i = 0; i < interValues.length; i++) {
                            interValues[i] = minimum + ((maximum - minimum) * i / (interValues.length - 1))  ;
                        }

                        for (int i = 1; i < interValues.length; i++) {
                            double start = interValues[i - 1];
                            double end = interValues[i];
                            FeatureQuery qb = new FeatureQuery();
                            final Filter above = FF.greaterOrEqual(property, FF.literal(start));
                            final Filter under;
                            if (i == interValues.length - 1) {
                                under = FF.lessOrEqual(property, FF.literal(end));
                            } else {
                                under = FF.less(property, FF.literal(end));
                            }
                            final Filter interval = FF.and(above, under);
                            qb.setSelection(interval);
                            try (final Stream<Feature> subCol = fs.subset(qb).features(false)) {
                                mapping.put((long) start + " - " + (long) end, subCol.count());
                            }
                        }
                    } else {
                        Iterator<Feature> it = featureSet.iterator();
                        while(it.hasNext()){
                            final Feature feature = it.next();
                            Object value = property.apply(feature);
                            if(value == null){
                                value = "null";
                            }
                            Long count = mapping.get(value);
                            if(mapping.get(value)!=null){
                                count++;
                                mapping.put(value,count);
                            }else {
                                mapping.put(value,1L);
                            }
                        }

                        //adjust mapping size for performance in client side issue.
                        final Set<Object> keys = mapping.keySet();
                        final Map<Object,Long> newmap = new LinkedHashMap<>();
                        int limit = 100;
                        if(keys.size()>limit){
                            int gap = keys.size()/limit;
                            int i=1;
                            for(final Object key : keys){
                                if(i== gap){
                                    newmap.put(key,mapping.get(key));
                                    i=1;//reset i
                                }else {
                                    i++;//skip the key and increase i
                                }
                            }
                            mapping.clear();
                            mapping.putAll(newmap);
                        }
                    }

                }
                result.setMapping(mapping);
            }
            return new ResponseEntity(result,OK);
        } catch(Exception ex) {
            LOGGER.log(Level.WARNING, ex.getLocalizedMessage(), ex);
            return new ErrorMessage(ex).build();
        }
    }

    @RequestMapping(value="/internal/styles/histogram/{dataId}", method=GET, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity getHistogram(@PathVariable("dataId") int dataId) {

        try {
            final org.constellation.dto.Data data = dataBusiness.getData(dataId);
            if (data == null) {
                return new ErrorMessage().status(UNPROCESSABLE_ENTITY).i18N(I18nCodes.Style.NOT_FOUND).build();
            }
            if ("COVERAGE".equals(data.getType())) {
                final ImageStatistics stats = StatsUtilities.getDataStatistics(new StatInfo(data.getStatsState(), data.getStatsResult())).orElse(null);
                if (stats != null) {
                    return new ResponseEntity(stats,OK);
                }
                return new ErrorMessage(UNPROCESSABLE_ENTITY).message("Data is currently analysed, statistics not yet available.")
                        .i18N(I18nCodes.Style.INVALID_ARGUMENT).build();
            }
            return new ErrorMessage(UNPROCESSABLE_ENTITY).message("Data is not coverage type.")
                    .i18N(I18nCodes.Style.INVALID_ARGUMENT).build();
        } catch(TargetNotFoundException ex) {
            return new ErrorMessage(ex).status(UNPROCESSABLE_ENTITY).i18N(I18nCodes.Style.NOT_FOUND).build();
        } catch(Exception ex) {
            LOGGER.log(Level.WARNING, ex.getLocalizedMessage(), ex);
            return new ErrorMessage(ex).build();
        }
    }

    /**
     * Import style from an SLD or palette file CPT,CLR,PAL.
     */
    @RequestMapping(value="/internal/styles/import",method=POST,consumes=MULTIPART_FORM_DATA_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity importStyle(
            @RequestParam(value="type",required=false,defaultValue = "sld") String type,
            @RequestParam(name = "sName", required = false) String styleName,
            @RequestParam("data") MultipartFile file){
        if (file.isEmpty()) {
            return new ErrorMessage().message("SLD file to import is empty!").build();
        }
        //copy the file content in memory
        final byte[] buffer;
        try(InputStream fileIs = file.getInputStream()){
            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            IOUtilities.copy(fileIs, bos);
            fileIs.close();
            buffer = bos.toByteArray();
        }catch(IOException ex){
            LOGGER.log(Level.WARNING, "Error while retrieving SLD input", ex);
            return new ErrorMessage(ex).build();
        }

        //try to parse SLD from various form and version
        final List<MutableStyle> styles = new ArrayList<>();
        final StyleXmlIO io = new StyleXmlIO();
        MutableStyle style = null;

        //try to parse an SLD input
        MutableStyledLayerDescriptor sld = null;
        try {
            sld = io.readSLD(new ByteArrayInputStream(buffer), Specification.StyledLayerDescriptor.V_1_1_0);
        } catch (JAXBException | FactoryException ex) {
            LOGGER.log(Level.FINEST, ex.getMessage(),ex);
        }
        if(sld==null){
            try {
                sld = io.readSLD(new ByteArrayInputStream(buffer), Specification.StyledLayerDescriptor.V_1_0_0);
            } catch (JAXBException | FactoryException ex) {
                LOGGER.log(Level.FINEST, ex.getMessage(),ex);
            }
        }

        if (sld != null) {
            for (MutableLayer sldLayer : sld.layers()) {
                if(sldLayer instanceof NamedLayer nl) {
                    for (LayerStyle ls : nl.styles()) {
                        if (ls instanceof MutableStyle ms) {
                            styles.add(ms);
                        }
                    }
                } else if (sldLayer instanceof UserLayer ul) {
                    for (org.opengis.style.Style ls : ul.styles()){
                        if (ls instanceof MutableStyle ms) {
                            styles.add(ms);
                        }
                    }
                }
            }
            if (!styles.isEmpty()) {
                style = styles.remove(0);
            }
        }else{
            //try to parse a UserStyle input
            try {
                style = io.readStyle(new ByteArrayInputStream(buffer), Specification.SymbologyEncoding.V_1_1_0);
            } catch (JAXBException | FactoryException ex) {
                LOGGER.log(Level.FINEST, ex.getMessage(),ex);
            }
            if (style==null) {
                try {
                    style = io.readStyle(new ByteArrayInputStream(buffer), Specification.SymbologyEncoding.SLD_1_0_0);
                } catch (JAXBException | FactoryException ex) {
                    LOGGER.log(Level.FINEST, ex.getMessage(),ex);
                }
            }
            if (style == null) {
                //test cpt,clr,pal type
                String originalFilename = file.getOriginalFilename();
                if (originalFilename != null) {
                    originalFilename = originalFilename.toLowerCase();
                    ColorMap colormap = null;
                    try {
                        if (originalFilename.endsWith("cpt")) {
                            PaletteReader reader = new PaletteReader(PaletteReader.PATTERN_CPT);
                            colormap = reader.read(new String(buffer));
                        } else if (originalFilename.endsWith("clr")) {
                            PaletteReader reader = new PaletteReader(PaletteReader.PATTERN_CLR);
                            colormap = reader.read(new String(buffer));
                        } else if (originalFilename.endsWith("pal")) {
                            PaletteReader reader = new PaletteReader(PaletteReader.PATTERN_PAL);
                            colormap = reader.read(new String(buffer));
                        }
                    } catch (IOException ex) {
                        LOGGER.log(Level.FINEST, ex.getMessage(),ex);
                    }
                    if (colormap != null) {
                        final MutableStyleFactory SF = (MutableStyleFactory) DefaultFactories.forBuildin(StyleFactory.class);
                        RasterSymbolizer symbol = SF.rasterSymbolizer(null, null, null, null, colormap, null, null, null);
                        style = SF.style(symbol);
                    }
                }
            }
        }

        if(style==null){
            final String message = "Failed to import style from XML, no UserStyle element defined";
            LOGGER.log(Level.WARNING, message);
            return new ErrorMessage().message(message).build();
        }

        //log styles which have been ignored
        if(!styles.isEmpty()){
            final StringBuilder sb = new StringBuilder("Ignored styles at import :");
            for(MutableStyle ms : styles){
                sb.append(' ').append(ms.getName());
            }
            LOGGER.log(Level.FINEST, sb.toString());
        }

        //store imported style
        if(styleName != null && !styleName.isEmpty()) {
            style.setName(styleName);
        }
        try {
            final boolean exists = styleBusiness.existsStyle(type,style.getName());
            if (!exists) {
                return new ResponseEntity(styleBusiness.createStyle(type, style),OK);
            } else {
                return new ErrorMessage(UNPROCESSABLE_ENTITY).i18N(I18nCodes.Style.ALREADY_EXIST).build();
            }
        } catch(Exception ex) {
            LOGGER.log(Level.WARNING, ex.getLocalizedMessage(), ex);
            return new ErrorMessage(ex).build();
        }

    }


}
