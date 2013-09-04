package ij3d;

import icy.gui.dialog.MessageDialog;
import icy.sequence.Sequence;
//import ij.IJ;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.media.j3d.BranchGroup;
import javax.media.j3d.Transform3D;
import javax.vecmath.AxisAngle4d;
import javax.vecmath.Color3f;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;




public class Image3DUniverse extends DefaultAnimatableUniverse {

	public static ArrayList<Image3DUniverse> universes = new ArrayList<Image3DUniverse>();

	private static final UniverseSynchronizer synchronizer = new UniverseSynchronizer();

	/** The current time point */
	private int currentTimepoint = 0;

	/** The global start time */
	private int startTime = 0;

	/** The global end time */
	private int endTime = 0;


	/** The selected Content */
	private Content selected;

	/**
	 * A Hashtable which stores the Contents of this universe. Keys in the table
	 * are the names of the Contents.
	 */
	private Map<String, Content> contents = new HashMap<String, Content>();


	/** A reference to the ImageCanvas3D */
	private ImageCanvas3D canvas;

//	/** A reference to the Executer */
//	private Executer executer;



	/**
	 * A flag indicating whether the view is adjusted each time a Content is
	 * added
	 */
	private boolean autoAdjustView = true;


	/**
	 * An object used for synchronizing. Synchronized methods in a subclass of
	 * SimpleUniverse should be avoided, since Java3D uses it obviously
	 * internally for locking.
	 */
	private final Object lock = new Object();

	static {
		UniverseSettings.load();
	}

	/**
	 * Default constructor. Creates a new universe using the Universe settings -
	 * either default settings or stored settings.
	 */
	public Image3DUniverse() {
		this(UniverseSettings.startupWidth, UniverseSettings.startupHeight);
	}

	/**
	 * Constructs a new universe with the specified width and height.
	 * 
	 * @param width
	 * @param height
	 */
	public Image3DUniverse(int width, int height) {
		super(width, height);
		canvas = (ImageCanvas3D) getCanvas();
//		executer = new Executer(this);

		BranchGroup bg = new BranchGroup();
		scene.addChild(bg);

		resetView();
		universes.add(this);
	}

	/**
	 * Close this universe. Remove all Contents and release all resources.
	 */
	@Override
	public void cleanup() {
		removeAllContents();
		contents.clear();
		universes.remove(this);
		adder.shutdownNow();
//		executer.flush();
		super.cleanup();
	}

	/**
	 * Shows the specified status string at the bottom of the viewer window.
	 * 
	 * @param text
	 */
	public void setStatus(String text) {
	
	}


//	/**
//	 * Returns a reference to the Executer used by this universe.
//	 * 
//	 * @return
//	 */
//	public Executer getExecuter() {
//		return executer;
//	}

	public void showTimepoint(int tp) {
		if (currentTimepoint == tp)
			return;
		this.currentTimepoint = tp;
		for (Content c : contents.values())
			c.showTimepoint(tp, false);
	}

	public int getCurrentTimepoint() {
		return currentTimepoint;
	}

	public int getStartTime() {
		return startTime;
	}

	public int getEndTime() {
		return endTime;
	}

	public void updateStartAndEndTime(int st, int e) {
		this.startTime = st;
		this.endTime = e;
		updateTimelineGUI();
	}

	public void updateTimeline() {
		if (contents.size() == 0)
			startTime = endTime = 0;
		else {
			startTime = Integer.MAX_VALUE;
			endTime = Integer.MIN_VALUE;
			for (Content c : contents.values()) {
				if (c.getStartTime() < startTime)
					startTime = c.getStartTime();
				if (c.getEndTime() > endTime)
					endTime = c.getEndTime();
			}
		}
		if (currentTimepoint > endTime)
			showTimepoint(endTime);
		else if (currentTimepoint < startTime)
			showTimepoint(startTime);
		updateTimelineGUI();
	}

	boolean timelineGUIVisible = false;

