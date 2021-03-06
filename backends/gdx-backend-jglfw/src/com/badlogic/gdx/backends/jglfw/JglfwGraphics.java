
package com.badlogic.gdx.backends.jglfw;

import static com.badlogic.jglfw.Glfw.*;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.GL10;
import com.badlogic.gdx.graphics.GL11;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GLCommon;
import com.badlogic.gdx.graphics.GLU;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;

/** An implementation of the {@link Graphics} interface based on GLFW.
 * @author Nathan Sweet */
public class JglfwGraphics implements Graphics {
	static int glMajorVersison, glMinorVersion;

	JglfwApplicationConfiguration config;
	long window;
	boolean fullscreen;
	int fullscreenMonitorIndex;
	final BufferFormat bufferFormat;
	volatile boolean isContinuous = true;

	float deltaTime;
	long frameStart, lastTime;
	int frames, fps;

	GLCommon gl;
	JglfwGL10 gl10;
	JglfwGL11 gl11;
	JglfwGL20 gl20;
	JglfwGLU glu;

	boolean sync;
	boolean resize;
	volatile boolean requestRendering;

	public JglfwGraphics (JglfwApplicationConfiguration config) {
		this.config = config;

		bufferFormat = new BufferFormat(config.r, config.g, config.b, config.a, config.depth, config.stencil, config.samples, false);

		createWindow();
		createGL();
	}

	private void createWindow () {
		glfwWindowHint(GLFW_RESIZABLE, config.resizable ? 1 : 0);
		glfwWindowHint(GLFW_RED_BITS, config.r);
		glfwWindowHint(GLFW_GREEN_BITS, config.g);
		glfwWindowHint(GLFW_BLUE_BITS, config.b);
		glfwWindowHint(GLFW_ALPHA_BITS, config.a);
		glfwWindowHint(GLFW_DEPTH_BITS, config.depth);
		glfwWindowHint(GLFW_STENCIL_BITS, config.stencil);
		glfwWindowHint(GLFW_SAMPLES, config.samples);
		glfwWindowHint(GLFW_DEPTH_BITS, config.bitsPerPixel);

		long fullscreenMonitor = glfwGetPrimaryMonitor();
		long[] monitors = glfwGetMonitors();
		// Find index of primary monitor.
		for (int i = 0, n = monitors.length; i < n; i++) {
			if (monitors[i] == fullscreenMonitor) {
				fullscreenMonitorIndex = i;
				break;
			}
		}
		// Find monitor specified in config.
		if (config.fullscreen) {
			if (monitors.length > 0) {
				if (config.fullscreenMonitorIndex < monitors.length) fullscreenMonitorIndex = config.fullscreenMonitorIndex;
				fullscreenMonitor = monitors[fullscreenMonitorIndex];
			}
		}

		// Create window.
		if (!setDisplayMode(config.width, config.height, config.fullscreen)) {
			throw new GdxRuntimeException("Unable to create window: " + config.width + "x" + config.height + ", fullscreen: "
				+ config.fullscreen);
		}

		setVSync(config.vSync);
		if (config.x != -1 && config.y != -1) glfwSetWindowPos(window, config.x, config.y);
	}

	private void createGL () {
		String version = glGetString(GL11.GL_VERSION);
		glMajorVersion = Integer.parseInt("" + version.charAt(0));
		glMinorVersion = Integer.parseInt("" + version.charAt(2));

		if (config.useGL20 && (glMajorVersion >= 2 || version.contains("2.1"))) { // special case for MESA, wtf...
			// FIXME - Add check whether gl 2.0 is actually supported.
			gl20 = new JglfwGL20();
			gl = gl20;
		} else {
			gl20 = null;
			if (glMajorVersion == 1 && glMinorVersion < 5) {
				gl10 = new JglfwGL10();
			} else {
				gl11 = new JglfwGL11();
				gl10 = gl11;
			}
			gl = gl10;
		}

		Gdx.gl = gl;
		Gdx.gl10 = gl10;
		Gdx.gl11 = gl11;
		Gdx.gl20 = gl20;
		Gdx.glu = glu = new JglfwGLU();
	}

	public boolean isGL11Available () {
		return gl11 != null;
	}

	public boolean isGL20Available () {
		return gl20 != null;
	}

	public GLCommon getGLCommon () {
		return gl;
	}

	public GL10 getGL10 () {
		return gl10;
	}

	public GL11 getGL11 () {
		return gl11;
	}

	public GL20 getGL20 () {
		return gl20;
	}

	public GLU getGLU () {
		return glu;
	}

	public int getWidth () {
		return glfwGetWindowWidth(window);
	}

