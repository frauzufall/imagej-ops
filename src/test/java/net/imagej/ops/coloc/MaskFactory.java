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

import java.util.Arrays;

import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

public class MaskFactory {
	
	public enum CombinationMode {
		AND, OR, NONE
	}
	
	/**
	 * Create a new mask image without any specific content, but with
	 * a defined size.
	 */
	public static RandomAccessibleInterval<BitType> createMask(long[] dim) {
		ImgFactory< BitType > imgFactory = new ArrayImgFactory< BitType >();
		return imgFactory.create(dim, new BitType());
	}
	
	/**
	 * Create a new mask image with a defined size and preset content.
	 */
	public static RandomAccessibleInterval<BitType> createMask(long[] dim, boolean val) {
		RandomAccessibleInterval<BitType> mask = createMask(dim);
		
		for (BitType t : Views.iterable(mask))
			t.set(val);
		
		return mask;
	}
	
	/**
	 * Create a new mask image with a defined size and preset content.
	 */
	public static RandomAccessibleInterval<BitType> createMask(long[] dim, long[] roiOffset, long[] roiDim) {
		if (dim.length != roiOffset.length || dim.length != roiDim.length) {
			throw new IllegalArgumentException("The dimensions of the mask as well as the ROIs and his offset must be the same.");
		}

		final RandomAccessibleInterval<BitType> mask = createMask(dim);
		final int dims = mask.numDimensions();
		final long[] pos = new long[dims];
		

		// create an array with the max corner of the ROI
		final long[] roiOffsetMax = new long[dims];
		for (int i=0; i<dims; ++i)
			roiOffsetMax[i] = roiOffset[i] + roiDim[i];
		// go through the mask and mask points as valid that are in the ROI
		Cursor<BitType> cursor = Views.iterable(mask).localizingCursor();
		while ( cursor.hasNext() ) {
			cursor.fwd();
			cursor.localize(pos);
			boolean valid = true;
			// test if the current position is contained in the ROI
			for(int i=0; i<dims; ++i)
				valid &= pos[i] >= roiOffset[i] && pos[i] < roiOffsetMax[i];
			cursor.get().set(valid);
		}

		return mask;
	}

	/**
	 * Creates a new mask of the given dimensions, based on the image data
	 * in the passed image. If the requested dimensionality is higher than
	 * what is available in the data, the data gets repeated in the higher
	 * dimensions.
	 *
	 * @param dim The dimensions of the new mask image
	 * @param origMask The image from which the mask should be created from
	 */
	public static<T extends RealType< T >> RandomAccessibleInterval<BitType> createMask(
			final long[] dim, final RandomAccessibleInterval<T> origMask) {
		final RandomAccessibleInterval<BitType> mask = createMask(dim);
		final long[] origDim = new long[ origMask.numDimensions() ];
		origMask.dimensions(origDim);

		// test if original mask and new mask have same dimensions
		if (Arrays.equals(dim, origDim)) {
			// copy the input image to the mask output image
			Cursor<T> origCursor = Views.iterable(origMask).localizingCursor();
			RandomAccess<BitType> maskCursor = mask.randomAccess();
			while (origCursor.hasNext()) {
				origCursor.fwd();
				maskCursor.setPosition(origCursor);
				boolean value = origCursor.get().getRealDouble() > 0.001;
				maskCursor.get().set(value);
			}
		} else if (dim.length > origDim.length) {
			// sanity check
			for (int i=0; i<origDim.length; i++) {
				if (origDim[i] != dim[i])
					throw new UnsupportedOperationException("Masks with lower dimensionality than the image, "
							+ " but a different extent are not yet supported.");
			}
			// mask and image have different dimensionality and maybe even a different extent
			Cursor<T> origCursor = Views.iterable(origMask).localizingCursor();
			RandomAccess<BitType> maskCursor = mask.randomAccess();
			final long[] pos = new long[ origMask.numDimensions() ];
			// iterate over the original mask
			while (origCursor.hasNext()) {
				origCursor.fwd();
				origCursor.localize(pos);
				boolean value = origCursor.get().getRealDouble() > 0.001;
				// set available (lower dimensional) position information
				for (int i=0; i<origDim.length; i++)
					// setPosition requires first the position and then the dimension
					maskCursor.setPosition(pos[i], i);
				// go through the missing dimensions and set the value
				for (int i=origDim.length; i<dim.length; i++)
					for (int j=0; j<dim[i]; j++) {
						// setPosition requires first the position and then the dimension
						maskCursor.setPosition(j, i);
						maskCursor.get().set(value);
					}
			}
		} else if (dim.length < origDim.length) {
			// mask has more dimensions than image
			throw new UnsupportedOperationException("Masks with more dimensions than the image are not supported, yet.");
		} else {
			// mask and image have a different extent, but are equal in dimensionality. Scale it?
			throw new UnsupportedOperationException("Masks with same dimensionality, but a different extent than the image are not supported, yet.");
		}

		return mask;
	}
}
