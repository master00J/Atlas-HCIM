package com.hcimroute;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * De geladen route met de volgorde van alle afvinkbare stappen.
 * Bevat geen RuneLite-code, zodat ze los te testen is.
 */
public final class RouteGuide
{
	private final Route route;
	/** Alle afvinkbare stappen van de hoofdroute, in volgorde (zonder parallelle hoofdstukken). */
	private final List<Route.Step> mainSteps = new ArrayList<>();
	/** Afvinkbare stappen van parallelle hoofdstukken (Fase 2 – Sailing). */
	private final List<Route.Step> parallelSteps = new ArrayList<>();
	private final Map<String, Route.Step> byId = new LinkedHashMap<>();

	private RouteGuide(Route route)
	{
		this.route = route;
		for (Route.Chapter chapter : route.chapters)
		{
			for (Route.Section section : chapter.getSections())
			{
				section.chapter = chapter;
				for (Route.Step step : section.getSteps())
				{
					step.section = section;
					byId.put(step.id, step);
					if (!chapter.trackable || section.info)
					{
						continue;
					}
					(chapter.parallel ? parallelSteps : mainSteps).add(step);
				}
			}
		}
	}

	public static RouteGuide load(Gson gson, InputStream in) throws IOException
	{
		if (in == null)
		{
			throw new IOException("route.json niet gevonden");
		}
		try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			Route route = gson.fromJson(reader, Route.class);
			if (route == null || route.chapters == null)
			{
				throw new IOException("route.json is leeg of ongeldig");
			}
			return new RouteGuide(route);
		}
	}

	public String getTitle()
	{
		return route.title;
	}

	public String getVersion()
	{
		return route.version;
	}

	public List<Route.Chapter> getChapters()
	{
		return Collections.unmodifiableList(route.chapters);
	}

	public Route.Step getStep(String id)
	{
		return byId.get(id);
	}

	public List<Route.Step> getMainSteps()
	{
		return Collections.unmodifiableList(mainSteps);
	}

	/** Alle afvinkbare stappen, hoofdroute en parallel. */
	public List<Route.Step> getAllTrackableSteps()
	{
		List<Route.Step> all = new ArrayList<>(mainSteps);
		all.addAll(parallelSteps);
		return all;
	}

	/** De eerstvolgende niet-afgevinkte stappen van de hoofdroute. */
	public List<Route.Step> upcoming(Predicate<String> isDone, int limit)
	{
		List<Route.Step> out = new ArrayList<>();
		for (Route.Step step : mainSteps)
		{
			if (!isDone.test(step.id))
			{
				out.add(step);
				if (out.size() >= limit)
				{
					break;
				}
			}
		}
		return out;
	}

	/** De eerstvolgende niet-afgevinkte stap van de parallelle hoofdstukken, of null. */
	public Route.Step nextParallel(Predicate<String> isDone)
	{
		for (Route.Step step : parallelSteps)
		{
			if (!isDone.test(step.id))
			{
				return step;
			}
		}
		return null;
	}

	/** Alle stappen tot en met {@code last} in route-volgorde (hoofdroute of parallel). */
	public List<Route.Step> stepsUpTo(Route.Step last)
	{
		List<Route.Step> list = parallelSteps.contains(last) ? parallelSteps : mainSteps;
		int idx = list.indexOf(last);
		if (idx < 0)
		{
			// stap uit een info-sectie: alleen die stap zelf
			return Collections.singletonList(last);
		}
		return new ArrayList<>(list.subList(0, idx + 1));
	}

	public static int countDone(List<Route.Step> steps, Predicate<String> isDone)
	{
		int n = 0;
		for (Route.Step s : steps)
		{
			if (isDone.test(s.id))
			{
				n++;
			}
		}
		return n;
	}

	public static List<Route.Step> trackableSteps(Route.Chapter chapter)
	{
		List<Route.Step> out = new ArrayList<>();
		if (!chapter.trackable)
		{
			return out;
		}
		for (Route.Section s : chapter.getSections())
		{
			if (!s.info)
			{
				out.addAll(s.getSteps());
			}
		}
		return out;
	}
}