	public int getHeight () {
		return glfwGetWindowHeight(window);
	}

	void updateTime () {
		long time = System.nanoTime();
		deltaTime = (time - lastTime) / 1000000000.0f;
		lastTime = time;

		if (time - frameStart >= 1000000000) {
			fps = frames;
			frames = 0;
			frameStart = time;
		}
		frames++;
	}

	public float getDeltaTime () {
		return deltaTime;
	}

	public float getRawDeltaTime () {
		return deltaTime;
	}

	public int getFramesPerSecond () {
		return fps;
	}

	public GraphicsType getType () {
		return GraphicsType.JGLFW;
	}

	public float getPpiX () {
		return Toolkit.getDefaultToolkit().getScreenResolution();
	}

	public float getPpiY () {
		return Toolkit.getDefaultToolkit().getScreenResolution();
	}

	public float getPpcX () {
		return Toolkit.getDefaultToolkit().getScreenResolution() / 2.54f;
	}

	public float getPpcY () {
		return Toolkit.getDefaultToolkit().getScreenResolution() / 2.54f;
	}

	public float getDensity () {
		return Toolkit.getDefaultToolkit().getScreenResolution() / 160f;
	}

	public boolean supportsDisplayModeChange () {
		return true;
	}

	public DisplayMode[] getDisplayModes () {
		GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
		java.awt.DisplayMode desktopMode = device.getDisplayMode();
		java.awt.DisplayMode[] displayModes = device.getDisplayModes();
		Array<DisplayMode> modes = new Array();
		outer:
		for (java.awt.DisplayMode mode : displayModes) {
			for (DisplayMode other : modes)
				if (other.width == mode.getWidth() && other.height == mode.getHeight() && other.bitsPerPixel == mode.getBitDepth())
					continue outer; // Duplicate.
			if (mode.getBitDepth() != desktopMode.getBitDepth()) continue;
			modes.add(new JglfwDisplayMode(mode.getWidth(), mode.getHeight(), mode.getRefreshRate(), mode.getBitDepth()));
		}
		return modes.toArray();
	}

	public DisplayMode getDesktopDisplayMode () {
		java.awt.DisplayMode mode = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDisplayMode();
		return new JglfwDisplayMode(mode.getWidth(), mode.getHeight(), mode.getRefreshRate(), mode.getBitDepth());
	}

	public boolean setDisplayMode (DisplayMode displayMode) {
		if (displayMode.bitsPerPixel != 0) glfwWindowHint(GLFW_DEPTH_BITS, displayMode.bitsPerPixel);
		glfwSetWindowSize(window, displayMode.width, displayMode.height);
	}

	public boolean setDisplayMode (int width, int height, boolean fullscreen) {
		if (window == 0 || fullscreen != config.fullscreen) {
			long fullscreenMonitor = 0;
			long[] monitors = glfwGetMonitors();
			if (monitors.length > 0)
				fullscreenMonitor = fullscreenMonitorIndex < monitors.length ? monitors[fullscreenMonitorIndex] : 0;

			long window = glfwCreateWindow(config.width, config.height, config.title, fullscreenMonitor, 0);
			if (window == 0) return false;
			if (this.window != 0) glfwDestroyWindow(window);
			glfwMakeContextCurrent(window);
			this.window = window;
			return true;
		}
		glfwSetWindowSize(window, width, height);
		return true;
	}

	public void setTitle (String title) {
		glfwSetWindowTitle(window, title);
	}

	public void setVSync (boolean vsync) {
		this.sync = vsync;
		if (!config.cpuSync) glfwSwapInterval(vsync ? 1 : 0);
	}

	public BufferFormat getBufferFormat () {
		return bufferFormat;
	}

	public boolean supportsExtension (String extension) {
		return glfwExtensionSupported(extension);
	}

	public void setContinuousRendering (boolean isContinuous) {
		this.isContinuous = isContinuous;
	}

	public boolean isContinuousRendering () {
		return isContinuous;
	}

	public void requestRendering () {
		synchronized (this) {
			requestRendering = true;
		}
	}

	public boolean isFullscreen () {
		return config.fullscreen;
	}

	public long getWindow () {
		return window;
	}

	boolean shouldRender () {
		synchronized (this) {
			boolean requestRendering = this.requestRendering;
			this.requestRendering = false;
			return requestRendering || isContinuous;
		}
	}

	static class JglfwDisplayMode extends DisplayMode {
		protected JglfwDisplayMode (int width, int height, int refreshRate, int bitsPerPixel) {
			super(width, height, refreshRate, bitsPerPixel);
		}
	}
}
