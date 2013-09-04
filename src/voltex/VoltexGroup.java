package voltex;

import icy.sequence.Sequence;
import icy.sequence.SequenceUtil;
import icy.type.collection.array.Array1DUtil;
import ij3d.Content;
import ij3d.ContentInstant;
import ij3d.ContentNode;

import javax.media.j3d.Canvas3D;
import javax.media.j3d.Transform3D;
import javax.media.j3d.View;
import javax.vecmath.Color3f;
import javax.vecmath.Point2d;
import javax.vecmath.Point3d;
import javax.vecmath.Tuple3d;

/**
 * This class extends ContentNode to display a Content as a Volume Rendering.
 * 
 * @author Benjamin Schmid
 */
public class VoltexGroup extends ContentNode {

	/** The VolumeRenderer behind this VoltexGroup */
	protected VolumeRenderer renderer;

	/** Reference to the Content which holds this VoltexGroup */
	protected ContentInstant c;

	/** The volume of this VoltexGroup */
	private float volume;

	/** The minimum coordinate of this VoltexGroup */
	private Point3d min;

	/** The maximum coordinate of this VoltexGroup */
	private Point3d max;

	/** The center point of this VoltexGroup */
	private Point3d center;

	/**
	 * This constructor only exists to allow subclasses to access the super
	 * constructor of BranchGroup.
	 */
	protected VoltexGroup() {
		super();
	}

	/**
	 * Initialize this VoltexGroup with the specified Content.
	 * 
	 * @param c
	 * @throws IllegalArgumentException
	 *             if the specified Content has no image.
	 */
	public VoltexGroup(Content c) {
		this(c.getCurrent());
	}

	/**
	 * @author Mengmeng Initialize this VoltexGroup with the specified
	 *         ContentInstant.
	 * @param c
	 * @throws IllegalArgumentException
	 *             if the specified ContentInstant has no image.
	 */
	public VoltexGroup(ContentInstant c) {
		super();
		if (c.getSequence() == null)
			throw new IllegalArgumentException(
					"VoltexGroup can only"
							+ "be initialized from a ContentInstant that holds an image.");
		this.c = c;
		Sequence sequenceInstant = c.getResamplingFactor() == 1 ? c
				.getSequence() : resample(c.getSequence(),
				c.getResamplingFactor());

		renderer = new VolumeRenderer(sequenceInstant, c.getColor(),
				c.getTransparency(), c.getChannels());


		renderer.volume.setPixelSizeX(sequenceInstant.getPixelSizeX());
		renderer.volume.setPixelSizeY(sequenceInstant.getPixelSizeY());
		renderer.volume.setPixelSizeZ(sequenceInstant.getPixelSizeZ());

		renderer.fullReload();
		calculateMinMaxCenterPoint();
		addChild(renderer.getVolumeNode());
	}
	
	public Sequence resample(Sequence sequence, int factor){
		int factorX = factor ;
		int factorY = factor ;
		return SequenceUtil.scale(sequence, sequence.getWidth() / factorX, sequence.getHeight() / factorY) ;
	}

	/**
	 * Update the volume rendering from the image (only if the resampling factor
	 * is 1.
	 */
	public void update() {
		if (c.getResamplingFactor() != 1)
			return;
		renderer.getVolume().updateData();
	}

	/**
	 * Get a reference VolumeRenderer which is used by this class
	 */
	public VolumeRenderer getRenderer() {
		return renderer;
	}

	/**
	 * @author Mengmeng
	 */
	// public Mask createMask() {
	// return renderer.createMask();
	// }

	/**
	 * @see ContentNode#getMin(Tupe3d) getMin
	 */
	public void getMin(Tuple3d min) {
		min.set(this.min);
	}

	/**
	 * @see ContentNode#getMax (Tupe3d) getMax
	 */
	public void getMax(Tuple3d max) {
		max.set(this.max);
	}
	
	public Point3d getMin() {
		return this.min ;
	}
	
	public Point3d getMax() {
		return this.max ;
	}
	

	/**
	 * @see ContentNode#getCenter(Tupe3d) getCenter
	 */
	public void getCenter(Tuple3d center) {
		center.set(this.center);
	}

	/**
	 * @see ContentNode#thresholdUpdated() thresholdUpdated
	 */
	public void thresholdUpdated(int threshold) {
		renderer.setThreshold(threshold);
	}

	/**
	 * @see ContentNode#getVolume() getVolume
	 */
	public float getVolume() {
		return volume;
	}

	/**
	 * @see ContentNode#eyePtChanged(View view) eyePtChanged
	 */
	public void eyePtChanged(View view) {
		renderer.eyePtChanged(view);
	}

	/**
	 * @see ContentNode#channelsUpdated() channelsUpdated
	 */
	public void channelsUpdated(boolean[] channels) {
		renderer.setChannels(channels);
	}

