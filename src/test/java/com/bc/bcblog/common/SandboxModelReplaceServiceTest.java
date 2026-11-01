package com.bc.bcblog.common;
import com.bc.bcblog.service.SandboxModelReplaceService;
import com.bc.bcblog.entity.AiProvider;
import com.bc.bcblog.mapper.AiProviderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SandboxModelReplaceServiceTest {
 @Test void replacesEveryStoredScopeAndRollsBackMalformedWorld() {
  DriverManagerDataSource ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE","sa","");JdbcTemplate db=new JdbcTemplate(ds);
  db.execute("CREATE TABLE sys_config(config_key VARCHAR(100) UNIQUE,config_value VARCHAR(500))");db.execute("CREATE TABLE sandbox_world(id BIGINT PRIMARY KEY,settings_json VARCHAR(20000))");db.execute("CREATE TABLE sandbox_character(id BIGINT PRIMARY KEY,provider_id BIGINT,model VARCHAR(200),persona VARCHAR(100))");
  db.update("INSERT INTO sandbox_world VALUES(1,?)","{\"sandbox_system_model\":\"old\",\"keep\":\"yes\"}");db.update("INSERT INTO sandbox_world VALUES(2,NULL)");db.update("INSERT INTO sandbox_character VALUES(1,3,'old','persona')");
  AiProviderMapper providers=mock(AiProviderMapper.class);AiProvider p=new AiProvider();p.setId(9L);when(providers.selectById(9L)).thenReturn(p);
  SandboxModelReplaceService service=new SandboxModelReplaceService(db,providers);TransactionTemplate tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
  Map<String,Integer> result=tx.execute(status->service.replace(9L,"qwen-new"));assertEquals(2,result.get("worlds"));assertEquals(1,result.get("characters"));assertEquals(8,db.queryForObject("SELECT COUNT(*) FROM sys_config",Integer.class));
  assertTrue(db.queryForObject("SELECT settings_json FROM sandbox_world WHERE id=1",String.class).contains("yes"));assertEquals("qwen-new",db.queryForObject("SELECT model FROM sandbox_character",String.class));assertEquals("persona",db.queryForObject("SELECT persona FROM sandbox_character",String.class));
  db.update("UPDATE sandbox_world SET settings_json='broken' WHERE id=2");assertThrows(BusinessException.class,()->tx.execute(status->service.replace(9L,"next")));assertEquals("qwen-new",db.queryForObject("SELECT model FROM sandbox_character",String.class));assertFalse(db.queryForObject("SELECT settings_json FROM sandbox_world WHERE id=1",String.class).contains("next"));
  p.setOwnerId(2L);assertThrows(BusinessException.class,()->service.replace(9L,"next"));
  assertTrue(AdminPathRules.isSuperOnly("/api/admin/sandbox/models/replace-all","POST"));assertTrue(SecurityPathRules.needVerify("/api/admin/sandbox/models/replace-all","POST"));
 }
}
