package com.deo.colorcontrol.desktop;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.deo.colorcontrol.Main;

public class DesktopLauncher {
	
	public static void main (String[] arg) {
		LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
		config.pauseWhenBackground = false;
		config.width = 800;
		config.height = 480;
		config.resizable = false;
		config.foregroundFPS = 60;
		config.backgroundFPS = 60;
		config.vSyncEnabled = true;
		config.forceExit = true;
		config.addIcon("spectrum.png", Files.FileType.Internal);
		config.title = "Arduino color music controller";
		new LwjglApplication(new Main(), config);
	}
}