	public void updateTimelineGUI() {
	}

//	/* *************************************************************
//	 * Selection methods
//	 * ************************************************************
//	 */
//	/**
//	 * Select the specified Content. If another Content is already selected, it
//	 * will be deselected. fireContentSelected() is thrown.
//	 * 
//	 * @param c
//	 */
//	public void select(Content c) {
//		if (selected != null) {
//			selected.setSelected(false);
//			selected = null;
//		}
//		if (c != null && c.isVisibleAt(currentTimepoint)) {
//			c.setSelected(true);
//			selected = c;
//		}
//		String st = c != null ? c.getName() : "none";
//		IJ.showStatus("selected: " + st);
//
//		fireContentSelected(selected);
//
//		if (c != null && ij.plugin.frame.Recorder.record)
//			Executer.record("select", c.getName());
//	}
//
//	/**
//	 * Returns the selected Content, or null if none is selected.
//	 */
//	@Override
//	public Content getSelected() {
//		return selected;
//	}
//
//	/**
//	 * If any Content is selected, deselects it.
//	 */
//	public void clearSelection() {
//		if (selected != null)
//			selected.setSelected(false);
//		selected = null;
//		fireContentSelected(null);
//	}

	/**
	 * Show/Hide the selection box upon selecting a Content(Instant).
	 */
	public void setShowBoundingBoxUponSelection(boolean b) {
		UniverseSettings.showSelectionBox = b;
		if (selected != null) {
			selected.setSelected(false);
			selected.setSelected(true);
		}
	}

	/**
	 * Returns true if the selection box is shown upon selecting a
	 * Content(Instant).
	 */
	public boolean getShowBoundingBoxUponSelection() {
		return UniverseSettings.showSelectionBox;
	}

	/* *************************************************************
	 * Dimensions ************************************************************
	 */
	/**
	 * autoAdjustView indicates, whether the view is adjusted to fit the whole
	 * universe each time a Content is added.
	 */
	public void setAutoAdjustView(boolean b) {
		autoAdjustView = b;
	}

	/**
	 * autoAdjustView indicates, whether the view is adjusted to fit the whole
	 * universe each time a Content is added.
	 */
	public boolean getAutoAdjustView() {
		return autoAdjustView;
	}

	/**
	 * Calculates the global minimum, maximum and center point depending on all
	 * the available contents.
	 */
	public void recalculateGlobalMinMax() {
		if (contents.isEmpty())
			return;
		Point3d min = new Point3d();
		Point3d max = new Point3d();

		Iterator it = contents();
		Content c = (Content) it.next();
		c.getMin(min);
		c.getMax(max);
		globalMin.set(min);
		globalMax.set(max);
		while (it.hasNext()) {
			c = (Content) it.next();
			c.getMin(min);
			c.getMax(max);
			if (min.x < globalMin.x)
				globalMin.x = min.x;
			if (min.y < globalMin.y)
				globalMin.y = min.y;
			if (min.z < globalMin.z)
				globalMin.z = min.z;
			if (max.x > globalMax.x)
				globalMax.x = max.x;
			if (max.y > globalMax.y)
				globalMax.y = max.y;
			if (max.z > globalMax.z)
				globalMax.z = max.z;
		}
		globalCenter.x = globalMin.x + (globalMax.x - globalMin.x) / 2;
		globalCenter.y = globalMin.y + (globalMax.y - globalMin.y) / 2;
		globalCenter.z = globalMin.z + (globalMax.z - globalMin.z) / 2;
	}

