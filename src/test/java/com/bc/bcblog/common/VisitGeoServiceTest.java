package com.bc.bcblog.common;
import com.bc.bcblog.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class VisitGeoServiceTest {
 @TempDir Path dir;
 @Test void countsAcrossDaysAndPreservesUnknownVisits() {
  JdbcTemplate db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE","sa",""));
  VisitorGeoService geo=mock(VisitorGeoService.class);
  when(geo.locate(anyString())).thenReturn(new VisitorGeoService.Place("China · Beijing",39.9,116.4));when(geo.available()).thenReturn(true);
  VisitGeoService service=new VisitGeoService(db,geo);ReflectionTestUtils.setField(service,"directory",dir.toString());service.init();
  service.record("8.8.8.8");service.record("8.8.8.8");
  db.update("UPDATE sys_visit_geo SET stat_date=?",LocalDate.now().minusDays(1));service.record("8.8.8.8");service.record("1.1.1.1");
  when(geo.locate("9.9.9.9")).thenReturn(VisitorGeoService.Place.unknown());service.record("9.9.9.9");
  Map totals=(Map)service.stats(7).get("totals");assertEquals(5,((Number)totals.get("pv")).intValue());assertEquals(3,((Number)totals.get("uv")).intValue());assertEquals(1,((Number)totals.get("unknownPv")).intValue());
  Map today=(Map)service.stats(1).get("totals");assertEquals(3,((Number)today.get("pv")).intValue());
  List<Map<String,Object>> points=(List)service.stats(7).get("points");assertEquals(1,points.size());assertEquals(2,((Number)points.get(0).get("uv")).intValue());
  for(Map<String,Object> row:db.queryForList("SELECT visitor_hash FROM sys_visit_geo"))assertTrue(row.get("visitor_hash").toString().matches("[0-9a-f]{64}"));
  db.update("UPDATE sys_visit_geo SET stat_date=? WHERE stat_date=?",LocalDate.now().minusDays(100),LocalDate.now().minusDays(1));service.cleanup();assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM sys_visit_geo",Integer.class));
 }
 @Test void readsActualOfflineDatabaseWhenProvided() throws Exception {
  String fixture=System.getProperty("visitor.geo.fixture");Assumptions.assumeTrue(fixture!=null);
  VisitorGeoService geo=new VisitorGeoService();ReflectionTestUtils.setField(geo,"directory",Paths.get(fixture).getParent().toString());geo.init();
  try { assertTrue(geo.available());assertNotNull(geo.locate("8.8.8.8").getLatitude());assertNotNull(geo.locate("114.114.114.114").getLatitude());assertNull(geo.locate("127.0.0.1").getLatitude()); }finally{geo.close();}
 }
}
