package net.imagej.ops.topology.eulerCharacteristic;

import net.imagej.ops.AbstractOpTest;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;
import org.junit.Test;

import static net.imagej.ops.topology.eulerCharacteristic.TestHelper.drawCube;
import static org.junit.Assert.assertEquals;

/**
 * Tests for the {@link EulerCharacteristic26N EulerCharacteristic26N} op
 *
 * @author Richard Domander (Royal Veterinary College, London)
 */
public class EulerCharacteristic26NTest extends AbstractOpTest {
    @Test(expected = IllegalArgumentException.class)
    public void testConforms() throws AssertionError {
        final Img<BitType> img = ArrayImgs.bits(3, 3);

        ops.topology().eulerCharacteristic26N(img);
    }

    @Test
    public void testNeighborhoodEulerIndex() throws Exception {
        final Img<BitType> bits = ArrayImgs.bits(2, 2, 2);
        final RandomAccess<BitType> neighborhood = bits.randomAccess();

        // Create and test all 256 configurations of a 2x2x2 neighborhood
        for (int n = 0; n < 256; n++) {
            setNeighbor(neighborhood, 0, 0, 0, n & 0b00000001);
            setNeighbor(neighborhood, 1, 0, 0, n & 0b00000010);
            setNeighbor(neighborhood, 0, 1, 0, n & 0b00000100);
            setNeighbor(neighborhood, 1, 1, 0, n & 0b00001000);
            setNeighbor(neighborhood, 0, 0, 1, n & 0b00010000);
            setNeighbor(neighborhood, 1, 0, 1, n & 0b00100000);
            setNeighbor(neighborhood, 0, 1, 1, n & 0b01000000);
            setNeighbor(neighborhood, 1, 1, 1, n & 0b10000000);

            final int result = EulerCharacteristic26N.neighborhoodEulerIndex(neighborhood, 0, 0, 0);

            assertEquals("LUT index is incorrect", n, result);
        }
    }

    /**
     * Test with a single voxel (=solid cube) that floats in the middle of a 3x3x3 space
     * <p>
     * Here χ = β_0 - β_1 + β_2 = 1 - 0 + 0 = 1.
     * The formula χ = vertices - edges + faces for surfaces of polyhedra doesn't apply because the cuboid is solid.
     */
    @Test
    public void testCube() throws Exception {
        final Img<BitType> img = drawCube(1, 1, 1, 1);

        final Double result = ops.topology().eulerCharacteristic26N(img);

        assertEquals("Euler characteristic (χ) is incorrect", 1.0, result, 1e-12);
    }

    /**
     * Test with a single voxel (=solid cube) in a 1x1x1 space
     * <p>
     * Result differs from {@link #testCube} because voxel touches edges (edge correction)
     */
    @Test
    public void testEdgeCube() throws Exception {
        final Img<BitType> img = drawCube(1, 1, 1, 0);

        final Double result = ops.topology().eulerCharacteristic26N(img);

        assertEquals("Euler characteristic (χ) is incorrect", 0.0, result, 1e-12);
    }

    /**
     * Test with a cube that has a cavity inside
     * <p>
     * Here χ = β_0 - β_1 + β_2 = 1 - 0 + 1 = 2
     */
    @Test
    public void testHollowCube() throws Exception {
        final Img<BitType> img = drawCube(3, 3, 3, 1);
        final RandomAccess<BitType> access = img.randomAccess();

        // Add a cavity
        access.setPosition(new long[]{2, 2, 2});
        access.get().setZero();

        final Double result = ops.topology().eulerCharacteristic26N(img);

        assertEquals("Euler characteristic (χ) is incorrect", 2.0, result, 1e-12);
    }

    /**
     * Test with a cube that has a "handle"
     * <p>
     * Here χ = β_0 - β_1 + β_2 = 1 - 1 + 0 = 0
     */
    @Test
    public void testHandleCube() throws Exception {
        final Img<BitType> cube = drawCube(9, 9, 9, 5);
        final RandomAccess<BitType> access = cube.randomAccess();

        // Draw a handle on the front xy-face of the cuboid
        access.setPosition(9, 0);
        access.setPosition(6, 1);
        access.setPosition(4, 2);
        access.get().setOne();
        access.setPosition(3, 2);
        access.get().setOne();
        access.setPosition(7, 1);
        access.get().setOne();
        access.setPosition(8, 1);
        access.get().setOne();
        access.setPosition(4, 2);
        access.get().setOne();

        final Double result = ops.topology().eulerCharacteristic26N(cube);

        assertEquals("Euler characteristic (χ) is incorrect", 0.0, result, 1e-12);
    }

    //region -- Helper methods --
    private void setNeighbor(RandomAccess<BitType> access, long x, long y, long z, long value) {
        access.setPosition(x, 0);
        access.setPosition(y, 1);
        access.setPosition(z, 2);
        access.get().setInteger(value);
    }
    //endregion
}