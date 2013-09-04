package plugins.kernel.canvas;

import icy.canvas.IcyCanvas;
import icy.canvas.Canvas3DViewer;
import icy.gui.viewer.Viewer;
import icy.plugin.abstract_.Plugin;
import icy.plugin.interface_.PluginCanvas;

public class Plugin3DViewer extends Plugin implements PluginCanvas {

	@Override
	public String getCanvasClassName() {
		return Plugin3DViewer.class.getName();
	}

	@Override
	public IcyCanvas createCanvas(Viewer viewer) {
		return new Canvas3DViewer(viewer);
	}

}
