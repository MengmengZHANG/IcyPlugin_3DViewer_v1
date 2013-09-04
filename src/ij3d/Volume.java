package ij3d;

import icy.canvas.Canvas3D;
import icy.file.Saver;
import icy.image.colormap.IcyColorMap;
import icy.main.Icy;
import icy.sequence.Sequence;
import icy.type.DataType;
//import ij.ImagePlus;
//import ij.ImageStack;
//import ij.measure.Calibration;

import java.io.File;

import javax.vecmath.Point3d;

/**
 * This class encapsulates an image stack and provides various methods for
 * retrieving data. It is possible to control the loaded color channels of
 * RGB images, and to specify whether or not to average several channels
 * (and merge them in this way into one byte per pixel).
 *
 * Depending on these settings, and on the type of image given at construction
 * time, the returned data type is one of INT_DATA or BYTE_DATA.
 *
 * @author Benjamin Schmid
 */
public class Volume {

	private int[] rLUT = new int[256];
	private int[] gLUT = new int[256];
	private int[] bLUT = new int[256];
	private int[] aLUT = new int[256];
	
	/**
	 * @author Mengmeng
	 */
	private static int scale = 1; 
	
	/**
	 * Data is read as int data.
	 * If the input image is RGB, this is the case if isDefaultLUT()
	 * returns false or more than a single channel is used. If the input
	 * image is 8 bit, again this is the case if isDefaultLUT() returns
	 * false.
	 */
	public static final int INT_DATA = 0;

	/**
	 * Data is read as byte data.
	 * If the input image is RGB, this is the case if isDefaultLUT()
	 * returns true and only a single channel is used. If the input
	 * image is 8 bit, again this is the case if isDefaultLUT() returns
	 * true.
	 */
	public static final int BYTE_DATA = 1;

	/**
	 * @author Mengmeng
	 * @param sequence Sequence
	 */
	/** The image holding the data */
	protected Sequence sequence;
//	/** The image holding the data */
//	protected ImagePlus imp;

	/**
	 * @author Mengmeng
	 * @param sequence Sequence
	 */
	/** Wraping the ImagePlus */
	protected Img image;

	/** The loader, initialized depending on the data type */
	protected Loader loader;

	/**
	 * Indicates in which format the data is loaded. This depends on
	 * the image type and on the number of selected channels.
	 * May be one of INT_DATA or BYTE_DATA
	 */
	protected int dataType;

	/** Flag indicating that the channels should be averaged */
	protected boolean average = false;

	/** Flag indicating that channels should be saturated */
	protected boolean saturatedVolumeRendering = false;

	/** Channels in RGB images which should be loaded */
	protected boolean[] channels = new boolean[] {true, true, true};

	/** The dimensions of the data */
	public int xDim, yDim, zDim;

	/** The calibration of the data */
	//==pixelSize
	public double pw, ph, pd;


	/** The minimum coordinate of the data */
	public final Point3d minCoord = new Point3d();

	/** The maximum coordinate of the data */
	public final Point3d maxCoord = new Point3d();

	/** Create instance with a null imp. */
	protected Volume() {
		this.image = null;
	}

	/**
	 * @author Mengmeng
	 * @param sequence Sequence
	 * Initializes this Volume with the specified image.
	 * All channels are used.
	 * @param imp
	 */
	public Volume(Sequence sequence) {
		this(sequence, new boolean[] {true, true, true});
	}

	/**
	 * @author Mengmeng
	 * @param sequence Sequence
	 * Initializes this Volume with the specified image and channels.
	 * @param imp
	 * @param ch A boolean[] array of length three, which indicates whether
	 * the red, blue and green channel should be read. This has only an
	 * effect when reading color images.
	 */
	public Volume(Sequence sequence, boolean[] ch) {
		setSequence(sequence, ch);
	}


	/**
	 * @author Mengmeng
	 * @param sequence Sequence
	 */
	private void setLUTsFromSequence(Sequence sequence) {
		switch(sequence.getDataType_()) {
			case UBYTE:
				IcyColorMap cm = sequence.getColorModel().getColormap(0) ;
				//cm.setDefaultAlphaFor3D() ;
				for(int i = 0; i < 256; i++) {
					rLUT[i] = cm.getRed(i);
					gLUT[i] = cm.getGreen(i);
					bLUT[i] = cm.getBlue(i);
					//aLUT[i] = cm.getAlpha(i) ;	
					aLUT[i] = Math.min(254, (rLUT[i] + gLUT[i] + bLUT[i]) / 3);
				}

				break;
			default:
				throw new IllegalArgumentException("Unsupported image type");
		}
	}
	
