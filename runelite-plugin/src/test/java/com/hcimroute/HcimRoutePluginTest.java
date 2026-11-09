package com.hcimroute;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Start RuneLite in developer-mode met deze plugin geladen: ./gradlew run */
public class HcimRoutePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(HcimRoutePlugin.class);
		RuneLite.main(args);
	}
}
