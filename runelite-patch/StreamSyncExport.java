package com.hcimguide;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import net.runelite.client.RuneLite;

/**
 * Local-only, privacy-preserving live stream snapshot for HCIM Atlas.
 * Called by Guide Overlay approximately once per 3 seconds while logged in.
 * No credentials, account names, API calls or external connections.
 */
final class StreamSyncExport
{
    private static final Path OUT = RuneLite.RUNELITE_DIR.toPath()
        .resolve("hcim-guide").resolve("stream-state.json");
    private static long lastWarningAt = 0;

    private StreamSyncExport() { }

    static synchronized void write(Guide guide, String guideId,
        Set<String> completedSteps, Set<String> skippedSteps)
    {
        if (guide == null || guideId == null || completedSteps == null || skippedSteps == null)
        {
            return;
        }
        // Snapshot both synchronized sets before iterating the guide.
        final Set<String> completed;
        final Set<String> skipped;
        synchronized (completedSteps)
        {
            completed = new HashSet<>(completedSteps);
        }
        synchronized (skippedSteps)
        {
            skipped = new HashSet<>(skippedSteps);
        }

        int total = 0;
        int done = 0;
        int skippedCount = 0;
        int sectionTotal = 0;
        int sectionIndex = 0;
        int activeSectionIndex = 0;
        int activeStepIndex = 0;
        String chapter = "";
        String section = "";
        String currentBank = "";
        String currentStep = "";

        for (GuideEpisode episode : guide.getEpisodes())
        {
            for (GuideBank bank : episode.getBanks())
            {
                sectionTotal++;
                sectionIndex++;
                int indexInSection = 0;
                for (GuideStep step : bank.getSteps())
                {
                    indexInSection++;
                    String key = step.getKey();
                    if (skipped.contains(key))
                    {
                        skippedCount++;
                        continue;
                    }
                    total++;
                    if (completed.contains(key))
                    {
                        done++;
                    }
                    else if (currentStep.isEmpty())
                    {
                        chapter = episode.getTitle();
                        section = bank.getTitle();
                        currentBank = bank.getTitle();
                        currentStep = step.getText();
                        activeSectionIndex = sectionIndex;
                        activeStepIndex = indexInSection;
                    }
                }
            }
        }

        if (currentStep.isEmpty())
        {
            currentStep = total == 0 ? "Geen actieve stappen" : "Alle guidestappen voltooid!";
            section = total == 0 ? "Geen guide" : "Guide voltooid";
            chapter = total == 0 ? "" : "Endgame";
            currentBank = section;
            activeSectionIndex = sectionTotal;
        }

        StringBuilder json = new StringBuilder(1000);
        json.append('{');
        json.append("\"schemaVersion\":1,");
        field(json,"guideId",guideId).append(',');
        field(json,"guideTitle",guideId.equals("b0aty-hcim-v3")?"B0aty HCIM Guide V3":"HCIM Atlas · "+guideId).append(',');
        field(json,"chapter",chapter).append(',');
        field(json,"section",section).append(',');
        field(json,"currentBank",currentBank).append(',');
        field(json,"currentStep",currentStep).append(',');
        number(json,"currentStepIndex",activeStepIndex).append(',');
        number(json,"sectionIndex",activeSectionIndex).append(',');
        number(json,"sectionTotal",sectionTotal).append(',');
        number(json,"stepsTotal",total).append(',');
        number(json,"stepsDone",done).append(',');
        number(json,"stepsSkipped",skippedCount).append(',');
        field(json,"timestamp",java.time.Instant.now().toString());
        json.append('}');

        try
        {
            Files.createDirectories(OUT.getParent());
            Path tmp = OUT.resolveSibling("stream-state.json.tmp");
            Files.write(tmp, json.toString().getBytes(StandardCharsets.UTF_8));
            try
            {
                Files.move(tmp, OUT, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException e)
            {
                Files.move(tmp, OUT, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch (IOException e)
        {
            long now=System.currentTimeMillis();
            if (now-lastWarningAt>60000)
            {
                System.err.println("HCIM Atlas: unable to write local stream state: " + e.getMessage());
                lastWarningAt=now;
            }
        }
    }

    private static StringBuilder number(StringBuilder b,String key,int value)
    {
        return b.append('"').append(key).append("\":").append(value);
    }
    private static StringBuilder field(StringBuilder b,String key,String value)
    {
        b.append('"').append(key).append("\":\"");
        String v=value==null?"":value;
        for(int i=0;i<v.length();i++)
        {
            char c=v.charAt(i);
            switch(c)
            {
                case '"': b.append("\\\"");break;
                case '\\': b.append("\\\\");break;
                case '\n': b.append("\\n");break;
                case '\r': b.append("\\r");break;
                case '\t': b.append("\\t");break;
                default:
                    if(c<32) b.append(String.format("\\u%04x",(int)c));
                    else b.append(c);
            }
        }
        return b.append('"');
    }
}
