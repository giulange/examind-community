/*
 *    Examind community - An open source and standard compliant SDI
 *    https://community.examind.com/
 *
 * Copyright 2022 Geomatys.
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
package com.examind.image.heatmap;

import java.awt.geom.Rectangle2D;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferDouble;
import org.apache.sis.geometry.Envelope2D;
import org.apache.sis.geometry.Envelopes;
import org.apache.sis.image.ComputedImage;
import org.apache.sis.internal.system.Loggers;
import org.apache.sis.util.collection.BackingStoreException;
import org.geotoolkit.util.CollectorsExt;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BandedSampleModel;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Simple implementation for HeatMap computation based on :
 * https://en.wikipedia.org/wiki/Multivariate_kernel_density_estimation
 * https://mapserver.org/output/kerneldensity.html
 * It uses an isotropic Gaussian function as Kernel.
 */
public final class HeatMapImage extends ComputedImage {

    /**
     * Points source to be used to compute the heatMap
     */
    private final PointCloudResource dataSource;
    private final Dimension imageDimension;
    private final Dimension tilingDimension;
    /**
     * MathTransform2D to compute coordinate in a given CRS from pixel corner of the current {@link HeatMapImage}.
     */
    private final MathTransform gridCornerToDataCRS;

    /**
     * MathTransform2D to compute coordinate in a given CRS from pixel center of the current {@link HeatMapImage}.
     */
    private final MathTransform dataCRSToGridCenter;

    private final double distanceX;
    private final double distanceY;

    //todo MAX_COMPUTATION: uncomment using atomic? Could be used to Colormodel definition
//    private float max = 0;

    /**
     * amplitude - default value 1
     */
    final double a = 1;
    /**
     * Standard deviation in x-direction
     */

    private final DistanceOp op;

    /**
     * TODO
     *  @param imageDimension  : not null, dimension in pixel of the computed image
     *
     * @param tilingDimension : not null, the dimension to be used to define the tiles of the computed image.
     * @param dataSource      : points source to be used to compute the heatMap.
     * @param distanceX       : distance on the 1st direction (x) to be used to compute the gaussian function. THe distance is in **pixels**
     * @param distanceY       : distance on the 2nd direction (y) to be used to compute the gaussian function The distance is in **pixels**
     * @param algorithm
     */
    HeatMapImage(final Dimension imageDimension, final Dimension tilingDimension, final PointCloudResource dataSource,
                 final MathTransform dataCrsToGridCenter, final MathTransform gridCornerToDataCrs,
                 final double distanceX, final double distanceY,
                 Algorithm algorithm) {
        super(new BandedSampleModel(DataBuffer.TYPE_DOUBLE, tilingDimension.width, tilingDimension.height, 1));
        this.tilingDimension = tilingDimension;
        this.imageDimension = imageDimension;
        this.dataSource = dataSource;

        this.gridCornerToDataCRS = gridCornerToDataCrs;
        this.dataCRSToGridCenter = dataCrsToGridCenter;

        this.distanceX = distanceX;
        this.distanceY = distanceY;

        this.op = switch (algorithm) {
            case GAUSSIAN -> new Gaussian(distanceX, distanceY);
            case EUCLIDEAN -> new Euclidean(distanceX, distanceY);
        };
    }


