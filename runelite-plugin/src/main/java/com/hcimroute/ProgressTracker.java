package com.hcimroute;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Welke stappen afgevinkt zijn. Wordt per RuneScape-account opgeslagen als
 * één tekstwaarde in de RuneLite-config (zie {@link #serialize()}).
 * Thread-safe: de client thread (auto-afvinken) en de Swing-thread (panel) schrijven allebei.
 */
public final class ProgressTracker
{
	private final Set<String> done = ConcurrentHashMap.newKeySet();

	public boolean isDone(String stepId)
	{
		return done.contains(stepId);
	}

	/** @return true als er iets veranderde */
	public boolean set(String stepId, boolean value)
	{
		return value ? done.add(stepId) : done.remove(stepId);
	}

	/** @return het aantal stappen dat veranderde */
	public int setAll(Collection<String> stepIds, boolean value)
	{
		int changed = 0;
		for (String id : stepIds)
		{
			if (set(id, value))
			{
				changed++;
			}
		}
		return changed;
	}

	public void clear()
	{
		done.clear();
	}

	public int size()
	{
		return done.size();
	}

	/** Laadt een waarde uit {@link #serialize()}; onbekende ids blijven bewaard voor als de route later terugkomt. */
	public void load(String value)
	{
		done.clear();
		if (value == null || value.isEmpty())
		{
			return;
		}
		for (String id : value.split(","))
		{
			String t = id.trim();
			if (!t.isEmpty())
			{
				done.add(t);
			}
		}
	}

	public String serialize()
	{
		List<String> ids = new ArrayList<>(done);
		Collections.sort(ids);
		return String.join(",", ids);
	}
}