	/**
	 * @author Mengmeng
	 * @param sequence Sequence
	 */
	public void setSequence(Sequence sequence, boolean[] ch) {
		this.sequence = sequence;
		this.channels = ch;
		DataType type = sequence.getDataType_() ;
		switch(type) {
			case UBYTE :
				image = new ByteImage(sequence);
				break;
			case UINT:
				image = new IntImage(sequence);
				break;
			default:
				throw new IllegalArgumentException("Unsupported image type");
		}
		setLUTsFromSequence(this.sequence);

		xDim = sequence.getWidth();
		yDim = sequence.getHeight();
		zDim = sequence.getSizeZ();

		
		/**
		 * @author Mengmeng
		 * add pixel size
		 */
		setPixelSizeX(sequence.getPixelSizeX());
		setPixelSizeY(sequence.getPixelSizeY());
		setPixelSizeZ(sequence.getPixelSizeZ());
		
		float xSpace = (float)getPixelSizeX();
		float ySpace = (float)getPixelSizeY();
		float zSpace = (float)getPixelSizeZ() ;

		// real coords
		minCoord.x = 0;
		minCoord.y = 0;
		minCoord.z = 0;

		maxCoord.x = minCoord.x + xDim * xSpace;
		maxCoord.y = minCoord.y + yDim * ySpace;
		maxCoord.z = minCoord.z + zDim * zSpace;

		initDataType();
		initLoader();
	}


	public double getPixelSizeX() {
		return this.pw;
	}

	public void setPixelSizeX(double pixelSizeX) {
		this.pw = pixelSizeX;
	}

	public double getPixelSizeY() {
		return this.ph;
	}

	public void setPixelSizeY(double pixelSizeY) {
		this.ph = pixelSizeY;
	}

	public double getPixelSizeZ() {
		return this.pd;
	}

	public void setPixelSizeZ(double pixelSizeZ) {
		this.pd = pixelSizeZ;
	}

	/**
	 * @author Mengmeng
	 * @param sequence Sequence
	 */
	public Sequence getSequence() {
		return sequence;
	}

	public void clear() {
		sequence = null;
		image = null;
		loader = null;
	}
	
	/**
	 * @author Mengmeng
	 * @param sequence Sequence
	 */
	public void swap(String path) {
		Saver.save(sequence, new File(path + ".tif")) ;
		sequence = null;
		image = null;
		loader = null;
	}

	/**
	 * @author Mengmeng
	 * @param sequence Sequence
	 */
	public void restore(String path) {

		setSequence(Icy.getMainInterface().getSequences(path + ".tif").get(0), channels);
	}

	/**
	 * Checks if the LUTs of all the used color channels and of the
	 * alpha channel have a default LUT.
	 */
	public boolean isDefaultLUT() {
		for(int i = 0; i < 256; i++) {
			if((channels[0] && rLUT[i] != i) ||
				(channels[1] && gLUT[i] != i) ||
				(channels[2] && bLUT[i] != i) ||
				aLUT[i] != i)
				return false;
		}
		return true;
	}

	/**
	 * Get the current set data type. This is one of BYTE_DATA or INT_DATA.
	 * The data type specifies in which format the data is read.
	 */
	public int getDataType() {
		return dataType;
	}

	/**
	 * If true, build an average byte from the specified channels
	 * (for each pixel).
	 * @return true if the value for 'average' has changed.
	 */
	public boolean setAverage(boolean a) {
		if(average != a) {
			this.average = a;
			initDataType();
			initLoader();
			return true;
		}
		return false;
	}

	/**
	 * Returns true if specified channels are being averaged when
	 * reading the image data.
	 * @return
	 */
	public boolean isAverage() {
		return average;
	}

	/**
	 * If true, saturate the channels of RGB images; the RGB values
	 * of each pixels are scaled so that at least one of the values is
	 * 255, the alpha value is the average of the original RGB values.
	 * @return true if the value for 'saturatedVolumeRendering' has changed
	 */
	public boolean setSaturatedVolumeRendering(boolean b) {
		if(this.saturatedVolumeRendering != b) {
			this.saturatedVolumeRendering = b;
			initLoader();
			return true;
		}
		return false;
	}

	/**
	 * Returns whether if saturatedVolumeRendering is set to true.
	 */
	public boolean isSaturatedVolumeRendering() {
		return saturatedVolumeRendering;
	}

