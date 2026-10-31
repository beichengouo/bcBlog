package com.bc.bcblog.common;
import com.bc.bcblog.service.VisitorGeoService;
import com.bc.bcblog.service.VisitGeoService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;
class VisitorGeoTest {
 @Test void rejectsHostnamesAndPrivateAddresses() {
  for(String ip:new String[]{"localhost","127.0.0.1","10.0.0.1","192.168.1.1","::1","fc00::1","169.254.1.1","100.64.0.1","bad","","1234","1.2.3"}) assertNull(VisitorGeoService.publicAddress(ip),ip);
  assertNotNull(VisitorGeoService.publicAddress("8.8.8.8"));
  assertNotNull(VisitorGeoService.publicAddress("2606:4700:4700::1111"));
 }
 @Test void onlyLocalProxyCanSupplyClientAddress() {
  MockHttpServletRequest r=new MockHttpServletRequest(); r.setRemoteAddr("8.8.8.8");r.addHeader("X-Real-IP","1.1.1.1");
  assertEquals("8.8.8.8",VisitorGeoService.clientIp(r));r.setRemoteAddr("127.0.0.1");assertEquals("1.1.1.1",VisitorGeoService.clientIp(r));
 }
 @Test void validatesRange() { for(int d:new int[]{1,7,30})assertEquals(d,VisitGeoService.validateDays(d));assertThrows(BusinessException.class,()->VisitGeoService.validateDays(999)); }
 @Test void hashIsStableAndSalted() { assertEquals(VisitGeoService.hash("salt","8.8.8.8"),VisitGeoService.hash("salt","8.8.8.8"));assertNotEquals(VisitGeoService.hash("salt","8.8.8.8"),VisitGeoService.hash("other","8.8.8.8")); }
}
