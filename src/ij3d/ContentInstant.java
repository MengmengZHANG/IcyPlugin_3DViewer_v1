package ij3d;

import icy.sequence.Sequence;
import ij3d.shapes.BoundingBox;
import ij3d.shapes.CoordinateSystem;

import java.awt.Color;
import java.io.File;
import java.util.Enumeration;

import javax.media.j3d.BranchGroup;
import javax.media.j3d.OrderedGroup;
import javax.media.j3d.Switch;
import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.media.j3d.View;
import javax.vecmath.Color3f;
import javax.vecmath.Matrix3f;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

import voltex.VoltexGroup;

public class ContentInstant extends BranchGroup implements UniverseListener, ContentConstants {

	// time point for this ContentInstant
	private int timepoint = 0; //old 

	private int posT = 0;
	private int posC = 0;
	private ContentFlag contentFlag ;

	public ContentFlag getContentFlag() {
		return contentFlag;
	}

	public void setContentFlag(ContentFlag contentFlag) {
		this.contentFlag = contentFlag;
		this.posC = contentFlag.getPosC() ;
		this.posT = contentFlag.getPosT() ;
	}

	// attributes
	private final String name;
	protected Color3f color = null;
	
	
	/**
	 * @author Mengmeng
	 * @param  sequence Sequence
	 */
	private Sequence sequence;


	protected boolean[] channels = new boolean[] {true, true, true};
	protected int[] rLUT = createDefaultLUT();
	protected int[] gLUT = createDefaultLUT();
	protected int[] bLUT = createDefaultLUT();
	protected int[] aLUT = createDefaultLUT();
	protected float transparency = 0f;
	private int resamplingF = 1;
	protected int threshold = 0;
	protected boolean shaded = true;
	protected int type = VOLUME;

	// visibility flags
	private boolean locked = false;
	private boolean visible = true;
	private boolean bbVisible = false;
	private boolean coordVisible = false;
	private boolean showPL = false;
	protected boolean selected = false;

	// entries
	private ContentNode contentNode = null;



	// scene graph entries
	private OrderedGroup ordered;

	protected TransformGroup localRotate;
	protected TransformGroup localTranslate;

	private boolean available = true;

	public ContentInstant(String name) {
		// create BranchGroup for this image
		this.name = name;
		setCapability(BranchGroup.ALLOW_DETACH);
		setCapability(BranchGroup.ENABLE_PICK_REPORTING);

		// create transformation for pickeing
		localTranslate = new TransformGroup();
		localTranslate.setCapability(TransformGroup.ALLOW_TRANSFORM_READ);
		localTranslate.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
		addChild(localTranslate);
		localRotate = new TransformGroup();
		localRotate.setCapability(TransformGroup.ALLOW_TRANSFORM_READ);
		localRotate.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
		localTranslate.addChild(localRotate);

		ordered = new OrderedGroup();
		for(int i = 0; i < 5; i++) {
			Switch s = new Switch();
			s.setCapability(Switch.ALLOW_SWITCH_WRITE);
			s.setCapability(Switch.ALLOW_SWITCH_READ);
			s.setCapability(Switch.ALLOW_CHILDREN_WRITE);
			s.setCapability(Switch.ALLOW_CHILDREN_EXTEND);
			ordered.addChild(s);
		}

		localRotate.addChild(ordered);
	}

	public void displayAs(int type) {
		if(getSequence() == null)
			return;
		// create content node and add it to the switch
		switch(type) {
			case VOLUME: contentNode = new VoltexGroup(this); break;
			default: throw new IllegalArgumentException(
					"Specified type is neither VOLUME, ORTHO," +
					"SURFACE or SURFACEPLOT2D");
		}
		display(contentNode);
		// update type
		this.type = type;
	}