    /**
     * {@inheritDoc}
     */
    @Override
    protected Raster computeTile(int tileX, int tileY, WritableRaster previous) throws Exception {
        final int startXPixel =  this.getMinX() + Math.multiplyExact((tileX - getMinTileX()), getTileWidth());
        final int startYPixel =  this.getMinY() + Math.multiplyExact((tileY - getMinTileY()), getTileHeight());

        final Envelope2D imageGrid = new Envelope2D(null, startXPixel - distanceX, startYPixel - distanceY, tilingDimension.width + distanceX * 2, tilingDimension.height + distanceY * 2);
        var roi = Envelopes.transform(this.gridCornerToDataCRS, imageGrid);
        if (previous != null) {
            Logger.getLogger(Loggers.APPLICATION).log(Level.FINE, "Reuse of previous raster not implemented yet in HeatMapImage.class");
        }

        final Rectangle tileBoundary = new Rectangle(startXPixel, startYPixel, tilingDimension.width, tilingDimension.height);
        try (final Stream<? extends Point2D> points = this.dataSource.points(roi, false)) {
            var samples = points.collect(CollectorsExt.buffering(1000, CollectorsExt.sink(new double[getTileWidth() * getTileHeight()], (s, p) -> {
                final int nPoints = p.size();
                if (nPoints < 1) return;

                var geoPts = new double[nPoints * 2];
                {
                    int i = 0;
                    for (Point2D pt : p) {
                        geoPts[i++] = pt.getX();
                        geoPts[i++] = pt.getY();
                    }
                }
                var packedPts = new double[nPoints * 2];
                try {
                    dataCRSToGridCenter.transform(geoPts, 0, packedPts, 0, nPoints);
                } catch (TransformException e) {
                    throw new BackingStoreException("Cannot project data points in image space", e);
                }

                for (int i = 0 ; i < packedPts.length ; i += 2) {
                    writeGridPoint(packedPts[i], packedPts[i+1], s, tileBoundary);
                }
            })));

            final DataBufferDouble db = new DataBufferDouble(samples, samples.length);
            return  WritableRaster.createWritableRaster(getSampleModel(), db, new Point(startXPixel, startYPixel));
        }
    }

    /**
     * Helper method for debug. Remove once not needed anymore.
     */
    private static Rectangle2D getBounds(double[] values) {
        if (values.length < 2) return new Rectangle2D.Double();
        else if (values.length < 4) return new Rectangle2D.Double(values[0], values[1], 0, 0);

        double minX, maxX, minY, maxY;
        minX = maxX = values[0];
        minY = maxY = values[1];

        for (int i = 2 ; i < values.length ; i+=2) {
            minX = Math.min(minX, values[i]);
            maxX = Math.max(maxX, values[i]);

            minY = Math.min(minY, values[i+1]);
            maxY = Math.max(maxY, values[i+1]);
        }

        return new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);
    }

    private void writeGridPoint(double x, double y, double[] tileData, Rectangle tileBoundary) {
        final Rectangle2D ptInfluence = new Rectangle2D.Double(x - distanceX, y - distanceY, distanceX * 2, distanceY * 2);
        final Rectangle pixels = tileBoundary.createIntersection(ptInfluence).getBounds();
        if (pixels.isEmpty()) return;
        for (int j = pixels.y; j < pixels.y + pixels.height ; j++) {
            for (int i = pixels.x; i < pixels.x + pixels.width; i++) {
                tileData[(j - tileBoundary.y) * tileBoundary.width + (i - tileBoundary.x)] += a * op.apply(i - x, j - y); // applyGaussian2D(i, j, x, y);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ColorModel getColorModel() {
        //TODO?
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getWidth() {
        return this.imageDimension.width;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getHeight() {
        return this.imageDimension.height;
    }

//    /**
//     * todo MAX_COMPUTE
//     * @return
//     */
//    public float getMax() {
//        return max;
//    }

    public enum Algorithm {
        EUCLIDEAN, GAUSSIAN
    }

    @FunctionalInterface
    private interface DistanceOp {
        double apply(double vx, double vy);
    }

    private static final class Gaussian implements DistanceOp {

        /**
         * - 1 / (2 . σx²)
         */
        final double invσx2;
        /**
         * - 1 / (2 . σy²)
         */
        final double invσy2;

        Gaussian(double distanceX, double distanceY) {
            var σx = distanceX / 3d;
            var σy = distanceY / 3d;
            this.invσx2 = -1 / (2 * σx * σx); // -1 to prepare the exponential exponent.
            this.invσy2 = -1 / (2 * σy * σy); // -1 to prepare the exponential exponent.
        }

        @Override
        public double apply(double vx, double vy) {
            return Math.exp(vx * vx * invσx2 + vy * vy * invσy2);
        }
    }

    private static final class Euclidean implements DistanceOp {

        private final double maxSquaredNorm;

        Euclidean(double distanceX, double distanceY) {
            this.maxSquaredNorm = distanceX * distanceX + distanceY * distanceY;
        }

        @Override
        public double apply(double vx, double vy) {
            var vectorSquaredNorm = vx * vx + vy * vy;
            var squaredDistanceFromEdge = maxSquaredNorm - vectorSquaredNorm;
            if (squaredDistanceFromEdge <= 0) return 0;
            return squaredDistanceFromEdge / maxSquaredNorm;
        }
    }
}
