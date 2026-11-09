package com.hcimroute;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Onthoudt bij welk RuneLite-profiel (= RuneScape-account) de voortgang in het
 * geheugen hoort. Voortgang mag alleen gewijzigd en opgeslagen worden als dat
 * profiel nog steeds het actieve is; anders zou je bij een accountwissel de
 * voortgang van het ene account in het andere wegschrijven.
 */
final class ProfileGuard
{
	private final Supplier<String> currentKey;
	private volatile String loadedKey;

	ProfileGuard(Supplier<String> currentKey)
	{
		this.currentKey = currentKey;
	}

	/** Het actieve profiel volgens RuneLite, of null als er (nog) geen is. */
	String current()
	{
		return currentKey.get();
	}

	/** Markeert dat de voortgang van dit profiel geladen is (null = niets geladen). */
	void loaded(String key)
	{
		loadedKey = key;
	}

	boolean isLoaded()
	{
		return loadedKey != null;
	}

	/** True als er voortgang geladen is én die bij het actieve profiel hoort. */
	boolean matches()
	{
		String key = currentKey.get();
		return key != null && key.equals(loadedKey);
	}

	/** True als RuneLite intussen een ander profiel gebruikt dan het geladen profiel. */
	boolean isStale()
	{
		return !Objects.equals(currentKey.get(), loadedKey);
	}
}
