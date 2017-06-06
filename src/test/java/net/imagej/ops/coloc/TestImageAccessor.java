/*-
 * #%L
 * Fiji's plugin for colocalization analysis.
 * %%
 * Copyright (C) 2009 - 2017 Fiji developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package net.imagej.ops.coloc;

import static org.junit.Assume.assumeNotNull;

import java.awt.Color;
import java.io.BufferedInputStream;
import java.io.InputStream;

import ij.ImagePlus;
import ij.gui.NewImage;
import ij.io.Opener;
import ij.process.ImageProcessor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

/**
 * A class containing some testing helper methods. It allows
 * to open Tiffs from within the Jar file and can generate noise
 * images.
 *
 * @author Dan White
 * @author Tom Kazimiers
 */
public class TestImageAccessor {
	/* a static opener for opening images without the
	 * need for creating every time a new opener
	 */
	static Opener opener = new Opener();

	/**
	 * Loads a Tiff file from within the jar to use as a mask Cursor.
	 * So we use Img<T> which has a cursor() method. 
	 * The given path is treated
	 * as relative to this tests-package (i.e. "Data/test.tiff" refers
	 * to the test.tiff in sub-folder Data).
	 *
	 * @param <T> The wanted output type.
	 * @param relPath The relative path to the Tiff file.
	 * @return The file as ImgLib image.
	 */
	public static <T extends RealType<T> & NativeType<T>> Img<T> loadTiffFromJar(String relPath) {
		InputStream is = TestImageAccessor.class.getResourceAsStream(relPath);
		BufferedInputStream bis = new BufferedInputStream(is);

		ImagePlus imp = opener.openTiff(bis, "The Test Image");
		assumeNotNull(imp);
		return ImagePlusAdapter.wrap(imp);
	}

	/**
	 * Creates a noisy image that is created by repeatedly adding points
	 * with random intensity to the canvas. That way it tries to mimic the
	 * way a microscope produces images.
	 *
	 * @param <T> The wanted output type.
	 * @param width The image width.
	 * @param height The image height.
	 * @param dotSize The size of the dots.
	 * @param numDots The number of dots.
	 * @param smoothingSigma The two dimensional sigma for smoothing.
	 * @return The noise image.
	 */
	public static <T extends RealType<T> & NativeType<T>> RandomAccessibleInterval<T> produceNoiseImage(int width,
			int height, float dotSize, int numDots) {
		/* For now (probably until ImageJ2 is out) we use an
		 * ImageJ image to draw circles.
		 */
		int options = NewImage.FILL_BLACK + NewImage.CHECK_AVAILABLE_MEMORY;
	        ImagePlus img = NewImage.createByteImage("Noise", width, height, 1, options);
		ImageProcessor imp = img.getProcessor();

		float dotRadius = dotSize * 0.5f;
		int dotIntSize = (int) dotSize;

		for (int i=0; i < numDots; i++) {
			int x = (int) (Math.random() * width - dotRadius);
			int y = (int) (Math.random() * height - dotRadius);
			imp.setColor(Color.WHITE);
			imp.fillOval(x, y, dotIntSize, dotIntSize);
		}
		// we changed the data, so update it
		img.updateImage();
		// create the new image
		RandomAccessibleInterval<T> noiseImage = ImagePlusAdapter.wrap(img);

		return noiseImage;
	}
}