	/**
	 * If the specified Content is the only content in the universe, global
	 * minimum, maximum and center point are set accordingly to this Content. If
	 * not, the extrema of the specified Content are compared with the current
	 * global extrema, and these are set accordingly.
	 * 
	 * @param c
	 */
	public void recalculateGlobalMinMax(Content c) {
		Point3d cmin = new Point3d();
		c.getMin(cmin);
		Point3d cmax = new Point3d();
		c.getMax(cmax);
		if (contents.size() == 1) {
			globalMin.set(cmin);
			globalMax.set(cmax);
		} else {
			if (cmin.x < globalMin.x)
				globalMin.x = cmin.x;
			if (cmin.y < globalMin.y)
				globalMin.y = cmin.y;
			if (cmin.z < globalMin.z)
				globalMin.z = cmin.z;
			if (cmax.x > globalMax.x)
				globalMax.x = cmax.x;
			if (cmax.y > globalMax.y)
				globalMax.y = cmax.y;
			if (cmax.z > globalMax.z)
				globalMax.z = cmax.z;
		}
		globalCenter.x = globalMin.x + (globalMax.x - globalMin.x) / 2;
		globalCenter.y = globalMin.y + (globalMax.y - globalMin.y) / 2;
		globalCenter.z = globalMin.z + (globalMax.z - globalMin.z) / 2;
	}

	/**
	 * Get a reference to the global center point. Attention: Changing the
	 * returned point results in unspecified behavior.
	 */
	public Point3d getGlobalCenterPoint() {
		return globalCenter;
	}

	/**
	 * Copies the global center point into the specified Point3d.
	 * 
	 * @param p
	 */
	public void getGlobalCenterPoint(Point3d p) {
		p.set(globalCenter);
	}

	/**
	 * Copies the global minimum point into the specified Point3d.
	 * 
	 * @param p
	 */
	public void getGlobalMinPoint(Point3d p) {
		p.set(globalMin);
	}

	/**
	 * Copies the global maximum point into the specified Point3d.
	 * 
	 * @param p
	 */
	public void getGlobalMaxPoint(Point3d p) {
		p.set(globalMax);
	}
	
	/**
	 * @author Mengmeng
	 * @param sequence
	 */
	public Content addContent(Sequence sequence, Color3f color, ContentFlag contentFlag,
			int thresh, boolean[] channels, int resf, int type) {
		String name = contentFlag.getContentName() ;
		if (contents.containsKey(name)) {
			MessageDialog.showDialog("Content named '" + name + "' exists already");
			return null;
		}
		Content c = ContentCreator.createContent(contentFlag, sequence, type, resf, 0,
				color, thresh, channels);
		return addContent(c);
	}

	
	/**
	 * @author Mengmeng
	 * @param sequence
	 */
	public Content addContent(Sequence sequence, int posT, int posC, int type) {
		int res = 1 ;
//		int res = Content.getDefaultResamplingFactor(sequence, type);
		int thr = 0 ;
		ContentFlag contentFlag = new ContentFlag(posT, posC) ;
		contentFlag.setSizeC(sequence.getSizeC()) ;
		return addContent(sequence, null, contentFlag, thr, new boolean[] {
				true, true, true }, res, type);
	}

	
	/**
	 * @author Mengmeng
	 * @param Sequence sequence
	 */
	public Content addVoltex(Sequence sequence, int posT, int posC) {
		return addContent(sequence, posT, posC, Content.VOLUME);
	}


	/**
	 * Remove all the contents of this universe.
	 */
	public void removeAllContents() {
		String[] names = new String[contents.size()];
		contents.keySet().toArray(names);
		for (int i = 0; i < names.length; i++)
			removeContent(names[i]);
	}

	/**
	 * Remove the Content with the specified name from the universe.
	 * 
	 * @param name
	 */
	public void removeContent(String name) {
		synchronized (lock) {
			Content content = contents.get(name);
			if (content == null)
				return;
			scene.removeChild(content);
			contents.remove(name);
//			if (selected == content)
//				clearSelection();
			fireContentRemoved(content);
			this.removeUniverseListener(content);
			updateTimeline();
		}
	}

	/* *************************************************************
	 * Content retrieval
	 * ************************************************************
	 */
	/**
	 * Return an Iterator which iterates through all the contents of this
	 * universe.
	 */
	@Override
	public Iterator contents() {
		return contents.values().iterator();
	}

	/**
	 * Returns a Collection containing the references to all the contents of
	 * this universe.
	 * 
	 * @return
	 */
	public Collection getContents() {
		if (contents == null)
			return null;
		return contents.values();
	}