	/**
	 * Copies the current color table into the given array.
	 */
	public void getRedLUT(int[] lut) {
		System.arraycopy(rLUT, 0, lut, 0, rLUT.length);
	}

	/**
	 * Copies the current color table into the given array.
	 */
	public void getGreenLUT(int[] lut) {
		System.arraycopy(gLUT, 0, lut, 0, gLUT.length);
	}

	/**
	 * Copies the current color table into the given array.
	 */
	public void getBlueLUT(int[] lut) {
		System.arraycopy(bLUT, 0, lut, 0, bLUT.length);
	}

	/**
	 * Copies the current color table into the given array.
	 */
	public void getAlphaLUT(int[] lut) {
		System.arraycopy(aLUT, 0, lut, 0, aLUT.length);
	}

	/**
	 * Specify the channels which should be read from the image.
	 * This only affects RGB images.
	 * @return true if the channels settings has changed.
	 */
	public boolean setChannels(boolean[] ch) {
		if(ch[0] == channels[0] &&
			ch[1] == channels[1] &&
			ch[2] == channels[2])
			return false;
		channels = ch;
		if(initDataType())
			initLoader();
		return true;
	}

	/**
	 * Set the lookup tables for this volume. Returns
	 * true if the data type of the textures have changed.
	 */
	public boolean setLUTs(int[] r, int[] g, int[] b, int[] a) {
		this.rLUT = r;
		this.gLUT = g;
		this.bLUT = b;
		this.aLUT = a;
		if(initDataType()) {
			initLoader();
			return true;
		}
		return false;
	}

	/**
	 * Set the alpha channel to fully opaque. Returns
	 * true if the data type of the textures have changed.
	 */
	public boolean setAlphaLUTFullyOpaque() {
		for(int i = 0; i < aLUT.length; i++)
			aLUT[i] = 254;
		if(initDataType()) {
			initLoader();
			return true;
		}
		return false;
	}

	/**
	 * Init the loader, based on the currently set data type,
	 * which is either INT_DATA or BYTE_DATA.
	 */
	protected void initLoader() {
		if(image == null)
			throw new RuntimeException("No image. Maybe it is swapped?");

		if(dataType == INT_DATA) {
			loader = saturatedVolumeRendering ? new SaturatedIntLoader(image) : new IntLoader(image);
			return;
		}

		// else: BYTE_DATA
		if (average) {
			loader = new AverageByteLoader(image);
			return;
		}
		int channel = 0;
		if(image instanceof IntImage) {
			for(int i = 0; i < 3; i++)
				if(channels[i])
					channel = i;
		}
		loader = new ByteLoader(image, channel);
	}

	/**
	 * Init the data type.
	 * For 8 bit images, BYTE_DATA is used if isDefaultLUT() returns
	 * true. For RGB images, an additional condition is that only a single
	 * channel is used. For other cases, the data type is INT_DATA.
	 */
	public boolean initDataType() {
		if(image == null)
			throw new RuntimeException("No image. Maybe it is swapped?");
		int noChannels = 0;
		if(image instanceof ByteImage) {
			noChannels = 1;
		} else {
			for(int i = 0; i < 3; i++)
				if(channels[i])
					noChannels++;
		}
		boolean defaultLUT = isDefaultLUT();
		int tmp = dataType;
		if(average || (defaultLUT && noChannels < 2))
			dataType = BYTE_DATA;
		else
			dataType = INT_DATA;

		return tmp != dataType;
	}

	public void setNoCheck(int x, int y, int z, int v) {
		try {
			loader.setNoCheck(x, y, z, v);
		} catch(NullPointerException e) {
			throw new RuntimeException("No image. Maybe it is swapped");
		}
	}

	public void set(int x, int y, int z, int v) {
		try {
			loader.set(x, y, z, v);
		} catch(NullPointerException e) {
			throw new RuntimeException("No image. Maybe it is swapped");
		}
	}

	/**
	 * Load the value at the specified position
	 * @param x
	 * @param y
	 * @param z
	 * @return value. Casted to int if it was a byte value before.
	 */
	public int load(int x, int y, int z) {
		try {
			return loader.load(x, y, z);
		} catch(NullPointerException e) {
			throw new RuntimeException("No image. Maybe it is swapped");
		}
	}

	/**
	 * Load the color at the specified position
	 * @param x
	 * @param y
	 * @param z
	 * @return int-packed color
	 */
	public int loadWithLUT(int x, int y, int z) {
		try {
			return loader.loadWithLUT(x, y, z);
		} catch(NullPointerException e) {
			throw new RuntimeException("No image. Maybe it is swapped");
		}
	}

