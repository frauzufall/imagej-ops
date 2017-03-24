package net.imagej.ops.coloc.icq;

import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import net.imagej.ops.AbstractOp;
import net.imagej.ops.Ops;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;
import net.imglib2.util.IterablePair;

@Plugin(type=Ops.Coloc.ICQ.class)
public class HighLiICQ extends AbstractOp implements Ops.Coloc.ICQ {
	/** the resulting ICQ value. */
	@Parameter(type=ItemIO.OUTPUT)
	private double icqValue;

	@Parameter
	private Img<ByteType> img1;
	
	@Parameter
	private Img<ByteType> img2;
	
	@Override
	public void run() {
		
		DoubleType mean1 = ops().stats().mean(new DoubleType(), img1);
		DoubleType mean2 = ops().stats().mean(new DoubleType(), img2);

		Iterable<Pair<ByteType, ByteType>> pairs = new IterablePair<ByteType, ByteType>(img1, img2);

		icqValue = (double) ops().run(LiICQ.class, pairs, mean1.get(), mean2.get());
	}
}