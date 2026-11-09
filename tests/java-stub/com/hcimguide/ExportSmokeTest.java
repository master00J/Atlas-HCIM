package com.hcimguide;
import java.util.*;
public class ExportSmokeTest {
 public static void main(String[] args){
  Guide g=new Guide();GuideEpisode e=new GuideEpisode("Starting out");GuideBank b=new GuideBank("Bank 1");
  b.steps.add(new GuideStep("one","Talk to Father Aereck"));
  b.steps.add(new GuideStep("two","Pickpocket a man/woman"));
  b.steps.add(new GuideStep("three","Buy spade, chisel & hammer"));
  e.banks.add(b);g.episodes.add(e);
  StreamSyncExport.write(g,"hcim-atlas-test",new HashSet<>(Arrays.asList("one")),new HashSet<>(Arrays.asList("three")));
 }
}