	/**
	 * Load the average value at the specified position
	 * @param x
	 * @param y
	 * @param z
	 * @return value.
	 */
	public byte getAverage(int x, int y, int z) {
		try {
			return image.getAverage(x, y, z);
		} catch(NullPointerException e) {
			throw new RuntimeException("No image. Maybe it is swapped");
		}
	}

	/**
	 * @author Mengmeng
	 */
	public static int getScale() {
		return scale;
	}

	/**
	 * @author Mengmeng
	 */
	public static void setScale(int scale) {
		Volume.scale = scale;
	}

	/**
	 * Abstract interface for the loader classes.
	 */
	protected interface Loader {
		int load(int x, int y, int z);
		int loadWithLUT(int x, int y, int z);
		void set(int x, int y, int z, int v);
		void setNoCheck(int x, int y, int z, int v);
		/**
		 * @author Mengmeng
		 */
		void setNoCheck(Sequence sequence, int t, int c) ;
//		void setNoCheck(Sequence sequence) ;
	}

	/**
	 * Abstract interface for the input image.
	 */
	protected interface Img {
		public int get(int x, int y, int z);
		public void get(int x, int y, int z, int[] c);
		public byte getAverage(int x, int y, int z);
		public void set(int x, int y, int z, int v);
		/**
		 * @author Mengmeng
		 */
		public void set(Sequence sequence, int t, int c) ;
//		public void set(Sequence sequence) ;
	}

	/**
	 * @author Mengmeng
	 * @param sequence Sequence
	 */
	protected final class ByteImage implements Img {
		protected byte[][] fData;
		private int w;

		protected ByteImage(Sequence sequence) {
			w = sequence.getWidth();
			int d = sequence.getSizeZ();
			fData = new byte[d][];
			for (int z = 0; z < d; z++)
//				fData[z] = sequence.getDataCopyXYAsByte(0, z, 0) ;
				fData[z] = sequence.getDataXYAsByte(0, z, 0) ;
		}

		public byte getAverage(int x, int y, int z) {
			return fData[z][y * w + x];
		}

		public int get(int x, int y, int z) {
			return fData[z][y * w + x] & 0xff;
		}

		public void get(int x, int y, int z, int[] c) {
			int v = get(x, y, z);
			c[0] = c[1] = c[2] = v;
		}

		public void set(int x, int y, int z, int v) {
			fData[z][y * w + x] = (byte)v;
		}
		
		@Override
		/**
		 * @author Mengmeng
		 */
		public void set(Sequence sequence, int t, int c) {
			w = sequence.getWidth();
			int d = sequence.getSizeZ() ;
			fData = new byte[d][];
			for (int z = 0; z < d; z++)
				fData[z] = (byte[])sequence.getDataXYAsByte(t, z, c);
		}

	}


	
	/**
	 * @author Mengmeng
	 * @param sequence Sequence
	 */
	protected final class IntImage implements Img {
		protected int[][] fData;
		private int w;

		protected IntImage(Sequence sequence) {
			//ImageStack stack = imp.getStack();
			w = sequence.getWidth();
			int d = sequence.getSizeZ() ;
			fData = new int[d][];
			for (int z = 0; z < d; z++)
				fData[z] = sequence.getDataXYAsInt(0, z, 0) ;
//				fData[z] = sequence.getDataCopyXYAsInt(0, z, 0) ;
		}

		public byte getAverage(int x, int y, int z) {
			int v = fData[z][y * w + x];
			int r = (v & 0xff0000) >> 16;
			int g = (v & 0xff00) >> 8;
			int b = (v & 0xff);
			return (byte)((r + g + b) / 3);
		}

		public int get(int x, int y, int z) {
			return fData[z][y * w + x];
		}

		public void get(int x, int y, int z, int[] c) {
			int v = get(x, y, z);
			c[0] = (v & 0xff0000) >> 16;
			c[1] = (v & 0xff00) >> 8;
			c[2] = (v & 0xff);
		}

		public void set(int x, int y, int z, int v) {
			fData[z][y * w + x] = v;
		}
		
		@Override
		/**
		 * @author Mengmeng
		 */
		public void set(Sequence sequence, int t, int c) {	
			w = sequence.getWidth();
			int d = sequence.getSizeZ() ;
			fData = new int[d][];
			for (int z = 0; z < d; z++)
				fData[z] = (int[])sequence.getDataXYAsInt(t, z, c);
		}
	}