	public static int[] createDefaultLUT() {
		int[] lut = new int[256];
		for(int i = 0; i < lut.length; i++)
			lut[i] = i;
		return lut;
	}

	
	/**
	 * @author Mengmeng
	 * @param sequence
	 */
	public static int getDefaultResamplingFactor(Sequence sequence, int type) {
		int w = sequence.getWidth(), h = sequence.getHeight();
		int d = sequence.getSizeZ() ;
				
		int max = Math.max(w, Math.max(h, d));
		switch(type) {
			case SURFACE: return (int)Math.ceil(max / 128f);
			case VOLUME:  return (int)Math.ceil(max / 256f);
			case ORTHO:   return (int)Math.ceil(max / 256f);
			case SURFACE_PLOT2D: return (int)Math.ceil(max / 128f);
		}
		return 1;
	}
	

	public void display(ContentNode node) {
		// remove everything if possible
		for(@SuppressWarnings("rawtypes")
		Enumeration e = ordered.getAllChildren(); e.hasMoreElements(); ) {
			Switch s = (Switch)e.nextElement();
			s.removeAllChildren();
		}

		// create content node and add it to the switch
		contentNode = node;
		((Switch)ordered.getChild(CO)).addChild(contentNode);
		// create the bounding box and add it to the switch
		Point3d min = new Point3d(); contentNode.getMin(min);
		Point3d max = new Point3d(); contentNode.getMax(max);
		BoundingBox bb = new BoundingBox(min, max);
		bb.setPickable(false);
		((Switch)ordered.getChild(BS)).addChild(bb);
//		bb = new BoundingBox(min, max, new Color3f(Color.RED));
//		bb.setPickable(false);
//		((Switch)ordered.getChild(BB)).addChild(bb);
		

		// create local coordinate system and add it to the switch
		float cl = (float)Math.abs(max.x - min.x) / 5f;
		CoordinateSystem cs = new CoordinateSystem(
						cl, new Color3f(Color.BLACK));
		cs.setPickable(false);
		((Switch)ordered.getChild(CS)).addChild(cs);


		// initialize child mask of the switch
		setSwitch(BS, false); //bounding box
		setSwitch(CS, false); // coordinate system
		setSwitch(CO, true); //Content node
		setSwitch(PL, false); //PointList

		// update type
		this.type = CUSTOM;
	}

	private void setSwitch(int which, boolean on) {
		((Switch)ordered.getChild(which)).setWhichChild(on ? Switch.CHILD_ALL : Switch.CHILD_NONE);
	}

	public void clearOriginalData() {
		if (getSequence() != null)
			getSequence().close();
		setSequence(null);
	}

	public void swapDisplayedData() {
		if(!available)
			return;
		contentNode.swapDisplayedData(getDisplayedDataSwapfile(), getName());
		available = false;
	}

	public void restoreDisplayedData() {
		System.out.println("restoreDisplayedData " + getName());
		if(available) {
			System.out.println("not restoring because it is not swapped");
			return;
		}
		contentNode.restoreDisplayedData(getDisplayedDataSwapfile(), getName());
		available = true;
	}

	public void clearDisplayedData() {
		if(!available) return;
		contentNode.clearDisplayedData();
		available = false;
	}
	
	public void updateDisplayData(int t, int c){
		contentNode.updateDisplayedData(sequence, t, c);
		available = true;
	}

	public boolean isAvailable() {
		return available;
	}

	private String displayedDataSwapfile = null;
	private String originalDataSwapfile = null;

	@SuppressWarnings("unused")
	private String getOriginalDataSwapfile() {
		if(originalDataSwapfile != null)
			return originalDataSwapfile;
		File tmp = new File(System.getProperty("java.io.tmpdir"), "3D_Viewer");
		if(!tmp.exists())
			tmp.mkdirs();
		tmp = new File(tmp, "original");
		if(!tmp.exists())
			tmp.mkdirs();
		originalDataSwapfile = new File(tmp, getName()).
			getAbsolutePath();
		return originalDataSwapfile;
	}

	private String getDisplayedDataSwapfile() {
		if(displayedDataSwapfile != null)
			return displayedDataSwapfile;
		File tmp = new File(System.getProperty("java.io.tmpdir"), "3D_Viewer");
		if(!tmp.exists())
			tmp.mkdirs();
		tmp = new File(tmp, "displayed");
		if(!tmp.exists())
			tmp.mkdirs();
		displayedDataSwapfile = new File(tmp, getName()).
			getAbsolutePath();
		return displayedDataSwapfile;
	}