	/**
	 * @see ContentNode#lutUpdated() lutUpdated
	 */
	public void lutUpdated(int[] r, int[] g, int[] b, int[] a) {
		renderer.setLUTs(r, g, b, a);
	}

	/**
	 * @see ContentNode#shadeUpdated() shadeUpdated
	 */
	public void shadeUpdated(boolean shaded) {
		// do nothing
	}

	/**
	 * @see ContentNode#colorUpdated() colorUpdated
	 */
	public void colorUpdated(Color3f color) {
		renderer.setColor(color);
	}

	/**
	 * @see ContentNode#transparencyUpdated() transparencyUpdated
	 */
	public void transparencyUpdated(float transparency) {
		renderer.setTransparency(transparency);
	}

	/**
	 * Stores the matrix which transforms this VoltexGroup to the image plate in
	 * the specified Transform3D.
	 * 
	 * @param toImagePlate
	 */
	public void volumeToImagePlate(Transform3D toImagePlate) {
		Transform3D toVWorld = new Transform3D();
		renderer.getVolumeNode().getLocalToVworld(toVWorld);
		toImagePlate.mul(toVWorld);
	}

	/**
	 * Returns the 3D coordinates of the given x, y, z position on the 3D
	 * canvas.
	 * 
	 * @param canvas
	 * @param volToIP
	 * @param x
	 * @param y
	 * @param z
	 * @return
	 */
	private void volumePointInCanvas(Canvas3D canvas, Transform3D volToIP,
			int x, int y, int z, Point2d ret) {

		VoltexVolume vol = renderer.volume;
		double px = x * vol.pw;
		double py = y * vol.ph;
		double pz = z * vol.pd;
		Point3d locInImagePlate = new Point3d(px, py, pz);

		volToIP.transform(locInImagePlate);

		canvas.getPixelLocationFromImagePlate(locInImagePlate, ret);
	}

	/**
	 * @author Mengmeng
	 * 
	 *         Calculate the minimum, maximum and center coordinate, together
	 *         with the volume.
	 */
	protected void calculateMinMaxCenterPoint() {
		Sequence sequence = c.getSequence();
		int w = sequence.getWidth(), h = sequence.getHeight();
		int d = sequence.getSizeZ();
		// Calibration cal = imp.getCalibration();
		min = new Point3d();
		max = new Point3d();
		center = new Point3d();
		min.x = w * (float) sequence.getPixelSizeX();
		min.y = h * (float) sequence.getPixelSizeY();
		min.z = d * (float) sequence.getPixelSizeZ();
		max.x = 0;
		max.y = 0;
		max.z = 0;

		float vol = 0;
		for (int zi = 0; zi < d; zi++) {
			float z = zi * (float) sequence.getPixelSizeZ();
			// ImageProcessor ip = imp.getStack().getProcessor(zi+1);

			float[] data = 
//					sequence.getDataXYAsFloat(0, zi, 0) ;
					Array1DUtil.arrayToFloatArray(
					sequence.getDataXY(0, zi, 0), sequence.isSignedDataType());
			int wh = w * h;
			for (int i = 0; i < wh; i++) {
				// float v = ip.getf(i);
				float v = data[i];

				if (v == 0)
					continue;
				vol += v;
				float x = (i % w) * (float) sequence.getPixelSizeX();
				float y = (i / w) * (float) sequence.getPixelSizeY();
				if (x < min.x)
					min.x = x;
				if (y < min.y)
					min.y = y;
				if (z < min.z)
					min.z = z;
				if (x > max.x)
					max.x = x;
				if (y > max.y)
					max.y = y;
				if (z > max.z)
					max.z = z;
				center.x += v * x;
				center.y += v * y;
				center.z += v * z;
			}
		}
		center.x /= vol;
		center.y /= vol;
		center.z /= vol;

		volume = (float) (vol * sequence.getPixelSizeX()
				* sequence.getPixelSizeY() * sequence.getPixelSizeZ());

	}
	
	public void setMinMaxPoint(Point3d min, Point3d max){
		this.min = min ;
		this.max = max ;
	} 

	@Override
	public void swapDisplayedData(String path, String name) {
		renderer.volume.swap(path + ".tif");
		renderer.disableTextures();
	}

	@Override
	public void clearDisplayedData() {
		renderer.volume.clear();
		renderer.disableTextures();
	}

	@Override
	public void restoreDisplayedData(String path, String name) {
		renderer.volume.restore(path + ".tif");
		renderer.enableTextures();
	}

	public void updateDisplayedData(Sequence sequence, int t, int c) {
		VoltexVolume vol = renderer.getVolume();
		vol.updateSequenceNoCheckNoUpdate(sequence, t, c);
		vol.updateData();
	}
}