	protected class IntLoader implements Loader {
		protected Img image;

		protected IntLoader(Img imp) {
			this.image = imp;
		}

		public final int load(int x, int y, int z) {
			return image.get(x, y, z);
		}

		protected int[] color = new int[3];
		public int loadWithLUT(int x, int y, int z) {
			image.get(x, y, z, color);
			int sum = 0, av = 0, v = 0;
			
			if(channels[0]) { int r = rLUT[color[0]]; sum++; av += color[0]; v += (r << 16); }
			if(channels[1]) { int g = gLUT[color[1]]; sum++; av += color[1]; v += (g << 8); }
			if(channels[2]) { int b = bLUT[color[2]]; sum++; av += color[2]; v += b; }
			av /= sum;
			int a = aLUT[av];
			return (a << 24) + v;
		}

		public void setNoCheck(int x, int y, int z, int v) {
			image.set(x, y, z, v);
		}
		
		@Override
		/**
		 * @author Mengmeng
		 */
		public void setNoCheck(Sequence sequence, int t, int c) {
			(image).set(sequence, t, c) ;
		}

		public void set(int x, int y, int z, int v) {
			if(x >= 0 && x < xDim &&
					y >= 0 && y < yDim && z > 0 && z < zDim) {
				this.setNoCheck(x, y, z, v);
			}
		}
	}
	
	protected class SaturatedIntLoader extends IntLoader {
		
		protected SaturatedIntLoader(Img imp) {
			super(imp);
		}
		
		@Override
		public final int loadWithLUT(int x, int y, int z) {
			image.get(x, y, z, color);
			
			int sum = 0, av = 0, r = 0, g = 0, b = 0;
			if(channels[0]) { r = rLUT[color[0]]; sum++; av += color[0]; }
			if(channels[1]) { g = gLUT[color[1]]; sum++; av += color[1]; }
			if(channels[2]) { b = bLUT[color[2]]; sum++; av += color[2]; }

			av /= sum;
			
			final int maxC = Math.max(r, Math.max(g, b));
			final float scale = maxC == 0 ? 0 : 255.0f / maxC;
			
			r = Math.min(255, Math.round(scale * r));
			g = Math.min(255, Math.round(scale * g));
			b = Math.min(255, Math.round(scale * b));
			
			return (aLUT[av] << 24) | (r << 16) | (g << 8) | b;
		}
	}

	protected class ByteLoader implements Loader {
		protected Img image;
		protected int channel;

		protected ByteLoader(Img imp, int channel) {
			this.image = imp;
			this.channel = channel;
		}

		public int load(int x, int y, int z) {
			return image.get(x, y, z);
		}

		private int[] color = new int[3];
		public int loadWithLUT(int x, int y, int z) {
			// ByteLoader only is in use with a default LUT
			image.get(x, y, z, color);
			return color[channel];
		}

		public void setNoCheck(int x, int y, int z, int v) {
			image.set(x, y, z, v);
		}
		
		@Override
		/**
		 * @author Mengmeng
		 */
		public void setNoCheck(Sequence sequence, int t, int c) {
			((ByteImage)image).set(sequence, t, c) ;
		}

		public void set(int x, int y, int z, int v) {
			if(x >= 0 && x < xDim &&
					y >= 0 && y < yDim && z > 0 && z < zDim) {
				this.setNoCheck(x, y, z, v);
			}
		}
	}

	protected class AverageByteLoader extends ByteLoader {

		protected AverageByteLoader(Img imp) {
			super(imp, 0);
		}

		private int[] color = new int[3];
		public final int load(int x, int y, int z) {
			image.get(x, y, z, color);
			return (color[0] + color[1] + color[2]) / 3;
		}

		public final int loadWithLUT(int x, int y, int z) {
			image.get(x, y, z, color);
			int sum = 0, av = 0;
			if(channels[0]) { av += rLUT[color[0]]; sum++; }
			if(channels[1]) { av += gLUT[color[1]]; sum++; }
			if(channels[2]) { av += bLUT[color[2]]; sum++; }
			av /= sum;
			return av;
		}

		public void setNoCheck(int x, int y, int z, int v) {
			image.set(x, y, z, v);
		}

		public void set(int x, int y, int z, int v) {
			if(x >= 0 && x < xDim &&
					y >= 0 && y < yDim && z > 0 && z < zDim) {
				this.setNoCheck(x, y, z, v);
			}
		}
	}
}