	/**
	 * Returns true if a Content with the specified name is present in this
	 * universe.
	 * 
	 * @param name
	 * @return
	 */
	public boolean contains(String name) {
		return contents.containsKey(name);
	}

	/**
	 * Returns the Content with the specified name. Null if no Content with the
	 * specified name is present.
	 * 
	 * @param name
	 * @return
	 */
	public Content getContent(String name) {
		if (null == name)
			return null;
		return contents.get(name);
	}
	
	public Content getContent(int posT, int posC){
		String contentName = "t" + posT + "c" + posC;
		return getContent(contentName);
	}

	/* *************************************************************
	 * Methods changing the view of this universe
	 * ************************************************************
	 */

	/**
	 * Syncs this window.
	 */
	public void sync(boolean b) {
		if (b)
			synchronizer.addUniverse(this);
		else
			synchronizer.removeUniverse(this);
	}



	/**
	 * Reset the transformations of the view side of the scene graph as if the
	 * Contents of this universe were just displayed.
	 */
	public void resetView() {
		fireTransformationStarted();

		// rotate so that y shows downwards
		Transform3D t = new Transform3D();
		AxisAngle4d aa = new AxisAngle4d(1, 0, 0, Math.PI);
		t.set(aa);
		getRotationTG().setTransform(t);

		t.setIdentity();
		getTranslateTG().setTransform(t);
		getZoomTG().setTransform(t);
		recalculateGlobalMinMax();
		getViewPlatformTransformer().centerAt(globalCenter);
		// reset zoom
		double d = oldRange / Math.tan(Math.PI / 8);
		getViewPlatformTransformer().zoomTo(d);
		fireTransformationUpdated();
		fireTransformationFinished();
	}

	/**
	 * Rotate the universe, using the given axis of rotation and angle; The
	 * center of rotation is the global center.
	 * 
	 * @param axis
	 *            The axis of rotation (in the image plate coordinate system)
	 * @param angle
	 *            The angle in radians.
	 */
	public void rotateUniverse(Vector3d axis, double angle) {
		viewTransformer.rotate(axis, angle);
	}

	/**
	 * Rotate the univere so that the user looks in the negative direction of
	 * the z-axis.
	 */
	public void rotateToNegativeXY() {
		fireTransformationStarted();
		getRotationTG().setTransform(new Transform3D());
		fireTransformationFinished();
	}

	/**
	 * Rotate the univere so that the user looks in the positive direction of
	 * the z-axis.
	 */
	public void rotateToPositiveXY() {
		fireTransformationStarted();
		getRotationTG().setTransform(new Transform3D());
		waitForNextFrame();
		rotateUniverse(new Vector3d(0, 1, 0), Math.PI);
	}

	/**
	 * Rotate the univere so that the user looks in the negative direction of
	 * the y-axis.
	 */
	public void rotateToNegativeXZ() {
		fireTransformationStarted();
		getRotationTG().setTransform(new Transform3D());
		waitForNextFrame();
		rotateUniverse(new Vector3d(1, 0, 0), Math.PI / 2);
	}

	/**
	 * Rotate the univere so that the user looks in the positive direction of
	 * the y-axis.
	 */
	public void rotateToPositiveXZ() {
		fireTransformationStarted();
		getRotationTG().setTransform(new Transform3D());
		waitForNextFrame();
		rotateUniverse(new Vector3d(0, 1, 0), -Math.PI / 2);
	}

	/**
	 * Rotate the univere so that the user looks in the negative direction of
	 * the x-axis.
	 */
	public void rotateToNegativeYZ() {
		fireTransformationStarted();
		getRotationTG().setTransform(new Transform3D());
		waitForNextFrame();
		rotateUniverse(new Vector3d(0, 1, 0), Math.PI / 2);
	}

	/**
	 * Rotate the univere so that the user looks in the positive direction of
	 * the x-axis.
	 */
	public void rotateToPositiveYZ() {
		fireTransformationStarted();
		getRotationTG().setTransform(new Transform3D());
		waitForNextFrame();
		rotateUniverse(new Vector3d(1, 0, 0), -Math.PI / 2);
	}

