package ij3d;

import com.sun.j3d.utils.universe.SimpleUniverse;
import javax.media.j3d.J3DGraphics2D;
import javax.media.j3d.Canvas3D;
import javax.media.j3d.Background;
import javax.vecmath.Color3f;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import java.awt.Color;
import java.awt.Dimension;

import java.util.Map;
import java.util.HashMap;

//import ij.process.ByteProcessor;
//import ij.gui.ImageCanvas;
////Mengmeng
////import ij.ImagePlus;
//import ij.gui.Roi;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class ImageCanvas3D extends Canvas3D implements KeyListener {

	private Map<Integer, Long> pressed, released;
	private Background background;
	final private ExecutorService exec = Executors.newSingleThreadExecutor();

	protected void flush() {
		exec.shutdown();
	}

	public ImageCanvas3D(int width, int height) {
		super(SimpleUniverse.getPreferredConfiguration());
		setPreferredSize(new Dimension(width, height));
		pressed = new HashMap<Integer, Long>();
		released = new HashMap<Integer, Long>();
		background = new Background(
		new Color3f(Color.WHITE));
		background.setCapability(Background.ALLOW_COLOR_WRITE);

	}

	public Background getBG() { // can't use getBackground()
		return background;
	}


	public void render() {
		stopRenderer();
		swap();
		startRenderer();
	}

	/*
	 * Needed for the isKeyDown() method. Problem: keyPressed() and keyReleased
	 * is fired periodically, dependent on the operating system preferences,
	 * even if the key is hold down.
	 */
	public void keyTyped(KeyEvent e) {
	}

	public synchronized void keyPressed(KeyEvent e) {
		long when = e.getWhen();
		pressed.put(e.getKeyCode(), when);
	}

	public synchronized void keyReleased(KeyEvent e) {
		long when = e.getWhen();
		released.put(e.getKeyCode(), when);
	}

	public synchronized void releaseKey(int keycode) {
		pressed.remove(keycode);
		released.remove(keycode);
	}

	public synchronized boolean isKeyDown(int keycode) {
		if (!pressed.containsKey(keycode))
			return false;
		if (!released.containsKey(keycode))
			return true;
		long p = pressed.get(keycode);
		long r = released.get(keycode);
		return p >= r || System.currentTimeMillis() - r < 100;
	}
}