	/* ************************************************************
	 * setters - visibility flags
	 *
	 * ***********************************************************/

	public void setVisible(boolean b) {
		visible = b;
		setSwitch(CO, b);
		setSwitch(CS, b & coordVisible);

	}

	public void showBoundingBox(boolean b) {
		bbVisible = b;
		setSwitch(BS, b);
	}


	public void showCoordinateSystem(boolean b) {
		coordVisible = b;
		setSwitch(CS, b);
	}

	public void setSelected(boolean selected) {
		this.selected = selected;
		boolean sb = selected && UniverseSettings.showSelectionBox;
		setSwitch(BS, sb);
	}


	/* ************************************************************
	 * setters - transform
	 *
	 **************************************************************/
	public void toggleLock() {
		locked = !locked;
	}

	public void setLocked(boolean b) {
		locked = b;
	}

	public void applyTransform(double[] matrix) {
		applyTransform(new Transform3D(matrix));
	}

	public void applyTransform(Transform3D transform) {
		Transform3D t1 = new Transform3D();
		localTranslate.getTransform(t1);
		Transform3D t2 = new Transform3D();
		localRotate.getTransform(t2);
		t1.mul(t2);

		t1.mul(transform, t1);
		setTransform(t1);
	}

	public void setTransform(double[] matrix) {
		if(contentNode == null)
			return;
		setTransform(new Transform3D(matrix));
	}

	public void setTransform(Transform3D transform) {
		if(contentNode == null)
			return;
		Transform3D t = new Transform3D();
		Point3d c = new Point3d(); contentNode.getCenter(c);

		Matrix3f m = new Matrix3f();
		transform.getRotationScale(m);
		t.setRotationScale(m);
		// One might thing a rotation matrix has no translational
		// component, however, if the rotation is composed of
		// translation - rotation - backtranslation, it has indeed.
		Vector3d v = new Vector3d();
		v.x = -m.m00*c.x - m.m01*c.y - m.m02*c.z + c.x;
		v.y = -m.m10*c.x - m.m11*c.y - m.m12*c.z + c.y;
		v.z = -m.m20*c.x - m.m21*c.y - m.m22*c.z + c.z;
		t.setTranslation(v);
		localRotate.setTransform(t);

		Vector3d v2 = new Vector3d();
		transform.get(v2);
		v2.sub(v);
		t.set(v2);
		localTranslate.setTransform(t);
	}

	/* ************************************************************
	 * setters - attributes
	 *
	 * ***********************************************************/

	public void setLUT(int[] rLUT, int[] gLUT, int[] bLUT, int[] aLUT) {
		this.rLUT = rLUT;
		this.gLUT = gLUT;
		this.bLUT = bLUT;
		this.aLUT = aLUT;
		if(contentNode != null)
			contentNode.lutUpdated(rLUT, gLUT, bLUT, aLUT);
	}

	public void setChannels(boolean[] channels) {
		boolean channelsChanged = channels[0] != this.channels[0] ||
				channels[1] != this.channels[1] ||
				channels[2] != this.channels[2];
		if(!channelsChanged)
			return;
		this.channels = channels;
		if(contentNode != null)
			contentNode.channelsUpdated(channels);
	}

	public void setThreshold(int th) {
		if(th != threshold) {
			this.threshold = th;
			if(contentNode != null)
				contentNode.thresholdUpdated(threshold);
		}
	}

	public void setShaded(boolean b) {
		if(b != shaded) {
			this.shaded = b;
			if(contentNode != null)
				contentNode.shadeUpdated(shaded);
		}
	}

	public boolean isShaded() {
		return shaded;
	}

	public void setSaturatedVolumeRendering(boolean b) {
		if(contentNode != null && type == VOLUME) {
			((VoltexGroup)contentNode)
				.getRenderer()
				.getVolume()
				.setSaturatedVolumeRendering(b);
		}
	}

	public boolean isSaturatedVolumeRendering() {
		return contentNode != null &&
			type == VOLUME &&
			((VoltexGroup)contentNode)
				.getRenderer()
				.getVolume()
				.isSaturatedVolumeRendering();
	}

