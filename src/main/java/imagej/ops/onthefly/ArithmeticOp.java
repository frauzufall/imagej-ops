/*
 * #%L
 * A framework for reusable algorithms.
 * %%
 * Copyright (C) 2014 Board of Regents of the University of
 * Wisconsin-Madison and University of Konstanz.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package imagej.ops.onthefly;

import imagej.ops.Contingent;
import imagej.ops.Op;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;

import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtNewMethod;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.basictypeaccess.array.ArrayDataAccess;
import net.imglib2.img.planar.PlanarImg;

import org.scijava.Priority;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * Uses Javassist to generate optimized versions of the AddOp on-the-fly.
 * 
 * @author Johannes Schindelin
 */
public abstract class ArithmeticOp implements Op, Contingent {

	@Plugin(type = Op.class, name = "add", priority = Priority.HIGH_PRIORITY)
	public static class AddOp extends ArithmeticOp {
		@Override
		public void run() {
			run("add", "+");
		}
	}

	@Plugin(type = Op.class, name = "subtract", priority = Priority.HIGH_PRIORITY)
	public static class SubtractOp extends ArithmeticOp {
		@Override
		public void run() {
			run("subtract", "-");
		}
	}

	@Plugin(type = Op.class, name = "multiply", priority = Priority.HIGH_PRIORITY)
	public static class MultiplyOp extends ArithmeticOp {
		@Override
		public void run() {
			run("multiply", "*");
		}
	}

	@Plugin(type = Op.class, name = "divide", priority = Priority.HIGH_PRIORITY)
	public static class DivideOp extends ArithmeticOp {
		@Override
		public void run() {
			run("divide", "/");
		}
	}

	@Parameter
	private Object a;

	@Parameter
	private Object b;

	@Parameter
	private Object result;

	public interface MyOp {
		void run(Object a, Object b, Object result);
	}

	public void run(final String name, final String operator) {
		if (a instanceof ArrayImg) {
			final Object access = ((ArrayImg<?, ?>) this.a).update(null);
			if (access instanceof ArrayDataAccess) {
				final Object a = ((ArrayDataAccess<?>) access).getCurrentStorageArray();
				final Object b = ((ArrayDataAccess<?>) ((ArrayImg<?, ?>) this.b).update(null)).getCurrentStorageArray();
				final Object result = ((ArrayDataAccess<?>) ((ArrayImg<?, ?>) this.result).update(null)).getCurrentStorageArray();
				getMyOp(a.getClass(), name, operator).run(a, b, result);
				return;
			}
		}
		if (a instanceof PlanarImg) {
			final PlanarImg<?, ?> a = (PlanarImg<?, ?>) this.a;
			if (a.numSlices() == 0) return;
			final Object plane = ((ArrayDataAccess<?>) a.getPlane(0)).getCurrentStorageArray();
			final PlanarImg<?, ?> b = (PlanarImg<?, ?>) this.b;
			final PlanarImg<?, ?> result = (PlanarImg<?, ?>) this.result;
			getPlanarOp(plane.getClass(), name, operator).run(a, b, result);
			return;
		}
		throw new RuntimeException("This should not happen!");
	}

	@Override
	public boolean conforms() {
		if (result == b && a != b) return false;
		if (a instanceof ArrayImg && b instanceof ArrayImg && result instanceof ArrayImg) {
			final ArrayImg<?, ?> aImg = (ArrayImg<?, ?>) a;
			final ArrayImg<?, ?> bImg = (ArrayImg<?, ?>) b;
			if (!dimensionsMatch(aImg, bImg)) return false;
			final ArrayImg<?, ?> resultImg = (ArrayImg<?, ?>) result;
			if (!dimensionsMatch(aImg, resultImg)) return false;
			final Object aData = aImg.update(null);
			if (!(aData instanceof ArrayDataAccess)) return false;
			final Object bData = bImg.update(null);
			if (aData.getClass() != bData.getClass()) return false;
			final Object resultData = resultImg.update(null);
			if (aData.getClass() != resultData.getClass()) return false;
			return true;
		}
		if (a instanceof PlanarImg && b instanceof PlanarImg && result instanceof PlanarImg) {
			final PlanarImg<?, ?> aImg = (PlanarImg<?, ?>) a;
			final PlanarImg<?, ?> bImg = (PlanarImg<?, ?>) b;
			if (!dimensionsMatch(aImg, bImg)) return false;
			final PlanarImg<?, ?> resultImg = (PlanarImg<?, ?>) result;
			if (!dimensionsMatch(aImg, resultImg)) return false;
			int numSlices = aImg.numSlices();
			if (numSlices == 0 || numSlices != bImg.numSlices() || numSlices != resultImg.numSlices()) return false;
			final Object aData = aImg.getPlane(0);
			if (!(aData instanceof ArrayDataAccess)) return false;
			final Object bData = bImg.getPlane(0);
			if (aData.getClass() != bData.getClass()) return false;
			final Object resultData = resultImg.getPlane(0);
			if (aData.getClass() != resultData.getClass()) return false;
			return true;
		}
		return false;
	}

