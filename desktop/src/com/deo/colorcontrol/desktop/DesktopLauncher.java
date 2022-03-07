package com.deo.colorcontrol.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.deo.colorcontrol.Main;

public class DesktopLauncher {
	
	public static void main (String[] arg) {
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setWindowedMode(800, 480);
		config.setResizable(false);
		config.setWindowIcon("spectrum.png");
		config.setTitle("Arduino color music controller");
		new Lwjgl3Application(new Main(), config);
	}
}
