package com.bc.bcblog.service;

import com.maxmind.db.Reader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.nio.file.*;
import java.util.*;

@Service
@Slf4j
public class VisitorGeoService {
 @Value("${bcblog.visitor-data-dir:./visitor-data}") private String directory;
 private Reader reader;
 @PostConstruct public void init() {
  Path file=Paths.get(directory,"dbip-city-lite.mmdb");
  try { if(Files.isRegularFile(file)) reader=new Reader(file.toFile()); }
  catch(Exception e) { log.warn("访客位置库不可用，位置将显示未知"); }
 }
 public boolean available() { return reader!=null; }
 @PreDestroy public void close() throws java.io.IOException { if(reader!=null)reader.close(); }
 public static String clientIp(HttpServletRequest request) {
  String remote=request.getRemoteAddr();
  if("127.0.0.1".equals(remote)||"::1".equals(remote)||"0:0:0:0:0:0:0:1".equals(remote)) {
   String forwarded=request.getHeader("X-Real-IP");
   if(forwarded!=null && !forwarded.trim().isEmpty())return forwarded.trim();
  }
  return remote;
 }
 public static InetAddress literalAddress(String value) {
  if(value==null || value.length()>45 || !value.matches("[0-9a-fA-F:.]+") || (!value.contains(":") && !value.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")))return null;
  try{return InetAddress.getByName(value);}catch(Exception e){return null;}
 }
 public static InetAddress publicAddress(String value) {
  if(value==null || value.length()>45 || !value.matches("[0-9a-fA-F:.]+"))return null;
  try {
   InetAddress ip=literalAddress(value); if(ip==null)return null;
   if(ip.isAnyLocalAddress()||ip.isLoopbackAddress()||ip.isLinkLocalAddress()||ip.isSiteLocalAddress()||ip.isMulticastAddress())return null;
   byte[] b=ip.getAddress();int first=b[0]&255;
   if(b.length==16 && (first&254)==252)return null;
   if(b.length==4 && (first==0 || first>=224 || (first==100 && (b[1]&255)>=64 && (b[1]&255)<=127)))return null;
   return ip;
  } catch(Exception e) { return null; }
 }
 public Place locate(String ip) {
  InetAddress address=publicAddress(ip);
  if(reader==null || address==null)return Place.unknown();
  try {
   Map data=reader.get(address,Map.class);if(data==null)return Place.unknown();
   Map location=map(data.get("location"));
   Object lat=location.get("latitude"),lon=location.get("longitude");
   if(!(lat instanceof Number)||!(lon instanceof Number))return Place.unknown();
   double y=((Number)lat).doubleValue(),x=((Number)lon).doubleValue();
   if(!Double.isFinite(y)||!Double.isFinite(x)||Math.abs(y)>90||Math.abs(x)>180)return Place.unknown();
   String country=name(data.get("country")),city=name(data.get("city")),region="";
   Object subdivisions=data.get("subdivisions");if(subdivisions instanceof List && !((List)subdivisions).isEmpty())region=name(((List)subdivisions).get(0));
   String label=String.join(" · ",new LinkedHashSet<>(Arrays.asList(country,region,city))).replaceAll("(^ · | · $)","");
   return new Place(label.isEmpty()?"未知地区":label,Math.round(y*1000.0)/1000.0,Math.round(x*1000.0)/1000.0);
  } catch(Exception e) { return Place.unknown(); }
 }
 private static Map map(Object o) { return o instanceof Map?(Map)o:Collections.emptyMap(); }
 private static String name(Object o) { Map names=map(map(o).get("names"));Object value=names.get("zh-CN");if(value==null)value=names.get("en");return value==null?"":String.valueOf(value); }
 @lombok.Value public static class Place {
  String label; Double latitude; Double longitude;
  public static Place unknown(){return new Place("未知位置",null,null);}
 }
}
