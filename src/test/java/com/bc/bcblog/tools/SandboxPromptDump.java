package com.bc.bcblog.tools;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bc.bcblog.entity.SandboxAct;
import com.bc.bcblog.entity.SandboxCharacter;
import com.bc.bcblog.entity.SandboxInteraction;
import com.bc.bcblog.entity.SandboxItem;
import com.bc.bcblog.entity.SandboxLocation;
import com.bc.bcblog.entity.SandboxMemory;
import com.bc.bcblog.entity.SandboxNews;
import com.bc.bcblog.entity.SandboxWorld;
import com.bc.bcblog.mapper.SandboxCharacterMapper;
import com.bc.bcblog.service.impl.SandboxServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

// 调试用：把「某个角色下次行动时真正发给 AI 的系统提示词 + 用户提示词」原样打印出来。
//
// 不调用 AI、不写库；只复用服务里那套私有方法组装提示词，所以打印出来的内容和线上发给模型的一致。
// 因为需要本地数据库里的真实角色数据，这个类故意不以 Test 结尾，默认不会参与 mvn test；
// 需要时手动跑： mvn test -Dtest=SandboxPromptDump
// 想换角色有两种方式（不用改代码）：
//   mvn test -Dtest=SandboxPromptDump "-Dsandbox.dump.charName=伊露雅"
//   mvn test -Dtest=SandboxPromptDump "-Dsandbox.dump.charId=15"
// 都不指定时用下面的 DEFAULT_CHARACTER_ID。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SandboxPromptDump {

    static {
        // 测试进程里禁用定时任务（见 SandboxSceneRun 里的说明）
        System.setProperty("bcblog.sandbox.scheduler.disabled", "true");
    }

    // 默认要打印哪个角色（14 = 伊露雅）
    private static final Long DEFAULT_CHARACTER_ID = 14L;

    @Autowired
    private SandboxServiceImpl service;
    @Autowired
    private SandboxCharacterMapper characterMapper;

    @SuppressWarnings("unchecked")
    @Test
    void dump() throws Exception {
        SandboxCharacter character = resolveCharacter();
        Long characterId = character.getId();
        SandboxWorld world = service.world(character.getWorldId());
        List<SandboxLocation> locations = service.locations(character.getWorldId());
        List<SandboxCharacter> companions = (List<SandboxCharacter>) call("otherCharacters",
                new Class[]{Long.class}, characterId);
        List<SandboxAct> recent = (List<SandboxAct>) call("recentActs",
                new Class[]{Long.class, int.class}, characterId, 12);
        List<SandboxInteraction> whispers = (List<SandboxInteraction>) call("recentWhispers",
                new Class[]{Long.class, LocalDateTime.class}, characterId, character.getLastRunTime());
        List<SandboxAct> companionActs = (List<SandboxAct>) call("neighborActs",
                new Class[]{List.class}, companions);
        List<SandboxMemory> memories = (List<SandboxMemory>) call("recentMemories",
                new Class[]{Long.class, int.class}, characterId, 5);
        List<SandboxItem> backpack = service.items(characterId);
        List<SandboxNews> news = service.todayNews(character.getWorldId());

        String systemPrompt = (String) call("buildSystemPrompt",
                new Class[]{SandboxCharacter.class, SandboxWorld.class, List.class, List.class},
                character, world, locations, companions);
        String userPrompt = (String) call("buildUserPrompt",
                new Class[]{SandboxCharacter.class, List.class, List.class, List.class, List.class,
                        boolean.class, String.class, SandboxAct.class, List.class, List.class, List.class,
                        String.class, Integer.class, SandboxAct.class},
                character, recent, whispers, companions, companionActs, false, null, null,
                memories, backpack, news, null, null, null);

        // 写成 UTF-8 文件，避免在 Windows 控制台里被搞成乱码
        java.nio.file.Path dir = java.nio.file.Paths.get("target", "prompt-dump");
        java.nio.file.Files.createDirectories(dir);
        java.nio.file.Files.write(dir.resolve("system.txt"),
                ("===== SYSTEM PROMPT · " + character.getName() + " · " + systemPrompt.length() + " 字 =====\n" + systemPrompt)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        java.nio.file.Files.write(dir.resolve("user.txt"),
                ("===== USER PROMPT · " + character.getName() + " · " + userPrompt.length() + " 字 =====\n" + userPrompt)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        System.out.println("角色「" + character.getName() + "」的提示词已导出到 target/prompt-dump/{system,user}.txt");
    }

    // 解析要导出的角色：优先看启动参数里的名字，其次是 id，都没给就用默认值。
    private SandboxCharacter resolveCharacter() {
        String name = System.getProperty("sandbox.dump.charName");
        if (name != null && !name.trim().isEmpty()) {
            SandboxCharacter found = characterMapper.selectOne(new QueryWrapper<SandboxCharacter>()
                    .eq("name", name.trim()).last("limit 1"));
            if (found == null) {
                throw new IllegalStateException("找不到名字为「" + name.trim() + "」的角色");
            }
            return found;
        }
        String id = System.getProperty("sandbox.dump.charId");
        Long targetId = (id == null || id.trim().isEmpty())
                ? DEFAULT_CHARACTER_ID : Long.valueOf(id.trim());
        SandboxCharacter character = characterMapper.selectById(targetId);
        if (character == null) {
            throw new IllegalStateException("找不到角色 #" + targetId
                    + "（可以用 -Dsandbox.dump.charName=角色名 指定）");
        }
        return character;
    }

    // 反射调用服务里的私有方法（只为调试，不改生产代码可见性）。
    // 注意：SandboxServiceImpl 带 @Transactional，Spring 会给它套 CGLIB 代理，
    // 而代理实例自己的字段是空的、私有方法也不会被代理转发——所以要先解包出原始目标对象再反射调用。
    private Object call(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = SandboxServiceImpl.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        Object target = org.springframework.aop.framework.AopProxyUtils.getSingletonTarget(service);
        return method.invoke(target == null ? service : target, args);
    }
}