	private boolean dimensionsMatch(Img<?> aImg, Img<?> bImg) {
		final int numDimensions = aImg.numDimensions();
		if (numDimensions != bImg.numDimensions()) return false;
		for (int i = 0; i < numDimensions; i++) {
			if (aImg.dimension(i) != bImg.dimension(i)) return false;
		}
		return true;
	}

	private final static Map<String, MyOp> ops = new HashMap<String, MyOp>();
	private final static ClassLoader loader;
	private final static ClassPool pool;

	static {
		loader = new URLClassLoader(new URL[0]);
		pool = new ClassPool(false);
		pool.appendClassPath(new ClassClassPath(AddOp.class));
	}

	private MyOp getMyOp(final Class<?> forClass, final String name, final String operator) {
		final String componentType = forClass.getComponentType().getSimpleName();
		final String myOpName = "myOp$" + name + "$" + componentType;
		MyOp op = ops.get(myOpName);
		if (op != null) return op;

		try {
			final String type = forClass.getSimpleName();
			final CtClass clazz = pool.makeClass(myOpName, pool.get(Object.class.getName()));
			clazz.addInterface(pool.get(MyOp.class.getName()));
			final String src =
					"public void run(java.lang.Object a, java.lang.Object b, java.lang.Object result) {\n"
				+ "  " + type + " a2 = (" + type + ") a;\n"
				+ "  " + type + " b2 = (" + type + ") b;\n"
				+ "  " + type + " result2 = (" + type + ") result;\n"
				+ "  for (int i = 0; i < a2.length; i++) {\n"
				+ "    result2[i] = (" + componentType + ") (a2[i] " + operator + " b2[i]);\n"
				+ "  }\n"
				+ "}";
			clazz.addMethod(CtNewMethod.make(src, clazz));
			op = (MyOp) clazz.toClass(loader, null).newInstance();
			ops.put(myOpName, op);
			return op;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	private MyOp getPlanarOp(Class<?> forClass, String name, String operator)
	{
		final String componentType = forClass.getComponentType().getSimpleName();
		final String myOpName = "myOp$planar$" + name + "$" + componentType;
		MyOp op = ops.get(myOpName);
		if (op != null) return op;

		try {
			final String imgType = PlanarImg.class.getName();
			final String type = forClass.getSimpleName();
			final CtClass clazz = pool.makeClass(myOpName, pool.get(Object.class.getName()));
			clazz.addInterface(pool.get(MyOp.class.getName()));
			final String src =
					"public void run(java.lang.Object a, java.lang.Object b, java.lang.Object result) {\n"
				+ "  " + imgType + " a2 = (" + imgType + ") a;\n"
				+ "  " + imgType + " b2 = (" + imgType + ") b;\n"
				+ "  " + imgType + " result2 = (" + imgType + ") result;\n"
				+ "  for (int j = 0; j < a2.numSlices(); j++) {\n"
				+ "    " + type + " a3 = (" + type + ") a2.getPlane(j).getCurrentStorageArray();\n"
				+ "    " + type + " b3 = (" + type + ") b2.getPlane(j).getCurrentStorageArray();\n"
				+ "    " + type + " result3 = (" + type + ") result2.getPlane(j).getCurrentStorageArray();\n"
				+ "    for (int i = 0; i < a3.length; i++) {\n"
				+ "      result3[i] = (" + componentType + ") (a3[i] " + operator + " b3[i]);\n"
				+ "    }\n"
				+ "  }\n"
				+ "}";
			clazz.addMethod(CtNewMethod.make(src, clazz));
			op = (MyOp) clazz.toClass(loader, null).newInstance();
			ops.put(myOpName, op);
			return op;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}
}