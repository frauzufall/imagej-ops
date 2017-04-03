package net.imagej.ops.hough;

import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import net.imagej.ops.special.function.AbstractUnaryFunctionOp;
import net.imglib2.Cursor;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.IterableInterval;
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.BooleanType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

@Plugin( type = HoughCircleTransformOp.class )
public class HoughTransformOpWeights< T extends BooleanType< T >, R extends RealType< R > >
		extends AbstractUnaryFunctionOp< IterableInterval< T >, Img< DoubleType > >
{

	@Parameter
	private StatusService statusService;

	@Parameter( label = "Min circle radius", description = "Minimal radius, in pixel units, for the transform.", min = "1", type = ItemIO.INPUT )
	private int minRadius = 1;

	@Parameter( label = "Max circle radius", description = "Maximal radius, in pixel units, for the transform.", min = "1", type = ItemIO.INPUT )
	private int maxRadius = 50;

	@Parameter( label = "Step radius", description = "Radius step, in pixel units, for the transform.", min = "1", type = ItemIO.INPUT, required = false )
	private int stepRadius = 1;
	
	@Parameter( label = "Weights", description = "Weight image for the vote image.", type = ItemIO.INPUT )
	private RandomAccessible< R > weights;

	@Override
	public Img< DoubleType > calculate( final IterableInterval< T > input )
	{
		final int numDimensions = input.numDimensions();

		if ( input.numDimensions() != 2 ) { throw new IllegalArgumentException(
				"Cannot compute Hough circle transform for non-2D images. Got " + numDimensions + "D image." ); }

		maxRadius = Math.max( minRadius, maxRadius );
		minRadius = Math.min( minRadius, maxRadius );
		final int nRadiuses = ( maxRadius - minRadius ) / stepRadius + 1;

		/*
		 * Voting image.
		 */

		// Get a suitable image factory.
		final long[] dims = new long[ numDimensions + 1 ];
		for ( int d = 0; d < numDimensions; d++ )
			dims[ d ] = input.dimension( d );
		dims[ numDimensions ] = nRadiuses;
		final Dimensions dimensions = FinalDimensions.wrap( dims );
		final ImgFactory< DoubleType > factory = ops().create().imgFactory( dimensions );
		final Img< DoubleType > votes = factory.create( dimensions, new DoubleType() );

		/*
		 * Hough transform.
		 */

		final DoubleType weight = new DoubleType( Double.NaN );
		final RandomAccess< R > raWeight = weights.randomAccess( input );
		final double sum = ops().stats().sum( input ).getRealDouble();
		int progress = 0;

		final Cursor< T > cursor = input.localizingCursor();
		while ( cursor.hasNext() )
		{
			cursor.fwd();
			if ( !cursor.get().get() )
				continue;

			raWeight.setPosition( cursor );
			weight.set( raWeight.get().getRealDouble() );

			for ( int i = 0; i < nRadiuses; i++ )
			{
				final IntervalView< DoubleType > slice = Views.hyperSlice( votes, numDimensions, i );
				final RandomAccess< DoubleType > ra = Views.extendZero( slice ).randomAccess();
				final int r = minRadius + i * stepRadius;
				midPointAlgorithm( cursor, r, ra, weight );
			}

			statusService.showProgress( ++progress, ( int ) sum );
		}

		return votes;
	}

	private static final < R extends RealType< R > > void midPointAlgorithm( final Localizable position, final int radius, final RandomAccess< DoubleType > ra, final DoubleType weight )
	{
		final int x0 = position.getIntPosition( 0 );
		final int y0 = position.getIntPosition( 1 );

		/*
		 * We "zig-zag" through indices, so that we reconstruct a continuous set
		 * of of x,y coordinates, starting from the top of the circle.
		 */

		final int octantSize = ( int ) Math.floor( ( Math.sqrt( 2 ) * ( radius - 1 ) + 4 ) / 2 );

		int x = 0;
		int y = radius;
		int f = 1 - radius;
		int dx = 1;
		int dy = -2 * radius;

		for ( int i = 2; i < octantSize; i++ )
		{
			// We update x & y
			if ( f > 0 )
			{
				y = y - 1;
				dy = dy + 2;
				f = f + dy;
			}
			x = x + 1;
			dx = dx + 2;
			f = f + dx;

			// 1st octant.
			ra.setPosition( x0 + x, 0 );
			ra.setPosition( y0 + y, 1 );
			ra.get().add( weight );

			// 2nd octant.
			ra.setPosition( x0 - x, 0 );
			ra.get().add( weight );

			// 3rd octant.
			ra.setPosition( x0 + x, 0 );
			ra.setPosition( y0 - y, 1 );
			ra.get().add( weight );

			// 4th octant.
			ra.setPosition( x0 - x, 0 );
			ra.get().add( weight );

			// 5th octant.
			ra.setPosition( x0 + y, 0 );
			ra.setPosition( y0 + x, 1 );
			ra.get().add( weight );

			// 6th octant.
			ra.setPosition( x0 - y, 0 );
			ra.get().add( weight );

			// 7th octant.
			ra.setPosition( x0 + y, 0 );
			ra.setPosition( y0 - x, 1 );
			ra.get().add( weight );

			// 8th octant.
			ra.setPosition( x0 - y, 0 );
			ra.get().add( weight );
		}
	}
}