	/**
	 * Select the view at the selected Content.
	 */
	public void centerSelected(Content c) {
		Point3d center = new Point3d();
		c.getContent().getCenter(center);

		Transform3D localToVWorld = new Transform3D();
		c.getContent().getLocalToVworld(localToVWorld);
		localToVWorld.transform(center);

		getViewPlatformTransformer().centerAt(center);
	}

	/**
	 * Center the universe at the given point.
	 */
	public void centerAt(Point3d p) {
		getViewPlatformTransformer().centerAt(p);
	}

	/**
	 * Fit all contents optimally into the canvas.
	 */
	public void adjustView() {
		adjustView(ViewAdjuster.ADJUST_BOTH);
	}

	/**
	 * Fit all contents optimally into the canvas.
	 * 
	 * @param dir
	 *            One of ViewAdjuster.ADJUST_HORIZONTAL,
	 *            ViewAdjuster.ADJUST_VERTICAL or ViewAdjuster.ADJUST_BOTH
	 */
	public void adjustView(int dir) {
		ViewAdjuster adj = new ViewAdjuster(this, dir);
		adj.addCenterOf(contents.values());
		for (Content c : contents.values())
			adj.add(c);
		adj.apply();
	}

	/**
	 * Fit the specified contents optimally into the canvas.
	 */
	public void adjustView(Iterable<Content> contents) {
		adjustView(contents, ViewAdjuster.ADJUST_BOTH);
	}

	/**
	 * Fit the specified contents optimally into the canvas.
	 * 
	 * @param dir
	 *            One of ViewAdjuster.ADJUST_HORIZONTAL,
	 *            ViewAdjuster.ADJUST_VERTICAL or ViewAdjuster.ADJUST_BOTH
	 */
	public void adjustView(Iterable<Content> contents, int dir) {
		ViewAdjuster adj = new ViewAdjuster(this, dir);
		adj.addCenterOf(contents);
		for (Content c : contents)
			adj.add(c);
		adj.apply();
	}

	/**
	 * Fit the specified content optimally into the canvas.
	 * 
	 * @param dir
	 *            One of ViewAdjuster.ADJUST_HORIZONTAL,
	 *            ViewAdjuster.ADJUST_VERTICAL or ViewAdjuster.ADJUST_BOTH
	 */
	public void adjustView(Content c, int dir) {
		ViewAdjuster adj = new ViewAdjuster(this, dir);
		adj.add(c);
		adj.apply();
	}

	/**
	 * Fit the specified content optimally into the canvas.
	 */
	public void adjustView(Content c) {
		adjustView(c, ViewAdjuster.ADJUST_BOTH);
	}

	/* *************************************************************
	 * Private methods
	 * ************************************************************
	 */
	private float oldRange = 2f;

	private void ensureScale(float range) {
		oldRange = range;
		double d = (range) / Math.tan(Math.PI / 8);
		getViewPlatformTransformer().zoomTo(d);
	}

	public String allContentsString() {
		StringBuffer sb = new StringBuffer();
		boolean first = true;
		for (String s : contents.keySet()) {
			if (first)
				first = false;
			else
				sb.append(", ");
			sb.append("\"");
			sb.append(s);
			sb.append("\"");
		}
		return sb.toString();
	}

	public String getSafeContentName(String suggested) {
		String originalName = suggested;
		String attempt = suggested;
		int tryNumber = 2;
		while (contains(attempt)) {
			attempt = originalName + " (" + tryNumber + ")";
			++tryNumber;
		}
		return attempt;
	}

