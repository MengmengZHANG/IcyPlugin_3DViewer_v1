package icy.canvas;

import icy.canvas.IcyCanvasEvent.IcyCanvasEventType;
import icy.gui.component.button.ColorChooserButton;
import icy.gui.component.button.ColorChooserButton.ColorChangeListener;
import icy.gui.util.ComponentUtil;
import icy.gui.util.GuiUtil;
import icy.gui.viewer.Viewer;
import icy.image.IcyBufferedImage;
import icy.image.colormap.IcyColorMap;
import icy.image.colormap.IcyColorMapComponent;
import icy.image.lut.LUT;
import icy.image.lut.LUT.LUTChannel;
import icy.math.Scaler;
import icy.painter.Painter;
import icy.sequence.Sequence;
import icy.sequence.SequenceEvent.SequenceEventType;
import icy.sequence.SequenceUtil;
import icy.system.SystemUtil;
import icy.system.thread.ThreadUtil;
import icy.type.collection.array.Array2DUtil;
import ij3d.Content;
//import ij3d.Executer;
import ij3d.Image3DUniverse;
import ij3d.ImageCanvas3D;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.media.j3d.Background;
import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.vecmath.Color3f;

import voltex.VoltexGroup;

public class Canvas3DViewer extends IcyCanvas3D implements ColorChangeListener,
		ActionListener {

	private class ColormapUpdater implements Runnable {
		private int posC;
		private int[][] LUTmaps;

		public ColormapUpdater() {
			super();
		}

		@Override
		public void run() {
			Content contentInstant = universe.getContent(getPositionT(), posC);
			contentInstant.setLUT(LUTmaps[0], LUTmaps[1], LUTmaps[2],
					LUTmaps[3]);
		}

		public void initialize(int posC, int[][] newLUTmaps) {
			this.posC = posC;
			this.LUTmaps = newLUTmaps;
		}

	}

	private class TimeUpdater implements Runnable {
		int posT;

		public TimeUpdater() {
			super();
		}

		public void initialize(int newTimePoint) {
			this.posT = newTimePoint;
		}

		@Override
		public void run() {
			updateSequence(posT);
		}

	}

	private static final long serialVersionUID = 5818984760887292841L;
	private boolean initialized;
	private Image3DUniverse universe;
	// private Executer executer;
	private ColormapUpdater colormapUpdater;
	private TimeUpdater timeUpdater;
	private LUT lutSave;
	private Sequence sequence;
	private Sequence sequenceScale;
	private List<Content> contents = new ArrayList<Content>();
	private int resampling;
	private ColorChooserButton backGroundColorBtn = new ColorChooserButton();
	// volume resolution
	private String[] volumeImageSampleString = { "1 (slow)", "2", "3", "4",
			"5", "6", "7", "8", "9", "10 (fast)" };
	private JComboBox volumeImageSampleCombo = new JComboBox(
			volumeImageSampleString);
	final private JCheckBox localCoordinateCheckBox = new JCheckBox(
			"Show local coor. sys", false);
	final private JCheckBox boundingBoxCheckBox = new JCheckBox(
			"Show bounding box", false);

	private ImageCanvas3D canvas;

	public Canvas3DViewer(Viewer viewer) {
		super(viewer);
		initialized = false;
		ThreadUtil.invokeNow(new Runnable(){
			@Override
			public void run() {
				SystemUtil
						.addToJavaLibraryPath(new String[] { "C:/Users/Administrator/Documents/eclipse_workspace/3DViewer_Java3D_v10/jar/java3d-1_5_1-windows-i586/bin" });
				universe = new Image3DUniverse();
				canvas = (ImageCanvas3D) universe.getCanvas();

				canvas.render();
				sequence = getSequence();
				resampling = Content.getDefaultResamplingFactor(sequence,
						Content.VOLUME);

				System.out.println("\r\nResampling started: " + resampling);
				sequenceScale = SequenceUtil.scale(sequence, sequence.getWidth()
						/ resampling, sequence.getHeight() / resampling);
				System.out.println("Resampling finieshed\r\n");

				sequenceScale.setPixelSizeX(sequence.getPixelSizeX() * resampling);
				sequenceScale.setPixelSizeY(sequence.getPixelSizeY() * resampling);

				initializeViewer();
				for (int i = 0; i < sequenceScale.getSizeC(); i++) {
					Content contentInstant = universe.addVoltex(sequenceScale, posT, i);
					contents.add(contentInstant);
					System.out.println("Content added: "
							+ contentInstant.getCurrent().getContentFlag()
									.getContentName());
				}

				// executer = universe.getExecuter();
				add(canvas);
				initialzeLUT();
				setVisible(true);

				// info panel
				panel = GuiUtil.generatePanelWithoutBorder();

				// general Settings
				final JPanel generalSettingsPanel = GuiUtil
						.generatePanel("General Settings");

				// background color
				// default is white
				backGroundColorBtn.setColorChooseText("3D Background Color");
				backGroundColorBtn.setColor(Color.white);
				backGroundColorBtn.addColorChangeListener(Canvas3DViewer.this);
				generalSettingsPanel.add(GuiUtil.createLineBoxPanel(
						Box.createHorizontalStrut(4),
						GuiUtil.createFixedWidthLabel("Background color", 100),
						Box.createHorizontalStrut(8), backGroundColorBtn,
						Box.createHorizontalGlue(), Box.createHorizontalStrut(4)));

				// volume Settings
				final JLabel maxVolumeSampleLabel = new JLabel("Sample");
				ComponentUtil.setFixedWidth(maxVolumeSampleLabel, 100);
				maxVolumeSampleLabel
						.setToolTipText("Use low value for fine (but slow) render and high value for fast (but draft) render");
				generalSettingsPanel.add(GuiUtil.createLineBoxPanel(
						Box.createHorizontalStrut(4), maxVolumeSampleLabel,
						Box.createHorizontalStrut(8), volumeImageSampleCombo,
						Box.createHorizontalGlue(), Box.createHorizontalStrut(4)));
				generalSettingsPanel.add(GuiUtil.createLineBoxPanel(
						Box.createHorizontalStrut(4), localCoordinateCheckBox,
						Box.createHorizontalGlue(), Box.createHorizontalStrut(4)));
				generalSettingsPanel.add(GuiUtil.createLineBoxPanel(
						Box.createHorizontalStrut(4), boundingBoxCheckBox,
						Box.createHorizontalGlue(), Box.createHorizontalStrut(4)));

				volumeImageSampleCombo.addActionListener(Canvas3DViewer.this);
				localCoordinateCheckBox.addActionListener(Canvas3DViewer.this);
				boundingBoxCheckBox.addActionListener(Canvas3DViewer.this);
				panel.add(generalSettingsPanel);
				volumeImageSampleCombo.setSelectedIndex(resampling - 1);

				System.out.println("\r\nJava3D 3D Viewer v1.10.0\r\n");
				
				
			}
			
		}) ;
		initialized = true;

	}

	private void initialzeLUT() {
		colormapUpdater = new ColormapUpdater();

		// save lut and prepare for 3D visualization
		lutSave = sequence.createCompatibleLUT();
		final LUT lut = getLut();
		// save colormap
		saveColormap(lut);
		// adjust LUT alpha level for 3D view (this make lutChanged() to
		// be called)
		setDefaultOpacity(lut);
	}

	private void initializeViewer() {
		if (sequenceScale.getSizeZ() > 1) {
			posZ = -1; // use all of the z
		} else {
			posZ = 0;
		}
		getZNavigationPanel().setVisible(false);

		posT = 0;
		if (sequenceScale.getSizeT() > 1) {
			timeUpdater = new TimeUpdater();
			getTNavigationPanel().setVisible(true);
		} else {
			getTNavigationPanel().setVisible(false);
		}
		updateTNav();

		if (sequenceScale.getSizeC() > 1) {
			posC = -1; // use all of the C
		} else {
			posC = 0;
		}
		getMouseImageInfosPanel().setVisible(false);
	}

	public int[][] getRGBA(LUTChannel channel) {
		final IcyColorMap colorMap = channel.getColorMap();
		final Scaler scaler = channel.getScaler();
		double[][] RGBA = new double[4][colorMap.SIZE];
		final int maxIndex = colorMap.SIZE - 1;

		int index;
		for (int i = 0; i <= maxIndex; i++) {
			index = (int) scaler.scale(i);
			RGBA[0][i] = colorMap.getRed(index);
			RGBA[1][i] = colorMap.getGreen(index);
			RGBA[2][i] = colorMap.getBlue(index);
			RGBA[3][i] = colorMap.getAlpha(index);
		}

		int[][] RGBA_int = new int[4][colorMap.SIZE];
		Array2DUtil.arrayToIntArray(RGBA, RGBA_int, false);
		return RGBA_int;
	}

	public Sequence getSequenceScale() {
		return sequenceScale;
	}

	public void setSequenceScale(Sequence sequenceScale) {
		this.sequenceScale = sequenceScale;
	}

	public void setDefaultAlphaFor3D(IcyColorMapComponent alpha) {
		alpha.beginUpdate();
		try {
			alpha.removeAllControlPoint();
			alpha.setControlPoint(0, 0f);
			alpha.setControlPoint(255, 1f);
		} finally {
			alpha.endUpdate();
		}
	}

	@Override
	public void refresh() {
		if (!initialized)
			return;
		initialized = false;
		System.out.println("At this moment it's not supported for refresh!!");
		initialized = true;
	}

	@Override
	protected void setPositionZInternal(int z) {
		// not supported, Z should stay at -1
	}

	private void saveColormap(LUT lut) {
		lutSave.setScalers(lut);
		lutSave.setColorMaps(lut, true);
	}

	private void setDefaultOpacity(LUT lut) {
		IcyColorMap colorMap = null;
		for (LUTChannel lutChannel : lut.getLutChannels()) {
			colorMap = lutChannel.getColorMap();
			setDefaultAlphaFor3D(colorMap.alpha);
		}
	}

	@Override
	public Component getViewComponent() {
		return canvas;
	}

	public BufferedImage captureScreen() {
		int w = universe.getCanvas().getWidth();
		int h = universe.getCanvas().getHeight();
		return universe.takeSnapshot(w, h);
	}

	@Override
	public BufferedImage getRenderedImage(int t, int z, int c,
			boolean canvasView) {
		if (z != -1)
			throw new UnsupportedOperationException(
					"Error: getRenderedImage(..) with z != -1 not supported on Canvas3D.");
		if (!canvasView)
			System.out
					.println("Warning: getRenderedImage(..) with canvasView = false not supported on Canvas3D.");
		return captureScreen();

	}

	@Override
	public void changed(IcyCanvasEvent event) {
		super.changed(event);

		// avoid useless process during canvas initialization
		if (!initialized)
			return;

		if (event.getType() == IcyCanvasEventType.POSITION_CHANGED) {
			switch (event.getDim()) {
			case C:
				System.out
						.print("At this moment it's not supported for multi-channel");
				System.out.println("   posC: " + getPositionC());
				break;

			case T:
				System.out.println("posT:" + getPositionT());
				timeChanged(getPositionT());
				break;

			case Z:
				// shouldn't happen
				System.out.println("It should not happen of changing Z");
				break;
			}
		}
	}

	public void timeChanged(int newTimePoint) {
		timeUpdater.initialize(newTimePoint);
		ThreadUtil.bgRunSingle(timeUpdater);
	}

	private void updateSequence(int posT) {
		if (!initialized)
			return;
		initialized = false;

		if (contents.get(0).getCurrent().getContentFlag().getPosT() == posT) {
			initialized = false;
			return;
		}

		for (Content content : contents) {
			VoltexGroup voltexGroup = (VoltexGroup) content.getContent();
			voltexGroup.updateDisplayedData(sequenceScale, getPositionT(),
					content.getCurrent().getContentFlag().getPosC());

			universe.fireContentChanged(content);
		}
		initialized = true;

	}

	@Override
	protected void lutChanged(int component) {
		super.lutChanged(component);

		// avoid useless process during canvas initialization
		if (!initialized)
			return;

		// final LUT lut = getLut();
		final int posC = getPositionC();

		System.out.println("lutChanged() is called: component:" + component
				+ ", posC: " + posC);

		// refresh color properties for specified component
		if (component == -1)
			setupColorProperties(posC);
		else if (posC == -1)
			setupColorProperties(lut.getLutChannel(component));
		else if (posC == component)
			setupColorProperties(posC);

	}

	void setupColorProperties(int c) {
		final LUT lut = getLut();

		if (c == -1)
			setupColorProperties(lut);
		else
			setupColorProperties(lut.getLutChannel(c));
	}

	private void setupColorProperties(LUT lut) {
		for (int comp = 0; comp < lut.getNumChannel(); comp++)
			setupColorProperties(lut.getLutChannel(comp));
	}

	private void setupColorProperties(LUTChannel channel) {
		// Content contentInstant = universe.getContent(getPositionT(), index);
		// verify if channels are changed
		updateChannelVisibility(channel);

		// update threshold and color spectrum
		int[][] newLUTmaps = getRGBA(channel);
		int posC = channel.getChannel();
		updateColorMap(posC, newLUTmaps);
	}

	public void updateColorMap(int posC, int[][] newLUTmaps) {
		colormapUpdater.initialize(posC, newLUTmaps);
		ThreadUtil.bgRunSingle(colormapUpdater);
	}

	public void channelChanged() {
		LUT lut = getLut();
		for (int comp = 0; comp < lut.getNumChannel(); comp++)
			updateChannelVisibility(lut.getLutChannel(comp));
	}

	public void updateChannelVisibility(LUTChannel channel) {
		int posC = channel.getChannel();
		Content contentInstant = universe.getContent(getPositionT(), posC);
		if (!channel.isEnabled()) {
			contentInstant.setVisible(false);
			return;
		} else {
			contentInstant.setVisible(true);
		}
	}
    
	@Override
	public void shutDown() {
		super.shutDown();

		// restore colormap
		restoreOpacity(lutSave, getLut());

		// AWTMultiCaster of vtkPanel keep reference of this frame so
		// we have to release as most stuff we can
		removeAll();
		panel.removeAll();

		// executer.close(); // in which close all runnable tasks
		colormapUpdater = null;
		universe.cleanup();
		// sequence.close() //This method close all attached viewers

		panel = null;

		System.gc();
	}

	private void restoreOpacity(LUT srcLut, LUT dstLut) {
		final int numComp = Math.min(srcLut.getNumChannel(),
				dstLut.getNumChannel());

		for (int c = 0; c < numComp; c++)
			retoreOpacity(srcLut.getLutChannel(c), dstLut.getLutChannel(c));
	}

	private void retoreOpacity(LUTChannel srcLutBand, LUTChannel dstLutBand) {
		dstLutBand.getColorMap().alpha.copyFrom(srcLutBand.getColorMap().alpha);
	}

	@Override
	protected void sequenceMetaChanged(String metadataName) {
		super.sequenceMetaChanged(metadataName);
		if (!(metadataName == Sequence.ID_PIXEL_SIZE_X
				|| metadataName == Sequence.ID_PIXEL_SIZE_Y || metadataName == Sequence.ID_PIXEL_SIZE_Z))
			return;
		if (!initialized)
			return;
		updatePixelSize(sequence.getPixelSizeX(), sequence.getPixelSizeY(),
				sequence.getPixelSizeZ());
	}

	public void updatePixelSize(double pixelSizeX, double pixelSizeY,
			double pixelSizeZ) {
		initialized = false;
		universe.removeAllContents();
		contents.clear();
		Content content = null;
		sequenceScale.setPixelSizeX(pixelSizeX * resampling);
		sequenceScale.setPixelSizeY(pixelSizeY * resampling);
		sequenceScale.setPixelSizeZ(pixelSizeZ);
		for (int c = 0; c < sequenceScale.getSizeC(); c++) {
			content = universe.addVoltex(sequenceScale, getPositionT(), c);
			contents.add(content);
		}
		initialized = true;
	}

	@Override
	protected void sequenceTypeChanged() {
		super.sequenceTypeChanged();

		if (!initialized)
			return;
	}

	@Override
	protected void sequenceDataChanged(IcyBufferedImage image,
			SequenceEventType type) {
		super.sequenceDataChanged(image, type);

		if (!initialized)
			return;

		// rebuild image data and refresh
		// buildImageData();
		System.out
				.println("sequenceDataChanged is not supported at this moment!");
		refresh();
	}

	@Override
	public void colorChanged(ColorChooserButton source) {
		if (source == backGroundColorBtn) {
			updateBackgroundColor(new Color3f(backGroundColorBtn.getColor()));
		}
	}

	public void updateBackgroundColor(Color3f newColor) {
		Background background = canvas.getBG();
		background.setColor(newColor);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == localCoordinateCheckBox) {
			if (localCoordinateCheckBox.isSelected()) {
				contents.get(0).showCoordinateSystem(true);
			} else {
				contents.get(0).showCoordinateSystem(false);
			}
		}

		if (e.getSource() == boundingBoxCheckBox) {
			if (localCoordinateCheckBox.isSelected()) {
				contents.get(0).showBoundingBox(true);
			} else {
				contents.get(0).showBoundingBox(false);
			}
		}

		if (e.getSource() == volumeImageSampleCombo) {
			int resamplingNew = volumeImageSampleCombo.getSelectedIndex() + 1;
			if (resamplingNew == 0) {
				resamplingNew = Content.getDefaultResamplingFactor(sequence,
						Content.VOLUME);
			}
			if (this.resampling == resamplingNew) {
				return;
			} else {
				updateResamplingFactor(resamplingNew);
			}
		}

	}

	public void updateResamplingFactor(int resamplingNew) {
		initialized = false;
		this.resampling = resamplingNew;
		System.out.println("\r\nResampling started: " + resampling);
		sequenceScale = SequenceUtil.scale(sequence, sequence.getWidth()
				/ resampling, sequence.getHeight() / resampling);
		sequenceScale.setPixelSizeX(sequence.getPixelSizeX() * resampling);
		sequenceScale.setPixelSizeY(sequence.getPixelSizeY() * resampling);
		universe.removeAllContents();
		contents.clear();
		for (int i = 0; i < sequenceScale.getSizeC(); i++) {
			Content contentInstant = universe.addVoltex(sequenceScale, posT, i);
			contents.add(contentInstant);
			System.out.println("Content added: "
					+ contentInstant.getCurrent().getContentFlag()
							.getContentName());
		}
		initialized = true;
	}

	public Image3DUniverse getUniverse() {
		return universe;
	}

	public ImageCanvas3D getCanvas() {
		return canvas;
	}

}
