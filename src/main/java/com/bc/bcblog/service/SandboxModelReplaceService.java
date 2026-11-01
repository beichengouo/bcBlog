package com.bc.bcblog.service;
import cn.hutool.json.*;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.entity.AiProvider;
import com.bc.bcblog.mapper.AiProviderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service @RequiredArgsConstructor
public class SandboxModelReplaceService {
 private final JdbcTemplate jdbc;
 private final AiProviderMapper providers;
 @Transactional public Map<String,Integer> replace(Long providerId,String model) {
  AiProvider provider=providerId==null?null:providers.selectById(providerId);
  if(provider==null || provider.getOwnerId()!=null)throw new BusinessException(400,"请选择系统服务商");
  if(model==null || model.trim().isEmpty() || model.length()>200 || model.chars().anyMatch(Character::isISOControl))throw new BusinessException(400,"请输入有效模型名");
  Map<String,String> replacements=new LinkedHashMap<>();
  for(String module:Arrays.asList("system","news","shop","quest")) {
   replacements.put("sandbox_"+module+"_provider_id",String.valueOf(providerId));
   replacements.put("sandbox_"+module+"_model",model.trim());
  }
  List<Map<String,Object>> worlds=jdbc.queryForList("SELECT id,settings_json FROM sandbox_world FOR UPDATE");
  for(Map<String,Object> world:worlds){
   Object raw=world.get("settings_json");String text=raw==null?"":String.valueOf(raw);JSONObject settings;
   try { if(!text.trim().isEmpty()&&!JSONUtil.isTypeJSONObject(text))throw new IllegalArgumentException();settings=text.trim().isEmpty()?new JSONObject():JSONUtil.parseObj(text); }
   catch(Exception e){throw new BusinessException(400,"世界配置格式异常，请先修复后再替换");}
   settings.putAll(replacements);
   jdbc.update("UPDATE sandbox_world SET settings_json=? WHERE id=?",settings.toString(),world.get("id"));
  }
  for(Map.Entry<String,String> entry:replacements.entrySet())jdbc.update("INSERT INTO sys_config(config_key,config_value) VALUES(?,?) ON DUPLICATE KEY UPDATE config_value=VALUES(config_value)",entry.getKey(),entry.getValue());
  int characters=jdbc.update("UPDATE sandbox_character SET provider_id=?,model=?",providerId,model.trim());
  Map<String,Integer> result=new LinkedHashMap<>();result.put("worlds",worlds.size());result.put("characters",characters);return result;
 }
}