	public void setColor(Color3f color) {
		if ((this.color == null && color == null) ||
				(this.color != null && color != null &&
				 this.color.equals(color)))
			return;
		this.color = color;
		//Mengmeng
// 		plShape.setColor(color);
		if(contentNode != null)
			contentNode.colorUpdated(this.color);
	}

	public synchronized void setTransparency(float transparency) {
		transparency = transparency < 0 ? 0 : transparency;
		transparency = transparency > 1 ? 1 : transparency;
		if(Math.abs(transparency - this.transparency) < 0.01)
			return;
		this.transparency = transparency;
		if(contentNode != null)
			contentNode.transparencyUpdated(this.transparency);
	}

	/* ************************************************************
	 * UniverseListener interface
	 *
	 *************************************************************/
	@Override
	public void transformationStarted(View view) {}
	@Override
	public void contentAdded(Content c) {}
	@Override
	public void contentRemoved(Content c) {

	}
	@Override
	public void canvasResized() {}
	@Override
	public void contentSelected(Content c) {}
	@Override
	public void contentChanged(Content c) {}

	@Override
	public void universeClosed() {
	
	}

	@Override
	public void transformationUpdated(View view) {
		eyePtChanged(view);
	}

	@Override
	public void transformationFinished(View view) {
		eyePtChanged(view);
	}

	public void eyePtChanged(View view) {
		if(contentNode != null)
			contentNode.eyePtChanged(view);
	}

	/* *************************************************************
	 * getters
	 *
	 **************************************************************/
	@Override
	public String getName() {
		return name + "_#" + getTimepoint();
	}

	public int getTimepoint() {
		return timepoint;
	}

	public int getType() {
		return type;
	}

	public ContentNode getContent() {
		return contentNode;
	}

	
	/**
	 * @author Mengmeng
	 * @param  sequence<Sequence>
	 */
	public Sequence getSequence() {
		return sequence;
	}

	public boolean[] getChannels() {
		return channels;
	}

	public void getRedLUT(int[] l) {
		System.arraycopy(rLUT, 0, l, 0, rLUT.length);
	}

	public void getGreenLUT(int[] l) {
		System.arraycopy(gLUT, 0, l, 0, gLUT.length);
	}

	public void getBlueLUT(int[] l) {
		System.arraycopy(bLUT, 0, l, 0, bLUT.length);
	}

	public int getPosT() {
		return posT;
	}

	public void setPosT(int posT) {
		this.posT = posT;
	}

	public int getPosC() {
		return posC;
	}

	public void setPosC(int posC) {
		this.posC = posC;
	}
	
	public void getAlphaLUT(int[] l) {
		System.arraycopy(aLUT, 0, l, 0, aLUT.length);
	}

	public Color3f getColor() {
		return color;
	}

	public int getThreshold() {
		return threshold;
	}

	public float getTransparency() {
		return transparency;
	}

	public int getResamplingFactor() {
		return getResamplingF();
	}

	public TransformGroup getLocalRotate() {
		return localRotate;
	}

	public TransformGroup getLocalTranslate() {
		return localTranslate;
	}

	public void getLocalRotate(Transform3D t) {
		localRotate.getTransform(t);
	}

	public void getLocalTranslate(Transform3D t) {
		localTranslate.getTransform(t);
	}

	public boolean isLocked() {
		return locked;
	}

	public boolean isVisible() {
		return visible;
	}

	public boolean hasCoord() {
		return coordVisible;
	}

	public boolean hasBoundingBox() {
		return bbVisible;
	}

	public boolean isPLVisible() {
		return showPL;
	}
	
	/**
	 * @author Mengmeng
	 * @param  sequence Sequence
	 */
	public void setSequence(Sequence sequence) {
		this.sequence = sequence;
	}
	
	public int getResamplingF() {
		return resamplingF;
	}

	public void setResamplingF(int resamplingF) {
		this.resamplingF = resamplingF;
	}

	public void setTimepoint(int timepoint) {
		this.timepoint = timepoint;
	}
}

