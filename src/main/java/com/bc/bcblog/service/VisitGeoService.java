package com.bc.bcblog.service;

import com.bc.bcblog.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.*;

@Service @RequiredArgsConstructor @Slf4j
public class VisitGeoService {
 private final JdbcTemplate jdbc;
 private final VisitorGeoService geo;
 @Value("${bcblog.visitor-data-dir:./visitor-data}") private String directory;
 private String salt;
 private boolean ready;
 @PostConstruct public void init() {
  try {
   Path dir=Paths.get(directory);Files.createDirectories(dir);Path file=dir.resolve("visitor-salt");
   if(!Files.exists(file)){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);try {Files.write(file,Base64.getEncoder().encode(bytes),StandardOpenOption.CREATE_NEW);}catch(FileAlreadyExistsException ignored){}}
   salt=new String(Files.readAllBytes(file),StandardCharsets.UTF_8);
   if(salt.length()<32)throw new IllegalStateException("Invalid visitor salt");
   jdbc.execute("CREATE TABLE IF NOT EXISTS sys_visit_geo (stat_date DATE NOT NULL, visitor_hash CHAR(64) NOT NULL, location_key CHAR(64) NOT NULL, label VARCHAR(255) NOT NULL, latitude DOUBLE NULL, longitude DOUBLE NULL, pv BIGINT NOT NULL DEFAULT 1, PRIMARY KEY(stat_date,visitor_hash), KEY idx_visit_geo_location(location_key)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
   ready=true;
  } catch(Exception e){log.warn("访客地理统计初始化失败，原访问量统计不受影响");}
 }
 public void record(String ip) {
  if(!ready)return;
  try {
   // Canonicalize IPv6 variants so one IP cannot become several unique visitors.
   java.net.InetAddress address=VisitorGeoService.literalAddress(ip);
   String identity=address==null?"unknown":address.getHostAddress();
   VisitorGeoService.Place place=geo.locate(identity);
   String key=hash("location",place.getLabel()+":"+place.getLatitude()+":"+place.getLongitude());
   jdbc.update("INSERT INTO sys_visit_geo(stat_date,visitor_hash,location_key,label,latitude,longitude,pv) VALUES(?,?,?,?,?,?,1) ON DUPLICATE KEY UPDATE pv=pv+1",LocalDate.now(),hash(salt,identity),key,place.getLabel(),place.getLatitude(),place.getLongitude());
  } catch(Exception e){log.debug("访客位置统计暂不可用");}
 }
 public static String hash(String salt,String ip) {
  try {Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(salt.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));byte[] bytes=mac.doFinal(ip.getBytes(StandardCharsets.UTF_8));StringBuilder text=new StringBuilder();for(byte b:bytes)text.append(String.format("%02x",b&255));return text.toString();}
  catch(Exception e){throw new IllegalStateException(e);}
 }
 public static int validateDays(int days){if(days!=1&&days!=7&&days!=30)throw new BusinessException(400,"时间范围仅支持1、7、30天");return days;}
 public Map<String,Object> stats(int days) {
  validateDays(days);LocalDate end=LocalDate.now(),start=end.minusDays(days-1);
  Map<String,Object> result=new LinkedHashMap<>();result.put("days",days);result.put("from",start.toString());result.put("to",end.toString());result.put("available",ready);result.put("geoAvailable",geo.available());
  if(!ready)throw new BusinessException(503,"访客位置统计尚未就绪");
  result.put("points",jdbc.queryForList("SELECT location_key AS id,label,latitude,longitude,SUM(pv) AS pv,COUNT(DISTINCT visitor_hash) AS uv FROM sys_visit_geo WHERE stat_date BETWEEN ? AND ? AND latitude IS NOT NULL GROUP BY location_key,label,latitude,longitude ORDER BY pv DESC",start,end));
  result.put("totals",jdbc.queryForMap("SELECT COALESCE(SUM(pv),0) AS pv,COUNT(DISTINCT visitor_hash) AS uv,COALESCE(SUM(CASE WHEN latitude IS NULL THEN pv ELSE 0 END),0) AS unknownPv,COUNT(DISTINCT CASE WHEN latitude IS NULL THEN visitor_hash END) AS unknownUv FROM sys_visit_geo WHERE stat_date BETWEEN ? AND ?",start,end));
  result.put("collectedSince",jdbc.queryForObject("SELECT MIN(stat_date) FROM sys_visit_geo",String.class));
  return result;
 }
 @Scheduled(fixedDelay=86400000,initialDelay=60000) public void cleanup(){if(ready)try{jdbc.update("DELETE FROM sys_visit_geo WHERE stat_date < ?",LocalDate.now().minusDays(89));}catch(Exception e){log.warn("访客统计清理稍后重试");}}
}