	/** Returns true on success. */
	private boolean addContentToScene(Content c) {
		synchronized (lock) {
			String name = c.getName();
			if (contents.containsKey(name)) {
//				IJ.log("Mesh named '" + name + "' exists already");
				return false;
			}
			// update start and end time
			int st = startTime;
			int e = endTime;
			if (c.getStartTime() < startTime)
				st = c.getStartTime();
			if (c.getEndTime() > endTime)
				e = c.getEndTime();
			updateStartAndEndTime(st, e);

			this.scene.addChild(c);
			this.contents.put(name, c);
			this.recalculateGlobalMinMax(c);

//			c.setPointListDialog(plDialog);

			c.showTimepoint(currentTimepoint, true);
		}
		return true;
	}

	/**
	 * Add the specified Content to the universe. It is assumed that the
	 * specified Content is constructed correctly. Will wait until the Content
	 * is fully added; for asynchronous additions of Content, use the @addContentLater
	 * method.
	 * 
	 * @param c
	 * @return the added Content, or null if an error occurred.
	 */
	public Content addContent(Content c) {
		try {
			return addContentLater(c).get();
		} catch (InterruptedException ie) {
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	private ExecutorService adder = Executors.newFixedThreadPool(1);

	/**
	 * Add the specified Content to the universe. It is assumed that the
	 * specified Content is constructed correctly. The Content is added
	 * asynchronously, and this method returns immediately.
	 * 
	 * @param c
	 *            The Content to add
	 * @return a Future holding the added Content, or null if an error occurred.
	 */
	public Future<Content> addContentLater(final Content c) {
		final Image3DUniverse univ = this;
		return adder.submit(new Callable<Content>() {

			@Override
			public Content call() {
				synchronized (lock) {
					if (addContentToScene(c)) {
						if (univ.autoAdjustView) {
							univ.getViewPlatformTransformer().centerAt(
									univ.globalCenter);
							float range = (float) (univ.globalMax.x - univ.globalMin.x);
							univ.ensureScale(range);
						}
					}
				}
				univ.fireContentAdded(c);
				univ.addUniverseListener(c);
				univ.waitForNextFrame();
				univ.fireTransformationUpdated();
				return c;
			}
		});
	}

//	public Collection<Future<Content>> addContentLater(String file) {
//		Map<String, CustomMesh> meshes = MeshLoader.load(file);
//		if (meshes == null)
//			return null;
//
//		List<Content> contents = new ArrayList<Content>();
//		for (Map.Entry<String, CustomMesh> entry : meshes.entrySet()) {
//			String name = entry.getKey();
//			name = getSafeContentName(name);
//			CustomMesh mesh = entry.getValue();
//
//			Content content = createContent(mesh, name);
//			contents.add(content);
//		}
//		return addContentLater(contents);
//	}

	/**
	 * Add the specified collection of Content to the universe. It is assumed
	 * that the specified Content is constructed correctly. The Content is added
	 * asynchronously, and this method returns immediately.
	 * 
	 * @param c
	 *            The Collection of Content to add
	 * @return a Collection of Future objects, each holding an added Content.
	 *         The returned Collection is never null, but its Future objects may
	 *         return null on calling get() on them if an error ocurred when
	 *         adding a specific Content object.
	 */
	public Collection<Future<Content>> addContentLater(Collection<Content> cc) {
		final Image3DUniverse univ = this;
		final ArrayList<Future<Content>> all = new ArrayList<Future<Content>>();
		for (final Content c : cc) {
			all.add(adder.submit(new Callable<Content>() {
				@Override
				public Content call() {
					if (!addContentToScene(c))
						return null;
					univ.fireContentAdded(c);
					univ.addUniverseListener(c);
					return c;
				}
			}));
		}
		// Post actions: submit a new task to the single-threaded
		// executor service, so it will be executed after all other
		// tasks.
		adder.submit(new Callable<Boolean>() {
			@Override
			public Boolean call() {
				// Now adjust universe view
				if (univ.autoAdjustView) {
					univ.getViewPlatformTransformer().centerAt(
							univ.globalCenter);
					float range = (float) (univ.globalMax.x - univ.globalMin.x);
					univ.ensureScale(range);
				}
				// Notify listeners
				univ.waitForNextFrame();
				univ.fireTransformationUpdated();
				return true;
			}
		});
		return all;
	}

}
